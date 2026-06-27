package com.bigcorp.common.rules.impl;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Trade surveillance rule: detects potential layering patterns.
 * 
 * Layering is when a trader places multiple orders on one side of the
 * book at different price levels to create a false impression of
 * supply/demand, then cancels them after executing on the other side.
 * 
 * Added after the SEC inquiry in 2015 (REG-2015-001).
 * 
 * This is a simplified check — counts recent orders from the same client
 * for the same symbol to see if there's an unusually high order count
 * in a short window (potential layering indicator).
 * 
 * @author compliance-bolt-on
 * @since 2015-Q1
 */
public class LayeringDetectionRule implements Rule {

    // Threshold: more than 5 orders for the same client+symbol is suspicious
    private static final int LAYERING_THRESHOLD = 5;

    public String getName() {
        return "LayeringDetection";
    }

    public int getPriority() {
        return 125;
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        if (context == null || context.getOrder() == null) {
            return true;
        }

        // Defensive null check — can't check layering without client and symbol
        if (context.getOrder().getClientId() == null || context.getOrder().getSymbol() == null) {
            context.setAttribute("layering_checked", Boolean.TRUE);
            context.setAttribute("layering_status", "SKIPPED_NO_DATA");
            return true;
        }

        String clientId = context.getOrder().getClientId();
        String symbol = context.getOrder().getSymbol();

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                // Can't check — fail open so we don't block trading
                context.setAttribute("layering_checked", Boolean.FALSE);
                return true;
            }

            // Count recent orders for same client+symbol in the last "day"
            // (simplified — we don't track intraday timestamps well enough)
            ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM TRADE_ORDERS WHERE CLIENT_ID = ? AND SYMBOL = ? AND STATUS <> 'CANCELLED'"
            );
            ps.setString(1, clientId);
            ps.setString(2, symbol);
            rs = ps.executeQuery();

            int recentOrderCount = 0;
            if (rs.next()) {
                recentOrderCount = rs.getInt(1);
            }

            context.setAttribute("layering_checked", Boolean.TRUE);
            context.setAttribute("layering_order_count", new Integer(recentOrderCount));

            if (recentOrderCount > LAYERING_THRESHOLD) {
                // Suspicious — flag but don't reject automatically
                // Compliance team reviews flagged orders
                context.setAttribute("layering_status", "FLAGGED");
                context.setAttribute("surveillance_flags",
                    (context.getAttribute("surveillance_flags") != null
                        ? context.getAttribute("surveillance_flags") + ",LAYERING" : "LAYERING"));
                context.addWarning("Potential layering pattern detected for "
                    + clientId + "/" + symbol + " (REG-2015-001): "
                    + recentOrderCount + " recent orders");
                // Don't reject — just flag. Let compliance review.
            } else {
                context.setAttribute("layering_status", "CLEAR");
            }

            return true;

        } catch (Exception e) {
            // Surveillance check failure must NOT block trading
            System.err.println("WARN: LayeringDetectionRule DB error: " + e.getMessage());
            context.setAttribute("layering_checked", Boolean.FALSE);
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
