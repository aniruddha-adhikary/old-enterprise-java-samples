import { ClientTier } from '../domain/Client';

// Tier-based commission rates from spec.json financials.commissionRates
const COMMISSION_RATES: Record<string, number> = {
  PLATINUM: 0.005,
  GOLD: 0.010,
  SILVER: 0.015,
  BRONZE: 0.020,
  DEFAULT: 0.020,
};

export function getCommissionRate(tier: string): number {
  return COMMISSION_RATES[tier] ?? COMMISSION_RATES['DEFAULT'];
}

export function calculateCommission(orderValue: number, tier: string): number {
  const rate = getCommissionRate(tier);
  return orderValue * rate;
}

// BUG-010: PricingServiceImpl uses flat 0.015 rate (different from tier-based)
export const PRICING_SERVICE_FLAT_RATE = 0.015;

// Derivatives commission rate (agreed with derivatives trading manager)
export const DERIVATIVES_COMMISSION_RATE = 0.015;
