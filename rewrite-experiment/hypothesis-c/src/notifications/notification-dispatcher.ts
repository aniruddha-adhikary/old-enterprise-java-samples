import { Notification, NotificationChannel, NotificationStatus, NotificationType } from '../domain/notification';

/**
 * Notification dispatcher, preserving behavior from notification-gateway.
 *
 * Channels: EMAIL, SMS, FAX
 * Templates: ORDER_CONFIRM, ORDER_REJECT, SETTLEMENT, PRICE_ALERT
 *
 * KNOWN BUG (SMS): Phone number formatting strips leading '+' character.
 * International carriers (e.g., Singapore) need the + prefix.
 * Ticket #4521 filed but not fixed.
 *
 * SMS messages truncated to 160 chars.
 * Email supports HTML with inline CSS (for Outlook 2000 compatibility).
 */
export class NotificationDispatcher {
  private devMode: boolean;
  private dispatched: Notification[] = [];

  constructor(devMode = true) {
    this.devMode = devMode;
  }

  async dispatch(notification: Notification): Promise<boolean> {
    try {
      switch (notification.channel) {
        case NotificationChannel.EMAIL:
          return await this.sendEmail(notification);
        case NotificationChannel.SMS:
          return await this.sendSMS(notification);
        case NotificationChannel.FAX:
          return this.sendFax(notification);
        default:
          console.warn(`Unknown notification channel: ${notification.channel}`);
          return false;
      }
    } catch (err) {
      notification.status = NotificationStatus.FAILED;
      notification.retryCount++;
      return false;
    }
  }

  private async sendEmail(notif: Notification): Promise<boolean> {
    const body = this.buildEmailBody(notif);

    if (this.devMode) {
      console.log('--- EMAIL (dev mode) ---');
      console.log(`To: ${notif.recipient}`);
      console.log(`Subject: ${notif.subject}`);
      console.log(`Body: ${body}`);
      console.log('--- END EMAIL ---');
      notif.status = NotificationStatus.SENT;
      notif.sentDate = new Date();
      this.dispatched.push(notif);
      return true;
    }

    notif.status = NotificationStatus.SENT;
    notif.sentDate = new Date();
    this.dispatched.push(notif);
    return true;
  }

  private async sendSMS(notif: Notification): Promise<boolean> {
    const phone = this.formatPhoneNumber(notif.recipient);
    let message = notif.body || '';

    // SMS 160-char limit
    if (message.length > 160) {
      message = message.substring(0, 157) + '...';
    }

    if (this.devMode) {
      console.log('--- SMS (dev mode) ---');
      console.log(`To: ${phone}`);
      console.log(`Message: ${message}`);
      console.log('--- END SMS ---');
      notif.status = NotificationStatus.SENT;
      notif.sentDate = new Date();
      this.dispatched.push(notif);
      return true;
    }

    notif.status = NotificationStatus.SENT;
    notif.sentDate = new Date();
    this.dispatched.push(notif);
    return true;
  }

  private sendFax(notif: Notification): boolean {
    if (this.devMode) {
      console.log('--- FAX (dev mode) ---');
      console.log(`To: ${notif.recipient}`);
      console.log(`Body: ${notif.body}`);
      console.log('--- END FAX ---');
      notif.status = NotificationStatus.SENT;
      notif.sentDate = new Date();
      this.dispatched.push(notif);
      return true;
    }

    notif.status = NotificationStatus.SENT;
    notif.sentDate = new Date();
    this.dispatched.push(notif);
    return true;
  }

  /**
   * BUG preserved: strips leading '+' from phone numbers.
   * International carriers need it. Ticket #4521.
   */
  formatPhoneNumber(phone: string): string {
    return phone.replace(/[^0-9]/g, '');
  }

  buildEmailBody(notif: Notification): string {
    if (!notif.body) return '';

    // Body may contain pipe-delimited template values
    if (notif.body.includes('|')) {
      const parts = notif.body.split('|');
      const templateData: Record<string, string> = {};
      if (parts.length >= 1) templateData.symbol = parts[0];
      if (parts.length >= 2) templateData.quantity = parts[1];
      if (parts.length >= 3) templateData.side = parts[2];
      if (parts.length >= 4) templateData.price = parts[3];
      if (parts.length >= 5) templateData.reason = parts[4];
      if (parts.length >= 6) templateData.amount = parts[5];
      if (parts.length >= 7) templateData.settlementDate = parts[6];

      return this.renderTemplate(notif.type, templateData);
    }

    return notif.body;
  }

  private renderTemplate(type: NotificationType, data: Record<string, string>): string {
    switch (type) {
      case NotificationType.ORDER_CONFIRM:
        return `Your order for ${data.quantity || ''} ${data.symbol || ''} (${data.side || ''}) has been filled at $${data.price || ''}. Amount: $${data.amount || ''}.`;
      case NotificationType.ORDER_REJECT:
        return `Your order for ${data.quantity || ''} ${data.symbol || ''} (${data.side || ''}) has been rejected. Reason: ${data.reason || 'N/A'}.`;
      case NotificationType.SETTLEMENT:
        return `Settlement confirmation for ${data.symbol || ''}: ${data.quantity || ''} shares at $${data.price || ''}. Settlement date: ${data.settlementDate || 'TBD'}.`;
      default:
        return Object.values(data).join(' ');
    }
  }

  getDispatched(): Notification[] {
    return this.dispatched;
  }
}
