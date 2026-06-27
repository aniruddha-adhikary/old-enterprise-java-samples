package com.bigcorp.derivatives.queue;

/**
 * JMS queue name constants for the derivatives desk.
 * 
 * We define our own queue names here instead of using MessageQueueHelper
 * from common-lib. Their queue naming scheme is BIGCORP.TRADE.* but
 * we want BIGCORP.DERIVATIVES.* so the ops team can route them separately.
 * 
 * (Yes, we know MessageQueueHelper already defines constants.
 *  No, we're not going to import it just for two strings.)
 * 
 * @author External contractor
 * @since 2004-Q3
 */
public class DerivativeQueueConstants {

    /** Queue for inbound derivative orders */
    public static final String DERIVATIVE_ORDERS_QUEUE = "BIGCORP.DERIVATIVES.ORDERS";

    /** Queue for outbound derivative confirmations */
    public static final String DERIVATIVE_CONFIRMS_QUEUE = "BIGCORP.DERIVATIVES.CONFIRMS";

    /** Queue for derivative pricing requests (future use) */
    public static final String DERIVATIVE_PRICING_QUEUE = "BIGCORP.DERIVATIVES.PRICING";

    private DerivativeQueueConstants() {
        // no instances
    }
}
