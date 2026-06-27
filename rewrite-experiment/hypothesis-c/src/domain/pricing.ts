export interface PriceQuote {
  symbol: string;
  bid: number;
  ask: number;
  last: number;
  currency: string;
  timestamp: Date;
}
