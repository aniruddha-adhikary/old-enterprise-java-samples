package com.bigcorp.derivatives.core;

import com.bigcorp.derivatives.util.DerivativeLogger;

/**
 * FX pricing helper with hardcoded rates.
 * 
 * This is basically a copy-paste of some of the pricing logic from
 * pricing-service, but adapted for FX pairs instead of equities.
 * We asked the pricing team if we could reuse their service but
 * they said "our SOAP endpoint only handles equities."
 * 
 * The rates here are hardcoded because the FX rates table hasn't
 * been created yet (DBA backlog, JIRA-3200).
 * 
 * @author External contractor
 * @since 2004-Q3
 */
public class FxPricingHelper {

    private static final DerivativeLogger log = new DerivativeLogger(FxPricingHelper.class);

    // Hardcoded FX rates vs USD
    // "Current" as of 2004-07-15. Will be stale by the time you read this.
    private static final double RATE_EUR_USD = 1.10;
    private static final double RATE_GBP_USD = 1.55;
    private static final double RATE_JPY_USD = 0.009;
    private static final double RATE_CHF_USD = 0.72;
    private static final double RATE_AUD_USD = 0.68;

    // Spread for FX quotes (bid/ask spread in percentage)
    private static final double FX_SPREAD = 0.002;

    /**
     * Get the mid-market rate for a currency pair.
     * Returns the rate in terms of USD (i.e., how many USD per 1 unit of currency).
     * 
     * @param ccyPair e.g. "EUR/USD", "GBP/USD", "JPY/USD"
     * @return mid rate, or -1.0 if pair not found
     */
    public static double getRate(String ccyPair) {
        if (ccyPair == null || ccyPair.length() == 0) {
            log.error("getRate called with null/empty pair");
            return -1.0;
        }

        // Normalize
        String pair = ccyPair.trim().toUpperCase();

        if ("EUR/USD".equals(pair) || "EURUSD".equals(pair)) {
            return RATE_EUR_USD;
        } else if ("GBP/USD".equals(pair) || "GBPUSD".equals(pair)) {
            return RATE_GBP_USD;
        } else if ("JPY/USD".equals(pair) || "JPYUSD".equals(pair)) {
            return RATE_JPY_USD;
        } else if ("CHF/USD".equals(pair) || "CHFUSD".equals(pair)) {
            return RATE_CHF_USD;
        } else if ("AUD/USD".equals(pair) || "AUDUSD".equals(pair)) {
            return RATE_AUD_USD;
        } else if ("USD/USD".equals(pair) || "USDUSD".equals(pair)) {
            return 1.0;
        }

        log.warn("Unknown currency pair: " + pair + ", returning -1");
        return -1.0;
    }

    /**
     * Get bid price for a currency pair.
     * Bid = mid * (1 - spread/2)
     */
    public static double getBid(String ccyPair) {
        double mid = getRate(ccyPair);
        if (mid < 0) return -1.0;
        return mid * (1.0 - FX_SPREAD / 2.0);
    }

    /**
     * Get ask price for a currency pair.
     * Ask = mid * (1 + spread/2)
     */
    public static double getAsk(String ccyPair) {
        double mid = getRate(ccyPair);
        if (mid < 0) return -1.0;
        return mid * (1.0 + FX_SPREAD / 2.0);
    }

    /**
     * Convert an amount from one currency to USD.
     * This is a simplified version of what pricing-service does for equities,
     * but we only handle FX pairs here.
     * 
     * @param amount the amount in source currency
     * @param ccyPair e.g. "EUR/USD"
     * @return equivalent USD amount, or -1.0 if pair unknown
     */
    public static double convertToUsd(double amount, String ccyPair) {
        double rate = getRate(ccyPair);
        if (rate < 0) {
            log.error("Cannot convert: unknown pair " + ccyPair);
            return -1.0;
        }
        return amount * rate;
    }

    /**
     * Check if a currency pair is supported.
     */
    public static boolean isSupportedPair(String ccyPair) {
        return getRate(ccyPair) >= 0;
    }
}
