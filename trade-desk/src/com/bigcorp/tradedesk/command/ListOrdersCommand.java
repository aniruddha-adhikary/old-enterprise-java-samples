package com.bigcorp.tradedesk.command;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.tradedesk.delegate.OrderServiceDelegate;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Command to list recent trade orders.
 * Uses the Business Delegate to fetch orders from the database.
 *
 * Shows last 25 orders in a table. Pagination is on the roadmap
 * (JIRA-2190, same as the old servlet).
 *
 * @author Bob
 * @since 2.0
 */
public class ListOrdersCommand implements Command {

    private OrderServiceDelegate delegate = new OrderServiceDelegate();

    private static final int MAX_ORDERS = 25;
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public String execute(HttpServletRequest request, HttpServletResponse response) throws Exception {

        List orders = delegate.getRecentOrders(MAX_ORDERS);

        StringBuffer html = new StringBuffer();

        html.append("<FONT SIZE=\"3\"><B>Recent Trade Orders</B></FONT>\n");
        html.append("<FONT SIZE=\"2\"> (showing last ").append(MAX_ORDERS).append(")</FONT>\n");
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

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        int rowCount = 0;

        for (int i = 0; i < orders.size(); i++) {
            TradeOrder order = (TradeOrder) orders.get(i);
            String rowClass = (rowCount % 2 == 1) ? " CLASS=\"alt\"" : "";
            String status = order.getStatus();

            html.append("<TR").append(rowClass).append(">\n");
            html.append("  <TD><A HREF=\"viewStatus.do?orderId=").append(order.getOrderId()).append("\">").append(order.getOrderId()).append("</A></TD>\n");
            html.append("  <TD>").append(order.getClientId()).append("</TD>\n");
            html.append("  <TD><B>").append(order.getSymbol()).append("</B></TD>\n");
            html.append("  <TD ALIGN=\"right\">").append(order.getQuantity()).append("</TD>\n");
            html.append("  <TD>").append(order.getSide()).append("</TD>\n");
            html.append("  <TD ALIGN=\"right\">$").append(order.getPrice()).append("</TD>\n");
            html.append("  <TD><SPAN CLASS=\"status-").append(status).append("\">").append(status).append("</SPAN></TD>\n");
            html.append("  <TD>").append(order.getOrderDate() != null ? sdf.format(order.getOrderDate()) : "N/A").append("</TD>\n");
            html.append("</TR>\n");
            rowCount++;
        }

        if (rowCount == 0) {
            html.append("<TR><TD COLSPAN=\"8\" ALIGN=\"center\"><I>No orders found.</I></TD></TR>\n");
        }

        html.append("</TABLE>\n");
        html.append("<BR><FONT SIZE=\"2\">").append(rowCount).append(" order(s) displayed.</FONT>\n");

        return html.toString();
    }
}
