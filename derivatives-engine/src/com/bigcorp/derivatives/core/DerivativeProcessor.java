package com.bigcorp.derivatives.core;

import com.bigcorp.derivatives.util.DerivativeLogger;

/**
 * Processes derivative orders for the FX/options desk.
 * 
 * We have our own pricing and commission logic here because the
 * equities pricing-service doesn't handle FX pairs, and the
 * RuleEngine from common-lib is overkill for what we need.
 * 
 * The FX_COMMISSION rate is 1.5% -- different from the equities
 * desk's 2%. This was agreed with the derivatives trading manager.
 * (Don't change it without talking to them first.)
 * 
 * @author External contractor
 * @since 2004-Q3
 */
public class DerivativeProcessor {

    private static final DerivativeLogger log = new DerivativeLogger(DerivativeProcessor.class);

    // Our own commission rate for FX/derivatives -- 1.5%
    // Equities desk uses 2% but that doesn't apply to us
    private static final double FX_COMMISSION = 0.015;

    // Hardcoded FX rates (no DB lookup -- the FX rates table hasn't
    // been built yet and we're not going to wait for the DBA)
    private static final double EUR_USD = 1.10;
    private static final double GBP_USD = 1.55;
    private static final double JPY_USD = 0.009;

    // Max notional per order (risk limit from the derivatives desk)
    private static final double MAX_NOTIONAL = 10000000.0;

    /**
     * Process a derivative order: validate -> price -> fill/reject.
     * Returns the processed order with status and premium updated.
     */
    public DerivativeOrder processOrder(DerivativeOrder order) {
        if (order == null) {
            log.error("processOrder called with null order");
            return null;
        }
        log.info("Processing derivative order: " + order.getOrderId()
                + " type=" + order.getContractType() + " underlying=" + order.getUnderlying());

        // Step 1: basic validation (our own rules, not the equities RuleEngine)
        String validationError = validateOrder(order);
        if (validationError != null) {
            order.setStatus(DerivativeOrder.STATUS_REJECTED);
            log.warn("Order " + order.getOrderId() + " REJECTED: " + validationError);
            return order;
        }

        // Step 2: compute premium / price
        double computedPremium = computePremium(order);
        order.setPremium(computedPremium);

        // Step 3: fill the order
        order.setStatus(DerivativeOrder.STATUS_FILLED);
        log.info("Order " + order.getOrderId() + " FILLED, premium=" + computedPremium);

        return order;
    }

    /**
     * Our own validation logic. We don't use RuleEngine because:
     *  (a) it's overly complex for what we need
     *  (b) it doesn't understand FX contract types
     *  (c) we'd have to add a dependency on their rule config
     */
    private String validateOrder(DerivativeOrder order) {
        if (order.getOrderId() == null || order.getOrderId().length() == 0) {
            return "Missing orderId";
        }
        if (order.getClientId() == null || order.getClientId().length() == 0) {
            return "Missing clientId";
        }
        if (order.getContractType() == null || order.getContractType().length() == 0) {
            return "Missing contractType";
        }
        if (!isValidContractType(order.getContractType())) {
            return "Unknown contract type: " + order.getContractType();
        }
        if (order.getUnderlying() == null || order.getUnderlying().length() == 0) {
            return "Missing underlying";
        }
        if (order.getQuantity() <= 0) {
            return "Invalid quantity: " + order.getQuantity();
        }
        if (order.getStrikePrice() <= 0) {
            return "Invalid strike price: " + order.getStrikePrice();
        }

        // Notional check
        double notional = order.getQuantity() * order.getStrikePrice();
        if (notional > MAX_NOTIONAL) {
            return "Notional " + notional + " exceeds limit " + MAX_NOTIONAL;
        }

        return null;
    }

    private boolean isValidContractType(String type) {
        return DerivativeOrder.TYPE_FX_SPOT.equals(type)
                || DerivativeOrder.TYPE_FX_FORWARD.equals(type)
                || DerivativeOrder.TYPE_OPTION_CALL.equals(type)
                || DerivativeOrder.TYPE_OPTION_PUT.equals(type);
    }

    /**
     * Compute the premium for an order based on contract type.
     * FX_SPOT/FX_FORWARD: premium = notional * commission
     * OPTION_CALL/OPTION_PUT: premium = simplified Black-Scholes-ish
     *   (we use a flat 5% of notional because nobody asked for real BS)
     */
    private double computePremium(DerivativeOrder order) {
        double notional = order.getQuantity() * order.getStrikePrice();

        if (DerivativeOrder.TYPE_FX_SPOT.equals(order.getContractType())
                || DerivativeOrder.TYPE_FX_FORWARD.equals(order.getContractType())) {
            return notional * FX_COMMISSION;
        } else {
            // Options: flat 5% of notional (placeholder)
            return notional * 0.05;
        }
    }

    /**
     * Calculate commission for a given trade amount.
     * Uses our own rate (1.5%), not the equities rate (2%).
     */
    public double calculateCommission(double amount) {
        return amount * FX_COMMISSION;
    }

    /**
     * Get the FX commission rate used by this processor.
     */
    public static double getCommissionRate() {
        return FX_COMMISSION;
    }
}
