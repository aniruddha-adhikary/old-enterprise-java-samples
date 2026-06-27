package com.bigcorp.demo;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.common.model.Client;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Notification;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.rules.RuleEngine;
import com.bigcorp.common.rules.impl.MaxOrderValueRule;
import com.bigcorp.common.rules.impl.ClientTierRule;
import com.bigcorp.common.rules.impl.MarketHoursRule;
import com.bigcorp.common.rules.impl.SpecialClientsRule;
import com.bigcorp.common.xml.XmlHelper;
import com.bigcorp.tradedesk.mq.TradeMessageProducer;
import com.bigcorp.orderengine.consumer.OrderMessageListener;
import com.bigcorp.pricing.service.PricingServiceImpl;
import com.bigcorp.notifications.consumer.NotificationListener;
import com.bigcorp.settlement.batch.BatchProcessor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;

/**
 * End-to-end demo runner.
 * 
 * Starts up all components with embedded infrastructure (HSQLDB, ActiveMQ)
 * and runs a sample trade through the full pipeline:
 * 
 *   1. Submit trade order via trade-desk
 *   2. Order-engine picks it up, runs rules, calls pricing, persists
 *   3. Notification-gateway sends email/SMS confirmation
 *   4. Settlement-gateway generates batch file
 * 
 * @author Demo team
 * @since 2.0
 */
public class DemoRunner {

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  BigCorp Trade Order Management System");
        System.out.println("  End-to-End Demo");
        System.out.println("==============================================");
        System.out.println();

        try {
            // Step 1: Initialize infrastructure
            System.out.println("[1/6] Initializing database...");
            ConnectionHelper.init();
            DatabaseBootstrap.bootstrap();
            System.out.println("      Database ready.");

            System.out.println("[2/6] Initializing message queues...");
            MessageQueueHelper.init();
            System.out.println("      MQ ready.");

            // Step 2: Set up the rule engine
            System.out.println("[3/6] Loading business rules...");
            RuleEngine engine = RuleEngine.getInstance();
            engine.addRule(new MaxOrderValueRule());
            engine.addRule(new ClientTierRule());
            engine.addRule(new MarketHoursRule());
            engine.addRule(new SpecialClientsRule());
            System.out.println("      " + engine.getRuleCount() + " rules loaded.");

            // Step 3: Start the order engine listener in a background thread
            System.out.println("[4/6] Starting order engine...");
            OrderMessageListener orderListener = new OrderMessageListener();
            Thread engineThread = new Thread(new Runnable() {
                public void run() {
                    orderListener.startListening();
                }
            });
            engineThread.setDaemon(true);
            engineThread.start();
            System.out.println("      Order engine listening.");

            // Step 4: Start notification listener in a background thread
            System.out.println("[5/6] Starting notification gateway...");
            NotificationListener notifListener = new NotificationListener();
            Thread notifThread = new Thread(new Runnable() {
                public void run() {
                    notifListener.startListening();
                }
            });
            notifThread.setDaemon(true);
            notifThread.start();
            System.out.println("      Notification gateway listening.");

            // Step 5: Submit a sample trade order
            System.out.println("[6/6] Submitting sample trade order...");
            System.out.println();

            TradeOrder order = new TradeOrder();
            order.setOrderId("ORD-DEMO-001");
            order.setClientId("C001");
            order.setSymbol("MSFT");
            order.setQuantity(500);
            order.setSide(TradeOrder.SIDE_BUY);
            order.setRequestedPrice(25.75);

            System.out.println("  Order: " + order);

            // Submit via trade-desk message producer
            TradeMessageProducer producer = new TradeMessageProducer();
            producer.submitOrder(order);

            System.out.println("  Order submitted to MQ queue.");
            System.out.println();

            // Wait for processing
            System.out.println("  Waiting for order processing...");
            Thread.sleep(5000);

            // Step 6: Check results in database
            System.out.println();
            System.out.println("==============================================");
            System.out.println("  Results");
            System.out.println("==============================================");
            printOrderStatus();
            printNotifications();

            // Step 7: Run settlement batch
            System.out.println();
            System.out.println("  Running settlement batch...");
            BatchProcessor batchProcessor = new BatchProcessor();
            batchProcessor.processBatch();
            System.out.println("  Settlement batch complete.");

            printSettlementRecords();

            System.out.println();
            System.out.println("==============================================");
            System.out.println("  Demo complete!");
            System.out.println("==============================================");

        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Force exit (ActiveMQ threads keep the JVM alive)
        System.exit(0);
    }

    private static void printOrderStatus() {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, PRICE, STATUS FROM TRADE_ORDERS");
            System.out.println();
            System.out.println("  TRADE ORDERS:");
            System.out.println("  " + padRight("ORDER_ID", 20) + padRight("CLIENT", 10) + padRight("SYMBOL", 8) + padRight("QTY", 8) + padRight("SIDE", 6) + padRight("PRICE", 12) + "STATUS");
            System.out.println("  " + repeat("-", 80));
            while (rs.next()) {
                System.out.println("  " +
                    padRight(rs.getString("ORDER_ID"), 20) +
                    padRight(rs.getString("CLIENT_ID"), 10) +
                    padRight(rs.getString("SYMBOL"), 8) +
                    padRight(String.valueOf(rs.getInt("QUANTITY")), 8) +
                    padRight(rs.getString("SIDE"), 6) +
                    padRight(String.valueOf(rs.getDouble("PRICE")), 12) +
                    rs.getString("STATUS")
                );
            }
        } catch (Exception e) {
            System.err.println("  Error reading orders: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    private static void printNotifications() {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT NOTIFICATION_ID, NOTIFICATION_TYPE, CHANNEL, RECIPIENT, STATUS FROM NOTIFICATIONS");
            System.out.println();
            System.out.println("  NOTIFICATIONS:");
            System.out.println("  " + padRight("ID", 20) + padRight("TYPE", 18) + padRight("CHANNEL", 10) + padRight("RECIPIENT", 25) + "STATUS");
            System.out.println("  " + repeat("-", 80));
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("  " +
                    padRight(rs.getString("NOTIFICATION_ID"), 20) +
                    padRight(rs.getString("NOTIFICATION_TYPE"), 18) +
                    padRight(rs.getString("CHANNEL"), 10) +
                    padRight(rs.getString("RECIPIENT"), 25) +
                    rs.getString("STATUS")
                );
            }
            if (!found) {
                System.out.println("  (no notifications yet)");
            }
        } catch (Exception e) {
            System.err.println("  Error reading notifications: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    private static void printSettlementRecords() {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT RECORD_ID, ORDER_ID, AMOUNT, STATUS, BATCH_ID FROM SETTLEMENT_RECORDS");
            System.out.println();
            System.out.println("  SETTLEMENT RECORDS:");
            System.out.println("  " + padRight("RECORD_ID", 20) + padRight("ORDER_ID", 20) + padRight("AMOUNT", 15) + padRight("STATUS", 12) + "BATCH_ID");
            System.out.println("  " + repeat("-", 80));
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("  " +
                    padRight(rs.getString("RECORD_ID"), 20) +
                    padRight(rs.getString("ORDER_ID"), 20) +
                    padRight(String.valueOf(rs.getDouble("AMOUNT")), 15) +
                    padRight(rs.getString("STATUS"), 12) +
                    nvl(rs.getString("BATCH_ID"))
                );
            }
            if (!found) {
                System.out.println("  (no settlement records yet)");
            }
        } catch (Exception e) {
            System.err.println("  Error reading settlements: " + e.getMessage());
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String repeat(String s, int count) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
