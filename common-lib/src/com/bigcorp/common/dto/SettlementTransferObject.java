package com.bigcorp.common.dto;

import com.bigcorp.common.model.SettlementRecord;

import java.io.Serializable;
import java.text.SimpleDateFormat;

/**
 * Transfer Object for settlement data.
 * Used by the settlement gateway when communicating with the 
 * clearinghouse file generator.
 *
 * Has extra fields for clearinghouse-specific data that don't
 * belong in the domain model.
 *
 * NOTE: The netAmount field is calculated (amount - commission) but
 * we store it anyway because the clearinghouse expects it in the file
 * and we don't trust floating-point math to be deterministic across
 * JVM versions. (This fear is probably unfounded but Dave insisted.)
 *
 * @author Dave (settlements team)
 * @since 1.3
 */
public class SettlementTransferObject implements Serializable {

    private static final long serialVersionUID = 201L;

    private static final String DISPLAY_DATE_FORMAT = "yyyy-MM-dd";

    /** Our member ID at the clearinghouse - hardcoded because it never changes */
    private static final String OUR_CLEARING_MEMBER_ID = "BIGCORP-001";

    private String recordId;
    private String orderId;
    private String clientId;
    private String symbol;
    private int quantity;
    private String side;
    private double amount;
    private double commission;
    private double netAmount; // amount - commission (calculated field)
    private String tradeDateStr;
    private String settlementDateStr;
    private String status;
    private String batchId;
    private String externalRef;
    // Clearinghouse-specific fields
    private String clearingMemberId; // our member ID at the clearinghouse
    private String contraPartyId; // counterparty (always null - we don't track this)

    public SettlementTransferObject() {
        // default constructor
        this.clearingMemberId = OUR_CLEARING_MEMBER_ID;
    }

    // --- Getters and Setters ---

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

    public double getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(double netAmount) {
        this.netAmount = netAmount;
    }

    public String getTradeDateStr() {
        return tradeDateStr;
    }

    public void setTradeDateStr(String tradeDateStr) {
        this.tradeDateStr = tradeDateStr;
    }

    public String getSettlementDateStr() {
        return settlementDateStr;
    }

    public void setSettlementDateStr(String settlementDateStr) {
        this.settlementDateStr = settlementDateStr;
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

    public String getClearingMemberId() {
        return clearingMemberId;
    }

    public void setClearingMemberId(String clearingMemberId) {
        this.clearingMemberId = clearingMemberId;
    }

    public String getContraPartyId() {
        return contraPartyId;
    }

    public void setContraPartyId(String contraPartyId) {
        this.contraPartyId = contraPartyId;
    }

    /**
     * Build from domain object.
     * Calculates netAmount and formats dates.
     */
    public static SettlementTransferObject fromSettlementRecord(SettlementRecord record) {
        if (record == null) {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(DISPLAY_DATE_FORMAT);

        SettlementTransferObject to = new SettlementTransferObject();
        to.setRecordId(record.getRecordId());
        to.setOrderId(record.getOrderId());
        to.setClientId(record.getClientId());
        to.setSymbol(record.getSymbol());
        to.setQuantity(record.getQuantity());
        to.setSide(record.getSide());
        to.setAmount(record.getAmount());
        to.setCommission(record.getCommission());
        to.setNetAmount(record.getAmount() - record.getCommission());
        to.setStatus(record.getStatus());
        to.setBatchId(record.getBatchId());
        to.setExternalRef(record.getExternalRef());

        if (record.getTradeDate() != null) {
            to.setTradeDateStr(sdf.format(record.getTradeDate()));
        }
        if (record.getSettlementDate() != null) {
            to.setSettlementDateStr(sdf.format(record.getSettlementDate()));
        }

        // clearinghouse fields
        to.setClearingMemberId(OUR_CLEARING_MEMBER_ID);
        to.setContraPartyId(null); // we never know the counterparty

        return to;
    }

    /**
     * Convert to XML for the clearinghouse file format.
     * 
     * This format matches the clearinghouse "Settlement Submission Schema v2.1"
     * spec document (the one with coffee stains on page 3).
     */
    public String toXml() {
        StringBuffer sb = new StringBuffer();
        sb.append("<settlementTO>\n");
        sb.append("  <recordId>").append(recordId != null ? recordId : "").append("</recordId>\n");
        sb.append("  <orderId>").append(orderId != null ? orderId : "").append("</orderId>\n");
        sb.append("  <clientId>").append(clientId != null ? clientId : "").append("</clientId>\n");
        sb.append("  <symbol>").append(symbol != null ? symbol : "").append("</symbol>\n");
        sb.append("  <quantity>").append(quantity).append("</quantity>\n");
        sb.append("  <side>").append(side != null ? side : "").append("</side>\n");
        sb.append("  <amount>").append(amount).append("</amount>\n");
        sb.append("  <commission>").append(commission).append("</commission>\n");
        sb.append("  <netAmount>").append(netAmount).append("</netAmount>\n");
        sb.append("  <tradeDate>").append(tradeDateStr != null ? tradeDateStr : "").append("</tradeDate>\n");
        sb.append("  <settlementDate>").append(settlementDateStr != null ? settlementDateStr : "").append("</settlementDate>\n");
        sb.append("  <status>").append(status != null ? status : "").append("</status>\n");
        sb.append("  <batchId>").append(batchId != null ? batchId : "").append("</batchId>\n");
        sb.append("  <externalRef>").append(externalRef != null ? externalRef : "").append("</externalRef>\n");
        sb.append("  <clearingMemberId>").append(clearingMemberId != null ? clearingMemberId : "").append("</clearingMemberId>\n");
        sb.append("  <contraPartyId>").append(contraPartyId != null ? contraPartyId : "").append("</contraPartyId>\n");
        sb.append("</settlementTO>");
        return sb.toString();
    }

    public String toString() {
        return "SettlementTO[" + recordId + " order=" + orderId + " net=" + netAmount + " " + status + "]";
    }
}
