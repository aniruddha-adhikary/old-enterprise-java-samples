package com.bigcorp.common.rules;

/**
 * Base interface for all business rules.
 * 
 * Rules are evaluated in priority order. A rule can either
 * pass, fail, or skip (return true from evaluate to proceed,
 * false to halt the chain).
 * 
 * @author Bob
 * @since 1.0
 */
public interface Rule {

    /**
     * Get the name of this rule (for logging/audit).
     */
    String getName();

    /**
     * Get the priority (lower number = evaluated first).
     */
    int getPriority();

    /**
     * Evaluate the rule against the given context.
     * 
     * @return true if the rule passes (continue chain), 
     *         false if it fails (stop chain, order rejected)
     */
    boolean evaluate(RuleContext context);

    /**
     * Execute any side effects of this rule (e.g., modify the order,
     * add notes, change pricing).
     * 
     * Called after evaluate() returns true.
     */
    void execute(RuleContext context);

    /**
     * Is this rule active? Inactive rules are skipped by the engine.
     * (In theory. See RuleEngine for the bug where inactive rules 
     * still get evaluated sometimes.)
     */
    boolean isActive();
}
