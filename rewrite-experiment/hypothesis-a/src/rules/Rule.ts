import { RuleContext } from '../domain/RuleContext';

export interface Rule {
  readonly name: string;
  readonly priority: number;
  readonly category: string;

  isActive(): boolean;
  evaluate(ctx: RuleContext): Promise<boolean>;
  execute(ctx: RuleContext): Promise<void>;
}

export interface RuleAuditLogger {
  logRuleDecision(
    ruleName: string,
    orderId: string,
    clientId: string,
    result: string,
    details: string
  ): Promise<void>;

  logSurveillanceDecision(
    ruleName: string,
    orderId: string,
    clientId: string,
    symbol: string,
    result: string,
    flags: string,
    details: string
  ): Promise<void>;
}

export interface OrderRepository {
  countNonCancelledOrders(clientId: string, symbol: string): Promise<number>;
  getCancelledOrderCount(clientId: string): Promise<number>;
  getTotalOrderCount(clientId: string): Promise<number>;
  findRecentOppositeOrders(
    clientId: string,
    symbol: string,
    side: string,
    windowMinutes: number
  ): Promise<number>;
}

export interface PositionRepository {
  getNetPosition(clientId: string, symbol: string): Promise<number | null>;
}

export interface ClientRepository {
  getKillSwitch(clientId: string): Promise<string>;
  getKycStatus(clientId: string): Promise<string | null>;
}
