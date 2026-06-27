export { RuleEngine, RuleContext, Rule, RuleEvaluationLog, createRuleContext, rejectContext } from './rule-engine';
export { MarketHaltRule } from './impl/market-halt';
export { ClientKillSwitchRule } from './impl/client-kill-switch';
export { KYCStatusRule } from './impl/kyc-status';
export { PositionLimitRule } from './impl/position-limit';
export { LayeringDetectionRule } from './impl/layering-detection';
export { SpoofingPatternRule } from './impl/spoofing-pattern';
export { DailyVolumeLimitRule } from './impl/daily-volume-limit';
export { WashTradeDetectionRule } from './impl/wash-trade-detection';
export { MaxOrderValueRule } from './impl/max-order-value';
export { RestrictedSymbolRule } from './impl/restricted-symbol';
export { ClientTierRule } from './impl/client-tier';
export { MarketHoursRule } from './impl/market-hours';
export { ShortSaleRule } from './impl/short-sale';
export { MultiCurrencyRule } from './impl/multi-currency';
export { VolumeDiscountRule } from './impl/volume-discount';
export { SpecialClientsRule } from './impl/special-clients';
export { LoyaltyBonusRule } from './impl/loyalty-bonus';

import { RuleEngine } from './rule-engine';
import { MarketHaltRule } from './impl/market-halt';
import { ClientKillSwitchRule } from './impl/client-kill-switch';
import { KYCStatusRule } from './impl/kyc-status';
import { PositionLimitRule } from './impl/position-limit';
import { LayeringDetectionRule } from './impl/layering-detection';
import { SpoofingPatternRule } from './impl/spoofing-pattern';
import { DailyVolumeLimitRule } from './impl/daily-volume-limit';
import { WashTradeDetectionRule } from './impl/wash-trade-detection';
import { MaxOrderValueRule } from './impl/max-order-value';
import { RestrictedSymbolRule } from './impl/restricted-symbol';
import { ClientTierRule } from './impl/client-tier';
import { MarketHoursRule } from './impl/market-hours';
import { ShortSaleRule } from './impl/short-sale';
import { MultiCurrencyRule } from './impl/multi-currency';
import { VolumeDiscountRule } from './impl/volume-discount';
import { SpecialClientsRule } from './impl/special-clients';
import { LoyaltyBonusRule } from './impl/loyalty-bonus';

/**
 * Create a fully-configured rule engine with all 17 business rules.
 * Mirrors the original config/rules.xml registration order and priorities.
 */
export function createDefaultRuleEngine(priorityFixed = false): RuleEngine {
  const engine = new RuleEngine(priorityFixed);

  // Circuit breaker / kill switch (REG-2011-001/002)
  engine.addRule(new MarketHaltRule());
  engine.addRule(new ClientKillSwitchRule());

  // Surveillance rules (REG-2015-001/002/003)
  engine.addRule(new LayeringDetectionRule());
  engine.addRule(new SpoofingPatternRule());
  engine.addRule(new PositionLimitRule());

  // Compliance rules (post-2005)
  engine.addRule(new KYCStatusRule());
  engine.addRule(new DailyVolumeLimitRule());
  engine.addRule(new WashTradeDetectionRule());

  // Original business rules
  engine.addRule(new MaxOrderValueRule());
  engine.addRule(new RestrictedSymbolRule());
  engine.addRule(new ClientTierRule());
  engine.addRule(new MarketHoursRule());
  engine.addRule(new ShortSaleRule());

  // Multi-currency (JIRA-7100)
  engine.addRule(new MultiCurrencyRule());
  engine.addRule(new VolumeDiscountRule());
  engine.addRule(new SpecialClientsRule());
  engine.addRule(new LoyaltyBonusRule());

  return engine;
}
