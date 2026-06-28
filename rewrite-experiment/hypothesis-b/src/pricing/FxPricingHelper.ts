// BUG-012: FX rates duplicated in 3 places (JIRA-7101), hardcoded as of 2004-07-15
// This is the canonical FX rate source; also duplicated in MultiCurrencyRule and DerivativeProcessor

export const FX_RATES: Record<string, number> = {
  'EUR/USD': 1.10,
  'GBP/USD': 1.55,
  'JPY/USD': 0.009,
  'CHF/USD': 0.72,
  'AUD/USD': 0.68, // Only in FxPricingHelper, not in MultiCurrencyRule
  'USD/USD': 1.0,
};

export const FX_SPREAD = 0.002;

export function getFxRate(pair: string): number {
  return FX_RATES[pair] ?? 1.0;
}

export function calculateFxBidAsk(midRate: number): { bid: number; ask: number } {
  return {
    bid: midRate * (1 - FX_SPREAD / 2),
    ask: midRate * (1 + FX_SPREAD / 2),
  };
}

export function convertToUsd(amount: number, currency: string): number {
  const pair = `${currency}/USD`;
  const rate = getFxRate(pair);
  return amount * rate;
}
