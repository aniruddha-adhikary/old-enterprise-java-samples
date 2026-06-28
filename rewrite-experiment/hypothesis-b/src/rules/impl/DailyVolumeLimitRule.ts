import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-007: Compliance — per-order volume check (despite the name)
// Priority 110, Category: Compliance, Behavior: REJECT
// Threshold: maxSharesPerOrder = 50000
// Note: Per-order check; cumulative daily tracking never implemented (DBA said too slow)
export class DailyVolumeLimitRule extends BaseRule {
  name = 'DailyVolumeLimitRule';
  priority = 110;
  category = 'Compliance';
  failOpen = false;

  private readonly MAX_SHARES_PER_ORDER = 50_000;

  evaluate(ctx: RuleContext): RuleResult {
    setAttribute(ctx, 'daily_volume_checked', true);

    if (ctx.order.quantity > this.MAX_SHARES_PER_ORDER) {
      setAttribute(ctx, 'compliance_flags', 'VOLUME_EXCEEDED');
      rejectContext(ctx, `Order quantity ${ctx.order.quantity} exceeds max ${this.MAX_SHARES_PER_ORDER} shares per order`);
      return 'FAIL';
    }

    setAttribute(ctx, 'compliance_flags', '');
    return 'PASS';
  }
}
