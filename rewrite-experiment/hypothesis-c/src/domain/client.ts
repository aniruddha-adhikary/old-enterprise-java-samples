export enum ClientTier {
  PLATINUM = 'PLATINUM',
  GOLD = 'GOLD',
  SILVER = 'SILVER',
  BRONZE = 'BRONZE',
}

export interface Client {
  clientId: string;
  name: string;
  email: string | null;
  phone: string | null;
  tier: ClientTier;
  maxOrderValue: number;
  active: boolean;
  createdDate: Date;
}

export interface SpecialClientOverride {
  clientId: string;
  commissionOverride: number | null;
  tierOverride: ClientTier | null;
  earlyAccess: boolean;
  fxPriority: boolean;
  discountPct: number;
  zeroCommission: boolean;
  freeTradesPerDay: number;
  notes: string | null;
}
