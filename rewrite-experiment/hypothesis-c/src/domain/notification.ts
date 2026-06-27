export enum NotificationType {
  ORDER_CONFIRM = 'ORDER_CONFIRM',
  ORDER_REJECT = 'ORDER_REJECT',
  SETTLEMENT = 'SETTLEMENT',
  PRICE_ALERT = 'PRICE_ALERT',
}

export enum NotificationChannel {
  EMAIL = 'EMAIL',
  SMS = 'SMS',
  FAX = 'FAX',
}

export enum NotificationStatus {
  PENDING = 'PENDING',
  SENT = 'SENT',
  FAILED = 'FAILED',
}

export interface Notification {
  notificationId: string;
  type: NotificationType;
  recipient: string;
  subject: string | null;
  body: string | null;
  channel: NotificationChannel;
  status: NotificationStatus;
  orderId: string | null;
  createdDate: Date;
  sentDate: Date | null;
  retryCount: number;
}
