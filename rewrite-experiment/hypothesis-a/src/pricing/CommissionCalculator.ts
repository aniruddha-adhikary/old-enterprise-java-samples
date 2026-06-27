import { ClientTier } from '../domain/enums';

const TIER_RATES: Record<string, number> = {
  [ClientTier.PLATINUM]: 0.005,
  [ClientTier.GOLD]: 0.010,
  [ClientTier.SILVER]: 0.015,
  [ClientTier.BRONZE]: 0.020,
};

const DEFAULT_RATE = 0.020;

export function getCommissionRate(tier: ClientTier | null | undefined): number {
  if (!tier) return DEFAULT_RATE;
  return TIER_RATES[tier] ?? DEFAULT_RATE;
}

export function calculateCommission(
  orderValue: number,
  tier: ClientTier | null | undefined
): number {
  return orderValue * getCommissionRate(tier);
}
