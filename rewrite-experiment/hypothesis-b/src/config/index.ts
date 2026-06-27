export interface AppConfig {
  port: number;
  database: {
    url: string;
    driver: string;
  };
  messaging: {
    url: string;
    acknowledgeMode: 'auto' | 'manual';
  };
  sftp: {
    host: string;
    port: number;
    username: string;
    uploadDir: string;
  };
  email: {
    host: string;
    port: number;
    fromAddress: string;
  };
  ruleEngine: {
    priorityFixed: boolean;
    marketHalted: boolean;
  };
  settlement: {
    batchIntervalMs: number;
  };
}

export function loadConfig(): AppConfig {
  return {
    port: parseInt(process.env['PORT'] ?? '3000', 10),
    database: {
      url: process.env['DATABASE_URL'] ?? 'postgresql://localhost:5432/bigcorp',
      driver: 'pg',
    },
    messaging: {
      url: process.env['AMQP_URL'] ?? 'amqp://localhost:5672',
      acknowledgeMode: 'auto',
    },
    sftp: {
      host: process.env['SFTP_HOST'] ?? 'localhost',
      port: parseInt(process.env['SFTP_PORT'] ?? '2222', 10),
      username: process.env['SFTP_USER'] ?? 'bigcorp_settle',
      uploadDir: '/incoming/',
    },
    email: {
      host: process.env['SMTP_HOST'] ?? '',
      port: parseInt(process.env['SMTP_PORT'] ?? '25', 10),
      fromAddress: process.env['SMTP_FROM'] ?? 'noreply@bigcorp.com',
    },
    ruleEngine: {
      priorityFixed: process.env['BIGCORP_RULES_PRIORITY_FIXED'] === 'true',
      marketHalted: process.env['BIGCORP_MARKET_HALTED'] === 'true',
    },
    settlement: {
      batchIntervalMs: parseInt(process.env['SETTLEMENT_BATCH_INTERVAL'] ?? '60000', 10),
    },
  };
}
