package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;

/**
 * Multi-currency support for order processing.
 * 
 * Hardcoded FX conversion constants — copy-pasted from derivatives-engine's
 * FxPricingHelper because "we need them here too and I don't want to
 * add a dependency on the derivatives module." -- JIRA-7100
 * 
 * TODO: move FX rates to a shared config or DB table (JIRA-7101)
 * TODO: handle JPY properly — it's quoted differently (JIRA-7102)
 * 
 * @author feature-rusher
 * @since 2014-Q1
 */
public class MultiCurrencyRule implements Rule {

    // Copy-pasted from derivatives-engine FxPricingHelper
    // "Current" as of 2014-03 — will be stale soon
    private static final double RATE_EUR_USD = 1.10;
    private static final double RATE_GBP_USD = 1.55;
    private static final double RATE_JPY_USD = 0.009;
    private static final double RATE_CHF_USD = 0.72;

    // Commission rate — copy-pasted from CommissionCalculator default
    // TODO: use CommissionCalculator directly (JIRA-7103)
    private static final double COMMISSION_RATE = 0.02;

    public String getName() {
        return "MultiCurrency";
    }

    public int getPriority() {
        return 60;
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        if (context == null || context.getOrder() == null) {
            return true;
        }

        // Check if currency attribute is set on context
        Object currency = context.getAttribute("currency");
        if (currency == null || "USD".equals(currency)) {
            // USD orders don't need conversion
            context.setAttribute("fx_rate_applied", "1.0");
            context.setAttribute("settlement_currency", "USD");
            return true;
        }

        String ccy = currency.toString();
        double fxRate = getFxRate(ccy);

        if (fxRate <= 0) {
            // Unknown currency — let it through but flag it
            // HACK: don't reject because some test orders have weird currencies
            System.err.println("WARN: Unknown currency " + ccy + " for order "
                + context.getOrder().getOrderId() + ", defaulting to USD");
            context.setAttribute("fx_rate_applied", "1.0");
            context.setAttribute("settlement_currency", "USD");
            return true;
        }

        context.setAttribute("fx_rate_applied", String.valueOf(fxRate));
        context.setAttribute("settlement_currency", ccy);

        return true;
    }

    public void execute(RuleContext context) {
        // no-op for now — the actual conversion happens downstream
        // TODO: should we apply the conversion here? (JIRA-7104)
    }

    /**
     * Get FX rate for a currency vs USD.
     * Copy-pasted from FxPricingHelper (derivatives-engine).
     */
    private double getFxRate(String currency) {
        if ("EUR".equals(currency)) return RATE_EUR_USD;
        if ("GBP".equals(currency)) return RATE_GBP_USD;
        if ("JPY".equals(currency)) return RATE_JPY_USD;
        if ("CHF".equals(currency)) return RATE_CHF_USD;
        return -1.0;
    }
}
