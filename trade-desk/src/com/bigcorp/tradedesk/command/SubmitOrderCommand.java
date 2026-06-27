package com.bigcorp.tradedesk.command;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bigcorp.tradedesk.delegate.OrderServiceDelegate;

/**
 * Command to submit a new trade order.
 * Takes form parameters, validates them (sort of), and delegates
 * to OrderServiceDelegate for the actual MQ send + DB insert.
 *
 * If the request is GET, shows the order entry form.
 * If POST, processes the submission.
 *
 * @author Bob
 * @since 2.0
 */
public class SubmitOrderCommand implements Command {

    private OrderServiceDelegate delegate = new OrderServiceDelegate();

    public String execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            return doSubmit(request, response);
        } else {
            return showForm(request);
        }
    }

    /**
     * Show the order entry form. Same fields as OrderEntryServlet
     * but routed through the Front Controller.
     */
    private String showForm(HttpServletRequest request) {
        String errorMsg = request.getParameter("error");
        String successMsg = request.getParameter("success");

        StringBuffer html = new StringBuffer();

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

        // POST to self via .do URL
        html.append("<FORM METHOD=\"POST\" ACTION=\"submitOrder.do\">\n");
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

        return html.toString();
    }

    /**
     * Process the order submission. Validates input and delegates
     * to OrderServiceDelegate.
     */
    private String doSubmit(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String clientId = request.getParameter("clientId");
        String symbol = request.getParameter("symbol");
        String quantityStr = request.getParameter("quantity");
        String side = request.getParameter("side");
        String priceStr = request.getParameter("requestedPrice");

        // basic validation - same as OrderEntryServlet
        if (clientId == null || clientId.trim().length() == 0 ||
            symbol == null || symbol.trim().length() == 0 ||
            quantityStr == null || quantityStr.trim().length() == 0 ||
            side == null || side.trim().length() == 0 ||
            priceStr == null || priceStr.trim().length() == 0) {

            response.sendRedirect("submitOrder.do?error=All+fields+are+required");
            return null; // redirect, no body needed
        }

        int quantity = 0;
        double requestedPrice = 0.0;
        try {
            quantity = Integer.parseInt(quantityStr.trim());
            requestedPrice = Double.parseDouble(priceStr.trim());
        } catch (NumberFormatException e) {
            response.sendRedirect("submitOrder.do?error=Invalid+number+format");
            return null;
        }

        if (quantity <= 0) {
            response.sendRedirect("submitOrder.do?error=Quantity+must+be+greater+than+zero");
            return null;
        }

        // delegate does the heavy lifting
        String orderId = delegate.submitOrder(clientId.trim(), symbol.trim(),
                quantity, side.trim(), requestedPrice);

        System.out.println("[FrontController] Order submitted via command: " + orderId);

        // redirect to status view
        response.sendRedirect("viewStatus.do?orderId=" + orderId + "&msg=Order+submitted+successfully");
        return null;
    }
}
