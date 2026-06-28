import { v4 as uuid } from 'uuid';
import { TradeOrder, OrderStatus } from '../domain/trade-order';
import { SettlementRecord, SettlementStatus } from '../domain/settlement';
import { CommissionCalculator } from '../pricing/commission-calculator';

/**
 * Settlement batch processor, preserving behavior from
 * settlement-gateway BatchProcessor.java.
 *
 * Steps:
 *   1. Find filled orders
 *   2. Create settlement records (with T+3 settlement date)
 *   3. Generate XML and flat files
 *   4. Upload to clearinghouse
 *   5. Send notifications
 *
 * KNOWN BUG (JIRA-2890): T+3 settlement date calculation does NOT
 * skip weekends. The clearinghouse recalculates on their end anyway.
 *
 * Settlement amount = (qty * price) - commission
 */
export class SettlementProcessor {
  private batchSequence = 1;

  /**
   * Calculate settlement date as T+3 calendar days.
   * BUG preserved: does not skip weekends or holidays.
   */
  calculateSettlementDate(tradeDate: Date): Date {
    const settlement = new Date(tradeDate);
    settlement.setDate(settlement.getDate() + 3);
    return settlement;
  }

  generateBatchId(): string {
    const now = new Date();
    const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');
    const seq = String(this.batchSequence++).padStart(3, '0');
    return `BATCH-${dateStr}-${seq}`;
  }

  createSettlementRecord(
    order: TradeOrder,
    batchId: string,
    clientTier: string
  ): SettlementRecord {
    const amount = order.quantity * order.price;
    const commission = CommissionCalculator.calculate(amount, clientTier);
    const settlementDate = this.calculateSettlementDate(order.orderDate);

    return {
      recordId: `SR-${Date.now()}-${uuid().slice(0, 8)}`,
      orderId: order.orderId,
      clientId: order.clientId,
      symbol: order.symbol,
      quantity: order.quantity,
      side: order.side,
      amount,
      commission,
      tradeDate: order.orderDate,
      settlementDate,
      status: SettlementStatus.PENDING,
      batchId,
      externalRef: null,
    };
  }

  /**
   * Generate XML settlement file content.
   * Format based on clearinghouse spec (fax dated 1999-11-12, ref CH-SPEC-004).
   */
  generateXmlFile(records: SettlementRecord[], batchId: string): string {
    const lines: string[] = [];
    lines.push('<?xml version="1.0" encoding="UTF-8"?>');
    lines.push('<settlementBatch>');
    lines.push('  <header>');
    lines.push(`    <batchId>${batchId}</batchId>`);
    lines.push(`    <generatedDate>${new Date().toISOString()}</generatedDate>`);
    lines.push(`    <recordCount>${records.length}</recordCount>`);
    lines.push('  </header>');
    lines.push('  <records>');

    for (const rec of records) {
      lines.push('    <record>');
      lines.push(`      <recordId>${rec.recordId}</recordId>`);
      lines.push(`      <orderId>${rec.orderId}</orderId>`);
      lines.push(`      <clientId>${rec.clientId}</clientId>`);
      lines.push(`      <symbol>${rec.symbol}</symbol>`);
      lines.push(`      <quantity>${rec.quantity}</quantity>`);
      lines.push(`      <side>${rec.side}</side>`);
      lines.push(`      <amount>${rec.amount.toFixed(2)}</amount>`);
      lines.push(`      <commission>${rec.commission.toFixed(4)}</commission>`);
      lines.push(`      <tradeDate>${rec.tradeDate.toISOString().slice(0, 10).replace(/-/g, '')}</tradeDate>`);
      lines.push(`      <settlementDate>${rec.settlementDate?.toISOString().slice(0, 10).replace(/-/g, '') || ''}</settlementDate>`);
      lines.push(`      <status>${rec.status}</status>`);
      lines.push('    </record>');
    }

    lines.push('  </records>');
    lines.push('</settlementBatch>');
    return lines.join('\n');
  }

  /**
   * Generate fixed-width flat file for the clearinghouse mainframe.
   * Column widths must not change or the clearinghouse rejects the batch.
   */
  generateFlatFile(records: SettlementRecord[], batchId: string): string {
    const lines: string[] = [];

    // Header record
    lines.push(
      'HDR' +
      batchId.padEnd(20) +
      new Date().toISOString().slice(0, 10).replace(/-/g, '').padEnd(8) +
      String(records.length).padStart(6, '0')
    );

    for (const rec of records) {
      lines.push(
        'DTL' +
        (rec.recordId || '').padEnd(20) +
        (rec.orderId || '').padEnd(20) +
        (rec.clientId || '').padEnd(10) +
        (rec.symbol || '').padEnd(10) +
        String(rec.quantity).padStart(10, '0') +
        (rec.side || '').padEnd(4) +
        rec.amount.toFixed(2).padStart(15, '0') +
        rec.commission.toFixed(4).padStart(12, '0') +
        (rec.tradeDate.toISOString().slice(0, 10).replace(/-/g, '')).padEnd(8) +
        (rec.settlementDate?.toISOString().slice(0, 10).replace(/-/g, '') || '').padEnd(8) +
        (rec.status || '').padEnd(10)
      );
    }

    // Trailer record
    const totalAmount = records.reduce((sum, r) => sum + r.amount, 0);
    lines.push(
      'TRL' +
      String(records.length).padStart(6, '0') +
      totalAmount.toFixed(2).padStart(18, '0')
    );

    return lines.join('\n');
  }
}
