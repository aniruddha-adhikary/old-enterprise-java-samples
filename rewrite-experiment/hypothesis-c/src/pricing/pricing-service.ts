import { PriceQuote } from '../domain/pricing';
import { ClientTier } from '../domain/client';

/**
 * Pricing service preserving behavior from PricingServiceImpl.java.
 *
 * Commission rate here is 0.015 (1.5%) - DIFFERENT from CommissionCalculator's
 * tier-based rates. The original code comment says "intentional" but it's confusing.
 *
 * Spread adjustments by tier:
 *   PLATINUM: ±0.1% (0.001)
 *   GOLD:     ±0.2% (0.002)
 *   SILVER:   ±0.3% (0.003)
 *   BRONZE:   ±0.5% (0.005)
 *
 * KNOWN BUG: Hardcoded fallback prices for MSFT, IBM, ORCL, SUNW, CSCO, INTC, DELL.
 * Comment says "TEMPORARY" added 2000-03-15, still present, last updated 2001-11-20.
 */
export class PricingService {
  static readonly COMMISSION_RATE = 0.015;

  static readonly SPREAD_BY_TIER: Record<string, number> = {
    [ClientTier.PLATINUM]: 0.001,
    [ClientTier.GOLD]: 0.002,
    [ClientTier.SILVER]: 0.003,
    [ClientTier.BRONZE]: 0.005,
  };

  static readonly DEFAULT_SPREAD = 0.005;

  /**
   * Hardcoded fallback prices from PricingServiceImpl.java lines 176-221.
   * These were marked as "TEMPORARY" in March 2000. Last updated Nov 2001.
   */
  static readonly HARDCODED_PRICES: Record<string, PriceQuote> = {
    MSFT: { symbol: 'MSFT', bid: 25.00, ask: 25.50, last: 25.25, currency: 'USD', timestamp: new Date() },
    IBM:  { symbol: 'IBM',  bid: 119.00, ask: 120.00, last: 119.50, currency: 'USD', timestamp: new Date() },
    ORCL: { symbol: 'ORCL', bid: 15.00, ask: 15.50, last: 15.25, currency: 'USD', timestamp: new Date() },
    SUNW: { symbol: 'SUNW', bid: 8.50, ask: 9.00, last: 8.75, currency: 'USD', timestamp: new Date() },
    CSCO: { symbol: 'CSCO', bid: 21.50, ask: 22.00, last: 21.75, currency: 'USD', timestamp: new Date() },
    INTC: { symbol: 'INTC', bid: 30.00, ask: 30.50, last: 30.25, currency: 'USD', timestamp: new Date() },
    DELL: { symbol: 'DELL', bid: 34.50, ask: 35.00, last: 34.75, currency: 'USD', timestamp: new Date() },
  };

  private dbLookup: ((symbol: string) => PriceQuote | null) | null;

  constructor(dbLookup?: (symbol: string) => PriceQuote | null) {
    this.dbLookup = dbLookup || null;
  }

  getQuote(symbol: string): PriceQuote | null {
    if (!symbol) return null;

    if (this.dbLookup) {
      const dbQuote = this.dbLookup(symbol);
      if (dbQuote) return dbQuote;
    }

    return PricingService.HARDCODED_PRICES[symbol] || null;
  }

  getBatchQuotes(symbols: string[]): Map<string, PriceQuote> {
    const results = new Map<string, PriceQuote>();
    for (const symbol of symbols) {
      const quote = this.getQuote(symbol);
      if (quote) results.set(symbol, quote);
    }
    return results;
  }

  applyTierSpread(quote: PriceQuote, tier: string): PriceQuote {
    const spread = PricingService.SPREAD_BY_TIER[tier] ?? PricingService.DEFAULT_SPREAD;
    const mid = (quote.bid + quote.ask) / 2;

    return {
      ...quote,
      bid: mid * (1 - spread),
      ask: mid * (1 + spread),
      timestamp: new Date(),
    };
  }

  calculateCommission(orderValue: number): number {
    return orderValue * PricingService.COMMISSION_RATE;
  }
}
