package com.bigcorp.common.dto;

import com.bigcorp.common.model.TradeOrder;

import java.io.Serializable;
import java.text.SimpleDateFormat;

/**
 * Transfer Object (Value Object) for trade orders.
 * 
 * This is the "official" way to pass order data between tiers
 * per the J2EE Design Patterns book. In practice, most code still
 * uses TradeOrder directly because "it's easier."
 *
 * The DTO was added when the architecture team mandated "proper
 * separation of concerns." Half the codebase uses it, half doesn't.
 *
 * Implements Serializable because it might need to travel over RMI
 * (it never does, but "just in case").
 *
 * @author Bob (after the architecture review of 2001-Q3)
 * @since 1.3
 */
public class OrderTransferObject implements Serializable {

    private static final long serialVersionUID = 200L;

    /** date format for display strings - NOT thread-safe but we don't care (single-threaded batch) */
    private static final String DISPLAY_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private String orderId;
    private String clientId;
    private String clientName; // denormalized for display convenience
    private String symbol;
    private int quantity;
    private String side;
    private double price;
    private double requestedPrice;
    private String status;
    private String orderDateStr; // formatted as string for display
    private String notes;

    public OrderTransferObject() {
        // default constructor required for serialization
    }

    // --- Getters and Setters ---

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getRequestedPrice() {
        return requestedPrice;
    }

    public void setRequestedPrice(double requestedPrice) {
        this.requestedPrice = requestedPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderDateStr() {
        return orderDateStr;
    }

    public void setOrderDateStr(String orderDateStr) {
        this.orderDateStr = orderDateStr;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Convert from domain object.
     * Does NOT populate clientName - use TransferObjectAssembler for that.
     */
    public static OrderTransferObject fromTradeOrder(TradeOrder order) {
        if (order == null) {
            return null;
        }

        OrderTransferObject to = new OrderTransferObject();
        to.setOrderId(order.getOrderId());
        to.setClientId(order.getClientId());
        to.setSymbol(order.getSymbol());
        to.setQuantity(order.getQuantity());
        to.setSide(order.getSide());
        to.setPrice(order.getPrice());
        to.setRequestedPrice(order.getRequestedPrice());
        to.setStatus(order.getStatus());
        to.setNotes(order.getNotes());

        // format date as string for display
        if (order.getOrderDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(DISPLAY_DATE_FORMAT);
            to.setOrderDateStr(sdf.format(order.getOrderDate()));
        }

        return to;
    }

    /**
     * Convert to XML string (hand-built because "JAXB is overkill").
     * 
     * NOTE: No escaping of special characters. If the notes field
     * contains XML (which it sometimes does - don't ask), this will
     * produce malformed XML. We know. It's on the backlog (JIRA-3612).
     */
    public String toXml() {
        StringBuffer sb = new StringBuffer();
        sb.append("<orderTO>\n");
        sb.append("  <orderId>").append(orderId != null ? orderId : "").append("</orderId>\n");
        sb.append("  <clientId>").append(clientId != null ? clientId : "").append("</clientId>\n");
        sb.append("  <clientName>").append(clientName != null ? clientName : "").append("</clientName>\n");
        sb.append("  <symbol>").append(symbol != null ? symbol : "").append("</symbol>\n");
        sb.append("  <quantity>").append(quantity).append("</quantity>\n");
        sb.append("  <side>").append(side != null ? side : "").append("</side>\n");
        sb.append("  <price>").append(price).append("</price>\n");
        sb.append("  <requestedPrice>").append(requestedPrice).append("</requestedPrice>\n");
        sb.append("  <status>").append(status != null ? status : "").append("</status>\n");
        sb.append("  <orderDate>").append(orderDateStr != null ? orderDateStr : "").append("</orderDate>\n");
        sb.append("  <notes>").append(notes != null ? notes : "").append("</notes>\n");
        sb.append("</orderTO>");
        return sb.toString();
    }

    /**
     * Parse from XML string.
     * 
     * Uses indexOf/substring because "we don't want to pull in a whole
     * XML parser dependency for one class" (Bob's words, 2001-Q3).
     * Yes, we already have DOM elsewhere in the project. No, that doesn't matter.
     */
    public static OrderTransferObject fromXml(String xml) {
        if (xml == null || xml.length() == 0) {
            return null;
        }

        OrderTransferObject to = new OrderTransferObject();
        to.setOrderId(extractTagValue(xml, "orderId"));
        to.setClientId(extractTagValue(xml, "clientId"));
        to.setClientName(extractTagValue(xml, "clientName"));
        to.setSymbol(extractTagValue(xml, "symbol"));
        to.setSide(extractTagValue(xml, "side"));
        to.setStatus(extractTagValue(xml, "status"));
        to.setOrderDateStr(extractTagValue(xml, "orderDate"));
        to.setNotes(extractTagValue(xml, "notes"));

        String qtyStr = extractTagValue(xml, "quantity");
        if (qtyStr != null && qtyStr.length() > 0) {
            try {
                to.setQuantity(Integer.parseInt(qtyStr));
            } catch (NumberFormatException e) {
                // bad data from upstream - happens more than you'd think
            }
        }

        String priceStr = extractTagValue(xml, "price");
        if (priceStr != null && priceStr.length() > 0) {
            try {
                to.setPrice(Double.parseDouble(priceStr));
            } catch (NumberFormatException e) {
                // see above
            }
        }

        String reqPriceStr = extractTagValue(xml, "requestedPrice");
        if (reqPriceStr != null && reqPriceStr.length() > 0) {
            try {
                to.setRequestedPrice(Double.parseDouble(reqPriceStr));
            } catch (NumberFormatException e) {
                // see above
            }
        }

        return to;
    }

    /**
     * Extract text content between XML tags using string operations.
     * This is terrible and we know it. But it works for our simple flat XML.
     */
    private static String extractTagValue(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        int end = xml.indexOf(closeTag);
        if (start >= 0 && end >= 0) {
            return xml.substring(start + openTag.length(), end);
        }
        return null;
    }

    public String toString() {
        return "OrderTO[" + orderId + " " + side + " " + quantity + " " + symbol + " @" + price + " " + status + "]";
    }
}
