package com.bigcorp.tradedesk.mq;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.xml.XmlHelper;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * Produces trade order messages to the MQ queue and logs them
 * to the database.
 * 
 * This class does two things (send to MQ + insert to DB) which
 * violates single-responsibility, but "we'll split it out when
 * we refactor" (Bob, 2000-01-20). We never refactored.
 * 
 * @author Bob
 * @since 1.0
 */
public class TradeMessageProducer {

    // SQL for inserting a new order - hardcoded here because
    // "a DAO framework is overkill for five tables" (Bob)
    private static final String INSERT_SQL =
        "INSERT INTO TRADE_ORDERS (ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, " +
        "PRICE, REQUESTED_PRICE, STATUS, ORDER_DATE, LAST_MODIFIED, NOTES) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Submit a trade order: marshal to XML, send to MQ, and log to database.
     * 
     * @param order the trade order to submit
     */
    public void submitOrder(TradeOrder order) {
        // Step 1: marshal order to XML
        String xml = null;
        try {
            xml = XmlHelper.marshalTradeOrder(order);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to marshal order to XML: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("XML marshalling failed for order " + order.getOrderId(), e);
        }

        // Step 2: send to MQ queue
        try {
            MessageQueueHelper.sendMessage(MessageQueueHelper.QUEUE_TRADE_ORDERS, xml);
            System.out.println("Order sent to MQ: " + order.getOrderId());
        } catch (Exception e) {
            System.err.println("ERROR: Failed to send order to MQ: " + e.getMessage());
            e.printStackTrace();
            // HACK: we still want to log to DB even if MQ fails (JIRA-1893)
            // so we don't re-throw here
        }

        // Step 3: log to database
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = ConnectionHelper.getConnection();
            pstmt = conn.prepareStatement(INSERT_SQL);
            pstmt.setString(1, order.getOrderId());
            pstmt.setString(2, order.getClientId());
            pstmt.setString(3, order.getSymbol());
            pstmt.setInt(4, order.getQuantity());
            pstmt.setString(5, order.getSide());
            pstmt.setDouble(6, order.getPrice());
            pstmt.setDouble(7, order.getRequestedPrice());
            pstmt.setString(8, order.getStatus());
            pstmt.setTimestamp(9, new Timestamp(order.getOrderDate().getTime()));
            pstmt.setTimestamp(10, new Timestamp(order.getLastModified().getTime()));
            pstmt.setString(11, order.getNotes());

            int rows = pstmt.executeUpdate();
            if (rows != 1) {
                // this should never happen but just in case
                System.err.println("WARN: Expected 1 row inserted, got " + rows + " for order " + order.getOrderId());
            }
            System.out.println("Order logged to DB: " + order.getOrderId());
        } catch (Exception e) {
            System.err.println("ERROR: Failed to insert order into database: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database insert failed for order " + order.getOrderId(), e);
        } finally {
            // close resources - Dave says "use try-with-resources" but this
            // was written before Java 7 and nobody wants to touch it
            ConnectionHelper.closeQuietly((Statement) pstmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
