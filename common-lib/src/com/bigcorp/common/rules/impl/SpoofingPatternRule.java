package com.bigcorp.common.rules.impl;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Trade surveillance rule: detects potential spoofing patterns.
 * 
 * Spoofing is placing orders with the intent to cancel before execution,
 * to mislead other market participants about supply/demand.
 * 
 * Added after the 2015 market manipulation review (REG-2015-002).
 * Checks for clients with high cancellation rates.
 * 
 * @author compliance-bolt-on
 * @since 2015-Q2
 */
public class SpoofingPatternRule implements Rule {

    // If more than 60% of a client's orders are cancelled, flag them
    private static final double CANCEL_RATE_THRESHOLD = 0.60;

    public String getName() {
        return "SpoofingPattern";
    }

    public int getPriority() {
        return 124;
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        if (context == null || context.getOrder() == null) {
            return true;
        }

        // Defensive null check
        if (context.getOrder().getClientId() == null) {
            context.setAttribute("spoofing_checked", Boolean.TRUE);
            context.setAttribute("spoofing_status", "SKIPPED_NO_CLIENT");
            return true;
        }

        String clientId = context.getOrder().getClientId();

        Connection conn = null;
        PreparedStatement psTotal = null;
        PreparedStatement psCancelled = null;
        ResultSet rsTotal = null;
        ResultSet rsCancelled = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                context.setAttribute("spoofing_checked", Boolean.FALSE);
                return true;
            }

            // Count total orders for this client
            psTotal = conn.prepareStatement(
                "SELECT COUNT(*) FROM TRADE_ORDERS WHERE CLIENT_ID = ?"
            );
            psTotal.setString(1, clientId);
            rsTotal = psTotal.executeQuery();
            int totalOrders = 0;
            if (rsTotal.next()) {
                totalOrders = rsTotal.getInt(1);
            }

            // Count cancelled orders
            psCancelled = conn.prepareStatement(
                "SELECT COUNT(*) FROM TRADE_ORDERS WHERE CLIENT_ID = ? AND STATUS = 'CANCELLED'"
            );
            psCancelled.setString(1, clientId);
            rsCancelled = psCancelled.executeQuery();
            int cancelledOrders = 0;
            if (rsCancelled.next()) {
                cancelledOrders = rsCancelled.getInt(1);
            }

            context.setAttribute("spoofing_checked", Boolean.TRUE);

            if (totalOrders > 0) {
                double cancelRate = (double) cancelledOrders / (double) totalOrders;
                context.setAttribute("spoofing_cancel_rate", new Double(cancelRate));

                if (cancelRate > CANCEL_RATE_THRESHOLD) {
                    context.setAttribute("spoofing_status", "FLAGGED");
                    context.setAttribute("surveillance_flags",
                        (context.getAttribute("surveillance_flags") != null
                            ? context.getAttribute("surveillance_flags") + ",SPOOFING" : "SPOOFING"));
                    context.addWarning("Potential spoofing pattern for " + clientId
                        + " (REG-2015-002): cancel rate=" + (cancelRate * 100) + "%");
                } else {
                    context.setAttribute("spoofing_status", "CLEAR");
                }
            } else {
                context.setAttribute("spoofing_status", "NO_HISTORY");
            }

            return true;

        } catch (Exception e) {
            // Surveillance failure must NOT block trading
            System.err.println("WARN: SpoofingPatternRule DB error: " + e.getMessage());
            context.setAttribute("spoofing_checked", Boolean.FALSE);
            return true;
        } finally {
            ConnectionHelper.closeQuietly(rsTotal);
            ConnectionHelper.closeQuietly(rsCancelled);
            ConnectionHelper.closeQuietly(psTotal);
            ConnectionHelper.closeQuietly(psCancelled);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    public void execute(RuleContext context) {
        // no side effects beyond what evaluate() sets
    }
}
