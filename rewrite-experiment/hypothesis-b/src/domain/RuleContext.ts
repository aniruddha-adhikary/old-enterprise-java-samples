import { TradeOrder } from './TradeOrder';
import { Client } from './Client';

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

export function addWarning(ctx: RuleContext, warning: string): void {
  ctx.warnings.push(warning);
}

export function addMessage(ctx: RuleContext, message: string): void {
  ctx.messages.push(message);
}

export function setAttribute(ctx: RuleContext, key: string, value: unknown): void {
  ctx.attributes.set(key, value);
}

export function getAttribute(ctx: RuleContext, key: string): unknown {
  return ctx.attributes.get(key);
}
