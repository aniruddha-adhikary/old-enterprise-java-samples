import { AuditEvent, BillingEntry, RuleAuditEntry, SurveillanceAuditEntry } from '../domain/AuditEvent';
import { TradeOrder } from '../domain/TradeOrder';
import { Client } from '../domain/Client';
import { OrderStatus } from '../domain/enums';
import { calculateCommission } from '../pricing/CommissionCalculator';

export interface AuditRepository {
  saveAuditEvent(event: AuditEvent): Promise<void>;
  saveBillingEntry(entry: BillingEntry): Promise<void>;
  saveRuleAuditEntry(entry: RuleAuditEntry): Promise<void>;
  saveSurveillanceAuditEntry(entry: SurveillanceAuditEntry): Promise<void>;
  getClient(clientId: string): Promise<Client | null>;
}

export class AuditService {
  constructor(private readonly repo: AuditRepository) {}

  async processOrderEvent(order: TradeOrder): Promise<void> {
    const eventType =
      order.status === OrderStatus.FILLED
        ? 'ORDER_FILLED'
        : order.status === OrderStatus.REJECTED
          ? 'ORDER_REJECTED'
          : order.status === OrderStatus.SETTLED
            ? 'ORDER_SETTLED'
            : `ORDER_${order.status}`;

    await this.repo.saveAuditEvent({
      eventType,
      sourceSystem: 'order-engine',
      entityType: 'ORDER',
      entityId: order.orderId,
      description: `Order ${order.orderId} status: ${order.status}`,
      logDate: new Date(),
      userId: order.clientId,
    });

    if (order.status === OrderStatus.FILLED) {
      await this.createBillingEntry(order);
    }
  }

  private async createBillingEntry(order: TradeOrder): Promise<void> {
    const client = await this.repo.getClient(order.clientId);
    const grossAmount = order.quantity * order.price;
    const commissionAmount = calculateCommission(grossAmount, client?.tier);

    await this.repo.saveBillingEntry({
      orderId: order.orderId,
      clientId: order.clientId,
      grossAmount,
      commissionAmount,
      netAmount: grossAmount + commissionAmount,
      chargedDate: new Date(),
      status: 'CHARGED',
    });

    await this.repo.saveAuditEvent({
      eventType: 'BILLING_CHARGED',
      sourceSystem: 'audit-service',
      entityType: 'BILLING',
      entityId: order.orderId,
      description: `Billing charged: gross=${grossAmount}, commission=${commissionAmount}`,
      logDate: new Date(),
      userId: order.clientId,
    });
  }
}

export class RuleAuditService {
  constructor(private readonly repo: AuditRepository) {}

  async logRuleDecision(
    ruleName: string,
    orderId: string,
    clientId: string,
    result: string,
    details: string
  ): Promise<void> {
    try {
      await this.repo.saveRuleAuditEntry({
        ruleName,
        orderId,
        clientId,
        result,
        evaluationTime: new Date(),
        details,
      });
    } catch {
      // audit logging failures never prevent order processing (NFR-001)
    }
  }

  async logSurveillanceDecision(
    ruleName: string,
    orderId: string,
    clientId: string,
    symbol: string,
    result: string,
    flags: string,
    details: string
  ): Promise<void> {
    try {
      await this.repo.saveSurveillanceAuditEntry({
        ruleName,
        orderId,
        clientId,
        symbol,
        result,
        surveillanceFlags: flags,
        evaluationTime: new Date(),
        details,
      });
    } catch {
      // audit logging failures never prevent order processing (NFR-001)
    }
  }
}
