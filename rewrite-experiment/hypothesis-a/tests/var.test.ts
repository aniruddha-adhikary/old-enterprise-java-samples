import { calculateVaR, calculateExposure } from '../src/risk/ExposureCalculator';
import { OrderSide } from '../src/domain/enums';

describe('VaR Calculation', () => {
  // VaR = |notional| * vol * 2.33 * sqrt(1/252)
  const sqrtFactor = Math.sqrt(1.0 / 252.0);

  test('equity VaR (vol=0.20)', () => {
    const notional = 1000000; // $1M
    const expected = notional * 0.20 * 2.33 * sqrtFactor;
    const result = calculateVaR(notional, 'MSFT');
    expect(result).toBeCloseTo(expected, 2);
  });

  test('FX VaR (vol=0.08, symbol contains "/")', () => {
    const notional = 1000000;
    const expected = notional * 0.08 * 2.33 * sqrtFactor;
    const result = calculateVaR(notional, 'EUR/USD');
    expect(result).toBeCloseTo(expected, 2);
  });

  test('commodity GOLD VaR (vol=0.30)', () => {
    const notional = 1000000;
    const expected = notional * 0.30 * 2.33 * sqrtFactor;
    const result = calculateVaR(notional, 'GOLD');
    expect(result).toBeCloseTo(expected, 2);
  });

  test('commodity OIL VaR (vol=0.30)', () => {
    const notional = 500000;
    const expected = 500000 * 0.30 * 2.33 * sqrtFactor;
    const result = calculateVaR(notional, 'OIL');
    expect(result).toBeCloseTo(expected, 2);
  });

  test('commodity SILVER VaR (vol=0.30)', () => {
    const notional = 500000;
    const expected = 500000 * 0.30 * 2.33 * sqrtFactor;
    const result = calculateVaR(notional, 'SILVER');
    expect(result).toBeCloseTo(expected, 2);
  });

  test('VaR flag threshold: 50,000', () => {
    // Find notional that produces VaR > 50,000 for equity
    // VaR = notional * 0.20 * 2.33 * sqrt(1/252)
    // 50000 = notional * 0.20 * 2.33 * 0.06299
    // 50000 = notional * 0.029354
    // notional = 50000 / 0.029354 ≈ 1,703,299
    const largeNotional = 2000000;
    const var_ = calculateVaR(largeNotional, 'MSFT');
    expect(var_).toBeGreaterThan(50000);

    const smallNotional = 100000;
    const var2 = calculateVaR(smallNotional, 'MSFT');
    expect(var2).toBeLessThan(50000);
  });

  test('negative notional uses absolute value', () => {
    const var1 = calculateVaR(1000000, 'MSFT');
    const var2 = calculateVaR(-1000000, 'MSFT');
    expect(var1).toBe(var2);
  });
});

describe('Exposure Calculation', () => {
  test('BUY = positive exposure', () => {
    expect(calculateExposure(1000000, OrderSide.BUY)).toBe(1000000);
  });

  test('SELL = negative exposure', () => {
    expect(calculateExposure(1000000, OrderSide.SELL)).toBe(-1000000);
  });

  test('unknown side = positive exposure', () => {
    expect(calculateExposure(1000000, 'UNKNOWN')).toBe(1000000);
  });
});
