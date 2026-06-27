package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.TradeOrder;

/**
 * Applies volume-based commission discounts for large orders.
 * 
 * Orders > 5000 shares get 25% off commission.
 * Orders > 10000 shares get 50% off commission.
 * 
 * TODO: this should integrate with CommissionCalculator (JIRA-6001)
 * but we're copy-pasting the base rate for now because we need to
 * ship this by end of sprint.
 * 
 * @author feature-rusher
 * @since 2009 Q2
 */
public class VolumeDiscountRule implements Rule {

    // TODO: use CommissionCalculator instead of copy-pasting (JIRA-6001)
    private static final double BASE_COMMISSION = 0.02;

    public String getName() {
        return "VolumeDiscount";
    }

    public int getPriority() {
        return 55; // just above SpecialClients (50)
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        // this rule always passes - it just adjusts commission
        return true;
    }

    public void execute(RuleContext context) {
        try {
            TradeOrder order = context.getOrder();
            if (order == null) return;

            int quantity = order.getQuantity();

            if (quantity > 10000) {
                // 50% off commission for very large orders
                context.setAttribute("volume_discount", Double.valueOf(0.50));
                context.setAttribute("volume_discount_applied", Boolean.TRUE);
                context.addMessage("Volume discount: 50% off commission (qty=" + quantity + " > 10000)");
            } else if (quantity > 5000) {
                // 25% off commission for large orders
                context.setAttribute("volume_discount", Double.valueOf(0.25));
                context.setAttribute("volume_discount_applied", Boolean.TRUE);
                context.addMessage("Volume discount: 25% off commission (qty=" + quantity + " > 5000)");
            }
        } catch (Exception e) {
            // Don't let discount calculation block the order
            System.err.println("WARN: VolumeDiscountRule error: " + e.getMessage());
        }
    }
}
