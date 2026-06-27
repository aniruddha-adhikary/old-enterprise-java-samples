package com.bigcorp.settlement.reconciliation;

/**
 * Represents a single entry from a reconciliation file.
 * Used internally by the parsers - not part of the domain model.
 *
 * This is essentially a struct to hold the parsed data before
 * we figure out what to do with it. It maps to one line in a
 * .dat file or one &lt;record&gt; element in an XML file.
 *
 * Status values returned by the clearinghouse:
 *   CONF - confirmed (trade settled successfully)
 *   REJC - rejected (clearinghouse rejected the trade)
 *   DISC - discrepancy (amounts don't match their records)
 *   PEND - pending (still processing on their end)
 *
 * We map these to our internal SettlementRecord status constants
 * in the ReconciliationProcessor.
 *
 * @author Dave (settlements team)
 * @since 1.3
 */
public class ReconciliationEntry {

    private String recordId;
    private String externalRef;
    private String status; // raw status from file (CONF/REJC/DISC/PEND or CONFIRMED/REJECTED/DISCREPANCY)
    private double amount;
    private String date; // as string from file (YYYYMMDD for .dat, various for .xml)
    private String reasonCode; // blank if confirmed, otherwise clearinghouse reason

    public ReconciliationEntry() {
        // default constructor
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String toString() {
        return "ReconciliationEntry[" + recordId + " status=" + status + " amt=" + amount + "]";
    }
}
