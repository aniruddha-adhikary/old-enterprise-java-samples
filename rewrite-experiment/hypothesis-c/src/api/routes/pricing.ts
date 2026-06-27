import { Router, Request, Response } from 'express';
import { PricingService } from '../../pricing';
import { CommissionCalculator } from '../../pricing/commission-calculator';

export function createPricingRoutes(): Router {
  const router = Router();
  const pricingService = new PricingService();

  router.get('/quote/:symbol', (req: Request, res: Response) => {
    const quote = pricingService.getQuote(req.params.symbol.toUpperCase());
    if (!quote) {
      res.status(404).json({ error: `No quote available for ${req.params.symbol}` });
      return;
    }
    res.json(quote);
  });

  router.get('/commission', (req: Request, res: Response) => {
    const amount = Number(req.query.amount || 0);
    const tier = (req.query.tier as string) || 'BRONZE';
    const commission = CommissionCalculator.calculate(amount, tier);
    const rate = CommissionCalculator.getRate(tier);
    res.json({ amount, tier, rate, commission });
  });

  return router;
}
