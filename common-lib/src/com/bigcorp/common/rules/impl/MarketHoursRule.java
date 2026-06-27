package com.bigcorp.common.rules.impl;

import com.bigcorp.common.rules.Rule;
import com.bigcorp.common.rules.RuleContext;
import java.util.Calendar;

/**
 * Checks if the order is placed during market hours.
 * 
 * Market hours: 9:30 AM - 4:00 PM Eastern.
 * Except we don't actually convert to Eastern time, we just use
 * the server's local time and hope for the best.
 * 
 * This rule was disabled for 6 months in 2001 because the server 
 * clock was wrong and nobody noticed until a client complained they
 * couldn't trade at 2 PM.
 * 
 * @author Bob
 * @since 1.0
 */
public class MarketHoursRule implements Rule {

    // market opens at 9:30 AM (server local time... not Eastern)
    private static final int MARKET_OPEN_HOUR = 9;
    private static final int MARKET_OPEN_MINUTE = 30;
    // market closes at 4:00 PM
    private static final int MARKET_CLOSE_HOUR = 16;
    private static final int MARKET_CLOSE_MINUTE = 0;

    // set to false to disable this rule without restarting the app
    // (added after "the incident" in 2001)
    private boolean active = true;

    public String getName() {
        return "MarketHours";
    }

    public int getPriority() {
        return 80;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean evaluate(RuleContext context) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        // check weekend
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            context.addWarning("Order placed on weekend - will be queued for Monday");
            // don't reject, just warn
            // (originally this rejected, but sales complained)
            context.setAttribute("queued", Boolean.TRUE);
            return true;
        }

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        int currentMinutes = hour * 60 + minute;
        int openMinutes = MARKET_OPEN_HOUR * 60 + MARKET_OPEN_MINUTE;
        int closeMinutes = MARKET_CLOSE_HOUR * 60 + MARKET_CLOSE_MINUTE;

        if (currentMinutes < openMinutes || currentMinutes >= closeMinutes) {
            context.addWarning("Order placed outside market hours (" + hour + ":" + minute + ") - will be queued");
            context.setAttribute("queued", Boolean.TRUE);
            return true; // don't reject, queue instead
        }

        return true;
    }

    public void execute(RuleContext context) {
        // nothing to execute
    }
}
