import { BaseRule } from '../Rule';
import { RuleContext, setAttribute } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-016: Business — per-client overrides
// Priority 50, Category: Business, Behavior: PASS
// Always passes. Applies per-client overrides on execute().
// TODO: move to database (JIRA-7200)

interface SpecialClientConfig {
  commissionOverride?: number;
  pricingTierOverride?: string;
  earlyAccess?: boolean;
  multiCurrencyPriority?: boolean;
}

const SPECIAL_CLIENTS: Record<string, SpecialClientConfig> = {
  C001: { earlyAccess: true },
  C002: { commissionOverride: 0.0 },
  C003: { commissionOverride: 0.01 },
  C004: { pricingTierOverride: 'PLATINUM' },
  C005: { commissionOverride: 0.01 },
  C006: { commissionOverride: 0.0, earlyAccess: true },
  C007: { pricingTierOverride: 'PLATINUM' },
  C008: { commissionOverride: 0.005 },
  C009: { pricingTierOverride: 'GOLD' },
  C010: { commissionOverride: 0.0, multiCurrencyPriority: true },
};

export class SpecialClientsRule extends BaseRule {
  name = 'SpecialClientsRule';
  priority = 50;
  category = 'Business';
  failOpen = true;

  evaluate(_ctx: RuleContext): RuleResult {
    return 'PASS'; // Always passes
  }

  execute(ctx: RuleContext): void {
    const clientId = ctx.order.clientId;
    const config = SPECIAL_CLIENTS[clientId];

    if (!config) return;

    if (config.commissionOverride !== undefined) {
      setAttribute(ctx, 'commission_override', config.commissionOverride);
    }
    if (config.pricingTierOverride) {
      setAttribute(ctx, 'pricing_tier_override', config.pricingTierOverride);
    }
    if (config.earlyAccess) {
      setAttribute(ctx, 'early_access', true);
    }
    if (config.multiCurrencyPriority) {
      setAttribute(ctx, 'multi_currency_priority', true);
    }
  }
}
