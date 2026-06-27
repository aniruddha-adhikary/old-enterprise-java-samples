import { AuditEvent, AuditEventType, AuditSource, AuditEntityType } from '../domain/AuditEvent';
import { BillingLedgerEntry, createBillingEntry } from '../domain/BillingLedgerEntry';
import { TradeOrder } from '../domain/TradeOrder';
import { calculateCommission } from '../pricing/CommissionCalculator';

export class AuditService {
  private auditLog: AuditEvent[] = [];
  private billingLedger: BillingLedgerEntry[] = [];

  getAuditLog(): AuditEvent[] {
    return [...this.auditLog];
  }

  getBillingLedger(): BillingLedgerEntry[] {
    return [...this.billingLedger];
  }

  logOrderFilled(order: TradeOrder): void {
    this.auditLog.push({
      eventType: AuditEventType.ORDER_FILLED,
      sourceSystem: AuditSource.ORDER_ENGINE,
      entityType: AuditEntityType.ORDER,
      entityId: order.orderId,
      description: `Order ${order.orderId} filled: ${order.quantity} ${order.symbol} @ ${order.price.toFixed(4)}`,
      logDate: new Date(),
      userId: null,
    });
  }

  logOrderRejected(order: TradeOrder, reason: string): void {
    this.auditLog.push({
      eventType: AuditEventType.ORDER_REJECTED,
      sourceSystem: AuditSource.ORDER_ENGINE,
      entityType: AuditEntityType.ORDER,
      entityId: order.orderId,
      description: `Order ${order.orderId} rejected: ${reason}`,
      logDate: new Date(),
      userId: null,
    });
  }

  logOrderSettled(orderId: string): void {
    this.auditLog.push({
      eventType: AuditEventType.ORDER_SETTLED,
      sourceSystem: AuditSource.SETTLEMENT,
      entityType: AuditEntityType.ORDER,
      entityId: orderId,
      description: `Order ${orderId} settled`,
      logDate: new Date(),
      userId: null,
    });
  }

  chargeBilling(order: TradeOrder, clientTier: string): BillingLedgerEntry {
    const grossAmount = order.quantity * order.price;
    const commissionAmount = calculateCommission(grossAmount, clientTier);

    const entry = createBillingEntry({
      orderId: order.orderId,
      clientId: order.clientId,
      grossAmount,
      commissionAmount,
    });

    this.billingLedger.push(entry);

    this.auditLog.push({
      eventType: AuditEventType.BILLING_CHARGED,
      sourceSystem: AuditSource.AUDIT_SERVICE,
      entityType: AuditEntityType.BILLING,
      entityId: order.orderId,
      description: `Billing: gross=${grossAmount.toFixed(4)}, commission=${commissionAmount.toFixed(4)}, net=${entry.netAmount.toFixed(4)}`,
      logDate: new Date(),
      userId: null,
    });

    return entry;
  }
}
