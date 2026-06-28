export enum ContractType {
  FX_SPOT = 'FX_SPOT',
  FX_FORWARD = 'FX_FORWARD',
  OPTION_CALL = 'OPTION_CALL',
  OPTION_PUT = 'OPTION_PUT',
}

export enum DerivativeStatus {
  NEW = 'NEW',
  FILLED = 'FILLED',
  REJECTED = 'REJECTED',
}

export interface DerivativeOrder {
  orderId: string;
  clientId: string;
  contractType: ContractType;
  underlying: string;
  strikePrice: number;
  quantity: number;
  expiry: string | null;
  status: DerivativeStatus;
  premium: number;
}
