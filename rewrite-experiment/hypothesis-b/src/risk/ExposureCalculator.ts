import { RiskOrder, RiskStatus, VAR_FLAG_THRESHOLD } from '../domain/RiskOrder';

// VaR calculation parameters from spec.json financials.varCalculation
const VAR_PARAMS = {
  zScore: 2.33,           // 99% confidence level
  holdingPeriodDays: 1.0,
  tradingDaysPerYear: 252.0,
};

// Volatility assumptions by asset class
const VOLATILITY: Record<string, number> = {
  equity: 0.20,
  fx: 0.08,
  commodity: 0.30,
  default: 0.25,
};

const COMMODITY_SYMBOLS = ['GOLD', 'OIL', 'SILVER'];

function getAssetClass(symbol: string): string {
  if (COMMODITY_SYMBOLS.includes(symbol.toUpperCase())) return 'commodity';
  if (symbol.includes('/')) return 'fx';
  return 'equity';
}

export function calculateVaR(notional: number, symbol: string): number {
  // VaR = |notional| * volatility * zScore * sqrt(holdingPeriod / tradingDaysPerYear)
  const assetClass = getAssetClass(symbol);
  const volatility = VOLATILITY[assetClass] ?? VOLATILITY['default'];
  const timeFactor = Math.sqrt(VAR_PARAMS.holdingPeriodDays / VAR_PARAMS.tradingDaysPerYear);

  return Math.abs(notional) * volatility * VAR_PARAMS.zScore * timeFactor;
}

export function assessRisk(params: {
  sourceOrderId: string;
  clientId: string;
  symbol: string;
  quantity: number;
  side: 'BUY' | 'SELL';
  price: number;
}): RiskOrder {
  const notionalValue = params.quantity * params.price;
  const exposureContribution = params.side === 'BUY' ? notionalValue : -notionalValue;
  const varContribution = calculateVaR(notionalValue, params.symbol);

  let riskStatus: string;
  if (varContribution > VAR_FLAG_THRESHOLD) {
    riskStatus = RiskStatus.FLAGGED;
  } else {
    riskStatus = RiskStatus.ASSESSED;
  }

  return {
    riskOrderId: `RISK-${Date.now()}-${params.sourceOrderId}`,
    sourceOrderId: params.sourceOrderId,
    clientId: params.clientId,
    symbol: params.symbol,
    quantity: params.quantity,
    side: params.side,
    price: params.price,
    notionalValue,
    exposureContribution,
    varContribution,
    riskStatus,
    assessmentDate: new Date(),
  };
}
