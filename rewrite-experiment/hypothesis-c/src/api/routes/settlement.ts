import { Router, Request, Response } from 'express';
import { SettlementProcessor } from '../../settlement';

export function createSettlementRoutes(): Router {
  const router = Router();
  const processor = new SettlementProcessor();

  router.get('/calculate-date', (req: Request, res: Response) => {
    const tradeDate = req.query.tradeDate ? new Date(req.query.tradeDate as string) : new Date();
    const settlementDate = processor.calculateSettlementDate(tradeDate);
    res.json({ tradeDate: tradeDate.toISOString(), settlementDate: settlementDate.toISOString() });
  });

  return router;
}
