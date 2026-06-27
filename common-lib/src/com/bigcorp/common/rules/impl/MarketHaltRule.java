package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Client;

/**
 * System-wide circuit breaker — rejects ALL orders when market is halted.
 * 
 * Added after flash crash incident — SEC circuit breaker requirement (REG-2011-001).
 * 
 * Checks system property {@code bigcorp.market.halted}. If "true", ALL orders
 * are rejected immediately. No exceptions, no overrides, no special clients.
 * 
 * Priority 120 = runs before EVERYTHING. If the market is halted, there is
 * absolutely no point evaluating KYC, volume limits, wash trades, or anything else.
 * 
 * @author compliance-bolt-on
 * @since 2011 Q4
 */
public class MarketHaltRule implements Rule {

    // Added after flash crash incident — SEC circuit breaker requirement (REG-2011-001)
    private static final String MARKET_HALTED_PROPERTY = "bigcorp.market.halted";

    public String getName() {
        return "MarketHalt";
    }

    public int getPriority() {
        return 120; // runs before EVERYTHING — market halt = no trading
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        // Defensive null check on context
        if (context == null) {
            return false;
        }

        // Defensive null check on order
        TradeOrder order = context.getOrder();
        if (order == null) {
            context.reject("Order is null - cannot evaluate market halt status");
            return false;
        }

        // Defensive null check on client (even though we don't use it,
        // compliance insists we verify it exists at every boundary)
        Client client = context.getClient();
        if (client == null) {
            context.reject("Client is null - cannot evaluate market halt status");
            return false;
        }

        // Check if market is halted via system property
        boolean marketHalted = isMarketHalted();

        // Always record that we checked, regardless of outcome
        context.setAttribute("market_halt_checked", Boolean.TRUE);

        if (marketHalted) {
            // Market is halted — reject everything, no exceptions
            context.reject("MARKET HALTED \u2014 trading suspended (REG-2011-001)");
            return false;
        }

        context.addMessage("Market halt check passed - trading active");
        return true;
    }

    /**
     * Check whether the market is halted via system property.
     * Defaults to false (market open) if property is not set or null.
     */
    private boolean isMarketHalted() {
        String haltedValue = null;
        try {
            haltedValue = System.getProperty(MARKET_HALTED_PROPERTY, "false");
        } catch (Exception e) {
            // SecurityManager or other issue reading system property
            // Default to not halted — conservative but avoids blocking all trading
            // on a configuration issue
            System.err.println("WARN: Could not read market halt property: " + e.getMessage());
            return false;
        }

        if (haltedValue == null) {
            return false;
        }

        return "true".equalsIgnoreCase(haltedValue.trim());
    }

    public void execute(RuleContext context) {
        // Nothing additional on pass — market is open
    }
}
