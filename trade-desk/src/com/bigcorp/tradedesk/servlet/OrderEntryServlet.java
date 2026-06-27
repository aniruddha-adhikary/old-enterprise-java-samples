package com.bigcorp.tradedesk.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.xml.XmlHelper;
import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.common.mq.MessageQueueHelper;
import com.bigcorp.tradedesk.mq.TradeMessageProducer;

/**
 * Order entry servlet. Displays the trade order form and processes
 * submitted orders.
 * 
 * NOTE: The HTML is inline because "we'll add JSP later" (Bob, 1999-11-12).
 * That was three years ago.
 * 
 * @author Bob (original), Dave (form validation), Karen (dropdown fix)
 * @since 1.0
 * @version 2.1
 */
public class OrderEntryServlet extends HttpServlet {

    // TODO: make this configurable via web.xml init-param
    private TradeMessageProducer producer;

    /**
     * Initialize database and MQ connections on servlet startup.
     * This is called by the container.
     */
    public void init() throws ServletException {
        super.init();
        try {
            ConnectionHelper.init();
            DatabaseBootstrap.bootstrap();
            MessageQueueHelper.init();
            producer = new TradeMessageProducer();
            System.out.println("OrderEntryServlet initialized successfully.");
        } catch (Exception e) {
            System.err.println("ERROR: OrderEntryServlet init failed: " + e.getMessage());
            e.printStackTrace();
            throw new ServletException("Failed to initialize OrderEntryServlet", e);
        }
    }

    /**
     * Serve the order entry HTML form.
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        // HACK: fix for JIRA-1287 - IE6 caching issue
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");

        StringBuffer html = new StringBuffer();
        html.append("<HTML>\n");
        html.append("<HEAD>\n");
        html.append("<TITLE>BigCorp Trading Desk - New Order Entry</TITLE>\n");
        html.append("<STYLE>\n");
        html.append("  body { background-color: #C0C0C0; font-family: 'Times New Roman', Times, serif; margin: 0; }\n");
        html.append("  .header { background-color: #000080; color: #FFFFFF; padding: 8px; font-size: 14pt; font-weight: bold; }\n");
        html.append("  .header img { vertical-align: middle; margin-right: 8px; }\n");
        html.append("  .content { padding: 15px; }\n");
        html.append("  .form-table { border: 2px outset #808080; background-color: #D4D0C8; padding: 10px; }\n");
        html.append("  .form-table td { padding: 4px 8px; font-size: 10pt; }\n");
        html.append("  .label { font-weight: bold; text-align: right; color: #000080; }\n");
        html.append("  input, select { border: 2px inset #808080; font-family: 'Times New Roman', Times, serif; font-size: 10pt; padding: 2px; }\n");
        html.append("  .submit-btn { border: 2px outset #808080; background-color: #D4D0C8; font-weight: bold; padding: 4px 16px; cursor: hand; }\n");
        html.append("  .nav { background-color: #D4D0C8; border-bottom: 1px solid #808080; padding: 4px 8px; font-size: 9pt; }\n");
        html.append("  .nav A { color: #000080; text-decoration: none; }\n");
        html.append("  .nav A:hover { text-decoration: underline; }\n");
        html.append("  .footer { font-size: 8pt; color: #808080; text-align: center; padding: 10px; }\n");
        html.append("  .required { color: #FF0000; font-weight: bold; }\n");
        html.append("</STYLE>\n");
        html.append("</HEAD>\n");
        html.append("<BODY>\n");

        // Header bar
        html.append("<TABLE WIDTH=\"100%\" CELLPADDING=\"0\" CELLSPACING=\"0\" BORDER=\"0\">\n");
        html.append("<TR><TD CLASS=\"header\">\n");
        html.append("  <FONT SIZE=\"4\"><B>BigCorp Trading Desk</B></FONT>\n");
        html.append("  <FONT SIZE=\"2\"> - Order Entry System v2.1</FONT>\n");
        html.append("</TD></TR>\n");
        html.append("</TABLE>\n");

        // Navigation bar
        html.append("<DIV CLASS=\"nav\">\n");
        html.append("  <A HREF=\"/trade-desk/\">Home</A> | \n");
        html.append("  <B>New Order</B> | \n");
        html.append("  <A HREF=\"/trade-desk/order/status\">Order Status</A>\n");
        html.append("</DIV>\n");
        html.append("<HR SIZE=\"1\" NOSHADE>\n");

        // Check for error or success messages
        String errorMsg = request.getParameter("error");
        String successMsg = request.getParameter("success");

        html.append("<DIV CLASS=\"content\">\n");

        if (errorMsg != null && errorMsg.length() > 0) {
            html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#FFCCCC\" BORDER=\"1\" BORDERCOLOR=\"#FF0000\"><TR><TD>\n");
            html.append("<FONT COLOR=\"#FF0000\"><B>Error:</B> ").append(errorMsg).append("</FONT>\n");
            html.append("</TD></TR></TABLE><BR>\n");
        }
        if (successMsg != null && successMsg.length() > 0) {
            html.append("<TABLE WIDTH=\"100%\" BGCOLOR=\"#CCFFCC\" BORDER=\"1\" BORDERCOLOR=\"#008000\"><TR><TD>\n");
            html.append("<FONT COLOR=\"#008000\"><B>Success:</B> ").append(successMsg).append("</FONT>\n");
            html.append("</TD></TR></TABLE><BR>\n");
        }

        html.append("<FONT SIZE=\"3\"><B>Submit New Trade Order</B></FONT>\n");
        html.append("<BR><FONT SIZE=\"2\" COLOR=\"#808080\">Fields marked with <FONT COLOR=\"#FF0000\">*</FONT> are required</FONT>\n");
        html.append("<BR><BR>\n");

        // Order entry form
        html.append("<FORM METHOD=\"POST\" ACTION=\"/trade-desk/order/entry\">\n");
        html.append("<TABLE CLASS=\"form-table\" CELLPADDING=\"4\" CELLSPACING=\"2\">\n");

        // Client ID
        html.append("<TR>\n");
        html.append("  <TD CLASS=\"label\">Client ID <SPAN CLASS=\"required\">*</SPAN></TD>\n");
        html.append("  <TD>\n");
        html.append("    <SELECT NAME=\"clientId\">\n");
        html.append("      <OPTION VALUE=\"\">-- Select Client --</OPTION>\n");
        html.append("      <OPTION VALUE=\"C001\">C001 - Acme Trading LLC</OPTION>\n");
        html.append("      <OPTION VALUE=\"C002\">C002 - Henderson Capital</OPTION>\n");
        html.append("      <OPTION VALUE=\"C003\">C003 - Smith &amp; Associates</OPTION>\n");
        html.append("      <OPTION VALUE=\"C004\">C004 - MegaFund Inc</OPTION>\n");
        html.append("      <OPTION VALUE=\"C005\">C005 - Pinnacle Investments</OPTION>\n");
        html.append("    </SELECT>\n");
        html.append("  </TD>\n");
        html.append("</TR>\n");

        // Symbol
        html.append("<TR>\n");
        html.append("  <TD CLASS=\"label\">Symbol <SPAN CLASS=\"required\">*</SPAN></TD>\n");
        html.append("  <TD>\n");
        html.append("    <SELECT NAME=\"symbol\">\n");
        html.append("      <OPTION VALUE=\"\">-- Select Symbol --</OPTION>\n");
        html.append("      <OPTION VALUE=\"MSFT\">MSFT - Microsoft Corp</OPTION>\n");
        html.append("      <OPTION VALUE=\"IBM\">IBM - Int'l Business Machines</OPTION>\n");
        html.append("      <OPTION VALUE=\"ORCL\">ORCL - Oracle Corp</OPTION>\n");
        html.append("      <OPTION VALUE=\"SUNW\">SUNW - Sun Microsystems</OPTION>\n");
        html.append("      <OPTION VALUE=\"CSCO\">CSCO - Cisco Systems</OPTION>\n");
        html.append("      <OPTION VALUE=\"INTC\">INTC - Intel Corp</OPTION>\n");
        html.append("      <OPTION VALUE=\"DELL\">DELL - Dell Computer</OPTION>\n");
        html.append("    </SELECT>\n");
        html.append("  </TD>\n");
        html.append("</TR>\n");

        // Quantity
        html.append("<TR>\n");
        html.append("  <TD CLASS=\"label\">Quantity <SPAN CLASS=\"required\">*</SPAN></TD>\n");
        html.append("  <TD><INPUT TYPE=\"text\" NAME=\"quantity\" SIZE=\"10\" MAXLENGTH=\"8\"></TD>\n");
        html.append("</TR>\n");

        // Side (BUY/SELL)
        html.append("<TR>\n");
        html.append("  <TD CLASS=\"label\">Side <SPAN CLASS=\"required\">*</SPAN></TD>\n");
        html.append("  <TD>\n");
        html.append("    <INPUT TYPE=\"radio\" NAME=\"side\" VALUE=\"BUY\" CHECKED> BUY\n");
        html.append("    &nbsp;&nbsp;\n");
        html.append("    <INPUT TYPE=\"radio\" NAME=\"side\" VALUE=\"SELL\"> SELL\n");
        html.append("  </TD>\n");
        html.append("</TR>\n");

        // Price
        html.append("<TR>\n");
        html.append("  <TD CLASS=\"label\">Price <SPAN CLASS=\"required\">*</SPAN></TD>\n");
        html.append("  <TD><INPUT TYPE=\"text\" NAME=\"requestedPrice\" SIZE=\"12\" MAXLENGTH=\"12\">\n");
        html.append("  <FONT SIZE=\"1\">(limit price in USD)</FONT></TD>\n");
        html.append("</TR>\n");

        // Submit button row
        html.append("<TR>\n");
        html.append("  <TD>&nbsp;</TD>\n");
        html.append("  <TD>\n");
        html.append("    <BR>\n");
        html.append("    <INPUT TYPE=\"submit\" VALUE=\"Submit Order\" CLASS=\"submit-btn\">\n");
        html.append("    &nbsp;\n");
        html.append("    <INPUT TYPE=\"reset\" VALUE=\"Clear Form\" CLASS=\"submit-btn\">\n");
        html.append("  </TD>\n");
        html.append("</TR>\n");

        html.append("</TABLE>\n");
        html.append("</FORM>\n");

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
     * Process the submitted order form.
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String clientId = request.getParameter("clientId");
        String symbol = request.getParameter("symbol");
        String quantityStr = request.getParameter("quantity");
        String side = request.getParameter("side");
        String priceStr = request.getParameter("requestedPrice");

        // TODO: add proper validation (JIRA-2045)
        // basic validation
        if (clientId == null || clientId.trim().length() == 0 ||
            symbol == null || symbol.trim().length() == 0 ||
            quantityStr == null || quantityStr.trim().length() == 0 ||
            side == null || side.trim().length() == 0 ||
            priceStr == null || priceStr.trim().length() == 0) {

            response.sendRedirect("/trade-desk/order/entry?error=All+fields+are+required");
            return;
        }

        int quantity = 0;
        double requestedPrice = 0.0;
        try {
            quantity = Integer.parseInt(quantityStr.trim());
            requestedPrice = Double.parseDouble(priceStr.trim());
        } catch (NumberFormatException e) {
            response.sendRedirect("/trade-desk/order/entry?error=Invalid+number+format");
            return;
        }

        // validation - quantity must be positive
        if (quantity <= 0) {
            response.sendRedirect("/trade-desk/order/entry?error=Quantity+must+be+greater+than+zero");
            return;
        }

        // Create the trade order
        TradeOrder order = new TradeOrder();
        String orderId = "ORD-" + System.currentTimeMillis();
        order.setOrderId(orderId);
        order.setClientId(clientId.trim());
        order.setSymbol(symbol.trim());
        order.setQuantity(quantity);
        order.setSide(side.trim());
        order.setRequestedPrice(requestedPrice);
        order.setPrice(requestedPrice); // initial price = requested, pricing engine will update
        order.setNotes("Submitted via Trade Desk web form");

        try {
            // marshal to XML and send to MQ
            // also inserts into database (see TradeMessageProducer)
            producer.submitOrder(order);

            System.out.println("Order submitted: " + orderId + " " + side + " " + quantity + " " + symbol + " @" + requestedPrice);

            // redirect to order status page
            response.sendRedirect("/trade-desk/order/status?orderId=" + orderId + "&msg=Order+submitted+successfully");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to submit order: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("/trade-desk/order/entry?error=Failed+to+submit+order:+" + e.getMessage());
        }
    }
}
