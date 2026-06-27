import { z } from 'zod';

export const BillingLedgerEntrySchema = z.object({
  entryId: z.number().int().optional(),
  orderId: z.string().max(30),
  clientId: z.string().max(20),
  grossAmount: z.number(),
  commissionAmount: z.number(),
  netAmount: z.number(),
  chargedDate: z.date().default(() => new Date()),
  status: z.string().max(15).default('CHARGED'),
});

export type BillingLedgerEntry = z.infer<typeof BillingLedgerEntrySchema>;

export function createBillingEntry(params: {
  orderId: string;
  clientId: string;
  grossAmount: number;
  commissionAmount: number;
}): BillingLedgerEntry {
  return {
    orderId: params.orderId,
    clientId: params.clientId,
    grossAmount: params.grossAmount,
    commissionAmount: params.commissionAmount,
    netAmount: params.grossAmount + params.commissionAmount,
    chargedDate: new Date(),
    status: 'CHARGED',
  };
}
