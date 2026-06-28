import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';
import { OrderSide } from '../domain/enums';
import { getCommissionRate } from '../pricing/CommissionCalculator';
import { ClientTier } from '../domain/enums';

const SHORT_SALE_LIMIT = 1000;

export class ShortSaleRule implements Rule {
  readonly name = 'ShortSaleRule';
  readonly priority = 75;
  readonly category = 'Business';

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order) return true;

    if (order.side === OrderSide.SELL && order.quantity > SHORT_SALE_LIMIT) {
      ctx.reject('Short sale limit exceeded');
      return false;
    }

    return true;
  }

  async execute(ctx: RuleContext): Promise<void> {
    const { order, client } = ctx;
    if (!order || order.side !== OrderSide.SELL) return;

    const tier = client?.tier as ClientTier | undefined;
    const rate = getCommissionRate(tier);
    ctx.setAttribute('commission_rate', rate);
  }
}
