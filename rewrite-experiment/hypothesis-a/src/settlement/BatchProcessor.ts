import {
  SettlementRecord,
  calculateSettlementDate,
} from '../domain/SettlementRecord';
import { TradeOrder } from '../domain/TradeOrder';
import { Client } from '../domain/Client';
import {
  OrderStatus,
  SettlementStatus,
  NotificationType,
  NotificationChannel,
  OrderSide,
} from '../domain/enums';
import { calculateCommission } from '../pricing/CommissionCalculator';

export interface SettlementOrderRepository {
  findFilledOrders(): Promise<TradeOrder[]>;
  updateOrderStatus(orderId: string, status: OrderStatus): Promise<void>;
  getClient(clientId: string): Promise<Client | null>;
}

export interface SettlementRecordRepository {
  saveSettlementRecord(record: SettlementRecord): Promise<void>;
}

export interface SettlementNotifier {
  sendNotification(
    type: NotificationType,
    channel: NotificationChannel,
    recipient: string,
    orderId: string,
    body: string
  ): Promise<void>;
}

export interface SettlementFileWriter {
  writeXmlFile(records: SettlementRecord[], batchId: string): Promise<string>;
  writeDatFile(records: SettlementRecord[], batchId: string): Promise<string>;
}

export interface SettlementUploader {
  upload(filePath: string): Promise<boolean>;
  copyToFallback(filePath: string): Promise<void>;
}

export class BatchProcessor {
  private batchSequence = 0;

  constructor(
    private readonly orderRepo: SettlementOrderRepository,
    private readonly settlementRepo: SettlementRecordRepository,
    private readonly notifier: SettlementNotifier,
    private readonly fileWriter: SettlementFileWriter,
    private readonly uploader: SettlementUploader
  ) {}

  async processBatch(): Promise<SettlementRecord[]> {
    const filledOrders = await this.orderRepo.findFilledOrders();
    if (filledOrders.length === 0) return [];

    const batchId = this.generateBatchId();
    const records: SettlementRecord[] = [];

    for (const order of filledOrders) {
      const client = await this.orderRepo.getClient(order.clientId);
      const amount = order.quantity * order.price;
      const commission = calculateCommission(amount, client?.tier);
      const now = new Date();

      const record: SettlementRecord = {
        recordId: `SR-${now.getTime()}-${this.hashCode(order.orderId)}`,
        orderId: order.orderId,
        clientId: order.clientId,
        symbol: order.symbol,
        quantity: order.quantity,
        side: order.side as OrderSide,
        amount,
        commission,
        tradeDate: order.orderDate,
        settlementDate: calculateSettlementDate(order.orderDate),
        status: SettlementStatus.PENDING,
        batchId,
        externalRef: '',
      };

      await this.settlementRepo.saveSettlementRecord(record);
      records.push(record);

      await this.orderRepo.updateOrderStatus(
        order.orderId,
        OrderStatus.SETTLED
      );

      if (client?.email) {
        const body = `${order.symbol}|${order.quantity}|${order.side}|${order.price}||${amount}|${record.settlementDate.toISOString()}`;
        try {
          await this.notifier.sendNotification(
            NotificationType.SETTLEMENT,
            NotificationChannel.EMAIL,
            client.email,
            order.orderId,
            body
          );
        } catch {
          // notification failures do not fail settlement
        }
      }
    }

    // Generate settlement files
    const xmlPath = await this.fileWriter.writeXmlFile(records, batchId);
    const datPath = await this.fileWriter.writeDatFile(records, batchId);

    // Upload via SFTP with fallback
    for (const path of [xmlPath, datPath]) {
      const uploaded = await this.uploader.upload(path);
      if (!uploaded) {
        await this.uploader.copyToFallback(path);
      }
    }

    return records;
  }

  private generateBatchId(): string {
    this.batchSequence++;
    const date = new Date();
    const dateStr = date.toISOString().slice(0, 10).replace(/-/g, '');
    return `BATCH-${dateStr}-${String(this.batchSequence).padStart(3, '0')}`;
  }

  private hashCode(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash |= 0;
    }
    return Math.abs(hash);
  }
}
