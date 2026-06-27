import { Rule, OrderRepository, RuleAuditLogger } from './Rule';
import { RuleContext } from '../domain/RuleContext';

const LAYERING_THRESHOLD = 5;

export class LayeringDetectionRule implements Rule {
  readonly name = 'LayeringDetectionRule';
  readonly priority = 125;
  readonly category = 'Surveillance';

  constructor(
    private readonly orderRepo: OrderRepository,
    private readonly auditLogger: RuleAuditLogger
  ) {}

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order || !order.clientId || !order.symbol) return true;

    try {
      const count = await this.orderRepo.countNonCancelledOrders(
        order.clientId,
        order.symbol
      );

      if (count > LAYERING_THRESHOLD) {
        const existing = (ctx.getAttribute('surveillance_flags') as string) || '';
        const flags = existing ? `${existing},LAYERING` : 'LAYERING';
        ctx.setAttribute('surveillance_flags', flags);
        ctx.addWarning(`Layering detected: ${count} orders for ${order.symbol}`);

        await this.auditLogger.logSurveillanceDecision(
          this.name, order.orderId, order.clientId, order.symbol,
          'FLAG', flags,
          `Order count ${count} > threshold ${LAYERING_THRESHOLD}`
        );
      }
    } catch {
      // fail open on DB errors (REG-2015-001)
    }

    return true; // never rejects
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
