import { Router, Request, Response } from 'express';
import { DerivativeProcessor } from '../../derivatives';
import { DerivativeOrder, ContractType, DerivativeStatus } from '../../domain/derivative';
import { v4 as uuid } from 'uuid';

export function createDerivativeRoutes(): Router {
  const router = Router();
  const processor = new DerivativeProcessor();

  router.post('/process', (req: Request, res: Response) => {
    const { clientId, contractType, underlying, strikePrice, quantity, expiry } = req.body;

    const order: DerivativeOrder = {
      orderId: `DRV-${uuid().slice(0, 8)}`,
      clientId: clientId || '',
      contractType: (contractType as ContractType) || ContractType.FX_SPOT,
      underlying: underlying || '',
      strikePrice: Number(strikePrice || 0),
      quantity: Number(quantity || 0),
      expiry: expiry || null,
      status: DerivativeStatus.NEW,
      premium: 0,
    };

    const result = processor.processOrder(order);
    res.json(result);
  });

  return router;
}
