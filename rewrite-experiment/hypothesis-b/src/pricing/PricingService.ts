// Pricing spreads by tier from spec.json financials.pricingSpreads
const PRICING_SPREADS: Record<string, number> = {
  PLATINUM: 0.001,
  GOLD: 0.002,
  SILVER: 0.003,
  BRONZE: 0.005,
  DEFAULT: 0.005,
};

export interface PriceQuote {
  symbol: string;
  bid: number;
  ask: number;
  last: number;
  currency: string;
}

// BUG-011: Hardcoded fallback prices from 2000-2001 (JIRA-1102)
const HARDCODED_PRICES: Record<string, PriceQuote> = {
  MSFT: { symbol: 'MSFT', bid: 25.00, ask: 25.50, last: 25.25, currency: 'USD' },
  IBM: { symbol: 'IBM', bid: 119.00, ask: 120.00, last: 119.50, currency: 'USD' },
  ORCL: { symbol: 'ORCL', bid: 15.00, ask: 15.50, last: 15.25, currency: 'USD' },
  SUNW: { symbol: 'SUNW', bid: 8.50, ask: 9.00, last: 8.75, currency: 'USD' },
  CSCO: { symbol: 'CSCO', bid: 21.50, ask: 22.00, last: 21.75, currency: 'USD' },
  INTC: { symbol: 'INTC', bid: 30.00, ask: 30.50, last: 30.25, currency: 'USD' },
  DELL: { symbol: 'DELL', bid: 34.50, ask: 35.00, last: 34.75, currency: 'USD' },
  DEFAULT: { symbol: 'DEFAULT', bid: 10.00, ask: 10.50, last: 10.25, currency: 'USD' },
};

// Database-cached prices (slightly different from hardcoded)
const DATABASE_PRICES: Record<string, PriceQuote> = {
  MSFT: { symbol: 'MSFT', bid: 25.50, ask: 25.75, last: 25.63, currency: 'USD' },
  IBM: { symbol: 'IBM', bid: 120.00, ask: 120.50, last: 120.25, currency: 'USD' },
  ORCL: { symbol: 'ORCL', bid: 15.25, ask: 15.50, last: 15.38, currency: 'USD' },
  SUNW: { symbol: 'SUNW', bid: 8.75, ask: 9.00, last: 8.88, currency: 'USD' },
  CSCO: { symbol: 'CSCO', bid: 22.00, ask: 22.25, last: 22.13, currency: 'USD' },
  INTC: { symbol: 'INTC', bid: 30.50, ask: 30.75, last: 30.63, currency: 'USD' },
  DELL: { symbol: 'DELL', bid: 35.00, ask: 35.25, last: 35.13, currency: 'USD' },
};

export function getSpreadForTier(tier: string): number {
  return PRICING_SPREADS[tier] ?? PRICING_SPREADS['DEFAULT'];
}

export function calculateBidAsk(lastPrice: number, tier: string): { bid: number; ask: number } {
  const spread = getSpreadForTier(tier);
  return {
    bid: lastPrice * (1.0 - spread),
    ask: lastPrice * (1.0 + spread),
  };
}

export class PricingService {
  private externalProvider: ((symbol: string) => PriceQuote | null) | null = null;

  setExternalProvider(fn: (symbol: string) => PriceQuote | null): void {
    this.externalProvider = fn;
  }

  // Fallback chain: external -> database cache -> hardcoded
  getQuote(symbol: string): PriceQuote {
    // 1. Try external source (SOAP/REST replacement)
    if (this.externalProvider) {
      const quote = this.externalProvider(symbol);
      if (quote) return quote;
    }

    // 2. Try database cache
    const dbQuote = DATABASE_PRICES[symbol];
    if (dbQuote) return dbQuote;

    // 3. Fall back to hardcoded prices (BUG-011)
    return HARDCODED_PRICES[symbol] ?? HARDCODED_PRICES['DEFAULT'];
  }

  // Price deviation check: reject if requested price deviates >10% from market
  checkPriceDeviation(requestedPrice: number, marketPrice: number): boolean {
    if (marketPrice === 0) return false;
    const deviation = Math.abs(requestedPrice - marketPrice) / marketPrice;
    return deviation <= 0.10; // 10% threshold
  }
}

export const PRICE_DEVIATION_THRESHOLD = 0.10;
