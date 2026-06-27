export interface AppConfig {
  db: {
    host: string;
    port: number;
    database: string;
    user: string;
    password: string;
  };
  amqp: {
    url: string;
  };
  smtp: {
    host: string;
    port: number;
    from: string;
    htmlEnabled: boolean;
  };
  sftp: {
    host: string;
    port: number;
    username: string;
    password: string;
    remoteDir: string;
    remoteInboundDir: string;
  };
  server: {
    port: number;
  };
  rules: {
    priorityFixed: boolean;
  };
  market: {
    halted: boolean;
  };
  settlement: {
    outputDir: string;
    localInboundDir: string;
    localProcessedDir: string;
    localFallbackDir: string;
  };
  regulatory: {
    outputDir: string;
    firmName: string;
  };
  helpdesk: {
    email: string;
    extension: string;
  };
  specialClients: SpecialClientConfig[];
}

export interface SpecialClientConfig {
  clientId: string;
  commissionOverride?: number;
  pricingTierOverride?: string;
  earlyAccess?: boolean;
  multiCurrencyPriority?: boolean;
  loyaltyBonus?: boolean;
}

export function loadConfig(): AppConfig {
  return {
    db: {
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '5432', 10),
      database: process.env.DB_NAME || 'bigcorp',
      user: process.env.DB_USER || 'bigcorp',
      password: process.env.DB_PASSWORD || 'bigcorp_pass',
    },
    amqp: {
      url: process.env.AMQP_URL || 'amqp://bigcorp:bigcorp_pass@localhost:5672',
    },
    smtp: {
      host: process.env.SMTP_HOST || '',
      port: parseInt(process.env.SMTP_PORT || '25', 10),
      from: process.env.SMTP_FROM || 'noreply@bigcorp.com',
      htmlEnabled: process.env.SMTP_HTML_ENABLED !== 'false',
    },
    sftp: {
      host: process.env.SFTP_HOST || 'localhost',
      port: parseInt(process.env.SFTP_PORT || '2222', 10),
      username: process.env.SFTP_USER || 'bigcorp_settle',
      password: process.env.SFTP_PASSWORD || 'settle_pass',
      remoteDir: process.env.SFTP_REMOTE_DIR || '/incoming/',
      remoteInboundDir: process.env.SFTP_REMOTE_INBOUND_DIR || '/outgoing/',
    },
    server: {
      port: parseInt(process.env.PORT || '3000', 10),
    },
    rules: {
      priorityFixed: process.env.RULES_PRIORITY_FIXED === 'true',
    },
    market: {
      halted: process.env.MARKET_HALTED === 'true',
    },
    settlement: {
      outputDir: process.env.SETTLEMENT_OUTPUT_DIR || './sftp-outbound/',
      localInboundDir: process.env.SETTLEMENT_INBOUND_DIR || './sftp-root/inbound/',
      localProcessedDir: process.env.SETTLEMENT_PROCESSED_DIR || './sftp-root/processed/',
      localFallbackDir: process.env.SETTLEMENT_FALLBACK_DIR || './sftp-root/outbound/',
    },
    regulatory: {
      outputDir: process.env.REGULATORY_OUTPUT_DIR || './regulatory-output/',
      firmName: process.env.REGULATORY_FIRM_NAME || 'BIGCORP',
    },
    helpdesk: {
      email: 'helpdesk@bigcorp.com',
      extension: '4357',
    },
    specialClients: [
      {
        clientId: 'C001',
        earlyAccess: true,
        loyaltyBonus: true,
      },
      {
        clientId: 'C002',
        commissionOverride: 0.0,
        loyaltyBonus: true,
      },
      {
        clientId: 'C003',
        commissionOverride: 0.01,
        loyaltyBonus: true,
      },
      {
        clientId: 'C004',
        pricingTierOverride: 'PLATINUM',
      },
      {
        clientId: 'C005',
        commissionOverride: 0.01,
      },
      {
        clientId: 'C006',
        commissionOverride: 0.0,
        earlyAccess: true,
      },
      {
        clientId: 'C007',
        pricingTierOverride: 'PLATINUM',
      },
      {
        clientId: 'C008',
        commissionOverride: 0.005,
      },
      {
        clientId: 'C009',
        pricingTierOverride: 'GOLD',
      },
      {
        clientId: 'C010',
        commissionOverride: 0.0,
        multiCurrencyPriority: true,
      },
    ],
  };
}
