import express from 'express';
import { loadConfig } from './config';
import { createRouter, ApiDependencies } from './api/routes';
import { RuleEngine } from './rules/RuleEngine';
import { PricingService } from './pricing/PricingService';
import { InMemoryMessageBroker } from './integration/MessageBroker';
import { DerivativeProcessor } from './derivatives/DerivativeProcessor';
import { ExposureCalculator } from './risk/ExposureCalculator';
import { RuleAuditService } from './audit/AuditService';
import { Client } from './domain/Client';
import { OrderStatus, ClientTier, KycStatus } from './domain/enums';
import { TradeOrder } from './domain/TradeOrder';

// Import all rules
import { LayeringDetectionRule } from './rules/LayeringDetectionRule';
import { SpoofingPatternRule } from './rules/SpoofingPatternRule';
import { PositionLimitRule } from './rules/PositionLimitRule';
import { MarketHaltRule } from './rules/MarketHaltRule';
import { ClientKillSwitchRule } from './rules/ClientKillSwitchRule';
import { KYCStatusRule } from './rules/KYCStatusRule';
import { DailyVolumeLimitRule } from './rules/DailyVolumeLimitRule';
import { WashTradeDetectionRule } from './rules/WashTradeDetectionRule';
import { MaxOrderValueRule } from './rules/MaxOrderValueRule';
import { RestrictedSymbolRule } from './rules/RestrictedSymbolRule';
import { ClientTierRule } from './rules/ClientTierRule';
import { MarketHoursRule } from './rules/MarketHoursRule';
import { ShortSaleRule } from './rules/ShortSaleRule';
import { MultiCurrencyRule } from './rules/MultiCurrencyRule';
import { VolumeDiscountRule } from './rules/VolumeDiscountRule';
import { SpecialClientsRule } from './rules/SpecialClientsRule';
import { LoyaltyBonusRule } from './rules/LoyaltyBonusRule';

const config = loadConfig();

// In-memory stores for standalone mode
const orders = new Map<string, TradeOrder>();
const clients = new Map<string, Client>();

// Seed sample clients
const sampleClients: Client[] = [
  { clientId: 'C001', clientName: 'Acme Trading LLC', email: 'acme@example.com', phone: '555-0001', tier: ClientTier.GOLD, maxOrderValue: 500000, active: true, kycStatus: KycStatus.APPROVED, killSwitch: 'N', createdDate: new Date() },
  { clientId: 'C002', clientName: 'Henderson Capital', email: 'henderson@example.com', phone: '555-0002', tier: ClientTier.PLATINUM, maxOrderValue: 5000000, active: true, kycStatus: KycStatus.APPROVED, killSwitch: 'N', createdDate: new Date() },
  { clientId: 'C003', clientName: 'Smith & Associates', email: 'smith@example.com', phone: '555-0003', tier: ClientTier.SILVER, maxOrderValue: 250000, active: true, kycStatus: KycStatus.APPROVED, killSwitch: 'N', createdDate: new Date() },
  { clientId: 'C004', clientName: 'MegaFund Inc', email: 'mega@example.com', phone: '555-0004', tier: ClientTier.GOLD, maxOrderValue: 1000000, active: true, kycStatus: KycStatus.APPROVED, killSwitch: 'N', createdDate: new Date() },
  { clientId: 'C005', clientName: 'Pinnacle Investments', email: 'pinnacle@example.com', phone: '555-0005', tier: ClientTier.BRONZE, maxOrderValue: 100000, active: true, kycStatus: KycStatus.APPROVED, killSwitch: 'N', createdDate: new Date() },
  { clientId: 'C006', clientName: 'Global Macro Fund', email: 'global@example.com', phone: '555-0006', tier: ClientTier.PLATINUM, maxOrderValue: 10000000, active: true, kycStatus: KycStatus.APPROVED, killSwitch: 'N', createdDate: new Date() },
  { clientId: 'C007', clientName: 'Velocity Trading LLC', email: 'velocity@example.com', phone: '555-0007', tier: ClientTier.GOLD, maxOrderValue: 2000000, active: true, kycStatus: KycStatus.APPROVED, killSwitch: 'N', createdDate: new Date() },
];
for (const c of sampleClients) clients.set(c.clientId, c);

// In-memory repositories
const noopAuditLogger: RuleAuditService = new RuleAuditService({
  saveAuditEvent: async () => {},
  saveBillingEntry: async () => {},
  saveRuleAuditEntry: async () => {},
  saveSurveillanceAuditEntry: async () => {},
  getClient: async (id) => clients.get(id) || null,
});

const orderRepo = {
  countNonCancelledOrders: async () => 0,
  getCancelledOrderCount: async () => 0,
  getTotalOrderCount: async () => 0,
  findRecentOppositeOrders: async () => 0,
};

const positionRepo = {
  getNetPosition: async () => null as number | null,
};

const clientRepo = {
  getKillSwitch: async (clientId: string) => clients.get(clientId)?.killSwitch || 'N',
  getKycStatus: async (clientId: string) => clients.get(clientId)?.kycStatus || null,
};

// Build rule engine with all 17 rules
const ruleEngine = new RuleEngine(
  { priorityFixed: config.rules.priorityFixed },
  noopAuditLogger
);

ruleEngine.registerRules([
  new LayeringDetectionRule(orderRepo, noopAuditLogger),
  new SpoofingPatternRule(orderRepo, noopAuditLogger),
  new PositionLimitRule(positionRepo, noopAuditLogger),
  new MarketHaltRule(() => config.market.halted),
  new ClientKillSwitchRule(clientRepo),
  new KYCStatusRule(clientRepo),
  new DailyVolumeLimitRule(),
  new WashTradeDetectionRule(orderRepo),
  new MaxOrderValueRule(),
  new RestrictedSymbolRule(),
  new ClientTierRule(),
  new MarketHoursRule(),
  new MultiCurrencyRule(),
  new VolumeDiscountRule(),
  new SpecialClientsRule(config.specialClients),
  new LoyaltyBonusRule(),
]);

// ShortSaleRule added manually after config-loaded rules (FR-RUL-009)
ruleEngine.registerRule(new ShortSaleRule());

const pricingService = new PricingService();
const messageBroker = new InMemoryMessageBroker();

const derivativeProcessor = new DerivativeProcessor({
  saveDerivativeOrder: async () => {},
});

const exposureCalculator = new ExposureCalculator({
  saveRiskAssessment: async () => {},
  findPendingOrders: async () => [],
});

const deps: ApiDependencies = {
  ruleEngine,
  pricingService,
  messageBroker,
  derivativeProcessor,
  exposureCalculator,
  getClient: async (clientId: string) => clients.get(clientId) || null,
  saveOrder: async (order) => {
    orders.set(order.orderId, order);
  },
  updateOrderStatus: async (orderId: string, status: OrderStatus) => {
    const order = orders.get(orderId);
    if (order) {
      order.status = status;
      order.lastModified = new Date();
    }
  },
};

const app = express();
app.use(express.json());
app.use('/api', createRouter(deps));

const port = config.server.port;

if (require.main === module) {
  messageBroker.connect().then(() => {
    app.listen(port, () => {
      console.log(`BigCorp Trade OMS running on port ${port}`);
      console.log(`Rules loaded: ${ruleEngine.getRules().length}`);
      console.log(`Priority sort: ${config.rules.priorityFixed ? 'ASCENDING (fixed)' : 'DESCENDING (legacy)'}`);
    });
  });
}

export { app, ruleEngine, pricingService };
