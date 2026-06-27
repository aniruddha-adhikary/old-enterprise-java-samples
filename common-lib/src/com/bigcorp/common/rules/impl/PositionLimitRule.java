package com.bigcorp.common.rules.impl;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Position limit enforcement rule.
 * 
 * Checks whether accepting this order would push a client's total
 * position in a symbol beyond the configured limit.
 * 
 * Position limits are checked from the POSITION_TRACKING table
 * (added as part of the surveillance initiative, REG-2015-003).
 * 
 * If the POSITION_TRACKING table is empty or the position is unknown,
 * we fail open and allow the trade (defense in depth — other rules
 * will catch problems too).
 * 
 * @author compliance-bolt-on
 * @since 2015-Q2
 */
public class PositionLimitRule implements Rule {

    // Default position limit per client per symbol (in shares)
    // This should be configurable but "we'll get to it" (JIRA-8200)
    private static final int DEFAULT_POSITION_LIMIT = 100000;

    public String getName() {
        return "PositionLimit";
    }

    public int getPriority() {
        return 123;
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        if (context == null || context.getOrder() == null) {
            return true;
        }

        // Defensive null checks
        if (context.getOrder().getClientId() == null || context.getOrder().getSymbol() == null) {
            context.setAttribute("position_limit_checked", Boolean.TRUE);
            context.setAttribute("position_status", "SKIPPED_NO_DATA");
            return true;
        }

        String clientId = context.getOrder().getClientId();
        String symbol = context.getOrder().getSymbol();
        int orderQty = context.getOrder().getQuantity();

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                context.setAttribute("position_limit_checked", Boolean.FALSE);
                return true;
            }

            // Check current position from POSITION_TRACKING
            ps = conn.prepareStatement(
                "SELECT NET_POSITION FROM POSITION_TRACKING WHERE CLIENT_ID = ? AND SYMBOL = ?"
            );
            ps.setString(1, clientId);
            ps.setString(2, symbol);
            rs = ps.executeQuery();

            int currentPosition = 0;
            boolean positionFound = false;
            if (rs.next()) {
                currentPosition = rs.getInt("NET_POSITION");
                positionFound = true;
            }

            context.setAttribute("position_limit_checked", Boolean.TRUE);
            context.setAttribute("current_position", new Integer(currentPosition));

            if (positionFound) {
                int newPosition;
                if ("BUY".equals(context.getOrder().getSide())) {
                    newPosition = currentPosition + orderQty;
                } else {
                    newPosition = currentPosition - orderQty;
                }

                if (Math.abs(newPosition) > DEFAULT_POSITION_LIMIT) {
                    // Reject — position limit would be breached
                    context.reject("Position limit breach (REG-2015-003): " + clientId + "/" + symbol
                        + " current=" + currentPosition + " proposed=" + newPosition
                        + " limit=" + DEFAULT_POSITION_LIMIT);
                    context.setAttribute("position_status", "REJECTED");
                    return false;
                }
                context.setAttribute("position_status", "WITHIN_LIMIT");
            } else {
                // No position record — first trade for this client+symbol
                // Check if the order itself exceeds the limit
                if (orderQty > DEFAULT_POSITION_LIMIT) {
                    context.reject("Position limit breach (REG-2015-003): initial order " + orderQty
                        + " exceeds limit " + DEFAULT_POSITION_LIMIT);
                    context.setAttribute("position_status", "REJECTED");
                    return false;
                }
                context.setAttribute("position_status", "NEW_POSITION");
            }

            return true;

        } catch (Exception e) {
            // Position check failure — fail open
            System.err.println("WARN: PositionLimitRule DB error: " + e.getMessage());
            context.setAttribute("position_limit_checked", Boolean.FALSE);
            return true;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    public void execute(RuleContext context) {
        // no side effects beyond what evaluate() sets
    }
}
