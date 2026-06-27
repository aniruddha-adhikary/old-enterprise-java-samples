import { ClientTier } from '../domain/enums';

export interface PriceQuote {
  symbol: string;
  bid: number;
  ask: number;
  last: number;
  currency: string;
}

const HARDCODED_PRICES: Record<string, PriceQuote> = {
  MSFT: { symbol: 'MSFT', bid: 25.00, ask: 25.50, last: 25.25, currency: 'USD' },
  IBM:  { symbol: 'IBM',  bid: 119.00, ask: 120.00, last: 119.50, currency: 'USD' },
  ORCL: { symbol: 'ORCL', bid: 15.00, ask: 15.50, last: 15.25, currency: 'USD' },
  SUNW: { symbol: 'SUNW', bid: 8.50, ask: 9.00, last: 8.75, currency: 'USD' },
  CSCO: { symbol: 'CSCO', bid: 21.50, ask: 22.00, last: 21.75, currency: 'USD' },
  INTC: { symbol: 'INTC', bid: 30.00, ask: 30.50, last: 30.25, currency: 'USD' },
  DELL: { symbol: 'DELL', bid: 34.50, ask: 35.00, last: 34.75, currency: 'USD' },
};

const DEFAULT_PRICE: PriceQuote = {
  symbol: 'UNKNOWN',
  bid: 10.00,
  ask: 10.50,
  last: 10.25,
  currency: 'USD',
};

const DB_PRICES: Record<string, PriceQuote> = {
  MSFT: { symbol: 'MSFT', bid: 25.50, ask: 25.75, last: 25.63, currency: 'USD' },
  IBM:  { symbol: 'IBM',  bid: 120.00, ask: 120.50, last: 120.25, currency: 'USD' },
  ORCL: { symbol: 'ORCL', bid: 15.25, ask: 15.50, last: 15.38, currency: 'USD' },
  SUNW: { symbol: 'SUNW', bid: 8.75, ask: 9.00, last: 8.88, currency: 'USD' },
  CSCO: { symbol: 'CSCO', bid: 22.00, ask: 22.25, last: 22.13, currency: 'USD' },
  INTC: { symbol: 'INTC', bid: 30.50, ask: 30.75, last: 30.63, currency: 'USD' },
  DELL: { symbol: 'DELL', bid: 35.00, ask: 35.25, last: 35.13, currency: 'USD' },
};

const TIER_SPREADS: Record<string, number> = {
  [ClientTier.PLATINUM]: 0.001,
  [ClientTier.GOLD]: 0.002,
  [ClientTier.SILVER]: 0.003,
  [ClientTier.BRONZE]: 0.005,
};

const DEFAULT_SPREAD = 0.005;

/** Pricing service commission rate (1.5%) -- different from order-engine's tier-based rate (BUG-010). */
const PRICING_COMMISSION_RATE = 0.015;

export interface PricingRepository {
  getQuoteFromDatabase(symbol: string): Promise<PriceQuote | null>;
}

export class PricingService {
  constructor(private readonly pricingRepo?: PricingRepository) {}

  async getQuote(
    symbol: string,
    tier?: ClientTier | null
  ): Promise<PriceQuote> {
    let quote: PriceQuote | null = null;

    // 1. Try database first
    if (this.pricingRepo) {
      try {
        quote = await this.pricingRepo.getQuoteFromDatabase(symbol);
      } catch {
        // fall through to hardcoded
      }
    }

    // 2. Fall back to in-memory DB prices
    if (!quote) {
      quote = DB_PRICES[symbol] || null;
    }

    // 3. Fall back to hardcoded prices
    if (!quote) {
      quote = HARDCODED_PRICES[symbol] || { ...DEFAULT_PRICE, symbol };
    }

    // Apply tier-based spread
    if (tier) {
      const spread = TIER_SPREADS[tier] ?? DEFAULT_SPREAD;
      return {
        ...quote,
        bid: quote.last * (1.0 - spread),
        ask: quote.last * (1.0 + spread),
      };
    }

    return quote;
  }

  async getBatchQuotes(
    symbols: string[],
    tier?: ClientTier | null
  ): Promise<PriceQuote[]> {
    // calls getQuote in a loop (not optimized, preserved from legacy)
    return Promise.all(symbols.map((s) => this.getQuote(s, tier)));
  }

  getPricingCommissionRate(): number {
    return PRICING_COMMISSION_RATE;
  }

  getHardcodedPrices(): Record<string, PriceQuote> {
    return { ...HARDCODED_PRICES };
  }

  getDbPrices(): Record<string, PriceQuote> {
    return { ...DB_PRICES };
  }
}
