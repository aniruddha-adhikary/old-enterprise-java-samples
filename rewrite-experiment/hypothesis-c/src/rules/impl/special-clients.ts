import { Rule, RuleContext } from '../rule-engine';
import { ClientTier, SpecialClientOverride } from '../../domain/client';

/**
 * SpecialClientsRule (priority 50)
 * Applies hardcoded overrides for 10 special clients.
 * In the original code these were fully hardcoded in SpecialClientsRule.java.
 * Here they are configurable but default to the original values.
 *
 * Client overrides from source:
 *   C001 (Acme): early access
 *   C002 (Henderson): zero commission first 1000/day, PLATINUM override (JIRA-1892)
 *   C003 (Smith): GOLD rate (commission 0.01)
 *   C004 (MegaFund): PLATINUM override (commission 0.01)
 *   C005 (Pinnacle): 50% discount (commission 0.005)
 *   C006 (Global Macro): zero commission + early access
 *   C007 (Velocity): PLATINUM override
 *   C008 (Falcon): 75% discount
 *   C009 (Apex): GOLD override
 *   C010 (Sterling): zero commission + FX priority
 */
export class SpecialClientsRule implements Rule {
  name = 'SpecialClientsRule';
  priority = 50;
  active = true;

  private overrides: Map<string, SpecialClientOverride>;

  constructor(overrides?: SpecialClientOverride[]) {
    this.overrides = new Map();

    if (overrides) {
      for (const o of overrides) {
        this.overrides.set(o.clientId, o);
      }
    } else {
      this.loadDefaults();
    }
  }

  private loadDefaults(): void {
    const defaults: SpecialClientOverride[] = [
      { clientId: 'C001', commissionOverride: null, tierOverride: null, earlyAccess: true, fxPriority: false, discountPct: 0, zeroCommission: false, freeTradesPerDay: 0, notes: 'Acme - early access' },
      { clientId: 'C002', commissionOverride: 0.0, tierOverride: ClientTier.PLATINUM, earlyAccess: false, fxPriority: false, discountPct: 0, zeroCommission: true, freeTradesPerDay: 1000, notes: 'Henderson - zero commission first 1000/day' },
      { clientId: 'C003', commissionOverride: 0.01, tierOverride: ClientTier.GOLD, earlyAccess: false, fxPriority: false, discountPct: 0, zeroCommission: false, freeTradesPerDay: 0, notes: 'Smith - GOLD rate' },
      { clientId: 'C004', commissionOverride: 0.01, tierOverride: ClientTier.PLATINUM, earlyAccess: false, fxPriority: false, discountPct: 0, zeroCommission: false, freeTradesPerDay: 0, notes: 'MegaFund - PLATINUM override' },
      { clientId: 'C005', commissionOverride: 0.005, tierOverride: null, earlyAccess: false, fxPriority: false, discountPct: 50, zeroCommission: false, freeTradesPerDay: 0, notes: 'Pinnacle - 50% discount' },
      { clientId: 'C006', commissionOverride: 0.0, tierOverride: null, earlyAccess: true, fxPriority: false, discountPct: 0, zeroCommission: true, freeTradesPerDay: 0, notes: 'Global Macro - zero commission + early access' },
      { clientId: 'C007', commissionOverride: null, tierOverride: ClientTier.PLATINUM, earlyAccess: false, fxPriority: false, discountPct: 0, zeroCommission: false, freeTradesPerDay: 0, notes: 'Velocity - PLATINUM override' },
      { clientId: 'C008', commissionOverride: null, tierOverride: null, earlyAccess: false, fxPriority: false, discountPct: 75, zeroCommission: false, freeTradesPerDay: 0, notes: 'Falcon - 75% discount' },
      { clientId: 'C009', commissionOverride: null, tierOverride: ClientTier.GOLD, earlyAccess: false, fxPriority: false, discountPct: 0, zeroCommission: false, freeTradesPerDay: 0, notes: 'Apex - GOLD override' },
      { clientId: 'C010', commissionOverride: 0.0, tierOverride: null, earlyAccess: false, fxPriority: true, discountPct: 0, zeroCommission: true, freeTradesPerDay: 0, notes: 'Sterling - zero commission + FX priority' },
    ];

    for (const d of defaults) {
      this.overrides.set(d.clientId, d);
    }
  }

  evaluate(ctx: RuleContext): boolean {
    const override = this.overrides.get(ctx.client.clientId);
    if (!override) {
      return true;
    }

    if (override.commissionOverride !== null) {
      ctx.attributes.set('commission_override', override.commissionOverride);
    }

    if (override.tierOverride) {
      ctx.attributes.set('pricing_tier_override', override.tierOverride);
    }

    if (override.earlyAccess) {
      ctx.attributes.set('early_access', true);
    }

    if (override.fxPriority) {
      ctx.attributes.set('multi_currency_priority', true);
    }

    if (override.zeroCommission) {
      ctx.attributes.set('commission_override', 0.0);
    }

    ctx.messages.push(`Special client override applied for ${ctx.client.clientId}: ${override.notes}`);
    return true;
  }
}
