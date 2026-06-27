package com.bigcorp.settlement.dao;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.model.SettlementRecord;
import com.bigcorp.common.model.TradeOrder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Data access for settlement processing.
 * 
 * All raw JDBC with string concatenation. We know about PreparedStatement.
 * We'll refactor this "soon" (JIRA-3102, filed 2001-04-12).
 * 
 * @author Dave (settlements team)
 * @since 1.1
 */
public class SettlementDAO {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Find all orders that have been filled and are ready for settlement.
     */
    public List findFilledOrders() {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            String sql = "SELECT ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, PRICE, "
                    + "REQUESTED_PRICE, STATUS, ORDER_DATE, LAST_MODIFIED, NOTES "
                    + "FROM TRADE_ORDERS WHERE STATUS = '" + TradeOrder.STATUS_FILLED + "'";

            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                TradeOrder order = new TradeOrder();
                order.setOrderId(rs.getString("ORDER_ID"));
                order.setClientId(rs.getString("CLIENT_ID"));
                order.setSymbol(rs.getString("SYMBOL"));
                order.setQuantity(rs.getInt("QUANTITY"));
                order.setSide(rs.getString("SIDE"));
                order.setPrice(rs.getDouble("PRICE"));
                order.setRequestedPrice(rs.getDouble("REQUESTED_PRICE"));
                order.setStatus(rs.getString("STATUS"));
                order.setOrderDate(rs.getTimestamp("ORDER_DATE"));
                order.setLastModified(rs.getTimestamp("LAST_MODIFIED"));
                order.setNotes(rs.getString("NOTES"));
                results.add(order);
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to find filled orders: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }

        return results;
    }

    /**
     * Insert a new settlement record.
     */
    public void saveSettlementRecord(SettlementRecord record) {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            String tradeDateStr = record.getTradeDate() != null ? sdf.format(record.getTradeDate()) : null;
            String settleDateStr = record.getSettlementDate() != null ? sdf.format(record.getSettlementDate()) : null;

            String sql = "INSERT INTO SETTLEMENT_RECORDS (RECORD_ID, ORDER_ID, CLIENT_ID, SYMBOL, "
                    + "QUANTITY, SIDE, AMOUNT, COMMISSION, TRADE_DATE, SETTLEMENT_DATE, STATUS, "
                    + "BATCH_ID, EXTERNAL_REF) VALUES ("
                    + "'" + record.getRecordId() + "', "
                    + "'" + record.getOrderId() + "', "
                    + "'" + record.getClientId() + "', "
                    + "'" + record.getSymbol() + "', "
                    + record.getQuantity() + ", "
                    + "'" + record.getSide() + "', "
                    + record.getAmount() + ", "
                    + record.getCommission() + ", "
                    + (tradeDateStr != null ? "'" + tradeDateStr + "'" : "NULL") + ", "
                    + (settleDateStr != null ? "'" + settleDateStr + "'" : "NULL") + ", "
                    + "'" + record.getStatus() + "', "
                    + (record.getBatchId() != null ? "'" + record.getBatchId() + "'" : "NULL") + ", "
                    + (record.getExternalRef() != null ? "'" + record.getExternalRef() + "'" : "NULL")
                    + ")";

            stmt.executeUpdate(sql);

        } catch (Exception e) {
            System.err.println("ERROR: Failed to save settlement record: " + e.getMessage());
            e.printStackTrace();
            // TODO: should this throw? Dave says "probably yes" but we haven't 
            // decided what the caller should do if a single record fails
        } finally {
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Update the status of a settlement record.
     */
    public void updateSettlementStatus(String recordId, String status) {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            String sql = "UPDATE SETTLEMENT_RECORDS SET STATUS = '" + status + "' "
                    + "WHERE RECORD_ID = '" + recordId + "'";

            int rows = stmt.executeUpdate(sql);
            if (rows == 0) {
                System.err.println("WARN: No settlement record found with ID: " + recordId);
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to update settlement status: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Update the status of a trade order.
     */
    public void updateOrderStatus(String orderId, String status) {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            String now = sdf.format(new Date());

            String sql = "UPDATE TRADE_ORDERS SET STATUS = '" + status + "', "
                    + "LAST_MODIFIED = '" + now + "' "
                    + "WHERE ORDER_ID = '" + orderId + "'";

            int rows = stmt.executeUpdate(sql);
            if (rows == 0) {
                System.err.println("WARN: No order found with ID: " + orderId);
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to update order status: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Find settlement records that are still pending (not yet included in a batch file).
     */
    public List findPendingSettlements() {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            String sql = "SELECT RECORD_ID, ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, "
                    + "AMOUNT, COMMISSION, TRADE_DATE, SETTLEMENT_DATE, STATUS, BATCH_ID, EXTERNAL_REF "
                    + "FROM SETTLEMENT_RECORDS WHERE STATUS = '" + SettlementRecord.STATUS_PENDING + "'";

            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                SettlementRecord rec = new SettlementRecord();
                rec.setRecordId(rs.getString("RECORD_ID"));
                rec.setOrderId(rs.getString("ORDER_ID"));
                rec.setClientId(rs.getString("CLIENT_ID"));
                rec.setSymbol(rs.getString("SYMBOL"));
                rec.setQuantity(rs.getInt("QUANTITY"));
                rec.setSide(rs.getString("SIDE"));
                rec.setAmount(rs.getDouble("AMOUNT"));
                rec.setCommission(rs.getDouble("COMMISSION"));
                rec.setTradeDate(rs.getTimestamp("TRADE_DATE"));
                rec.setSettlementDate(rs.getTimestamp("SETTLEMENT_DATE"));
                rec.setStatus(rs.getString("STATUS"));
                rec.setBatchId(rs.getString("BATCH_ID"));
                rec.setExternalRef(rs.getString("EXTERNAL_REF"));
                results.add(rec);
            }

        } catch (Exception e) {
            System.err.println("ERROR: Failed to find pending settlements: " + e.getMessage());
            e.printStackTrace();
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }

        return results;
    }
}
