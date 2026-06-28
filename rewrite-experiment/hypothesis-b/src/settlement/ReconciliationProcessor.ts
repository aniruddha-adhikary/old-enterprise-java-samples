import { SettlementRecord, SettlementStatus } from '../domain/SettlementRecord';

// DAT reconciliation status mapping from spec.json
const DAT_STATUS_MAPPING: Record<string, string> = {
  CONF: 'RECONCILED',
  REJC: 'DISCREPANCY',
  DISC: 'DISCREPANCY',
  PEND: 'SKIPPED', // skipped
};

export interface ReconciliationEntry {
  recordId: string;
  externalRef: string;
  status: string;
  amount: number;
  date: string;
  reasonCode: string;
}

export class ReconciliationProcessor {
  // Parse fixed-width DAT file from clearinghouse
  parseDatFile(content: string): ReconciliationEntry[] {
    const lines = content.split('\n');
    const entries: ReconciliationEntry[] = [];

    for (const line of lines) {
      // Skip header (HDR) and trailer (TRL)
      if (line.startsWith('HDR') || line.startsWith('TRL') || line.trim() === '') {
        continue;
      }

      // Fixed-width format from spec:
      // recordId: 1-20 (width 20)
      // externalRef: 21-30 (width 10)
      // status: 31-40 (width 10)
      // amount: 41-50 (width 10, implied 2 decimals, right-justified)
      // date: 51-58 (width 8, YYYYMMDD)
      // reasonCode: 59-78 (width 20)
      const recordId = line.substring(0, 20).trim();
      const externalRef = line.substring(20, 30).trim();
      const status = line.substring(30, 40).trim();
      const amountStr = line.substring(40, 50).trim();
      const date = line.substring(50, 58).trim();
      const reasonCode = line.substring(58, 78).trim();

      // Amount has implied 2 decimals
      const amount = amountStr ? parseInt(amountStr, 10) / 100 : 0;

      entries.push({ recordId, externalRef, status, amount, date, reasonCode });
    }

    return entries;
  }

  // Parse XML reconciliation file
  parseXmlFile(content: string): ReconciliationEntry[] {
    const entries: ReconciliationEntry[] = [];
    const recordRegex = /<Record>([\s\S]*?)<\/Record>/g;
    let match;

    while ((match = recordRegex.exec(content)) !== null) {
      const block = match[1];
      const recordId = this.extractXmlField(block, 'RecordId');
      const externalRef = this.extractXmlField(block, 'ExternalRef');
      const status = this.extractXmlField(block, 'Status');
      const amount = parseFloat(this.extractXmlField(block, 'Amount') || '0');
      const date = this.extractXmlField(block, 'Date');
      const reasonCode = this.extractXmlField(block, 'ReasonCode');

      entries.push({ recordId, externalRef, status, amount, date, reasonCode });
    }

    return entries;
  }

  private extractXmlField(block: string, field: string): string {
    const regex = new RegExp(`<${field}>(.*?)</${field}>`);
    const match = regex.exec(block);
    return match ? match[1] : '';
  }

  // Apply reconciliation results to settlement records
  reconcile(records: Map<string, SettlementRecord>, entries: ReconciliationEntry[]): {
    reconciled: string[];
    discrepancies: string[];
    skipped: string[];
  } {
    const result = { reconciled: [] as string[], discrepancies: [] as string[], skipped: [] as string[] };

    for (const entry of entries) {
      const record = records.get(entry.recordId);
      if (!record) continue;

      const mappedStatus = DAT_STATUS_MAPPING[entry.status];
      if (!mappedStatus || mappedStatus === 'SKIPPED') {
        result.skipped.push(entry.recordId);
        continue;
      }

      record.externalRef = entry.externalRef;

      if (mappedStatus === 'RECONCILED') {
        record.status = SettlementStatus.RECONCILED;
        result.reconciled.push(entry.recordId);
      } else {
        record.status = SettlementStatus.DISCREPANCY;
        // BUG-015: Reason code not stored (no field for it)
        result.discrepancies.push(entry.recordId);
      }
    }

    return result;
  }
}
