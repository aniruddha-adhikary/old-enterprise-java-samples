// Modern equivalent of JMS/ActiveMQ: AMQP via RabbitMQ
// Maps legacy queues to modern topic/queue equivalents

export const QUEUES = {
  TRADE_ORDERS: 'bigcorp.trade.orders',          // BIGCORP.TRADE.ORDERS
  TRADE_CONFIRMATIONS: 'bigcorp.trade.confirmations', // BIGCORP.TRADE.CONFIRMATIONS
  NOTIFICATIONS: 'bigcorp.notifications',          // BIGCORP.NOTIFICATIONS
  SETTLEMENT_EVENTS: 'bigcorp.settlement.events',  // BUG-009: phantom dependency
  DERIVATIVES_ORDERS: 'bigcorp.derivatives.orders', // BIGCORP.DERIVATIVES.ORDERS
  DERIVATIVES_CONFIRMS: 'bigcorp.derivatives.confirms', // BIGCORP.DERIVATIVES.CONFIRMS
  DERIVATIVES_PRICING: 'bigcorp.derivatives.pricing', // BIGCORP.DERIVATIVES.PRICING
  RISK_INBOUND: 'risk.orders.inbound',            // RISK.ORDERS.INBOUND
  RISK_OUTBOUND: 'risk.results.outbound',          // RISK.RESULTS.OUTBOUND
} as const;

export type MessageHandler = (message: unknown) => Promise<void>;

export interface MessageBrokerConfig {
  url: string;
  acknowledgeMode: 'auto' | 'manual';
}

export class MessageBroker {
  private handlers: Map<string, MessageHandler[]> = new Map();
  private connected = false;
  private config: MessageBrokerConfig;

  constructor(config?: Partial<MessageBrokerConfig>) {
    this.config = {
      url: config?.url ?? 'amqp://localhost:5672',
      acknowledgeMode: config?.acknowledgeMode ?? 'auto',
    };
  }

  async connect(): Promise<void> {
    // In production, connect to RabbitMQ via amqplib
    this.connected = true;
  }

  async disconnect(): Promise<void> {
    this.connected = false;
  }

  isConnected(): boolean {
    return this.connected;
  }

  subscribe(queue: string, handler: MessageHandler): void {
    if (!this.handlers.has(queue)) {
      this.handlers.set(queue, []);
    }
    this.handlers.get(queue)!.push(handler);
  }

  async publish(queue: string, message: unknown): Promise<void> {
    // In-memory dispatch for dev/testing
    const handlers = this.handlers.get(queue) ?? [];
    for (const handler of handlers) {
      await handler(message);
    }
  }

  getSubscriberCount(queue: string): number {
    return this.handlers.get(queue)?.length ?? 0;
  }
}
