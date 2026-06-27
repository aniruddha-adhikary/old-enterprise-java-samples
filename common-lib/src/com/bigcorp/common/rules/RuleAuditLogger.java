package com.bigcorp.common.rules;

import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

/**
 * Audit logger for rule engine decisions.
 * 
 * Logs every rule evaluation result to the RULE_AUDIT_LOG table for
 * regulatory compliance (REG-2011-003). Every single rule decision —
 * pass, fail, or skip — is recorded with timestamp and details.
 * 
 * CRITICAL: Audit logging failures must NEVER prevent order processing.
 * All methods swallow exceptions. We'd rather lose an audit record than
 * reject a valid order because the audit table is full or the connection
 * pool is exhausted.
 * 
 * @author compliance-bolt-on
 * @since 2011 Q4
 */
public class RuleAuditLogger {

    // Audit trail added for regulatory compliance (REG-2011-003) — must never block trading
    private static final String INSERT_SQL =
        "INSERT INTO RULE_AUDIT_LOG (RULE_NAME, ORDER_ID, CLIENT_ID, RESULT, EVALUATION_TIME, DETAILS) " +
        "VALUES (?, ?, ?, ?, ?, ?)";

    /**
     * Log a rule decision to the RULE_AUDIT_LOG table.
     * 
     * This method NEVER throws an exception. If logging fails, the
     * failure is printed to stderr and silently swallowed. Order
     * processing must not be affected by audit logging issues.
     * 
     * @param ruleName  the name of the rule that was evaluated (nullable — will log "UNKNOWN")
     * @param orderId   the order ID being evaluated (nullable — will log "UNKNOWN")
     * @param clientId  the client ID (nullable — will log "UNKNOWN")
     * @param passed    whether the rule passed
     * @param details   additional details about the evaluation (nullable)
     */
    public static void logRuleDecision(String ruleName, String orderId, String clientId,
                                        boolean passed, String details) {
        // Defensive null checks on all parameters
        if (ruleName == null) {
            ruleName = "UNKNOWN";
        }
        if (orderId == null) {
            orderId = "UNKNOWN";
        }
        if (clientId == null) {
            clientId = "UNKNOWN";
        }
        if (details == null) {
            details = "";
        }

        // Truncate details if too long (VARCHAR(500) in DDL)
        if (details.length() > 500) {
            details = details.substring(0, 497) + "...";
        }

        String result = passed ? "PASS" : "FAIL";

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                System.err.println("WARN: No DB connection for rule audit logging");
                return;
            }

            ps = conn.prepareStatement(INSERT_SQL);
            ps.setString(1, ruleName);
            ps.setString(2, orderId);
            ps.setString(3, clientId);
            ps.setString(4, result);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.setString(6, details);

            ps.executeUpdate();

        } catch (Exception e) {
            // Audit logging failure must NEVER prevent order processing
            // Log to stderr and move on
            System.err.println("WARN: Rule audit log write failed for " + ruleName 
                + " / " + orderId + ": " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Log a skipped rule (inactive) to the audit trail.
     * We log skips too — auditors want to know which rules were NOT evaluated.
     */
    public static void logRuleSkipped(String ruleName, String orderId, String clientId) {
        // Defensive null checks
        if (ruleName == null) {
            ruleName = "UNKNOWN";
        }
        if (orderId == null) {
            orderId = "UNKNOWN";
        }
        if (clientId == null) {
            clientId = "UNKNOWN";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                System.err.println("WARN: No DB connection for rule audit logging (skip)");
                return;
            }

            ps = conn.prepareStatement(INSERT_SQL);
            ps.setString(1, ruleName);
            ps.setString(2, orderId);
            ps.setString(3, clientId);
            ps.setString(4, "SKIP");
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.setString(6, "Rule inactive - skipped");

            ps.executeUpdate();

        } catch (Exception e) {
            // Audit logging failure must NEVER prevent order processing
            System.err.println("WARN: Rule audit log write failed (skip) for " + ruleName 
                + " / " + orderId + ": " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
