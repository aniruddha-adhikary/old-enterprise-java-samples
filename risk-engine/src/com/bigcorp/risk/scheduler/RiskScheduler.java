package com.bigcorp.risk.scheduler;

import com.bigcorp.risk.dao.RiskDAO;
import com.bigcorp.risk.engine.ExposureCalculator;
import com.bigcorp.risk.model.RiskOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Risk assessment scheduler.
 * 
 * Periodically scans TRADE_ORDERS for new/modified orders and
 * runs risk calculations on them. In production this would be
 * triggered by JMS events from the confirmations queue, but for
 * now we just poll the database.
 * 
 * The risk team manages their own scheduling — we don't use
 * whatever timer mechanism the main app has.
 * 
 * @author contractor (risk team)
 * @since 2017-Q2
 */
public class RiskScheduler {

    // Poll interval in ms — we run our own timer
    private static final long POLL_INTERVAL_MS = 30000;

    // Our own queue names
    public static final String RISK_INBOUND_QUEUE = "RISK.ORDERS.INBOUND";

    private RiskDAO riskDAO;
    private boolean running;

    public RiskScheduler() {
        this.riskDAO = new RiskDAO();
        this.running = false;
    }

    /**
     * Process all unassessed orders from TRADE_ORDERS.
     * This is the main batch assessment method.
     * Returns the number of orders assessed.
     */
    public int assessPendingOrders() {
        List orders = fetchUnassessedOrders();
        int assessed = 0;
        for (int i = 0; i < orders.size(); i++) {
            RiskOrder ro = (RiskOrder) orders.get(i);
            try {
                ExposureCalculator.calculateRisk(ro);
                riskDAO.saveRiskAssessment(ro);
                assessed++;
                System.out.println("[RISK] Assessed: " + ro);
            } catch (Exception e) {
                System.err.println("[RISK] Error assessing order " + ro.getSourceOrderId() + ": " + e.getMessage());
                ro.setRiskStatus(RiskOrder.RISK_STATUS_ERROR);
            }
        }
        return assessed;
    }

    /**
     * Fetch orders from TRADE_ORDERS that haven't been risk-assessed yet.
     * We do our own DB access here — separate from the OrderDAO.
     */
    private List fetchUnassessedOrders() {
        List orders = new ArrayList();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
            conn = DriverManager.getConnection("jdbc:hsqldb:mem:bigcorpdb", "sa", "");
            stmt = conn.createStatement();

            // Find FILLED orders that don't have a risk assessment yet
            rs = stmt.executeQuery(
                "SELECT o.ORDER_ID, o.CLIENT_ID, o.SYMBOL, o.QUANTITY, o.SIDE, o.PRICE " +
                "FROM TRADE_ORDERS o " +
                "WHERE o.STATUS IN ('FILLED', 'SETTLED') " +
                "AND o.ORDER_ID NOT IN (SELECT SOURCE_ORDER_ID FROM RISK_ASSESSMENTS)"
            );

            int seq = 1;
            while (rs.next()) {
                RiskOrder ro = new RiskOrder();
                ro.setRiskOrderId("RISK-" + System.currentTimeMillis() + "-" + seq++);
                ro.setSourceOrderId(rs.getString("ORDER_ID"));
                ro.setClientId(rs.getString("CLIENT_ID"));
                ro.setSymbol(rs.getString("SYMBOL"));
                ro.setQuantity(rs.getInt("QUANTITY"));
                ro.setSide(rs.getString("SIDE"));
                ro.setPrice(rs.getDouble("PRICE"));
                orders.add(ro);
            }
        } catch (Exception e) {
            System.err.println("[RISK] Error fetching unassessed orders: " + e.getMessage());
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception e) {}
            if (stmt != null) try { stmt.close(); } catch (Exception e) {}
            if (conn != null) try { conn.close(); } catch (Exception e) {}
        }
        return orders;
    }

    /**
     * Get total portfolio exposure for a client.
     */
    public double getClientExposure(String clientId) {
        return riskDAO.getClientExposure(clientId);
    }

    /**
     * Get total VaR for a client.
     */
    public double getClientVaR(String clientId) {
        return riskDAO.getClientVaR(clientId);
    }
}
