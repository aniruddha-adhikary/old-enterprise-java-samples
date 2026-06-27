import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';
import { ClientTier } from '../domain/enums';

export class ClientTierRule implements Rule {
  readonly name = 'ClientTierRule';
  readonly priority = 90;
  readonly category = 'Business';

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { client } = ctx;
    if (!client) return true;

    if (!client.active) {
      ctx.reject('Client account is inactive');
      return false;
    }

    return true;
  }

  async execute(ctx: RuleContext): Promise<void> {
    const { client } = ctx;
    if (!client) return;

    const tier = client.tier;
    if (tier === ClientTier.PLATINUM || tier === ClientTier.GOLD) {
      ctx.setAttribute('priority', 'HIGH');
    } else {
      ctx.setAttribute('priority', 'NORMAL');
    }
  }
}
