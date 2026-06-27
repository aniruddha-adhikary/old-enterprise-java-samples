package com.bigcorp.common.rules;

/**
 * Extended rule interface that returns a structured {@link RuleResult}
 * instead of a bare boolean.
 * 
 * New rules should implement this interface to provide typed output data.
 * The RuleEngine detects TypedRule implementations and calls
 * {@link #evaluateTyped(RuleContext)} instead of the legacy
 * {@link Rule#evaluate(RuleContext)} method.
 * 
 * The legacy {@link Rule#evaluate(RuleContext)} method should still be
 * implemented (it can delegate to evaluateTyped and return the passed flag)
 * so that code which calls rules directly without the engine still works.
 * 
 * @author architect
 * @since 2007 Q1 (Wave 5)
 */
public interface TypedRule extends Rule {

    /**
     * Evaluate the rule and return a structured result.
     * 
     * @param context the rule context containing the order and client
     * @return a RuleResult with pass/fail status, message, and typed attributes
     */
    RuleResult evaluateTyped(RuleContext context);
}
