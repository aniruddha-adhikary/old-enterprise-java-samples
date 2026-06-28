import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-003: Surveillance rule — position limit check
// Priority 123, Category: Surveillance, Behavior: REJECT
// Threshold: defaultPositionLimit = 100000
// JIRA-8200: Position limit should be configurable per client but hardcoded for now
export class PositionLimitRule extends BaseRule {
  name = 'PositionLimitRule';
  priority = 123;
  category = 'Surveillance';
  failOpen = true;

  private readonly DEFAULT_POSITION_LIMIT = 100_000;
  private positionProvider: ((clientId: string, symbol: string) => number) | null = null;

  setPositionProvider(fn: (clientId: string, symbol: string) => number): void {
    this.positionProvider = fn;
  }

  evaluate(ctx: RuleContext): RuleResult {
    try {
      const { clientId, symbol, quantity, side } = ctx.order;
      let currentPosition = 0;

      if (this.positionProvider) {
        currentPosition = this.positionProvider(clientId, symbol);
      }

      setAttribute(ctx, 'position_limit_checked', true);
      setAttribute(ctx, 'current_position', currentPosition);

      const projectedPosition = side === 'BUY'
        ? currentPosition + quantity
        : currentPosition - quantity;

      if (Math.abs(projectedPosition) > this.DEFAULT_POSITION_LIMIT) {
        setAttribute(ctx, 'position_status', 'EXCEEDED');
        rejectContext(ctx, `Position limit exceeded: projected ${projectedPosition}, limit ${this.DEFAULT_POSITION_LIMIT}`);
        return 'FAIL';
      }

      setAttribute(ctx, 'position_status', 'WITHIN_LIMITS');
      return 'PASS';
    } catch {
      // Fail-open
      setAttribute(ctx, 'position_limit_checked', true);
      setAttribute(ctx, 'position_status', 'ERROR');
      return 'PASS';
    }
  }
}
