import { Router, Request, Response } from 'express';
import { createTradeOrder, TradeOrder, OrderStatus } from '../domain/TradeOrder';
import { Client } from '../domain/Client';
import { createRuleContext, RuleContext } from '../domain/RuleContext';
import { RuleEngine } from '../rules/RuleEngine';
import { PricingService, PRICE_DEVIATION_THRESHOLD } from '../pricing/PricingService';
import { AuditService } from '../audit/AuditService';
import { NotificationService } from '../notifications/NotificationService';
import { assessRisk } from '../risk/ExposureCalculator';

export function createOrderRouter(deps: {
  ruleEngine: RuleEngine;
  pricingService: PricingService;
  auditService: AuditService;
  notificationService: NotificationService;
  clientLookup: (clientId: string) => Client | null;
  orderStore: Map<string, TradeOrder>;
}): Router {
  const router = Router();

  // Submit a new order
  router.post('/orders', (req: Request, res: Response) => {
    const { clientId, symbol, quantity, side, requestedPrice } = req.body;

    // Validate input
    if (!clientId || !symbol || !quantity || !side || !requestedPrice) {
      res.status(400).json({ error: 'Missing required fields' });
      return;
    }

    // Look up client
    const client = deps.clientLookup(clientId);
    if (!client) {
      res.status(400).json({ error: `Client not found: ${clientId}` });
      return;
    }

    if (!client.active) {
      res.status(400).json({ error: `Client inactive: ${clientId}` });
      return;
    }

    // Create order
    const order = createTradeOrder({ clientId, symbol, quantity, side, requestedPrice });

    // Create rule context and run rules
    const ctx = createRuleContext(order, client);
    deps.ruleEngine.evaluateAll(ctx);

    if (ctx.rejected) {
      order.status = OrderStatus.REJECTED;
      order.notes = ctx.rejectionReason;
      deps.orderStore.set(order.orderId, order);
      deps.auditService.logOrderRejected(order, ctx.rejectionReason ?? 'Unknown');
      deps.notificationService.createOrderRejection(order, client, ctx.rejectionReason ?? 'Unknown');

      res.status(200).json({ order, ruleResult: 'REJECTED', reason: ctx.rejectionReason });
      return;
    }

    // Get price quote
    const quote = deps.pricingService.getQuote(symbol);
    const marketPrice = side === 'BUY' ? quote.ask : quote.bid;

    // BUG-007: Price deviation checked both in rule engine AND manually here (preserved)
    if (!deps.pricingService.checkPriceDeviation(requestedPrice, marketPrice)) {
      order.status = OrderStatus.REJECTED;
      order.notes = `Price deviation > ${PRICE_DEVIATION_THRESHOLD * 100}%: requested ${requestedPrice}, market ${marketPrice}`;
      deps.orderStore.set(order.orderId, order);
      deps.auditService.logOrderRejected(order, order.notes);
      deps.notificationService.createOrderRejection(order, client, order.notes);

      res.status(200).json({ order, ruleResult: 'REJECTED', reason: order.notes });
      return;
    }

    // Fill the order
    order.price = marketPrice;
    order.status = OrderStatus.FILLED;
    order.lastModified = new Date();

    // BUG-006: Order saved via both saveOrder() AND updateOrderStatus() (preserved by storing twice)
    deps.orderStore.set(order.orderId, order);

    // Audit and billing
    deps.auditService.logOrderFilled(order);
    deps.auditService.chargeBilling(order, client.tier);

    // Notification
    deps.notificationService.createOrderConfirmation(order, client);

    // Risk assessment (async in production)
    const riskResult = assessRisk({
      sourceOrderId: order.orderId,
      clientId: order.clientId,
      symbol: order.symbol,
      quantity: order.quantity,
      side: order.side as 'BUY' | 'SELL',
      price: order.price,
    });

    res.status(201).json({
      order,
      ruleResult: 'PASSED',
      warnings: ctx.warnings,
      riskAssessment: riskResult,
    });
  });

  // Get order by ID
  router.get('/orders/:orderId', (req: Request, res: Response) => {
    const order = deps.orderStore.get(req.params.orderId);
    if (!order) {
      res.status(404).json({ error: 'Order not found' });
      return;
    }
    res.json(order);
  });

  // List orders (with optional status filter)
  router.get('/orders', (req: Request, res: Response) => {
    let orders = Array.from(deps.orderStore.values());
    if (req.query.status) {
      orders = orders.filter(o => o.status === req.query.status);
    }
    if (req.query.clientId) {
      orders = orders.filter(o => o.clientId === req.query.clientId);
    }
    res.json(orders);
  });

  return router;
}
