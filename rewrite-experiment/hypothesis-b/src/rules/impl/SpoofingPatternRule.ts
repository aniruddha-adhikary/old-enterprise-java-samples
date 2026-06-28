import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, addWarning } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-002: Surveillance rule — detects potential spoofing via cancel rate
// Priority 124, Category: Surveillance, Behavior: FLAG
// Threshold: cancelRateThreshold = 0.60 (60%)
export class SpoofingPatternRule extends BaseRule {
  name = 'SpoofingPatternRule';
  priority = 124;
  category = 'Surveillance';
  failOpen = true;

  private readonly CANCEL_RATE_THRESHOLD = 0.60;
  private cancelRateProvider: ((clientId: string) => { cancelled: number; total: number }) | null = null;

  setCancelRateProvider(fn: (clientId: string) => { cancelled: number; total: number }): void {
    this.cancelRateProvider = fn;
  }

  evaluate(ctx: RuleContext): RuleResult {
    try {
      const { clientId } = ctx.order;
      let cancelRate = 0;

      if (this.cancelRateProvider) {
        const { cancelled, total } = this.cancelRateProvider(clientId);
        cancelRate = total > 0 ? cancelled / total : 0;
      }

      setAttribute(ctx, 'spoofing_checked', true);
      setAttribute(ctx, 'spoofing_cancel_rate', cancelRate);

      if (cancelRate > this.CANCEL_RATE_THRESHOLD) {
        setAttribute(ctx, 'spoofing_status', 'FLAGGED');
        const flags = ctx.order.surveillanceFlags
          ? `${ctx.order.surveillanceFlags},SPOOFING`
          : 'SPOOFING';
        setAttribute(ctx, 'surveillance_flags', flags);
        addWarning(ctx, `Spoofing pattern: cancel rate ${(cancelRate * 100).toFixed(1)}% for ${clientId}`);
      } else {
        setAttribute(ctx, 'spoofing_status', 'CLEAR');
      }

      return 'PASS'; // FLAG behavior: never rejects
    } catch {
      setAttribute(ctx, 'spoofing_checked', true);
      setAttribute(ctx, 'spoofing_status', 'ERROR');
      return 'PASS';
    }
  }
}
