import { TradeOrder } from './TradeOrder';
import { Client } from './Client';

export class RuleContext {
  readonly order: TradeOrder;
  readonly client: Client | null;
  private readonly attributes: Map<string, unknown> = new Map();
  private readonly _messages: string[] = [];
  private readonly _warnings: string[] = [];
  private _rejected = false;
  private _rejectionReason = '';

  constructor(order: TradeOrder, client: Client | null) {
    this.order = order;
    this.client = client;
  }

  getAttribute(key: string): unknown {
    return this.attributes.get(key);
  }

  setAttribute(key: string, value: unknown): void {
    this.attributes.set(key, value);
  }

  get messages(): string[] {
    return [...this._messages];
  }

  addMessage(msg: string): void {
    this._messages.push(msg);
  }

  get warnings(): string[] {
    return [...this._warnings];
  }

  addWarning(warning: string): void {
    this._warnings.push(warning);
  }

  get rejected(): boolean {
    return this._rejected;
  }

  get rejectionReason(): string {
    return this._rejectionReason;
  }

  reject(reason: string): void {
    this._rejected = true;
    this._rejectionReason = reason;
  }
}
