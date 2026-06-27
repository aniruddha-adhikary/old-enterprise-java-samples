package com.bigcorp.tradedesk.command;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Fallback command for unknown actions.
 * Shows an error page instead of a NullPointerException.
 * 
 * Better than what we had before (JIRA-2567) where an unknown
 * action parameter caused an NPE and a blank page in IE6.
 *
 * @author Bob
 * @since 2.0
 */
public class UnknownCommand implements Command {

    public String execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String action = request.getParameter("action");
        // also check if it came from a .do URL
        if (action == null || action.length() == 0) {
            String uri = request.getRequestURI();
            if (uri != null) {
                action = uri;
            }
        }

        StringBuffer html = new StringBuffer();
        html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#FFCCCC\" BORDER=\"1\" BORDERCOLOR=\"#FF0000\"><TR><TD>\n");
        html.append("<FONT COLOR=\"#FF0000\"><B>Error:</B> Unknown action");
        if (action != null && action.length() > 0) {
            html.append(": ").append(action);
        }
        html.append("</FONT>\n");
        html.append("</TD></TR></TABLE>\n");
        html.append("<BR>\n");
        html.append("<FONT SIZE=\"2\">Available actions:</FONT><BR>\n");
        html.append("<UL>\n");
        html.append("  <LI><A HREF=\"submitOrder.do\">Submit Order</A></LI>\n");
        html.append("  <LI><A HREF=\"viewOrders.do\">View Orders</A></LI>\n");
        html.append("  <LI><A HREF=\"dashboard.do\">Dashboard</A></LI>\n");
        html.append("</UL>\n");
        return html.toString();
    }
}
