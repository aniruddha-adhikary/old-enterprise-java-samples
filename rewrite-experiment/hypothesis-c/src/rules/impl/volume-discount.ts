import { Rule, RuleContext } from '../rule-engine';

/**
 * VolumeDiscountRule (priority 55)
 * Applies commission discount based on order quantity:
 *   >10,000 shares: 50% discount
 *   >5,000 shares: 25% discount
 */
export class VolumeDiscountRule implements Rule {
  name = 'VolumeDiscountRule';
  priority = 55;
  active = true;

  static readonly TIER_1_THRESHOLD = 5000;
  static readonly TIER_1_DISCOUNT = 0.25;
  static readonly TIER_2_THRESHOLD = 10000;
  static readonly TIER_2_DISCOUNT = 0.50;

  evaluate(ctx: RuleContext): boolean {
    if (ctx.order.quantity > VolumeDiscountRule.TIER_2_THRESHOLD) {
      ctx.attributes.set('volume_discount', VolumeDiscountRule.TIER_2_DISCOUNT);
      ctx.attributes.set('volume_discount_applied', true);
      ctx.messages.push(`Volume discount applied: 50% (${ctx.order.quantity} shares > ${VolumeDiscountRule.TIER_2_THRESHOLD})`);
    } else if (ctx.order.quantity > VolumeDiscountRule.TIER_1_THRESHOLD) {
      ctx.attributes.set('volume_discount', VolumeDiscountRule.TIER_1_DISCOUNT);
      ctx.attributes.set('volume_discount_applied', true);
      ctx.messages.push(`Volume discount applied: 25% (${ctx.order.quantity} shares > ${VolumeDiscountRule.TIER_1_THRESHOLD})`);
    }

    return true;
  }
}
