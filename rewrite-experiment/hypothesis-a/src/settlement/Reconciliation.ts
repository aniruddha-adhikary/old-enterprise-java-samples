import { SettlementStatus } from '../domain/enums';

export interface ReconciliationEntry {
  recordId: string;
  externalRef: string;
  status: string;
  amount: number;
  date: string;
  reasonCode: string;
}

export interface ReconciliationRepository {
  updateSettlementStatus(
    recordId: string,
    status: SettlementStatus,
    externalRef: string
  ): Promise<void>;
}

const DAT_STATUS_MAP: Record<string, SettlementStatus> = {
  CONF: SettlementStatus.RECONCILED,
  REJC: SettlementStatus.DISCREPANCY,
  DISC: SettlementStatus.DISCREPANCY,
};

export class ReconciliationProcessor {
  constructor(private readonly repo: ReconciliationRepository) {}

  async processXmlFile(xmlContent: string): Promise<number> {
    const entries = this.parseXml(xmlContent);
    return this.processEntries(entries);
  }

  async processDatFile(datContent: string): Promise<number> {
    const entries = this.parseDat(datContent);
    return this.processEntries(entries);
  }

  private async processEntries(entries: ReconciliationEntry[]): Promise<number> {
    let processed = 0;

    for (const entry of entries) {
      const mappedStatus = DAT_STATUS_MAP[entry.status];
      if (!mappedStatus) {
        // PEND status: skip (clearinghouse hasn't finished)
        continue;
      }

      // Reason codes are logged but NOT stored (BUG-015, JIRA-3522)
      if (entry.reasonCode) {
        console.log(
          `[Reconciliation] Record ${entry.recordId}: reason=${entry.reasonCode}`
        );
      }

      await this.repo.updateSettlementStatus(
        entry.recordId,
        mappedStatus,
        entry.externalRef
      );
      processed++;
    }

    return processed;
  }

  private parseDat(content: string): ReconciliationEntry[] {
    const lines = content.split('\n');
    const entries: ReconciliationEntry[] = [];

    for (const line of lines) {
      if (line.startsWith('HDR') || line.startsWith('TRL')) continue;
      if (line.trim().length < 58) continue;

      entries.push({
        recordId: line.substring(0, 20).trim(),
        externalRef: line.substring(20, 30).trim(),
        status: line.substring(30, 40).trim(),
        amount: parseFloat(line.substring(40, 50).trim()) || 0,
        date: line.substring(50, 58).trim(),
        reasonCode: line.length >= 78 ? line.substring(58, 78).trim() : '',
      });
    }

    return entries;
  }

  private parseXml(content: string): ReconciliationEntry[] {
    const entries: ReconciliationEntry[] = [];
    const recordRegex =
      /<Record>[\s\S]*?<RecordId>(.*?)<\/RecordId>[\s\S]*?<ExternalRef>(.*?)<\/ExternalRef>[\s\S]*?<Status>(.*?)<\/Status>[\s\S]*?<\/Record>/g;

    let match;
    while ((match = recordRegex.exec(content)) !== null) {
      entries.push({
        recordId: match[1],
        externalRef: match[2],
        status: match[3] === 'CONFIRMED' ? 'CONF' : match[3],
        amount: 0,
        date: '',
        reasonCode: '',
      });
    }

    return entries;
  }
}
