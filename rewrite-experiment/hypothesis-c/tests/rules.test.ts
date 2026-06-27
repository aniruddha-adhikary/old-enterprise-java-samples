import { RuleEngine, createRuleContext, RuleContext } from '../src/rules/rule-engine';
import { MarketHaltRule } from '../src/rules/impl/market-halt';
import { ClientKillSwitchRule } from '../src/rules/impl/client-kill-switch';
import { KYCStatusRule } from '../src/rules/impl/kyc-status';
import { PositionLimitRule } from '../src/rules/impl/position-limit';
import { LayeringDetectionRule } from '../src/rules/impl/layering-detection';
import { SpoofingPatternRule } from '../src/rules/impl/spoofing-pattern';
import { DailyVolumeLimitRule } from '../src/rules/impl/daily-volume-limit';
import { WashTradeDetectionRule } from '../src/rules/impl/wash-trade-detection';
import { MaxOrderValueRule } from '../src/rules/impl/max-order-value';
import { RestrictedSymbolRule } from '../src/rules/impl/restricted-symbol';
import { ClientTierRule } from '../src/rules/impl/client-tier';
import { MarketHoursRule } from '../src/rules/impl/market-hours';
import { ShortSaleRule } from '../src/rules/impl/short-sale';
import { MultiCurrencyRule } from '../src/rules/impl/multi-currency';
import { VolumeDiscountRule } from '../src/rules/impl/volume-discount';
import { SpecialClientsRule } from '../src/rules/impl/special-clients';
import { LoyaltyBonusRule } from '../src/rules/impl/loyalty-bonus';
import { createDefaultRuleEngine } from '../src/rules';
import { createTradeOrder, OrderSide, OrderStatus } from '../src/domain/trade-order';
import { Client, ClientTier } from '../src/domain/client';

function makeOrder(overrides: Partial<ReturnType<typeof createTradeOrder>> = {}) {
  return createTradeOrder({
    orderId: 'ORD-001',
    clientId: 'C001',
    symbol: 'MSFT',
    quantity: 100,
    side: OrderSide.BUY,
    requestedPrice: 25.00,
    ...overrides,
  });
}

function makeClient(overrides: Partial<Client> = {}): Client {
  return {
    clientId: 'C001',
    name: 'Test Client',
    email: 'test@test.com',
    phone: '555-0001',
    tier: ClientTier.GOLD,
    maxOrderValue: 500000,
    active: true,
    createdDate: new Date(),
    ...overrides,
  };
}

function makeCtx(orderOverrides = {}, clientOverrides = {}): RuleContext {
  return createRuleContext(makeOrder(orderOverrides), makeClient(clientOverrides));
}

describe('RuleEngine', () => {
  it('should evaluate all active rules', () => {
    const engine = new RuleEngine();
    engine.addRule(new ClientTierRule());
    engine.addRule(new VolumeDiscountRule());

    const ctx = makeCtx();
    const { passed } = engine.evaluate(ctx);
    expect(passed).toBe(true);
  });

  it('should stop on rejection', () => {
    const engine = new RuleEngine();
    engine.addRule(new RestrictedSymbolRule());
    engine.addRule(new ClientTierRule());

    const ctx = makeCtx({ symbol: 'ENRN' });
    const { passed, log } = engine.evaluate(ctx);
    expect(passed).toBe(false);
    expect(ctx.rejectionReason).toContain('restricted');
  });

  it('should sort by DESCENDING priority by default (bug preserved)', () => {
    const engine = new RuleEngine(false);
    engine.addRule(new LoyaltyBonusRule());    // priority 45
    engine.addRule(new MarketHoursRule());     // priority 80
    engine.addRule(new ClientTierRule());       // priority 90

    const ctx = makeCtx();
    const { log } = engine.evaluate(ctx);
    // Higher priority number runs first (descending)
    expect(log[0].ruleName).toBe('ClientTierRule');      // 90
    expect(log[1].ruleName).toBe('MarketHoursRule');      // 80
    expect(log[2].ruleName).toBe('LoyaltyBonusRule');     // 45
  });

  it('should sort ASCENDING when priorityFixed is true', () => {
    const engine = new RuleEngine(true);
    engine.addRule(new LoyaltyBonusRule());    // priority 45
    engine.addRule(new MarketHoursRule());     // priority 80
    engine.addRule(new ClientTierRule());       // priority 90

    const ctx = makeCtx();
    const { log } = engine.evaluate(ctx);
    expect(log[0].ruleName).toBe('LoyaltyBonusRule');     // 45
    expect(log[1].ruleName).toBe('MarketHoursRule');      // 80
    expect(log[2].ruleName).toBe('ClientTierRule');        // 90
  });

  it('should create engine with all 17 rules', () => {
    const engine = createDefaultRuleEngine();
    expect(engine.getRuleCount()).toBe(17);
  });
});

describe('MarketHaltRule (priority 120)', () => {
  it('should reject all orders when market is halted', () => {
    const rule = new MarketHaltRule(() => true);
    const ctx = makeCtx();
    expect(rule.evaluate(ctx)).toBe(false);
    expect(ctx.rejected).toBe(true);
    expect(ctx.rejectionReason).toContain('Market is halted');
  });

  it('should pass when market is not halted', () => {
    const rule = new MarketHaltRule(() => false);
    const ctx = makeCtx();
    expect(rule.evaluate(ctx)).toBe(true);
    expect(ctx.rejected).toBe(false);
  });
});

describe('ClientKillSwitchRule (priority 118)', () => {
  it('should reject orders for killed clients', () => {
    const rule = new ClientKillSwitchRule(new Set(['C001']));
    const ctx = makeCtx();
    expect(rule.evaluate(ctx)).toBe(false);
    expect(ctx.rejected).toBe(true);
    expect(ctx.rejectionReason).toContain('kill-switched');
  });

  it('should pass orders for non-killed clients', () => {
    const rule = new ClientKillSwitchRule(new Set(['C999']));
    const ctx = makeCtx();
    expect(rule.evaluate(ctx)).toBe(true);
  });
});

describe('KYCStatusRule (priority 115)', () => {
  it('should reject when KYC is not approved', () => {
    const rule = new KYCStatusRule(() => 'PENDING');
    const ctx = makeCtx();
    expect(rule.evaluate(ctx)).toBe(false);
    expect(ctx.rejectionReason).toContain('KYC');
  });

  it('should pass when KYC is approved', () => {
    const rule = new KYCStatusRule(() => 'APPROVED');
    const ctx = makeCtx();
    expect(rule.evaluate(ctx)).toBe(true);
  });
});

describe('PositionLimitRule (priority 123)', () => {
  it('should reject when position exceeds 100,000 shares', () => {
    const rule = new PositionLimitRule(() => 95000);
    const ctx = makeCtx({ quantity: 6000 });
    expect(rule.evaluate(ctx)).toBe(false);
    expect(ctx.rejectionReason).toContain('Position limit exceeded');
  });

  it('should pass when within limit', () => {
    const rule = new PositionLimitRule(() => 50000);
    const ctx = makeCtx({ quantity: 1000 });
    expect(rule.evaluate(ctx)).toBe(true);
  });

  it('should handle new positions', () => {
    const rule = new PositionLimitRule(() => 0);
    const ctx = makeCtx({ quantity: 1000 });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('position_status')).toBe('NEW_POSITION');
  });
});

describe('LayeringDetectionRule (priority 125)', () => {
  it('should flag when >5 recent orders for same client+symbol', () => {
    const rule = new LayeringDetectionRule(() => 8);
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('layering_status')).toBe('FLAGGED');
    expect(ctx.warnings.length).toBeGreaterThan(0);
  });

  it('should not flag when <=5 orders', () => {
    const rule = new LayeringDetectionRule(() => 3);
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('layering_status')).toBe('CLEAR');
  });
});

describe('SpoofingPatternRule (priority 124)', () => {
  it('should flag when >60% cancellation rate', () => {
    const rule = new SpoofingPatternRule(() => ({ totalOrders: 10, cancelledOrders: 7 }));
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('spoofing_status')).toBe('FLAGGED');
  });

  it('should not flag when <=60% cancellation rate', () => {
    const rule = new SpoofingPatternRule(() => ({ totalOrders: 10, cancelledOrders: 5 }));
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('spoofing_status')).toBe('CLEAR');
  });

  it('should handle no history', () => {
    const rule = new SpoofingPatternRule(() => null);
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('spoofing_status')).toBe('NO_HISTORY');
  });
});

describe('DailyVolumeLimitRule (priority 110)', () => {
  it('should reject orders exceeding 50,000 shares', () => {
    const rule = new DailyVolumeLimitRule();
    const ctx = makeCtx({ quantity: 51000 });
    expect(rule.evaluate(ctx)).toBe(false);
    expect(ctx.rejectionReason).toContain('daily volume limit');
  });

  it('should pass orders within limit', () => {
    const rule = new DailyVolumeLimitRule();
    const ctx = makeCtx({ quantity: 50000 });
    expect(rule.evaluate(ctx)).toBe(true);
  });
});

describe('WashTradeDetectionRule (priority 105)', () => {
  it('should reject when opposite side trade exists within 5 minutes', () => {
    const rule = new WashTradeDetectionRule(() => [
      makeOrder({ side: OrderSide.SELL, orderDate: new Date() }),
    ]);
    const ctx = makeCtx({ side: OrderSide.BUY });
    expect(rule.evaluate(ctx)).toBe(false);
    expect(ctx.rejectionReason).toContain('wash trade');
  });

  it('should pass when no opposite side trade exists', () => {
    const rule = new WashTradeDetectionRule(() => []);
    const ctx = makeCtx();
    expect(rule.evaluate(ctx)).toBe(true);
  });
});

describe('MaxOrderValueRule (priority 100)', () => {
  it('should reject when order value exceeds limit with buffer', () => {
    const rule = new MaxOrderValueRule();
    // maxOrderValue = 500000, buffer = 1.10, so limit = 550000
    // quantity=10000 * price=60 = 600000 > 550000
    const ctx = makeCtx({ quantity: 10000, requestedPrice: 60 });
    expect(rule.evaluate(ctx)).toBe(false);
  });

  it('should pass when within limit', () => {
    const rule = new MaxOrderValueRule();
    const ctx = makeCtx({ quantity: 100, requestedPrice: 25 });
    expect(rule.evaluate(ctx)).toBe(true);
  });

  it('should use 1.10 buffer (JIRA-1892)', () => {
    expect(MaxOrderValueRule.BUFFER).toBe(1.10);
  });
});

describe('RestrictedSymbolRule (priority 95)', () => {
  it('should reject ENRN', () => {
    const rule = new RestrictedSymbolRule();
    const ctx = makeCtx({ symbol: 'ENRN' });
    expect(rule.evaluate(ctx)).toBe(false);
  });

  it('should reject WCOM', () => {
    const rule = new RestrictedSymbolRule();
    const ctx = makeCtx({ symbol: 'WCOM' });
    expect(rule.evaluate(ctx)).toBe(false);
  });

  it('should reject TYCO', () => {
    const rule = new RestrictedSymbolRule();
    const ctx = makeCtx({ symbol: 'TYCO' });
    expect(rule.evaluate(ctx)).toBe(false);
  });

  it('should reject ADLP', () => {
    const rule = new RestrictedSymbolRule();
    const ctx = makeCtx({ symbol: 'ADLP' });
    expect(rule.evaluate(ctx)).toBe(false);
  });

  it('should pass valid symbols', () => {
    const rule = new RestrictedSymbolRule();
    const ctx = makeCtx({ symbol: 'MSFT' });
    expect(rule.evaluate(ctx)).toBe(true);
  });
});

describe('ClientTierRule (priority 90)', () => {
  it('should set HIGH priority for PLATINUM clients', () => {
    const rule = new ClientTierRule();
    const ctx = makeCtx({}, { tier: ClientTier.PLATINUM });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('priority')).toBe('HIGH');
  });

  it('should set HIGH priority for GOLD clients', () => {
    const rule = new ClientTierRule();
    const ctx = makeCtx({}, { tier: ClientTier.GOLD });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('priority')).toBe('HIGH');
  });

  it('should set NORMAL priority for SILVER clients', () => {
    const rule = new ClientTierRule();
    const ctx = makeCtx({}, { tier: ClientTier.SILVER });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('priority')).toBe('NORMAL');
  });

  it('should set NORMAL priority for BRONZE clients', () => {
    const rule = new ClientTierRule();
    const ctx = makeCtx({}, { tier: ClientTier.BRONZE });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('priority')).toBe('NORMAL');
  });
});

describe('MarketHoursRule (priority 80)', () => {
  it('should queue orders before market open (9:30 AM)', () => {
    const rule = new MarketHoursRule(() => new Date('2024-01-15T09:00:00'));
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('queued')).toBe(true);
  });

  it('should pass orders during market hours', () => {
    const rule = new MarketHoursRule(() => new Date('2024-01-15T12:00:00'));
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('queued')).toBeUndefined();
  });

  it('should queue orders after market close (4:00 PM)', () => {
    const rule = new MarketHoursRule(() => new Date('2024-01-15T16:30:00'));
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('queued')).toBe(true);
  });
});

describe('ShortSaleRule (priority 75)', () => {
  it('should reject SELL orders > 1000 shares', () => {
    const rule = new ShortSaleRule();
    const ctx = makeCtx({ side: OrderSide.SELL, quantity: 1500 });
    expect(rule.evaluate(ctx)).toBe(false);
    expect(ctx.rejectionReason).toContain('Short sale limit');
  });

  it('should pass SELL orders <= 1000 shares', () => {
    const rule = new ShortSaleRule();
    const ctx = makeCtx({ side: OrderSide.SELL, quantity: 500 });
    expect(rule.evaluate(ctx)).toBe(true);
  });

  it('should pass BUY orders regardless of quantity', () => {
    const rule = new ShortSaleRule();
    const ctx = makeCtx({ side: OrderSide.BUY, quantity: 50000 });
    expect(rule.evaluate(ctx)).toBe(true);
  });

  it('should have limit of 1000', () => {
    expect(ShortSaleRule.SHORT_SALE_LIMIT).toBe(1000);
  });
});

describe('MultiCurrencyRule (priority 60)', () => {
  it('should apply EUR rate of 1.10', () => {
    const rule = new MultiCurrencyRule();
    const ctx = makeCtx();
    ctx.attributes.set('currency', 'EUR');
    rule.evaluate(ctx);
    expect(ctx.attributes.get('fx_rate_applied')).toBe('1.1');
  });

  it('should apply GBP rate of 1.55', () => {
    const rule = new MultiCurrencyRule();
    const ctx = makeCtx();
    ctx.attributes.set('currency', 'GBP');
    rule.evaluate(ctx);
    expect(ctx.attributes.get('fx_rate_applied')).toBe('1.55');
  });

  it('should apply JPY rate of 0.009', () => {
    const rule = new MultiCurrencyRule();
    const ctx = makeCtx();
    ctx.attributes.set('currency', 'JPY');
    rule.evaluate(ctx);
    expect(ctx.attributes.get('fx_rate_applied')).toBe('0.009');
  });

  it('should apply CHF rate of 0.72', () => {
    const rule = new MultiCurrencyRule();
    const ctx = makeCtx();
    ctx.attributes.set('currency', 'CHF');
    rule.evaluate(ctx);
    expect(ctx.attributes.get('fx_rate_applied')).toBe('0.72');
  });

  it('should default to 1.0 for USD', () => {
    const rule = new MultiCurrencyRule();
    const ctx = makeCtx();
    rule.evaluate(ctx);
    expect(ctx.attributes.get('fx_rate_applied')).toBe('1.0');
  });
});

describe('VolumeDiscountRule (priority 55)', () => {
  it('should apply 25% discount for >5000 shares', () => {
    const rule = new VolumeDiscountRule();
    const ctx = makeCtx({ quantity: 7000 });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('volume_discount')).toBe(0.25);
  });

  it('should apply 50% discount for >10000 shares', () => {
    const rule = new VolumeDiscountRule();
    const ctx = makeCtx({ quantity: 15000 });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('volume_discount')).toBe(0.50);
  });

  it('should not apply discount for <=5000 shares', () => {
    const rule = new VolumeDiscountRule();
    const ctx = makeCtx({ quantity: 3000 });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('volume_discount')).toBeUndefined();
  });
});

describe('SpecialClientsRule (priority 50)', () => {
  it('should apply early access for C001 (Acme)', () => {
    const rule = new SpecialClientsRule();
    const ctx = makeCtx({}, { clientId: 'C001' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('early_access')).toBe(true);
  });

  it('should apply zero commission for C002 (Henderson)', () => {
    const rule = new SpecialClientsRule();
    const ctx = makeCtx({}, { clientId: 'C002' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('commission_override')).toBe(0.0);
  });

  it('should apply PLATINUM override for C004 (MegaFund)', () => {
    const rule = new SpecialClientsRule();
    const ctx = makeCtx({}, { clientId: 'C004' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('pricing_tier_override')).toBe('PLATINUM');
  });

  it('should apply 50% discount for C005 (Pinnacle)', () => {
    const rule = new SpecialClientsRule();
    const ctx = makeCtx({}, { clientId: 'C005' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('commission_override')).toBe(0.005);
  });

  it('should apply zero commission + early access for C006 (Global Macro)', () => {
    const rule = new SpecialClientsRule();
    const ctx = makeCtx({}, { clientId: 'C006' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('commission_override')).toBe(0.0);
    expect(ctx.attributes.get('early_access')).toBe(true);
  });

  it('should apply FX priority for C010 (Sterling)', () => {
    const rule = new SpecialClientsRule();
    const ctx = makeCtx({}, { clientId: 'C010' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('multi_currency_priority')).toBe(true);
    expect(ctx.attributes.get('commission_override')).toBe(0.0);
  });

  it('should not apply overrides for unknown clients', () => {
    const rule = new SpecialClientsRule();
    const ctx = makeCtx({}, { clientId: 'C999' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('commission_override')).toBeUndefined();
  });
});

describe('LoyaltyBonusRule (priority 45)', () => {
  it('should apply 10% bonus for C001', () => {
    const rule = new LoyaltyBonusRule();
    const ctx = makeCtx({}, { clientId: 'C001' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('loyalty_bonus')).toBe(0.10);
  });

  it('should apply 10% bonus for C002', () => {
    const rule = new LoyaltyBonusRule();
    const ctx = makeCtx({}, { clientId: 'C002' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('loyalty_bonus')).toBe(0.10);
  });

  it('should apply 10% bonus for C003', () => {
    const rule = new LoyaltyBonusRule();
    const ctx = makeCtx({}, { clientId: 'C003' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('loyalty_bonus')).toBe(0.10);
  });

  it('should not apply bonus for other clients', () => {
    const rule = new LoyaltyBonusRule();
    const ctx = makeCtx({}, { clientId: 'C004' });
    rule.evaluate(ctx);
    expect(ctx.attributes.get('loyalty_bonus')).toBeUndefined();
  });
});
