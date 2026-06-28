import { Router, Request, Response } from 'express';
import { Client } from '../domain/Client';
import { TradeOrder } from '../domain/TradeOrder';

// Modern equivalent of Wave 13: Client Portal API
export function createClientPortalRouter(deps: {
  clientLookup: (clientId: string) => Client | null;
  orderStore: Map<string, TradeOrder>;
}): Router {
  const router = Router();

  // Authentication middleware (simplified from original)
  router.use((req: Request, res: Response, next) => {
    const clientId = req.headers['x-client-id'] as string;
    if (!clientId) {
      res.status(401).json({ error: 'Missing X-Client-Id header' });
      return;
    }
    const client = deps.clientLookup(clientId);
    if (!client) {
      res.status(403).json({ error: 'Client not found' });
      return;
    }
    (req as unknown as Record<string, unknown>)['authenticatedClient'] = client;
    next();
  });

  // Get orders for authenticated client
  router.get('/portal/orders', (req: Request, res: Response) => {
    const client = (req as unknown as Record<string, unknown>)['authenticatedClient'] as Client;
    const orders = Array.from(deps.orderStore.values())
      .filter(o => o.clientId === client.clientId);
    res.json(orders);
  });

  // Get client balance (simplified)
  router.get('/portal/balance', (req: Request, res: Response) => {
    const client = (req as unknown as Record<string, unknown>)['authenticatedClient'] as Client;
    const orders = Array.from(deps.orderStore.values())
      .filter(o => o.clientId === client.clientId && o.status === 'FILLED');

    const totalValue = orders.reduce((sum, o) => sum + (o.quantity * o.price), 0);
    res.json({
      clientId: client.clientId,
      filledOrderCount: orders.length,
      totalTradeValue: totalValue,
      maxOrderValue: client.maxOrderValue,
    });
  });

  // Get order status as JSON
  router.get('/portal/orders/:orderId', (req: Request, res: Response) => {
    const client = (req as unknown as Record<string, unknown>)['authenticatedClient'] as Client;
    const order = deps.orderStore.get(req.params.orderId);

    if (!order || order.clientId !== client.clientId) {
      res.status(404).json({ error: 'Order not found' });
      return;
    }

    res.json({
      orderId: order.orderId,
      symbol: order.symbol,
      quantity: order.quantity,
      side: order.side,
      price: order.price,
      status: order.status,
      orderDate: order.orderDate,
    });
  });

  return router;
}
