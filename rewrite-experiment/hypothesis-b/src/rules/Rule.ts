import { RuleContext } from '../domain/RuleContext';

export type RuleResult = 'PASS' | 'FAIL' | 'SKIP';

export interface RuleAuditEntry {
  ruleName: string;
  orderId: string;
  clientId: string;
  result: RuleResult;
  evaluationTime: Date;
  details: string;
}

export interface Rule {
  name: string;
  priority: number;
  category: string;
  failOpen: boolean;

  evaluate(ctx: RuleContext): RuleResult;
  execute(ctx: RuleContext): void;
}

export abstract class BaseRule implements Rule {
  abstract name: string;
  abstract priority: number;
  abstract category: string;
  abstract failOpen: boolean;

  abstract evaluate(ctx: RuleContext): RuleResult;

  execute(_ctx: RuleContext): void {
    // Default no-op; override in subclasses that need post-evaluate logic
  }
}
