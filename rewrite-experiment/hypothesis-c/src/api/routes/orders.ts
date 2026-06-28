import { Router, Request, Response } from 'express';
import { v4 as uuid } from 'uuid';
import { createTradeOrder, OrderSide, OrderStatus, TradeOrder } from '../../domain/trade-order';
import { Client, ClientTier } from '../../domain/client';
import { RuleEngine, createRuleContext } from '../../rules';
import { PricingService } from '../../pricing';
import { CommissionCalculator } from '../../pricing/commission-calculator';
import { AuditService } from '../../audit';

export function createOrderRoutes(ruleEngine: RuleEngine): Router {
  const router = Router();
  const pricingService = new PricingService();
  const auditService = new AuditService();

  // In-memory order store (would be DB in production)
  const orders = new Map<string, TradeOrder>();
  const clients = new Map<string, Client>();

  // Seed some default clients
  const defaultClients: Client[] = [
    { clientId: 'C001', name: 'Acme Trading LLC', email: 'trading@acme.com', phone: '555-0100', tier: ClientTier.GOLD, maxOrderValue: 500000, active: true, createdDate: new Date() },
    { clientId: 'C002', name: 'Henderson Capital', email: 'orders@henderson.com', phone: '555-0200', tier: ClientTier.PLATINUM, maxOrderValue: 5000000, active: true, createdDate: new Date() },
    { clientId: 'C003', name: 'Smith & Associates', email: 'desk@smithassoc.com', phone: '555-0300', tier: ClientTier.SILVER, maxOrderValue: 250000, active: true, createdDate: new Date() },
    { clientId: 'C004', name: 'MegaFund Inc', email: 'ops@megafund.com', phone: '555-0400', tier: ClientTier.GOLD, maxOrderValue: 1000000, active: true, createdDate: new Date() },
    { clientId: 'C005', name: 'Pinnacle Investments', email: 'trade@pinnacle.com', phone: '555-0500', tier: ClientTier.BRONZE, maxOrderValue: 100000, active: true, createdDate: new Date() },
  ];
  for (const c of defaultClients) {
    clients.set(c.clientId, c);
  }

  // POST /api/orders - Submit a new order
  router.post('/', (req: Request, res: Response) => {
    const { clientId, symbol, quantity, side, requestedPrice } = req.body;

    if (!clientId || !symbol || !quantity || !side) {
      res.status(400).json({ error: 'Missing required fields: clientId, symbol, quantity, side' });
      return;
    }

    const client = clients.get(clientId);
    if (!client) {
      res.status(404).json({ error: `Client not found: ${clientId}` });
      return;
    }

    if (!client.active) {
      res.status(400).json({ error: `Client ${clientId} is inactive` });
      return;
    }

    const order = createTradeOrder({
      orderId: `ORD-${uuid().slice(0, 8)}`,
      clientId,
      symbol: symbol.toUpperCase(),
      quantity: Number(quantity),
      side: side.toUpperCase() as OrderSide,
      requestedPrice: Number(requestedPrice || 0),
    });

    // Run rule engine
    const ctx = createRuleContext(order, client);
    const { passed, log } = ruleEngine.evaluate(ctx);

    if (!passed) {
      order.status = OrderStatus.REJECTED;
      order.notes = ctx.rejectionReason;
      orders.set(order.orderId, order);
      auditService.logOrderEvent(order, 'ORDER_REJECTED');

      res.status(200).json({
        order,
        ruleEvaluation: { passed: false, reason: ctx.rejectionReason, log },
        messages: ctx.messages,
        warnings: ctx.warnings,
      });
      return;
    }

    // Pricing
    order.status = OrderStatus.VALIDATED;
    const quote = pricingService.getQuote(order.symbol);
    if (quote) {
      const tierQuote = pricingService.applyTierSpread(quote, client.tier);
      order.price = order.side === OrderSide.BUY ? tierQuote.ask : tierQuote.bid;
    } else {
      order.price = order.requestedPrice;
    }

    order.status = OrderStatus.FILLED;
    order.lastModified = new Date();

    // Commission
    const orderValue = order.quantity * order.price;
    const commission = CommissionCalculator.calculate(orderValue, client.tier);

    orders.set(order.orderId, order);
    auditService.logOrderEvent(order, 'ORDER_FILLED');
    auditService.createBillingEntry(order, client.tier);

    res.status(201).json({
      order,
      ruleEvaluation: { passed: true, log },
      commission,
      messages: ctx.messages,
      warnings: ctx.warnings,
      attributes: Object.fromEntries(ctx.attributes),
    });
  });

  // GET /api/orders - List orders
  router.get('/', (_req: Request, res: Response) => {
    res.json(Array.from(orders.values()));
  });

  // GET /api/orders/:id - Get order
  router.get('/:id', (req: Request, res: Response) => {
    const order = orders.get(req.params.id);
    if (!order) {
      res.status(404).json({ error: 'Order not found' });
      return;
    }
    res.json(order);
  });

  return router;
}
