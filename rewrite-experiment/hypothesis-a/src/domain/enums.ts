export enum OrderStatus {
  NEW = 'NEW',
  VALIDATED = 'VALIDATED',       // unused, preserved for compatibility
  PRICED = 'PRICED',             // unused, preserved for compatibility
  REJECTED = 'REJECTED',
  FILLED = 'FILLED',
  SETTLED = 'SETTLED',
  PENDING_REVIEW = 'PENDING_REVIEW', // unused (JIRA-2341)
  CANCELLED = 'CANCELLED',      // only checked, never set in order lifecycle
  RECONCILED = 'RECONCILED',
  DISCREPANCY = 'DISCREPANCY',
}

export enum OrderSide {
  BUY = 'BUY',
  SELL = 'SELL',
}

export enum ClientTier {
  PLATINUM = 'PLATINUM',
  GOLD = 'GOLD',
  SILVER = 'SILVER',
  BRONZE = 'BRONZE',
}

export enum NotificationType {
  ORDER_CONFIRM = 'ORDER_CONFIRM',
  ORDER_REJECT = 'ORDER_REJECT',
  SETTLEMENT = 'SETTLEMENT',
  PRICE_ALERT = 'PRICE_ALERT',
}

export enum NotificationChannel {
  EMAIL = 'EMAIL',
  SMS = 'SMS',
  FAX = 'FAX', // deprecated since 2002
}

export enum NotificationStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  FAILED = 'FAILED',
}

export enum SettlementStatus {
  PENDING = 'PENDING',
  GENERATED = 'GENERATED',
  UPLOADED = 'UPLOADED',
  CONFIRMED = 'CONFIRMED',
  FAILED = 'FAILED',
  RECONCILED = 'RECONCILED',
  DISCREPANCY = 'DISCREPANCY',
}

export enum DerivativeContractType {
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

export enum RiskStatus {
  PENDING = 'PENDING',
  ASSESSED = 'ASSESSED',
  FLAGGED = 'FLAGGED',
  ERROR = 'ERROR',
}

export enum KycStatus {
  APPROVED = 'APPROVED',
  PENDING = 'PENDING',
  EXPIRED = 'EXPIRED',
  REJECTED = 'REJECTED',
}

export enum RuleResult {
  PASS = 'PASS',
  FAIL = 'FAIL',
  SKIP = 'SKIP',
}

export enum ReconciliationStatus {
  CONF = 'CONF',
  REJC = 'REJC',
  DISC = 'DISC',
  PEND = 'PEND',
}
