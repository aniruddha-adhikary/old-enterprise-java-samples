import { Rule, RuleContext, rejectContext } from '../rule-engine';

/**
 * RestrictedSymbolRule (priority 95)
 * Blocks trading of restricted/delisted symbols.
 * Original list: ENRN, WCOM, TYCO, ADLP (fraud-related delistings).
 */
export class RestrictedSymbolRule implements Rule {
  name = 'RestrictedSymbolRule';
  priority = 95;
  active = true;

  static readonly RESTRICTED_SYMBOLS = new Set(['ENRN', 'WCOM', 'TYCO', 'ADLP']);

  evaluate(ctx: RuleContext): boolean {
    if (RestrictedSymbolRule.RESTRICTED_SYMBOLS.has(ctx.order.symbol)) {
      rejectContext(ctx, `Symbol ${ctx.order.symbol} is restricted from trading`);
      return false;
    }

    ctx.attributes.set('restricted_check', 'passed');
    return true;
  }
}
