import * as fs from 'fs';
import * as path from 'path';
import { TradeOrder } from '../domain/TradeOrder';

export interface RegulatoryOrderRepository {
  findFilledAndSettledOrders(): Promise<TradeOrder[]>;
}

export interface RegReportLogRepository {
  logReport(
    reportType: string,
    filePath: string,
    recordCount: number,
    status: string
  ): Promise<void>;
}

export class RegulatoryReporter {
  constructor(
    private readonly outputDir: string,
    private readonly firmName: string,
    private readonly orderRepo: RegulatoryOrderRepository,
    private readonly reportLogRepo: RegReportLogRepository
  ) {
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }
  }

  async generateDailyReport(): Promise<{
    datPath: string;
    xmlPath: string;
    recordCount: number;
  }> {
    const orders = await this.orderRepo.findFilledAndSettledOrders();
    // sort by order date ascending
    orders.sort(
      (a, b) => a.orderDate.getTime() - b.orderDate.getTime()
    );

    const timestamp = this.formatTimestamp(new Date());
    const fileTimestamp = timestamp.replace(/[:-]/g, '').replace('T', '_');

    const datPath = await this.generateDatReport(orders, fileTimestamp, timestamp);
    const xmlPath = await this.generateXmlReport(orders, fileTimestamp, timestamp);

    // Log report generation
    for (const [type, fp] of [
      ['DAILY_TRADE_REPORT_DAT', datPath],
      ['DAILY_TRADE_REPORT_XML', xmlPath],
    ]) {
      try {
        await this.reportLogRepo.logReport(
          type,
          fp,
          orders.length,
          'SUCCESS'
        );
      } catch {
        // logging failures never block reporting
      }
    }

    return { datPath, xmlPath, recordCount: orders.length };
  }

  private async generateDatReport(
    orders: TradeOrder[],
    fileTimestamp: string,
    timestamp: string
  ): Promise<string> {
    let content = '';

    // Header: "HDR" + firm name (30 chars) + timestamp (19 chars)
    content +=
      'HDR' +
      this.firmName.padEnd(30) +
      timestamp.substring(0, 19) +
      '\n';

    // Data lines
    for (const order of orders) {
      content += 'DTL';
      content += this.safe(order.orderId, '').padEnd(20);
      content += this.safe(order.clientId, '').padEnd(10);
      content += this.safe(order.symbol, '').padEnd(10);
      content += this.safe(order.side, '').padEnd(4);
      content += String(order.quantity ?? 0).padStart(12);
      content += (order.price ?? 0).toFixed(4).padStart(15);
      content += this.safe(order.status, '').padEnd(12);
      content += this.formatTimestamp(order.orderDate).substring(0, 19);
      content += '\n';
    }

    // Trailer: "TRL" + record count (10, right-justified) + timestamp (19)
    content +=
      'TRL' +
      String(orders.length).padStart(10) +
      timestamp.substring(0, 19) +
      '\n';

    const fileName = `REG_REPORT_${fileTimestamp}.dat`;
    const filePath = path.join(this.outputDir, fileName);
    fs.writeFileSync(filePath, content, 'utf-8');
    return filePath;
  }

  private async generateXmlReport(
    orders: TradeOrder[],
    fileTimestamp: string,
    timestamp: string
  ): Promise<string> {
    let xml = '<?xml version="1.0" encoding="UTF-8"?>\n';
    xml += '<RegulatoryReport>\n';
    xml += '  <Header>\n';
    xml += `    <Firm>${this.firmName}</Firm>\n`;
    xml += `    <ReportDate>${timestamp}</ReportDate>\n`;
    xml += '    <ReportType>DAILY_TRADE_REPORT</ReportType>\n';
    xml += '  </Header>\n';
    xml += '  <Trades>\n';

    for (const order of orders) {
      xml += '    <Trade>\n';
      xml += `      <OrderId>${this.safe(order.orderId, '')}</OrderId>\n`;
      xml += `      <ClientId>${this.safe(order.clientId, '')}</ClientId>\n`;
      xml += `      <Symbol>${this.safe(order.symbol, '')}</Symbol>\n`;
      xml += `      <Side>${this.safe(order.side, '')}</Side>\n`;
      xml += `      <Quantity>${order.quantity ?? 0}</Quantity>\n`;
      xml += `      <Price>${(order.price ?? 0).toFixed(4)}</Price>\n`;
      xml += `      <Status>${this.safe(order.status, '')}</Status>\n`;
      xml += `      <OrderDate>${this.formatTimestamp(order.orderDate)}</OrderDate>\n`;
      xml += '    </Trade>\n';
    }

    xml += '  </Trades>\n';
    xml += `  <Trailer><RecordCount>${orders.length}</RecordCount></Trailer>\n`;
    xml += '</RegulatoryReport>\n';

    const fileName = `REG_REPORT_${fileTimestamp}.xml`;
    const filePath = path.join(this.outputDir, fileName);
    fs.writeFileSync(filePath, xml, 'utf-8');
    return filePath;
  }

  /** Defensive null check -- null fields sent to regulator crash their parser (FR-REG-007). */
  private safe(value: string | null | undefined, fallback: string): string {
    return value ?? fallback;
  }

  private formatTimestamp(date: Date | null): string {
    if (!date) return '0000-00-00T00:00:00';
    return date.toISOString().substring(0, 19);
  }
}
