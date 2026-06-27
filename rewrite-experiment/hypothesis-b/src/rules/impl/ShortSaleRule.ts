import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-013: Business — short sale limit
// Priority 75, Category: Business, Behavior: REJECT
// Condition: order.side == SELL AND order.quantity > 1000
// BUG-019: Not in rules.xml; added manually after XML-loaded rules (JIRA-4101)
export class ShortSaleRule extends BaseRule {
  name = 'ShortSaleRule';
  priority = 75;
  category = 'Business';
  failOpen = true;

  private readonly SHORT_SALE_SHARE_LIMIT = 1000;

  evaluate(ctx: RuleContext): RuleResult {
    if (ctx.order.side === 'SELL' && ctx.order.quantity > this.SHORT_SALE_SHARE_LIMIT) {
      setAttribute(ctx, 'short_sale_commission', 0.02);
      rejectContext(ctx, `Short sale exceeds limit: ${ctx.order.quantity} > ${this.SHORT_SALE_SHARE_LIMIT} shares`);
      return 'FAIL';
    }

    return 'PASS';
  }
}
