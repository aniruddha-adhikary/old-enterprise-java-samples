import { DerivativeProcessor, FxPricingHelper } from '../src/derivatives/DerivativeProcessor';
import { DerivativeOrder } from '../src/domain/DerivativeOrder';
import { DerivativeContractType, DerivativeStatus } from '../src/domain/enums';

describe('DerivativeProcessor', () => {
  const repo = { saveDerivativeOrder: jest.fn() };

  beforeEach(() => {
    repo.saveDerivativeOrder.mockClear();
  });

  function makeDerivOrder(overrides: Partial<DerivativeOrder> = {}): DerivativeOrder {
    return {
      orderId: 'DRV-001',
      clientId: 'C001',
      contractType: DerivativeContractType.FX_SPOT,
      underlying: 'EUR/USD',
      strikePrice: 1.10,
      quantity: 100000,
      expiry: null,
      status: DerivativeStatus.NEW,
      premium: 0,
      ...overrides,
    };
  }

  test('FX_SPOT premium = notional * 0.015', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder();
    const result = await proc.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.FILLED);
    expect(result.premium).toBeCloseTo(100000 * 1.10 * 0.015, 2);
  });

  test('FX_FORWARD premium = notional * 0.015', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder({ contractType: DerivativeContractType.FX_FORWARD });
    const result = await proc.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.FILLED);
    expect(result.premium).toBeCloseTo(100000 * 1.10 * 0.015, 2);
  });

  test('OPTION_CALL premium = notional * 0.05', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder({
      contractType: DerivativeContractType.OPTION_CALL,
      quantity: 1000,
      strikePrice: 100,
    });
    const result = await proc.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.FILLED);
    expect(result.premium).toBeCloseTo(1000 * 100 * 0.05, 2);
  });

  test('OPTION_PUT premium = notional * 0.05', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder({
      contractType: DerivativeContractType.OPTION_PUT,
      quantity: 500,
      strikePrice: 200,
    });
    const result = await proc.processOrder(order);
    expect(result.premium).toBeCloseTo(500 * 200 * 0.05, 2);
  });

  test('rejects when notional > 10,000,000', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder({
      quantity: 10000000,
      strikePrice: 1.10,
    }); // notional = 11M
    const result = await proc.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.REJECTED);
  });

  test('rejects when quantity <= 0', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder({ quantity: 0 });
    const result = await proc.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.REJECTED);
  });

  test('rejects when strikePrice <= 0', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder({ strikePrice: 0 });
    const result = await proc.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.REJECTED);
  });

  test('rejects missing orderId', async () => {
    const proc = new DerivativeProcessor(repo);
    const order = makeDerivOrder({ orderId: '' });
    const result = await proc.processOrder(order);
    expect(result.status).toBe(DerivativeStatus.REJECTED);
  });
});

describe('FxPricingHelper', () => {
  test('EUR/USD rate = 1.10', () => {
    expect(FxPricingHelper.getMidRate('EUR/USD')).toBe(1.10);
  });

  test('GBP/USD rate = 1.55', () => {
    expect(FxPricingHelper.getMidRate('GBP/USD')).toBe(1.55);
  });

  test('JPY/USD rate = 0.009', () => {
    expect(FxPricingHelper.getMidRate('JPY/USD')).toBe(0.009);
  });

  test('CHF/USD rate = 0.72', () => {
    expect(FxPricingHelper.getMidRate('CHF/USD')).toBe(0.72);
  });

  test('AUD/USD rate = 0.68', () => {
    expect(FxPricingHelper.getMidRate('AUD/USD')).toBe(0.68);
  });

  test('unknown pair returns null', () => {
    expect(FxPricingHelper.getMidRate('XXX/YYY')).toBeNull();
  });

  test('bid/ask spread = 0.2%', () => {
    const ba = FxPricingHelper.getBidAsk('EUR/USD');
    expect(ba).not.toBeNull();
    expect(ba!.bid).toBeCloseTo(1.10 * (1 - 0.001), 4);
    expect(ba!.ask).toBeCloseTo(1.10 * (1 + 0.001), 4);
  });
});
