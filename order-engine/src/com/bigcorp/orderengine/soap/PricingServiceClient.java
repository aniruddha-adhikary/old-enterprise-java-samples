package com.bigcorp.orderengine.soap;

import com.bigcorp.common.db.ConnectionHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Client for the BigCorp Pricing SOAP Service.
 * 
 * Calls the pricing-service web app via hand-crafted SOAP/XML over HTTP.
 * We tried using Axis-generated stubs but they kept conflicting with 
 * the JAXP classes on the WebLogic classpath, so we went back to 
 * doing it by hand.
 * 
 * If the SOAP call fails (service not running, network issues), we 
 * fall back to querying the PRICING_CACHE table directly. This is 
 * "good enough" for demo purposes.
 * 
 * // TODO: use Axis generated stubs instead of manual SOAP
 * 
 * @author Bob
 * @author Mike (DB fallback - 2001-09-14)
 * @since 1.1
 */
public class PricingServiceClient {

    // SOAP endpoint - hardcoded because "we only have one pricing server"
    private static final String PRICING_SERVICE_URL = 
            "http://localhost:8080/pricing-service/services/PricingService";

    // SOAP action header
    private static final String SOAP_ACTION = "getQuote";

    // connection timeout in ms - increased from 5000 after JIRA-2089
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 15000;

    /**
     * Get a price quote for the given symbol.
     * 
     * Tries SOAP first, falls back to DB lookup.
     * Returns the ASK price for BUY orders (we always use ASK 
     * because "that's what the traders expect").
     * 
     * @param symbol the stock ticker symbol
     * @return the quoted price, or -1.0 if not found
     */
    public double getQuote(String symbol) {
        // try SOAP call first
        try {
            double soapPrice = callSoapService(symbol);
            if (soapPrice > 0) {
                System.out.println("Got price from SOAP service for " + symbol + ": " + soapPrice);
                return soapPrice;
            }
        } catch (Exception e) {
            // SOAP call failed - this is expected when pricing-service isn't running
            System.err.println("WARN: SOAP pricing call failed for " + symbol + 
                    ", falling back to DB: " + e.getMessage());
        }

        // fallback: query PRICING_CACHE table directly
        return getQuoteFromDatabase(symbol);
    }

    /**
     * Call the SOAP pricing service.
     * 
     * We build the SOAP envelope by hand because SAX was too complicated
     * for this simple case and nobody wanted to learn JAXB.
     */
    private double callSoapService(String symbol) throws Exception {
        // build the SOAP request envelope manually
        // (yes, we know about SAAJ. no, we're not using it.)
        StringBuffer soapEnvelope = new StringBuffer();
        soapEnvelope.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        soapEnvelope.append("<soapenv:Envelope ");
        soapEnvelope.append("xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ");
        soapEnvelope.append("xmlns:pric=\"http://bigcorp.com/pricing\">");
        soapEnvelope.append("<soapenv:Header/>");
        soapEnvelope.append("<soapenv:Body>");
        soapEnvelope.append("<pric:getQuote>");
        soapEnvelope.append("<pric:symbol>");
        soapEnvelope.append(symbol);
        soapEnvelope.append("</pric:symbol>");
        soapEnvelope.append("</pric:getQuote>");
        soapEnvelope.append("</soapenv:Body>");
        soapEnvelope.append("</soapenv:Envelope>");

        String requestXml = soapEnvelope.toString();

        // make the HTTP call
        URL url = new URL(PRICING_SERVICE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        conn.setRequestProperty("SOAPAction", SOAP_ACTION);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setDoOutput(true);

        // send the SOAP request
        OutputStream os = conn.getOutputStream();
        os.write(requestXml.getBytes("UTF-8"));
        os.flush();
        os.close();

        // check response code
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("SOAP service returned HTTP " + responseCode);
        }

        // read the response
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuffer response = new StringBuffer();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        String responseXml = response.toString();

        // parse the price from the SOAP response using indexOf/substring
        // (SAX was too complicated for this - Bob)
        // Expected response contains: <price>123.45</price>
        return parsePriceFromSoapResponse(responseXml);
    }

    /**
     * Parse the price value from the SOAP response XML.
     * 
     * Uses indexOf/substring because "we don't need a full XML parser
     * for one element" and "it works fine as long as the response 
     * format doesn't change" (famous last words).
     */
    private double parsePriceFromSoapResponse(String responseXml) {
        // look for <price> tag in response
        String startTag = "<price>";
        String endTag = "</price>";

        int startIdx = responseXml.indexOf(startTag);
        if (startIdx < 0) {
            // try with namespace prefix - sometimes Axis adds one
            startTag = "<ns1:price>";
            endTag = "</ns1:price>";
            startIdx = responseXml.indexOf(startTag);
        }

        // JIRA-2201: also try ns2 prefix (seen on WebLogic 8.1)
        if (startIdx < 0) {
            startTag = "<ns2:price>";
            endTag = "</ns2:price>";
            startIdx = responseXml.indexOf(startTag);
        }

        if (startIdx < 0) {
            System.err.println("WARN: Could not find <price> element in SOAP response");
            System.err.println("Response was: " + responseXml);
            return -1.0;
        }

        int valueStart = startIdx + startTag.length();
        int valueEnd = responseXml.indexOf(endTag, valueStart);

        if (valueEnd < 0) {
            System.err.println("WARN: Malformed SOAP response - no closing price tag");
            return -1.0;
        }

        String priceStr = responseXml.substring(valueStart, valueEnd).trim();
        try {
            return Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            System.err.println("WARN: Could not parse price value: " + priceStr);
            return -1.0;
        }
    }

    /**
     * Fallback: get the price from PRICING_CACHE table.
     * 
     * Added by Mike when the SOAP service kept crashing during 
     * the load test in Sept 2001. "Temporary" solution.
     */
    private double getQuoteFromDatabase(String symbol) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionHelper.getConnection();
            stmt = conn.createStatement();

            // use ASK_PRICE since that's what we quote for buy orders
            // for sell orders we should use BID_PRICE but nobody has
            // asked for that yet so... - Mike
            String sql = "SELECT ASK_PRICE FROM PRICING_CACHE WHERE SYMBOL = '" + symbol + "'";
            rs = stmt.executeQuery(sql);

            if (rs.next()) {
                double price = rs.getDouble("ASK_PRICE");
                System.out.println("Got price from DB cache for " + symbol + ": " + price);
                return price;
            }

            System.err.println("WARN: No pricing data found for symbol: " + symbol);
            return -1.0;

        } catch (Exception e) {
            System.err.println("ERROR: DB pricing lookup failed for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            return -1.0;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(stmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }
}
