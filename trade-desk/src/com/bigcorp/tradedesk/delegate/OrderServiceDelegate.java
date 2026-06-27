package com.bigcorp.tradedesk.delegate;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.tradedesk.mq.TradeMessageProducer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Business Delegate for order operations.
 *
 * Shields the web tier from the complexity of looking up services,
 * handling MQ connections, and dealing with remote exceptions.
 * In the J2EE version this would hide the EJB lookup/invoke cycle.
 *
 * "The delegate pattern reduces coupling between tiers"
 * - from the architecture document, section 4.2.1
 *
 * In practice this class is just a wrapper around TradeMessageProducer
 * and raw JDBC queries, but at least the servlet code is cleaner now.
 * That's what Bob says anyway.
 *
 * @author Bob
 * @since 2.0
 */
public class OrderServiceDelegate {

    private TradeMessageProducer producer;

    public OrderServiceDelegate() {
        this.producer = new TradeMessageProducer();
    }

    /**
     * Submit a new order. Creates the TradeOrder, sends to MQ, inserts into DB.
     * Returns the generated order ID.
     *
     * @param clientId  the client identifier
     * @param symbol    the stock symbol
     * @param quantity  number of shares
     * @param side      BUY or SELL
     * @param price     the requested price
     * @return the generated order ID
     */
    public String submitOrder(String clientId, String symbol, int quantity, String side, double price) {
        TradeOrder order = new TradeOrder();
        String orderId = "ORD-" + System.currentTimeMillis();
        order.setOrderId(orderId);
        order.setClientId(clientId);
        order.setSymbol(symbol);
        order.setQuantity(quantity);
        order.setSide(side);
        order.setRequestedPrice(price);
        order.setPrice(price); // initial price = requested, pricing engine will update
        order.setNotes("Submitted via Front Controller (v2.0)");

        // delegate to the producer (which does MQ + DB)
        producer.submitOrder(order);

        System.out.println("[OrderServiceDelegate] Order submitted: " + orderId);
        return orderId;
    }

    /**
     * Look up a single order by ID.
     *
     * @param orderId the order identifier
     * @return the TradeOrder, or null if not found
     */
    public TradeOrder findOrder(String orderId) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            pstmt = conn.prepareStatement(
                "SELECT ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, PRICE, " +
                "REQUESTED_PRICE, STATUS, ORDER_DATE, LAST_MODIFIED, NOTES " +
                "FROM TRADE_ORDERS WHERE ORDER_ID = ?");
            pstmt.setString(1, orderId);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToOrder(rs);
            }
            return null;

        } catch (Exception e) {
            System.err.println("ERROR: OrderServiceDelegate.findOrder failed for " + orderId + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to find order " + orderId, e);
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly((Statement) pstmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Get recent orders, up to maxResults.
     * Returns a List of TradeOrder objects (no generics - Java 1.4 style).
     *
     * @param maxResults max number of orders to return
     * @return List of TradeOrder objects
     */
    public List getRecentOrders(int maxResults) {
        List orders = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "SELECT ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, PRICE, " +
                "REQUESTED_PRICE, STATUS, ORDER_DATE, LAST_MODIFIED, NOTES " +
                "FROM TRADE_ORDERS ORDER BY ORDER_DATE DESC " +
                "FETCH FIRST " + maxResults + " ROWS ONLY");

            while (rs.next()) {
                orders.add(mapResultSetToOrder(rs));
            }

        } catch (Exception e) {
            System.err.println("ERROR: OrderServiceDelegate.getRecentOrders failed: " + e.getMessage());
            e.printStackTrace();
            // return what we have so far (probably empty)
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
        return orders;
    }

    /**
     * Get order counts grouped by status.
     * Returns a Hashtable because it's thread-safe (Bob's favorite data structure).
     *
     * @return Hashtable mapping status String to Integer count
     */
    public Hashtable getOrderCountsByStatus() {
        Hashtable counts = new Hashtable();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "SELECT STATUS, COUNT(*) AS CNT FROM TRADE_ORDERS GROUP BY STATUS ORDER BY STATUS");

            while (rs.next()) {
                String status = rs.getString("STATUS");
                int cnt = rs.getInt("CNT");
                counts.put(status, new Integer(cnt));
            }

        } catch (Exception e) {
            System.err.println("ERROR: OrderServiceDelegate.getOrderCountsByStatus failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
        return counts;
    }

    /**
     * Map a ResultSet row to a TradeOrder object.
     * This should really be in a DAO but "we'll add that layer later."
     */
    private TradeOrder mapResultSetToOrder(ResultSet rs) throws Exception {
        TradeOrder order = new TradeOrder();
        order.setOrderId(rs.getString("ORDER_ID"));
        order.setClientId(rs.getString("CLIENT_ID"));
        order.setSymbol(rs.getString("SYMBOL"));
        order.setQuantity(rs.getInt("QUANTITY"));
        order.setSide(rs.getString("SIDE"));
        order.setPrice(rs.getDouble("PRICE"));
        order.setRequestedPrice(rs.getDouble("REQUESTED_PRICE"));
        // use the private field setter to avoid lastModified being reset
        order.setStatus(rs.getString("STATUS"));

        Timestamp orderDate = rs.getTimestamp("ORDER_DATE");
        if (orderDate != null) {
            order.setOrderDate(new java.util.Date(orderDate.getTime()));
        }
        Timestamp lastMod = rs.getTimestamp("LAST_MODIFIED");
        if (lastMod != null) {
            order.setLastModified(new java.util.Date(lastMod.getTime()));
        }

        order.setNotes(rs.getString("NOTES"));
        return order;
    }
}
