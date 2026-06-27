package com.bigcorp.common.xml;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Notification;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * DOM-based XML marshalling/unmarshalling for domain objects.
 * 
 * Yes, we know about JAXB. No, we can't use it. The version on the 
 * app server classpath conflicts with the one in our lib/ directory
 * and nobody has time to sort that out.
 * 
 * @author Bob
 * @since 1.0
 */
public class XmlHelper {

    // date format used in XML messages - DO NOT CHANGE
    // (settlement gateway and clearinghouse both depend on this format)
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private static DocumentBuilderFactory dbFactory;
    private static TransformerFactory tFactory;

    static {
        dbFactory = DocumentBuilderFactory.newInstance();
        tFactory = TransformerFactory.newInstance();
    }

    /**
     * Marshal a TradeOrder to XML string.
     */
    public static String marshalTradeOrder(TradeOrder order) {
        try {
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElement("tradeOrder");
            doc.appendChild(root);

            appendElement(doc, root, "orderId", order.getOrderId());
            appendElement(doc, root, "clientId", order.getClientId());
            appendElement(doc, root, "symbol", order.getSymbol());
            appendElement(doc, root, "quantity", String.valueOf(order.getQuantity()));
            appendElement(doc, root, "side", order.getSide());
            appendElement(doc, root, "price", String.valueOf(order.getPrice()));
            appendElement(doc, root, "requestedPrice", String.valueOf(order.getRequestedPrice()));
            appendElement(doc, root, "status", order.getStatus());
            appendElement(doc, root, "orderDate", formatDate(order.getOrderDate()));
            appendElement(doc, root, "lastModified", formatDate(order.getLastModified()));
            if (order.getNotes() != null) {
                appendElement(doc, root, "notes", order.getNotes());
            }

            return documentToString(doc);
        } catch (Exception e) {
            // this shouldn't happen but if it does, fall back to string builder
            System.err.println("WARN: DOM marshalling failed, using string fallback: " + e.getMessage());
            return StringXmlBuilder.buildTradeOrderXml(order);
        }
    }

    /**
     * Unmarshal a TradeOrder from XML string.
     */
    public static TradeOrder unmarshalTradeOrder(String xml) {
        try {
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();

            TradeOrder order = new TradeOrder();
            order.setOrderId(getElementText(root, "orderId"));
            order.setClientId(getElementText(root, "clientId"));
            order.setSymbol(getElementText(root, "symbol"));

            String qtyStr = getElementText(root, "quantity");
            if (qtyStr != null && qtyStr.length() > 0) {
                order.setQuantity(Integer.parseInt(qtyStr));
            }

            order.setSide(getElementText(root, "side"));

            String priceStr = getElementText(root, "price");
            if (priceStr != null && priceStr.length() > 0) {
                order.setPrice(Double.parseDouble(priceStr));
            }

            String reqPriceStr = getElementText(root, "requestedPrice");
            if (reqPriceStr != null && reqPriceStr.length() > 0) {
                order.setRequestedPrice(Double.parseDouble(reqPriceStr));
            }

            order.setStatus(getElementText(root, "status"));

            String dateStr = getElementText(root, "orderDate");
            if (dateStr != null && dateStr.length() > 0) {
                order.setOrderDate(parseDate(dateStr));
            }

            String modStr = getElementText(root, "lastModified");
            if (modStr != null && modStr.length() > 0) {
                order.setLastModified(parseDate(modStr));
            }

            order.setNotes(getElementText(root, "notes"));

            return order;
        } catch (Exception e) {
            System.err.println("ERROR: Failed to unmarshal TradeOrder XML: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Marshal a Notification to XML string.
     */
    public static String marshalNotification(Notification notif) {
        try {
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElement("notification");
            doc.appendChild(root);

            appendElement(doc, root, "notificationId", notif.getNotificationId());
            appendElement(doc, root, "type", notif.getType());
            appendElement(doc, root, "recipient", notif.getRecipient());
            appendElement(doc, root, "subject", notif.getSubject());
            appendElement(doc, root, "body", notif.getBody());
            appendElement(doc, root, "channel", notif.getChannel());
            appendElement(doc, root, "status", notif.getStatus());
            if (notif.getOrderId() != null) {
                appendElement(doc, root, "orderId", notif.getOrderId());
            }
            appendElement(doc, root, "createdDate", formatDate(notif.getCreatedDate()));

            return documentToString(doc);
        } catch (Exception e) {
            System.err.println("WARN: Failed to marshal Notification: " + e.getMessage());
            return null;
        }
    }

    /**
     * Unmarshal a Notification from XML string.
     */
    public static Notification unmarshalNotification(String xml) {
        try {
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();

            Notification notif = new Notification();
            notif.setNotificationId(getElementText(root, "notificationId"));
            notif.setType(getElementText(root, "type"));
            notif.setRecipient(getElementText(root, "recipient"));
            notif.setSubject(getElementText(root, "subject"));
            notif.setBody(getElementText(root, "body"));
            notif.setChannel(getElementText(root, "channel"));
            notif.setStatus(getElementText(root, "status"));
            notif.setOrderId(getElementText(root, "orderId"));

            String dateStr = getElementText(root, "createdDate");
            if (dateStr != null && dateStr.length() > 0) {
                notif.setCreatedDate(parseDate(dateStr));
            }

            return notif;
        } catch (Exception e) {
            System.err.println("ERROR: Failed to unmarshal Notification XML: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ---- utility methods ----

    private static void appendElement(Document doc, Element parent, String name, String value) {
        Element elem = doc.createElement(name);
        if (value != null) {
            elem.setTextContent(value);
        }
        parent.appendChild(elem);
    }

    private static String getElementText(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private static String documentToString(Document doc) {
        try {
            Transformer transformer = tFactory.newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize XML document", e);
        }
    }

    private static String formatDate(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        return sdf.format(date);
    }

    private static Date parseDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            return sdf.parse(dateStr);
        } catch (Exception e) {
            System.err.println("WARN: Could not parse date: " + dateStr);
            return null;
        }
    }
}
