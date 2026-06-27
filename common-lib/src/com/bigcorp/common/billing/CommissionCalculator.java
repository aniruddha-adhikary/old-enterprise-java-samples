package com.bigcorp.common.billing;

import com.bigcorp.common.model.Client;

/**
 * Centralized commission rate calculation based on client tier.
 * 
 * Resolves JIRA-2501: commission rate was hardcoded as 0.02 in both
 * OrderMessageListener and BatchProcessor. Now it lives here and
 * is derived from the client's tier level.
 * 
 * Tier rates (set by compliance, 2002-Q3):
 *   PLATINUM = 0.005 (0.5%)  - our best clients pay least
 *   GOLD     = 0.010 (1.0%)
 *   SILVER   = 0.015 (1.5%)
 *   BRONZE   = 0.020 (2.0%)  - default / walkup rate
 * 
 * @author Commission team (JIRA-2501)
 * @since 2.1
 */
public class CommissionCalculator {

    private static final double RATE_PLATINUM = 0.005;
    private static final double RATE_GOLD = 0.010;
    private static final double RATE_SILVER = 0.015;
    private static final double RATE_BRONZE = 0.020;
    private static final double RATE_DEFAULT = 0.020;

    /**
     * Get the commission rate for a client based on their tier.
     * Returns the rate as a decimal (e.g. 0.02 = 2%).
     */
    public static double getRate(String tier) {
        if (tier == null) {
            return RATE_DEFAULT;
        }
        if (Client.TIER_PLATINUM.equals(tier)) {
            return RATE_PLATINUM;
        } else if (Client.TIER_GOLD.equals(tier)) {
            return RATE_GOLD;
        } else if (Client.TIER_SILVER.equals(tier)) {
            return RATE_SILVER;
        } else if (Client.TIER_BRONZE.equals(tier)) {
            return RATE_BRONZE;
        }
        return RATE_DEFAULT;
    }

    /**
     * Calculate the commission amount for a given order value and client tier.
     */
    public static double calculate(double orderValue, String tier) {
        return orderValue * getRate(tier);
    }

    /**
     * Calculate commission using a Client object.
     */
    public static double calculate(double orderValue, Client client) {
        if (client == null) {
            return orderValue * RATE_DEFAULT;
        }
        return calculate(orderValue, client.getTier());
    }
}
