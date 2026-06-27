package com.bigcorp.tradedesk.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;

import com.bigcorp.common.db.ConnectionHelper;

/**
 * Order status servlet. Displays order details or a list of recent orders.
 * 
 * Uses raw JDBC because "the DAO layer is overkill for a read-only servlet"
 * (famous last words from the design meeting, 2000-02-14).
 * 
 * @author Dave
 * @since 1.1
 */
public class OrderStatusServlet extends HttpServlet {

    // date format for display - copied from XmlHelper (should share but whatever)
    private static final String DISPLAY_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // max orders to show in the list view
    private static final int MAX_RECENT_ORDERS = 20;

    /**
     * Display order details or list of recent orders.
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        // HACK: fix for JIRA-1287 - IE6 caching issue (copied from OrderEntryServlet)
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");

        String orderId = request.getParameter("orderId");
        String msg = request.getParameter("msg");

        StringBuffer html = new StringBuffer();
        html.append("<HTML>\n");
        html.append("<HEAD>\n");
        html.append("<TITLE>BigCorp Trading Desk - Order Status</TITLE>\n");
        html.append("<STYLE>\n");
        html.append("  body { background-color: #C0C0C0; font-family: 'Times New Roman', Times, serif; margin: 0; }\n");
        html.append("  .header { background-color: #000080; color: #FFFFFF; padding: 8px; font-size: 14pt; font-weight: bold; }\n");
        html.append("  .content { padding: 15px; }\n");
        html.append("  .data-table { border-collapse: collapse; border: 2px outset #808080; background-color: #FFFFFF; }\n");
        html.append("  .data-table TH { background-color: #000080; color: #FFFFFF; padding: 4px 8px; font-size: 10pt; border: 1px solid #404040; }\n");
        html.append("  .data-table TD { padding: 4px 8px; font-size: 10pt; border: 1px solid #C0C0C0; }\n");
        html.append("  .data-table TR.alt { background-color: #E8E8E8; }\n");
        html.append("  .detail-table { border: 2px outset #808080; background-color: #D4D0C8; }\n");
        html.append("  .detail-table TD { padding: 4px 8px; font-size: 10pt; }\n");
        html.append("  .detail-label { font-weight: bold; text-align: right; color: #000080; width: 150px; }\n");
        html.append("  .nav { background-color: #D4D0C8; border-bottom: 1px solid #808080; padding: 4px 8px; font-size: 9pt; }\n");
        html.append("  .nav A { color: #000080; text-decoration: none; }\n");
        html.append("  .nav A:hover { text-decoration: underline; }\n");
        html.append("  .footer { font-size: 8pt; color: #808080; text-align: center; padding: 10px; }\n");
        html.append("  A { color: #000080; }\n");
        html.append("  .status-NEW { color: #0000FF; font-weight: bold; }\n");
        html.append("  .status-VALIDATED { color: #008080; font-weight: bold; }\n");
        html.append("  .status-PRICED { color: #008000; font-weight: bold; }\n");
        html.append("  .status-FILLED { color: #006400; font-weight: bold; }\n");
        html.append("  .status-REJECTED { color: #FF0000; font-weight: bold; }\n");
        html.append("  .status-CANCELLED { color: #808080; font-weight: bold; }\n");
        html.append("</STYLE>\n");
        html.append("</HEAD>\n");
        html.append("<BODY>\n");

        // Header bar
        html.append("<TABLE WIDTH=\"100%\" CELLPADDING=\"0\" CELLSPACING=\"0\" BORDER=\"0\">\n");
        html.append("<TR><TD CLASS=\"header\">\n");
        html.append("  <FONT SIZE=\"4\"><B>BigCorp Trading Desk</B></FONT>\n");
        html.append("  <FONT SIZE=\"2\"> - Order Status</FONT>\n");
        html.append("</TD></TR>\n");
        html.append("</TABLE>\n");

        // Navigation bar
        html.append("<DIV CLASS=\"nav\">\n");
        html.append("  <A HREF=\"/trade-desk/\">Home</A> | \n");
        html.append("  <A HREF=\"/trade-desk/order/entry\">New Order</A> | \n");
        html.append("  <B>Order Status</B>\n");
        html.append("</DIV>\n");
        html.append("<HR SIZE=\"1\" NOSHADE>\n");

        html.append("<DIV CLASS=\"content\">\n");

        // Success message (e.g., after order submission)
        if (msg != null && msg.length() > 0) {
            html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#CCFFCC\" BORDER=\"1\" BORDERCOLOR=\"#008000\"><TR><TD>\n");
            html.append("<FONT COLOR=\"#008000\"><B>").append(msg).append("</B></FONT>\n");
            html.append("</TD></TR></TABLE><BR>\n");
        }

        if (orderId != null && orderId.trim().length() > 0) {
            // Show single order detail
            showOrderDetail(html, orderId.trim());
        } else {
            // Show list of recent orders
            showRecentOrders(html);
        }

        html.append("</DIV>\n");

        // Footer
        html.append("<HR SIZE=\"1\" NOSHADE>\n");
        html.append("<DIV CLASS=\"footer\">\n");
        html.append("  <FONT SIZE=\"1\">BigCorp Trading Desk v2.1 | Internal Use Only | &copy; 2002 BigCorp Financial Services<BR>\n");
        html.append("  For support contact: helpdesk@bigcorp.com ext. 4357</FONT>\n");
        html.append("</DIV>\n");

        html.append("</BODY>\n");
        html.append("</HTML>");

        out.println(html.toString());
    }

    /**
     * Show details for a single order.
     */
    private void showOrderDetail(StringBuffer html, String orderId) {
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
                SimpleDateFormat sdf = new SimpleDateFormat(DISPLAY_DATE_FORMAT);
                String status = rs.getString("STATUS");

                html.append("<FONT SIZE=\"3\"><B>Order Details: ").append(orderId).append("</B></FONT>\n");
                html.append("<BR><BR>\n");

                html.append("<TABLE CLASS=\"detail-table\" CELLPADDING=\"4\" CELLSPACING=\"2\">\n");
                html.append("<TR><TD CLASS=\"detail-label\">Order ID:</TD><TD>").append(rs.getString("ORDER_ID")).append("</TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Client ID:</TD><TD>").append(rs.getString("CLIENT_ID")).append("</TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Symbol:</TD><TD><B>").append(rs.getString("SYMBOL")).append("</B></TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Quantity:</TD><TD>").append(rs.getInt("QUANTITY")).append("</TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Side:</TD><TD>").append(rs.getString("SIDE")).append("</TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Requested Price:</TD><TD>$").append(rs.getDouble("REQUESTED_PRICE")).append("</TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Filled Price:</TD><TD>$").append(rs.getDouble("PRICE")).append("</TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Status:</TD><TD><SPAN CLASS=\"status-").append(status).append("\">").append(status).append("</SPAN></TD></TR>\n");

                java.sql.Timestamp orderDate = rs.getTimestamp("ORDER_DATE");
                java.sql.Timestamp lastModified = rs.getTimestamp("LAST_MODIFIED");
                html.append("<TR><TD CLASS=\"detail-label\">Order Date:</TD><TD>").append(orderDate != null ? sdf.format(orderDate) : "N/A").append("</TD></TR>\n");
                html.append("<TR><TD CLASS=\"detail-label\">Last Modified:</TD><TD>").append(lastModified != null ? sdf.format(lastModified) : "N/A").append("</TD></TR>\n");

                String notes = rs.getString("NOTES");
                html.append("<TR><TD CLASS=\"detail-label\">Notes:</TD><TD>").append(notes != null ? notes : "&nbsp;").append("</TD></TR>\n");

                html.append("</TABLE>\n");

                html.append("<BR><A HREF=\"/trade-desk/order/status\">&laquo; Back to Order List</A>\n");

            } else {
                html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#FFCCCC\" BORDER=\"1\" BORDERCOLOR=\"#FF0000\"><TR><TD>\n");
                html.append("<FONT COLOR=\"#FF0000\"><B>Error:</B> Order not found: ").append(orderId).append("</FONT>\n");
                html.append("</TD></TR></TABLE>\n");
                html.append("<BR><A HREF=\"/trade-desk/order/status\">&laquo; Back to Order List</A>\n");
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to query order " + orderId + ": " + e.getMessage());
            e.printStackTrace();
            html.append("<FONT COLOR=\"#FF0000\"><B>Database error:</B> ").append(e.getMessage()).append("</FONT>\n");
        } finally {
            ConnectionHelper.closeQuietly(rs);
            // PreparedStatement extends Statement so this cast is fine
            ConnectionHelper.closeQuietly((Statement) pstmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Show a list of recent orders.
     */
    private void showRecentOrders(StringBuffer html) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();
            // TODO: add pagination (JIRA-2190)
            rs = stmt.executeQuery(
                "SELECT ORDER_ID, CLIENT_ID, SYMBOL, QUANTITY, SIDE, PRICE, " +
                "STATUS, ORDER_DATE FROM TRADE_ORDERS " +
                "ORDER BY ORDER_DATE DESC " +
                "FETCH FIRST " + MAX_RECENT_ORDERS + " ROWS ONLY");

            html.append("<FONT SIZE=\"3\"><B>Recent Trade Orders</B></FONT>\n");
            html.append("<FONT SIZE=\"2\"> (showing last ").append(MAX_RECENT_ORDERS).append(")</FONT>\n");
            html.append("<BR><BR>\n");

            html.append("<TABLE CLASS=\"data-table\" WIDTH=\"100%\" CELLPADDING=\"0\" CELLSPACING=\"0\">\n");
            html.append("<TR>\n");
            html.append("  <TH>Order ID</TH>\n");
            html.append("  <TH>Client</TH>\n");
            html.append("  <TH>Symbol</TH>\n");
            html.append("  <TH>Qty</TH>\n");
            html.append("  <TH>Side</TH>\n");
            html.append("  <TH>Price</TH>\n");
            html.append("  <TH>Status</TH>\n");
            html.append("  <TH>Date</TH>\n");
            html.append("</TR>\n");

            SimpleDateFormat sdf = new SimpleDateFormat(DISPLAY_DATE_FORMAT);
            int rowCount = 0;

            while (rs.next()) {
                String rowClass = (rowCount % 2 == 1) ? " CLASS=\"alt\"" : "";
                String status = rs.getString("STATUS");

                html.append("<TR").append(rowClass).append(">\n");
                html.append("  <TD><A HREF=\"/trade-desk/order/status?orderId=").append(rs.getString("ORDER_ID")).append("\">").append(rs.getString("ORDER_ID")).append("</A></TD>\n");
                html.append("  <TD>").append(rs.getString("CLIENT_ID")).append("</TD>\n");
                html.append("  <TD><B>").append(rs.getString("SYMBOL")).append("</B></TD>\n");
                html.append("  <TD ALIGN=\"right\">").append(rs.getInt("QUANTITY")).append("</TD>\n");
                html.append("  <TD>").append(rs.getString("SIDE")).append("</TD>\n");
                html.append("  <TD ALIGN=\"right\">$").append(rs.getDouble("PRICE")).append("</TD>\n");
                html.append("  <TD><SPAN CLASS=\"status-").append(status).append("\">").append(status).append("</SPAN></TD>\n");

                java.sql.Timestamp orderDate = rs.getTimestamp("ORDER_DATE");
                html.append("  <TD>").append(orderDate != null ? sdf.format(orderDate) : "N/A").append("</TD>\n");
                html.append("</TR>\n");
                rowCount++;
            }

            if (rowCount == 0) {
                html.append("<TR><TD COLSPAN=\"8\" ALIGN=\"center\"><I>No orders found.</I></TD></TR>\n");
            }

            html.append("</TABLE>\n");
            html.append("<BR><FONT SIZE=\"2\">").append(rowCount).append(" order(s) displayed.</FONT>\n");

        } catch (Exception e) {
            System.err.println("ERROR: Failed to query recent orders: " + e.getMessage());
            e.printStackTrace();
            html.append("<FONT COLOR=\"#FF0000\"><B>Database error:</B> ").append(e.getMessage()).append("</FONT>\n");
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
