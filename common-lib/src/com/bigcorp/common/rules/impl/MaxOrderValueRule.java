package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Client;

/**
 * Checks that the order value doesn't exceed the client's max.
 * 
 * This rule was straightforward until Henderson Capital (C002) 
 * complained they kept getting rejected. So we added a special 
 * 10% buffer "just for now." That was in 2000.
 * 
 * @author Bob
 * @since 1.0
 */
public class MaxOrderValueRule implements Rule {

    // 10% buffer added for JIRA-1892 (Henderson complaint)
    private static final double BUFFER_MULTIPLIER = 1.10;

    public String getName() {
        return "MaxOrderValue";
    }

    public int getPriority() {
        return 100; // runs first (because priority sort is backwards, remember)
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        TradeOrder order = context.getOrder();
        Client client = context.getClient();

        if (client == null) {
            context.reject("No client record found for order " + order.getOrderId());
            return false;
        }

        double orderValue = order.getQuantity() * order.getRequestedPrice();
        double maxAllowed = client.getMaxOrderValue() * BUFFER_MULTIPLIER;

        if (orderValue > maxAllowed) {
            context.reject("Order value " + orderValue + " exceeds max allowed " 
                    + maxAllowed + " for client " + client.getClientId());
            return false;
        }

        context.addMessage("Order value " + orderValue + " within limit " + maxAllowed);
        return true;
    }

    public void execute(RuleContext context) {
        // nothing to do on pass
    }
}
