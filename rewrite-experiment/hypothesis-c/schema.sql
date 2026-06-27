-- BigCorp Trade Order Management System - Modern Schema
-- Adapted from the original Oracle 8i / HSQLDB schema (circa 2002)
-- Now targeting PostgreSQL with proper constraints and types

-- ============================
-- CLIENTS
-- ============================
CREATE TABLE IF NOT EXISTS clients (
    client_id       TEXT PRIMARY KEY,
    client_name     TEXT NOT NULL,
    email           TEXT,
    phone           TEXT,
    tier            TEXT DEFAULT 'BRONZE' CHECK (tier IN ('PLATINUM', 'GOLD', 'SILVER', 'BRONZE')),
    max_order_value REAL DEFAULT 100000.00,
    active          INTEGER DEFAULT 1,
    created_date    TEXT DEFAULT (datetime('now'))
);

-- ============================
-- TRADE_ORDERS
-- ============================
CREATE TABLE IF NOT EXISTS trade_orders (
    order_id        TEXT PRIMARY KEY,
    client_id       TEXT NOT NULL REFERENCES clients(client_id),
    symbol          TEXT NOT NULL,
    quantity        INTEGER NOT NULL,
    side            TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price           REAL DEFAULT 0,
    requested_price REAL DEFAULT 0,
    status          TEXT DEFAULT 'NEW',
    order_date      TEXT DEFAULT (datetime('now')),
    last_modified   TEXT DEFAULT (datetime('now')),
    notes           TEXT
);

CREATE INDEX IF NOT EXISTS idx_orders_status ON trade_orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_client ON trade_orders(client_id);
CREATE INDEX IF NOT EXISTS idx_orders_date ON trade_orders(order_date);

-- ============================
-- NOTIFICATIONS
-- ============================
CREATE TABLE IF NOT EXISTS notifications (
    notification_id   TEXT PRIMARY KEY,
    notification_type TEXT NOT NULL,
    recipient         TEXT NOT NULL,
    subject           TEXT,
    body              TEXT,
    channel           TEXT NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'FAX')),
    status            TEXT DEFAULT 'PENDING',
    order_id          TEXT,
    created_date      TEXT DEFAULT (datetime('now')),
    sent_date         TEXT,
    retry_count       INTEGER DEFAULT 0
);

-- ============================
-- SETTLEMENT_RECORDS
-- ============================
CREATE TABLE IF NOT EXISTS settlement_records (
    record_id       TEXT PRIMARY KEY,
    order_id        TEXT NOT NULL,
    client_id       TEXT NOT NULL,
    symbol          TEXT NOT NULL,
    quantity        INTEGER NOT NULL,
    side            TEXT NOT NULL,
    amount          REAL NOT NULL,
    commission      REAL DEFAULT 0,
    trade_date      TEXT NOT NULL,
    settlement_date TEXT,
    status          TEXT DEFAULT 'PENDING',
    batch_id        TEXT,
    external_ref    TEXT
);

CREATE INDEX IF NOT EXISTS idx_settlement_status ON settlement_records(status);
CREATE INDEX IF NOT EXISTS idx_settlement_batch ON settlement_records(batch_id);

-- ============================
-- AUDIT_LOG
-- ============================
CREATE TABLE IF NOT EXISTS audit_log (
    log_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type      TEXT NOT NULL,
    source_system   TEXT,
    entity_type     TEXT,
    entity_id       TEXT,
    description     TEXT,
    log_date        TEXT DEFAULT (datetime('now')),
    user_id         TEXT
);

-- ============================
-- BILLING_LEDGER
-- ============================
CREATE TABLE IF NOT EXISTS billing_ledger (
    entry_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id          TEXT NOT NULL,
    client_id         TEXT NOT NULL,
    gross_amount      REAL NOT NULL,
    commission_amount REAL NOT NULL,
    net_amount        REAL NOT NULL,
    charged_date      TEXT DEFAULT (datetime('now')),
    status            TEXT DEFAULT 'CHARGED'
);

CREATE INDEX IF NOT EXISTS idx_billing_client ON billing_ledger(client_id);
CREATE INDEX IF NOT EXISTS idx_billing_order ON billing_ledger(order_id);

-- ============================
-- PRICING_CACHE
-- ============================
CREATE TABLE IF NOT EXISTS pricing_cache (
    symbol       TEXT PRIMARY KEY,
    bid_price    REAL,
    ask_price    REAL,
    last_price   REAL,
    currency     TEXT DEFAULT 'USD',
    last_updated TEXT DEFAULT (datetime('now'))
);

-- ============================
-- RISK_ASSESSMENTS
-- ============================
CREATE TABLE IF NOT EXISTS risk_assessments (
    risk_order_id         TEXT PRIMARY KEY,
    source_order_id       TEXT NOT NULL,
    client_id             TEXT NOT NULL,
    symbol                TEXT NOT NULL,
    quantity              INTEGER NOT NULL,
    side                  TEXT NOT NULL,
    price                 REAL NOT NULL,
    notional_value        REAL,
    exposure_contribution REAL,
    var_contribution      REAL,
    risk_status           TEXT DEFAULT 'PENDING',
    assessment_date       TEXT DEFAULT (datetime('now'))
);

-- ============================
-- SPECIAL_CLIENT_OVERRIDES (extracted from hardcoded rules)
-- ============================
CREATE TABLE IF NOT EXISTS special_client_overrides (
    client_id             TEXT PRIMARY KEY,
    commission_override   REAL,
    tier_override         TEXT,
    early_access          INTEGER DEFAULT 0,
    fx_priority           INTEGER DEFAULT 0,
    discount_pct          REAL DEFAULT 0,
    zero_commission       INTEGER DEFAULT 0,
    free_trades_per_day   INTEGER DEFAULT 0,
    notes                 TEXT
);

-- ============================
-- SEED DATA
-- ============================
INSERT OR IGNORE INTO clients VALUES ('C001', 'Acme Trading LLC', 'trading@acme.com', '555-0100', 'GOLD', 500000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C002', 'Henderson Capital', 'orders@henderson.com', '555-0200', 'PLATINUM', 5000000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C003', 'Smith & Associates', 'desk@smithassoc.com', '555-0300', 'SILVER', 250000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C004', 'MegaFund Inc', 'ops@megafund.com', '555-0400', 'GOLD', 1000000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C005', 'Pinnacle Investments', 'trade@pinnacle.com', '555-0500', 'BRONZE', 100000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C006', 'Global Macro Partners', 'desk@globalmacro.com', '555-0600', 'PLATINUM', 10000000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C007', 'Velocity Trading', 'ops@velocity.com', '555-0700', 'GOLD', 2000000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C008', 'Falcon Capital', 'trade@falcon.com', '555-0800', 'SILVER', 500000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C009', 'Apex Securities', 'desk@apex.com', '555-0900', 'BRONZE', 750000.00, 1, datetime('now'));
INSERT OR IGNORE INTO clients VALUES ('C010', 'Sterling & Co', 'orders@sterling.com', '555-1000', 'GOLD', 3000000.00, 1, datetime('now'));

INSERT OR IGNORE INTO pricing_cache VALUES ('MSFT', 25.50, 25.75, 25.63, 'USD', datetime('now'));
INSERT OR IGNORE INTO pricing_cache VALUES ('IBM', 120.00, 120.50, 120.25, 'USD', datetime('now'));
INSERT OR IGNORE INTO pricing_cache VALUES ('ORCL', 15.25, 15.50, 15.38, 'USD', datetime('now'));
INSERT OR IGNORE INTO pricing_cache VALUES ('SUNW', 8.75, 9.00, 8.88, 'USD', datetime('now'));
INSERT OR IGNORE INTO pricing_cache VALUES ('CSCO', 22.00, 22.25, 22.13, 'USD', datetime('now'));
INSERT OR IGNORE INTO pricing_cache VALUES ('INTC', 30.50, 30.75, 30.63, 'USD', datetime('now'));
INSERT OR IGNORE INTO pricing_cache VALUES ('DELL', 35.00, 35.25, 35.13, 'USD', datetime('now'));

-- Special client overrides (extracted from hardcoded SpecialClientsRule)
INSERT OR IGNORE INTO special_client_overrides VALUES ('C001', NULL, NULL, 1, 0, 0, 0, 0, 'Acme - early access (legacy deal)');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C002', 0.0, 'PLATINUM', 0, 0, 0, 1, 1000, 'Henderson - zero commission first 1000/day, PLATINUM override (JIRA-1892)');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C003', 0.01, 'GOLD', 0, 0, 0, 0, 0, 'Smith - GOLD rate override');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C004', 0.01, 'PLATINUM', 0, 0, 0, 0, 0, 'MegaFund - PLATINUM override');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C005', 0.005, NULL, 0, 0, 50, 0, 0, 'Pinnacle - 50% discount');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C006', 0.0, NULL, 1, 0, 0, 1, 0, 'Global Macro - zero commission + early access');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C007', NULL, 'PLATINUM', 0, 0, 0, 0, 0, 'Velocity - PLATINUM override');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C008', NULL, NULL, 0, 0, 75, 0, 0, 'Falcon - 75% discount');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C009', NULL, 'GOLD', 0, 0, 0, 0, 0, 'Apex - GOLD override');
INSERT OR IGNORE INTO special_client_overrides VALUES ('C010', 0.0, NULL, 0, 1, 0, 1, 0, 'Sterling - zero commission + FX priority');
