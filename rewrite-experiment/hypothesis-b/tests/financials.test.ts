import { getCommissionRate, calculateCommission, PRICING_SERVICE_FLAT_RATE } from '../src/pricing/CommissionCalculator';
import { PricingService, calculateBidAsk, getSpreadForTier } from '../src/pricing/PricingService';
import { getFxRate, calculateFxBidAsk, convertToUsd, FX_SPREAD } from '../src/pricing/FxPricingHelper';
import { calculateVaR } from '../src/risk/ExposureCalculator';
import { DerivativeProcessor } from '../src/derivatives/DerivativeProcessor';
import { DerivativeContractType, MAX_NOTIONAL } from '../src/domain/DerivativeOrder';

describe('Commission Rates (tier-based)', () => {
  test('PLATINUM rate = 0.005', () => {
    expect(getCommissionRate('PLATINUM')).toBe(0.005);
  });

  test('GOLD rate = 0.010', () => {
    expect(getCommissionRate('GOLD')).toBe(0.010);
  });

  test('SILVER rate = 0.015', () => {
    expect(getCommissionRate('SILVER')).toBe(0.015);
  });

  test('BRONZE rate = 0.020', () => {
    expect(getCommissionRate('BRONZE')).toBe(0.020);
  });

  test('DEFAULT rate = 0.020', () => {
    expect(getCommissionRate('UNKNOWN')).toBe(0.020);
  });

  test('commission calculation', () => {
    // 100,000 * 0.01 (GOLD) = 1,000
    expect(calculateCommission(100000, 'GOLD')).toBe(1000);
  });

  test('BUG-010: PricingService flat rate discrepancy', () => {
    expect(PRICING_SERVICE_FLAT_RATE).toBe(0.015);
    // Different from tier-based rates
    expect(PRICING_SERVICE_FLAT_RATE).not.toBe(getCommissionRate('GOLD'));
  });
});

describe('Pricing Spreads', () => {
  test('spread by tier', () => {
    expect(getSpreadForTier('PLATINUM')).toBe(0.001);
    expect(getSpreadForTier('GOLD')).toBe(0.002);
    expect(getSpreadForTier('SILVER')).toBe(0.003);
    expect(getSpreadForTier('BRONZE')).toBe(0.005);
  });

  test('bid/ask calculation', () => {
    // lastPrice=100, GOLD spread=0.002
    const { bid, ask } = calculateBidAsk(100, 'GOLD');
    expect(bid).toBeCloseTo(99.8, 4);
    expect(ask).toBeCloseTo(100.2, 4);
  });
});

describe('PricingService', () => {
  test('returns database cached prices', () => {
    const svc = new PricingService();
    const quote = svc.getQuote('MSFT');
    expect(quote.bid).toBe(25.50);
    expect(quote.ask).toBe(25.75);
    expect(quote.last).toBe(25.63);
  });

  test('BUG-011: returns hardcoded default for unknown symbol', () => {
    const svc = new PricingService();
    const quote = svc.getQuote('UNKNOWN_SYMBOL');
    expect(quote.bid).toBe(10.00);
    expect(quote.ask).toBe(10.50);
    expect(quote.last).toBe(10.25);
  });

  test('price deviation within 10% passes', () => {
    const svc = new PricingService();
    expect(svc.checkPriceDeviation(100, 105)).toBe(true); // 5% deviation
    expect(svc.checkPriceDeviation(100, 95)).toBe(true);
  });

  test('price deviation beyond 10% fails', () => {
    const svc = new PricingService();
    expect(svc.checkPriceDeviation(100, 115)).toBe(false); // >10%
    expect(svc.checkPriceDeviation(100, 80)).toBe(false);
  });
});

describe('FX Rates', () => {
  test('known FX pairs', () => {
    expect(getFxRate('EUR/USD')).toBe(1.10);
    expect(getFxRate('GBP/USD')).toBe(1.55);
    expect(getFxRate('JPY/USD')).toBe(0.009);
    expect(getFxRate('CHF/USD')).toBe(0.72);
    expect(getFxRate('AUD/USD')).toBe(0.68);
    expect(getFxRate('USD/USD')).toBe(1.0);
  });

  test('unknown pair defaults to 1.0', () => {
    expect(getFxRate('XYZ/USD')).toBe(1.0);
  });

  test('FX bid/ask with spread', () => {
    // mid=1.10, spread=0.002
    const { bid, ask } = calculateFxBidAsk(1.10);
    expect(bid).toBeCloseTo(1.10 * (1 - 0.002 / 2), 6);
    expect(ask).toBeCloseTo(1.10 * (1 + 0.002 / 2), 6);
  });

  test('USD conversion', () => {
    expect(convertToUsd(100, 'EUR')).toBeCloseTo(110, 2);
    expect(convertToUsd(100, 'GBP')).toBeCloseTo(155, 2);
  });
});

describe('VaR Calculation', () => {
  test('equity VaR formula', () => {
    // VaR = |notional| * volatility * zScore * sqrt(holdingPeriod / tradingDaysPerYear)
    // = 100000 * 0.20 * 2.33 * sqrt(1/252) = 100000 * 0.20 * 2.33 * 0.063
    const var_ = calculateVaR(100000, 'MSFT');
    const expected = 100000 * 0.20 * 2.33 * Math.sqrt(1 / 252);
    expect(var_).toBeCloseTo(expected, 2);
  });

  test('FX VaR uses 0.08 volatility', () => {
    const var_ = calculateVaR(100000, 'EUR/USD');
    const expected = 100000 * 0.08 * 2.33 * Math.sqrt(1 / 252);
    expect(var_).toBeCloseTo(expected, 2);
  });

  test('commodity VaR uses 0.30 volatility', () => {
    const var_ = calculateVaR(100000, 'GOLD');
    const expected = 100000 * 0.30 * 2.33 * Math.sqrt(1 / 252);
    expect(var_).toBeCloseTo(expected, 2);
  });

  test('flag threshold is 50000', () => {
    // Need notional large enough: n * 0.20 * 2.33 * 0.063 > 50000
    // n > 50000 / 0.0294 ≈ 1,700,000
    const var_ = calculateVaR(2000000, 'MSFT');
    expect(var_).toBeGreaterThan(50000);
  });
});

describe('Derivatives Premium', () => {
  const processor = new DerivativeProcessor();

  test('FX_SPOT premium = notional * 0.015', () => {
    const result = processor.processOrder({
      orderId: 'D001',
      clientId: 'C001',
      contractType: 'FX_SPOT',
      underlying: 'EUR/USD',
      strikePrice: 1.10,
      quantity: 100000,
      expiry: null,
      status: 'NEW',
      premium: 0,
    });
    expect(result.status).toBe('FILLED');
    expect(result.premium).toBeCloseTo(100000 * 1.10 * 0.015, 2);
  });

  test('OPTION_CALL premium = notional * 0.05', () => {
    const result = processor.processOrder({
      orderId: 'D002',
      clientId: 'C001',
      contractType: 'OPTION_CALL',
      underlying: 'MSFT',
      strikePrice: 25.0,
      quantity: 1000,
      expiry: '2024-12-31',
      status: 'NEW',
      premium: 0,
    });
    expect(result.status).toBe('FILLED');
    expect(result.premium).toBeCloseTo(1000 * 25.0 * 0.05, 2);
  });

  test('rejects notional > 10,000,000', () => {
    const result = processor.processOrder({
      orderId: 'D003',
      clientId: 'C001',
      contractType: 'FX_SPOT',
      underlying: 'EUR/USD',
      strikePrice: 100.0,
      quantity: 200000, // 20,000,000 notional
      expiry: null,
      status: 'NEW',
      premium: 0,
    });
    expect(result.status).toBe('REJECTED');
  });

  test('rejects invalid contract type', () => {
    const result = processor.processOrder({
      orderId: 'D004',
      clientId: 'C001',
      contractType: 'INVALID' as any,
      underlying: 'EUR/USD',
      strikePrice: 1.0,
      quantity: 100,
      expiry: null,
      status: 'NEW',
      premium: 0,
    });
    expect(result.status).toBe('REJECTED');
  });
});
