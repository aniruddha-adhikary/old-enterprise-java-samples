package com.bigcorp.tradedesk.api;

import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client portal API for exposing order status and balances.
 * 
 * This is a "read API" that the client portal team asked for. They
 * want to show clients their order status and billing info without
 * going through the full trade-desk UI.
 * 
 * Auth is handled by... checking if the client ID is valid. That's it.
 * TODO: add proper authentication (JIRA-9100)
 * TODO: add rate limiting (JIRA-9101)
 * TODO: fix error handling — right now we just return empty results (JIRA-9102)
 * 
 * @author feature-rusher
 * @since 2019-Q1
 */
public class ClientPortalAPI {

    // Hardcoded API keys for "authentication"
    // TODO: replace with proper auth system (JIRA-9100)
    private static final String API_KEY_INTERNAL = "bigcorp-internal-2019";
    private static final String API_KEY_PORTAL = "client-portal-key-2019";

    // Hardcoded max results to prevent huge queries
    private static final int MAX_RESULTS = 100;

    /**
     * "Authenticate" a request. Just checks if the API key matches.
     * TODO: real auth (JIRA-9100)
     */
    public static boolean authenticate(String apiKey) {
        // "Good enough for now" — PM
        if (apiKey == null) return false;
        return API_KEY_INTERNAL.equals(apiKey) || API_KEY_PORTAL.equals(apiKey);
    }

    /**
     * Get orders for a client.
     * Copy-pasted from OrderDAO.findOrdersByStatus() and modified.
     * 
     * @return list of maps with order fields, or empty list on error
     */
    public List getOrdersForClient(String clientId) {
        List results = new ArrayList();
        if (clientId == null || clientId.trim().length() == 0) {
            return results;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            // Copy-pasted SQL — same as what the trade-desk uses
            ps = conn.prepareStatement(
                "SELECT ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, PRICE, " +
                "REQUESTED_PRICE, STATUS, ORDER_DATE, NOTES " +
                "FROM TRADE_ORDERS WHERE CLIENT_ID = ? " +
                "ORDER BY ORDER_DATE DESC"
            );
            ps.setString(1, clientId);
            rs = ps.executeQuery();

            int count = 0;
            while (rs.next() && count < MAX_RESULTS) {
                Map order = new HashMap();
                order.put("orderId", rs.getString("ORDER_ID"));
                order.put("clientId", rs.getString("CLIENT_ID"));
                order.put("symbol", rs.getString("SYMBOL"));
                order.put("quantity", String.valueOf(rs.getInt("QUANTITY")));
                order.put("side", rs.getString("SIDE"));
                order.put("price", String.valueOf(rs.getDouble("PRICE")));
                order.put("requestedPrice", String.valueOf(rs.getDouble("REQUESTED_PRICE")));
                order.put("status", rs.getString("STATUS"));
                order.put("orderDate", rs.getString("ORDER_DATE"));
                order.put("notes", rs.getString("NOTES"));
                results.add(order);
                count++;
            }
        } catch (Exception e) {
            // Swallow exception — just return empty list
            // TODO: log this properly (JIRA-9103)
            System.err.println("ERROR in ClientPortalAPI.getOrdersForClient: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
        return results;
    }

    /**
     * Get billing summary for a client.
     * Copy-pasted from ReportingDAO.getBillingSummary() and modified.
     */
    public Map getClientBalance(String clientId) {
        Map balance = new HashMap();
        if (clientId == null) return balance;

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            ps = conn.prepareStatement(
                "SELECT SUM(GROSS_AMOUNT) AS TOTAL_GROSS, " +
                "SUM(COMMISSION_AMOUNT) AS TOTAL_COMMISSION, " +
                "SUM(NET_AMOUNT) AS TOTAL_NET, COUNT(*) AS ENTRY_COUNT " +
                "FROM BILLING_LEDGER WHERE CLIENT_ID = ? AND STATUS = 'CHARGED'"
            );
            ps.setString(1, clientId);
            rs = ps.executeQuery();

            if (rs.next()) {
                balance.put("clientId", clientId);
                balance.put("totalGross", String.valueOf(rs.getDouble("TOTAL_GROSS")));
                balance.put("totalCommission", String.valueOf(rs.getDouble("TOTAL_COMMISSION")));
                balance.put("totalNet", String.valueOf(rs.getDouble("TOTAL_NET")));
                balance.put("entryCount", String.valueOf(rs.getInt("ENTRY_COUNT")));
            }
        } catch (Exception e) {
            // Just return empty map
            System.err.println("ERROR in ClientPortalAPI.getClientBalance: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
        return balance;
    }

    /**
     * Get order status as a simple JSON-ish string.
     * We don't have a JSON library so we just build the string manually.
     * TODO: add a proper JSON library (JIRA-9104)
     */
    public String getOrderStatusJson(String orderId) {
        if (orderId == null) return "{}";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            ps = conn.prepareStatement(
                "SELECT ORDER_ID, STATUS, PRICE, QUANTITY, SYMBOL, SIDE FROM TRADE_ORDERS WHERE ORDER_ID = ?"
            );
            ps.setString(1, orderId);
            rs = ps.executeQuery();

            if (rs.next()) {
                // Hand-built "JSON" — forgive me
                StringBuffer json = new StringBuffer();
                json.append("{");
                json.append("\"orderId\":\"" + rs.getString("ORDER_ID") + "\",");
                json.append("\"status\":\"" + rs.getString("STATUS") + "\",");
                json.append("\"price\":" + rs.getDouble("PRICE") + ",");
                json.append("\"quantity\":" + rs.getInt("QUANTITY") + ",");
                json.append("\"symbol\":\"" + rs.getString("SYMBOL") + "\",");
                json.append("\"side\":\"" + rs.getString("SIDE") + "\"");
                json.append("}");
                return json.toString();
            }
        } catch (Exception e) {
            System.err.println("ERROR in ClientPortalAPI.getOrderStatusJson: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(ps);
            ConnectionHelper.closeQuietly(conn);
        }
        return "{}";
    }
}
