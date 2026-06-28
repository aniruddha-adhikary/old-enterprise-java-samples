import * as fs from 'fs';
import * as path from 'path';
import { SettlementRecord } from '../domain/SettlementRecord';

export class DefaultSettlementFileWriter {
  constructor(private readonly outputDir: string) {
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }
  }

  async writeXmlFile(
    records: SettlementRecord[],
    batchId: string
  ): Promise<string> {
    const timestamp = new Date().toISOString();
    let xml = '<?xml version="1.0" encoding="UTF-8"?>\n';
    xml += '<SettlementBatch>\n';
    xml += `  <BatchId>${batchId}</BatchId>\n`;
    xml += `  <Timestamp>${timestamp}</Timestamp>\n`;
    xml += `  <RecordCount>${records.length}</RecordCount>\n`;
    xml += '  <Records>\n';

    for (const rec of records) {
      xml += '    <Record>\n';
      xml += `      <RecordId>${rec.recordId}</RecordId>\n`;
      xml += `      <OrderId>${rec.orderId}</OrderId>\n`;
      xml += `      <ClientId>${rec.clientId}</ClientId>\n`;
      xml += `      <Symbol>${rec.symbol}</Symbol>\n`;
      xml += `      <Quantity>${rec.quantity}</Quantity>\n`;
      xml += `      <Side>${rec.side}</Side>\n`;
      xml += `      <Amount>${rec.amount.toFixed(4)}</Amount>\n`;
      xml += `      <Commission>${rec.commission.toFixed(4)}</Commission>\n`;
      xml += `      <TradeDate>${rec.tradeDate.toISOString()}</TradeDate>\n`;
      xml += `      <SettlementDate>${rec.settlementDate.toISOString()}</SettlementDate>\n`;
      xml += '    </Record>\n';
    }

    xml += '  </Records>\n';
    xml += '</SettlementBatch>\n';

    const fileName = `SETTLEMENT_${batchId}.xml`;
    const filePath = path.join(this.outputDir, fileName);
    fs.writeFileSync(filePath, xml, 'utf-8');
    return filePath;
  }

  async writeDatFile(
    records: SettlementRecord[],
    batchId: string
  ): Promise<string> {
    const timestamp = new Date().toISOString().slice(0, 19);
    let content = '';

    // Header
    content +=
      'HDR' +
      batchId.padEnd(30) +
      timestamp.padEnd(19) +
      '\n';

    // Data lines
    for (const rec of records) {
      content +=
        rec.recordId.padEnd(20) +
        rec.orderId.padEnd(20) +
        rec.clientId.padEnd(10) +
        rec.symbol.padEnd(10) +
        rec.side.padEnd(4) +
        String(rec.quantity).padStart(12) +
        rec.amount.toFixed(4).padStart(15) +
        rec.commission.toFixed(4).padStart(12) +
        '\n';
    }

    // Trailer
    content +=
      'TRL' +
      String(records.length).padStart(10) +
      timestamp.padEnd(19) +
      '\n';

    const fileName = `SETTLEMENT_${batchId}.dat`;
    const filePath = path.join(this.outputDir, fileName);
    fs.writeFileSync(filePath, content, 'utf-8');
    return filePath;
  }
}
