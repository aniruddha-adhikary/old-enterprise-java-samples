import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-004: Circuit breaker — rejects all orders when market is halted
// Priority 120, Category: CircuitBreaker, Behavior: REJECT
// No exceptions, no overrides. SecurityManager errors default to not halted.
export class MarketHaltRule extends BaseRule {
  name = 'MarketHaltRule';
  priority = 120;
  category = 'CircuitBreaker';
  failOpen = false;

  private marketHaltedProvider: (() => boolean) | null = null;

  setMarketHaltedProvider(fn: () => boolean): void {
    this.marketHaltedProvider = fn;
  }

  evaluate(ctx: RuleContext): RuleResult {
    try {
      let halted = false;

      if (this.marketHaltedProvider) {
        halted = this.marketHaltedProvider();
      } else {
        halted = process.env['BIGCORP_MARKET_HALTED'] === 'true';
      }

      setAttribute(ctx, 'market_halt_checked', true);

      if (halted) {
        rejectContext(ctx, 'Market is halted — all trading suspended');
        return 'FAIL';
      }

      return 'PASS';
    } catch {
      // SecurityManager errors default to not halted
      setAttribute(ctx, 'market_halt_checked', true);
      return 'PASS';
    }
  }
}
