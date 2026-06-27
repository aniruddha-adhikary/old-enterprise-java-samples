import { RiskOrder } from '../domain/RiskOrder';
import { RiskStatus, OrderSide } from '../domain/enums';

const Z_SCORE = 2.33;            // 99% confidence
const HOLDING_PERIOD = 1.0;       // 1 day
const TRADING_DAYS_PER_YEAR = 252.0;

const VOL_EQUITY = 0.20;
const VOL_FX = 0.08;
const VOL_COMMODITY = 0.30;
const VOL_UNKNOWN = 0.25;

const COMMODITY_SYMBOLS = ['GOLD', 'OIL', 'SILVER'];
const VAR_FLAG_THRESHOLD = 50000.0;

export interface RiskRepository {
  saveRiskAssessment(riskOrder: RiskOrder): Promise<void>;
  findPendingOrders(): Promise<RiskOrder[]>;
}

function getVolatility(symbol: string): number {
  if (symbol.includes('/')) return VOL_FX;
  if (COMMODITY_SYMBOLS.includes(symbol.toUpperCase())) return VOL_COMMODITY;
  // check if it looks like an equity (no special chars = equity default)
  if (/^[A-Z]+$/.test(symbol.toUpperCase())) return VOL_EQUITY;
  return VOL_UNKNOWN;
}

export function calculateVaR(notional: number, symbol: string): number {
  const vol = getVolatility(symbol);
  return (
    Math.abs(notional) *
    vol *
    Z_SCORE *
    Math.sqrt(HOLDING_PERIOD / TRADING_DAYS_PER_YEAR)
  );
}

export function calculateExposure(
  notional: number,
  side: OrderSide | string
): number {
  if (side === OrderSide.BUY) return notional;
  if (side === OrderSide.SELL) return -notional;
  return notional; // unknown side defaults to positive
}

export class ExposureCalculator {
  constructor(private readonly repo: RiskRepository) {}

  async assessOrder(riskOrder: RiskOrder): Promise<RiskOrder> {
    try {
      riskOrder.notionalValue = riskOrder.quantity * riskOrder.price;
      riskOrder.exposureContribution = calculateExposure(
        riskOrder.notionalValue,
        riskOrder.side
      );
      riskOrder.varContribution = calculateVaR(
        riskOrder.notionalValue,
        riskOrder.symbol
      );

      if (riskOrder.varContribution > VAR_FLAG_THRESHOLD) {
        riskOrder.riskStatus = RiskStatus.FLAGGED;
      } else {
        riskOrder.riskStatus = RiskStatus.ASSESSED;
      }
    } catch {
      riskOrder.riskStatus = RiskStatus.ERROR;
    }

    riskOrder.assessmentDate = new Date();
    await this.repo.saveRiskAssessment(riskOrder);
    return riskOrder;
  }

  async processUnassessed(): Promise<RiskOrder[]> {
    const pending = await this.repo.findPendingOrders();
    const results: RiskOrder[] = [];

    for (const order of pending) {
      const assessed = await this.assessOrder(order);
      results.push(assessed);
    }

    return results;
  }
}
