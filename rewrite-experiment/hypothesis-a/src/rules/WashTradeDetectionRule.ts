import { Rule, OrderRepository } from './Rule';
import { RuleContext } from '../domain/RuleContext';
import { OrderSide } from '../domain/enums';

const WASH_TRADE_WINDOW_MINUTES = 5;

export class WashTradeDetectionRule implements Rule {
  readonly name = 'WashTradeDetectionRule';
  readonly priority = 105;
  readonly category = 'Compliance';

  constructor(private readonly orderRepo: OrderRepository) {}

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order || !order.clientId || !order.symbol) return true;

    try {
      const oppositeSide =
        order.side === OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;

      const count = await this.orderRepo.findRecentOppositeOrders(
        order.clientId,
        order.symbol,
        oppositeSide,
        WASH_TRADE_WINDOW_MINUTES
      );

      if (count > 0) {
        ctx.reject('Potential wash trade detected (REG-2005-002)');
        return false;
      }
    } catch {
      // fail open on DB errors
    }

    return true;
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
