import { TradeOrder } from '../domain/trade-order';

/**
 * Regulatory reporting export, preserving behavior from
 * settlement-gateway RegulatoryExportJob.java.
 *
 * Generates fixed-width (REG-FW-001) and XML (REG-XML-001) reports
 * for submission to the regulator. Added after 2021 regulatory review
 * (REG-2021-001).
 *
 * Field widths per REG-FW-001:
 *   ORDER_ID:  20
 *   CLIENT_ID: 10
 *   SYMBOL:    10
 *   SIDE:       4
 *   QTY:       12
 *   PRICE:     15
 *   STATUS:    12
 *   DATE:      19
 */
export class RegulatoryExport {
  static readonly FW_ORDER_ID = 20;
  static readonly FW_CLIENT_ID = 10;
  static readonly FW_SYMBOL = 10;
  static readonly FW_SIDE = 4;
  static readonly FW_QTY = 12;
  static readonly FW_PRICE = 15;
  static readonly FW_STATUS = 12;
  static readonly FW_DATE = 19;

  generateFixedWidthReport(orders: TradeOrder[]): string {
    const lines: string[] = [];
    const now = new Date().toISOString().slice(0, 19);

    // Header record
    lines.push('HDR' + 'BIGCORP_REG_REPORT'.padEnd(30) + now.padEnd(19));

    for (const order of orders) {
      lines.push(
        'DTL' +
        (order.orderId || '').padEnd(RegulatoryExport.FW_ORDER_ID) +
        (order.clientId || '').padEnd(RegulatoryExport.FW_CLIENT_ID) +
        (order.symbol || '').padEnd(RegulatoryExport.FW_SYMBOL) +
        (order.side || '').padEnd(RegulatoryExport.FW_SIDE) +
        String(order.quantity).padStart(RegulatoryExport.FW_QTY, '0') +
        order.price.toFixed(4).padStart(RegulatoryExport.FW_PRICE, '0') +
        (order.status || '').padEnd(RegulatoryExport.FW_STATUS) +
        (order.orderDate?.toISOString().slice(0, 19) || '').padEnd(RegulatoryExport.FW_DATE)
      );
    }

    // Trailer
    lines.push('TRL' + String(orders.length).padStart(10, '0'));

    return lines.join('\n');
  }

  generateXmlReport(orders: TradeOrder[]): string {
    const lines: string[] = [];
    const now = new Date().toISOString();

    lines.push('<?xml version="1.0" encoding="UTF-8"?>');
    lines.push('<regulatoryReport>');
    lines.push(`  <header>`);
    lines.push(`    <reportType>DAILY_TRADE</reportType>`);
    lines.push(`    <submitter>BIGCORP</submitter>`);
    lines.push(`    <generatedDate>${now}</generatedDate>`);
    lines.push(`    <recordCount>${orders.length}</recordCount>`);
    lines.push(`  </header>`);
    lines.push(`  <trades>`);

    for (const order of orders) {
      lines.push('    <trade>');
      lines.push(`      <orderId>${order.orderId || ''}</orderId>`);
      lines.push(`      <clientId>${order.clientId || ''}</clientId>`);
      lines.push(`      <symbol>${order.symbol || ''}</symbol>`);
      lines.push(`      <side>${order.side || ''}</side>`);
      lines.push(`      <quantity>${order.quantity}</quantity>`);
      lines.push(`      <price>${order.price.toFixed(4)}</price>`);
      lines.push(`      <status>${order.status || ''}</status>`);
      lines.push(`      <orderDate>${order.orderDate?.toISOString() || ''}</orderDate>`);
      lines.push('    </trade>');
    }

    lines.push('  </trades>');
    lines.push('</regulatoryReport>');

    return lines.join('\n');
  }
}
