package com.bigcorp.orderengine.dao;

import com.bigcorp.common.model.Client;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Data access object for trade orders and clients.
 * 
 * Uses raw JDBC because "the Entity Bean approach was too slow"
 * and "we'll switch to Hibernate when it's more mature."
 * 
 * WARNING: SQL uses string concatenation. We know. 
 * We'll fix this when we add prepared statements. - Bob, 2001-03-22
 * 
 * @author Bob
 * @author Dave (findOrdersByStatus - incomplete)
 * @since 1.0
 */
public class OrderDAO {

    // date format for SQL timestamps - matches Oracle TO_TIMESTAMP format
    private static final String SQL_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Look up a client by ID.
     * 
     * @param clientId the client identifier
     * @return Client object, or null if not found
     */
    public Client findClient(String clientId) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            // NOTE: yes, this is SQL injection vulnerable. 
            // We'll fix this when we add prepared statements. - Bob
            String sql = "SELECT CLIENT_ID, CLIENT_NAME, EMAIL, PHONE, TIER, " +
                    "MAX_ORDER_VALUE, ACTIVE FROM CLIENTS WHERE CLIENT_ID = '" + clientId + "'";

            rs = stmt.executeQuery(sql);

            if (rs.next()) {
                Client client = new Client();
                client.setClientId(rs.getString("CLIENT_ID"));
                client.setName(rs.getString("CLIENT_NAME"));
                client.setEmail(rs.getString("EMAIL"));
                client.setPhone(rs.getString("PHONE"));
                client.setTier(rs.getString("TIER"));
                client.setMaxOrderValue(rs.getDouble("MAX_ORDER_VALUE"));
                client.setActive(rs.getInt("ACTIVE") == 1);
                return client;
            }

            return null;

        } catch (Exception e) {
            System.err.println("ERROR: Failed to find client " + clientId + ": " + e.getMessage());
            e.printStackTrace();
            // swallow exception and return null - caller should handle
            return null;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Save a trade order (INSERT or UPDATE).
     * 
     * If the order already exists (by ORDER_ID), we update it.
     * Otherwise we insert a new row.
     * 
     * JIRA-2156: There's a race condition here if two threads try to
     * save the same order simultaneously. We've never seen it happen
     * in production though. (That we know of.)
     */
    public void saveOrder(TradeOrder order) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            // check if order already exists
            String checkSql = "SELECT COUNT(*) FROM TRADE_ORDERS WHERE ORDER_ID = '" + order.getOrderId() + "'";
            rs = stmt.executeQuery(checkSql);
            rs.next();
            int count = rs.getInt(1);
            rs.close();
            rs = null;

            SimpleDateFormat sdf = new SimpleDateFormat(SQL_DATE_FORMAT);
            String orderDateStr = sdf.format(order.getOrderDate() != null ? order.getOrderDate() : new Date());
            String lastModStr = sdf.format(order.getLastModified() != null ? order.getLastModified() : new Date());

            if (count > 0) {
                // UPDATE existing order
                // NOTE: we update everything, even fields that haven't changed.
                // This is not ideal but "it works" - Bob
                String updateSql = "UPDATE TRADE_ORDERS SET " +
                        "CLIENT_ID = '" + order.getClientId() + "', " +
                        "SYMBOL = '" + order.getSymbol() + "', " +
                        "QUANTITY = " + order.getQuantity() + ", " +
                        "SIDE = '" + order.getSide() + "', " +
                        "PRICE = " + order.getPrice() + ", " +
                        "REQUESTED_PRICE = " + order.getRequestedPrice() + ", " +
                        "STATUS = '" + order.getStatus() + "', " +
                        "ORDER_DATE = '" + orderDateStr + "', " +
                        "LAST_MODIFIED = '" + lastModStr + "', " +
                        "NOTES = '" + (order.getNotes() != null ? order.getNotes() : "") + "' " +
                        "WHERE ORDER_ID = '" + order.getOrderId() + "'";

                stmt.executeUpdate(updateSql);
                System.out.println("Updated order: " + order.getOrderId());

            } else {
                // INSERT new order
                String insertSql = "INSERT INTO TRADE_ORDERS " +
                        "(ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, PRICE, " +
                        "REQUESTED_PRICE, STATUS, ORDER_DATE, LAST_MODIFIED, NOTES) VALUES (" +
                        "'" + order.getOrderId() + "', " +
                        "'" + order.getClientId() + "', " +
                        "'" + order.getSymbol() + "', " +
                        order.getQuantity() + ", " +
                        "'" + order.getSide() + "', " +
                        order.getPrice() + ", " +
                        order.getRequestedPrice() + ", " +
                        "'" + order.getStatus() + "', " +
                        "'" + orderDateStr + "', " +
                        "'" + lastModStr + "', " +
                        "'" + (order.getNotes() != null ? order.getNotes() : "") + "'" +
                        ")";

                stmt.executeUpdate(insertSql);
                System.out.println("Inserted order: " + order.getOrderId());
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to save order " + order.getOrderId() + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save order: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Update just the status and price of an order.
     * Used after pricing and fill/reject decisions.
     */
    public void updateOrderStatus(String orderId, String status, double price) {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            SimpleDateFormat sdf = new SimpleDateFormat(SQL_DATE_FORMAT);
            String nowStr = sdf.format(new Date());

            // string concatenation again - see note in saveOrder()
            String sql = "UPDATE TRADE_ORDERS SET STATUS = '" + status + "', " +
                    "PRICE = " + price + ", " +
                    "LAST_MODIFIED = '" + nowStr + "' " +
                    "WHERE ORDER_ID = '" + orderId + "'";

            int rows = stmt.executeUpdate(sql);
            if (rows == 0) {
                System.err.println("WARN: updateOrderStatus affected 0 rows for " + orderId);
            } else {
                System.out.println("Updated order " + orderId + " -> " + status + " @ " + price);
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to update order status for " + orderId + ": " + e.getMessage());
            e.printStackTrace();
            // don't throw here - caller can check the order status later
        } finally {
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Find orders by status.
     * 
     * TODO: finish this - Dave 2001-05-10
     * Was going to be used for the order monitor screen but we
     * ended up doing that with a direct SQL query in the JSP instead.
     * Leaving this here in case we need it later.
     */
    public java.util.List findOrdersByStatus(String status) {
        // TODO: implement this
        // Connection conn = null;
        // Statement stmt = null;
        // ResultSet rs = null;
        // try {
        //     conn = ConnectionHelper.getConnection();
        //     stmt = conn.createStatement();
        //     String sql = "SELECT * FROM TRADE_ORDERS WHERE STATUS = '" + status + "'";
        //     rs = stmt.executeQuery(sql);
        //     List orders = new ArrayList();
        //     while (rs.next()) {
        //         TradeOrder order = new TradeOrder();
        //         // ... map all columns ...
        //         orders.add(order);
        //     }
        //     return orders;
        // } catch ...
        return null;
    }
}
