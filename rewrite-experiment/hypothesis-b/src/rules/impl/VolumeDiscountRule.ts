import { BaseRule } from '../Rule';
import { RuleContext, setAttribute } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-015: Pricing — volume-based commission discount
// Priority 55, Category: Pricing, Behavior: PASS
// Always passes. Adjusts commission discount on execute().
// BUG-016: Uses hardcoded 0.02 base commission instead of CommissionCalculator (JIRA-6001)
export class VolumeDiscountRule extends BaseRule {
  name = 'VolumeDiscountRule';
  priority = 55;
  category = 'Pricing';
  failOpen = true;

  private readonly LARGE_ORDER_THRESHOLD = 5000;
  private readonly LARGE_ORDER_DISCOUNT = 0.25; // 25%
  private readonly VERY_LARGE_ORDER_THRESHOLD = 10000;
  private readonly VERY_LARGE_ORDER_DISCOUNT = 0.50; // 50%
  private readonly BASE_COMMISSION = 0.02; // BUG-016: hardcoded

  evaluate(_ctx: RuleContext): RuleResult {
    return 'PASS'; // Always passes
  }

  execute(ctx: RuleContext): void {
    const quantity = ctx.order.quantity;
    let discount = 0;

    if (quantity > this.VERY_LARGE_ORDER_THRESHOLD) {
      discount = this.VERY_LARGE_ORDER_DISCOUNT;
    } else if (quantity > this.LARGE_ORDER_THRESHOLD) {
      discount = this.LARGE_ORDER_DISCOUNT;
    }

    if (discount > 0) {
      setAttribute(ctx, 'volume_discount', discount);
      setAttribute(ctx, 'volume_discount_applied', true);
    } else {
      setAttribute(ctx, 'volume_discount', 0);
      setAttribute(ctx, 'volume_discount_applied', false);
    }
  }
}
