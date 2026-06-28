/**
 * Modern message queue abstraction replacing JMS/ActiveMQ.
 * Uses RabbitMQ (AMQP) in production, in-memory for dev/testing.
 *
 * Queue names preserved from MessageQueueHelper.java and
 * DerivativeQueueConstants.java:
 *   BIGCORP.TRADE.ORDERS        - inbound trade orders
 *   BIGCORP.TRADE.CONFIRMATIONS - outbound confirmations
 *   BIGCORP.NOTIFICATIONS       - notification dispatch
 *   BIGCORP.SETTLEMENT.EVENTS   - settlement events (originally created
 *                                  for a cancelled project but removing
 *                                  it breaks something in settlement)
 *   BIGCORP.DERIVATIVES.ORDERS  - derivative order inbound
 *   BIGCORP.DERIVATIVES.CONFIRMS - derivative confirmations
 *   BIGCORP.DERIVATIVES.PRICING  - derivative pricing requests (future use)
 *   RISK.ORDERS.INBOUND          - risk engine inbound
 *   RISK.RESULTS.OUTBOUND        - risk engine results
 */
export const QUEUES = {
  TRADE_ORDERS: 'BIGCORP.TRADE.ORDERS',
  TRADE_CONFIRMATIONS: 'BIGCORP.TRADE.CONFIRMATIONS',
  NOTIFICATIONS: 'BIGCORP.NOTIFICATIONS',
  SETTLEMENT_EVENTS: 'BIGCORP.SETTLEMENT.EVENTS',
  DERIVATIVE_ORDERS: 'BIGCORP.DERIVATIVES.ORDERS',
  DERIVATIVE_CONFIRMS: 'BIGCORP.DERIVATIVES.CONFIRMS',
  DERIVATIVE_PRICING: 'BIGCORP.DERIVATIVES.PRICING',
  RISK_INBOUND: 'RISK.ORDERS.INBOUND',
  RISK_RESULTS: 'RISK.RESULTS.OUTBOUND',
} as const;

export type MessageHandler = (message: string) => void;

export interface MessageBroker {
  publish(queue: string, message: string): Promise<void>;
  subscribe(queue: string, handler: MessageHandler): Promise<void>;
  close(): Promise<void>;
}

/**
 * In-memory message broker for development and testing.
 */
export class InMemoryBroker implements MessageBroker {
  private handlers = new Map<string, MessageHandler[]>();
  private messages = new Map<string, string[]>();

  async publish(queue: string, message: string): Promise<void> {
    const handlers = this.handlers.get(queue) || [];
    for (const handler of handlers) {
      handler(message);
    }

    const msgs = this.messages.get(queue) || [];
    msgs.push(message);
    this.messages.set(queue, msgs);
  }

  async subscribe(queue: string, handler: MessageHandler): Promise<void> {
    const handlers = this.handlers.get(queue) || [];
    handlers.push(handler);
    this.handlers.set(queue, handlers);
  }

  async close(): Promise<void> {
    this.handlers.clear();
    this.messages.clear();
  }

  getMessages(queue: string): string[] {
    return this.messages.get(queue) || [];
  }
}
