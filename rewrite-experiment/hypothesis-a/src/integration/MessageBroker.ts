export interface MessageHandler {
  handle(message: string): Promise<void>;
}

export const QUEUES = {
  TRADE_ORDERS: 'bigcorp.trade.orders',
  TRADE_CONFIRMATIONS: 'bigcorp.trade.confirmations',
  NOTIFICATIONS: 'bigcorp.notifications',
  SETTLEMENT_EVENTS: 'bigcorp.settlement.events', // phantom queue (BUG-009)
  DERIVATIVES_ORDERS: 'bigcorp.derivatives.orders',
  DERIVATIVES_CONFIRMS: 'bigcorp.derivatives.confirms',
  DERIVATIVES_PRICING: 'bigcorp.derivatives.pricing',
  RISK_INBOUND: 'risk.orders.inbound',
  RISK_OUTBOUND: 'risk.results.outbound',
} as const;

export interface MessageBroker {
  connect(): Promise<void>;
  publish(queue: string, message: string): Promise<void>;
  subscribe(queue: string, handler: MessageHandler): Promise<void>;
  close(): Promise<void>;
}

export class InMemoryMessageBroker implements MessageBroker {
  private handlers: Map<string, MessageHandler[]> = new Map();
  private queues: Map<string, string[]> = new Map();

  async connect(): Promise<void> {
    console.log('[MessageBroker] In-memory broker connected');
  }

  async publish(queue: string, message: string): Promise<void> {
    const handlers = this.handlers.get(queue) || [];
    for (const handler of handlers) {
      try {
        await handler.handle(message);
      } catch (error) {
        console.error(
          `[MessageBroker] Handler error on ${queue}:`,
          error
        );
      }
    }

    const q = this.queues.get(queue) || [];
    q.push(message);
    this.queues.set(queue, q);
  }

  async subscribe(queue: string, handler: MessageHandler): Promise<void> {
    const handlers = this.handlers.get(queue) || [];
    handlers.push(handler);
    this.handlers.set(queue, handlers);
  }

  async close(): Promise<void> {
    this.handlers.clear();
    this.queues.clear();
  }
}
