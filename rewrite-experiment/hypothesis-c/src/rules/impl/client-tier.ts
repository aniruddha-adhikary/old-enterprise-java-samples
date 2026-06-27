import { Rule, RuleContext } from '../rule-engine';
import { ClientTier } from '../../domain/client';

/**
 * ClientTierRule (priority 90)
 * Sets order processing priority based on client tier.
 * PLATINUM/GOLD get HIGH priority, SILVER/BRONZE get NORMAL.
 */
export class ClientTierRule implements Rule {
  name = 'ClientTierRule';
  priority = 90;
  active = true;

  evaluate(ctx: RuleContext): boolean {
    const tier = ctx.client.tier;

    if (tier === ClientTier.PLATINUM || tier === ClientTier.GOLD) {
      ctx.attributes.set('priority', 'HIGH');
    } else {
      ctx.attributes.set('priority', 'NORMAL');
    }

    return true;
  }
}
