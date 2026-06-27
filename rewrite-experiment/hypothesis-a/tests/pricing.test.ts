import { PricingService } from '../src/pricing/PricingService';
import { ClientTier } from '../src/domain/enums';

describe('PricingService', () => {
  const service = new PricingService();

  describe('getQuote - no tier (raw prices)', () => {
    test('returns DB price for known symbol', async () => {
      const quote = await service.getQuote('MSFT');
      expect(quote.symbol).toBe('MSFT');
      expect(quote.bid).toBe(25.50);
      expect(quote.ask).toBe(25.75);
      expect(quote.last).toBe(25.63);
    });

    test('returns default price for unknown symbol (BUG-011)', async () => {
      const quote = await service.getQuote('ZZZZZ');
      expect(quote.bid).toBe(10.00);
      expect(quote.ask).toBe(10.50);
      expect(quote.last).toBe(10.25);
    });
  });

  describe('getQuote - with tier-based spread', () => {
    test('PLATINUM spread = 0.1%', async () => {
      const quote = await service.getQuote('MSFT', ClientTier.PLATINUM);
      const last = 25.63;
      expect(quote.bid).toBeCloseTo(last * (1 - 0.001), 4);
      expect(quote.ask).toBeCloseTo(last * (1 + 0.001), 4);
    });

    test('GOLD spread = 0.2%', async () => {
      const quote = await service.getQuote('IBM', ClientTier.GOLD);
      const last = 120.25;
      expect(quote.bid).toBeCloseTo(last * (1 - 0.002), 4);
      expect(quote.ask).toBeCloseTo(last * (1 + 0.002), 4);
    });

    test('SILVER spread = 0.3%', async () => {
      const quote = await service.getQuote('ORCL', ClientTier.SILVER);
      const last = 15.38;
      expect(quote.bid).toBeCloseTo(last * (1 - 0.003), 4);
      expect(quote.ask).toBeCloseTo(last * (1 + 0.003), 4);
    });

    test('BRONZE spread = 0.5%', async () => {
      const quote = await service.getQuote('DELL', ClientTier.BRONZE);
      const last = 35.13;
      expect(quote.bid).toBeCloseTo(last * (1 - 0.005), 4);
      expect(quote.ask).toBeCloseTo(last * (1 + 0.005), 4);
    });
  });

  describe('getBatchQuotes', () => {
    test('returns quotes for multiple symbols', async () => {
      const quotes = await service.getBatchQuotes(['MSFT', 'IBM', 'ORCL']);
      expect(quotes).toHaveLength(3);
      expect(quotes[0].symbol).toBe('MSFT');
      expect(quotes[1].symbol).toBe('IBM');
      expect(quotes[2].symbol).toBe('ORCL');
    });
  });

  describe('pricing commission rate', () => {
    test('is 1.5% (different from order-engine tier-based rates)', () => {
      expect(service.getPricingCommissionRate()).toBe(0.015);
    });
  });

  describe('hardcoded fallback prices', () => {
    test('includes all 7 standard symbols', () => {
      const prices = service.getHardcodedPrices();
      expect(Object.keys(prices)).toEqual(
        expect.arrayContaining(['MSFT', 'IBM', 'ORCL', 'SUNW', 'CSCO', 'INTC', 'DELL'])
      );
    });

    test('MSFT hardcoded: bid=25.00, ask=25.50, last=25.25', () => {
      const prices = service.getHardcodedPrices();
      expect(prices.MSFT.bid).toBe(25.00);
      expect(prices.MSFT.ask).toBe(25.50);
      expect(prices.MSFT.last).toBe(25.25);
    });
  });
});
