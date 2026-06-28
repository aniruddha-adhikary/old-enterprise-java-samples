import express from 'express';
import { loadConfig } from '../config';
import { createOrderRoutes } from './routes/orders';
import { createClientRoutes } from './routes/clients';
import { createSettlementRoutes } from './routes/settlement';
import { createPricingRoutes } from './routes/pricing';
import { createRiskRoutes } from './routes/risk';
import { createDerivativeRoutes } from './routes/derivatives';
import { createReportRoutes } from './routes/reports';
import { createDefaultRuleEngine } from '../rules';

const config = loadConfig();

export function createApp() {
  const app = express();
  app.use(express.json());

  const ruleEngine = createDefaultRuleEngine(config.rules.priorityFixed);

  app.use('/api/orders', createOrderRoutes(ruleEngine));
  app.use('/api/clients', createClientRoutes());
  app.use('/api/settlement', createSettlementRoutes());
  app.use('/api/pricing', createPricingRoutes());
  app.use('/api/risk', createRiskRoutes());
  app.use('/api/derivatives', createDerivativeRoutes());
  app.use('/api/reports', createReportRoutes());

  app.get('/health', (_req, res) => {
    res.json({ status: 'ok', version: '2.0.0', timestamp: new Date().toISOString() });
  });

  return app;
}

if (require.main === module) {
  const app = createApp();
  app.listen(config.port, () => {
    console.log(`BigCorp Trade Order Management v2.0.0 running on port ${config.port}`);
  });
}
