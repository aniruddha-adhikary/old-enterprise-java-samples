import { RuleContext } from '../src/domain/RuleContext';
import { createTradeOrder, TradeOrder } from '../src/domain/TradeOrder';
import { Client } from '../src/domain/Client';
import { OrderSide, ClientTier, KycStatus } from '../src/domain/enums';
import { RuleEngine } from '../src/rules/RuleEngine';

import { LayeringDetectionRule } from '../src/rules/LayeringDetectionRule';
import { SpoofingPatternRule } from '../src/rules/SpoofingPatternRule';
import { PositionLimitRule } from '../src/rules/PositionLimitRule';
import { MarketHaltRule } from '../src/rules/MarketHaltRule';
import { ClientKillSwitchRule } from '../src/rules/ClientKillSwitchRule';
import { KYCStatusRule } from '../src/rules/KYCStatusRule';
import { DailyVolumeLimitRule } from '../src/rules/DailyVolumeLimitRule';
import { WashTradeDetectionRule } from '../src/rules/WashTradeDetectionRule';
import { MaxOrderValueRule } from '../src/rules/MaxOrderValueRule';
import { RestrictedSymbolRule } from '../src/rules/RestrictedSymbolRule';
import { ClientTierRule } from '../src/rules/ClientTierRule';
import { ShortSaleRule } from '../src/rules/ShortSaleRule';
import { MultiCurrencyRule } from '../src/rules/MultiCurrencyRule';
import { VolumeDiscountRule } from '../src/rules/VolumeDiscountRule';
import { SpecialClientsRule } from '../src/rules/SpecialClientsRule';
import { LoyaltyBonusRule } from '../src/rules/LoyaltyBonusRule';

function makeClient(overrides: Partial<Client> = {}): Client {
  return {
    clientId: 'C001',
    clientName: 'Test Client',
    email: 'test@example.com',
    phone: '555-0001',
    tier: ClientTier.GOLD,
    maxOrderValue: 500000,
    active: true,
    kycStatus: KycStatus.APPROVED,
    killSwitch: 'N',
    createdDate: new Date(),
    ...overrides,
  };
}

function makeOrder(overrides: Partial<TradeOrder> = {}): TradeOrder {
  return {
    ...createTradeOrder({
      clientId: 'C001',
      symbol: 'MSFT',
      quantity: 100,
      side: OrderSide.BUY,
      requestedPrice: 25.0,
    }),
    ...overrides,
  };
}

const noopAuditLogger = {
  logRuleDecision: async () => {},
  logSurveillanceDecision: async () => {},
};

describe('Rule Engine - Priority Sorting', () => {
  test('sorts rules in descending order by default (BUG-001)', () => {
    const engine = new RuleEngine({ priorityFixed: false }, noopAuditLogger);
    engine.registerRules([
      new DailyVolumeLimitRule(),   // 110
      new MaxOrderValueRule(),      // 100
      new RestrictedSymbolRule(),   // 95
    ]);
    const rules = engine.getRules();
    expect(rules[0].priority).toBe(110);
    expect(rules[1].priority).toBe(100);
    expect(rules[2].priority).toBe(95);
  });

  test('sorts rules in ascending order when priorityFixed=true', () => {
    const engine = new RuleEngine({ priorityFixed: true }, noopAuditLogger);
    engine.registerRules([
      new DailyVolumeLimitRule(),   // 110
      new MaxOrderValueRule(),      // 100
      new RestrictedSymbolRule(),   // 95
    ]);
    const rules = engine.getRules();
    expect(rules[0].priority).toBe(95);
    expect(rules[1].priority).toBe(100);
    expect(rules[2].priority).toBe(110);
  });
});

describe('RUL-001: LayeringDetectionRule (priority=125)', () => {
  const orderRepo = {
    countNonCancelledOrders: jest.fn(),
    getCancelledOrderCount: jest.fn(),
    getTotalOrderCount: jest.fn(),
    findRecentOppositeOrders: jest.fn(),
  };

  test('flags when > 5 non-cancelled orders', async () => {
    orderRepo.countNonCancelledOrders.mockResolvedValue(6);
    const rule = new LayeringDetectionRule(orderRepo, noopAuditLogger);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true); // never rejects
    expect(ctx.getAttribute('surveillance_flags')).toContain('LAYERING');
  });

  test('does not flag when <= 5 orders', async () => {
    orderRepo.countNonCancelledOrders.mockResolvedValue(5);
    const rule = new LayeringDetectionRule(orderRepo, noopAuditLogger);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
    expect(ctx.getAttribute('surveillance_flags')).toBeUndefined();
  });

  test('fails open on DB error', async () => {
    orderRepo.countNonCancelledOrders.mockRejectedValue(new Error('DB error'));
    const rule = new LayeringDetectionRule(orderRepo, noopAuditLogger);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-002: SpoofingPatternRule (priority=124)', () => {
  test('flags when cancel rate > 60%', async () => {
    const orderRepo = {
      countNonCancelledOrders: jest.fn(),
      getCancelledOrderCount: jest.fn().mockResolvedValue(7),
      getTotalOrderCount: jest.fn().mockResolvedValue(10),
      findRecentOppositeOrders: jest.fn(),
    };
    const rule = new SpoofingPatternRule(orderRepo, noopAuditLogger);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true); // never rejects
    expect(ctx.getAttribute('surveillance_flags')).toContain('SPOOFING');
  });
});

describe('RUL-003: PositionLimitRule (priority=123)', () => {
  test('rejects when position exceeds 100,000', async () => {
    const posRepo = {
      getNetPosition: jest.fn().mockResolvedValue(95000),
    };
    const rule = new PositionLimitRule(posRepo, noopAuditLogger);
    const order = makeOrder({ quantity: 10000 });
    const ctx = new RuleContext(order, makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejected).toBe(true);
  });

  test('allows when position within limit', async () => {
    const posRepo = {
      getNetPosition: jest.fn().mockResolvedValue(50000),
    };
    const rule = new PositionLimitRule(posRepo, noopAuditLogger);
    const order = makeOrder({ quantity: 10000 });
    const ctx = new RuleContext(order, makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });

  test('fails open on DB error', async () => {
    const posRepo = {
      getNetPosition: jest.fn().mockRejectedValue(new Error('DB')),
    };
    const rule = new PositionLimitRule(posRepo, noopAuditLogger);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-004: MarketHaltRule (priority=120)', () => {
  test('rejects all orders when market is halted', async () => {
    const rule = new MarketHaltRule(() => true);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toContain('MARKET HALTED');
  });

  test('allows orders when market is open', async () => {
    const rule = new MarketHaltRule(() => false);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-005: ClientKillSwitchRule (priority=118)', () => {
  test('rejects when kill switch is Y', async () => {
    const clientRepo = {
      getKillSwitch: jest.fn().mockResolvedValue('Y'),
      getKycStatus: jest.fn(),
    };
    const rule = new ClientKillSwitchRule(clientRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toContain('kill switch');
  });

  test('allows when kill switch is N', async () => {
    const clientRepo = {
      getKillSwitch: jest.fn().mockResolvedValue('N'),
      getKycStatus: jest.fn(),
    };
    const rule = new ClientKillSwitchRule(clientRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });

  test('defaults to N on DB error', async () => {
    const clientRepo = {
      getKillSwitch: jest.fn().mockRejectedValue(new Error('DB')),
      getKycStatus: jest.fn(),
    };
    const rule = new ClientKillSwitchRule(clientRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-006: KYCStatusRule (priority=115)', () => {
  test('allows APPROVED clients', async () => {
    const clientRepo = {
      getKillSwitch: jest.fn(),
      getKycStatus: jest.fn().mockResolvedValue('APPROVED'),
    };
    const rule = new KYCStatusRule(clientRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });

  test('rejects PENDING clients', async () => {
    const clientRepo = {
      getKillSwitch: jest.fn(),
      getKycStatus: jest.fn().mockResolvedValue('PENDING'),
    };
    const rule = new KYCStatusRule(clientRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
  });

  test('treats null KYC as PENDING (reject)', async () => {
    const clientRepo = {
      getKillSwitch: jest.fn(),
      getKycStatus: jest.fn().mockResolvedValue(null),
    };
    const rule = new KYCStatusRule(clientRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.getAttribute('kyc_status')).toBe('PENDING');
  });
});

describe('RUL-007: DailyVolumeLimitRule (priority=110)', () => {
  test('rejects orders > 50,000 shares', async () => {
    const rule = new DailyVolumeLimitRule();
    const order = makeOrder({ quantity: 50001 });
    const ctx = new RuleContext(order, makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toContain('Daily volume limit');
  });

  test('allows orders <= 50,000 shares', async () => {
    const rule = new DailyVolumeLimitRule();
    const order = makeOrder({ quantity: 50000 });
    const ctx = new RuleContext(order, makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-008: WashTradeDetectionRule (priority=105)', () => {
  test('rejects when opposite-side order found within 5 minutes', async () => {
    const orderRepo = {
      countNonCancelledOrders: jest.fn(),
      getCancelledOrderCount: jest.fn(),
      getTotalOrderCount: jest.fn(),
      findRecentOppositeOrders: jest.fn().mockResolvedValue(1),
    };
    const rule = new WashTradeDetectionRule(orderRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toContain('wash trade');
  });

  test('fails open on DB error', async () => {
    const orderRepo = {
      countNonCancelledOrders: jest.fn(),
      getCancelledOrderCount: jest.fn(),
      getTotalOrderCount: jest.fn(),
      findRecentOppositeOrders: jest.fn().mockRejectedValue(new Error('DB')),
    };
    const rule = new WashTradeDetectionRule(orderRepo);
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-009: MaxOrderValueRule (priority=100)', () => {
  test('rejects when order value exceeds max * 1.10', async () => {
    const rule = new MaxOrderValueRule();
    const client = makeClient({ maxOrderValue: 100000 });
    const order = makeOrder({ quantity: 5000, requestedPrice: 25.0 }); // 125,000 > 110,000
    const ctx = new RuleContext(order, client);
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
  });

  test('allows when order value within max * 1.10', async () => {
    const rule = new MaxOrderValueRule();
    const client = makeClient({ maxOrderValue: 500000 });
    const order = makeOrder({ quantity: 100, requestedPrice: 25.0 }); // 2,500 < 550,000
    const ctx = new RuleContext(order, client);
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });

  test('rejects when client is null', async () => {
    const rule = new MaxOrderValueRule();
    const ctx = new RuleContext(makeOrder(), null);
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toBe('No client record found');
  });
});

describe('RUL-010: RestrictedSymbolRule (priority=95)', () => {
  test.each(['ENRN', 'WCOM', 'TYCO', 'ADLP'])(
    'rejects restricted symbol %s',
    async (symbol) => {
      const rule = new RestrictedSymbolRule();
      const order = makeOrder({ symbol });
      const ctx = new RuleContext(order, makeClient());
      const result = await rule.evaluate(ctx);
      expect(result).toBe(false);
      expect(ctx.rejectionReason).toContain('Restricted symbol');
    }
  );

  test('allows non-restricted symbols', async () => {
    const rule = new RestrictedSymbolRule();
    const ctx = new RuleContext(makeOrder({ symbol: 'MSFT' }), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-011: ClientTierRule (priority=90)', () => {
  test('rejects inactive client', async () => {
    const rule = new ClientTierRule();
    const client = makeClient({ active: false });
    const ctx = new RuleContext(makeOrder(), client);
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
  });

  test('sets HIGH priority for PLATINUM clients', async () => {
    const rule = new ClientTierRule();
    const client = makeClient({ tier: ClientTier.PLATINUM });
    const ctx = new RuleContext(makeOrder(), client);
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('priority')).toBe('HIGH');
  });

  test('sets NORMAL priority for BRONZE clients', async () => {
    const rule = new ClientTierRule();
    const client = makeClient({ tier: ClientTier.BRONZE });
    const ctx = new RuleContext(makeOrder(), client);
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('priority')).toBe('NORMAL');
  });
});

describe('RUL-013: ShortSaleRule (priority=75)', () => {
  test('rejects SELL orders > 1,000 shares', async () => {
    const rule = new ShortSaleRule();
    const order = makeOrder({ side: OrderSide.SELL, quantity: 1001 });
    const ctx = new RuleContext(order, makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toBe('Short sale limit exceeded');
  });

  test('allows SELL orders <= 1,000 shares', async () => {
    const rule = new ShortSaleRule();
    const order = makeOrder({ side: OrderSide.SELL, quantity: 1000 });
    const ctx = new RuleContext(order, makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });

  test('allows BUY orders regardless of quantity', async () => {
    const rule = new ShortSaleRule();
    const order = makeOrder({ side: OrderSide.BUY, quantity: 99999 });
    const ctx = new RuleContext(order, makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
  });
});

describe('RUL-014: MultiCurrencyRule (priority=60)', () => {
  test('sets USD rate for null currency', async () => {
    const rule = new MultiCurrencyRule();
    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await rule.evaluate(ctx);
    expect(result).toBe(true);
    expect(ctx.getAttribute('fx_rate_applied')).toBe(1.0);
    expect(ctx.getAttribute('settlement_currency')).toBe('USD');
  });

  test('sets EUR rate', async () => {
    const rule = new MultiCurrencyRule();
    const ctx = new RuleContext(makeOrder(), makeClient());
    ctx.setAttribute('currency', 'EUR');
    await rule.evaluate(ctx);
    expect(ctx.getAttribute('fx_rate_applied')).toBe(1.10);
    expect(ctx.getAttribute('settlement_currency')).toBe('EUR');
  });

  test('defaults unknown currency to USD with warning', async () => {
    const rule = new MultiCurrencyRule();
    const ctx = new RuleContext(makeOrder(), makeClient());
    ctx.setAttribute('currency', 'XYZ');
    await rule.evaluate(ctx);
    expect(ctx.getAttribute('fx_rate_applied')).toBe(1.0);
    expect(ctx.getAttribute('settlement_currency')).toBe('USD');
    expect(ctx.warnings.length).toBeGreaterThan(0);
  });
});

describe('RUL-015: VolumeDiscountRule (priority=55)', () => {
  test('applies 50% discount for > 10,000 shares', async () => {
    const rule = new VolumeDiscountRule();
    const order = makeOrder({ quantity: 15000 });
    const ctx = new RuleContext(order, makeClient());
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('volume_discount')).toBe(0.50);
    expect(ctx.getAttribute('volume_discount_applied')).toBe(0.01); // 0.02 * 0.50
  });

  test('applies 25% discount for > 5,000 shares', async () => {
    const rule = new VolumeDiscountRule();
    const order = makeOrder({ quantity: 7500 });
    const ctx = new RuleContext(order, makeClient());
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('volume_discount')).toBe(0.25);
    expect(ctx.getAttribute('volume_discount_applied')).toBe(0.015);
  });

  test('no discount for <= 5,000 shares', async () => {
    const rule = new VolumeDiscountRule();
    const order = makeOrder({ quantity: 3000 });
    const ctx = new RuleContext(order, makeClient());
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('volume_discount')).toBe(0);
  });
});

describe('RUL-016: SpecialClientsRule (priority=50)', () => {
  test('applies C002 zero commission override', async () => {
    const rule = new SpecialClientsRule([
      { clientId: 'C002', commissionOverride: 0.0 },
    ]);
    const order = makeOrder({ clientId: 'C002' });
    const ctx = new RuleContext(order, makeClient({ clientId: 'C002' }));
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('commission_override')).toBe(0.0);
  });

  test('applies C004 PLATINUM pricing override', async () => {
    const rule = new SpecialClientsRule([
      { clientId: 'C004', pricingTierOverride: 'PLATINUM' },
    ]);
    const order = makeOrder({ clientId: 'C004' });
    const ctx = new RuleContext(order, makeClient({ clientId: 'C004' }));
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('pricing_tier_override')).toBe('PLATINUM');
  });
});

describe('RUL-017: LoyaltyBonusRule (priority=45)', () => {
  test('applies 10% bonus for C001', async () => {
    const rule = new LoyaltyBonusRule();
    const order = makeOrder({ clientId: 'C001' });
    const ctx = new RuleContext(order, makeClient());
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('loyalty_bonus')).toBe(0.10);
  });

  test('does not apply bonus for C004', async () => {
    const rule = new LoyaltyBonusRule();
    const order = makeOrder({ clientId: 'C004' });
    const ctx = new RuleContext(order, makeClient({ clientId: 'C004' }));
    await rule.evaluate(ctx);
    await rule.execute(ctx);
    expect(ctx.getAttribute('loyalty_bonus')).toBeUndefined();
  });
});

describe('RuleEngine - Chain Behavior', () => {
  test('stops chain on first failing rule', async () => {
    const engine = new RuleEngine({ priorityFixed: false }, noopAuditLogger);
    engine.registerRules([
      new DailyVolumeLimitRule(),   // 110
      new MaxOrderValueRule(),      // 100
    ]);

    const order = makeOrder({ quantity: 60000, requestedPrice: 25.0 });
    const ctx = new RuleContext(order, makeClient());
    const result = await engine.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toContain('Daily volume limit');
  });

  test('execute errors do not fail the order (NFR-001)', async () => {
    const engine = new RuleEngine({ priorityFixed: false }, noopAuditLogger);

    const badRule = {
      name: 'BadExecuteRule',
      priority: 200,
      category: 'Test',
      isActive: () => true,
      evaluate: async () => true,
      execute: async () => { throw new Error('execute boom'); },
    };

    engine.registerRule(badRule);
    engine.registerRule(new MaxOrderValueRule());

    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await engine.evaluate(ctx);
    expect(result).toBe(true); // still passes
  });

  test('evaluate exception rejects with rule error message', async () => {
    const engine = new RuleEngine({ priorityFixed: false }, noopAuditLogger);

    const errorRule = {
      name: 'ErrorRule',
      priority: 200,
      category: 'Test',
      isActive: () => true,
      evaluate: async () => { throw new Error('eval boom'); },
      execute: async () => {},
    };

    engine.registerRule(errorRule);

    const ctx = new RuleContext(makeOrder(), makeClient());
    const result = await engine.evaluate(ctx);
    expect(result).toBe(false);
    expect(ctx.rejectionReason).toContain('Rule error: ErrorRule');
  });
});
