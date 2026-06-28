import { ClientTier, Client } from '../domain/client';

/**
 * Commission calculator preserving exact rates from CommissionCalculator.java.
 *
 * Rates by tier:
 *   PLATINUM: 0.5% (0.005)
 *   GOLD:     1.0% (0.010)
 *   SILVER:   1.5% (0.015)
 *   BRONZE:   2.0% (0.020)
 *
 * Note: PricingServiceImpl uses a DIFFERENT commission rate of 0.015 (1.5%).
 * This is documented as intentional in the original source but is confusing.
 */
export class CommissionCalculator {
  static readonly RATES: Record<string, number> = {
    [ClientTier.PLATINUM]: 0.005,
    [ClientTier.GOLD]: 0.010,
    [ClientTier.SILVER]: 0.015,
    [ClientTier.BRONZE]: 0.020,
  };

  static readonly DEFAULT_RATE = 0.020;

  static getRate(tier: string): number {
    return CommissionCalculator.RATES[tier] ?? CommissionCalculator.DEFAULT_RATE;
  }

  static calculate(orderValue: number, tier: string): number {
    const rate = CommissionCalculator.getRate(tier);
    return orderValue * rate;
  }

  static calculateForClient(orderValue: number, client: Client): number {
    return CommissionCalculator.calculate(orderValue, client.tier);
  }
}
