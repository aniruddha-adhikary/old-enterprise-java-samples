import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * DailyVolumeLimitRule (priority 110, REG-2005-001)
 * Max 50,000 shares per single order.
 *
 * KNOWN BUG: The original checks per-order quantity, not cumulative
 * daily volume. The comment says "simplified from daily aggregate
 * to per-order check after the DBA complained about the aggregate
 * query being too slow." (DailyVolumeLimitRule.java line 290)
 */
export class DailyVolumeLimitRule implements Rule {
  name = 'DailyVolumeLimitRule';
  priority = 110;
  active = true;

  static readonly MAX_VOLUME = 50_000;

  evaluate(ctx: RuleContext): boolean {
    ctx.attributes.set('daily_volume_checked', true);

    if (ctx.order.quantity > DailyVolumeLimitRule.MAX_VOLUME) {
      rejectContext(ctx, `Order quantity ${ctx.order.quantity} exceeds daily volume limit of ${DailyVolumeLimitRule.MAX_VOLUME} (REG-2005-001)`);
      return false;
    }

    ctx.attributes.set('compliance_flags', 'VOLUME_CHECKED');
    return true;
  }
}
