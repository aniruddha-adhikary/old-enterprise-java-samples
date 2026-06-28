import { DerivativeOrder, ContractType, DerivativeStatus } from '../domain/derivative';

/**
 * Derivative order processor, preserving behavior from
 * derivatives-engine DerivativeProcessor.java.
 *
 * FX commission rate: 1.5% (0.015) - different from equities 2%.
 *
 * Premium calculation:
 *   FX_SPOT/FX_FORWARD: notional * FX_COMMISSION (1.5%)
 *   OPTION_CALL/OPTION_PUT: notional * 5% (flat, placeholder for BS)
 *
 * Max notional per order: $10,000,000.
 *
 * KNOWN BUG: FX rates hardcoded here identically to MultiCurrencyRule.
 * Changes in one are not reflected in the other.
 */
export class DerivativeProcessor {
  static readonly FX_COMMISSION = 0.015;
  static readonly OPTION_PREMIUM_RATE = 0.05;
  static readonly MAX_NOTIONAL = 10_000_000.0;

  static readonly FX_RATES: Record<string, number> = {
    EUR_USD: 1.10,
    GBP_USD: 1.55,
    JPY_USD: 0.009,
  };

  static readonly DERIVATIVE_QUEUES = {
    ORDERS: 'BIGCORP.DERIVATIVES.ORDERS',
    CONFIRMS: 'BIGCORP.DERIVATIVES.CONFIRMS',
    PRICING: 'BIGCORP.DERIVATIVES.PRICING',
  };

  processOrder(order: DerivativeOrder): DerivativeOrder {
    const validationError = this.validateOrder(order);
    if (validationError) {
      order.status = DerivativeStatus.REJECTED;
      return order;
    }

    order.premium = this.computePremium(order);
    order.status = DerivativeStatus.FILLED;
    return order;
  }

  private validateOrder(order: DerivativeOrder): string | null {
    if (!order.orderId) return 'Missing orderId';
    if (!order.clientId) return 'Missing clientId';
    if (!order.contractType) return 'Missing contractType';

    const validTypes = [ContractType.FX_SPOT, ContractType.FX_FORWARD, ContractType.OPTION_CALL, ContractType.OPTION_PUT];
    if (!validTypes.includes(order.contractType)) {
      return `Unknown contract type: ${order.contractType}`;
    }

    if (!order.underlying) return 'Missing underlying';
    if (order.quantity <= 0) return `Invalid quantity: ${order.quantity}`;
    if (order.strikePrice <= 0) return `Invalid strike price: ${order.strikePrice}`;

    const notional = order.quantity * order.strikePrice;
    if (notional > DerivativeProcessor.MAX_NOTIONAL) {
      return `Notional ${notional} exceeds limit ${DerivativeProcessor.MAX_NOTIONAL}`;
    }

    return null;
  }

  private computePremium(order: DerivativeOrder): number {
    const notional = order.quantity * order.strikePrice;

    if (order.contractType === ContractType.FX_SPOT || order.contractType === ContractType.FX_FORWARD) {
      return notional * DerivativeProcessor.FX_COMMISSION;
    }

    return notional * DerivativeProcessor.OPTION_PREMIUM_RATE;
  }

  calculateCommission(amount: number): number {
    return amount * DerivativeProcessor.FX_COMMISSION;
  }

  static getCommissionRate(): number {
    return DerivativeProcessor.FX_COMMISSION;
  }
}
