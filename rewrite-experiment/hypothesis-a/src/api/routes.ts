import { Router, Request, Response } from 'express';
import { createTradeOrder } from '../domain/TradeOrder';
import { OrderSide, OrderStatus, DerivativeContractType, DerivativeStatus, RiskStatus } from '../domain/enums';
import { RuleContext } from '../domain/RuleContext';
import { RuleEngine } from '../rules/RuleEngine';
import { PricingService } from '../pricing/PricingService';
import { calculateCommission } from '../pricing/CommissionCalculator';
import { MessageBroker, QUEUES } from '../integration/MessageBroker';
import { DerivativeOrder } from '../domain/DerivativeOrder';
import { DerivativeProcessor } from '../derivatives/DerivativeProcessor';
import { RiskOrder } from '../domain/RiskOrder';
import { ExposureCalculator } from '../risk/ExposureCalculator';
import { Client } from '../domain/Client';

export interface ApiDependencies {
  ruleEngine: RuleEngine;
  pricingService: PricingService;
  messageBroker: MessageBroker;
  derivativeProcessor: DerivativeProcessor;
  exposureCalculator: ExposureCalculator;
  getClient: (clientId: string) => Promise<Client | null>;
  saveOrder: (order: ReturnType<typeof createTradeOrder>) => Promise<void>;
  updateOrderStatus: (orderId: string, status: OrderStatus) => Promise<void>;
}

export function createRouter(deps: ApiDependencies): Router {
  const router = Router();

  router.post('/orders', async (req: Request, res: Response) => {
    try {
      const { clientId, symbol, quantity, side, requestedPrice } = req.body;

      if (!clientId || !symbol || !quantity || !side || !requestedPrice) {
        res.status(400).json({ error: 'Missing required fields' });
        return;
      }

      const order = createTradeOrder({
        clientId,
        symbol,
        quantity: Number(quantity),
        side: side as OrderSide,
        requestedPrice: Number(requestedPrice),
      });

      // Look up client
      const client = await deps.getClient(clientId);
      if (!client) {
        order.status = OrderStatus.REJECTED;
        order.notes = `Client not found: ${clientId}`;
        await deps.saveOrder(order);
        res.status(200).json({ order, rejected: true, reason: order.notes });
        return;
      }

      if (!client.active) {
        order.status = OrderStatus.REJECTED;
        order.notes = 'Client account is inactive';
        await deps.saveOrder(order);
        res.status(200).json({ order, rejected: true, reason: order.notes });
        return;
      }

      // Rule engine
      const ctx = new RuleContext(order, client);
      const passed = await deps.ruleEngine.evaluate(ctx);

      if (!passed) {
        order.status = OrderStatus.REJECTED;
        order.notes = ctx.rejectionReason;
        order.lastModified = new Date();
        await deps.saveOrder(order);

        // Send rejection notification if client has email
        if (client.email) {
          const body = `${order.symbol}|${order.quantity}|${order.side}|${order.requestedPrice}|${ctx.rejectionReason}||`;
          await deps.messageBroker.publish(
            QUEUES.NOTIFICATIONS,
            JSON.stringify({
              type: 'ORDER_REJECT',
              channel: 'EMAIL',
              recipient: client.email,
              orderId: order.orderId,
              body,
            })
          );
        }

        await deps.messageBroker.publish(
          QUEUES.TRADE_CONFIRMATIONS,
          JSON.stringify({ orderId: order.orderId, status: 'REJECTED' })
        );

        res.status(200).json({ order, rejected: true, reason: ctx.rejectionReason });
        return;
      }

      // Get price quote
      const pricingTierOverride = ctx.getAttribute('pricing_tier_override') as string | undefined;
      const quoteTier = pricingTierOverride || client.tier;
      const quote = await deps.pricingService.getQuote(symbol, quoteTier as never);
      const quotedPrice = quote.last;

      if (quotedPrice <= 0) {
        order.status = OrderStatus.REJECTED;
        order.notes = `Price unavailable for symbol: ${symbol}`;
        order.lastModified = new Date();
        await deps.saveOrder(order);
        res.status(200).json({ order, rejected: true, reason: order.notes });
        return;
      }

      // Manual price deviation check (BUG-007, redundant with rule engine)
      const deviation =
        Math.abs(quotedPrice - order.requestedPrice) / order.requestedPrice;
      if (deviation > 0.10) {
        order.status = OrderStatus.REJECTED;
        order.notes = 'Price deviation exceeds 10% limit';
        order.lastModified = new Date();
        await deps.saveOrder(order);
        res.status(200).json({ order, rejected: true, reason: order.notes });
        return;
      }

      // Manual volume compliance warning (redundant with DailyVolumeLimitRule)
      if (order.quantity > 50000) {
        console.warn(
          `[VolumeCompliance] WARNING: quantity ${order.quantity} > 50000 (REG-2005-003)`
        );
      }

      // Fill the order
      const commission = calculateCommission(
        order.quantity * quotedPrice,
        client.tier
      );
      order.price = quotedPrice;
      order.status = OrderStatus.FILLED;
      order.lastModified = new Date();
      order.notes += `Filled at ${quotedPrice}, commission=${commission}`;

      // Surveillance flags from context
      const flags = ctx.getAttribute('surveillance_flags') as string;
      if (flags) {
        order.surveillanceFlags = flags;
      }

      await deps.saveOrder(order);
      // Redundant update for settlement batch compatibility (BUG-006)
      await deps.updateOrderStatus(order.orderId, OrderStatus.FILLED);

      // Send confirmation notification
      if (client.email) {
        const body = `${order.symbol}|${order.quantity}|${order.side}|${quotedPrice}||${order.quantity * quotedPrice}|`;
        await deps.messageBroker.publish(
          QUEUES.NOTIFICATIONS,
          JSON.stringify({
            type: 'ORDER_CONFIRM',
            channel: 'EMAIL',
            recipient: client.email,
            orderId: order.orderId,
            body,
          })
        );
      }

      await deps.messageBroker.publish(
        QUEUES.TRADE_CONFIRMATIONS,
        JSON.stringify({ orderId: order.orderId, status: 'FILLED' })
      );

      res.status(201).json({ order, rejected: false });
    } catch (error) {
      console.error('[API] Order processing error:', error);
      res.status(500).json({ error: 'Internal server error' });
    }
  });

  router.get('/orders/:orderId', async (req: Request, res: Response) => {
    res.status(501).json({ error: 'Not implemented - query DB directly' });
  });

  router.get('/pricing/quote/:symbol', async (req: Request, res: Response) => {
    try {
      const { symbol } = req.params;
      const tier = req.query.tier as string | undefined;
      const quote = await deps.pricingService.getQuote(
        symbol,
        tier as never
      );
      res.json(quote);
    } catch (error) {
      res.status(500).json({ error: 'Price lookup failed' });
    }
  });

  router.post(
    '/pricing/batch',
    async (req: Request, res: Response) => {
      try {
        const { symbols, tier } = req.body;
        const quotes = await deps.pricingService.getBatchQuotes(
          symbols,
          tier as never
        );
        res.json(quotes);
      } catch (error) {
        res.status(500).json({ error: 'Batch price lookup failed' });
      }
    }
  );

  router.post('/derivatives/orders', async (req: Request, res: Response) => {
    try {
      const { orderId, clientId, contractType, underlying, strikePrice, quantity, expiry } = req.body;

      const order: DerivativeOrder = {
        orderId: orderId || `DRV-${Date.now()}`,
        clientId,
        contractType: contractType as DerivativeContractType,
        underlying,
        strikePrice: Number(strikePrice),
        quantity: Number(quantity),
        expiry: expiry ? new Date(expiry) : null,
        status: DerivativeStatus.NEW,
        premium: 0,
      };

      const result = await deps.derivativeProcessor.processOrder(order);
      res.status(result.status === DerivativeStatus.FILLED ? 201 : 200).json(result);
    } catch (error) {
      res.status(500).json({ error: 'Derivative processing failed' });
    }
  });

  router.post('/risk/assess', async (req: Request, res: Response) => {
    try {
      const { sourceOrderId, clientId, symbol, quantity, side, price } = req.body;

      const riskOrder: RiskOrder = {
        riskOrderId: `RISK-${Date.now()}`,
        sourceOrderId,
        clientId,
        symbol,
        quantity: Number(quantity),
        side: side as OrderSide,
        price: Number(price),
        notionalValue: 0,
        exposureContribution: 0,
        varContribution: 0,
        riskStatus: RiskStatus.PENDING,
        assessmentDate: new Date(),
      };

      const result = await deps.exposureCalculator.assessOrder(riskOrder);
      res.json(result);
    } catch (error) {
      res.status(500).json({ error: 'Risk assessment failed' });
    }
  });

  router.get('/health', (_req: Request, res: Response) => {
    res.json({ status: 'UP', timestamp: new Date().toISOString() });
  });

  return router;
}
