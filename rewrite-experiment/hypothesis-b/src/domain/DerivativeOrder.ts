import { z } from 'zod';

export const DerivativeContractType = {
  FX_SPOT: 'FX_SPOT',
  FX_FORWARD: 'FX_FORWARD',
  OPTION_CALL: 'OPTION_CALL',
  OPTION_PUT: 'OPTION_PUT',
} as const;

export type DerivativeContractType = (typeof DerivativeContractType)[keyof typeof DerivativeContractType];

export const DerivativeStatus = {
  NEW: 'NEW',
  FILLED: 'FILLED',
  REJECTED: 'REJECTED',
} as const;

export type DerivativeStatus = (typeof DerivativeStatus)[keyof typeof DerivativeStatus];

export const DerivativeOrderSchema = z.object({
  orderId: z.string(),
  clientId: z.string(),
  contractType: z.enum(['FX_SPOT', 'FX_FORWARD', 'OPTION_CALL', 'OPTION_PUT']),
  underlying: z.string(),
  strikePrice: z.number().positive(),
  quantity: z.number().int().positive(),
  expiry: z.string().nullable().default(null),
  status: z.string().default('NEW'),
  premium: z.number().default(0),
});

export type DerivativeOrder = z.infer<typeof DerivativeOrderSchema>;

export const MAX_NOTIONAL = 10_000_000;
