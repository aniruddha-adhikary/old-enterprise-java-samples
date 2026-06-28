import { z } from 'zod';

export const OrderStatus = {
  NEW: 'NEW',
  VALIDATED: 'VALIDATED',
  PRICED: 'PRICED',
  REJECTED: 'REJECTED',
  FILLED: 'FILLED',
  SETTLED: 'SETTLED',
  PENDING_REVIEW: 'PENDING_REVIEW',
  CANCELLED: 'CANCELLED',
  RECONCILED: 'RECONCILED',
  DISCREPANCY: 'DISCREPANCY',
} as const;

export type OrderStatus = (typeof OrderStatus)[keyof typeof OrderStatus];

export const OrderSide = {
  BUY: 'BUY',
  SELL: 'SELL',
} as const;

export type OrderSide = (typeof OrderSide)[keyof typeof OrderSide];

export const TradeOrderSchema = z.object({
  orderId: z.string().regex(/^ORD-\d+$/),
  clientId: z.string().max(20),
  symbol: z.string().max(10),
  quantity: z.number().int().positive(),
  side: z.enum(['BUY', 'SELL']),
  price: z.number().default(0),
  requestedPrice: z.number().default(0),
  status: z.string().default(OrderStatus.NEW),
  orderDate: z.date().default(() => new Date()),
  lastModified: z.date().default(() => new Date()),
  notes: z.string().max(500).nullable().default(null),
  surveillanceFlags: z.string().max(200).default(''),
});

export type TradeOrder = z.infer<typeof TradeOrderSchema>;

export function createTradeOrder(params: {
  clientId: string;
  symbol: string;
  quantity: number;
  side: OrderSide;
  requestedPrice: number;
  notes?: string | null;
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
    notes: params.notes ?? null,
    surveillanceFlags: '',
  };
}

export const ORDER_STATE_TRANSITIONS: Record<string, { to: string; condition: string }[]> = {
  [OrderStatus.NEW]: [
    { to: OrderStatus.FILLED, condition: 'All rules pass AND price obtained AND manual checks pass' },
    { to: OrderStatus.REJECTED, condition: 'Any rule fails OR price unavailable OR price deviation > 10%' },
  ],
  [OrderStatus.FILLED]: [
    { to: OrderStatus.SETTLED, condition: 'Settlement batch processes the order' },
  ],
  [OrderStatus.SETTLED]: [
    { to: OrderStatus.RECONCILED, condition: 'Clearinghouse returns CONF status' },
    { to: OrderStatus.DISCREPANCY, condition: 'Clearinghouse returns REJC or DISC status' },
  ],
};
