package com.bigcorp.common.rules;

import com.bigcorp.common.model.TradeOrder;
import com.bigcorp.common.model.Client;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Context object passed through the rule chain.
 * Contains the order being evaluated plus any additional data
 * rules might need.
 * 
 * Also accumulates messages and warnings from rules.
 * 
 * @author Bob
 * @since 1.0
 */
public class RuleContext {

    private TradeOrder order;
    private Client client;
    private Map attributes;  // generic key-value store for rule data
    private List messages;    // rule evaluation messages (strings)
    private List warnings;    // warnings that don't block the order
    private boolean rejected;
    private String rejectionReason;

    public RuleContext(TradeOrder order, Client client) {
        this.order = order;
        this.client = client;
        this.attributes = new HashMap();
        this.messages = new ArrayList();
        this.warnings = new ArrayList();
        this.rejected = false;
    }

    public TradeOrder getOrder() {
        return order;
    }

    public Client getClient() {
        return client;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void addMessage(String message) {
        messages.add(message);
    }

    public List getMessages() {
        return messages;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List getWarnings() {
        return warnings;
    }

    public boolean isRejected() {
        return rejected;
    }

    public void reject(String reason) {
        this.rejected = true;
        this.rejectionReason = reason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
