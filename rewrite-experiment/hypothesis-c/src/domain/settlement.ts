export enum SettlementStatus {
  PENDING = 'PENDING',
  UPLOADED = 'UPLOADED',
  CONFIRMED = 'CONFIRMED',
  FAILED = 'FAILED',
  RECONCILED = 'RECONCILED',
}

export interface SettlementRecord {
  recordId: string;
  orderId: string;
  clientId: string;
  symbol: string;
  quantity: number;
  side: string;
  amount: number;
  commission: number;
  tradeDate: Date;
  settlementDate: Date | null;
  status: SettlementStatus;
  batchId: string | null;
  externalRef: string | null;
}
