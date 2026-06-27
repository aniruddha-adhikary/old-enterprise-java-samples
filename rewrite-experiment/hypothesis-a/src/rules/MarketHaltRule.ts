import { Rule } from './Rule';
import { RuleContext } from '../domain/RuleContext';

export class MarketHaltRule implements Rule {
  readonly name = 'MarketHaltRule';
  readonly priority = 120;
  readonly category = 'CircuitBreaker';

  constructor(private readonly isMarketHalted: () => boolean) {}

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    try {
      if (this.isMarketHalted()) {
        ctx.reject('MARKET HALTED -- trading suspended (REG-2011-001)');
        return false;
      }
    } catch {
      // SecurityManager errors default to not halted
    }
    return true;
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
