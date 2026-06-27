import { Rule, RuleAuditLogger } from './Rule';
import { RuleContext } from '../domain/RuleContext';
import { RuleResult } from '../domain/enums';

export interface RuleEngineConfig {
  priorityFixed: boolean;
}

export class RuleEngine {
  private rules: Rule[] = [];
  private readonly config: RuleEngineConfig;
  private readonly auditLogger: RuleAuditLogger;

  constructor(config: RuleEngineConfig, auditLogger: RuleAuditLogger) {
    this.config = config;
    this.auditLogger = auditLogger;
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
    if (this.config.priorityFixed) {
      // ASCENDING: lower priority number runs first (JIRA-5300 fix)
      this.rules.sort((a, b) => a.priority - b.priority);
    } else {
      // DESCENDING (legacy default): higher priority number runs first (BUG-001)
      this.rules.sort((a, b) => b.priority - a.priority);
    }
  }

  async evaluate(ctx: RuleContext): Promise<boolean> {
    for (const rule of this.rules) {
      if (!rule.isActive()) {
        await this.logAudit(rule, ctx, RuleResult.SKIP, 'Rule inactive');
        continue;
      }

      try {
        const passed = await rule.evaluate(ctx);

        if (!passed) {
          await this.logAudit(
            rule,
            ctx,
            RuleResult.FAIL,
            ctx.rejectionReason || 'Rule evaluation returned false'
          );
          return false; // chain stops immediately
        }

        await this.logAudit(rule, ctx, RuleResult.PASS, 'Evaluation passed');
      } catch (error) {
        const message =
          error instanceof Error ? error.message : String(error);
        ctx.reject(`Rule error: ${rule.name} - ${message}`);
        await this.logAudit(rule, ctx, RuleResult.FAIL, `Exception: ${message}`);
        return false;
      }

      // execute() phase: errors logged but do NOT fail the order (NFR-001, FR-RUL-005)
      try {
        await rule.execute(ctx);
      } catch (error) {
        const message =
          error instanceof Error ? error.message : String(error);
        console.error(
          `[RuleEngine] execute() error in ${rule.name}: ${message} (non-fatal)`
        );
      }
    }

    return true;
  }

  private async logAudit(
    rule: Rule,
    ctx: RuleContext,
    result: RuleResult,
    details: string
  ): Promise<void> {
    try {
      await this.auditLogger.logRuleDecision(
        rule.name,
        ctx.order.orderId,
        ctx.order.clientId,
        result,
        details
      );
    } catch {
      // audit logging failures never prevent order processing (NFR-001)
    }
  }

  getRules(): readonly Rule[] {
    return [...this.rules];
  }
}
