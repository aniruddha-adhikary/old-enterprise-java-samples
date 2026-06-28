export enum OrderStatus {
  NEW = 'NEW',
  VALIDATED = 'VALIDATED',
  PRICED = 'PRICED',
  REJECTED = 'REJECTED',
  FILLED = 'FILLED',
  SETTLED = 'SETTLED',
  /** Added for JIRA-2341 but never used in the original system */
  PENDING_REVIEW = 'PENDING_REVIEW',
  CANCELLED = 'CANCELLED',
}

export enum OrderSide {
  BUY = 'BUY',
  SELL = 'SELL',
}

export interface TradeOrder {
  orderId: string;
  clientId: string;
  symbol: string;
  quantity: number;
  side: OrderSide;
  price: number;
  requestedPrice: number;
  status: OrderStatus;
  orderDate: Date;
  lastModified: Date;
  notes: string | null;
}

export function createTradeOrder(partial: Partial<TradeOrder> & Pick<TradeOrder, 'orderId' | 'clientId' | 'symbol' | 'quantity' | 'side'>): TradeOrder {
  const now = new Date();
  return {
    price: 0,
    requestedPrice: 0,
    status: OrderStatus.NEW,
    orderDate: now,
    lastModified: now,
    notes: null,
    ...partial,
  };
}
