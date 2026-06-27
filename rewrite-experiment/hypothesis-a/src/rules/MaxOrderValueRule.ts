import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';

const BUFFER = 1.10; // 10% buffer (JIRA-1892 Henderson complaint)

export class MaxOrderValueRule implements Rule {
  readonly name = 'MaxOrderValueRule';
  readonly priority = 100;
  readonly category = 'Business';

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order, client } = ctx;
    if (!order) return true;

    if (!client) {
      ctx.reject('No client record found');
      return false;
    }

    const orderValue = order.quantity * order.requestedPrice;
    const maxAllowed = client.maxOrderValue * BUFFER;

    if (orderValue > maxAllowed) {
      ctx.reject(
        `Order value ${orderValue} exceeds max allowed ${maxAllowed}`
      );
      return false;
    }

    return true;
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
