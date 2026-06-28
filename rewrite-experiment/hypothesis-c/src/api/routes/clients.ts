import { Router, Request, Response } from 'express';
import { Client, ClientTier } from '../../domain/client';

export function createClientRoutes(): Router {
  const router = Router();

  const clients = new Map<string, Client>();

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

  router.get('/', (_req: Request, res: Response) => {
    res.json(Array.from(clients.values()));
  });

  router.get('/:id', (req: Request, res: Response) => {
    const client = clients.get(req.params.id);
    if (!client) {
      res.status(404).json({ error: 'Client not found' });
      return;
    }
    res.json(client);
  });

  return router;
}
