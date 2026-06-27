import { TradeOrder, OrderStatus } from '../domain/TradeOrder';

// Regulatory reporting spec from spec.json regulatoryReporting
const FIRM_NAME = 'BIGCORP';
const REPORT_TYPE = 'DAILY_TRADE_REPORT';

export interface RegReportLog {
  reportType: string;
  filePath: string;
  recordCount: number;
  status: string;
  generationTime: Date;
}

export class RegulatoryReportGenerator {
  private reportLog: RegReportLog[] = [];

  getReportLog(): RegReportLog[] {
    return [...this.reportLog];
  }

  // Generate fixed-width regulatory report (REG-FW-001)
  generateFixedWidth(orders: TradeOrder[]): string {
    const now = new Date();
    const eligible = orders.filter(o =>
      o.status === OrderStatus.FILLED || o.status === OrderStatus.SETTLED
    ).sort((a, b) => a.orderDate.getTime() - b.orderDate.getTime());

    const timestamp = this.formatTimestamp(now);
    const lines: string[] = [];

    // Header: HDR + firmName(30) + timestamp(19)
    lines.push(`HDR${FIRM_NAME.padEnd(30)}${timestamp}`);

    // Data lines
    for (const order of eligible) {
      const line = [
        'DTL',
        order.orderId.padEnd(20),
        order.clientId.padEnd(10),
        order.symbol.padEnd(10),
        order.side.padEnd(4),
        String(order.quantity).padStart(12),
        order.price.toFixed(4).padStart(15),
        order.status.padEnd(12),
        this.formatTimestamp(order.orderDate),
      ].join('');
      lines.push(line);
    }

    // Trailer: TRL + recordCount(10, right) + timestamp(19)
    lines.push(`TRL${String(eligible.length).padStart(10)}${timestamp}`);

    const filePath = `./regulatory-output/REG_REPORT_${this.formatFileTimestamp(now)}.dat`;
    this.reportLog.push({
      reportType: REPORT_TYPE,
      filePath,
      recordCount: eligible.length,
      status: 'GENERATED',
      generationTime: now,
    });

    return lines.join('\n');
  }

  // Generate XML regulatory report (REG-XML-001)
  generateXml(orders: TradeOrder[]): string {
    const now = new Date();
    const eligible = orders.filter(o =>
      o.status === OrderStatus.FILLED || o.status === OrderStatus.SETTLED
    ).sort((a, b) => a.orderDate.getTime() - b.orderDate.getTime());

    const lines: string[] = [
      '<?xml version="1.0" encoding="UTF-8"?>',
      `<RegulatoryReport firm="${FIRM_NAME}" generatedAt="${now.toISOString()}">`,
    ];

    for (const order of eligible) {
      lines.push('  <Trade>');
      lines.push(`    <OrderId>${order.orderId}</OrderId>`);
      lines.push(`    <ClientId>${order.clientId}</ClientId>`);
      lines.push(`    <Symbol>${order.symbol}</Symbol>`);
      lines.push(`    <Side>${order.side}</Side>`);
      lines.push(`    <Quantity>${order.quantity}</Quantity>`);
      lines.push(`    <Price>${order.price.toFixed(4)}</Price>`);
      lines.push(`    <Status>${order.status}</Status>`);
      lines.push(`    <OrderDate>${order.orderDate.toISOString()}</OrderDate>`);
      lines.push('  </Trade>');
    }

    lines.push('</RegulatoryReport>');

    const filePath = `./regulatory-output/REG_REPORT_${this.formatFileTimestamp(now)}.xml`;
    this.reportLog.push({
      reportType: REPORT_TYPE,
      filePath,
      recordCount: eligible.length,
      status: 'GENERATED',
      generationTime: now,
    });

    return lines.join('\n');
  }

  private formatTimestamp(date: Date): string {
    return date.toISOString().replace('T', ' ').substring(0, 19);
  }

  private formatFileTimestamp(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const h = String(date.getHours()).padStart(2, '0');
    const min = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    return `${y}${m}${d}_${h}${min}${s}`;
  }
}
