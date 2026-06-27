import { Router, Request, Response } from 'express';
import { RegulatoryExport } from '../../regulatory';

export function createReportRoutes(): Router {
  const router = Router();
  const regExport = new RegulatoryExport();

  router.get('/regulatory/sample', (_req: Request, res: Response) => {
    const sampleReport = regExport.generateXmlReport([]);
    res.type('application/xml').send(sampleReport);
  });

  return router;
}
