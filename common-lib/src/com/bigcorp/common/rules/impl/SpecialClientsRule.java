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

        // Added per email from VP Sales 2009-03-10, Smith gets GOLD-tier pricing
        if ("C003".equals(clientId)) {
            context.setAttribute("commission_override", Double.valueOf(0.01));
            context.addMessage("Smith & Associates: GOLD-tier commission applied (1.0%)");
        }

        // JIRA-3401 finally implemented after 10 years — Pinnacle gets half-price commission
        if ("C005".equals(clientId)) {
            context.setAttribute("commission_override", Double.valueOf(0.01));  // 50% off BRONZE 2%
            context.addMessage("Pinnacle Investments: 50% commission discount applied");
        }

        // C006 added per CEO directive 2009-06-01, no commission + early access
        if ("C006".equals(clientId)) {
            context.setAttribute("commission_override", Double.valueOf(0.0));
            context.setAttribute("early_access", Boolean.TRUE);
            context.addMessage("Global Macro Fund: zero commission + early access (CEO directive)");
        }

        // C007 special pricing deal — sales promised PLATINUM rates
        if ("C007".equals(clientId)) {
            context.setAttribute("pricing_tier_override", Client.TIER_PLATINUM);
            context.addMessage("Velocity Trading: PLATINUM pricing override applied");
        }
    }
}
