package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.Client;

/**
 * Adjusts order handling based on client tier.
 * 
 * PLATINUM and GOLD clients get priority processing.
 * SILVER clients get standard processing.
 * BRONZE clients get... well, they also get standard processing.
 * The original spec said BRONZE should have a 30-minute delay but
 * nobody implemented it and the business forgot they asked for it.
 * 
 * @author Karen
 * @since 1.1
 */
public class ClientTierRule implements Rule {

    public String getName() {
        return "ClientTier";
    }

    public int getPriority() {
        return 90; // second to run
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        Client client = context.getClient();

        if (client == null) {
            context.addWarning("No client found, skipping tier check");
            return true; // don't block the order, just warn
        }

        if (!client.isActive()) {
            context.reject("Client " + client.getClientId() + " is not active");
            return false;
        }

        return true;
    }

    public void execute(RuleContext context) {
        Client client = context.getClient();
        if (client == null) return;

        String tier = client.getTier();
        if (Client.TIER_PLATINUM.equals(tier) || Client.TIER_GOLD.equals(tier)) {
            context.setAttribute("priority", "HIGH");
            context.addMessage("Client tier " + tier + " -> HIGH priority");
        } else {
            context.setAttribute("priority", "NORMAL");
            context.addMessage("Client tier " + tier + " -> NORMAL priority");
        }
    }
}
