import { Router, Request, Response } from 'express';
import { ExposureCalculator } from '../../risk';
import { RiskAssessment, RiskStatus } from '../../domain/risk';
import { v4 as uuid } from 'uuid';

export function createRiskRoutes(): Router {
  const router = Router();

  router.post('/assess', (req: Request, res: Response) => {
    const { sourceOrderId, clientId, symbol, quantity, side, price } = req.body;

    const assessment: RiskAssessment = {
      riskOrderId: `RISK-${uuid().slice(0, 8)}`,
      sourceOrderId: sourceOrderId || '',
      clientId: clientId || '',
      symbol: symbol || '',
      quantity: Number(quantity || 0),
      side: side || 'BUY',
      price: Number(price || 0),
      notionalValue: 0,
      exposureContribution: 0,
      varContribution: 0,
      riskStatus: RiskStatus.PENDING,
      assessmentDate: new Date(),
    };

    ExposureCalculator.calculateRisk(assessment);
    res.json(assessment);
  });

  return router;
}
