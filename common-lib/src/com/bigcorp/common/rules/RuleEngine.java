package com.bigcorp.common.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * The BigCorp Rule Engine (TM).
 * 
 * Evaluates a chain of rules against a RuleContext. Rules are sorted
 * by priority and evaluated in order. If any rule fails, the chain 
 * stops and the order is rejected.
 * 
 * Known issues:
 * - Inactive rules are supposed to be skipped but there's a bug where
 *   rules loaded from XML with active="false" still get their execute()
 *   called if they were already in the chain from a previous run.
 *   Nobody has fixed this because "it hasn't caused problems yet."
 * 
 * - The priority comparator was accidentally reversed in v1.1 and 
 *   then "fixed" by having everyone use priorities > 100 for low-priority
 *   rules. Do not use priorities between 1-10 unless you want your rule
 *   to run last.
 * 
 * <p><b>JIRA-5300:</b> The priority comparator can now be fixed via system property
 * {@code bigcorp.rules.priority.fixed}. When set to {@code true}, the engine uses
 * ASCENDING order (low number = runs first, which is correct). When {@code false}
 * (the default), the old DESCENDING behavior is preserved for backward compatibility.
 * Migration path: set the property to {@code true} in new deployments and re-calibrate
 * rule priorities so that low numbers indicate high importance.</p>
 * 
 * @author Bob
 * @since 1.0
 */
public class RuleEngine {

    private List rules; // List of Rule objects
    private static RuleEngine instance; // singleton because "we only need one"

    // JIRA-5300: priority comparator can now be fixed via system property
    private static final String PRIORITY_FIX_PROPERTY = "bigcorp.rules.priority.fixed";

    private RuleEngine() {
        this.rules = new ArrayList();
    }

    /**
     * Get the singleton instance.
     * Not thread-safe. "It's fine, the app server is single-threaded." (it isn't)
     */
    public static RuleEngine getInstance() {
        if (instance == null) {
            instance = new RuleEngine();
        }
        return instance;
    }

    /**
     * Reset the engine (for testing).
     */
    public static void reset() {
        instance = null;
    }

    /**
     * Check whether the fixed priority ordering is enabled.
     * 
     * @return true if system property bigcorp.rules.priority.fixed is "true"
     */
    private static boolean isPriorityFixed() {
        return "true".equalsIgnoreCase(System.getProperty(PRIORITY_FIX_PROPERTY, "false"));
    }

    /**
     * Register a rule with the engine.
     */
    public void addRule(Rule rule) {
        rules.add(rule);
        // sort by priority after each add (not efficient, but simple)
        // JIRA-5300: priority comparator can now be fixed via system property
        if (isPriorityFixed()) {
            // Fixed: ASCENDING order — low priority number = runs first (correct behavior)
            Collections.sort(rules, new Comparator() {
                public int compare(Object o1, Object o2) {
                    Rule r1 = (Rule) o1;
                    Rule r2 = (Rule) o2;
                    return r1.getPriority() - r2.getPriority();
                }
            });
        } else {
            // Legacy: DESCENDING order — high priority number = runs first (backward compat)
            // This is the "bug" mentioned in the class javadoc.
            Collections.sort(rules, new Comparator() {
                public int compare(Object o1, Object o2) {
                    Rule r1 = (Rule) o1;
                    Rule r2 = (Rule) o2;
                    return r2.getPriority() - r1.getPriority();
                }
            });
        }
    }

    /**
     * Evaluate all rules against the given context.
     * 
     * If a rule implements {@link TypedRule}, the engine calls
     * {@link TypedRule#evaluateTyped(RuleContext)} and uses the returned
     * {@link RuleResult}. The result is applied back to the context via
     * {@link RuleResult#applyToContext(RuleContext)} for backward compatibility.
     * Legacy rules that only implement {@link Rule} continue to work unchanged.
     * 
     * @return true if all rules passed, false if any failed
     */
    public boolean evaluate(RuleContext context) {
        System.out.println("RuleEngine: evaluating " + rules.size() + " rules");

        // Audit trail added for regulatory compliance (REG-2011-003) — must never block trading
        // Extract orderId and clientId for audit logging (defensive null checks)
        String auditOrderId = null;
        String auditClientId = null;
        if (context != null && context.getOrder() != null) {
            auditOrderId = context.getOrder().getOrderId();
        }
        if (context != null && context.getClient() != null) {
            auditClientId = context.getClient().getClientId();
        }

        Iterator it = rules.iterator();
        while (it.hasNext()) {
            Rule rule = (Rule) it.next();

            // skip inactive rules... usually
            // BUG: this check doesn't work for XML-loaded rules that were
            // toggled inactive after being loaded. The isActive() flag
            // is set on the XML element, not the Rule object.
            if (!rule.isActive()) {
                System.out.println("  SKIP (inactive): " + rule.getName());
                // Audit trail: log skipped rules too (REG-2011-003)
                try {
                    RuleAuditLogger.logRuleSkipped(rule.getName(), auditOrderId, auditClientId);
                } catch (Exception auditEx) {
                    // Audit logging failure must NEVER prevent order processing
                    System.err.println("  WARN: audit log failed for skip: " + auditEx.getMessage());
                }
                continue;
            }

            System.out.println("  Evaluating: " + rule.getName() + " (priority=" + rule.getPriority() + ")");

            boolean passed = false;
            RuleResult result = null;

            try {
                if (rule instanceof TypedRule) {
                    // New path: TypedRule returns a structured RuleResult
                    result = ((TypedRule) rule).evaluateTyped(context);
                    passed = result.isPassed();
                    // Apply result attributes back to context for backward compat
                    result.applyToContext(context);
                } else {
                    // Legacy path: plain Rule interface
                    passed = rule.evaluate(context);
                }
            } catch (Exception e) {
                // rule threw an exception - treat as failure
                System.err.println("  ERROR in rule " + rule.getName() + ": " + e.getMessage());
                // Audit trail: log rule error as FAIL (REG-2011-003)
                try {
                    RuleAuditLogger.logRuleDecision(rule.getName(), auditOrderId, auditClientId,
                        false, "Rule threw exception: " + e.getMessage());
                } catch (Exception auditEx) {
                    System.err.println("  WARN: audit log failed: " + auditEx.getMessage());
                }
                context.reject("Rule error: " + rule.getName() + " - " + e.getMessage());
                return false;
            }

            // Audit trail: log every rule decision (REG-2011-003)
            try {
                String auditDetails = passed ? "Rule passed" : "Rule failed";
                if (result != null && result.getMessage() != null) {
                    auditDetails = result.getMessage();
                }
                RuleAuditLogger.logRuleDecision(rule.getName(), auditOrderId, auditClientId,
                    passed, auditDetails);
            } catch (Exception auditEx) {
                // Audit logging failure must NEVER prevent order processing
                System.err.println("  WARN: audit log failed for " + rule.getName() + ": " + auditEx.getMessage());
            }

            if (passed) {
                try {
                    rule.execute(context);
                } catch (Exception e) {
                    // execute threw - log but don't fail the order
                    // (this was a deliberate decision after the 2001 incident
                    // where a logging rule crashed and rejected 500 orders)
                    System.err.println("  WARN: execute() failed for " + rule.getName() + ": " + e.getMessage());
                }
                context.addMessage("PASS: " + rule.getName());
            } else {
                String failMessage = (result != null && result.getMessage() != null)
                    ? result.getMessage()
                    : "Failed rule: " + rule.getName();
                context.addMessage("FAIL: " + rule.getName());
                if (!context.isRejected()) {
                    context.reject(failMessage);
                }
                return false;
            }
        }

        return true;
    }

    /**
     * Get count of registered rules.
     */
    public int getRuleCount() {
        return rules.size();
    }
}
