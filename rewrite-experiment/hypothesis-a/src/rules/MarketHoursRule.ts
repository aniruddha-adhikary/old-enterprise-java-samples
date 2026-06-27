import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';

/**
 * Market hours: 9:30 AM to 4:00 PM server local time.
 * Known bug preserved: uses server local time, NOT Eastern Time (BUG-003).
 * Weekend and outside-hours orders are queued (not rejected) with warning.
 * Known bug preserved: sets queued=true attribute but no downstream code
 * actually defers execution (BUG-020).
 */
export class MarketHoursRule implements Rule {
  readonly name = 'MarketHoursRule';
  readonly priority = 80;
  readonly category = 'Business';

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const now = new Date();
    const day = now.getDay();
    const hour = now.getHours();
    const minute = now.getMinutes();
    const timeInMinutes = hour * 60 + minute;

    const marketOpen = 9 * 60 + 30;  // 9:30 AM
    const marketClose = 16 * 60;      // 4:00 PM

    if (day === 0 || day === 6) {
      ctx.setAttribute('queued', true);
      ctx.addWarning('Weekend order -- will be queued for next trading day');
      return true; // queued, not rejected
    }

    if (timeInMinutes < marketOpen || timeInMinutes >= marketClose) {
      ctx.setAttribute('queued', true);
      ctx.addWarning('Outside market hours -- order will be queued');
      return true; // queued, not rejected
    }

    return true;
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
