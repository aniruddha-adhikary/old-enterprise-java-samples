import { Rule, RuleContext, rejectContext } from '../rule-engine';
import { TradeOrder } from '../../domain/trade-order';

/**
 * WashTradeDetectionRule (priority 105, REG-2005-002)
 * Detects same client BUY/SELL of the same symbol within 5 minutes.
 */
export class WashTradeDetectionRule implements Rule {
  name = 'WashTradeDetectionRule';
  priority = 105;
  active = true;

  static readonly WINDOW_MS = 5 * 60 * 1000; // 5 minutes

  private lookupRecentOrders: (clientId: string, symbol: string, windowMs: number) => TradeOrder[];

  constructor(lookupFn?: (clientId: string, symbol: string, windowMs: number) => TradeOrder[]) {
    this.lookupRecentOrders = lookupFn || (() => []);
  }

  evaluate(ctx: RuleContext): boolean {
    ctx.attributes.set('wash_trade_checked', true);

    const recentOrders = this.lookupRecentOrders(
      ctx.client.clientId,
      ctx.order.symbol,
      WashTradeDetectionRule.WINDOW_MS
    );

    const oppositeSide = ctx.order.side === 'BUY' ? 'SELL' : 'BUY';
    const hasOpposite = recentOrders.some(o => o.side === oppositeSide);

    if (hasOpposite) {
      rejectContext(ctx, `Potential wash trade detected: ${ctx.order.side} after recent ${oppositeSide} for ${ctx.order.symbol} within 5 minutes`);
      ctx.messages.push('Order rejected: wash trade pattern detected');
      return false;
    }

    return true;
  }
}
