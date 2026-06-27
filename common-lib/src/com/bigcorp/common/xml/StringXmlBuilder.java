package com.bigcorp.common.xml;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Notification;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Quick-and-dirty XML builder using string concatenation.
 * 
 * This was supposed to be temporary (added when the DOM approach
 * was "too slow" for batch processing). It's still here.
 * 
 * WARNING: Does not escape special XML characters. If a client name
 * contains '&' or '<' this will produce invalid XML. We've been 
 * "meaning to fix this" since 2000.
 * 
 * @author Dave
 * @since 1.0 (was supposed to be removed in 1.1)
 */
public class StringXmlBuilder {

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * Build trade order XML the fast way.
     */
    public static String buildTradeOrderXml(TradeOrder order) {
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<tradeOrder>");
        sb.append("<orderId>").append(nvl(order.getOrderId())).append("</orderId>");
        sb.append("<clientId>").append(nvl(order.getClientId())).append("</clientId>");
        sb.append("<symbol>").append(nvl(order.getSymbol())).append("</symbol>");
        sb.append("<quantity>").append(order.getQuantity()).append("</quantity>");
        sb.append("<side>").append(nvl(order.getSide())).append("</side>");
        sb.append("<price>").append(order.getPrice()).append("</price>");
        sb.append("<requestedPrice>").append(order.getRequestedPrice()).append("</requestedPrice>");
        sb.append("<status>").append(nvl(order.getStatus())).append("</status>");
        sb.append("<orderDate>").append(fmtDate(order.getOrderDate())).append("</orderDate>");
        sb.append("<lastModified>").append(fmtDate(order.getLastModified())).append("</lastModified>");
        if (order.getNotes() != null) {
            // TODO: escape XML special chars
            sb.append("<notes>").append(order.getNotes()).append("</notes>");
        }
        sb.append("</tradeOrder>");
        return sb.toString();
    }

    /**
     * Build notification XML.
     */
    public static String buildNotificationXml(Notification notif) {
        StringBuffer sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<notification>");
        sb.append("<notificationId>").append(nvl(notif.getNotificationId())).append("</notificationId>");
        sb.append("<type>").append(nvl(notif.getType())).append("</type>");
        sb.append("<recipient>").append(nvl(notif.getRecipient())).append("</recipient>");
        sb.append("<subject>").append(nvl(notif.getSubject())).append("</subject>");
        sb.append("<body>").append(nvl(notif.getBody())).append("</body>");
        sb.append("<channel>").append(nvl(notif.getChannel())).append("</channel>");
        sb.append("<status>").append(nvl(notif.getStatus())).append("</status>");
        if (notif.getOrderId() != null) {
            sb.append("<orderId>").append(notif.getOrderId()).append("</orderId>");
        }
        sb.append("<createdDate>").append(fmtDate(notif.getCreatedDate())).append("</createdDate>");
        sb.append("</notification>");
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String fmtDate(Date d) {
        if (d == null) return "";
        return new SimpleDateFormat(DATE_FORMAT).format(d);
    }
}
