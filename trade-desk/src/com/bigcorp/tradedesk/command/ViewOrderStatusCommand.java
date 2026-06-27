package com.bigcorp.tradedesk.command;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.tradedesk.delegate.OrderServiceDelegate;

import java.text.SimpleDateFormat;

/**
 * Command to view a single order's status.
 * Looks up order by ID from the database using the Business Delegate.
 *
 * If no orderId parameter is provided, shows an error.
 * Probably should redirect to ListOrdersCommand but "we'll fix that later."
 *
 * @author Bob
 * @since 2.0
 */
public class ViewOrderStatusCommand implements Command {

    private OrderServiceDelegate delegate = new OrderServiceDelegate();

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public String execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String orderId = request.getParameter("orderId");
        String msg = request.getParameter("msg");

        StringBuffer html = new StringBuffer();

        // success message (e.g., after order submission)
        if (msg != null && msg.length() > 0) {
            html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#CCFFCC\" BORDER=\"1\" BORDERCOLOR=\"#008000\"><TR><TD>\n");
            html.append("<FONT COLOR=\"#008000\"><B>").append(msg).append("</B></FONT>\n");
            html.append("</TD></TR></TABLE><BR>\n");
        }

        if (orderId == null || orderId.trim().length() == 0) {
            html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#FFCCCC\" BORDER=\"1\" BORDERCOLOR=\"#FF0000\"><TR><TD>\n");
            html.append("<FONT COLOR=\"#FF0000\"><B>Error:</B> No order ID specified.</FONT>\n");
            html.append("</TD></TR></TABLE>\n");
            html.append("<BR><A HREF=\"viewOrders.do\">&laquo; Back to Order List</A>\n");
            return html.toString();
        }

        TradeOrder order = delegate.findOrder(orderId.trim());

        if (order != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            String status = order.getStatus();

            html.append("<FONT SIZE=\"3\"><B>Order Details: ").append(orderId).append("</B></FONT>\n");
            html.append("<BR><BR>\n");

            html.append("<TABLE CLASS=\"detail-table\" CELLPADDING=\"4\" CELLSPACING=\"2\">\n");
            html.append("<TR><TD CLASS=\"detail-label\">Order ID:</TD><TD>").append(order.getOrderId()).append("</TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Client ID:</TD><TD>").append(order.getClientId()).append("</TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Symbol:</TD><TD><B>").append(order.getSymbol()).append("</B></TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Quantity:</TD><TD>").append(order.getQuantity()).append("</TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Side:</TD><TD>").append(order.getSide()).append("</TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Requested Price:</TD><TD>$").append(order.getRequestedPrice()).append("</TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Filled Price:</TD><TD>$").append(order.getPrice()).append("</TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Status:</TD><TD><SPAN CLASS=\"status-").append(status).append("\">").append(status).append("</SPAN></TD></TR>\n");

            html.append("<TR><TD CLASS=\"detail-label\">Order Date:</TD><TD>").append(order.getOrderDate() != null ? sdf.format(order.getOrderDate()) : "N/A").append("</TD></TR>\n");
            html.append("<TR><TD CLASS=\"detail-label\">Last Modified:</TD><TD>").append(order.getLastModified() != null ? sdf.format(order.getLastModified()) : "N/A").append("</TD></TR>\n");

            String notes = order.getNotes();
            html.append("<TR><TD CLASS=\"detail-label\">Notes:</TD><TD>").append(notes != null ? notes : "&nbsp;").append("</TD></TR>\n");

            html.append("</TABLE>\n");
            html.append("<BR><A HREF=\"viewOrders.do\">&laquo; Back to Order List</A>\n");

        } else {
            html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#FFCCCC\" BORDER=\"1\" BORDERCOLOR=\"#FF0000\"><TR><TD>\n");
            html.append("<FONT COLOR=\"#FF0000\"><B>Error:</B> Order not found: ").append(orderId).append("</FONT>\n");
            html.append("</TD></TR></TABLE>\n");
            html.append("<BR><A HREF=\"viewOrders.do\">&laquo; Back to Order List</A>\n");
        }

        return html.toString();
    }
}
