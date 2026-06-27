import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * MaxOrderValueRule (priority 100)
 * Rejects if order qty * requestedPrice > client.maxOrderValue * buffer.
 * Buffer is 1.10 (10%) - originally added for Henderson account (JIRA-1892).
 */
export class MaxOrderValueRule implements Rule {
  name = 'MaxOrderValueRule';
  priority = 100;
  active = true;

  static readonly BUFFER = 1.10;

  evaluate(ctx: RuleContext): boolean {
    const orderValue = ctx.order.quantity * ctx.order.requestedPrice;
    const limit = ctx.client.maxOrderValue * MaxOrderValueRule.BUFFER;

    if (orderValue > limit) {
      rejectContext(ctx, `Order value $${orderValue.toFixed(2)} exceeds limit $${limit.toFixed(2)} (max: $${ctx.client.maxOrderValue} * ${MaxOrderValueRule.BUFFER})`);
      return false;
    }

    return true;
  }
}
