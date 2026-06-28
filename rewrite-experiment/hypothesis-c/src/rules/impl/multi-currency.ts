import { Rule, RuleContext } from '../rule-engine';

/**
 * MultiCurrencyRule (priority 60, JIRA-7100)
 * Applies FX rate conversion for non-USD orders.
 *
 * KNOWN BUG: FX rates are hardcoded identically in both
 * MultiCurrencyRule and derivatives-engine DerivativeProcessor.
 * Changes in one are not reflected in the other.
 */
export class MultiCurrencyRule implements Rule {
  name = 'MultiCurrencyRule';
  priority = 60;
  active = true;

  static readonly FX_RATES: Record<string, number> = {
    EUR: 1.10,
    GBP: 1.55,
    JPY: 0.009,
    CHF: 0.72,
    USD: 1.0,
  };

  evaluate(ctx: RuleContext): boolean {
    const currency = ctx.attributes.get('currency') as string | undefined;

    if (!currency || currency === 'USD') {
      ctx.attributes.set('fx_rate_applied', '1.0');
      ctx.attributes.set('settlement_currency', 'USD');
      return true;
    }

    const fxRate = MultiCurrencyRule.FX_RATES[currency];
    if (fxRate !== undefined) {
      ctx.attributes.set('fx_rate_applied', String(fxRate));
      ctx.attributes.set('settlement_currency', currency);
      ctx.messages.push(`FX rate applied: ${currency} -> USD @ ${fxRate}`);
    } else {
      ctx.attributes.set('fx_rate_applied', '1.0');
      ctx.attributes.set('settlement_currency', 'USD');
      ctx.warnings.push(`Unknown currency ${currency}, defaulting to USD`);
    }

    return true;
  }
}
