package com.bigcorp.pricing.endpoint;

import com.bigcorp.common.db.ConnectionHelper;
import com.bigcorp.common.db.DatabaseBootstrap;
import com.bigcorp.pricing.service.PriceQuote;
import com.bigcorp.pricing.service.PricingServiceImpl;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * SOAP endpoint servlet for the Pricing Service.
 * 
 * Handles SOAP requests manually because we haven't migrated to Axis yet.
 * "We'll migrate to Axis when we upgrade the app server" - Bob, 2001
 * (we are still on WebLogic 6.1)
 * 
 * doPost() - processes SOAP requests (getQuote, getBatchQuotes)
 * doGet()  - returns the WSDL document
 * 
 * @author Dave
 * @author Bob (added batch quotes support)
 * @since 1.0
 */
public class PricingEndpointServlet extends HttpServlet {

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private PricingServiceImpl pricingService;
    private DocumentBuilderFactory dbFactory;

    /**
     * Initialize the servlet - set up DB and pricing service.
     */
    public void init() throws ServletException {
        super.init();
        System.out.println(timestamp() + " PricingEndpointServlet initializing...");

        try {
            ConnectionHelper.init();
            DatabaseBootstrap.bootstrap();
        } catch (Exception e) {
            System.err.println(timestamp() + " WARN: Database init failed, will use fallback prices: " + e.getMessage());
            // don't fail startup - we have hardcoded fallbacks
        }

        pricingService = new PricingServiceImpl();
        dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);

        System.out.println(timestamp() + " PricingEndpointServlet initialized OK");
    }

    /**
     * Handle SOAP requests.
     * Parses the SOAP envelope, figures out what operation is being called,
     * does the work, builds a SOAP response.
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println(timestamp() + " SOAP request received from " + request.getRemoteAddr());

        // read the request body
        StringBuffer requestBody = new StringBuffer();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            requestBody.append(line);
        }

        String soapRequest = requestBody.toString();
        System.out.println(timestamp() + " Request body length: " + soapRequest.length());

        String soapResponse;
        try {
            // parse the SOAP envelope
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(soapRequest)));

            // figure out which operation was called by looking at the SOAPAction header
            // or by inspecting the body element
            String soapAction = request.getHeader("SOAPAction");
            if (soapAction != null) {
                // strip quotes if present (some clients add them, some don't)
                soapAction = soapAction.replace("\"", "");
            }

            System.out.println(timestamp() + " SOAPAction: " + soapAction);

            if (soapAction != null && soapAction.indexOf("getBatchQuotes") >= 0) {
                soapResponse = handleBatchQuotes(doc);
            } else if (soapAction != null && soapAction.indexOf("getQuote") >= 0) {
                soapResponse = handleGetQuote(doc);
            } else {
                // try to figure it out from the body
                String bodyStr = soapRequest;
                if (bodyStr.indexOf("BatchQuoteRequest") >= 0) {
                    soapResponse = handleBatchQuotes(doc);
                } else if (bodyStr.indexOf("QuoteRequest") >= 0) {
                    soapResponse = handleGetQuote(doc);
                } else {
                    soapResponse = buildSoapFault("UNKNOWN_OP", 
                        "Could not determine operation from SOAPAction or body");
                }
            }

        } catch (Exception e) {
            System.err.println(timestamp() + " ERROR processing SOAP request: " + e.getMessage());
            e.printStackTrace();
            soapResponse = buildSoapFault("SERVER_ERROR", 
                "Internal server error: " + e.getMessage());
        }

        // send the response
        response.setContentType("text/xml; charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        out.write(soapResponse);
        out.flush();

        System.out.println(timestamp() + " SOAP response sent, length: " + soapResponse.length());
    }

    /**
     * Handle GET requests - return the WSDL.
     * The ?wsdl convention is what most SOAP toolkits expect.
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println(timestamp() + " WSDL request from " + request.getRemoteAddr());

        response.setContentType("text/xml; charset=utf-8");
        PrintWriter out = response.getWriter();

        // try to load WSDL from classpath first, then from filesystem
        InputStream wsdlStream = getClass().getClassLoader()
                .getResourceAsStream("wsdl/PricingService.wsdl");

        if (wsdlStream == null) {
            // try servlet context
            wsdlStream = getServletContext()
                    .getResourceAsStream("/WEB-INF/wsdl/PricingService.wsdl");
        }

        if (wsdlStream != null) {
            BufferedReader wsdlReader = new BufferedReader(new InputStreamReader(wsdlStream));
            String wsdlLine;
            while ((wsdlLine = wsdlReader.readLine()) != null) {
                out.println(wsdlLine);
            }
            wsdlReader.close();
            wsdlStream.close();
        } else {
            // this shouldn't happen but let's not blow up
            System.err.println(timestamp() + " ERROR: WSDL file not found!");
            out.write("<!-- WSDL file not found. This is a problem. -->");
        }

        out.flush();
    }

    // ---- SOAP operation handlers ----

    /**
     * Handle getQuote operation.
     */
    private String handleGetQuote(Document soapDoc) {
        // extract symbol from the SOAP body
        String symbol = extractSymbolFromBody(soapDoc);
        System.out.println(timestamp() + " getQuote for symbol: " + symbol);

        PriceQuote quote = pricingService.getQuote(symbol);

        if (quote == null) {
            return buildSoapFault("SYMBOL_NOT_FOUND", 
                "No quote available for symbol: " + symbol);
        }

        return buildQuoteResponse(quote);
    }

    /**
     * Handle getBatchQuotes operation.
     */
    private String handleBatchQuotes(Document soapDoc) {
        // extract symbols from the SOAP body
        String[] symbols = extractSymbolsFromBody(soapDoc);
        System.out.println(timestamp() + " getBatchQuotes for " + symbols.length + " symbols");

        PriceQuote[] quotes = pricingService.getBatchQuotes(symbols);

        return buildBatchQuoteResponse(quotes);
    }

    /**
     * Extract a single symbol element from the SOAP body.
     * This is ugly but it works.
     */
    private String extractSymbolFromBody(Document doc) {
        try {
            NodeList symbolNodes = doc.getElementsByTagName("symbol");
            if (symbolNodes.getLength() > 0) {
                return symbolNodes.item(0).getTextContent().trim();
            }
        } catch (Exception e) {
            System.err.println(timestamp() + " ERROR extracting symbol: " + e.getMessage());
        }
        return "";
    }

    /**
     * Extract multiple symbol elements from the SOAP body.
     */
    private String[] extractSymbolsFromBody(Document doc) {
        try {
            NodeList symbolNodes = doc.getElementsByTagName("symbol");
            // can't use generics here - we're targeting 1.4 (well, 1.8, but the style is 1.4)
            ArrayList list = new ArrayList();
            for (int i = 0; i < symbolNodes.getLength(); i++) {
                String sym = symbolNodes.item(i).getTextContent().trim();
                if (sym.length() > 0) {
                    list.add(sym);
                }
            }
            String[] result = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = (String) list.get(i);
            }
            return result;
        } catch (Exception e) {
            System.err.println(timestamp() + " ERROR extracting symbols: " + e.getMessage());
        }
        return new String[0];
    }

    // ---- SOAP response builders ----
    // Building SOAP XML by hand because we're not using Axis for the endpoint.
    // Yes, this is tedious. No, we can't use a template engine.

    /**
     * Build a SOAP response for a single quote.
     */
    private String buildQuoteResponse(PriceQuote quote) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);

        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"");
        sb.append(" xmlns:types=\"http://pricing.bigcorp.com/types\">");
        sb.append("<soap:Body>");
        sb.append("<types:QuoteResponse>");
        sb.append("<symbol>").append(nvl(quote.getSymbol())).append("</symbol>");
        sb.append("<bidPrice>").append(quote.getBidPrice()).append("</bidPrice>");
        sb.append("<askPrice>").append(quote.getAskPrice()).append("</askPrice>");
        sb.append("<lastPrice>").append(quote.getLastPrice()).append("</lastPrice>");
        sb.append("<currency>").append(nvl(quote.getCurrency())).append("</currency>");
        sb.append("<timestamp>").append(sdf.format(quote.getTimestamp())).append("</timestamp>");
        sb.append("</types:QuoteResponse>");
        sb.append("</soap:Body>");
        sb.append("</soap:Envelope>");
        return sb.toString();
    }

    /**
     * Build a SOAP response for batch quotes.
     */
    private String buildBatchQuoteResponse(PriceQuote[] quotes) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);

        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"");
        sb.append(" xmlns:types=\"http://pricing.bigcorp.com/types\">");
        sb.append("<soap:Body>");
        sb.append("<types:BatchQuoteResponse>");

        for (int i = 0; i < quotes.length; i++) {
            PriceQuote q = quotes[i];
            if (q != null) {
                sb.append("<quote>");
                sb.append("<symbol>").append(nvl(q.getSymbol())).append("</symbol>");
                sb.append("<bidPrice>").append(q.getBidPrice()).append("</bidPrice>");
                sb.append("<askPrice>").append(q.getAskPrice()).append("</askPrice>");
                sb.append("<lastPrice>").append(q.getLastPrice()).append("</lastPrice>");
                sb.append("<currency>").append(nvl(q.getCurrency())).append("</currency>");
                sb.append("<timestamp>").append(sdf.format(q.getTimestamp())).append("</timestamp>");
                sb.append("</quote>");
            }
        }

        sb.append("</types:BatchQuoteResponse>");
        sb.append("</soap:Body>");
        sb.append("</soap:Envelope>");
        return sb.toString();
    }

    /**
     * Build a SOAP fault response.
     */
    private String buildSoapFault(String code, String message) {
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">");
        sb.append("<soap:Body>");
        sb.append("<soap:Fault>");
        sb.append("<faultcode>soap:Server</faultcode>");
        sb.append("<faultstring>").append(nvl(message)).append("</faultstring>");
        sb.append("<detail>");
        sb.append("<errorCode>").append(nvl(code)).append("</errorCode>");
        sb.append("<errorMessage>").append(nvl(message)).append("</errorMessage>");
        sb.append("</detail>");
        sb.append("</soap:Fault>");
        sb.append("</soap:Body>");
        sb.append("</soap:Envelope>");
        return sb.toString();
    }

    // ---- utility methods ----

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
