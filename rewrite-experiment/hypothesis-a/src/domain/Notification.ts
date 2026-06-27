import { NotificationType, NotificationChannel, NotificationStatus } from './enums';

export interface Notification {
  notificationId: string;
  notificationType: NotificationType;
  recipient: string;
  subject: string;
  body: string;
  channel: NotificationChannel;
  status: NotificationStatus;
  orderId: string;
  createdDate: Date;
  sentDate: Date | null;
  retryCount: number;
}
