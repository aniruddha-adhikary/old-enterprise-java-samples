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
 * Verifies client KYC (Know Your Customer) status before allowing trades.
 * 
 * KYC verification required post-2005 regulatory review.
 * 
 * Clients must have KYC_STATUS = 'APPROVED' in the CLIENTS table to trade.
 * If status is 'PENDING', 'EXPIRED', or 'REJECTED', the order is rejected.
 * 
 * Priority 115 = runs before everything else. KYC must be the absolute
 * first check because there's no point evaluating volume limits or wash
 * trades if we don't even know who this client is.
 * 
 * @author compliance-bolt-on
 * @since 2005 Q2
 */
public class KYCStatusRule implements Rule {

    // KYC verification required post-2005 regulatory review
    private static final String KYC_APPROVED = "APPROVED";
    private static final String KYC_PENDING = "PENDING";
    private static final String KYC_EXPIRED = "EXPIRED";
    private static final String KYC_REJECTED = "REJECTED";

    public String getName() {
        return "KYCStatus";
    }

    public int getPriority() {
        return 115;
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        // Defensive null checks everywhere
        if (context == null) {
            return false;
        }

        TradeOrder order = context.getOrder();
        if (order == null) {
            context.reject("Order is null - cannot verify KYC status");
            return false;
        }

        Client client = context.getClient();
        if (client == null) {
            context.reject("Client is null - cannot verify KYC status");
            return false;
        }

        String clientId = client.getClientId();
        if (clientId == null) {
            context.reject("Client ID is null - cannot verify KYC status");
            return false;
        }

        // Look up KYC status from the database
        String kycStatus = lookupKycStatus(clientId);

        // Store the status in context regardless of outcome -
        // downstream systems need to know what we found
        if (kycStatus != null) {
            context.setAttribute("kyc_status", kycStatus);
        } else {
            // No KYC record found - treat as PENDING (conservative approach)
            kycStatus = KYC_PENDING;
            context.setAttribute("kyc_status", kycStatus);
        }

        // Only APPROVED clients can trade
        if (!KYC_APPROVED.equals(kycStatus)) {
            context.reject("Client KYC status is '" + kycStatus 
                + "' - must be APPROVED to trade");
            return false;
        }

        context.addMessage("KYC status verified: " + kycStatus + " for client " + clientId);
        return true;
    }

    /**
     * Look up the KYC_STATUS column from the CLIENTS table.
     * Returns the status string, or null if not found / DB error.
     */
    private String lookupKycStatus(String clientId) {
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
                System.err.println("WARN: No DB connection for KYC status check");
                return null;
            }

            ps = conn.prepareStatement(
                "SELECT KYC_STATUS FROM CLIENTS WHERE CLIENT_ID = ?"
            );
            ps.setString(1, clientId);

            rs = ps.executeQuery();
            if (rs != null && rs.next()) {
                String status = rs.getString("KYC_STATUS");
                return status;
            }

            return null;
        } catch (Exception e) {
            // DB error - log but return null (caller will treat as PENDING)
            System.err.println("WARN: KYC status lookup failed for " + clientId + ": " + e.getMessage());
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
