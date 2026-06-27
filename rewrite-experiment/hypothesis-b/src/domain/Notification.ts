import { z } from 'zod';

export const NotificationChannel = {
  EMAIL: 'EMAIL',
  SMS: 'SMS',
  FAX: 'FAX',
} as const;

export type NotificationChannel = (typeof NotificationChannel)[keyof typeof NotificationChannel];

export const NotificationStatus = {
  PENDING: 'PENDING',
  SENT: 'SENT',
  FAILED: 'FAILED',
} as const;

export type NotificationStatus = (typeof NotificationStatus)[keyof typeof NotificationStatus];

export const NotificationType = {
  ORDER_CONFIRM: 'ORDER_CONFIRM',
  ORDER_REJECT: 'ORDER_REJECT',
  SETTLEMENT: 'SETTLEMENT',
  PRICE_ALERT: 'PRICE_ALERT',
} as const;

export type NotificationType = (typeof NotificationType)[keyof typeof NotificationType];

export const NotificationSchema = z.object({
  notificationId: z.string().max(30),
  type: z.string().max(20),
  recipient: z.string().max(100),
  subject: z.string().max(200).nullable().default(null),
  body: z.string().max(2000).nullable().default(null),
  channel: z.enum(['EMAIL', 'SMS', 'FAX']),
  status: z.string().default('PENDING'),
  orderId: z.string().max(30).nullable().default(null),
  createdDate: z.date().default(() => new Date()),
  sentDate: z.date().nullable().default(null),
  retryCount: z.number().int().default(0),
});

export type Notification = z.infer<typeof NotificationSchema>;

export const MAX_RETRY_COUNT = 3;
