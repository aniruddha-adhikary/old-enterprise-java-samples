import { BaseRule } from '../Rule';
import { RuleContext, setAttribute } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-014: Business — multi-currency FX rate assignment
// Priority 60, Category: Business, Behavior: PASS
// Sets FX rate context attributes. Unknown currencies default to USD (not rejected).
// BUG-012: FX rates copy-pasted from FxPricingHelper (JIRA-7101), hardcoded as of 2004-07-15
// BUG-017: Uses hardcoded 0.02 commission instead of CommissionCalculator (JIRA-7103)
export class MultiCurrencyRule extends BaseRule {
  name = 'MultiCurrencyRule';
  priority = 60;
  category = 'Business';
  failOpen = true;

  private readonly FX_RATES: Record<string, number> = {
    EUR: 1.10,
    GBP: 1.55,
    JPY: 0.009,
    CHF: 0.72,
  };

  private readonly COMMISSION_RATE = 0.02; // BUG-017: hardcoded instead of using CommissionCalculator

  evaluate(ctx: RuleContext): RuleResult {
    // Detect currency from symbol (e.g., "EUR/USD" -> EUR)
    const symbol = ctx.order.symbol;
    let currency = 'USD';

    if (symbol.includes('/')) {
      currency = symbol.split('/')[0];
    }

    const fxRate = this.FX_RATES[currency] ?? 1.0; // Unknown defaults to USD

    setAttribute(ctx, 'fx_rate_applied', fxRate);
    setAttribute(ctx, 'settlement_currency', currency);

    return 'PASS';
  }
}
