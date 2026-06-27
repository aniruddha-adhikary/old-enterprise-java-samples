import { Rule, PositionRepository, RuleAuditLogger } from './Rule';
import { RuleContext } from '../domain/RuleContext';
import { OrderSide } from '../domain/enums';

const POSITION_LIMIT = 100000;

export class PositionLimitRule implements Rule {
  readonly name = 'PositionLimitRule';
  readonly priority = 123;
  readonly category = 'Surveillance';

  constructor(
    private readonly positionRepo: PositionRepository,
    private readonly auditLogger: RuleAuditLogger
  ) {}

  isActive(): boolean {
    return true;
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    const { order } = ctx;
    if (!order || !order.clientId || !order.symbol) return true;

    try {
      const currentPosition = await this.positionRepo.getNetPosition(
        order.clientId,
        order.symbol
      );

      let newPosition: number;
      if (currentPosition === null) {
        if (order.quantity > POSITION_LIMIT) {
          ctx.reject(
            `Position limit exceeded: order quantity ${order.quantity} > ${POSITION_LIMIT}`
          );

          await this.auditLogger.logSurveillanceDecision(
            this.name, order.orderId, order.clientId, order.symbol,
            'REJECT', '',
            `No position record, qty ${order.quantity} > limit ${POSITION_LIMIT}`
          );
          return false;
        }
        return true;
      }

      if (order.side === OrderSide.BUY) {
        newPosition = Math.abs(currentPosition + order.quantity);
      } else {
        newPosition = Math.abs(currentPosition - order.quantity);
      }

      if (newPosition > POSITION_LIMIT) {
        ctx.reject(
          `Position limit exceeded: projected position ${newPosition} > ${POSITION_LIMIT}`
        );

        await this.auditLogger.logSurveillanceDecision(
          this.name, order.orderId, order.clientId, order.symbol,
          'REJECT', '',
          `Projected position ${newPosition} > limit ${POSITION_LIMIT}`
        );
        return false;
      }

      return true;
    } catch {
      // fail open on DB errors (REG-2015-003)
      return true;
    }
  }

  async execute(_ctx: RuleContext): Promise<void> {
    // no-op
  }
}
