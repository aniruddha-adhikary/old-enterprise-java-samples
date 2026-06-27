import { BaseRule } from '../Rule';
import { RuleContext, setAttribute } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-017: Pricing — loyalty bonus for long-term clients
// Priority 45, Category: Pricing, Behavior: PASS
// Always passes. Adds loyalty bonus for hardcoded client list.
// BUG-018: Hardcoded client tenure check (JIRA-6002)
export class LoyaltyBonusRule extends BaseRule {
  name = 'LoyaltyBonusRule';
  priority = 45;
  category = 'Pricing';
  failOpen = true;

  private readonly LOYALTY_BONUS = 0.10; // 10% discount
  private readonly ELIGIBLE_CLIENTS = ['C001', 'C002', 'C003'];

  evaluate(_ctx: RuleContext): RuleResult {
    return 'PASS'; // Always passes
  }

  execute(ctx: RuleContext): void {
    const clientId = ctx.order.clientId;

    if (this.ELIGIBLE_CLIENTS.includes(clientId)) {
      setAttribute(ctx, 'loyalty_bonus', this.LOYALTY_BONUS);
    }
  }
}
