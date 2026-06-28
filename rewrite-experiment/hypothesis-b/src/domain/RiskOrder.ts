import { z } from 'zod';

export const RiskStatus = {
  PENDING: 'PENDING',
  ASSESSED: 'ASSESSED',
  FLAGGED: 'FLAGGED',
  ERROR: 'ERROR',
} as const;

export type RiskStatus = (typeof RiskStatus)[keyof typeof RiskStatus];

export const RiskOrderSchema = z.object({
  riskOrderId: z.string().max(50),
  sourceOrderId: z.string().max(50).nullable().default(null),
  clientId: z.string().max(20).nullable().default(null),
  symbol: z.string().max(10).nullable().default(null),
  quantity: z.number().int().nullable().default(null),
  side: z.enum(['BUY', 'SELL']).nullable().default(null),
  price: z.number().nullable().default(null),
  notionalValue: z.number().nullable().default(null),
  exposureContribution: z.number().nullable().default(null),
  varContribution: z.number().nullable().default(null),
  riskStatus: z.string().default('PENDING'),
  assessmentDate: z.date().default(() => new Date()),
});

export type RiskOrder = z.infer<typeof RiskOrderSchema>;

export const VAR_FLAG_THRESHOLD = 50_000.0;
