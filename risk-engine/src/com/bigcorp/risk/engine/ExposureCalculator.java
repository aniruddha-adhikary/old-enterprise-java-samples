package com.bigcorp.risk.engine;

import com.bigcorp.risk.model.RiskOrder;

/**
 * Exposure and VaR-style metric calculator.
 * 
 * We compute our own risk metrics rather than using any shared
 * library because the risk team has specific requirements that
 * don't map to the trading engine's model.
 * 
 * VaR is a simplified parametric VaR (delta-normal) using hardcoded
 * volatility constants. Real VaR would need historical simulation
 * but "we'll add that later when we get the data feed." — risk PM
 * 
 * @author contractor (risk team)
 * @since 2017-Q1
 */
public class ExposureCalculator {

    // Hardcoded volatility assumptions per symbol category
    // These should come from a risk config file but the risk PM
    // said "just hardcode them for now, we'll update quarterly"
    private static final double VOL_EQUITY = 0.20;     // 20% annual vol
    private static final double VOL_FX = 0.08;         // 8% for FX
    private static final double VOL_COMMODITY = 0.30;  // 30% for commodities
    private static final double VOL_DEFAULT = 0.25;    // conservative default

    // VaR confidence level (99% = 2.33 standard deviations)
    private static final double VAR_Z_SCORE = 2.33;

    // Time horizon in trading days (1 day VaR)
    private static final double HOLDING_PERIOD_DAYS = 1.0;
    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    // Risk queue name — our own queue, not the shared ones
    public static final String RISK_QUEUE_ORDERS = "RISK.ORDERS.INBOUND";
    public static final String RISK_QUEUE_RESULTS = "RISK.RESULTS.OUTBOUND";

    /**
     * Calculate exposure and VaR for a given risk order.
     * Mutates the RiskOrder with computed values.
     */
    public static void calculateRisk(RiskOrder order) {
        if (order == null) return;

        // Notional value = qty * price
        double notional = order.getQuantity() * order.getPrice();
        order.setNotionalValue(notional);

        // Exposure is directional: BUY = positive, SELL = negative
        double exposure;
        if ("BUY".equals(order.getSide())) {
            exposure = notional;
        } else if ("SELL".equals(order.getSide())) {
            exposure = -notional;
        } else {
            // Unknown side — treat as full exposure
            exposure = notional;
        }
        order.setExposureContribution(exposure);

        // VaR = notional * vol * z-score * sqrt(t/252)
        double vol = getVolatility(order.getSymbol());
        double sqrtTime = Math.sqrt(HOLDING_PERIOD_DAYS / TRADING_DAYS_PER_YEAR);
        double var = Math.abs(notional) * vol * VAR_Z_SCORE * sqrtTime;
        order.setVarContribution(var);

        // Flag if VaR exceeds threshold
        if (var > 50000.0) {
            order.setRiskStatus(RiskOrder.RISK_STATUS_FLAGGED);
        } else {
            order.setRiskStatus(RiskOrder.RISK_STATUS_ASSESSED);
        }
    }

    /**
     * Get assumed volatility for a symbol.
     * Uses hardcoded symbol prefixes because we don't have a proper
     * asset classification service.
     */
    private static double getVolatility(String symbol) {
        if (symbol == null) return VOL_DEFAULT;

        // Very crude classification based on symbol patterns
        // FX pairs contain /
        if (symbol.indexOf('/') >= 0) return VOL_FX;

        // Known commodity symbols
        if ("GOLD".equals(symbol) || "OIL".equals(symbol) || "SILVER".equals(symbol)) {
            return VOL_COMMODITY;
        }

        // Everything else is equity
        return VOL_EQUITY;
    }
}
