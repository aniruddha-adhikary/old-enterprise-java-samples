import { SettlementStatus, OrderSide } from './enums';

export interface SettlementRecord {
  recordId: string;
  orderId: string;
  clientId: string;
  symbol: string;
  quantity: number;
  side: OrderSide;
  amount: number;
  commission: number;
  tradeDate: Date;
  settlementDate: Date;
  status: SettlementStatus;
  batchId: string;
  externalRef: string;
}

/**
 * T+3 settlement date calculation.
 * Known legacy behavior: adds 3 calendar days without skipping weekends/holidays (BUG-004).
 * Preserved for backward compatibility -- clearinghouse recalculates on their end.
 */
export function calculateSettlementDate(tradeDate: Date): Date {
  const settlement = new Date(tradeDate);
  settlement.setDate(settlement.getDate() + 3);
  return settlement;
}
