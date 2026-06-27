import { TradeOrder, OrderStatus } from '../domain/TradeOrder';
import { SettlementRecord, SettlementStatus, calculateSettlementDate, generateRecordId, generateBatchId } from '../domain/SettlementRecord';
import { calculateCommission } from '../pricing/CommissionCalculator';
import { Client } from '../domain/Client';

export interface SettlementBatchResult {
  batchId: string;
  records: SettlementRecord[];
  xmlContent: string;
  datContent: string;
  recordCount: number;
}

export class SettlementService {
  private batchSequence = 0;

  processBatch(filledOrders: TradeOrder[], clients: Map<string, Client>): SettlementBatchResult {
    const now = new Date();
    this.batchSequence++;
    const batchId = generateBatchId(now, this.batchSequence);

    const records: SettlementRecord[] = filledOrders
      .filter(o => o.status === OrderStatus.FILLED)
      .map(order => {
        const client = clients.get(order.clientId);
        const tier = client?.tier ?? 'BRONZE';
        const amount = order.quantity * order.price;
        const commission = calculateCommission(amount, tier);

        return {
          recordId: generateRecordId(order.orderId),
          orderId: order.orderId,
          clientId: order.clientId,
          symbol: order.symbol,
          quantity: order.quantity,
          side: order.side as 'BUY' | 'SELL',
          amount,
          commission,
          tradeDate: order.orderDate,
          // BUG-004: T+3 calendar days, does NOT skip weekends
          settlementDate: calculateSettlementDate(order.orderDate),
          status: SettlementStatus.GENERATED,
          batchId,
          externalRef: null,
        };
      });

    const xmlContent = this.generateXml(records, batchId);
    const datContent = this.generateDat(records, batchId);

    return {
      batchId,
      records,
      xmlContent,
      datContent,
      recordCount: records.length,
    };
  }

  private generateXml(records: SettlementRecord[], batchId: string): string {
    const lines: string[] = [
      '<?xml version="1.0" encoding="UTF-8"?>',
      `<SettlementBatch batchId="${batchId}" recordCount="${records.length}">`,
    ];

    for (const rec of records) {
      lines.push('  <Record>');
      lines.push(`    <RecordId>${rec.recordId}</RecordId>`);
      lines.push(`    <OrderId>${rec.orderId}</OrderId>`);
      lines.push(`    <ClientId>${rec.clientId}</ClientId>`);
      lines.push(`    <Symbol>${rec.symbol}</Symbol>`);
      lines.push(`    <Quantity>${rec.quantity}</Quantity>`);
      lines.push(`    <Side>${rec.side}</Side>`);
      lines.push(`    <Amount>${rec.amount.toFixed(4)}</Amount>`);
      lines.push(`    <Commission>${rec.commission.toFixed(4)}</Commission>`);
      lines.push(`    <TradeDate>${rec.tradeDate.toISOString()}</TradeDate>`);
      lines.push(`    <SettlementDate>${rec.settlementDate?.toISOString() ?? ''}</SettlementDate>`);
      lines.push('  </Record>');
    }

    lines.push('</SettlementBatch>');
    return lines.join('\n');
  }

  private generateDat(records: SettlementRecord[], batchId: string): string {
    // Fixed-width flat file format
    const lines: string[] = [];
    lines.push(`HDR${batchId.padEnd(77)}`);

    for (const rec of records) {
      const line = [
        rec.recordId.padEnd(20),
        rec.orderId.padEnd(20),
        rec.clientId.padEnd(10),
        rec.symbol.padEnd(10),
        String(rec.quantity).padStart(10),
        rec.side.padEnd(4),
        rec.amount.toFixed(2).padStart(15),
      ].join('');
      lines.push(line);
    }

    lines.push(`TRL${String(records.length).padStart(10)}`);
    return lines.join('\n');
  }
}
