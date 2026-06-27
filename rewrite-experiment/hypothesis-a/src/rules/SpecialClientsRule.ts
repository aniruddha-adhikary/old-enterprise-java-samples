import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';
import { SpecialClientConfig } from '../config';

export class SpecialClientsRule implements Rule {
  readonly name = 'SpecialClientsRule';
  readonly priority = 50;
  readonly category = 'Business';

  constructor(private readonly overrides: SpecialClientConfig[]) {}

  isActive(): boolean {
    return true;
  }

  async evaluate(_ctx: RuleContext): Promise<boolean> {
    return true; // always passes
  }

  async execute(ctx: RuleContext): Promise<void> {
    const { order } = ctx;
    if (!order || !order.clientId) return;

    const config = this.overrides.find((c) => c.clientId === order.clientId);
    if (!config) return;

    if (config.commissionOverride !== undefined) {
      ctx.setAttribute('commission_override', config.commissionOverride);
    }

    if (config.pricingTierOverride) {
      ctx.setAttribute('pricing_tier_override', config.pricingTierOverride);
    }

    if (config.earlyAccess) {
      ctx.setAttribute('early_access', true);
    }

    if (config.multiCurrencyPriority) {
      ctx.setAttribute('multi_currency_priority', true);
    }
  }
}
