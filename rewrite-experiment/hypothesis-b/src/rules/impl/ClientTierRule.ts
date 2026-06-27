import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext, addWarning } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-011: Business — client tier & active status check
// Priority 90, Category: Business, Behavior: REJECT_IF_INACTIVE
// PLATINUM/GOLD -> priority=HIGH, SILVER/BRONZE -> priority=NORMAL
// Null client warns but passes.
export class ClientTierRule extends BaseRule {
  name = 'ClientTierRule';
  priority = 90;
  category = 'Business';
  failOpen = true;

  evaluate(ctx: RuleContext): RuleResult {
    if (!ctx.client) {
      addWarning(ctx, 'Client is null — passing with warning');
      return 'PASS';
    }

    if (!ctx.client.active) {
      rejectContext(ctx, `Client ${ctx.client.clientId} is inactive`);
      return 'FAIL';
    }

    const tier = ctx.client.tier;
    if (tier === 'PLATINUM' || tier === 'GOLD') {
      setAttribute(ctx, 'priority', 'HIGH');
    } else {
      setAttribute(ctx, 'priority', 'NORMAL');
    }

    return 'PASS';
  }
}
