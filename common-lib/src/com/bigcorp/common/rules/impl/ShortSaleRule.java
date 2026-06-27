package com.bigcorp.common.rules.impl;

import com.bigcorp.common.billing.CommissionCalculator;
import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.TradeOrder;

/**
 * Short sale restriction — rejects SELL orders above 1000 shares.
 * 
 * Added after compliance flagged large short sales in Q3 2003.
 * The 1000 share threshold was "what felt right" per the trading desk.
 * 
 * @author feature-rusher
 * @since 2003 Q4
 */
public class ShortSaleRule implements Rule {

    // Resolved: was copy-pasted constant, now uses centralized CommissionCalculator (JIRA-2501)

    public String getName() {
        return "ShortSale";
    }

    public int getPriority() {
        return 75; // after MarketHours(80), before SpecialClients(50)
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        try {
            TradeOrder order = context.getOrder();

            if (TradeOrder.SIDE_SELL.equals(order.getSide())) {
                int quantity = order.getQuantity();

                if (quantity > 1000) {
                    context.reject("Short sale limit exceeded");
                    return false;
                }

                // stash commission for downstream — uses centralized rate from client tier
                String tier = (context.getClient() != null) ? context.getClient().getTier() : null;
                double rate = CommissionCalculator.getRate(tier);
                context.setAttribute("short_sale_commission", Double.valueOf(quantity * rate));
            }

            return true;
        } catch (Exception e) {
            System.err.println("ShortSaleRule error: " + e.getMessage());
            return true;
        }
    }

    public void execute(RuleContext context) {
        // nothing to do
    }
}
