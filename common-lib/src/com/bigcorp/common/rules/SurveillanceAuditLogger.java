package com.bigcorp.common.rules;

import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

/**
 * Audit logger for surveillance rule decisions.
 * 
 * Mirrors RuleAuditLogger but writes to SURVEILLANCE_AUDIT_LOG
 * for surveillance-specific tracking (REG-2015-004).
 * 
 * Yes, this duplicates a lot of code from RuleAuditLogger. We were
 * told to keep surveillance audit trails separate from regular rule
 * audit trails "for regulatory segmentation." Compliance insists.
 * 
 * @author compliance-bolt-on
 * @since 2015-Q2
 */
public class SurveillanceAuditLogger {

    private static final String INSERT_SQL =
        "INSERT INTO SURVEILLANCE_AUDIT_LOG (RULE_NAME, ORDER_ID, CLIENT_ID, SYMBOL, RESULT, " +
        "SURVEILLANCE_FLAGS, EVALUATION_TIME, DETAILS) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Log a surveillance rule decision.
     * 
     * This method NEVER throws an exception. Surveillance logging
     * failures must not prevent order processing.
     */
    public static void logSurveillanceDecision(String ruleName, String orderId, String clientId,
                                                String symbol, boolean passed,
                                                String surveillanceFlags, String details) {
        // Defensive null checks on all parameters
        if (ruleName == null) ruleName = "UNKNOWN";
        if (orderId == null) orderId = "UNKNOWN";
        if (clientId == null) clientId = "UNKNOWN";
        if (symbol == null) symbol = "UNKNOWN";
        if (surveillanceFlags == null) surveillanceFlags = "";
        if (details == null) details = "";

        // Truncate if too long
        if (details.length() > 500) {
            details = details.substring(0, 497) + "...";
        }
        if (surveillanceFlags.length() > 200) {
            surveillanceFlags = surveillanceFlags.substring(0, 197) + "...";
        }

        String result = passed ? "CLEAR" : "FLAGGED";

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                System.err.println("WARN: No DB connection for surveillance audit logging");
                return;
            }

            ps = conn.prepareStatement(INSERT_SQL);
            ps.setString(1, ruleName);
            ps.setString(2, orderId);
            ps.setString(3, clientId);
            ps.setString(4, symbol);
            ps.setString(5, result);
            ps.setString(6, surveillanceFlags);
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            ps.setString(8, details);

            ps.executeUpdate();

        } catch (Exception e) {
            // Surveillance audit logging failure must NEVER prevent order processing
            System.err.println("WARN: Surveillance audit log write failed for " + ruleName
                + " / " + orderId + ": " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
