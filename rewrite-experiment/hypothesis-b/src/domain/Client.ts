import { z } from 'zod';

export const ClientTier = {
  PLATINUM: 'PLATINUM',
  GOLD: 'GOLD',
  SILVER: 'SILVER',
  BRONZE: 'BRONZE',
} as const;

export type ClientTier = (typeof ClientTier)[keyof typeof ClientTier];

export const ClientSchema = z.object({
  clientId: z.string().max(20),
  name: z.string().max(100),
  email: z.string().max(100).nullable().default(null),
  phone: z.string().max(20).nullable().default(null),
  tier: z.enum(['PLATINUM', 'GOLD', 'SILVER', 'BRONZE']).default('BRONZE'),
  maxOrderValue: z.number().default(100000.0),
  active: z.boolean().default(true),
  kycStatus: z.string().max(20).default('APPROVED'),
  killSwitch: z.string().max(1).default('N'),
});

export type Client = z.infer<typeof ClientSchema>;

export const DEFAULT_CLIENT: Partial<Client> = {
  tier: 'BRONZE',
  maxOrderValue: 100000.0,
  active: true,
  kycStatus: 'APPROVED',
  killSwitch: 'N',
};
