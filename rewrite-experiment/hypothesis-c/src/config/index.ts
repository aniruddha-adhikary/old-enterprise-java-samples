export interface AppConfig {
  port: number;
  dbPath: string;
  rabbitmqUrl: string;
  smtp: {
    host: string;
    port: number;
    from: string;
    devMode: boolean;
  };
  sftp: {
    host: string;
    port: number;
    username: string;
    password: string;
    remoteDir: string;
    enabled: boolean;
  };
  sms: {
    gatewayUrl: string;
    apiKey: string;
    devMode: boolean;
  };
  settlement: {
    outputDir: string;
    batchIntervalMs: number;
  };
  reporting: {
    outputDir: string;
  };
  rules: {
    priorityFixed: boolean;
  };
  market: {
    halted: boolean;
  };
}

export function loadConfig(): AppConfig {
  return {
    port: parseInt(process.env.PORT || '3000', 10),
    dbPath: process.env.DB_PATH || ':memory:',
    rabbitmqUrl: process.env.RABBITMQ_URL || '',
    smtp: {
      host: process.env.SMTP_HOST || '',
      port: parseInt(process.env.SMTP_PORT || '25', 10),
      from: process.env.SMTP_FROM || 'noreply@bigcorp.com',
      devMode: process.env.SMTP_DEV_MODE === 'true' || !process.env.SMTP_HOST,
    },
    sftp: {
      host: process.env.SFTP_HOST || '',
      port: parseInt(process.env.SFTP_PORT || '22', 10),
      username: process.env.SFTP_USER || '',
      password: process.env.SFTP_PASS || '',
      remoteDir: process.env.SFTP_REMOTE_DIR || '/incoming/',
      enabled: !!process.env.SFTP_HOST,
    },
    sms: {
      gatewayUrl: process.env.SMS_GATEWAY_URL || '',
      apiKey: process.env.SMS_API_KEY || '',
      devMode: process.env.SMS_DEV_MODE === 'true' || !process.env.SMS_GATEWAY_URL,
    },
    settlement: {
      outputDir: process.env.SETTLEMENT_OUTPUT_DIR || './sftp-outbound/',
      batchIntervalMs: parseInt(process.env.SETTLEMENT_BATCH_INTERVAL_MS || '60000', 10),
    },
    reporting: {
      outputDir: process.env.REPORTING_OUTPUT_DIR || './reports-output/',
    },
    rules: {
      priorityFixed: process.env.BIGCORP_RULES_PRIORITY_FIXED === 'true',
    },
    market: {
      halted: process.env.BIGCORP_MARKET_HALTED === 'true',
    },
  };
}
