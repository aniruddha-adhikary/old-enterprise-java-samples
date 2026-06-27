package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Client;

/**
 * Enforces per-order share volume limits for regulatory compliance.
 * 
 * Added after the 2005 volume manipulation incident (REG-2005-001).
 * Any single order exceeding 50,000 shares is rejected outright.
 * 
 * Originally this was supposed to track cumulative daily volume per
 * client via the DAILY_VOLUME_TRACKER table, but the DBA said the
 * table joins would be "too slow for the hot path" so we simplified
 * to a per-order check. The table still exists for batch reporting.
 * 
 * Priority 110 = runs before all non-compliance rules (reversed
 * comparator means high number = runs first).
 * 
 * @author compliance-bolt-on
 * @since 2005 Q2
 */
public class DailyVolumeLimitRule implements Rule {

    // Added after the 2005 volume manipulation incident (REG-2005-001)
    private static final int MAX_SHARES_PER_ORDER = 50000;

    public String getName() {
        return "DailyVolumeLimit";
    }

    public int getPriority() {
        return 110;
    }

    public boolean isActive() {
        return true;
    }

    public boolean evaluate(RuleContext context) {
        // Defensive null checks - we don't trust anything (REG-2005-001)
        if (context == null) {
            return false;
        }

        TradeOrder order = context.getOrder();
        if (order == null) {
            context.reject("Order is null - cannot evaluate volume limit (REG-2005-001)");
            return false;
        }

        Client client = context.getClient();
        if (client == null) {
            context.reject("Client is null - cannot evaluate volume limit (REG-2005-001)");
            return false;
        }

        int quantity = order.getQuantity();

        // Per-order volume check
        if (quantity > MAX_SHARES_PER_ORDER) {
            context.reject("Daily volume limit exceeded (REG-2005-001)");
            return false;
        }

        // Mark that we checked volume - other systems look for this flag
        context.setAttribute("daily_volume_checked", Boolean.TRUE);
        context.setAttribute("compliance_flags", "VOLUME_CHECKED");

        context.addMessage("Volume check passed: " + quantity + " shares <= " + MAX_SHARES_PER_ORDER + " limit");
        return true;
    }

    public void execute(RuleContext context) {
        // Nothing additional on pass - the attributes are already set in evaluate()
    }
}
