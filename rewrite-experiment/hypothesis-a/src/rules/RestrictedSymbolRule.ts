import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';

const RESTRICTED_SYMBOLS = ['ENRN', 'WCOM', 'TYCO', 'ADLP'];

export class RestrictedSymbolRule implements Rule {
  readonly name = 'RestrictedSymbolRule';
  readonly priority = 95;
  readonly category = 'Business';

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order || !order.symbol) return true;

    if (RESTRICTED_SYMBOLS.includes(order.symbol)) {
      ctx.reject(
        `Restricted symbol: ${order.symbol} -- trading suspended`
      );
      return false;
    }

    return true;
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
