export interface AuditEvent {
  logId?: number;
  eventType: string;
  sourceSystem: string;
  entityType: string;
  entityId: string;
  description: string;
  logDate: Date;
  userId: string;
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

export interface RuleAuditEntry {
  auditId?: number;
  ruleName: string;
  orderId: string;
  clientId: string;
  result: string;
  evaluationTime: Date;
  details: string;
}

export interface SurveillanceAuditEntry {
  logId?: number;
  ruleName: string;
  orderId: string;
  clientId: string;
  symbol: string;
  result: string;
  surveillanceFlags: string;
  evaluationTime: Date;
  details: string;
}
