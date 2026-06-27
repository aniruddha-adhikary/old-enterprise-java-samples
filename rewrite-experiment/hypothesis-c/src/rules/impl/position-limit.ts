import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * PositionLimitRule (priority 123, REG-2015-003)
 * Max 100,000 shares net position per symbol per client.
 * Added after 2015 SEC inquiry.
 */
export class PositionLimitRule implements Rule {
  name = 'PositionLimitRule';
  priority = 123;
  active = true;

  static readonly MAX_POSITION = 100_000;

  private lookupPosition: (clientId: string, symbol: string) => number;

  constructor(lookupFn?: (clientId: string, symbol: string) => number) {
    this.lookupPosition = lookupFn || (() => 0);
  }

  evaluate(ctx: RuleContext): boolean {
    ctx.attributes.set('position_limit_checked', true);

    const currentPosition = this.lookupPosition(ctx.client.clientId, ctx.order.symbol);
    ctx.attributes.set('current_position', currentPosition);

    const orderQuantity = ctx.order.side === 'BUY' ? ctx.order.quantity : -ctx.order.quantity;
    const newPosition = Math.abs(currentPosition + orderQuantity);

    if (newPosition > PositionLimitRule.MAX_POSITION) {
      rejectContext(ctx, `Position limit exceeded: ${newPosition} shares (max ${PositionLimitRule.MAX_POSITION}) for ${ctx.order.symbol}`);
      ctx.attributes.set('position_status', 'REJECTED');
      return false;
    }

    ctx.attributes.set('position_status', currentPosition === 0 ? 'NEW_POSITION' : 'WITHIN_LIMIT');
    return true;
  }
}
