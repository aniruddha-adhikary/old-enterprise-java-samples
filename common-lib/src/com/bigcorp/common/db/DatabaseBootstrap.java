package com.bigcorp.common.db;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Creates database tables if they don't exist.
 * This runs on application startup for HSQLDB.
 * 
 * In production, the DBA runs the schema.sql script manually against Oracle.
 * This class is only for development / demo purposes.
 * 
 * NOTE: The SQL here uses Oracle-compatible syntax where possible,
 * but some things (like SYSDATE, sequences) are adjusted for HSQLDB.
 * 
 * @author Bob
 * @since 1.0
 */
public class DatabaseBootstrap {

    /**
     * Create all tables. Safe to call multiple times.
     */
    public static void bootstrap() {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            // CLIENTS table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS CLIENTS (" +
                "  CLIENT_ID VARCHAR(20) PRIMARY KEY," +
                "  CLIENT_NAME VARCHAR(100) NOT NULL," +
                "  EMAIL VARCHAR(100)," +
                "  PHONE VARCHAR(20)," +
                "  TIER VARCHAR(10) DEFAULT 'BRONZE'," +
                "  MAX_ORDER_VALUE DECIMAL(15,2) DEFAULT 100000.00," +
                "  ACTIVE INTEGER DEFAULT 1," +
                "  CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // TRADE_ORDERS table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS TRADE_ORDERS (" +
                "  ORDER_ID VARCHAR(30) PRIMARY KEY," +
                "  CLIENT_ID VARCHAR(20) NOT NULL," +
                "  SYMBOL VARCHAR(10) NOT NULL," +
                "  QUANTITY INTEGER NOT NULL," +
                "  SIDE VARCHAR(4) NOT NULL," +
                "  PRICE DECIMAL(15,4) DEFAULT 0," +
                "  REQUESTED_PRICE DECIMAL(15,4) DEFAULT 0," +
                "  STATUS VARCHAR(20) DEFAULT 'NEW'," +
                "  ORDER_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  LAST_MODIFIED TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  NOTES VARCHAR(500)," +
                "  FOREIGN KEY (CLIENT_ID) REFERENCES CLIENTS(CLIENT_ID)" +
                ")"
            );

            // NOTIFICATIONS table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS NOTIFICATIONS (" +
                "  NOTIFICATION_ID VARCHAR(30) PRIMARY KEY," +
                "  NOTIFICATION_TYPE VARCHAR(20) NOT NULL," +
                "  RECIPIENT VARCHAR(100) NOT NULL," +
                "  SUBJECT VARCHAR(200)," +
                "  BODY VARCHAR(2000)," +
                "  CHANNEL VARCHAR(10) NOT NULL," +
                "  STATUS VARCHAR(10) DEFAULT 'PENDING'," +
                "  ORDER_ID VARCHAR(30)," +
                "  CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  SENT_DATE TIMESTAMP," +
                "  RETRY_COUNT INTEGER DEFAULT 0" +
                ")"
            );

            // SETTLEMENT_RECORDS table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS SETTLEMENT_RECORDS (" +
                "  RECORD_ID VARCHAR(30) PRIMARY KEY," +
                "  ORDER_ID VARCHAR(30) NOT NULL," +
                "  CLIENT_ID VARCHAR(20) NOT NULL," +
                "  SYMBOL VARCHAR(10) NOT NULL," +
                "  QUANTITY INTEGER NOT NULL," +
                "  SIDE VARCHAR(4) NOT NULL," +
                "  AMOUNT DECIMAL(15,4) NOT NULL," +
                "  COMMISSION DECIMAL(10,4) DEFAULT 0," +
                "  TRADE_DATE TIMESTAMP NOT NULL," +
                "  SETTLEMENT_DATE TIMESTAMP," +
                "  STATUS VARCHAR(15) DEFAULT 'PENDING'," +
                "  BATCH_ID VARCHAR(30)," +
                "  EXTERNAL_REF VARCHAR(50)" +
                ")"
            );

            // AUDIT_LOG table - used by multiple systems
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS AUDIT_LOG (" +
                "  LOG_ID INTEGER IDENTITY PRIMARY KEY," +
                "  EVENT_TYPE VARCHAR(30) NOT NULL," +
                "  SOURCE_SYSTEM VARCHAR(30)," +
                "  ENTITY_TYPE VARCHAR(20)," +
                "  ENTITY_ID VARCHAR(30)," +
                "  DESCRIPTION VARCHAR(500)," +
                "  LOG_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  USER_ID VARCHAR(30)" +
                ")"
            );

            // PRICING_CACHE - used by pricing service
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS PRICING_CACHE (" +
                "  SYMBOL VARCHAR(10) PRIMARY KEY," +
                "  BID_PRICE DECIMAL(15,4)," +
                "  ASK_PRICE DECIMAL(15,4)," +
                "  LAST_PRICE DECIMAL(15,4)," +
                "  CURRENCY VARCHAR(3) DEFAULT 'USD'," +
                "  LAST_UPDATED TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // Insert sample data
            insertSampleData(stmt);

            System.out.println("Database bootstrap completed successfully.");

        } catch (Exception e) {
            System.err.println("ERROR during database bootstrap: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    private static void insertSampleData(Statement stmt) {
        try {
            // Check if data already exists
            java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM CLIENTS");
            rs.next();
            if (rs.getInt(1) > 0) {
                rs.close();
                return; // data already loaded
            }
            rs.close();

            // Sample clients
            stmt.executeUpdate("INSERT INTO CLIENTS (CLIENT_ID, CLIENT_NAME, EMAIL, PHONE, TIER, MAX_ORDER_VALUE, ACTIVE) VALUES ('C001', 'Acme Trading LLC', 'trading@acme.com', '555-0100', 'GOLD', 500000.00, 1)");
            stmt.executeUpdate("INSERT INTO CLIENTS (CLIENT_ID, CLIENT_NAME, EMAIL, PHONE, TIER, MAX_ORDER_VALUE, ACTIVE) VALUES ('C002', 'Henderson Capital', 'orders@henderson.com', '555-0200', 'PLATINUM', 5000000.00, 1)");
            stmt.executeUpdate("INSERT INTO CLIENTS (CLIENT_ID, CLIENT_NAME, EMAIL, PHONE, TIER, MAX_ORDER_VALUE, ACTIVE) VALUES ('C003', 'Smith & Associates', 'desk@smithassoc.com', '555-0300', 'SILVER', 250000.00, 1)");
            stmt.executeUpdate("INSERT INTO CLIENTS (CLIENT_ID, CLIENT_NAME, EMAIL, PHONE, TIER, MAX_ORDER_VALUE, ACTIVE) VALUES ('C004', 'MegaFund Inc', 'ops@megafund.com', '555-0400', 'GOLD', 1000000.00, 1)");
            stmt.executeUpdate("INSERT INTO CLIENTS (CLIENT_ID, CLIENT_NAME, EMAIL, PHONE, TIER, MAX_ORDER_VALUE, ACTIVE) VALUES ('C005', 'Pinnacle Investments', 'trade@pinnacle.com', '555-0500', 'BRONZE', 100000.00, 1)");

            // Sample pricing data
            stmt.executeUpdate("INSERT INTO PRICING_CACHE (SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY) VALUES ('MSFT', 25.50, 25.75, 25.63, 'USD')");
            stmt.executeUpdate("INSERT INTO PRICING_CACHE (SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY) VALUES ('IBM', 120.00, 120.50, 120.25, 'USD')");
            stmt.executeUpdate("INSERT INTO PRICING_CACHE (SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY) VALUES ('ORCL', 15.25, 15.50, 15.38, 'USD')");
            stmt.executeUpdate("INSERT INTO PRICING_CACHE (SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY) VALUES ('SUNW', 8.75, 9.00, 8.88, 'USD')");
            stmt.executeUpdate("INSERT INTO PRICING_CACHE (SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY) VALUES ('CSCO', 22.00, 22.25, 22.13, 'USD')");
            stmt.executeUpdate("INSERT INTO PRICING_CACHE (SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY) VALUES ('INTC', 30.50, 30.75, 30.63, 'USD')");
            stmt.executeUpdate("INSERT INTO PRICING_CACHE (SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY) VALUES ('DELL', 35.00, 35.25, 35.13, 'USD')");

            System.out.println("Sample data inserted.");

        } catch (Exception e) {
            // probably already exists, ignore
            // (this is why we need proper IF NOT EXISTS for inserts...)
            System.err.println("WARN: Sample data insert issue: " + e.getMessage());
        }
    }
}
