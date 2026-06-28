package com.bigcorp.connector.account;

import java.io.Serializable;

/**
 * Value object representing a client account record from the
 * mainframe back-office EIS (CICS/COBOL system).
 * 
 * NOTE: This intentionally overlaps with com.bigcorp.common.model.Client.
 * The mainframe owns the "source of truth" for credit limits and account
 * status, while the Java side has its own Client model from the CLIENTS
 * table. We asked about unifying these but the mainframe team said
 * "the COBOL copybook is the master record" so here we are.
 * 
 * Field names follow the mainframe naming conventions (ACCT-NBR, etc.)
 * mapped to Java getters for readability.
 * 
 * @author Vendor Integration Team (Apex Consulting)
 * @since connector-1.0
 */
public class AccountRecord implements Serializable {

    private static final long serialVersionUID = 7001L;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";

    private String accountNumber;
    private double creditLimit;
    private String accountStatus;
    private String lastSettlementDate;

    public AccountRecord() {
    }

    public AccountRecord(String accountNumber, double creditLimit,
                         String accountStatus, String lastSettlementDate) {
        this.accountNumber = accountNumber;
        this.creditLimit = creditLimit;
        this.accountStatus = accountStatus;
        this.lastSettlementDate = lastSettlementDate;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public double getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(double creditLimit) {
        this.creditLimit = creditLimit;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getLastSettlementDate() {
        return lastSettlementDate;
    }

    public void setLastSettlementDate(String lastSettlementDate) {
        this.lastSettlementDate = lastSettlementDate;
    }

    public boolean isAccountActive() {
        return STATUS_ACTIVE.equals(accountStatus);
    }

    public String toString() {
        return "AccountRecord[acct=" + accountNumber + " limit=" + creditLimit
            + " status=" + accountStatus + " lastSettle=" + lastSettlementDate + "]";
    }
}
