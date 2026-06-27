package com.bigcorp.common.dto;

import com.bigcorp.common.model.Notification;

import java.io.Serializable;
import java.text.SimpleDateFormat;

/**
 * Transfer Object for notifications.
 * Added because the notification gateway team wanted
 * "a clean interface" (their words, not ours).
 *
 * In retrospect this is 95% identical to the Notification domain
 * object. But removing it would break the "architecture diagram"
 * that management likes to show to auditors.
 *
 * @author Karen
 * @since 1.3
 */
public class NotificationTransferObject implements Serializable {

    private static final long serialVersionUID = 202L;

    private static final String DISPLAY_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private String notificationId;
    private String type;
    private String recipient;
    private String subject;
    private String body;
    private String channel;
    private String status;
    private String orderId;
    private String createdDateStr;
    private String sentDateStr;

    public NotificationTransferObject() {
        // default constructor
    }

    // --- Getters and Setters ---

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCreatedDateStr() {
        return createdDateStr;
    }

    public void setCreatedDateStr(String createdDateStr) {
        this.createdDateStr = createdDateStr;
    }

    public String getSentDateStr() {
        return sentDateStr;
    }

    public void setSentDateStr(String sentDateStr) {
        this.sentDateStr = sentDateStr;
    }

    /**
     * Build from domain Notification object.
     */
    public static NotificationTransferObject fromNotification(Notification notif) {
        if (notif == null) {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(DISPLAY_DATE_FORMAT);

        NotificationTransferObject to = new NotificationTransferObject();
        to.setNotificationId(notif.getNotificationId());
        to.setType(notif.getType());
        to.setRecipient(notif.getRecipient());
        to.setSubject(notif.getSubject());
        to.setBody(notif.getBody());
        to.setChannel(notif.getChannel());
        to.setStatus(notif.getStatus());
        to.setOrderId(notif.getOrderId());

        if (notif.getCreatedDate() != null) {
            to.setCreatedDateStr(sdf.format(notif.getCreatedDate()));
        }
        if (notif.getSentDate() != null) {
            to.setSentDateStr(sdf.format(notif.getSentDate()));
        }

        return to;
    }

    /**
     * Convert to XML.
     * 
     * WARNING: the body field may contain HTML (for email notifications).
     * This means the resulting XML may not be well-formed. This is a
     * known issue (JIRA-3588) that nobody has prioritized fixing.
     */
    public String toXml() {
        StringBuffer sb = new StringBuffer();
        sb.append("<notificationTO>\n");
        sb.append("  <notificationId>").append(notificationId != null ? notificationId : "").append("</notificationId>\n");
        sb.append("  <type>").append(type != null ? type : "").append("</type>\n");
        sb.append("  <recipient>").append(recipient != null ? recipient : "").append("</recipient>\n");
        sb.append("  <subject>").append(subject != null ? subject : "").append("</subject>\n");
        sb.append("  <body>").append(body != null ? body : "").append("</body>\n");
        sb.append("  <channel>").append(channel != null ? channel : "").append("</channel>\n");
        sb.append("  <status>").append(status != null ? status : "").append("</status>\n");
        sb.append("  <orderId>").append(orderId != null ? orderId : "").append("</orderId>\n");
        sb.append("  <createdDate>").append(createdDateStr != null ? createdDateStr : "").append("</createdDate>\n");
        sb.append("  <sentDate>").append(sentDateStr != null ? sentDateStr : "").append("</sentDate>\n");
        sb.append("</notificationTO>");
        return sb.toString();
    }

    public String toString() {
        return "NotificationTO[" + notificationId + " " + type + " " + channel + " -> " + recipient + " " + status + "]";
    }
}
