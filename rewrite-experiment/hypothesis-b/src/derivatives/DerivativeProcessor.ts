import { DerivativeOrder, DerivativeContractType, DerivativeStatus, MAX_NOTIONAL } from '../domain/DerivativeOrder';
import { FX_RATES } from '../pricing/FxPricingHelper';

// Derivatives commission from spec.json financials.derivativesPremium
const FX_COMMISSION = 0.015;
const OPTION_RATE = 0.05; // Flat 5% of notional, placeholder for Black-Scholes

// BUG-012: FX rates duplicated here (also in MultiCurrencyRule and FxPricingHelper)
const DERIVATIVE_FX_RATES: Record<string, number> = {
  'EUR/USD': 1.10,
  'GBP/USD': 1.55,
  'JPY/USD': 0.009,
  'CHF/USD': 0.72,
  'AUD/USD': 0.68,
  'USD/USD': 1.0,
};

export class DerivativeProcessor {
  processOrder(order: DerivativeOrder): DerivativeOrder {
    // Validation
    if (!order.orderId || !order.clientId || !order.contractType || !order.underlying) {
      return { ...order, status: DerivativeStatus.REJECTED };
    }

    const validTypes = Object.values(DerivativeContractType);
    if (!validTypes.includes(order.contractType as DerivativeContractType)) {
      return { ...order, status: DerivativeStatus.REJECTED };
    }

    // Compute notional
    const notional = order.quantity * order.strikePrice;
    if (notional > MAX_NOTIONAL) {
      return { ...order, status: DerivativeStatus.REJECTED };
    }

    // Compute premium based on contract type
    let premium: number;
    if (order.contractType === DerivativeContractType.FX_SPOT ||
        order.contractType === DerivativeContractType.FX_FORWARD) {
      premium = notional * FX_COMMISSION;
    } else {
      // OPTION_CALL or OPTION_PUT: flat 5% of notional
      premium = notional * OPTION_RATE;
    }

    return {
      ...order,
      premium,
      status: DerivativeStatus.FILLED,
    };
  }

  getFxRate(pair: string): number {
    return DERIVATIVE_FX_RATES[pair] ?? 1.0;
  }
}
