package com.bigcorp.reporting.dao;

import com.bigcorp.reporting.config.ReportConfig;
import com.bigcorp.reporting.util.ReportLogger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access layer for reporting queries.
 * 
 * NOTE: We have our own JDBC connection management here instead of using
 * the common-lib ConnectionHelper because we might need to point this at
 * a read replica in the future. Also I didn't want to figure out their
 * connection setup — their code has some Oracle-specific hacks that don't
 * apply to us.
 * 
 * @author contractor (reporting team)
 * @since 2013-Q2
 */
public class ReportingDAO {

    private static final ReportLogger log = new ReportLogger(ReportingDAO.class);

    /**
     * Get a connection using our own config.
     * We duplicate ConnectionHelper logic because we manage our own pool.
     */
    private Connection getConnection() throws Exception {
        Class.forName(ReportConfig.getDbDriver());
        return DriverManager.getConnection(
            ReportConfig.getDbUrl(),
            ReportConfig.getDbUser(),
            ReportConfig.getDbPass()
        );
    }

    private void closeQuietly(Connection c) {
        if (c != null) { try { c.close(); } catch (Exception e) { /* ignore */ } }
    }

    private void closeQuietly(Statement s) {
        if (s != null) { try { s.close(); } catch (Exception e) { /* ignore */ } }
    }

    private void closeQuietly(ResultSet r) {
        if (r != null) { try { r.close(); } catch (Exception e) { /* ignore */ } }
    }

    /**
     * Get daily P&L summary from TRADE_ORDERS.
     * Returns list of maps with keys: TRADE_DATE, SYMBOL, SIDE, TOTAL_QTY, TOTAL_VALUE, ORDER_COUNT
     */
    public List getDailyPnlSummary() {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "SELECT CAST(ORDER_DATE AS DATE) AS TRADE_DATE, SYMBOL, SIDE, " +
                "SUM(QUANTITY) AS TOTAL_QTY, SUM(QUANTITY * PRICE) AS TOTAL_VALUE, " +
                "COUNT(*) AS ORDER_COUNT " +
                "FROM TRADE_ORDERS WHERE STATUS = 'FILLED' OR STATUS = 'SETTLED' " +
                "GROUP BY CAST(ORDER_DATE AS DATE), SYMBOL, SIDE " +
                "ORDER BY CAST(ORDER_DATE AS DATE) DESC, SYMBOL ASC"
            );
            while (rs.next()) {
                Map row = new HashMap();
                row.put("TRADE_DATE", rs.getString("TRADE_DATE"));
                row.put("SYMBOL", rs.getString("SYMBOL"));
                row.put("SIDE", rs.getString("SIDE"));
                row.put("TOTAL_QTY", String.valueOf(rs.getInt("TOTAL_QTY")));
                row.put("TOTAL_VALUE", String.valueOf(rs.getDouble("TOTAL_VALUE")));
                row.put("ORDER_COUNT", String.valueOf(rs.getInt("ORDER_COUNT")));
                results.add(row);
            }
            log.info("Retrieved " + results.size() + " P&L summary rows");
        } catch (Exception e) {
            log.error("Failed to get daily P&L summary", e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            closeQuietly(conn);
        }
        return results;
    }

    /**
     * Get monthly trade volume report from TRADE_ORDERS.
     * Returns list of maps with keys: MONTH, TOTAL_ORDERS, TOTAL_VOLUME, TOTAL_VALUE
     */
    public List getMonthlyVolumeReport() {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();
            // HSQLDB-compatible month extraction
            rs = stmt.executeQuery(
                "SELECT MONTH(ORDER_DATE) AS TRADE_MONTH, YEAR(ORDER_DATE) AS TRADE_YEAR, " +
                "COUNT(*) AS TOTAL_ORDERS, SUM(QUANTITY) AS TOTAL_VOLUME, " +
                "SUM(QUANTITY * PRICE) AS TOTAL_VALUE " +
                "FROM TRADE_ORDERS WHERE STATUS IN ('FILLED', 'SETTLED') " +
                "GROUP BY YEAR(ORDER_DATE), MONTH(ORDER_DATE) " +
                "ORDER BY YEAR(ORDER_DATE) DESC, MONTH(ORDER_DATE) DESC"
            );
            while (rs.next()) {
                Map row = new HashMap();
                row.put("MONTH", rs.getString("TRADE_YEAR") + "-" + String.format("%02d", rs.getInt("TRADE_MONTH")));
                row.put("TOTAL_ORDERS", String.valueOf(rs.getInt("TOTAL_ORDERS")));
                row.put("TOTAL_VOLUME", String.valueOf(rs.getLong("TOTAL_VOLUME")));
                row.put("TOTAL_VALUE", String.valueOf(rs.getDouble("TOTAL_VALUE")));
                results.add(row);
            }
            log.info("Retrieved " + results.size() + " monthly volume rows");
        } catch (Exception e) {
            log.error("Failed to get monthly volume report", e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            closeQuietly(conn);
        }
        return results;
    }

    /**
     * Get billing summary from BILLING_LEDGER.
     * Returns list of maps with keys: CLIENT_ID, TOTAL_GROSS, TOTAL_COMMISSION, TOTAL_NET, ENTRY_COUNT
     */
    public List getBillingSummary() {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "SELECT CLIENT_ID, SUM(GROSS_AMOUNT) AS TOTAL_GROSS, " +
                "SUM(COMMISSION_AMOUNT) AS TOTAL_COMMISSION, " +
                "SUM(NET_AMOUNT) AS TOTAL_NET, COUNT(*) AS ENTRY_COUNT " +
                "FROM BILLING_LEDGER WHERE STATUS = 'CHARGED' " +
                "GROUP BY CLIENT_ID ORDER BY TOTAL_GROSS DESC"
            );
            while (rs.next()) {
                Map row = new HashMap();
                row.put("CLIENT_ID", rs.getString("CLIENT_ID"));
                row.put("TOTAL_GROSS", String.valueOf(rs.getDouble("TOTAL_GROSS")));
                row.put("TOTAL_COMMISSION", String.valueOf(rs.getDouble("TOTAL_COMMISSION")));
                row.put("TOTAL_NET", String.valueOf(rs.getDouble("TOTAL_NET")));
                row.put("ENTRY_COUNT", String.valueOf(rs.getInt("ENTRY_COUNT")));
                results.add(row);
            }
            log.info("Retrieved " + results.size() + " billing summary rows");
        } catch (Exception e) {
            log.error("Failed to get billing summary", e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            closeQuietly(conn);
        }
        return results;
    }

    /**
     * Get settlement summary from SETTLEMENT_RECORDS.
     * Returns list of maps with keys: STATUS, RECORD_COUNT, TOTAL_AMOUNT, TOTAL_COMMISSION
     */
    public List getSettlementSummary() {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(
                "SELECT STATUS, COUNT(*) AS RECORD_COUNT, " +
                "SUM(AMOUNT) AS TOTAL_AMOUNT, SUM(COMMISSION) AS TOTAL_COMMISSION " +
                "FROM SETTLEMENT_RECORDS " +
                "GROUP BY STATUS ORDER BY STATUS"
            );
            while (rs.next()) {
                Map row = new HashMap();
                row.put("STATUS", rs.getString("STATUS"));
                row.put("RECORD_COUNT", String.valueOf(rs.getInt("RECORD_COUNT")));
                row.put("TOTAL_AMOUNT", String.valueOf(rs.getDouble("TOTAL_AMOUNT")));
                row.put("TOTAL_COMMISSION", String.valueOf(rs.getDouble("TOTAL_COMMISSION")));
                results.add(row);
            }
            log.info("Retrieved " + results.size() + " settlement summary rows");
        } catch (Exception e) {
            log.error("Failed to get settlement summary", e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            closeQuietly(conn);
        }
        return results;
    }
}
