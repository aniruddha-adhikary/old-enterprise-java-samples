import { AuditEvent, BillingEntry, SourceSystem, EntityType } from '../domain/audit';
import { TradeOrder, OrderStatus } from '../domain/trade-order';
import { CommissionCalculator } from '../pricing/commission-calculator';

/**
 * Audit and billing service, preserving behavior from
 * audit-service AuditListener.java, AuditDAO.java, BillingDAO.java.
 *
 * Logs all order lifecycle events and creates billing ledger entries
 * for filled orders with tier-based commission.
 */
export class AuditService {
  private auditLog: AuditEvent[] = [];
  private billingLedger: BillingEntry[] = [];

  logEvent(event: AuditEvent): void {
    this.auditLog.push(event);
  }

  logOrderEvent(order: TradeOrder, eventType: string): void {
    const description = `client=${order.clientId} symbol=${order.symbol} qty=${order.quantity} side=${order.side} price=${order.price} status=${order.status}`;

    this.logEvent({
      eventType,
      sourceSystem: SourceSystem.ORDER_ENGINE,
      entityType: EntityType.ORDER,
      entityId: order.orderId,
      description,
      logDate: new Date(),
      userId: order.clientId,
    });
  }

  createBillingEntry(order: TradeOrder, clientTier: string): BillingEntry {
    const grossAmount = order.quantity * order.price;
    const commission = CommissionCalculator.calculate(grossAmount, clientTier);
    const netAmount = grossAmount + commission;

    const entry: BillingEntry = {
      orderId: order.orderId,
      clientId: order.clientId,
      grossAmount,
      commissionAmount: commission,
      netAmount,
      chargedDate: new Date(),
      status: 'CHARGED',
    };

    this.billingLedger.push(entry);

    // Also log the billing event
    this.logEvent({
      eventType: 'BILLING_CHARGED',
      sourceSystem: SourceSystem.AUDIT_SERVICE,
      entityType: EntityType.BILLING,
      entityId: order.orderId,
      description: `gross=${grossAmount} commission=${commission} net=${netAmount} tier=${clientTier}`,
      logDate: new Date(),
      userId: order.clientId,
    });

    return entry;
  }

  deriveEventType(status: string): string {
    switch (status) {
      case OrderStatus.NEW: return 'ORDER_SUBMITTED';
      case OrderStatus.VALIDATED: return 'ORDER_VALIDATED';
      case OrderStatus.PRICED: return 'ORDER_PRICED';
      case OrderStatus.FILLED: return 'ORDER_FILLED';
      case OrderStatus.REJECTED: return 'ORDER_REJECTED';
      case OrderStatus.SETTLED: return 'SETTLEMENT_CREATED';
      case OrderStatus.CANCELLED: return 'ORDER_CANCELLED';
      default: return 'ORDER_STATUS_CHANGED';
    }
  }

  getAuditLog(): AuditEvent[] {
    return this.auditLog;
  }

  getBillingLedger(): BillingEntry[] {
    return this.billingLedger;
  }
}
