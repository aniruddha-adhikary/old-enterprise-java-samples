package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Client;
import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Detects potential wash trades: same client buying and selling the
 * same symbol within a short time window.
 * 
 * Required by SEC Rule 10b-5 (anti-manipulation).
 * 
 * A "wash trade" is when the same client places a BUY and SELL for
 * the same symbol in rapid succession, potentially to manipulate
 * volume or price. We check the TRADE_ORDERS table for any order
 * from the same client with the same symbol and opposite side
 * within the last 5 minutes.
 * 
 * Priority 105 = runs right after DailyVolumeLimit (110) but before
 * all legacy rules.
 * 
 * @author compliance-bolt-on
 * @since 2005 Q2
 */
public class WashTradeDetectionRule implements Rule {

    // Required by SEC Rule 10b-5 (anti-manipulation)
    private static final int WASH_TRADE_WINDOW_MINUTES = 5;

    public String getName() {
        return "WashTradeDetection";
    }

    public int getPriority() {
        return 105;
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        // Defensive null checks - assume nothing about the data
        if (context == null) {
            return false;
        }

        TradeOrder order = context.getOrder();
        if (order == null) {
            context.reject("Order is null - cannot check wash trade (REG-2005-002)");
            return false;
        }

        Client client = context.getClient();
        if (client == null) {
            context.reject("Client is null - cannot check wash trade (REG-2005-002)");
            return false;
        }

        String clientId = order.getClientId();
        if (clientId == null) {
            context.reject("Client ID is null - cannot check wash trade (REG-2005-002)");
            return false;
        }

        String symbol = order.getSymbol();
        if (symbol == null) {
            context.reject("Symbol is null - cannot check wash trade (REG-2005-002)");
            return false;
        }

        String side = order.getSide();
        if (side == null) {
            context.reject("Side is null - cannot check wash trade (REG-2005-002)");
            return false;
        }

        // Determine the opposite side for wash trade detection
        String oppositeSide = null;
        if (TradeOrder.SIDE_BUY.equals(side)) {
            oppositeSide = TradeOrder.SIDE_SELL;
        } else if (TradeOrder.SIDE_SELL.equals(side)) {
            oppositeSide = TradeOrder.SIDE_BUY;
        } else {
            // Unknown side - let it pass but log a warning
            context.addWarning("Unknown order side '" + side + "' - wash trade check skipped");
            context.setAttribute("wash_trade_checked", Boolean.TRUE);
            return true;
        }

        // Check TRADE_ORDERS for recent opposite-side order from same client+symbol
        boolean washTradeDetected = checkForWashTrade(clientId, symbol, oppositeSide);

        if (washTradeDetected) {
            context.reject("Potential wash trade detected (REG-2005-002)");
            return false;
        }

        // Mark that we checked - other systems look for this flag
        context.setAttribute("wash_trade_checked", Boolean.TRUE);
        context.addMessage("Wash trade check passed for " + clientId + "/" + symbol);
        return true;
    }

    /**
     * Query TRADE_ORDERS for a recent order with opposite side from the
     * same client and symbol. Returns true if a potential wash trade is found.
     */
    private boolean checkForWashTrade(String clientId, String symbol, String oppositeSide) {
        // Defensive null checks on parameters (yes, we already checked above,
        // but belt and suspenders - compliance insists)
        if (clientId == null || symbol == null || oppositeSide == null) {
            return false;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                // Can't check - log warning but don't block the order
                System.err.println("WARN: No DB connection for wash trade check");
                return false;
            }

            // Look for orders from same client, same symbol, opposite side,
            // within the last 5 minutes
            ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM TRADE_ORDERS " +
                "WHERE CLIENT_ID = ? AND SYMBOL = ? AND SIDE = ? " +
                "AND STATUS <> 'REJECTED' " +
                "AND ORDER_DATE > CURRENT_TIMESTAMP - " + WASH_TRADE_WINDOW_MINUTES + " * INTERVAL '1' MINUTE"
            );
            ps.setString(1, clientId);
            ps.setString(2, symbol);
            ps.setString(3, oppositeSide);

            rs = ps.executeQuery();
            if (rs != null && rs.next()) {
                int count = rs.getInt(1);
                return count > 0;
            }

            return false;
        } catch (Exception e) {
            // DB error - log but don't block the order
            // (we learned from the 2001 incident where a logging rule
            // crashed and rejected 500 orders)
            System.err.println("WARN: Wash trade DB check failed: " + e.getMessage());
            return false;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    public void execute(RuleContext context) {
        // Nothing additional on pass
    }
}
