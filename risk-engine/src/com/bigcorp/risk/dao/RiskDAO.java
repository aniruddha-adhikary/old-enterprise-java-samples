package com.bigcorp.risk.dao;

import com.bigcorp.risk.model.RiskOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for risk engine tables.
 * 
 * We have our own connection management because the risk team
 * manages their own DB configuration. Currently points to the
 * same HSQLDB but in production this will be a separate schema.
 * 
 * @author contractor (risk team)
 * @since 2017-Q1
 */
public class RiskDAO {

    // Connection params — we manage our own, don't use ConnectionHelper
    private static final String DB_DRIVER = "org.hsqldb.jdbc.JDBCDriver";
    private static final String DB_URL = "jdbc:hsqldb:mem:bigcorpdb";
    private static final String DB_USER = "sa";
    private static final String DB_PASS = "";

    private Connection getConn() throws Exception {
        Class.forName(DB_DRIVER);
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private void close(Connection c) {
        if (c != null) try { c.close(); } catch (Exception e) { /* */ }
    }
    private void close(Statement s) {
        if (s != null) try { s.close(); } catch (Exception e) { /* */ }
    }
    private void close(ResultSet r) {
        if (r != null) try { r.close(); } catch (Exception e) { /* */ }
    }

    /**
     * Save a risk assessment.
     */
    public void saveRiskAssessment(RiskOrder order) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConn();
            ps = conn.prepareStatement(
                "INSERT INTO RISK_ASSESSMENTS (RISK_ORDER_ID, SOURCE_ORDER_ID, CLIENT_ID, SYMBOL, " +
                "QUANTITY, SIDE, PRICE, NOTIONAL_VALUE, EXPOSURE_CONTRIBUTION, VAR_CONTRIBUTION, " +
                "RISK_STATUS, ASSESSMENT_DATE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            );
            ps.setString(1, order.getRiskOrderId());
            ps.setString(2, order.getSourceOrderId());
            ps.setString(3, order.getClientId());
            ps.setString(4, order.getSymbol());
            ps.setInt(5, order.getQuantity());
            ps.setString(6, order.getSide());
            ps.setDouble(7, order.getPrice());
            ps.setDouble(8, order.getNotionalValue());
            ps.setDouble(9, order.getExposureContribution());
            ps.setDouble(10, order.getVarContribution());
            ps.setString(11, order.getRiskStatus());
            ps.setTimestamp(12, new Timestamp(order.getAssessmentDate().getTime()));
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[RISK-DAO] Error saving risk assessment: " + e.getMessage());
        } finally {
            close(ps);
            close(conn);
        }
    }

    /**
     * Get total exposure for a client.
     */
    public double getClientExposure(String clientId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConn();
            ps = conn.prepareStatement(
                "SELECT SUM(EXPOSURE_CONTRIBUTION) FROM RISK_ASSESSMENTS WHERE CLIENT_ID = ?"
            );
            ps.setString(1, clientId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
            return 0.0;
        } catch (Exception e) {
            System.err.println("[RISK-DAO] Error getting client exposure: " + e.getMessage());
            return 0.0;
        } finally {
            close(rs);
            close(ps);
            close(conn);
        }
    }

    /**
     * Get total VaR for a client.
     */
    public double getClientVaR(String clientId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConn();
            ps = conn.prepareStatement(
                "SELECT SUM(VAR_CONTRIBUTION) FROM RISK_ASSESSMENTS WHERE CLIENT_ID = ?"
            );
            ps.setString(1, clientId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
            return 0.0;
        } catch (Exception e) {
            System.err.println("[RISK-DAO] Error getting client VaR: " + e.getMessage());
            return 0.0;
        } finally {
            close(rs);
            close(ps);
            close(conn);
        }
    }

    /**
     * Get all risk assessments.
     */
    public List getAllAssessments() {
        List results = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConn();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT * FROM RISK_ASSESSMENTS ORDER BY ASSESSMENT_DATE DESC");
            while (rs.next()) {
                RiskOrder ro = new RiskOrder();
                ro.setRiskOrderId(rs.getString("RISK_ORDER_ID"));
                ro.setSourceOrderId(rs.getString("SOURCE_ORDER_ID"));
                ro.setClientId(rs.getString("CLIENT_ID"));
                ro.setSymbol(rs.getString("SYMBOL"));
                ro.setQuantity(rs.getInt("QUANTITY"));
                ro.setSide(rs.getString("SIDE"));
                ro.setPrice(rs.getDouble("PRICE"));
                ro.setNotionalValue(rs.getDouble("NOTIONAL_VALUE"));
                ro.setExposureContribution(rs.getDouble("EXPOSURE_CONTRIBUTION"));
                ro.setVarContribution(rs.getDouble("VAR_CONTRIBUTION"));
                ro.setRiskStatus(rs.getString("RISK_STATUS"));
                ro.setAssessmentDate(rs.getTimestamp("ASSESSMENT_DATE"));
                results.add(ro);
            }
        } catch (Exception e) {
            System.err.println("[RISK-DAO] Error getting assessments: " + e.getMessage());
        } finally {
            close(rs);
            close(stmt);
            close(conn);
        }
        return results;
    }
}
