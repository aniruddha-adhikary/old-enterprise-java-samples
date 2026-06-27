import {
  Notification,
} from '../domain/Notification';
import {
  NotificationType,
  NotificationChannel,
  NotificationStatus,
} from '../domain/enums';

const MAX_RETRY_COUNT = 3;

export interface NotificationDispatcher {
  dispatch(notification: Notification): Promise<boolean>;
}

export interface NotificationRepository {
  save(notification: Notification): Promise<void>;
}

export interface NotificationQueue {
  enqueue(notification: Notification): Promise<void>;
}

export class EmailDispatcher implements NotificationDispatcher {
  constructor(
    private readonly smtpHost: string,
    private readonly smtpPort: number,
    private readonly fromAddress: string,
    private readonly htmlEnabled: boolean,
    private readonly sendFn?: (opts: {
      from: string;
      to: string;
      subject: string;
      html?: string;
      text?: string;
    }) => Promise<void>
  ) {}

  async dispatch(notification: Notification): Promise<boolean> {
    if (!this.smtpHost) {
      // dev mode: log to console
      console.log(`[EmailDispatcher DEV] To: ${notification.recipient}`);
      console.log(`[EmailDispatcher DEV] Subject: ${notification.subject}`);
      console.log(`[EmailDispatcher DEV] Body: ${notification.body}`);
      return true;
    }

    const body = this.formatBody(notification);

    if (this.sendFn) {
      await this.sendFn({
        from: this.fromAddress,
        to: notification.recipient,
        subject: notification.subject,
        ...(this.htmlEnabled ? { html: body } : { text: body }),
      });
    }

    return true;
  }

  private formatBody(notification: Notification): string {
    if (!this.htmlEnabled) return notification.body;

    return `<!DOCTYPE html>
<html>
<head><style>
  .header { background-color: #003366; color: white; padding: 10px; }
  .footer { font-size: 10px; color: #666; margin-top: 20px; }
</style></head>
<body>
  <div class="header"><h2>BigCorp Trading</h2></div>
  <div class="content">${this.applyTemplate(notification.body)}</div>
  <div class="footer">
    Do not reply to this email. For support contact helpdesk@bigcorp.com ext. 4357.
  </div>
</body>
</html>`;
  }

  private applyTemplate(body: string): string {
    const parts = body.split('|');
    if (parts.length >= 7) {
      return `<p>Symbol: ${parts[0]}</p>
<p>Quantity: ${parts[1]}</p>
<p>Side: ${parts[2]}</p>
<p>Price: ${parts[3]}</p>
<p>Reason: ${parts[4]}</p>
<p>Amount: ${parts[5]}</p>
<p>Settlement Date: ${parts[6]}</p>`;
    }
    return `<p>${body}</p>`;
  }
}

export class SmsDispatcher implements NotificationDispatcher {
  async dispatch(notification: Notification): Promise<boolean> {
    const xml = `<?xml version="1.0"?>
<sms>
  <sender>BIGCORP</sender>
  <recipient>${notification.recipient}</recipient>
  <message>${notification.body}</message>
</sms>`;
    console.log(`[SmsDispatcher] Sending: ${xml}`);
    return true;
  }
}

export class NotificationGateway {
  private readonly dispatchers: Map<
    NotificationChannel,
    NotificationDispatcher
  > = new Map();

  constructor(
    private readonly repo: NotificationRepository,
    private readonly queue: NotificationQueue
  ) {}

  registerDispatcher(
    channel: NotificationChannel,
    dispatcher: NotificationDispatcher
  ): void {
    this.dispatchers.set(channel, dispatcher);
  }

  async processNotification(notification: Notification): Promise<void> {
    const dispatcher = this.dispatchers.get(notification.channel);

    if (!dispatcher) {
      if (notification.channel === NotificationChannel.FAX) {
        console.warn(
          `[NotificationGateway] FAX channel deprecated (2002) -- marking FAILED`
        );
        notification.status = NotificationStatus.FAILED;
        await this.repo.save(notification);
        return;
      }

      console.error(
        `[NotificationGateway] No dispatcher for channel: ${notification.channel}`
      );
      notification.status = NotificationStatus.FAILED;
      await this.repo.save(notification);
      return;
    }

    try {
      const success = await dispatcher.dispatch(notification);
      if (success) {
        notification.status = NotificationStatus.SENT;
        notification.sentDate = new Date();
      } else {
        throw new Error('Dispatch returned false');
      }
    } catch (error) {
      if (notification.retryCount < MAX_RETRY_COUNT) {
        notification.retryCount++;
        // re-queue with incremented retryCount (creates new row per BUG-008)
        await this.queue.enqueue(notification);
        return;
      }

      notification.status = NotificationStatus.FAILED;
    }

    // INSERT only, no updates (BUG-008 preserved)
    await this.repo.save(notification);
  }

  createNotification(params: {
    orderId: string;
    type: NotificationType;
    channel: NotificationChannel;
    recipient: string;
    subject: string;
    body: string;
  }): Notification {
    const now = Date.now();
    let notificationId: string;

    if (params.type === NotificationType.ORDER_CONFIRM) {
      notificationId = `N-${params.orderId}-${now}`;
    } else if (params.type === NotificationType.ORDER_REJECT) {
      notificationId = `N-${params.orderId}-REJ-${now}`;
    } else {
      const hash = Math.abs(this.hashCode(params.orderId));
      notificationId = `NOTIF-${now}-${hash}`;
    }

    return {
      notificationId,
      notificationType: params.type,
      recipient: params.recipient,
      subject: params.subject,
      body: params.body,
      channel: params.channel,
      status: NotificationStatus.PENDING,
      orderId: params.orderId,
      createdDate: new Date(),
      sentDate: null,
      retryCount: 0,
    };
  }

  private hashCode(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = (hash << 5) - hash + str.charCodeAt(i);
      hash |= 0;
    }
    return hash;
  }
}
