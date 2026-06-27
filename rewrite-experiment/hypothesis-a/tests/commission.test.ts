import { getCommissionRate, calculateCommission } from '../src/pricing/CommissionCalculator';
import { ClientTier } from '../src/domain/enums';

describe('CommissionCalculator', () => {
  describe('getCommissionRate', () => {
    test('PLATINUM tier = 0.5%', () => {
      expect(getCommissionRate(ClientTier.PLATINUM)).toBe(0.005);
    });

    test('GOLD tier = 1.0%', () => {
      expect(getCommissionRate(ClientTier.GOLD)).toBe(0.010);
    });

    test('SILVER tier = 1.5%', () => {
      expect(getCommissionRate(ClientTier.SILVER)).toBe(0.015);
    });

    test('BRONZE tier = 2.0%', () => {
      expect(getCommissionRate(ClientTier.BRONZE)).toBe(0.020);
    });

    test('null tier defaults to 2.0%', () => {
      expect(getCommissionRate(null)).toBe(0.020);
    });

    test('undefined tier defaults to 2.0%', () => {
      expect(getCommissionRate(undefined)).toBe(0.020);
    });
  });

  describe('calculateCommission', () => {
    test('PLATINUM: 100,000 * 0.005 = 500', () => {
      expect(calculateCommission(100000, ClientTier.PLATINUM)).toBe(500);
    });

    test('GOLD: 100,000 * 0.010 = 1,000', () => {
      expect(calculateCommission(100000, ClientTier.GOLD)).toBe(1000);
    });

    test('SILVER: 100,000 * 0.015 = 1,500', () => {
      expect(calculateCommission(100000, ClientTier.SILVER)).toBe(1500);
    });

    test('BRONZE: 100,000 * 0.020 = 2,000', () => {
      expect(calculateCommission(100000, ClientTier.BRONZE)).toBe(2000);
    });

    test('null tier: defaults to BRONZE rate', () => {
      expect(calculateCommission(100000, null)).toBe(2000);
    });

    test('large order: 5,000,000 * 0.005 = 25,000', () => {
      expect(calculateCommission(5000000, ClientTier.PLATINUM)).toBe(25000);
    });

    test('zero order value = 0 commission', () => {
      expect(calculateCommission(0, ClientTier.GOLD)).toBe(0);
    });
  });
});
