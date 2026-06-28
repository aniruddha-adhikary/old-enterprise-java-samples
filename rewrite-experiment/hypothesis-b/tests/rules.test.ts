import { RuleEngine } from '../src/rules/RuleEngine';
import { createRuleContext, setAttribute } from '../src/domain/RuleContext';
import { createTradeOrder, OrderStatus } from '../src/domain/TradeOrder';
import { Client } from '../src/domain/Client';
import {
  LayeringDetectionRule, SpoofingPatternRule, PositionLimitRule,
  MarketHaltRule, ClientKillSwitchRule, KYCStatusRule,
  DailyVolumeLimitRule, WashTradeDetectionRule, MaxOrderValueRule,
  RestrictedSymbolRule, ClientTierRule, MarketHoursRule,
  ShortSaleRule, MultiCurrencyRule, VolumeDiscountRule,
  SpecialClientsRule, LoyaltyBonusRule,
} from '../src/rules/impl';

function makeClient(overrides?: Partial<Client>): Client {
  return {
    clientId: 'C001',
    name: 'Test Client',
    email: 'test@test.com',
    phone: '555-0000',
    tier: 'GOLD',
    maxOrderValue: 500000,
    active: true,
    kycStatus: 'APPROVED',
    killSwitch: 'N',
    ...overrides,
  };
}

function makeOrder(overrides?: Partial<ReturnType<typeof createTradeOrder>>) {
  const order = createTradeOrder({
    clientId: 'C001',
    symbol: 'MSFT',
    quantity: 500,
    side: 'BUY',
    requestedPrice: 25.75,
  });
  return { ...order, ...overrides };
}

describe('RuleEngine', () => {
  beforeEach(() => {
    RuleEngine.resetInstance();
  });

  test('singleton pattern', () => {
    const e1 = RuleEngine.getInstance();
    const e2 = RuleEngine.getInstance();
    expect(e1).toBe(e2);
  });

  test('BUG-001: rules sorted DESCENDING by priority (higher runs first)', () => {
    const engine = RuleEngine.getInstance();
    engine.registerRules([
      new MarketHoursRule(),   // priority 80
      new MaxOrderValueRule(), // priority 100
      new MarketHaltRule(),    // priority 120
    ]);
    const rules = engine.getRules();
    expect(rules[0].name).toBe('MarketHaltRule');
    expect(rules[1].name).toBe('MaxOrderValueRule');
    expect(rules[2].name).toBe('MarketHoursRule');
  });

  test('priorityFixed=true sorts ASCENDING', () => {
    const engine = RuleEngine.getInstance({ priorityFixed: true });
    engine.registerRules([
      new MarketHoursRule(),
      new MaxOrderValueRule(),
      new MarketHaltRule(),
    ]);
    const rules = engine.getRules();
    expect(rules[0].name).toBe('MarketHoursRule');
    expect(rules[2].name).toBe('MarketHaltRule');
  });

  test('audit log populated after evaluation', () => {
    const engine = RuleEngine.getInstance();
    engine.registerRules([new MaxOrderValueRule()]);
    const ctx = createRuleContext(makeOrder(), makeClient());
    engine.evaluateAll(ctx);
    const log = engine.getAuditLog();
    expect(log.length).toBe(1);
    expect(log[0].ruleName).toBe('MaxOrderValueRule');
    expect(log[0].result).toBe('PASS');
  });
});

describe('RUL-001: LayeringDetectionRule', () => {
  test('passes for normal order count', () => {
    const rule = new LayeringDetectionRule();
    rule.setOrderCountProvider(() => 3);
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('layering_status')).toBe('CLEAR');
  });

  test('flags but passes when count > 5', () => {
    const rule = new LayeringDetectionRule();
    rule.setOrderCountProvider(() => 8);
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS'); // FLAG behavior
    expect(ctx.attributes.get('layering_status')).toBe('FLAGGED');
    expect(ctx.warnings.length).toBe(1);
  });

  test('fail-open on error', () => {
    const rule = new LayeringDetectionRule();
    rule.setOrderCountProvider(() => { throw new Error('DB fail'); });
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });
});

describe('RUL-002: SpoofingPatternRule', () => {
  test('passes for low cancel rate', () => {
    const rule = new SpoofingPatternRule();
    rule.setCancelRateProvider(() => ({ cancelled: 1, total: 10 }));
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('spoofing_status')).toBe('CLEAR');
  });

  test('flags when cancel rate > 60%', () => {
    const rule = new SpoofingPatternRule();
    rule.setCancelRateProvider(() => ({ cancelled: 7, total: 10 }));
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS'); // FLAG behavior
    expect(ctx.attributes.get('spoofing_status')).toBe('FLAGGED');
  });
});

describe('RUL-003: PositionLimitRule', () => {
  test('passes within limits', () => {
    const rule = new PositionLimitRule();
    rule.setPositionProvider(() => 50000);
    const ctx = createRuleContext(makeOrder({ quantity: 500 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects when position limit exceeded', () => {
    const rule = new PositionLimitRule();
    rule.setPositionProvider(() => 99800);
    const ctx = createRuleContext(makeOrder({ quantity: 500 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });
});

describe('RUL-004: MarketHaltRule', () => {
  test('passes when market not halted', () => {
    const rule = new MarketHaltRule();
    rule.setMarketHaltedProvider(() => false);
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects when market halted', () => {
    const rule = new MarketHaltRule();
    rule.setMarketHaltedProvider(() => true);
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
    expect(ctx.rejectionReason).toContain('halted');
  });
});

describe('RUL-005: ClientKillSwitchRule', () => {
  test('passes for normal client', () => {
    const rule = new ClientKillSwitchRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ killSwitch: 'N' }));
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects when kill switch active', () => {
    const rule = new ClientKillSwitchRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ killSwitch: 'Y' }));
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });

  test('handles case-insensitive trimmed value', () => {
    const rule = new ClientKillSwitchRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ killSwitch: ' y ' }));
    expect(rule.evaluate(ctx)).toBe('FAIL');
  });
});

describe('RUL-006: KYCStatusRule', () => {
  test('passes for APPROVED status', () => {
    const rule = new KYCStatusRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ kycStatus: 'APPROVED' }));
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects for PENDING status', () => {
    const rule = new KYCStatusRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ kycStatus: 'PENDING' }));
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });
});

describe('RUL-007: DailyVolumeLimitRule', () => {
  test('passes for normal quantity', () => {
    const rule = new DailyVolumeLimitRule();
    const ctx = createRuleContext(makeOrder({ quantity: 1000 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects quantity > 50000', () => {
    const rule = new DailyVolumeLimitRule();
    const ctx = createRuleContext(makeOrder({ quantity: 60000 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });
});

describe('RUL-008: WashTradeDetectionRule', () => {
  test('passes when no opposite-side order', () => {
    const rule = new WashTradeDetectionRule();
    rule.setRecentOrderProvider(() => 0);
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects when wash trade detected', () => {
    const rule = new WashTradeDetectionRule();
    rule.setRecentOrderProvider(() => 1);
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });
});

describe('RUL-009: MaxOrderValueRule', () => {
  test('passes within max (with 10% buffer)', () => {
    const rule = new MaxOrderValueRule();
    // Order: 500 * 25.75 = 12,875 vs max 500,000 * 1.10 = 550,000
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects exceeding max + buffer', () => {
    const rule = new MaxOrderValueRule();
    // Order: 50000 * 25.75 = 1,287,500 vs max 500,000 * 1.10 = 550,000
    const ctx = createRuleContext(makeOrder({ quantity: 50000 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });

  test('Henderson buffer: passes at exactly 10% over', () => {
    const rule = new MaxOrderValueRule();
    // maxOrderValue=100,000, buffer=110,000. Order=110,000 should PASS (<=)
    const client = makeClient({ maxOrderValue: 100000 });
    const order = makeOrder({ quantity: 1000, requestedPrice: 110 }); // 110,000 exactly
    const ctx = createRuleContext(order, client);
    expect(rule.evaluate(ctx)).toBe('PASS');
  });
});

describe('RUL-010: RestrictedSymbolRule', () => {
  test('passes for normal symbol', () => {
    const rule = new RestrictedSymbolRule();
    const ctx = createRuleContext(makeOrder({ symbol: 'MSFT' }), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects restricted symbols', () => {
    const rule = new RestrictedSymbolRule();
    for (const sym of ['ENRN', 'WCOM', 'TYCO', 'ADLP']) {
      const ctx = createRuleContext(makeOrder({ symbol: sym }), makeClient());
      expect(rule.evaluate(ctx)).toBe('FAIL');
    }
  });
});

describe('RUL-011: ClientTierRule', () => {
  test('passes and sets HIGH priority for PLATINUM', () => {
    const rule = new ClientTierRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ tier: 'PLATINUM' }));
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('priority')).toBe('HIGH');
  });

  test('passes and sets NORMAL priority for BRONZE', () => {
    const rule = new ClientTierRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ tier: 'BRONZE' }));
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('priority')).toBe('NORMAL');
  });

  test('rejects inactive client', () => {
    const rule = new ClientTierRule();
    const ctx = createRuleContext(makeOrder(), makeClient({ active: false }));
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });
});

describe('RUL-012: MarketHoursRule', () => {
  test('BUG-003: uses server local time', () => {
    const rule = new MarketHoursRule();
    // Tuesday 10:00 AM - during market hours
    rule.setTimeProvider(() => new Date(2024, 0, 2, 10, 0, 0)); // Tue
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('queued')).toBe(false);
  });

  test('sets queued=true outside hours (BUG-020: never actually queued)', () => {
    const rule = new MarketHoursRule();
    // Tuesday 8:00 AM - before market open
    rule.setTimeProvider(() => new Date(2024, 0, 2, 8, 0, 0));
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS'); // PASS_WITH_QUEUE
    expect(ctx.attributes.get('queued')).toBe(true);
  });

  test('weekend sets queued=true', () => {
    const rule = new MarketHoursRule();
    // Saturday 12:00
    rule.setTimeProvider(() => new Date(2024, 0, 6, 12, 0, 0)); // Sat
    const ctx = createRuleContext(makeOrder(), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('queued')).toBe(true);
  });
});

describe('RUL-013: ShortSaleRule', () => {
  test('passes for small SELL order', () => {
    const rule = new ShortSaleRule();
    const ctx = createRuleContext(makeOrder({ side: 'SELL', quantity: 500 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('passes for BUY order regardless of size', () => {
    const rule = new ShortSaleRule();
    const ctx = createRuleContext(makeOrder({ side: 'BUY', quantity: 5000 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
  });

  test('rejects SELL > 1000 shares', () => {
    const rule = new ShortSaleRule();
    const ctx = createRuleContext(makeOrder({ side: 'SELL', quantity: 1500 }), makeClient());
    expect(rule.evaluate(ctx)).toBe('FAIL');
    expect(ctx.rejected).toBe(true);
  });
});

describe('RUL-014: MultiCurrencyRule', () => {
  test('sets fx_rate for EUR pair', () => {
    const rule = new MultiCurrencyRule();
    const ctx = createRuleContext(makeOrder({ symbol: 'EUR/USD' }), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('fx_rate_applied')).toBe(1.10);
    expect(ctx.attributes.get('settlement_currency')).toBe('EUR');
  });

  test('defaults to USD rate 1.0 for unknown currency', () => {
    const rule = new MultiCurrencyRule();
    const ctx = createRuleContext(makeOrder({ symbol: 'MSFT' }), makeClient());
    expect(rule.evaluate(ctx)).toBe('PASS');
    expect(ctx.attributes.get('fx_rate_applied')).toBe(1.0);
  });
});

describe('RUL-015: VolumeDiscountRule', () => {
  test('no discount for small orders', () => {
    const rule = new VolumeDiscountRule();
    const ctx = createRuleContext(makeOrder({ quantity: 500 }), makeClient());
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('volume_discount')).toBe(0);
    expect(ctx.attributes.get('volume_discount_applied')).toBe(false);
  });

  test('25% discount for > 5000 shares', () => {
    const rule = new VolumeDiscountRule();
    const ctx = createRuleContext(makeOrder({ quantity: 7000 }), makeClient());
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('volume_discount')).toBe(0.25);
    expect(ctx.attributes.get('volume_discount_applied')).toBe(true);
  });

  test('50% discount for > 10000 shares', () => {
    const rule = new VolumeDiscountRule();
    const ctx = createRuleContext(makeOrder({ quantity: 15000 }), makeClient());
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('volume_discount')).toBe(0.50);
  });
});

describe('RUL-016: SpecialClientsRule', () => {
  test('C001 gets early access', () => {
    const rule = new SpecialClientsRule();
    const ctx = createRuleContext(makeOrder({ clientId: 'C001' }), makeClient({ clientId: 'C001' }));
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('early_access')).toBe(true);
  });

  test('C002 gets zero commission', () => {
    const rule = new SpecialClientsRule();
    const ctx = createRuleContext(makeOrder({ clientId: 'C002' }), makeClient({ clientId: 'C002' }));
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('commission_override')).toBe(0.0);
  });

  test('C005 gets 50% commission discount (0.01)', () => {
    const rule = new SpecialClientsRule();
    const ctx = createRuleContext(makeOrder({ clientId: 'C005' }), makeClient({ clientId: 'C005' }));
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('commission_override')).toBe(0.01);
  });

  test('C004 gets PLATINUM pricing tier', () => {
    const rule = new SpecialClientsRule();
    const ctx = createRuleContext(makeOrder({ clientId: 'C004' }), makeClient({ clientId: 'C004' }));
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('pricing_tier_override')).toBe('PLATINUM');
  });

  test('C008 gets 0.005 commission override', () => {
    const rule = new SpecialClientsRule();
    const ctx = createRuleContext(makeOrder({ clientId: 'C008' }), makeClient({ clientId: 'C008' }));
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('commission_override')).toBe(0.005);
  });
});

describe('RUL-017: LoyaltyBonusRule', () => {
  test('eligible client C001 gets 10% bonus', () => {
    const rule = new LoyaltyBonusRule();
    const ctx = createRuleContext(makeOrder({ clientId: 'C001' }), makeClient({ clientId: 'C001' }));
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('loyalty_bonus')).toBe(0.10);
  });

  test('non-eligible client gets no bonus', () => {
    const rule = new LoyaltyBonusRule();
    const ctx = createRuleContext(makeOrder({ clientId: 'C004' }), makeClient({ clientId: 'C004' }));
    rule.evaluate(ctx);
    rule.execute(ctx);
    expect(ctx.attributes.get('loyalty_bonus')).toBeUndefined();
  });
});
