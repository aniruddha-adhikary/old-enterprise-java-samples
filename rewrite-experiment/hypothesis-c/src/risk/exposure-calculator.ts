import { RiskAssessment, RiskStatus } from '../domain/risk';

/**
 * Exposure and VaR calculator, preserving exact formulas from
 * risk-engine ExposureCalculator.java.
 *
 * VaR = notional * volatility * z_score * sqrt(holdingDays / 252)
 *
 * Parameters:
 *   z_score = 2.33 (99% confidence)
 *   holdingDays = 1
 *   tradingDaysPerYear = 252
 *
 * Volatility assumptions:
 *   Equity:    20% (VOL_EQUITY)
 *   FX:         8% (VOL_FX)
 *   Commodity: 30% (VOL_COMMODITY)
 *   Default:   25% (VOL_DEFAULT)
 *
 * VaR flagging threshold: $50,000
 *
 * Exposure:
 *   BUY  -> +notional
 *   SELL -> -notional
 */
export class ExposureCalculator {
  static readonly VOL_EQUITY = 0.20;
  static readonly VOL_FX = 0.08;
  static readonly VOL_COMMODITY = 0.30;
  static readonly VOL_DEFAULT = 0.25;

  static readonly VAR_Z_SCORE = 2.33;
  static readonly HOLDING_PERIOD_DAYS = 1.0;
  static readonly TRADING_DAYS_PER_YEAR = 252.0;
  static readonly VAR_THRESHOLD = 50_000.0;

  static readonly RISK_QUEUE_ORDERS = 'RISK.ORDERS.INBOUND';
  static readonly RISK_QUEUE_RESULTS = 'RISK.RESULTS.OUTBOUND';

  static getVolatility(symbol: string): number {
    if (!symbol) return ExposureCalculator.VOL_DEFAULT;

    if (symbol.includes('/')) return ExposureCalculator.VOL_FX;

    if (['GOLD', 'OIL', 'SILVER'].includes(symbol)) {
      return ExposureCalculator.VOL_COMMODITY;
    }

    return ExposureCalculator.VOL_EQUITY;
  }

  static calculateRisk(assessment: RiskAssessment): void {
    const notional = assessment.quantity * assessment.price;
    assessment.notionalValue = notional;

    if (assessment.side === 'BUY') {
      assessment.exposureContribution = notional;
    } else if (assessment.side === 'SELL') {
      assessment.exposureContribution = -notional;
    } else {
      assessment.exposureContribution = notional;
    }

    const vol = ExposureCalculator.getVolatility(assessment.symbol);
    const sqrtTime = Math.sqrt(
      ExposureCalculator.HOLDING_PERIOD_DAYS / ExposureCalculator.TRADING_DAYS_PER_YEAR
    );
    const varValue = Math.abs(notional) * vol * ExposureCalculator.VAR_Z_SCORE * sqrtTime;
    assessment.varContribution = varValue;

    if (varValue > ExposureCalculator.VAR_THRESHOLD) {
      assessment.riskStatus = RiskStatus.FLAGGED;
    } else {
      assessment.riskStatus = RiskStatus.ASSESSED;
    }
  }
}
