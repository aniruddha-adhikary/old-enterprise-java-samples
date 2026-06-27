import { OrderStatus, OrderSide } from './enums';

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
  notes: string;
  surveillanceFlags: string;
}

export function createTradeOrder(params: {
  clientId: string;
  symbol: string;
  quantity: number;
  side: OrderSide;
  requestedPrice: number;
}): TradeOrder {
  const now = new Date();
  return {
    orderId: `ORD-${now.getTime()}`,
    clientId: params.clientId,
    symbol: params.symbol,
    quantity: params.quantity,
    side: params.side,
    price: 0,
    requestedPrice: params.requestedPrice,
    status: OrderStatus.NEW,
    orderDate: now,
    lastModified: now,
    notes: '',
    surveillanceFlags: '',
  };
}
