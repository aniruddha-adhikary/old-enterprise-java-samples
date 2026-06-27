import { Rule, RuleContext } from '../rule-engine';

/**
 * SpoofingPatternRule (priority 124, REG-2015-002)
 * Flags if >60% of a client's recent orders were cancelled.
 * Does not reject - only flags for surveillance review.
 */
export class SpoofingPatternRule implements Rule {
  name = 'SpoofingPatternRule';
  priority = 124;
  active = true;

  static readonly CANCEL_RATE_THRESHOLD = 0.60;

  private lookupCancelRate: (clientId: string) => { totalOrders: number; cancelledOrders: number } | null;

  constructor(lookupFn?: (clientId: string) => { totalOrders: number; cancelledOrders: number } | null) {
    this.lookupCancelRate = lookupFn || (() => null);
  }

  evaluate(ctx: RuleContext): boolean {
    ctx.attributes.set('spoofing_checked', true);

    const stats = this.lookupCancelRate(ctx.client.clientId);

    if (!stats || stats.totalOrders === 0) {
      ctx.attributes.set('spoofing_status', 'NO_HISTORY');
      return true;
    }

    const cancelRate = stats.cancelledOrders / stats.totalOrders;
    ctx.attributes.set('spoofing_cancel_rate', cancelRate);

    if (cancelRate > SpoofingPatternRule.CANCEL_RATE_THRESHOLD) {
      ctx.attributes.set('spoofing_status', 'FLAGGED');
      const existing = (ctx.attributes.get('surveillance_flags') as string) || '';
      ctx.attributes.set('surveillance_flags', existing ? `${existing},SPOOFING` : 'SPOOFING');
      ctx.warnings.push(`Spoofing pattern detected: ${(cancelRate * 100).toFixed(1)}% cancellation rate`);
    } else {
      ctx.attributes.set('spoofing_status', 'CLEAR');
    }

    return true;
  }
}
