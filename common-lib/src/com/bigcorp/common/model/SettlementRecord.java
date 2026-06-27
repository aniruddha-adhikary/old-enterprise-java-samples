package com.bigcorp.common.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Settlement record for end-of-day processing.
 * Maps to SETTLEMENT_RECORDS table.
 * 
 * @author Dave (settlements team)
 * @since 1.1
 */
public class SettlementRecord implements Serializable {

    private static final long serialVersionUID = 103L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_FAILED = "FAILED";
    // added for the clearinghouse reconciliation feed (2002)
    public static final String STATUS_RECONCILED = "RECONCILED";
    public static final String STATUS_DISCREPANCY = "DISCREPANCY";

    private String recordId;
    private String orderId;
    private String clientId;
    private String symbol;
    private int quantity;
    private String side;
    private double amount;
    private double commission;
    private Date tradeDate;
    private Date settlementDate; // T+3 in the 90s
    private String status;
    private String batchId; // which batch file this was included in
    private String externalRef; // ref from clearinghouse

    public SettlementRecord() {
        this.status = STATUS_PENDING;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
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

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getCommission() {
        return commission;
    }

    public void setCommission(double commission) {
        this.commission = commission;
    }

    public Date getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(Date tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Date getSettlementDate() {
        return settlementDate;
    }

    public void setSettlementDate(Date settlementDate) {
        this.settlementDate = settlementDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String toString() {
        return "SettlementRecord[" + recordId + " order=" + orderId + " " + amount + " " + status + "]";
    }
}
