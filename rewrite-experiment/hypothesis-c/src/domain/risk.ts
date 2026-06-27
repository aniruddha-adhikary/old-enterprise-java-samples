export enum RiskStatus {
  PENDING = 'PENDING',
  ASSESSED = 'ASSESSED',
  FLAGGED = 'FLAGGED',
  ERROR = 'ERROR',
}

export interface RiskAssessment {
  riskOrderId: string;
  sourceOrderId: string;
  clientId: string;
  symbol: string;
  quantity: number;
  side: string;
  price: number;
  notionalValue: number;
  exposureContribution: number;
  varContribution: number;
  riskStatus: RiskStatus;
  assessmentDate: Date;
}
