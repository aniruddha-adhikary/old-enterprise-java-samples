import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-010: Business — restricted symbol check
// Priority 95, Category: Business, Behavior: REJECT
// Hardcoded restricted list; TODO: move to database table (JIRA-4100)
export class RestrictedSymbolRule extends BaseRule {
  name = 'RestrictedSymbolRule';
  priority = 95;
  category = 'Business';
  failOpen = false;

  private readonly RESTRICTED_SYMBOLS = ['ENRN', 'WCOM', 'TYCO', 'ADLP'];

  evaluate(ctx: RuleContext): RuleResult {
    const symbol = ctx.order.symbol.toUpperCase();

    if (this.RESTRICTED_SYMBOLS.includes(symbol)) {
      setAttribute(ctx, 'restricted_check', 'BLOCKED');
      rejectContext(ctx, `Symbol ${symbol} is restricted from trading`);
      return 'FAIL';
    }

    setAttribute(ctx, 'restricted_check', 'CLEAR');
    return 'PASS';
  }
}
