import { BaseRule } from '../Rule';
import { RuleContext, setAttribute, rejectContext, addWarning } from '../../domain/RuleContext';
import type { RuleResult } from '../Rule';

// RUL-001: Surveillance rule — detects potential layering patterns
// Priority 125, Category: Surveillance, Behavior: FLAG (warn but don't reject)
// Fail-open: if DB query fails, allow the order
export class LayeringDetectionRule extends BaseRule {
  name = 'LayeringDetectionRule';
  priority = 125;
  category = 'Surveillance';
  failOpen = true;

  private readonly LAYERING_ORDER_COUNT_THRESHOLD = 5;
  private orderCountProvider: ((clientId: string, symbol: string) => number) | null = null;

  setOrderCountProvider(fn: (clientId: string, symbol: string) => number): void {
    this.orderCountProvider = fn;
  }

  evaluate(ctx: RuleContext): RuleResult {
    try {
      const { clientId, symbol } = ctx.order;
      let orderCount = 0;

      if (this.orderCountProvider) {
        orderCount = this.orderCountProvider(clientId, symbol);
      }

      setAttribute(ctx, 'layering_checked', true);
      setAttribute(ctx, 'layering_order_count', orderCount);

      if (orderCount > this.LAYERING_ORDER_COUNT_THRESHOLD) {
        setAttribute(ctx, 'layering_status', 'FLAGGED');
        const flags = ctx.order.surveillanceFlags
          ? `${ctx.order.surveillanceFlags},LAYERING`
          : 'LAYERING';
        setAttribute(ctx, 'surveillance_flags', flags);
        addWarning(ctx, `Layering detected: ${orderCount} orders for ${clientId}/${symbol}`);
        return 'PASS'; // FLAG behavior: warn but don't reject
      }

      setAttribute(ctx, 'layering_status', 'CLEAR');
      return 'PASS';
    } catch {
      // Fail-open: allow on error
      setAttribute(ctx, 'layering_checked', true);
      setAttribute(ctx, 'layering_status', 'ERROR');
      return 'PASS';
    }
  }
}
