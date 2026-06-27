package com.bigcorp.tradedesk.command;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * Dashboard command - shows system statistics.
 * Total orders, orders by status, total settlements, total notifications.
 *
 * Queries the database directly with raw JDBC because "there's no
 * point wrapping a few COUNT(*) queries in a delegate" (Bob).
 * Except we DO have a delegate method for order counts.
 * This class uses both. Nobody said it had to be consistent.
 *
 * Added for the "management dashboard" initiative (JIRA-2801).
 * Management wanted "a page with numbers on it" and this is what
 * they got.
 *
 * @author Bob
 * @since 2.0
 */
public class DashboardCommand implements Command {

    public String execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        StringBuffer html = new StringBuffer();

        html.append("<FONT SIZE=\"3\"><B>System Dashboard</B></FONT>\n");
        html.append("<BR><FONT SIZE=\"2\" COLOR=\"#808080\">BigCorp Trade Order Management System - Status Overview</FONT>\n");
        html.append("<BR><BR>\n");

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            // --- Order Statistics ---
            html.append("<TABLE CLASS=\"form-table\" CELLPADDING=\"6\" CELLSPACING=\"2\" WIDTH=\"80%\">\n");
            html.append("<TR><TD COLSPAN=\"2\" BGCOLOR=\"#000080\"><FONT COLOR=\"#FFFFFF\"><B>Order Statistics</B></FONT></TD></TR>\n");

            // Total orders
            rs = stmt.executeQuery("SELECT COUNT(*) FROM TRADE_ORDERS");
            int totalOrders = 0;
            if (rs.next()) {
                totalOrders = rs.getInt(1);
            }
            rs.close();
            html.append("<TR><TD CLASS=\"label\" WIDTH=\"40%\">Total Orders:</TD><TD><B>").append(totalOrders).append("</B></TD></TR>\n");

            // Orders by status
            rs = stmt.executeQuery(
                "SELECT STATUS, COUNT(*) AS CNT FROM TRADE_ORDERS GROUP BY STATUS ORDER BY STATUS");
            Hashtable statusCounts = new Hashtable();
            while (rs.next()) {
                String status = rs.getString("STATUS");
                int cnt = rs.getInt("CNT");
                statusCounts.put(status, new Integer(cnt));
            }
            rs.close();

            // show each status count
            Enumeration keys = statusCounts.keys();
            while (keys.hasMoreElements()) {
                String status = (String) keys.nextElement();
                Integer cnt = (Integer) statusCounts.get(status);
                html.append("<TR><TD CLASS=\"label\">&nbsp;&nbsp;&nbsp;").append(status).append(":</TD>");
                html.append("<TD><SPAN CLASS=\"status-").append(status).append("\">").append(cnt).append("</SPAN></TD></TR>\n");
            }

            html.append("</TABLE>\n");
            html.append("<BR>\n");

            // --- Settlement Statistics ---
            html.append("<TABLE CLASS=\"form-table\" CELLPADDING=\"6\" CELLSPACING=\"2\" WIDTH=\"80%\">\n");
            html.append("<TR><TD COLSPAN=\"2\" BGCOLOR=\"#000080\"><FONT COLOR=\"#FFFFFF\"><B>Settlement Statistics</B></FONT></TD></TR>\n");

            rs = stmt.executeQuery("SELECT COUNT(*) FROM SETTLEMENT_RECORDS");
            int totalSettlements = 0;
            if (rs.next()) {
                totalSettlements = rs.getInt(1);
            }
            rs.close();
            html.append("<TR><TD CLASS=\"label\" WIDTH=\"40%\">Total Settlements:</TD><TD><B>").append(totalSettlements).append("</B></TD></TR>\n");

            // settlement amount total
            rs = stmt.executeQuery("SELECT COALESCE(SUM(AMOUNT), 0) FROM SETTLEMENT_RECORDS");
            double totalAmount = 0.0;
            if (rs.next()) {
                totalAmount = rs.getDouble(1);
            }
            rs.close();
            html.append("<TR><TD CLASS=\"label\">Total Settlement Value:</TD><TD><B>$").append(totalAmount).append("</B></TD></TR>\n");

            html.append("</TABLE>\n");
            html.append("<BR>\n");

            // --- Notification Statistics ---
            html.append("<TABLE CLASS=\"form-table\" CELLPADDING=\"6\" CELLSPACING=\"2\" WIDTH=\"80%\">\n");
            html.append("<TR><TD COLSPAN=\"2\" BGCOLOR=\"#000080\"><FONT COLOR=\"#FFFFFF\"><B>Notification Statistics</B></FONT></TD></TR>\n");

            rs = stmt.executeQuery("SELECT COUNT(*) FROM NOTIFICATIONS");
            int totalNotifications = 0;
            if (rs.next()) {
                totalNotifications = rs.getInt(1);
            }
            rs.close();
            html.append("<TR><TD CLASS=\"label\" WIDTH=\"40%\">Total Notifications:</TD><TD><B>").append(totalNotifications).append("</B></TD></TR>\n");

            // notifications by status
            rs = stmt.executeQuery(
                "SELECT STATUS, COUNT(*) AS CNT FROM NOTIFICATIONS GROUP BY STATUS ORDER BY STATUS");
            while (rs.next()) {
                String nStatus = rs.getString("STATUS");
                int nCnt = rs.getInt("CNT");
                html.append("<TR><TD CLASS=\"label\">&nbsp;&nbsp;&nbsp;").append(nStatus).append(":</TD><TD>").append(nCnt).append("</TD></TR>\n");
            }
            rs.close();

            html.append("</TABLE>\n");
            html.append("<BR>\n");

            // --- Client Statistics ---
            html.append("<TABLE CLASS=\"form-table\" CELLPADDING=\"6\" CELLSPACING=\"2\" WIDTH=\"80%\">\n");
            html.append("<TR><TD COLSPAN=\"2\" BGCOLOR=\"#000080\"><FONT COLOR=\"#FFFFFF\"><B>Client Statistics</B></FONT></TD></TR>\n");

            rs = stmt.executeQuery("SELECT COUNT(*) FROM CLIENTS WHERE ACTIVE = 1");
            int activeClients = 0;
            if (rs.next()) {
                activeClients = rs.getInt(1);
            }
            rs.close();
            html.append("<TR><TD CLASS=\"label\" WIDTH=\"40%\">Active Clients:</TD><TD><B>").append(activeClients).append("</B></TD></TR>\n");

            html.append("</TABLE>\n");

        } catch (Exception e) {
            System.err.println("ERROR: Dashboard query failed: " + e.getMessage());
            e.printStackTrace();
            html.append("<FONT COLOR=\"#FF0000\"><B>Database error:</B> ").append(e.getMessage()).append("</FONT>\n");
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }

        return html.toString();
    }
}
