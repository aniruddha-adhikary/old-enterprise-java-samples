package com.bigcorp.risk.model;

import java.util.Date;

/**
 * Risk engine's own order model.
 * 
 * We don't use TradeOrder from common-lib because:
 * (a) we need different fields (exposure, VaR contribution)
 * (b) we don't want to depend on their serialization format
 * (c) we were told "just make your own, it's easier"
 * 
 * @author contractor (risk team)
 * @since 2017-Q1
 */
public class RiskOrder {

    private String riskOrderId;
    private String sourceOrderId;
    private String clientId;
    private String symbol;
    private int quantity;
    private String side;
    private double price;
    private double notionalValue;
    private double exposureContribution;
    private double varContribution;
    private String riskStatus;
    private Date assessmentDate;

    public static final String RISK_STATUS_PENDING = "PENDING";
    public static final String RISK_STATUS_ASSESSED = "ASSESSED";
    public static final String RISK_STATUS_FLAGGED = "FLAGGED";
    public static final String RISK_STATUS_ERROR = "ERROR";

    public RiskOrder() {
        this.riskStatus = RISK_STATUS_PENDING;
        this.assessmentDate = new Date();
    }

    public String getRiskOrderId() { return riskOrderId; }
    public void setRiskOrderId(String id) { this.riskOrderId = id; }

    public String getSourceOrderId() { return sourceOrderId; }
    public void setSourceOrderId(String id) { this.sourceOrderId = id; }

    public String getClientId() { return clientId; }
    public void setClientId(String id) { this.clientId = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String s) { this.symbol = s; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int q) { this.quantity = q; }

    public String getSide() { return side; }
    public void setSide(String s) { this.side = s; }

    public double getPrice() { return price; }
    public void setPrice(double p) { this.price = p; }

    public double getNotionalValue() { return notionalValue; }
    public void setNotionalValue(double nv) { this.notionalValue = nv; }

    public double getExposureContribution() { return exposureContribution; }
    public void setExposureContribution(double ec) { this.exposureContribution = ec; }

    public double getVarContribution() { return varContribution; }
    public void setVarContribution(double vc) { this.varContribution = vc; }

    public String getRiskStatus() { return riskStatus; }
    public void setRiskStatus(String s) { this.riskStatus = s; }

    public Date getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(Date d) { this.assessmentDate = d; }

    public String toString() {
        return "RiskOrder[" + riskOrderId + " src=" + sourceOrderId + " " + side
            + " " + quantity + " " + symbol + " notional=" + notionalValue
            + " exposure=" + exposureContribution + " VaR=" + varContribution
            + " status=" + riskStatus + "]";
    }
}
