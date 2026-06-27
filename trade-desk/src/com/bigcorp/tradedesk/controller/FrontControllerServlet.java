package com.bigcorp.tradedesk.controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.tradedesk.command.Command;
import com.bigcorp.tradedesk.command.CommandFactory;

/**
 * Front Controller pattern - Core J2EE Patterns.
 * 
 * Single servlet that handles ALL requests and dispatches to Command objects.
 * This was added in the "architecture improvement" initiative of 2002.
 * The old servlets (OrderEntryServlet, OrderStatusServlet) still work
 * and are still mapped in web.xml -- nobody wanted to remove them because
 * "what if the new controller breaks?"
 *
 * Supports two URL patterns:
 *   /controller/*  - action from ?action= parameter
 *   *.do           - action parsed from URL (Struts-style)
 *                    e.g. submitOrder.do -> action "submitOrder"
 *
 * Maps action parameter to Command classes:
 *   ?action=submitOrder   -> SubmitOrderCommand
 *   ?action=viewStatus    -> ViewOrderStatusCommand
 *   ?action=viewOrders    -> ListOrdersCommand
 *   ?action=viewDashboard -> DashboardCommand (shows system stats)
 *
 * @author Bob (after reading "Core J2EE Patterns" by Deepak Alur)
 * @since 2.0
 */
public class FrontControllerServlet extends HttpServlet {

    /**
     * Initialize database and MQ connections.
     * Same as OrderEntryServlet.init() -- yes, both servlets
     * initialize the same things. ConnectionHelper.init() is
     * idempotent so it's fine. Probably.
     */
    public void init() throws ServletException {
        super.init();
        try {
            ConnectionHelper.init();
            DatabaseBootstrap.bootstrap();
            MessageQueueHelper.init();
            System.out.println("[FrontController] Initialized successfully.");
        } catch (Exception e) {
            System.err.println("ERROR: FrontControllerServlet init failed: " + e.getMessage());
            e.printStackTrace();
            throw new ServletException("Failed to initialize FrontControllerServlet", e);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Central request processing method.
     * Determines the action, gets the Command, executes it,
     * and wraps the result in the standard page template.
     */
    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // figure out the action name
        String action = resolveAction(request);

        System.out.println("[FrontController] Action: " + action + " URI: " + request.getRequestURI());

        // get the command from the factory
        Command command = CommandFactory.getCommand(action);

        // execute the command
        String bodyHtml = null;
        try {
            bodyHtml = command.execute(request, response);
        } catch (Exception e) {
            System.err.println("ERROR: Command execution failed for action '" + action + "': " + e.getMessage());
            e.printStackTrace();
            bodyHtml = "<TABLE WIDTH=\"100%\" BGCOLOR=\"#FFCCCC\" BORDER=\"1\" BORDERCOLOR=\"#FF0000\"><TR><TD>\n"
                     + "<FONT COLOR=\"#FF0000\"><B>System Error:</B> " + e.getMessage() + "</FONT>\n"
                     + "</TD></TR></TABLE>\n";
        }

        // if the command did a redirect, bodyHtml will be null
        if (bodyHtml == null) {
            return;
        }

        // wrap in standard page template and send response
        response.setContentType("text/html");
        // HACK: fix for JIRA-1287 - IE6 caching issue
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");

        PrintWriter out = response.getWriter();
        out.println(buildPage(action, bodyHtml));
    }

    /**
     * Resolve the action name from the request.
     * First checks the ?action= parameter (for /controller/* mapping).
     * If not found, tries to parse from the URL (for *.do mapping).
     *
     * For *.do URLs:
     *   /trade-desk/submitOrder.do  -> "submitOrder"
     *   /submitOrder.do             -> "submitOrder"
     *   /some/path/viewOrders.do    -> "viewOrders"
     */
    private String resolveAction(HttpServletRequest request) {
        // check parameter first
        String action = request.getParameter("action");
        if (action != null && action.trim().length() > 0) {
            return action.trim();
        }

        // try to parse from URI (for *.do mapping)
        String uri = request.getRequestURI();
        if (uri != null && uri.endsWith(".do")) {
            // strip the .do extension
            String withoutDo = uri.substring(0, uri.length() - 3);
            // get just the last path segment (action name)
            int lastSlash = withoutDo.lastIndexOf('/');
            if (lastSlash >= 0) {
                return withoutDo.substring(lastSlash + 1);
            }
            return withoutDo;
        }

        // no action found - will default to dashboard in CommandFactory
        return null;
    }

    /**
     * Build the complete HTML page with the standard BigCorp template.
     * Navy header, grey background, Times New Roman - just like 2001.
     */
    private String buildPage(String action, String bodyHtml) {
        // figure out page title based on action
        String pageTitle = "BigCorp Trading Desk";
        if ("submitOrder".equals(action)) {
            pageTitle = "BigCorp Trading Desk - New Order Entry";
        } else if ("viewStatus".equals(action)) {
            pageTitle = "BigCorp Trading Desk - Order Status";
        } else if ("viewOrders".equals(action)) {
            pageTitle = "BigCorp Trading Desk - Order List";
        } else if ("viewDashboard".equals(action) || "dashboard".equals(action)) {
            pageTitle = "BigCorp Trading Desk - Dashboard";
        }

        StringBuffer page = new StringBuffer();
        page.append("<HTML>\n");
        page.append("<HEAD>\n");
        page.append("<TITLE>").append(pageTitle).append("</TITLE>\n");
        page.append("<STYLE>\n");
        page.append("  body { background-color: #C0C0C0; font-family: 'Times New Roman', Times, serif; margin: 0; }\n");
        page.append("  .header { background-color: #000080; color: #FFFFFF; padding: 8px; font-size: 14pt; font-weight: bold; }\n");
        page.append("  .header img { vertical-align: middle; margin-right: 8px; }\n");
        page.append("  .content { padding: 15px; }\n");
        page.append("  .form-table { border: 2px outset #808080; background-color: #D4D0C8; padding: 10px; }\n");
        page.append("  .form-table td { padding: 4px 8px; font-size: 10pt; }\n");
        page.append("  .label { font-weight: bold; text-align: right; color: #000080; }\n");
        page.append("  input, select { border: 2px inset #808080; font-family: 'Times New Roman', Times, serif; font-size: 10pt; padding: 2px; }\n");
        page.append("  .submit-btn { border: 2px outset #808080; background-color: #D4D0C8; font-weight: bold; padding: 4px 16px; cursor: hand; }\n");
        page.append("  .data-table { border-collapse: collapse; border: 2px outset #808080; background-color: #FFFFFF; }\n");
        page.append("  .data-table TH { background-color: #000080; color: #FFFFFF; padding: 4px 8px; font-size: 10pt; border: 1px solid #404040; }\n");
        page.append("  .data-table TD { padding: 4px 8px; font-size: 10pt; border: 1px solid #C0C0C0; }\n");
        page.append("  .data-table TR.alt { background-color: #E8E8E8; }\n");
        page.append("  .detail-table { border: 2px outset #808080; background-color: #D4D0C8; }\n");
        page.append("  .detail-table TD { padding: 4px 8px; font-size: 10pt; }\n");
        page.append("  .detail-label { font-weight: bold; text-align: right; color: #000080; width: 150px; }\n");
        page.append("  .nav { background-color: #D4D0C8; border-bottom: 1px solid #808080; padding: 4px 8px; font-size: 9pt; }\n");
        page.append("  .nav A { color: #000080; text-decoration: none; }\n");
        page.append("  .nav A:hover { text-decoration: underline; }\n");
        page.append("  .footer { font-size: 8pt; color: #808080; text-align: center; padding: 10px; }\n");
        page.append("  A { color: #000080; }\n");
        page.append("  .required { color: #FF0000; font-weight: bold; }\n");
        page.append("  .status-NEW { color: #0000FF; font-weight: bold; }\n");
        page.append("  .status-VALIDATED { color: #008080; font-weight: bold; }\n");
        page.append("  .status-PRICED { color: #008000; font-weight: bold; }\n");
        page.append("  .status-FILLED { color: #006400; font-weight: bold; }\n");
        page.append("  .status-REJECTED { color: #FF0000; font-weight: bold; }\n");
        page.append("  .status-CANCELLED { color: #808080; font-weight: bold; }\n");
        page.append("</STYLE>\n");
        page.append("</HEAD>\n");
        page.append("<BODY>\n");

        // Header bar
        page.append("<TABLE WIDTH=\"100%\" CELLPADDING=\"0\" CELLSPACING=\"0\" BORDER=\"0\">\n");
        page.append("<TR><TD CLASS=\"header\">\n");
        page.append("  <FONT SIZE=\"4\"><B>BigCorp Trading Desk</B></FONT>\n");
        page.append("  <FONT SIZE=\"2\"> - Order Management System v2.0</FONT>\n");
        page.append("</TD></TR>\n");
        page.append("</TABLE>\n");

        // Navigation bar - uses .do URLs
        page.append("<DIV CLASS=\"nav\">\n");
        page.append("  <A HREF=\"dashboard.do\">Home</A> | \n");
        page.append("  <A HREF=\"submitOrder.do\">New Order</A> | \n");
        page.append("  <A HREF=\"viewOrders.do\">Order List</A> | \n");
        page.append("  <A HREF=\"dashboard.do\">Dashboard</A>\n");
        page.append("</DIV>\n");
        page.append("<HR SIZE=\"1\" NOSHADE>\n");

        // Content area
        page.append("<DIV CLASS=\"content\">\n");
        page.append(bodyHtml);
        page.append("</DIV>\n");

        // Footer
        page.append("<HR SIZE=\"1\" NOSHADE>\n");
        page.append("<DIV CLASS=\"footer\">\n");
        page.append("  <FONT SIZE=\"1\">BigCorp Trading Desk v2.0 | Front Controller | Internal Use Only | &copy; 2002 BigCorp Financial Services<BR>\n");
        page.append("  For support contact: helpdesk@bigcorp.com ext. 4357</FONT>\n");
        page.append("</DIV>\n");

        page.append("</BODY>\n");
        page.append("</HTML>");

        return page.toString();
    }
}
