package com.bigcorp.pricing.service;

import java.io.Serializable;
import java.util.Date;

/**
 * Value object for a price quote.
 * 
 * Used by PricingServiceImpl and serialized into SOAP responses.
 * Also sent over JMS to the order engine (or it will be, once
 * we finish the MQ integration - see JIRA-1847).
 * 
 * @author Dave
 * @since 1.0
 */
public class PriceQuote implements Serializable {

    private static final long serialVersionUID = 200L;

    private String symbol;
    private double bidPrice;
    private double askPrice;
    private double lastPrice;
    private String currency;
    private Date timestamp;

    public PriceQuote() {
        this.currency = "USD";
        this.timestamp = new Date();
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(double bidPrice) {
        this.bidPrice = bidPrice;
    }

    public double getAskPrice() {
        return askPrice;
    }

    public void setAskPrice(double askPrice) {
        this.askPrice = askPrice;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(double lastPrice) {
        this.lastPrice = lastPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String toString() {
        return "PriceQuote[" + symbol + " bid=" + bidPrice + " ask=" + askPrice 
               + " last=" + lastPrice + " " + currency + "]";
    }
}
