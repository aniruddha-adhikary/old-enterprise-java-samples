package com.bigcorp.audit.dao;

import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Data access for BILLING_LEDGER table.
 * 
 * Records commission charges against clients for filled orders.
 * Uses HSQLDB IDENTITY column for ENTRY_ID (auto-generated).
 * 
 * @author Billing team
 * @since 2.1
 */
public class BillingDAO {

    /**
     * Insert a billing ledger entry charging commission to a client.
     */
    public void insertBillingEntry(String orderId, String clientId,
                                    double grossAmount, double commissionAmount,
                                    double netAmount) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = ConnectionHelper.getConnection();

            String sql = "INSERT INTO BILLING_LEDGER (ORDER_ID, CLIENT_ID, GROSS_AMOUNT, "
                    + "COMMISSION_AMOUNT, NET_AMOUNT, CHARGED_DATE, STATUS) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

            ps = conn.prepareStatement(sql);
            ps.setString(1, orderId);
            ps.setString(2, clientId);
            ps.setDouble(3, grossAmount);
            ps.setDouble(4, commissionAmount);
            ps.setDouble(5, netAmount);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.setString(7, "CHARGED");

            ps.executeUpdate();
            System.out.println("Billing entry saved: order=" + orderId 
                    + " client=" + clientId + " commission=" + commissionAmount);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to save billing entry: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
