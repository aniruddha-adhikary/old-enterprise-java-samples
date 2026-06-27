import { Rule, OrderRepository, RuleAuditLogger } from './Rule';
import { RuleContext } from '../domain/RuleContext';

const SPOOFING_THRESHOLD = 0.60;

export class SpoofingPatternRule implements Rule {
  readonly name = 'SpoofingPatternRule';
  readonly priority = 124;
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
    if (!order || !order.clientId) return true;

    try {
      const cancelled = await this.orderRepo.getCancelledOrderCount(order.clientId);
      const total = await this.orderRepo.getTotalOrderCount(order.clientId);

      if (total > 0) {
        const cancelRate = cancelled / total;
        if (cancelRate > SPOOFING_THRESHOLD) {
          const existing = (ctx.getAttribute('surveillance_flags') as string) || '';
          const flags = existing ? `${existing},SPOOFING` : 'SPOOFING';
          ctx.setAttribute('surveillance_flags', flags);
          ctx.addWarning(
            `Spoofing pattern: cancel rate ${(cancelRate * 100).toFixed(1)}%`
          );

          await this.auditLogger.logSurveillanceDecision(
            this.name, order.orderId, order.clientId, order.symbol,
            'FLAG', flags,
            `Cancel rate ${cancelRate.toFixed(3)} > ${SPOOFING_THRESHOLD}`
          );
        }
      }
    } catch {
      // fail open on DB errors (REG-2015-002)
    }

    return true; // never rejects
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
