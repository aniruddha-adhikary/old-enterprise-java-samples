package com.bigcorp.common.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Trade order domain object.
 * 
 * NOTE: Do not change the field names - they are mapped to the XML schema
 * and the database columns. If you change them, you will break the
 * settlement file generator AND the MQ message format.
 * 
 * @author Bob (original), various others
 * @since 1.0 (1999-03-15)
 */
public class TradeOrder implements Serializable {

    /** added for serialization - do not change (breaks MQ compat) */
    private static final long serialVersionUID = 100L;

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_VALIDATED = "VALIDATED";
    public static final String STATUS_PRICED = "PRICED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_SETTLED = "SETTLED";
    // STATUS_PENDING_REVIEW was added for JIRA-2341 but never used
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";

    private String orderId;
    private String clientId;
    private String symbol;
    private int quantity;
    private String side; // BUY or SELL
    private double price;
    private double requestedPrice; // price submitted by client
    private String status;
    private Date orderDate;
    private Date lastModified;
    private String notes; // free text, sometimes has XML in it (don't ask)

    public TradeOrder() {
        this.status = STATUS_NEW;
        this.orderDate = new Date();
        this.lastModified = new Date();
    }

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
        this.lastModified = new Date();
    }

    public Date getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Date orderDate) {
        this.orderDate = orderDate;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String toString() {
        return "TradeOrder[" + orderId + " " + side + " " + quantity + " " + symbol + " @" + price + " " + status + "]";
    }
}
