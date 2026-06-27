import { BaseRule } from '../Rule';
import { RuleContext, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-009: Business — max order value with 10% buffer (Henderson buffer, JIRA-1892)
// Priority 100, Category: Business, Behavior: REJECT
// Condition: quantity * requestedPrice > client.maxOrderValue * 1.10
export class MaxOrderValueRule extends BaseRule {
  name = 'MaxOrderValueRule';
  priority = 100;
  category = 'Business';
  failOpen = false;

  private readonly BUFFER_MULTIPLIER = 1.10;

  evaluate(ctx: RuleContext): RuleResult {
    const orderValue = ctx.order.quantity * ctx.order.requestedPrice;
    const maxAllowed = ctx.client.maxOrderValue * this.BUFFER_MULTIPLIER;

    if (orderValue > maxAllowed) {
      rejectContext(ctx,
        `Order value $${orderValue.toFixed(2)} exceeds max allowed $${maxAllowed.toFixed(2)} ` +
        `(client limit $${ctx.client.maxOrderValue.toFixed(2)} + 10% buffer)`
      );
      return 'FAIL';
    }

    return 'PASS';
  }
}
