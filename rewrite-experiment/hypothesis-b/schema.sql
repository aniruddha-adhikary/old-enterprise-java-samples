-- BigCorp Trade Order Management System - PostgreSQL Schema
-- Derived from spec.json entity definitions

-- Core domain tables

CREATE TABLE clients (
    client_id VARCHAR(20) PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    tier VARCHAR(10) DEFAULT 'BRONZE',
    max_order_value DECIMAL(15,2) DEFAULT 100000.00,
    active INTEGER DEFAULT 1,  -- 1=active, 0=inactive
    kyc_status VARCHAR(20) DEFAULT 'APPROVED',
    kill_switch VARCHAR(1) DEFAULT 'N'
);

CREATE TABLE trade_orders (
    order_id VARCHAR(30) PRIMARY KEY,  -- format: ORD-{timestamp}
    client_id VARCHAR(20) NOT NULL REFERENCES clients(client_id),
    symbol VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    price DECIMAL(15,4) DEFAULT 0,
    requested_price DECIMAL(15,4) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'NEW',
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(500),
    surveillance_flags VARCHAR(200) DEFAULT ''
);

CREATE TABLE notifications (
    notification_id VARCHAR(30) PRIMARY KEY,
    notification_type VARCHAR(20) NOT NULL,
    recipient VARCHAR(100) NOT NULL,
    subject VARCHAR(200),
    body VARCHAR(2000),
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'FAX')),
    status VARCHAR(10) DEFAULT 'PENDING',
    order_id VARCHAR(30),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_date TIMESTAMP,
    retry_count INTEGER DEFAULT 0
);

CREATE TABLE settlement_records (
    record_id VARCHAR(30) PRIMARY KEY,  -- format: SR-{timestamp}-{hash}
    order_id VARCHAR(30) NOT NULL,
    client_id VARCHAR(20) NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    side VARCHAR(4) NOT NULL,
    amount DECIMAL(15,4) NOT NULL,
    commission DECIMAL(10,4) DEFAULT 0,
    trade_date TIMESTAMP NOT NULL,
    settlement_date TIMESTAMP,  -- T+3 calendar days
    status VARCHAR(15) DEFAULT 'PENDING',
    batch_id VARCHAR(30),  -- format: BATCH-yyyyMMdd-NNN
    external_ref VARCHAR(50)
);

CREATE TABLE audit_log (
    log_id SERIAL PRIMARY KEY,
    event_type VARCHAR(30) NOT NULL,
    source_system VARCHAR(30),
    entity_type VARCHAR(20),
    entity_id VARCHAR(30),
    description VARCHAR(500),
    log_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(30)
);

CREATE TABLE billing_ledger (
    entry_id SERIAL PRIMARY KEY,
    order_id VARCHAR(30) NOT NULL,
    client_id VARCHAR(20) NOT NULL,
    gross_amount DECIMAL(15,4) NOT NULL,
    commission_amount DECIMAL(10,4) NOT NULL,
    net_amount DECIMAL(15,4) NOT NULL,
    charged_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(15) DEFAULT 'CHARGED'
);

-- Additional tables from spec.json additionalTables

CREATE TABLE rule_audit_log (
    audit_id SERIAL PRIMARY KEY,
    rule_name VARCHAR(50) NOT NULL,
    order_id VARCHAR(30),
    client_id VARCHAR(20),
    result VARCHAR(10) NOT NULL CHECK (result IN ('PASS', 'FAIL', 'SKIP')),
    evaluation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details VARCHAR(500)
);

CREATE TABLE daily_volume_tracker (
    client_id VARCHAR(20),
    trade_date DATE,
    total_shares INTEGER DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, trade_date)
);

CREATE TABLE pricing_cache (
    symbol VARCHAR(10) PRIMARY KEY,
    bid_price DECIMAL(15,4),
    ask_price DECIMAL(15,4),
    last_price DECIMAL(15,4),
    currency VARCHAR(3) DEFAULT 'USD',
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE surveillance_audit_log (
    log_id SERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    order_id VARCHAR(50),
    client_id VARCHAR(20),
    symbol VARCHAR(10),
    result VARCHAR(20),
    surveillance_flags VARCHAR(200),
    evaluation_time TIMESTAMP,
    details VARCHAR(500)
);

CREATE TABLE position_tracking (
    client_id VARCHAR(20),
    symbol VARCHAR(10),
    net_position INTEGER DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, symbol)
);

CREATE TABLE reg_report_log (
    log_id SERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    file_path VARCHAR(500),
    record_count INTEGER DEFAULT 0,
    status VARCHAR(50),
    generation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Risk engine table
CREATE TABLE risk_assessments (
    risk_order_id VARCHAR(50) PRIMARY KEY,
    source_order_id VARCHAR(50),
    client_id VARCHAR(20),
    symbol VARCHAR(10),
    quantity INTEGER,
    side VARCHAR(4),
    price DECIMAL(15,4),
    notional_value DECIMAL(20,4),
    exposure_contribution DECIMAL(20,4),
    var_contribution DECIMAL(20,4),
    risk_status VARCHAR(20) DEFAULT 'PENDING',
    assessment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_orders_client ON trade_orders(client_id);
CREATE INDEX idx_orders_status ON trade_orders(status);
CREATE INDEX idx_orders_symbol ON trade_orders(symbol);
CREATE INDEX idx_orders_date ON trade_orders(order_date);
CREATE INDEX idx_settlement_order ON settlement_records(order_id);
CREATE INDEX idx_settlement_batch ON settlement_records(batch_id);
CREATE INDEX idx_notifications_order ON notifications(order_id);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_billing_client ON billing_ledger(client_id);
CREATE INDEX idx_risk_source ON risk_assessments(source_order_id);

-- Seed data from spec.json sampleData
INSERT INTO clients (client_id, client_name, email, phone, tier, max_order_value, active) VALUES
('C001', 'Acme Trading LLC', 'trading@acme.com', '555-0100', 'GOLD', 500000.00, 1),
('C002', 'Henderson Capital', 'orders@henderson.com', '555-0200', 'PLATINUM', 5000000.00, 1),
('C003', 'Smith & Associates', 'desk@smithassoc.com', '555-0300', 'SILVER', 250000.00, 1),
('C004', 'MegaFund Inc', 'ops@megafund.com', '555-0400', 'GOLD', 1000000.00, 1),
('C005', 'Pinnacle Investments', 'trade@pinnacle.com', '555-0500', 'BRONZE', 100000.00, 1),
('C006', 'Global Macro Fund', 'globalfund@trading.com', '555-0600', 'PLATINUM', 10000000.00, 1),
('C007', 'Velocity Trading LLC', 'trades@velocity.com', '555-0700', 'GOLD', 2000000.00, 1);

-- Seed pricing cache
INSERT INTO pricing_cache (symbol, bid_price, ask_price, last_price) VALUES
('MSFT', 25.50, 25.75, 25.63),
('IBM', 120.00, 120.50, 120.25),
('ORCL', 15.25, 15.50, 15.38),
('SUNW', 8.75, 9.00, 8.88),
('CSCO', 22.00, 22.25, 22.13),
('INTC', 30.50, 30.75, 30.63),
('DELL', 35.00, 35.25, 35.13);
