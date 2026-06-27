package com.bigcorp.common.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Notification message for email/SMS dispatch.
 * 
 * @author Karen (notifications team)
 * @since 1.2
 */
public class Notification implements Serializable {

    private static final long serialVersionUID = 102L;

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";
    // CHANNEL_FAX was removed in 2002 but some records still have it
    public static final String CHANNEL_FAX = "FAX";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String TYPE_ORDER_CONFIRM = "ORDER_CONFIRM";
    public static final String TYPE_ORDER_REJECT = "ORDER_REJECT";
    public static final String TYPE_SETTLEMENT = "SETTLEMENT";
    public static final String TYPE_PRICE_ALERT = "PRICE_ALERT";

    private String notificationId;
    private String type;
    private String recipient; // email address or phone number
    private String subject;
    private String body;
    private String channel;
    private String status;
    private String orderId; // related order, may be null
    private Date createdDate;
    private Date sentDate;
    private int retryCount;

    public Notification() {
        this.status = STATUS_PENDING;
        this.createdDate = new Date();
        this.retryCount = 0;
    }

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

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getSentDate() {
        return sentDate;
    }

    public void setSentDate(Date sentDate) {
        this.sentDate = sentDate;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String toString() {
        return "Notification[" + notificationId + " " + type + " " + channel + " -> " + recipient + " " + status + "]";
    }
}
