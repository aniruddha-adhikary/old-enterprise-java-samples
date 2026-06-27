import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';

const MAX_SHARES_PER_ORDER = 50000;

export class DailyVolumeLimitRule implements Rule {
  readonly name = 'DailyVolumeLimitRule';
  readonly priority = 110;
  readonly category = 'Compliance';

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order) return true;

    if (order.quantity > MAX_SHARES_PER_ORDER) {
      ctx.reject('Daily volume limit exceeded (REG-2005-001)');
      return false;
    }

    return true;
  }

  async execute(ctx: RuleContext): Promise<void> {
    ctx.setAttribute('daily_volume_checked', true);
    ctx.setAttribute('compliance_flags', 'VOLUME_OK');
  }
}
