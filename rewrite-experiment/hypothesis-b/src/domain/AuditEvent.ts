import { z } from 'zod';

export const AuditEventType = {
  ORDER_FILLED: 'ORDER_FILLED',
  ORDER_REJECTED: 'ORDER_REJECTED',
  ORDER_SETTLED: 'ORDER_SETTLED',
  BILLING_CHARGED: 'BILLING_CHARGED',
} as const;

export type AuditEventType = (typeof AuditEventType)[keyof typeof AuditEventType];

export const AuditSource = {
  ORDER_ENGINE: 'order-engine',
  SETTLEMENT: 'settlement-gateway',
  AUDIT_SERVICE: 'audit-service',
} as const;

export const AuditEntityType = {
  ORDER: 'ORDER',
  BILLING: 'BILLING',
} as const;

export const AuditEventSchema = z.object({
  logId: z.number().int().optional(),
  eventType: z.string().max(30),
  sourceSystem: z.string().max(30).nullable().default(null),
  entityType: z.string().max(20).nullable().default(null),
  entityId: z.string().max(30).nullable().default(null),
  description: z.string().max(500).nullable().default(null),
  logDate: z.date().default(() => new Date()),
  userId: z.string().max(30).nullable().default(null),
});

export type AuditEvent = z.infer<typeof AuditEventSchema>;
