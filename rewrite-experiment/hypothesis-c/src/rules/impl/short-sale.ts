import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * ShortSaleRule (priority 75)
 * Rejects SELL orders exceeding 1,000 shares per order.
 */
export class ShortSaleRule implements Rule {
  name = 'ShortSaleRule';
  priority = 75;
  active = true;

  static readonly SHORT_SALE_LIMIT = 1000;

  evaluate(ctx: RuleContext): boolean {
    if (ctx.order.side !== 'SELL') {
      return true;
    }

    if (ctx.order.quantity > ShortSaleRule.SHORT_SALE_LIMIT) {
      rejectContext(ctx, `Short sale limit exceeded: ${ctx.order.quantity} shares (max ${ShortSaleRule.SHORT_SALE_LIMIT})`);
      return false;
    }

    const rate = 0.02;
    ctx.attributes.set('short_sale_commission', ctx.order.quantity * rate);
    return true;
  }
}
