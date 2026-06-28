import { DerivativeContractType, DerivativeStatus } from './enums';

export interface DerivativeOrder {
  orderId: string;
  clientId: string;
  contractType: DerivativeContractType;
  underlying: string;
  strikePrice: number;
  quantity: number;
  expiry: Date | null;
  status: DerivativeStatus;
  premium: number;
}
