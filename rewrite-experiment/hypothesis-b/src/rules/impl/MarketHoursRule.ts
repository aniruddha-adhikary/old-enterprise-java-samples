import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, addWarning } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-012: Business — market hours check (9:30-16:00, weekdays)
// Priority 80, Category: Business, Behavior: PASS_WITH_QUEUE
// BUG-003: Uses server local time, not Eastern Time (preserved)
// BUG-020: Sets queued=true attribute but no downstream code actually defers execution
export class MarketHoursRule extends BaseRule {
  name = 'MarketHoursRule';
  priority = 80;
  category = 'Business';
  failOpen = true;

  private readonly MARKET_OPEN_HOUR = 9;
  private readonly MARKET_OPEN_MINUTE = 30;
  private readonly MARKET_CLOSE_HOUR = 16;
  private readonly MARKET_CLOSE_MINUTE = 0;

  private timeProvider: (() => Date) | null = null;

  setTimeProvider(fn: () => Date): void {
    this.timeProvider = fn;
  }

  evaluate(ctx: RuleContext): RuleResult {
    // BUG-003: Uses server local time instead of Eastern Time
    const now = this.timeProvider ? this.timeProvider() : new Date();
    const day = now.getDay();
    const hour = now.getHours();
    const minute = now.getMinutes();

    const isWeekend = day === 0 || day === 6;
    const currentMinutes = hour * 60 + minute;
    const openMinutes = this.MARKET_OPEN_HOUR * 60 + this.MARKET_OPEN_MINUTE;
    const closeMinutes = this.MARKET_CLOSE_HOUR * 60 + this.MARKET_CLOSE_MINUTE;

    const isOutsideHours = currentMinutes < openMinutes || currentMinutes >= closeMinutes;

    if (isWeekend || isOutsideHours) {
      // BUG-020: Sets queued=true but nothing downstream actually queues
      setAttribute(ctx, 'queued', true);
      addWarning(ctx, `Order placed outside market hours (${hour}:${String(minute).padStart(2, '0')}, day=${day})`);
    } else {
      setAttribute(ctx, 'queued', false);
    }

    return 'PASS'; // PASS_WITH_QUEUE: never rejects
  }
}
