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
 * Per-client kill switch for rapid risk response (REG-2011-002).
 * 
 * Checks the KILL_SWITCH column on the CLIENTS table. If set to 'Y' for
 * a given client, ALL orders from that client are rejected immediately.
 * 
 * This was added after the incident where a rogue algorithm at one client
 * submitted thousands of orders in seconds. We needed a way to cut off
 * individual clients without halting the entire market.
 * 
 * Priority 118 = runs right after MarketHalt(120), before KYC(115).
 * If the client is killed, we don't even check their KYC status.
 * 
 * @author compliance-bolt-on
 * @since 2011 Q4
 */
public class ClientKillSwitchRule implements Rule {

    // Per-client kill switch for rapid risk response (REG-2011-002)
    private static final String KILL_SWITCH_ACTIVE = "Y";

    public String getName() {
        return "ClientKillSwitch";
    }

    public int getPriority() {
        return 118; // runs right after MarketHalt(120), before KYC(115)
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
            context.reject("Order is null - cannot check kill switch");
            return false;
        }

        // Defensive null check on client
        Client client = context.getClient();
        if (client == null) {
            context.reject("Client is null - cannot check kill switch");
            return false;
        }

        String clientId = client.getClientId();
        if (clientId == null) {
            context.reject("Client ID is null - cannot check kill switch");
            return false;
        }

        // Look up kill switch status from the database
        String killSwitchValue = lookupKillSwitch(clientId);

        // Always record that we checked, regardless of outcome
        context.setAttribute("kill_switch_checked", Boolean.TRUE);

        // Defensive null check on DB result
        if (killSwitchValue == null) {
            // No kill switch value found — default to 'N' (allow trading)
            // This is the conservative approach: don't block trading because
            // the column might not exist yet in an older schema
            killSwitchValue = "N";
        }

        if (KILL_SWITCH_ACTIVE.equalsIgnoreCase(killSwitchValue.trim())) {
            // Kill switch is active — reject the order
            context.reject("Client trading suspended \u2014 kill switch active (REG-2011-002)");
            return false;
        }

        context.addMessage("Kill switch check passed for client " + clientId);
        return true;
    }

    /**
     * Look up the KILL_SWITCH column from the CLIENTS table.
     * Returns the value string, or null if not found / DB error.
     */
    private String lookupKillSwitch(String clientId) {
        // Defensive null check (yes, caller already checked, but
        // compliance insists on checking at every boundary)
        if (clientId == null) {
            return null;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            if (conn == null) {
                System.err.println("WARN: No DB connection for kill switch check");
                return null;
            }

            ps = conn.prepareStatement(
                "SELECT KILL_SWITCH FROM CLIENTS WHERE CLIENT_ID = ?"
            );
            ps.setString(1, clientId);

            rs = ps.executeQuery();
            if (rs != null && rs.next()) {
                String value = rs.getString("KILL_SWITCH");
                return value;
            }

            return null;
        } catch (Exception e) {
            // DB error - log but return null (caller will treat as 'N')
            System.err.println("WARN: Kill switch lookup failed for " + clientId + ": " + e.getMessage());
            return null;
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
