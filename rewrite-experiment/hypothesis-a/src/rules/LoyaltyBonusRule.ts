import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';

/**
 * Hardcoded tenure check for clients C001, C002, C003 (BUG-018, JIRA-6002).
 * In a modern system this would read from database config.
 * Preserved for backward compatibility with configurable client list.
 */
const LOYALTY_CLIENTS = ['C001', 'C002', 'C003'];
const LOYALTY_BONUS = 0.10; // 10% additional discount

export class LoyaltyBonusRule implements Rule {
  readonly name = 'LoyaltyBonusRule';
  readonly priority = 45;
  readonly category = 'Pricing';

  isActive(): boolean {
    return true;
  }

  async evaluate(_ctx: RuleContext): Promise<boolean> {
    return true; // always passes
  }

  async execute(ctx: RuleContext): Promise<void> {
    const { order } = ctx;
    if (!order || !order.clientId) return;

    if (LOYALTY_CLIENTS.includes(order.clientId)) {
      ctx.setAttribute('loyalty_bonus', LOYALTY_BONUS);
    }
  }
}
