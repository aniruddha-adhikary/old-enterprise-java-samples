import { CommissionCalculator } from '../src/pricing/commission-calculator';
import { PricingService } from '../src/pricing/pricing-service';
import { ExposureCalculator } from '../src/risk/exposure-calculator';
import { DerivativeProcessor } from '../src/derivatives/derivative-processor';
import { RiskAssessment, RiskStatus } from '../src/domain/risk';
import { DerivativeOrder, ContractType, DerivativeStatus } from '../src/domain/derivative';

describe('CommissionCalculator', () => {
  it('should return 0.5% for PLATINUM', () => {
    expect(CommissionCalculator.getRate('PLATINUM')).toBe(0.005);
    expect(CommissionCalculator.calculate(100000, 'PLATINUM')).toBe(500);
  });

  it('should return 1.0% for GOLD', () => {
    expect(CommissionCalculator.getRate('GOLD')).toBe(0.010);
    expect(CommissionCalculator.calculate(100000, 'GOLD')).toBe(1000);
  });

  it('should return 1.5% for SILVER', () => {
    expect(CommissionCalculator.getRate('SILVER')).toBe(0.015);
    expect(CommissionCalculator.calculate(100000, 'SILVER')).toBe(1500);
  });

  it('should return 2.0% for BRONZE', () => {
    expect(CommissionCalculator.getRate('BRONZE')).toBe(0.020);
    expect(CommissionCalculator.calculate(100000, 'BRONZE')).toBe(2000);
  });

  it('should default to 2.0% for unknown tier', () => {
    expect(CommissionCalculator.getRate('UNKNOWN')).toBe(0.020);
  });
});

describe('PricingService', () => {
  it('should have COMMISSION_RATE of 0.015 (different from CommissionCalculator)', () => {
    expect(PricingService.COMMISSION_RATE).toBe(0.015);
  });

  it('should calculate commission at 1.5%', () => {
    const svc = new PricingService();
    expect(svc.calculateCommission(100000)).toBe(1500);
  });

  it('should return hardcoded prices for known symbols', () => {
    const svc = new PricingService();

    const msft = svc.getQuote('MSFT');
    expect(msft).not.toBeNull();
    expect(msft!.bid).toBe(25.00);
    expect(msft!.ask).toBe(25.50);
    expect(msft!.last).toBe(25.25);

    const ibm = svc.getQuote('IBM');
    expect(ibm!.bid).toBe(119.00);
    expect(ibm!.ask).toBe(120.00);
    expect(ibm!.last).toBe(119.50);
  });

  it('should return null for unknown symbols', () => {
    const svc = new PricingService();
    expect(svc.getQuote('UNKNOWN')).toBeNull();
  });

  it('should apply tier-specific spreads', () => {
    const svc = new PricingService();
    const quote = svc.getQuote('MSFT')!;

    const platinum = svc.applyTierSpread(quote, 'PLATINUM');
    const mid = (25.00 + 25.50) / 2; // 25.25
    expect(platinum.bid).toBeCloseTo(mid * (1 - 0.001), 4);
    expect(platinum.ask).toBeCloseTo(mid * (1 + 0.001), 4);

    const bronze = svc.applyTierSpread(quote, 'BRONZE');
    expect(bronze.bid).toBeCloseTo(mid * (1 - 0.005), 4);
    expect(bronze.ask).toBeCloseTo(mid * (1 + 0.005), 4);
  });

  it('should have correct spread values per tier', () => {
    expect(PricingService.SPREAD_BY_TIER['PLATINUM']).toBe(0.001);
    expect(PricingService.SPREAD_BY_TIER['GOLD']).toBe(0.002);
    expect(PricingService.SPREAD_BY_TIER['SILVER']).toBe(0.003);
    expect(PricingService.SPREAD_BY_TIER['BRONZE']).toBe(0.005);
  });

  it('should have hardcoded prices for all 7 symbols', () => {
    const symbols = ['MSFT', 'IBM', 'ORCL', 'SUNW', 'CSCO', 'INTC', 'DELL'];
    for (const sym of symbols) {
      expect(PricingService.HARDCODED_PRICES[sym]).toBeDefined();
    }
  });
});

describe('ExposureCalculator', () => {
  it('should use correct VaR parameters', () => {
    expect(ExposureCalculator.VAR_Z_SCORE).toBe(2.33);
    expect(ExposureCalculator.HOLDING_PERIOD_DAYS).toBe(1.0);
    expect(ExposureCalculator.TRADING_DAYS_PER_YEAR).toBe(252.0);
    expect(ExposureCalculator.VAR_THRESHOLD).toBe(50000.0);
  });

  it('should use correct volatility assumptions', () => {
    expect(ExposureCalculator.VOL_EQUITY).toBe(0.20);
    expect(ExposureCalculator.VOL_FX).toBe(0.08);
    expect(ExposureCalculator.VOL_COMMODITY).toBe(0.30);
    expect(ExposureCalculator.VOL_DEFAULT).toBe(0.25);
  });

  it('should classify FX symbols by / character', () => {
    expect(ExposureCalculator.getVolatility('EUR/USD')).toBe(0.08);
  });

  it('should classify commodity symbols', () => {
    expect(ExposureCalculator.getVolatility('GOLD')).toBe(0.30);
    expect(ExposureCalculator.getVolatility('OIL')).toBe(0.30);
    expect(ExposureCalculator.getVolatility('SILVER')).toBe(0.30);
  });

  it('should classify equities by default', () => {
    expect(ExposureCalculator.getVolatility('MSFT')).toBe(0.20);
    expect(ExposureCalculator.getVolatility('IBM')).toBe(0.20);
  });

  it('should calculate VaR = notional * vol * z-score * sqrt(t/252)', () => {
    const assessment: RiskAssessment = {
      riskOrderId: 'R1',
      sourceOrderId: 'O1',
      clientId: 'C1',
      symbol: 'MSFT',
      quantity: 1000,
      side: 'BUY',
      price: 25.00,
      notionalValue: 0,
      exposureContribution: 0,
      varContribution: 0,
      riskStatus: RiskStatus.PENDING,
      assessmentDate: new Date(),
    };

    ExposureCalculator.calculateRisk(assessment);

    // notional = 1000 * 25 = 25000
    expect(assessment.notionalValue).toBe(25000);

    // exposure = +25000 (BUY)
    expect(assessment.exposureContribution).toBe(25000);

    // VaR = 25000 * 0.20 * 2.33 * sqrt(1/252)
    const expectedVaR = 25000 * 0.20 * 2.33 * Math.sqrt(1 / 252);
    expect(assessment.varContribution).toBeCloseTo(expectedVaR, 2);
  });

  it('should set negative exposure for SELL orders', () => {
    const assessment: RiskAssessment = {
      riskOrderId: 'R1',
      sourceOrderId: 'O1',
      clientId: 'C1',
      symbol: 'MSFT',
      quantity: 1000,
      side: 'SELL',
      price: 25.00,
      notionalValue: 0,
      exposureContribution: 0,
      varContribution: 0,
      riskStatus: RiskStatus.PENDING,
      assessmentDate: new Date(),
    };

    ExposureCalculator.calculateRisk(assessment);
    expect(assessment.exposureContribution).toBe(-25000);
  });

  it('should flag VaR > $50,000', () => {
    const assessment: RiskAssessment = {
      riskOrderId: 'R1',
      sourceOrderId: 'O1',
      clientId: 'C1',
      symbol: 'MSFT',
      quantity: 100000,
      side: 'BUY',
      price: 100.00,
      notionalValue: 0,
      exposureContribution: 0,
      varContribution: 0,
      riskStatus: RiskStatus.PENDING,
      assessmentDate: new Date(),
    };

    ExposureCalculator.calculateRisk(assessment);
    expect(assessment.riskStatus).toBe(RiskStatus.FLAGGED);
  });

  it('should assess (not flag) small VaR orders', () => {
    const assessment: RiskAssessment = {
      riskOrderId: 'R1',
      sourceOrderId: 'O1',
      clientId: 'C1',
      symbol: 'MSFT',
      quantity: 10,
      side: 'BUY',
      price: 25.00,
      notionalValue: 0,
      exposureContribution: 0,
      varContribution: 0,
      riskStatus: RiskStatus.PENDING,
      assessmentDate: new Date(),
    };

    ExposureCalculator.calculateRisk(assessment);
    expect(assessment.riskStatus).toBe(RiskStatus.ASSESSED);
  });
});

describe('DerivativeProcessor', () => {
  it('should have FX commission rate of 1.5%', () => {
    expect(DerivativeProcessor.FX_COMMISSION).toBe(0.015);
    expect(DerivativeProcessor.getCommissionRate()).toBe(0.015);
  });

  it('should have max notional of $10,000,000', () => {
    expect(DerivativeProcessor.MAX_NOTIONAL).toBe(10000000);
  });

  it('should calculate FX_SPOT premium as notional * 1.5%', () => {
    const processor = new DerivativeProcessor();
    const order: DerivativeOrder = {
      orderId: 'DRV-001',
      clientId: 'C001',
      contractType: ContractType.FX_SPOT,
      underlying: 'EUR/USD',
      strikePrice: 1.10,
      quantity: 100000,
      expiry: null,
      status: DerivativeStatus.NEW,
      premium: 0,
    };

    const result = processor.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.FILLED);
    expect(result.premium).toBeCloseTo(100000 * 1.10 * 0.015, 2);
  });

  it('should calculate option premium as notional * 5%', () => {
    const processor = new DerivativeProcessor();
    const order: DerivativeOrder = {
      orderId: 'DRV-002',
      clientId: 'C001',
      contractType: ContractType.OPTION_CALL,
      underlying: 'MSFT',
      strikePrice: 25.00,
      quantity: 1000,
      expiry: '2024-06-15',
      status: DerivativeStatus.NEW,
      premium: 0,
    };

    const result = processor.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.FILLED);
    expect(result.premium).toBeCloseTo(1000 * 25 * 0.05, 2);
  });

  it('should reject orders exceeding max notional', () => {
    const processor = new DerivativeProcessor();
    const order: DerivativeOrder = {
      orderId: 'DRV-003',
      clientId: 'C001',
      contractType: ContractType.FX_FORWARD,
      underlying: 'EUR/USD',
      strikePrice: 1.10,
      quantity: 10000000, // notional = 11M > 10M limit
      expiry: null,
      status: DerivativeStatus.NEW,
      premium: 0,
    };

    const result = processor.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.REJECTED);
  });

  it('should reject orders with missing fields', () => {
    const processor = new DerivativeProcessor();
    const order: DerivativeOrder = {
      orderId: '',
      clientId: 'C001',
      contractType: ContractType.FX_SPOT,
      underlying: 'EUR/USD',
      strikePrice: 1.10,
      quantity: 100,
      expiry: null,
      status: DerivativeStatus.NEW,
      premium: 0,
    };

    const result = processor.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.REJECTED);
  });

  it('should calculate FX commission correctly', () => {
    const processor = new DerivativeProcessor();
    expect(processor.calculateCommission(100000)).toBe(1500);
  });
});
