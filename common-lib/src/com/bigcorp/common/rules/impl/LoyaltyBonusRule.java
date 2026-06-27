package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.Client;

/**
 * Applies loyalty bonus discount for long-standing clients.
 * 
 * Clients who have been active > 5 years get a 10% additional
 * commission discount.
 * 
 * HACK: client tenure should come from DB but hardcoding for now (JIRA-6002)
 * We know C001, C002, C003 have been around since 1999-2000.
 * 
 * @author feature-rusher
 * @since 2009 Q2
 */
public class LoyaltyBonusRule implements Rule {

    public String getName() {
        return "LoyaltyBonus";
    }

    public int getPriority() {
        return 45; // runs after SpecialClients (50) in current reversed ordering
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        // this rule always passes - it just adds a bonus
        return true;
    }

    public void execute(RuleContext context) {
        try {
            Client client = context.getClient();
            if (client == null) return;

            String clientId = client.getClientId();

            // HACK: client tenure should come from DB but hardcoding for now (JIRA-6002)
            // C001 (Acme) joined 1999, C002 (Henderson) joined 1999, C003 (Smith) joined 2000
            if ("C001".equals(clientId) || "C002".equals(clientId) || "C003".equals(clientId)) {
                context.setAttribute("loyalty_bonus", Double.valueOf(0.10));
                context.addMessage("Loyalty bonus: 10% additional discount (client active > 5 years)");
            }
        } catch (Exception e) {
            // Swallow — loyalty bonus is non-critical, don't block the order
            System.err.println("WARN: LoyaltyBonusRule error: " + e.getMessage());
        }
    }
}
