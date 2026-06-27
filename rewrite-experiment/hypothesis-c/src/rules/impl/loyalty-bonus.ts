import { Rule, RuleContext } from '../rule-engine';

/**
 * LoyaltyBonusRule (priority 45)
 * 10% commission discount for clients active >5 years.
 *
 * KNOWN BUG: In the original, this was hardcoded for specific clients
 * (C001, C002, C003) rather than checking actual account age from the
 * database. We preserve this behavior but make it configurable.
 */
export class LoyaltyBonusRule implements Rule {
  name = 'LoyaltyBonusRule';
  priority = 45;
  active = true;

  static readonly BONUS_RATE = 0.10; // 10% discount
  static readonly MIN_YEARS = 5;

  private loyalClients: Set<string>;

  constructor(loyalClients?: string[]) {
    this.loyalClients = new Set(loyalClients || ['C001', 'C002', 'C003']);
  }

  evaluate(ctx: RuleContext): boolean {
    if (this.loyalClients.has(ctx.client.clientId)) {
      ctx.attributes.set('loyalty_bonus', LoyaltyBonusRule.BONUS_RATE);
      ctx.messages.push(`Loyalty bonus applied: ${(LoyaltyBonusRule.BONUS_RATE * 100)}% discount for ${ctx.client.clientId}`);
    }

    return true;
  }
}
