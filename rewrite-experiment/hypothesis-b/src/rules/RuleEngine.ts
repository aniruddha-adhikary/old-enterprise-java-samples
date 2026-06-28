import { RuleContext, setAttribute } from '../domain/RuleContext';
import { Rule, RuleResult, RuleAuditEntry } from './Rule';

export interface RuleEngineConfig {
  priorityFixed: boolean;
  marketHalted: boolean;
}

const defaultConfig: RuleEngineConfig = {
  priorityFixed: false,
  marketHalted: false,
};

export class RuleEngine {
  private static instance: RuleEngine | null = null;
  private rules: Rule[] = [];
  private config: RuleEngineConfig;
  private auditLog: RuleAuditEntry[] = [];

  private constructor(config?: Partial<RuleEngineConfig>) {
    this.config = { ...defaultConfig, ...config };
  }

  // BUG-005: Non-thread-safe singleton (preserved behavior, though Node is single-threaded)
  static getInstance(config?: Partial<RuleEngineConfig>): RuleEngine {
    if (!RuleEngine.instance) {
      RuleEngine.instance = new RuleEngine(config);
    }
    return RuleEngine.instance;
  }

  static resetInstance(): void {
    RuleEngine.instance = null;
  }

  getConfig(): RuleEngineConfig {
    return this.config;
  }

  setConfig(config: Partial<RuleEngineConfig>): void {
    this.config = { ...this.config, ...config };
  }

  registerRule(rule: Rule): void {
    this.rules.push(rule);
    this.sortRules();
  }

  registerRules(rules: Rule[]): void {
    this.rules.push(...rules);
    this.sortRules();
  }

  private sortRules(): void {
    // BUG-001: Priority Comparator Reversed — DESCENDING by default (higher number runs first)
    // Can be "fixed" with priorityFixed config, but defaults to false
    if (this.config.priorityFixed) {
      this.rules.sort((a, b) => a.priority - b.priority);
    } else {
      this.rules.sort((a, b) => b.priority - a.priority);
    }
  }

  getRules(): Rule[] {
    return [...this.rules];
  }

  getAuditLog(): RuleAuditEntry[] {
    return [...this.auditLog];
  }

  clearAuditLog(): void {
    this.auditLog = [];
  }

  evaluateAll(ctx: RuleContext): void {
    for (const rule of this.rules) {
      let result: RuleResult;

      try {
        result = rule.evaluate(ctx);
      } catch (err) {
        // behaviorOnEvaluateException: REJECT
        result = 'FAIL';
        ctx.rejected = true;
        ctx.rejectionReason = `Rule ${rule.name} threw exception: ${(err as Error).message}`;
      }

      this.auditLog.push({
        ruleName: rule.name,
        orderId: ctx.order.orderId,
        clientId: ctx.order.clientId,
        result,
        evaluationTime: new Date(),
        details: result === 'FAIL' ? (ctx.rejectionReason ?? '') : '',
      });

      if (result === 'FAIL' && ctx.rejected) {
        break;
      }

      // Execute post-evaluate logic
      try {
        rule.execute(ctx);
      } catch (err) {
        // behaviorOnExecuteException: LOG_AND_CONTINUE
        setAttribute(ctx, `${rule.name}_execute_error`, (err as Error).message);
      }
    }
  }
}
