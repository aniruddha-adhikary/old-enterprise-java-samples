package com.bigcorp.common.rules;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Typed result object returned by {@link TypedRule#evaluateTyped(RuleContext)}.
 * 
 * Provides a structured alternative to polluting the RuleContext with
 * ad-hoc string-keyed attributes. New rules should return a RuleResult
 * with typed data in the attributes map; the engine calls
 * {@link #applyToContext(RuleContext)} to copy attributes back into the
 * context for backward compatibility with downstream code that reads
 * string-keyed values.
 * 
 * @author architect
 * @since 2007 Q1 (Wave 5)
 */
public class RuleResult {

    private boolean passed;
    private String ruleName;
    private String message;
    private Map attributes; // typed data produced by the rule

    /**
     * Construct a RuleResult.
     * 
     * @param passed   whether the rule passed
     * @param ruleName the name of the rule that produced this result
     * @param message  human-readable message (may be null)
     */
    public RuleResult(boolean passed, String ruleName, String message) {
        this.passed = passed;
        this.ruleName = ruleName;
        this.message = message;
        this.attributes = new HashMap();
    }

    public boolean isPassed() {
        return passed;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getMessage() {
        return message;
    }

    public Map getAttributes() {
        return attributes;
    }

    /**
     * Set a typed attribute on this result.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Get a typed attribute from this result.
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Backward-compatibility shim: copies all attributes from this RuleResult
     * back into the RuleContext's string-keyed attribute map. This allows new
     * TypedRule implementations to coexist with legacy code that reads context
     * attributes directly.
     * 
     * @param ctx the RuleContext to copy attributes into
     */
    public void applyToContext(RuleContext ctx) {
        Iterator it = attributes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            ctx.setAttribute((String) entry.getKey(), entry.getValue());
        }
    }
}
