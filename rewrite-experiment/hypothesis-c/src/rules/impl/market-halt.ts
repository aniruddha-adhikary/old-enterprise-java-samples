import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * MarketHaltRule (priority 120, REG-2011-001)
 * Rejects ALL orders when the market is halted (circuit breaker).
 * Controlled by system property "bigcorp.market.halted".
 * Added after the 2011 flash crash.
 */
export class MarketHaltRule implements Rule {
  name = 'MarketHaltRule';
  priority = 120;
  active = true;

  private marketHalted: () => boolean;

  constructor(marketHaltedFn?: () => boolean) {
    this.marketHalted = marketHaltedFn || (() => process.env.BIGCORP_MARKET_HALTED === 'true');
  }

  evaluate(ctx: RuleContext): boolean {
    ctx.attributes.set('market_halt_checked', true);

    if (this.marketHalted()) {
      rejectContext(ctx, 'Market is halted - all trading suspended (REG-2011-001)');
      ctx.messages.push('Order rejected: market halt in effect');
      return false;
    }

    return true;
  }
}
