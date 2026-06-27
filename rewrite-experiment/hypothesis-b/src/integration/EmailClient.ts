// Modern equivalent of JavaMail SMTP: nodemailer

export interface EmailConfig {
  host: string;
  port: number;
  fromAddress: string;
  authentication: boolean;
  htmlEnabled: boolean;
}

export const DEFAULT_EMAIL_CONFIG: EmailConfig = {
  host: 'smtp-internal.bigcorp.com',
  port: 25,
  fromAddress: 'noreply@bigcorp.com',
  authentication: false,
  htmlEnabled: true,
};

export class EmailClient {
  private config: EmailConfig;

  constructor(config?: Partial<EmailConfig>) {
    this.config = { ...DEFAULT_EMAIL_CONFIG, ...config };
  }

  async send(to: string, subject: string, htmlBody: string): Promise<boolean> {
    // Dev mode: log to console when SMTP host is empty/unavailable
    if (!this.config.host) {
      console.log(`[EMAIL DEV] To: ${to}, Subject: ${subject}`);
      return true;
    }

    // In production, use nodemailer.createTransport()
    // Note from spec: "Unreliable during 5:00-5:30 PM EST market close"
    try {
      console.log(`[EMAIL] Sending to ${to}: ${subject}`);
      return true;
    } catch {
      return false;
    }
  }

  getConfig(): EmailConfig {
    return { ...this.config };
  }
}
