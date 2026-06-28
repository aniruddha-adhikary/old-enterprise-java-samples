import amqplib from 'amqplib';
import { MessageBroker, MessageHandler } from './MessageBroker';

export class RabbitMQBroker implements MessageBroker {
  private connection: Awaited<ReturnType<typeof amqplib.connect>> | null = null;
  private channel: Awaited<ReturnType<Awaited<ReturnType<typeof amqplib.connect>>['createChannel']>> | null = null;

  constructor(private readonly url: string) {}

  async connect(): Promise<void> {
    this.connection = await amqplib.connect(this.url);
    this.channel = await this.connection.createChannel();
    console.log('[RabbitMQ] Connected');
  }

  async publish(queue: string, message: string): Promise<void> {
    if (!this.channel) throw new Error('Not connected');
    await this.channel.assertQueue(queue, { durable: true });
    this.channel.sendToQueue(queue, Buffer.from(message), {
      persistent: true,
    });
  }

  async subscribe(queue: string, handler: MessageHandler): Promise<void> {
    if (!this.channel) throw new Error('Not connected');
    await this.channel.assertQueue(queue, { durable: true });
    await this.channel.consume(queue, async (msg) => {
      if (!msg) return;
      try {
        await handler.handle(msg.content.toString());
        this.channel?.ack(msg);
      } catch (error) {
        console.error(`[RabbitMQ] Handler error on ${queue}:`, error);
        this.channel?.nack(msg, false, true);
      }
    });
  }

  async close(): Promise<void> {
    await this.channel?.close();
    await this.connection?.close();
  }
}
