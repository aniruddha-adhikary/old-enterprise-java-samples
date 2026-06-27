package com.bigcorp.notifications.sms;

import com.bigcorp.common.model.Notification;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

/**
 * Dispatches SMS notifications via external SMS gateway.
 * 
 * The SMS gateway uses a proprietary XML-over-HTTP protocol.
 * We POST an XML request and get an XML response back.
 * 
 * History:
 *   - 2000: Original integration with TelcoSMS v1 API
 *   - 2001: SMS gateway switched vendors, old format still works somehow
 *   - 2002: Added dev mode fallback when gateway URL is not configured
 * 
 * Known bug: The phone number formatting strips the leading '+'
 * character, but some international carriers need it. We've gotten
 * complaints from the Singapore office about this.
 * 
 * @author Dave (integration team)
 * @since 1.2
 */
public class SMSDispatcher {

    private String gatewayUrl;
    private String apiKey;
    private boolean devMode;
    private int connectTimeout;
    private int readTimeout;

    public SMSDispatcher() {
        loadConfig();
    }

    private void loadConfig() {
        try {
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("notification.properties");

            if (is != null) {
                props.load(is);
                is.close();

                gatewayUrl = props.getProperty("sms.gateway.url", "");
                apiKey = props.getProperty("sms.gateway.apikey", "");
                devMode = "true".equals(props.getProperty("dev.mode", "false"));
                connectTimeout = Integer.parseInt(props.getProperty("sms.connect.timeout", "5000"));
                readTimeout = Integer.parseInt(props.getProperty("sms.read.timeout", "10000"));
            } else {
                System.out.println("WARN: notification.properties not found, SMS dispatch in dev mode");
                gatewayUrl = "";
                apiKey = "";
                devMode = true;
                connectTimeout = 5000;
                readTimeout = 10000;
            }
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load SMS config: " + e.getMessage());
            devMode = true;
        }
    }

    /**
     * Send an SMS notification via the external gateway.
     * 
     * @throws Exception if the SMS could not be sent (will trigger retry)
     */
    public void sendSMS(Notification notif) throws Exception {
        String phoneNumber = formatPhoneNumber(notif.getRecipient());
        String messageText = notif.getBody();

        // Truncate to 160 chars - SMS limit
        // (We should really be splitting into multiple messages but
        //  the gateway supposedly handles that... supposedly)
        if (messageText != null && messageText.length() > 160) {
            messageText = messageText.substring(0, 157) + "...";
        }

        if (devMode || gatewayUrl == null || gatewayUrl.length() == 0) {
            // Dev mode - just log it
            System.out.println("--- SMS (dev mode) ---");
            System.out.println("To: " + phoneNumber);
            System.out.println("Message: " + messageText);
            System.out.println("--- END SMS ---");
            return;
        }

        // Build the request XML using string concatenation
        // (yes, we know about DOM. This is faster for simple XML and
        //  the gateway doesn't validate structure anyway)
        // SMS gateway switched vendors in 2001, old format still works somehow
        StringBuffer requestXml = new StringBuffer();
        requestXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        requestXml.append("<smsRequest>");
        requestXml.append("<apiKey>").append(apiKey).append("</apiKey>");
        requestXml.append("<destination>").append(phoneNumber).append("</destination>");
        requestXml.append("<message>").append(escapeXml(messageText)).append("</message>");
        requestXml.append("<sender>BIGCORP</sender>");
        requestXml.append("<priority>normal</priority>");
        requestXml.append("</smsRequest>");

        // Send HTTP POST to the gateway
        String responseXml = postToGateway(requestXml.toString());

        // Parse response to check success
        if (responseXml == null) {
            throw new Exception("No response from SMS gateway");
        }

        // Simple string parsing - look for <status>OK</status>
        // The response format is:
        //   <smsResponse><status>OK</status><messageId>12345</messageId></smsResponse>
        // or:
        //   <smsResponse><status>ERROR</status><error>Invalid number</error></smsResponse>
        if (responseXml.indexOf("<status>OK</status>") >= 0) {
            System.out.println("SMS sent successfully to " + phoneNumber 
                    + " [" + notif.getNotificationId() + "]");
        } else if (responseXml.indexOf("<status>ERROR</status>") >= 0) {
            // extract error message
            String error = extractXmlValue(responseXml, "error");
            throw new Exception("SMS gateway error: " + (error != null ? error : "unknown"));
        } else {
            // unexpected response format
            System.err.println("WARN: Unexpected SMS gateway response: " + responseXml);
            throw new Exception("Unexpected response from SMS gateway");
        }
    }

    /**
     * POST XML to the SMS gateway and return the response body.
     */
    private String postToGateway(String requestXml) throws Exception {
        URL url = new URL(gatewayUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
            conn.setRequestProperty("Accept", "text/xml");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);

            // Write request body
            OutputStream os = conn.getOutputStream();
            os.write(requestXml.getBytes("UTF-8"));
            os.flush();
            os.close();

            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("SMS gateway returned HTTP " + responseCode);
            }

            InputStream responseStream = conn.getInputStream();
            InputStreamReader reader = new InputStreamReader(responseStream, "UTF-8");
            StringBuffer response = new StringBuffer();
            char[] buf = new char[512];
            int charsRead;
            while ((charsRead = reader.read(buf)) != -1) {
                response.append(buf, 0, charsRead);
            }
            reader.close();

            return response.toString();

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Format phone number for the SMS gateway.
     * 
     * Bug: strips leading '+' but some carriers need it.
     * Ticket #4521 filed but not fixed because "it works for US numbers"
     * and "international is phase 2"
     */
    private String formatPhoneNumber(String phone) {
        if (phone == null) return "";

        // strip everything except digits
        StringBuffer cleaned = new StringBuffer();
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (c >= '0' && c <= '9') {
                cleaned.append(c);
            }
            // BUG: '+' is deliberately stripped here. International carriers
            // like those in Singapore need the + prefix. See ticket #4521.
        }

        return cleaned.toString();
    }

    /**
     * Basic XML escaping for text content.
     */
    private String escapeXml(String text) {
        if (text == null) return "";

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Extract a value from simple XML by tag name.
     * This is NOT a proper XML parser. It works for the simple
     * responses we get from the SMS gateway.
     */
    private String extractXmlValue(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        if (start < 0) return null;
        start += openTag.length();
        int end = xml.indexOf(closeTag, start);
        if (end < 0) return null;
        return xml.substring(start, end);
    }
}
