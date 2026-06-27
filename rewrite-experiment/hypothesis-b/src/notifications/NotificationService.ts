import { Notification, NotificationStatus, NotificationType, NotificationChannel, MAX_RETRY_COUNT } from '../domain/Notification';
import { TradeOrder } from '../domain/TradeOrder';
import { Client } from '../domain/Client';
import { SettlementRecord } from '../domain/SettlementRecord';

const HTML_BRAND_COLOR = '#003366';
const FOOTER_TEXT = 'This is an automated message from BigCorp Trading Systems. Do not reply to this email. For questions, contact the trading desk.';
const SMS_SENDER_TAG = 'BIGCORP';

export interface NotificationDispatcher {
  sendEmail(to: string, subject: string, htmlBody: string): Promise<boolean>;
  sendSms(to: string, body: string): Promise<boolean>;
}

export class NotificationService {
  private dispatcher: NotificationDispatcher | null = null;
  private notifications: Notification[] = [];

  setDispatcher(dispatcher: NotificationDispatcher): void {
    this.dispatcher = dispatcher;
  }

  getNotifications(): Notification[] {
    return [...this.notifications];
  }

  createOrderConfirmation(order: TradeOrder, client: Client): Notification {
    const notificationId = `N-${order.orderId}-${Date.now()}`;
    const subject = `Order Confirmation - ${order.orderId}`;
    const body = this.formatBody(order, client, 'CONFIRMED');

    const notification: Notification = {
      notificationId,
      type: NotificationType.ORDER_CONFIRM,
      recipient: client.email ?? '',
      subject,
      body,
      channel: 'EMAIL',
      status: NotificationStatus.PENDING,
      orderId: order.orderId,
      createdDate: new Date(),
      sentDate: null,
      retryCount: 0,
    };

    // BUG-008: INSERT-only; retried notifications create duplicate rows
    this.notifications.push(notification);
    return notification;
  }

  createOrderRejection(order: TradeOrder, client: Client, reason: string): Notification {
    const notificationId = `N-${order.orderId}-REJ-${Date.now()}`;
    const subject = `Order Rejected - ${order.orderId}`;
    const body = this.formatBody(order, client, 'REJECTED', reason);

    const notification: Notification = {
      notificationId,
      type: NotificationType.ORDER_REJECT,
      recipient: client.email ?? '',
      subject,
      body,
      channel: 'EMAIL',
      status: NotificationStatus.PENDING,
      orderId: order.orderId,
      createdDate: new Date(),
      sentDate: null,
      retryCount: 0,
    };

    this.notifications.push(notification);
    return notification;
  }

  createSettlementNotification(record: SettlementRecord, client: Client): Notification {
    const hash = record.recordId.split('').reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
    const notificationId = `NOTIF-${Date.now()}-${hash}`;
    const subject = `Settlement Confirmation - ${record.orderId}`;

    const notification: Notification = {
      notificationId,
      type: NotificationType.SETTLEMENT,
      recipient: client.email ?? '',
      subject,
      body: `${record.symbol}|${record.quantity}|${record.side}|${record.amount.toFixed(2)}||${record.amount.toFixed(2)}|${record.settlementDate?.toISOString() ?? ''}`,
      channel: 'EMAIL',
      status: NotificationStatus.PENDING,
      orderId: record.orderId,
      createdDate: new Date(),
      sentDate: null,
      retryCount: 0,
    };

    this.notifications.push(notification);
    return notification;
  }

  async dispatch(notification: Notification): Promise<boolean> {
    if (!this.dispatcher) {
      // Dev mode: log to console when dispatcher not configured
      console.log(`[NOTIFICATION] ${notification.channel}: ${notification.subject} -> ${notification.recipient}`);
      notification.status = NotificationStatus.SENT;
      notification.sentDate = new Date();
      return true;
    }

    try {
      let success = false;

      if (notification.channel === 'EMAIL') {
        const htmlBody = this.wrapHtml(notification.subject ?? '', notification.body ?? '');
        success = await this.dispatcher.sendEmail(notification.recipient, notification.subject ?? '', htmlBody);
      } else if (notification.channel === 'SMS') {
        success = await this.dispatcher.sendSms(notification.recipient, notification.body ?? '');
      }

      if (success) {
        notification.status = NotificationStatus.SENT;
        notification.sentDate = new Date();
      } else {
        notification.retryCount++;
        if (notification.retryCount >= MAX_RETRY_COUNT) {
          notification.status = NotificationStatus.FAILED;
        }
        // BUG-008: Re-insert creates duplicate rows (preserved behavior)
        this.notifications.push({ ...notification });
      }

      return success;
    } catch {
      notification.retryCount++;
      if (notification.retryCount >= MAX_RETRY_COUNT) {
        notification.status = NotificationStatus.FAILED;
      }
      this.notifications.push({ ...notification });
      return false;
    }
  }

  private formatBody(order: TradeOrder, client: Client, status: string, reason?: string): string {
    // Pipe-delimited format from spec
    return `${order.symbol}|${order.quantity}|${order.side}|${order.price.toFixed(2)}|${reason ?? ''}|${(order.quantity * order.price).toFixed(2)}|`;
  }

  private wrapHtml(subject: string, body: string): string {
    return `<!DOCTYPE html>
<html>
<head><title>${subject}</title></head>
<body style="font-family: Arial, sans-serif;">
<div style="border-top: 4px solid ${HTML_BRAND_COLOR}; padding: 20px;">
<h2 style="color: ${HTML_BRAND_COLOR};">${subject}</h2>
<p>${body.replace(/\|/g, ' | ')}</p>
<hr/>
<p style="font-size: 11px; color: #666;">${FOOTER_TEXT}</p>
</div>
</body>
</html>`;
  }
}
