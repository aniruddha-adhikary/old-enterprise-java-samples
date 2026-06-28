import express from 'express';
import cors from 'cors';
import { loadConfig } from './config';
import { Client } from './domain/Client';
import { TradeOrder } from './domain/TradeOrder';
import { RuleEngine } from './rules/RuleEngine';
import {
  LayeringDetectionRule, SpoofingPatternRule, PositionLimitRule,
  MarketHaltRule, ClientKillSwitchRule, KYCStatusRule,
  DailyVolumeLimitRule, WashTradeDetectionRule, MaxOrderValueRule,
  RestrictedSymbolRule, ClientTierRule, MarketHoursRule,
  ShortSaleRule, MultiCurrencyRule, VolumeDiscountRule,
  SpecialClientsRule, LoyaltyBonusRule,
} from './rules/impl';
import { PricingService } from './pricing/PricingService';
import { AuditService } from './audit/AuditService';
import { NotificationService } from './notifications/NotificationService';
import { createOrderRouter } from './api/OrderController';
import { createClientPortalRouter } from './api/ClientPortalApi';

// Sample client data from spec.json
const SAMPLE_CLIENTS: Client[] = [
  { clientId: 'C001', name: 'Acme Trading LLC', email: 'trading@acme.com', phone: '555-0100', tier: 'GOLD', maxOrderValue: 500000, active: true, kycStatus: 'APPROVED', killSwitch: 'N' },
  { clientId: 'C002', name: 'Henderson Capital', email: 'orders@henderson.com', phone: '555-0200', tier: 'PLATINUM', maxOrderValue: 5000000, active: true, kycStatus: 'APPROVED', killSwitch: 'N' },
  { clientId: 'C003', name: 'Smith & Associates', email: 'desk@smithassoc.com', phone: '555-0300', tier: 'SILVER', maxOrderValue: 250000, active: true, kycStatus: 'APPROVED', killSwitch: 'N' },
  { clientId: 'C004', name: 'MegaFund Inc', email: 'ops@megafund.com', phone: '555-0400', tier: 'GOLD', maxOrderValue: 1000000, active: true, kycStatus: 'APPROVED', killSwitch: 'N' },
  { clientId: 'C005', name: 'Pinnacle Investments', email: 'trade@pinnacle.com', phone: '555-0500', tier: 'BRONZE', maxOrderValue: 100000, active: true, kycStatus: 'APPROVED', killSwitch: 'N' },
  { clientId: 'C006', name: 'Global Macro Fund', email: 'globalfund@trading.com', phone: '555-0600', tier: 'PLATINUM', maxOrderValue: 10000000, active: true, kycStatus: 'APPROVED', killSwitch: 'N' },
  { clientId: 'C007', name: 'Velocity Trading LLC', email: 'trades@velocity.com', phone: '555-0700', tier: 'GOLD', maxOrderValue: 2000000, active: true, kycStatus: 'APPROVED', killSwitch: 'N' },
];

export function bootstrap() {
  const config = loadConfig();

  // Client store
  const clientMap = new Map<string, Client>();
  SAMPLE_CLIENTS.forEach(c => clientMap.set(c.clientId, c));

  // Order store
  const orderStore = new Map<string, TradeOrder>();

  // Rule Engine — register all 17 rules
  RuleEngine.resetInstance();
  const ruleEngine = RuleEngine.getInstance(config.ruleEngine);
  ruleEngine.registerRules([
    new LayeringDetectionRule(),
    new SpoofingPatternRule(),
    new PositionLimitRule(),
    new MarketHaltRule(),
    new ClientKillSwitchRule(),
    new KYCStatusRule(),
    new DailyVolumeLimitRule(),
    new WashTradeDetectionRule(),
    new MaxOrderValueRule(),
    new RestrictedSymbolRule(),
    new ClientTierRule(),
    new MarketHoursRule(),
    new ShortSaleRule(),
    new MultiCurrencyRule(),
    new VolumeDiscountRule(),
    new SpecialClientsRule(),
    new LoyaltyBonusRule(),
  ]);

  // Services
  const pricingService = new PricingService();
  const auditService = new AuditService();
  const notificationService = new NotificationService();

  // Express app
  const app = express();
  app.use(cors());
  app.use(express.json());

  // Routes
  const clientLookup = (id: string) => clientMap.get(id) ?? null;
  app.use('/api', createOrderRouter({ ruleEngine, pricingService, auditService, notificationService, clientLookup, orderStore }));
  app.use('/api', createClientPortalRouter({ clientLookup, orderStore }));

  // Health check
  app.get('/health', (_req, res) => {
    res.json({ status: 'UP', ruleCount: ruleEngine.getRules().length });
  });

  return { app, config, ruleEngine, pricingService, auditService, notificationService, clientMap, orderStore };
}

// Start server if run directly
if (require.main === module) {
  const { app, config } = bootstrap();
  app.listen(config.port, () => {
    console.log(`BigCorp Trade Order Management System running on port ${config.port}`);
  });
}
