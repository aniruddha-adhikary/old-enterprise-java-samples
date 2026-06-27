package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.TradeOrder;

/**
 * Checks if the order symbol is on the restricted list.
 * 
 * Restricted symbols cannot be traded. Period. No exceptions.
 * (Well, except for the ones that sales negotiates. But that's
 * a different rule.)
 * 
 * @author feature-rusher
 * @since 2003 Q4
 */
public class RestrictedSymbolRule implements Rule {

    // TODO: move these to a database table (JIRA-4100)
    private static final String[] RESTRICTED_SYMBOLS = {"ENRN", "WCOM", "TYCO", "ADLP"};

    public String getName() {
        return "RestrictedSymbol";
    }

    public int getPriority() {
        return 95; // runs early, between MaxOrderValue(100) and ClientTier(90)
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        TradeOrder order = context.getOrder();
        String symbol = order.getSymbol();

        for (int i = 0; i < RESTRICTED_SYMBOLS.length; i++) {
            if (RESTRICTED_SYMBOLS[i].equals(symbol)) {
                context.reject("Restricted symbol: " + symbol + " — trading suspended");
                return false;
            }
        }

        // passed — stash the check result
        context.setAttribute("restricted_check", "passed");
        return true;
    }

    public void execute(RuleContext context) {
        // nothing to do on pass
    }
}
