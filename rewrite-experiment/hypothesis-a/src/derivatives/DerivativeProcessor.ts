import { DerivativeOrder } from '../domain/DerivativeOrder';
import { DerivativeContractType, DerivativeStatus } from '../domain/enums';

const VALID_CONTRACT_TYPES = [
  DerivativeContractType.FX_SPOT,
  DerivativeContractType.FX_FORWARD,
  DerivativeContractType.OPTION_CALL,
  DerivativeContractType.OPTION_PUT,
];

const MAX_NOTIONAL = 10_000_000;
const FX_COMMISSION = 0.015; // 1.5%, separate from equities (2%)
const OPTION_FLAT_RATE = 0.05; // 5% placeholder for Black-Scholes

const FX_RATES: Record<string, number> = {
  'EUR/USD': 1.10,
  'GBP/USD': 1.55,
  'JPY/USD': 0.009,
  'CHF/USD': 0.72,
  'AUD/USD': 0.68,
};

const FX_SPREAD = 0.002;

export interface DerivativeRepository {
  saveDerivativeOrder(order: DerivativeOrder): Promise<void>;
}

export class FxPricingHelper {
  static getMidRate(pair: string): number | null {
    return FX_RATES[pair] ?? null;
  }

  static getBidAsk(pair: string): { bid: number; ask: number } | null {
    const mid = FX_RATES[pair];
    if (mid === undefined) return null;
    return {
      bid: mid * (1 - FX_SPREAD / 2),
      ask: mid * (1 + FX_SPREAD / 2),
    };
  }

  static getAllRates(): Record<string, number> {
    return { ...FX_RATES };
  }
}

export class DerivativeProcessor {
  constructor(private readonly repo: DerivativeRepository) {}

  async processOrder(order: DerivativeOrder): Promise<DerivativeOrder> {
    const validationError = this.validate(order);
    if (validationError) {
      order.status = DerivativeStatus.REJECTED;
      console.error(
        `[DerivativeProcessor] Rejected ${order.orderId}: ${validationError}`
      );
      await this.repo.saveDerivativeOrder(order);
      return order;
    }

    order.premium = this.calculatePremium(order);
    order.status = DerivativeStatus.FILLED;
    await this.repo.saveDerivativeOrder(order);
    return order;
  }

  private validate(order: DerivativeOrder): string | null {
    if (!order.orderId) return 'orderId is required';
    if (!order.clientId) return 'clientId is required';
    if (!order.contractType) return 'contractType is required';
    if (!order.underlying) return 'underlying is required';

    if (!VALID_CONTRACT_TYPES.includes(order.contractType)) {
      return `Invalid contract type: ${order.contractType}`;
    }

    if (order.quantity <= 0) return 'quantity must be > 0';
    if (order.strikePrice <= 0) return 'strikePrice must be > 0';

    const notional = order.quantity * order.strikePrice;
    if (notional > MAX_NOTIONAL) {
      return `Notional ${notional} exceeds maximum ${MAX_NOTIONAL}`;
    }

    return null;
  }

  private calculatePremium(order: DerivativeOrder): number {
    const notional = order.quantity * order.strikePrice;

    switch (order.contractType) {
      case DerivativeContractType.FX_SPOT:
      case DerivativeContractType.FX_FORWARD:
        return notional * FX_COMMISSION;
      case DerivativeContractType.OPTION_CALL:
      case DerivativeContractType.OPTION_PUT:
        return notional * OPTION_FLAT_RATE;
      default:
        return notional * FX_COMMISSION;
    }
  }
}
