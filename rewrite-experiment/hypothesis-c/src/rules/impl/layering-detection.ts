import { Rule, RuleContext } from '../rule-engine';

/**
 * LayeringDetectionRule (priority 125, REG-2015-001)
 * Flags if >5 orders for the same client+symbol exist recently.
 * Does not reject - only flags for surveillance review.
 */
export class LayeringDetectionRule implements Rule {
  name = 'LayeringDetectionRule';
  priority = 125;
  active = true;

  static readonly THRESHOLD = 5;

  private lookupRecentOrderCount: (clientId: string, symbol: string) => number;

  constructor(lookupFn?: (clientId: string, symbol: string) => number) {
    this.lookupRecentOrderCount = lookupFn || (() => 0);
  }

  evaluate(ctx: RuleContext): boolean {
    ctx.attributes.set('layering_checked', true);

    const recentOrderCount = this.lookupRecentOrderCount(ctx.client.clientId, ctx.order.symbol);
    ctx.attributes.set('layering_order_count', recentOrderCount);

    if (recentOrderCount > LayeringDetectionRule.THRESHOLD) {
      ctx.attributes.set('layering_status', 'FLAGGED');
      const existing = (ctx.attributes.get('surveillance_flags') as string) || '';
      ctx.attributes.set('surveillance_flags', existing ? `${existing},LAYERING` : 'LAYERING');
      ctx.warnings.push(`Layering pattern detected: ${recentOrderCount} recent orders for ${ctx.order.symbol}`);
    } else {
      ctx.attributes.set('layering_status', 'CLEAR');
    }

    return true;
  }
}
