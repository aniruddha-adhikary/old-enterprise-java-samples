import { z } from 'zod';

export const SettlementStatus = {
  PENDING: 'PENDING',
  GENERATED: 'GENERATED',
  UPLOADED: 'UPLOADED',
  CONFIRMED: 'CONFIRMED',
  FAILED: 'FAILED',
  RECONCILED: 'RECONCILED',
  DISCREPANCY: 'DISCREPANCY',
} as const;

export type SettlementStatus = (typeof SettlementStatus)[keyof typeof SettlementStatus];

export const SettlementRecordSchema = z.object({
  recordId: z.string().max(30),
  orderId: z.string().max(30),
  clientId: z.string().max(20),
  symbol: z.string().max(10),
  quantity: z.number().int(),
  side: z.enum(['BUY', 'SELL']),
  amount: z.number(),
  commission: z.number().default(0),
  tradeDate: z.date(),
  settlementDate: z.date().nullable().default(null),
  status: z.string().default('PENDING'),
  batchId: z.string().max(30).nullable().default(null),
  externalRef: z.string().max(50).nullable().default(null),
});

export type SettlementRecord = z.infer<typeof SettlementRecordSchema>;

export function calculateSettlementDate(tradeDate: Date): Date {
  // BUG-004: T+3 calendar days, does NOT skip weekends (preserved for backward compat)
  const settlement = new Date(tradeDate);
  settlement.setDate(settlement.getDate() + 3);
  return settlement;
}

export function generateRecordId(orderId: string): string {
  const timestamp = Date.now();
  const hash = orderId.split('').reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
  return `SR-${timestamp}-${hash}`;
}

export function generateBatchId(date: Date, sequence: number): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const seq = String(sequence).padStart(3, '0');
  return `BATCH-${y}${m}${d}-${seq}`;
}
