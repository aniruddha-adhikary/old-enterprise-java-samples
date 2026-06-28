export enum AuditEventType {
  ORDER_SUBMITTED = 'ORDER_SUBMITTED',
  ORDER_VALIDATED = 'ORDER_VALIDATED',
  ORDER_PRICED = 'ORDER_PRICED',
  ORDER_FILLED = 'ORDER_FILLED',
  ORDER_REJECTED = 'ORDER_REJECTED',
  ORDER_CANCELLED = 'ORDER_CANCELLED',
  SETTLEMENT_CREATED = 'SETTLEMENT_CREATED',
  SETTLEMENT_UPLOADED = 'SETTLEMENT_UPLOADED',
  BILLING_CHARGED = 'BILLING_CHARGED',
  RULE_EVALUATED = 'RULE_EVALUATED',
}

export enum SourceSystem {
  ORDER_ENGINE = 'ORDER_ENGINE',
  SETTLEMENT_GATEWAY = 'SETTLEMENT_GATEWAY',
  NOTIFICATION_GATEWAY = 'NOTIFICATION_GATEWAY',
  AUDIT_SERVICE = 'AUDIT_SERVICE',
  RISK_ENGINE = 'RISK_ENGINE',
}

export enum EntityType {
  ORDER = 'ORDER',
  SETTLEMENT = 'SETTLEMENT',
  NOTIFICATION = 'NOTIFICATION',
  BILLING = 'BILLING',
  CLIENT = 'CLIENT',
}

export interface AuditEvent {
  logId?: number;
  eventType: string;
  sourceSystem: string;
  entityType: string;
  entityId: string;
  description: string;
  logDate: Date;
  userId: string | null;
}

export interface BillingEntry {
  entryId?: number;
  orderId: string;
  clientId: string;
  grossAmount: number;
  commissionAmount: number;
  netAmount: number;
  chargedDate: Date;
  status: string;
}
