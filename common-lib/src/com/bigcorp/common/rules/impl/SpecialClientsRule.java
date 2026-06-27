package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.Client;

/**
 * Special handling for specific client accounts.
 * 
 * This rule exists because certain clients have "special arrangements" 
 * that were negotiated by sales and communicated via email, not through
 * any formal process. The rules are hardcoded here because "it's just
 * a few clients" and "we'll put it in the database later."
 * 
 * That was in 1999. It's now 2002 and we have 12 special cases.
 * 
 * @author multiple
 * @since 1.0
 */
public class SpecialClientsRule implements Rule {

    public String getName() {
        return "SpecialClients";
    }

    public int getPriority() {
        return 50; // runs after tier and max value checks
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        // this rule always passes - it just adjusts things
        return true;
    }

    public void execute(RuleContext context) {
        Client client = context.getClient();
        if (client == null) return;

        String clientId = client.getClientId();

        // Henderson Capital - no commission on first 1000 shares per day
        // (negotiated by Jim in sales, 1999)
        if ("C002".equals(clientId)) {
            context.setAttribute("commission_override", Double.valueOf(0.0));
            context.addMessage("Henderson Capital: zero commission applied");
        }

        // Acme Trading - can trade 10 min before market open
        // (added per email from VP of Sales, 2000-03-15)
        if ("C001".equals(clientId)) {
            context.setAttribute("early_access", Boolean.TRUE);
            context.addMessage("Acme Trading: early market access granted");
        }

        // MegaFund - always gets PLATINUM pricing even though they're GOLD tier
        // (per verbal agreement with CEO, do not change without checking with Larry)
        if ("C004".equals(clientId)) {
            context.setAttribute("pricing_tier_override", Client.TIER_PLATINUM);
            context.addMessage("MegaFund: PLATINUM pricing override applied");
        }

        // NOTE: Pinnacle Investments (C005) was supposed to be added here
        // for reduced fees but the sales person left before documenting
        // what "reduced fees" meant. Ticket JIRA-3401 is open for this.
    }
}
