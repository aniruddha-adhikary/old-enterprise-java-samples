import { Rule, RuleContext } from '../rule-engine';

/**
 * MarketHoursRule (priority 80)
 * Orders outside 9:30 AM - 4:00 PM are queued (not rejected).
 *
 * KNOWN BUG: Uses server local time instead of Eastern time.
 * The original MarketHoursRule.java (line 748) uses Calendar.getInstance()
 * without specifying timezone, so it depends on server locale.
 * We preserve this bug for behavioral compatibility.
 */
export class MarketHoursRule implements Rule {
  name = 'MarketHoursRule';
  priority = 80;
  active = true;

  static readonly MARKET_OPEN_HOUR = 9;
  static readonly MARKET_OPEN_MINUTE = 30;
  static readonly MARKET_CLOSE_HOUR = 16;
  static readonly MARKET_CLOSE_MINUTE = 0;

  private getNow: () => Date;

  constructor(nowFn?: () => Date) {
    this.getNow = nowFn || (() => new Date());
  }

  evaluate(ctx: RuleContext): boolean {
    const now = this.getNow();
    // BUG preserved: uses local time, not Eastern
    const hour = now.getHours();
    const minute = now.getMinutes();
    const timeInMinutes = hour * 60 + minute;

    const openTime = MarketHoursRule.MARKET_OPEN_HOUR * 60 + MarketHoursRule.MARKET_OPEN_MINUTE;
    const closeTime = MarketHoursRule.MARKET_CLOSE_HOUR * 60 + MarketHoursRule.MARKET_CLOSE_MINUTE;

    if (timeInMinutes < openTime || timeInMinutes >= closeTime) {
      ctx.attributes.set('queued', true);
      ctx.messages.push('Order queued: outside market hours (9:30 AM - 4:00 PM)');
    }

    return true;
  }
}
