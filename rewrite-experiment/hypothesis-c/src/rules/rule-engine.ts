import { TradeOrder } from '../domain/trade-order';
import { Client } from '../domain/client';

/**
 * Rule context wrapping the order and client being evaluated.
 * Mirrors the original RuleContext.java: holds attributes, messages,
 * warnings, and rejection state set by individual rules.
 */
export interface RuleContext {
  order: TradeOrder;
  client: Client;
  attributes: Map<string, unknown>;
  messages: string[];
  warnings: string[];
  rejected: boolean;
  rejectionReason: string | null;
}

export function createRuleContext(order: TradeOrder, client: Client): RuleContext {
  return {
    order,
    client,
    attributes: new Map(),
    messages: [],
    warnings: [],
    rejected: false,
    rejectionReason: null,
  };
}

export function rejectContext(ctx: RuleContext, reason: string): void {
  ctx.rejected = true;
  ctx.rejectionReason = reason;
}

/**
 * A business rule that evaluates an order in context.
 * Returns true if evaluation should continue, false to short-circuit.
 */
export interface Rule {
  name: string;
  priority: number;
  active: boolean;
  evaluate(ctx: RuleContext): boolean;
}

export interface RuleEvaluationLog {
  ruleName: string;
  priority: number;
  result: boolean;
  rejected: boolean;
  reason: string | null;
  durationMs: number;
}

/**
 * Rule engine that evaluates an ordered chain of business rules.
 *
 * KNOWN BUG (JIRA-5300): The original Java RuleEngine sorts by
 * DESCENDING priority (higher number runs first). When
 * `priorityFixed` is true, it sorts ASCENDING (lower number first).
 * We preserve this behavior for backward compatibility.
 */
export class RuleEngine {
  private rules: Rule[] = [];
  private priorityFixed: boolean;

  constructor(priorityFixed = false) {
    this.priorityFixed = priorityFixed;
  }

  addRule(rule: Rule): void {
    this.rules.push(rule);
  }

  getRuleCount(): number {
    return this.rules.length;
  }

  /**
   * Evaluate all active rules against the context.
   * Returns true if the order was NOT rejected (all rules passed).
   */
  evaluate(ctx: RuleContext): { passed: boolean; log: RuleEvaluationLog[] } {
    const sorted = [...this.rules].sort((a, b) => {
      if (this.priorityFixed) {
        return a.priority - b.priority;
      }
      // BUG preserved: descending sort means higher priority number runs first
      return b.priority - a.priority;
    });

    const log: RuleEvaluationLog[] = [];

    for (const rule of sorted) {
      if (!rule.active) {
        continue;
      }

      const start = Date.now();
      let result: boolean;
      try {
        result = rule.evaluate(ctx);
      } catch (err) {
        ctx.warnings.push(`Rule ${rule.name} threw: ${err}`);
        result = true;
      }
      const durationMs = Date.now() - start;

      log.push({
        ruleName: rule.name,
        priority: rule.priority,
        result,
        rejected: ctx.rejected,
        reason: ctx.rejectionReason,
        durationMs,
      });

      if (ctx.rejected) {
        break;
      }
    }

    return { passed: !ctx.rejected, log };
  }

  reset(): void {
    this.rules = [];
  }
}
