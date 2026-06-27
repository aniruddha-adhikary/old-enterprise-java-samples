package com.bigcorp.pricing.service;

import com.bigcorp.common.db.ConnectionHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

/**
 * Core pricing service implementation.
 * 
 * Queries the PRICING_CACHE table for current prices and applies
 * spread adjustments based on client tier.
 * 
 * NOTE: The commission rate here (0.015) is different from order-engine's
 * rate (0.02). This is NOT a bug - business wanted different rates in 
 * different systems. Or maybe it IS a bug and nobody noticed. Either way,
 * do NOT change it without talking to Susan in compliance first.
 * 
 * @author Dave
 * @author Bob (added the tier spread logic per JIRA-892)
 * @since 1.0
 */
public class PricingServiceImpl {

    // Commission rate for pricing calculations
    // NOTE: order-engine uses 0.02 - the discrepancy is "by design" 
    // (business wanted it in both places, don't ask)
    private static final double COMMISSION_RATE = 0.015;

    // Spread adjustments by tier
    // PLATINUM gets tighter spreads because Henderson complained
    private static final double SPREAD_PLATINUM = 0.001;  // 0.1%
    private static final double SPREAD_GOLD = 0.002;      // 0.2%
    private static final double SPREAD_SILVER = 0.003;    // 0.3%
    private static final double SPREAD_BRONZE = 0.005;    // 0.5%
    private static final double SPREAD_DEFAULT = 0.005;   // same as bronze

    /**
     * Get a price quote for a single symbol.
     * Looks up PRICING_CACHE table, falls back to hardcoded prices
     * if the DB is having one of its "moments."
     */
    public PriceQuote getQuote(String symbol) {
        if (symbol == null || symbol.trim().length() == 0) {
            System.out.println("WARN: getQuote called with null/empty symbol");
            return null;
        }

        symbol = symbol.trim().toUpperCase();
        PriceQuote quote = lookupFromDatabase(symbol);

        if (quote == null) {
            // TEMPORARY: remove when DB is stable
            // Added 2000-03-15 by Bob during the Great Database Outage
            quote = getHardcodedQuote(symbol);
        }

        return quote;
    }

    /**
     * Get price quotes for multiple symbols.
     * Just calls getQuote in a loop because "premature optimization
     * is the root of all evil" and we never got around to optimizing it.
     */
    public PriceQuote[] getBatchQuotes(String[] symbols) {
        if (symbols == null || symbols.length == 0) {
            return new PriceQuote[0];
        }

        // TODO: optimize this to use a single SQL query with IN clause
        // (Bob said he'd do it in Q3 2001... it's now 2002)
        PriceQuote[] results = new PriceQuote[symbols.length];
        for (int i = 0; i < symbols.length; i++) {
            results[i] = getQuote(symbols[i]);
        }
        return results;
    }

    /**
     * Apply spread adjustment based on client tier.
     * This duplicates logic from the rule engine's ClientTierRule,
     * but business wanted pricing to also handle it independently.
     * "In case the rule engine is down" they said. Sure.
     */
    public PriceQuote applyTierSpread(PriceQuote quote, String clientTier) {
        if (quote == null) return null;

        double spreadPct;
        if ("PLATINUM".equals(clientTier)) {
            spreadPct = SPREAD_PLATINUM;
        } else if ("GOLD".equals(clientTier)) {
            spreadPct = SPREAD_GOLD;
        } else if ("SILVER".equals(clientTier)) {
            spreadPct = SPREAD_SILVER;
        } else if ("BRONZE".equals(clientTier)) {
            spreadPct = SPREAD_BRONZE;
        } else {
            spreadPct = SPREAD_DEFAULT;
        }

        double midPrice = quote.getLastPrice();
        quote.setBidPrice(midPrice * (1.0 - spreadPct));
        quote.setAskPrice(midPrice * (1.0 + spreadPct));

        return quote;
    }

    /**
     * Calculate commission for a trade.
     * Uses 0.015 rate (NOT the 0.02 from order-engine).
     */
    public double calculateCommission(double tradeAmount) {
        return tradeAmount * COMMISSION_RATE;
    }

    /**
     * Look up price from PRICING_CACHE table via JDBC.
     */
    private PriceQuote lookupFromDatabase(String symbol) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = ConnectionHelper.getConnection();
            pstmt = conn.prepareStatement(
                "SELECT SYMBOL, BID_PRICE, ASK_PRICE, LAST_PRICE, CURRENCY, LAST_UPDATED " +
                "FROM PRICING_CACHE WHERE SYMBOL = ?"
            );
            pstmt.setString(1, symbol);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                PriceQuote quote = new PriceQuote();
                quote.setSymbol(rs.getString("SYMBOL"));
                quote.setBidPrice(rs.getDouble("BID_PRICE"));
                quote.setAskPrice(rs.getDouble("ASK_PRICE"));
                quote.setLastPrice(rs.getDouble("LAST_PRICE"));
                quote.setCurrency(rs.getString("CURRENCY"));

                java.sql.Timestamp ts = rs.getTimestamp("LAST_UPDATED");
                if (ts != null) {
                    quote.setTimestamp(new Date(ts.getTime()));
                }

                System.out.println("Pricing lookup OK: " + quote);
                return quote;
            } else {
                System.out.println("WARN: No pricing data found for symbol: " + symbol);
                return null;
            }

        } catch (Exception e) {
            System.err.println("ERROR: Database lookup failed for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            ConnectionHelper.closeQuietly(rs);
            ConnectionHelper.closeQuietly(pstmt);
            ConnectionHelper.closeQuietly(conn);
        }
    }

    /**
     * Hardcoded fallback prices.
     * 
     * TEMPORARY: remove when DB is stable
     * Added: 2000-03-15 during the Great Database Outage of Q1 2000.
     * Last updated: 2001-11-20 (added SUNW)
     * Still here: yes
     * Will be removed: "soon"
     */
    private PriceQuote getHardcodedQuote(String symbol) {
        System.out.println("WARN: Using hardcoded fallback price for " + symbol);

        PriceQuote quote = new PriceQuote();
        quote.setSymbol(symbol);
        quote.setTimestamp(new Date());

        // These prices were current when they were added. 
        // They are probably not current anymore.
        if ("MSFT".equals(symbol)) {
            quote.setBidPrice(25.00);
            quote.setAskPrice(25.50);
            quote.setLastPrice(25.25);
        } else if ("IBM".equals(symbol)) {
            quote.setBidPrice(119.00);
            quote.setAskPrice(120.00);
            quote.setLastPrice(119.50);
        } else if ("ORCL".equals(symbol)) {
            quote.setBidPrice(15.00);
            quote.setAskPrice(15.50);
            quote.setLastPrice(15.25);
        } else if ("SUNW".equals(symbol)) {
            // added 2001-11-20 after the Henderson account started trading it
            quote.setBidPrice(8.50);
            quote.setAskPrice(9.00);
            quote.setLastPrice(8.75);
        } else if ("CSCO".equals(symbol)) {
            quote.setBidPrice(21.50);
            quote.setAskPrice(22.00);
            quote.setLastPrice(21.75);
        } else if ("INTC".equals(symbol)) {
            quote.setBidPrice(30.00);
            quote.setAskPrice(30.50);
            quote.setLastPrice(30.25);
        } else if ("DELL".equals(symbol)) {
            quote.setBidPrice(34.50);
            quote.setAskPrice(35.00);
            quote.setLastPrice(34.75);
        } else {
            // unknown symbol - return some default so we don't blow up
            // (this has caused problems before - see JIRA-1102)
            System.out.println("WARN: Unknown symbol '" + symbol + "', returning default price");
            quote.setBidPrice(10.00);
            quote.setAskPrice(10.50);
            quote.setLastPrice(10.25);
        }

        quote.setCurrency("USD");
        return quote;
    }
}
