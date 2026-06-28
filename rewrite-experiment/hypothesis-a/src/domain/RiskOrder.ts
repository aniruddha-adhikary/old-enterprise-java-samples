import { RiskStatus, OrderSide } from './enums';

export interface RiskOrder {
  riskOrderId: string;
  sourceOrderId: string;
  clientId: string;
  symbol: string;
  quantity: number;
  side: OrderSide;
  price: number;
  notionalValue: number;
  exposureContribution: number;
  varContribution: number;
  riskStatus: RiskStatus;
  assessmentDate: Date;
}
