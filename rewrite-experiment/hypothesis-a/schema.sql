-- BigCorp Trade Order Management System -- Modern PostgreSQL Schema
-- Migrated from HSQLDB/Oracle dual-schema legacy system

CREATE TABLE IF NOT EXISTS clients (
    client_id       VARCHAR(20) PRIMARY KEY,
    client_name     VARCHAR(100) NOT NULL,
    email           VARCHAR(100),
    phone           VARCHAR(20),
    tier            VARCHAR(10) DEFAULT 'BRONZE'
                    CHECK (tier IN ('PLATINUM', 'GOLD', 'SILVER', 'BRONZE')),
    max_order_value DECIMAL(15, 2) DEFAULT 100000.00,
    active          BOOLEAN DEFAULT TRUE,
    kyc_status      VARCHAR(20) DEFAULT 'APPROVED'
                    CHECK (kyc_status IN ('APPROVED', 'PENDING', 'EXPIRED', 'REJECTED')),
    kill_switch     VARCHAR(1) DEFAULT 'N' CHECK (kill_switch IN ('Y', 'N')),
    created_date    TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trade_orders (
    order_id          VARCHAR(30) PRIMARY KEY,
    client_id         VARCHAR(20) NOT NULL REFERENCES clients(client_id),
    symbol            VARCHAR(10) NOT NULL,
    quantity          INTEGER NOT NULL,
    side              VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price             DECIMAL(15, 4) DEFAULT 0,
    requested_price   DECIMAL(15, 4) DEFAULT 0,
    status            VARCHAR(20) DEFAULT 'NEW',
    order_date        TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    last_modified     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    notes             VARCHAR(500),
    surveillance_flags VARCHAR(200) DEFAULT ''
);

CREATE INDEX idx_trade_orders_client ON trade_orders(client_id);
CREATE INDEX idx_trade_orders_status ON trade_orders(status);
CREATE INDEX idx_trade_orders_symbol ON trade_orders(symbol);
CREATE INDEX idx_trade_orders_date ON trade_orders(order_date);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id   VARCHAR(30) PRIMARY KEY,
    notification_type VARCHAR(20) NOT NULL
                      CHECK (notification_type IN ('ORDER_CONFIRM', 'ORDER_REJECT', 'SETTLEMENT', 'PRICE_ALERT')),
    recipient         VARCHAR(100) NOT NULL,
    subject           VARCHAR(200),
    body              VARCHAR(2000),
    channel           VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'FAX')),
    status            VARCHAR(10) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    order_id          VARCHAR(30),
    created_date      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    sent_date         TIMESTAMPTZ,
    retry_count       INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS settlement_records (
    record_id       VARCHAR(30) PRIMARY KEY,
    order_id        VARCHAR(30) NOT NULL,
    client_id       VARCHAR(20) NOT NULL,
    symbol          VARCHAR(10) NOT NULL,
    quantity        INTEGER NOT NULL,
    side            VARCHAR(4) NOT NULL,
    amount          DECIMAL(15, 4) NOT NULL,
    commission      DECIMAL(10, 4) DEFAULT 0,
    trade_date      TIMESTAMPTZ NOT NULL,
    settlement_date TIMESTAMPTZ,
    status          VARCHAR(15) DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'GENERATED', 'UPLOADED', 'CONFIRMED', 'FAILED', 'RECONCILED', 'DISCREPANCY')),
    batch_id        VARCHAR(30),
    external_ref    VARCHAR(50)
);

CREATE INDEX idx_settlement_status ON settlement_records(status);
CREATE INDEX idx_settlement_batch ON settlement_records(batch_id);

CREATE TABLE IF NOT EXISTS audit_log (
    log_id        SERIAL PRIMARY KEY,
    event_type    VARCHAR(30) NOT NULL,
    source_system VARCHAR(30),
    entity_type   VARCHAR(20),
    entity_id     VARCHAR(30),
    description   VARCHAR(500),
    log_date      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    user_id       VARCHAR(30)
);

CREATE TABLE IF NOT EXISTS billing_ledger (
    entry_id          SERIAL PRIMARY KEY,
    order_id          VARCHAR(30) NOT NULL,
    client_id         VARCHAR(20) NOT NULL,
    gross_amount      DECIMAL(15, 4) NOT NULL,
    commission_amount DECIMAL(10, 4) NOT NULL,
    net_amount        DECIMAL(15, 4) NOT NULL,
    charged_date      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    status            VARCHAR(15) DEFAULT 'CHARGED'
);

CREATE TABLE IF NOT EXISTS rule_audit_log (
    audit_id        SERIAL PRIMARY KEY,
    rule_name       VARCHAR(50) NOT NULL,
    order_id        VARCHAR(30),
    client_id       VARCHAR(20),
    result          VARCHAR(10) NOT NULL,
    evaluation_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    details         VARCHAR(500)
);

CREATE INDEX idx_rule_audit_order ON rule_audit_log(order_id);

CREATE TABLE IF NOT EXISTS daily_volume_tracker (
    client_id    VARCHAR(20),
    trade_date   DATE,
    total_shares INTEGER DEFAULT 0,
    last_updated TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, trade_date)
);

CREATE TABLE IF NOT EXISTS pricing_cache (
    symbol       VARCHAR(10) PRIMARY KEY,
    bid_price    DECIMAL(15, 4),
    ask_price    DECIMAL(15, 4),
    last_price   DECIMAL(15, 4),
    currency     VARCHAR(3) DEFAULT 'USD',
    last_updated TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS surveillance_audit_log (
    log_id            SERIAL PRIMARY KEY,
    rule_name         VARCHAR(100) NOT NULL,
    order_id          VARCHAR(50),
    client_id         VARCHAR(20),
    symbol            VARCHAR(10),
    result            VARCHAR(20),
    surveillance_flags VARCHAR(200),
    evaluation_time   TIMESTAMPTZ,
    details           VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS position_tracking (
    client_id    VARCHAR(20),
    symbol       VARCHAR(10),
    net_position INTEGER DEFAULT 0,
    last_updated TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, symbol)
);

CREATE TABLE IF NOT EXISTS risk_assessments (
    risk_order_id         VARCHAR(50) PRIMARY KEY,
    source_order_id       VARCHAR(50),
    client_id             VARCHAR(20),
    symbol                VARCHAR(10),
    quantity              INTEGER,
    side                  VARCHAR(4),
    price                 DECIMAL(15, 4),
    notional_value        DECIMAL(20, 4),
    exposure_contribution DECIMAL(20, 4),
    var_contribution      DECIMAL(20, 4),
    risk_status           VARCHAR(20),
    assessment_date       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_report_log (
    log_id          SERIAL PRIMARY KEY,
    report_type     VARCHAR(50) NOT NULL,
    file_path       VARCHAR(500),
    record_count    INTEGER DEFAULT 0,
    status          VARCHAR(50),
    generation_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Seed pricing cache (FR-PRC-006)
INSERT INTO pricing_cache (symbol, bid_price, ask_price, last_price) VALUES
    ('MSFT', 25.50, 25.75, 25.63),
    ('IBM',  120.00, 120.50, 120.25),
    ('ORCL', 15.25, 15.50, 15.38),
    ('SUNW', 8.75, 9.00, 8.88),
    ('CSCO', 22.00, 22.25, 22.13),
    ('INTC', 30.50, 30.75, 30.63),
    ('DELL', 35.00, 35.25, 35.13)
ON CONFLICT (symbol) DO NOTHING;

-- Seed sample clients
INSERT INTO clients (client_id, client_name, email, phone, tier, max_order_value) VALUES
    ('C001', 'Acme Trading LLC',     'acme@example.com',     '555-0001', 'GOLD',     500000),
    ('C002', 'Henderson Capital',    'henderson@example.com', '555-0002', 'PLATINUM', 5000000),
    ('C003', 'Smith & Associates',   'smith@example.com',     '555-0003', 'SILVER',   250000),
    ('C004', 'MegaFund Inc',         'mega@example.com',      '555-0004', 'GOLD',     1000000),
    ('C005', 'Pinnacle Investments', 'pinnacle@example.com',  '555-0005', 'BRONZE',   100000),
    ('C006', 'Global Macro Fund',    'global@example.com',    '555-0006', 'PLATINUM', 10000000),
    ('C007', 'Velocity Trading LLC', 'velocity@example.com',  '555-0007', 'GOLD',     2000000)
ON CONFLICT (client_id) DO NOTHING;
