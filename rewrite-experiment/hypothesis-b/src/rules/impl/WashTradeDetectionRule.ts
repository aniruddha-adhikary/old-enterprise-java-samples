import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-008: Compliance — wash trade detection
// Priority 105, Category: Compliance, Behavior: REJECT
// Condition: Opposite-side order from same client+symbol within 5 minutes window
// Fail-open: true
export class WashTradeDetectionRule extends BaseRule {
  name = 'WashTradeDetectionRule';
  priority = 105;
  category = 'Compliance';
  failOpen = true;

  private readonly WASH_TRADE_WINDOW_MINUTES = 5;
  private recentOrderProvider: ((clientId: string, symbol: string, oppositeSide: string, windowMinutes: number) => number) | null = null;

  setRecentOrderProvider(fn: (clientId: string, symbol: string, oppositeSide: string, windowMinutes: number) => number): void {
    this.recentOrderProvider = fn;
  }

  evaluate(ctx: RuleContext): RuleResult {
    try {
      const { clientId, symbol, side } = ctx.order;
      const oppositeSide = side === 'BUY' ? 'SELL' : 'BUY';

      let matchCount = 0;
      if (this.recentOrderProvider) {
        matchCount = this.recentOrderProvider(clientId, symbol, oppositeSide, this.WASH_TRADE_WINDOW_MINUTES);
      }

      setAttribute(ctx, 'wash_trade_checked', true);

      if (matchCount > 0) {
        rejectContext(ctx, `Wash trade detected: opposite-side order exists within ${this.WASH_TRADE_WINDOW_MINUTES} min window`);
        return 'FAIL';
      }

      return 'PASS';
    } catch {
      // Fail-open
      setAttribute(ctx, 'wash_trade_checked', true);
      return 'PASS';
    }
  }
}
