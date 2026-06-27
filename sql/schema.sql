-- BigCorp Trade Order Management System
-- Database Schema
-- 
-- Written for Oracle 8i but adapted for HSQLDB in development.
-- In production, the DBA runs this script manually.
-- In development, DatabaseBootstrap.java creates the tables.
--
-- Author: DBA team / Bob
-- Last updated: 2002-03-20
-- 
-- IMPORTANT: Do not add constraints without checking with the
-- settlement team first. Last time someone added a NOT NULL 
-- constraint on NOTES it broke the batch feed for 3 days.

-- ============================
-- CLIENTS
-- ============================
CREATE TABLE CLIENTS (
    CLIENT_ID       VARCHAR(20)     PRIMARY KEY,
    CLIENT_NAME     VARCHAR(100)    NOT NULL,
    EMAIL           VARCHAR(100),
    PHONE           VARCHAR(20),
    TIER            VARCHAR(10)     DEFAULT 'BRONZE',
    MAX_ORDER_VALUE NUMBER(15,2)    DEFAULT 100000.00,
    ACTIVE          NUMBER(1)       DEFAULT 1,
    CREATED_DATE    DATE            DEFAULT SYSDATE
);

-- ============================
-- TRADE_ORDERS
-- ============================
CREATE TABLE TRADE_ORDERS (
    ORDER_ID        VARCHAR(30)     PRIMARY KEY,
    CLIENT_ID       VARCHAR(20)     NOT NULL REFERENCES CLIENTS(CLIENT_ID),
    SYMBOL          VARCHAR(10)     NOT NULL,
    QUANTITY        NUMBER(10)      NOT NULL,
    SIDE            VARCHAR(4)      NOT NULL,  -- BUY or SELL
    PRICE           NUMBER(15,4)    DEFAULT 0,
    REQUESTED_PRICE NUMBER(15,4)    DEFAULT 0,
    STATUS          VARCHAR(20)     DEFAULT 'NEW',
    ORDER_DATE      DATE            DEFAULT SYSDATE,
    LAST_MODIFIED   DATE            DEFAULT SYSDATE,
    NOTES           VARCHAR2(500)   -- can contain anything, sometimes XML
);

-- index for status queries (the order status page is slow without this)
CREATE INDEX IDX_ORDERS_STATUS ON TRADE_ORDERS(STATUS);
CREATE INDEX IDX_ORDERS_CLIENT ON TRADE_ORDERS(CLIENT_ID);
-- this index was requested by the reporting team but nobody is sure 
-- if it actually helps
CREATE INDEX IDX_ORDERS_DATE ON TRADE_ORDERS(ORDER_DATE);

-- ============================
-- NOTIFICATIONS
-- ============================
CREATE TABLE NOTIFICATIONS (
    NOTIFICATION_ID     VARCHAR(30)     PRIMARY KEY,
    NOTIFICATION_TYPE   VARCHAR(20)     NOT NULL,
    RECIPIENT           VARCHAR(100)    NOT NULL,
    SUBJECT             VARCHAR(200),
    BODY                VARCHAR2(2000),
    CHANNEL             VARCHAR(10)     NOT NULL,  -- EMAIL, SMS, FAX
    STATUS              VARCHAR(10)     DEFAULT 'PENDING',
    ORDER_ID            VARCHAR(30),
    CREATED_DATE        DATE            DEFAULT SYSDATE,
    SENT_DATE           DATE,
    RETRY_COUNT         NUMBER(3)       DEFAULT 0
);

-- ============================
-- SETTLEMENT_RECORDS
-- ============================
CREATE TABLE SETTLEMENT_RECORDS (
    RECORD_ID       VARCHAR(30)     PRIMARY KEY,
    ORDER_ID        VARCHAR(30)     NOT NULL,
    CLIENT_ID       VARCHAR(20)     NOT NULL,
    SYMBOL          VARCHAR(10)     NOT NULL,
    QUANTITY        NUMBER(10)      NOT NULL,
    SIDE            VARCHAR(4)      NOT NULL,
    AMOUNT          NUMBER(15,4)    NOT NULL,
    COMMISSION      NUMBER(10,4)    DEFAULT 0,
    TRADE_DATE      DATE            NOT NULL,
    SETTLEMENT_DATE DATE,           -- T+3 baby
    STATUS          VARCHAR(15)     DEFAULT 'PENDING',
    BATCH_ID        VARCHAR(30),
    EXTERNAL_REF    VARCHAR(50)     -- ref from clearinghouse
);

CREATE INDEX IDX_SETTLEMENT_STATUS ON SETTLEMENT_RECORDS(STATUS);
CREATE INDEX IDX_SETTLEMENT_BATCH ON SETTLEMENT_RECORDS(BATCH_ID);

-- ============================
-- AUDIT_LOG
-- ============================
CREATE SEQUENCE SEQ_AUDIT_LOG START WITH 1 INCREMENT BY 1;

CREATE TABLE AUDIT_LOG (
    LOG_ID          NUMBER          PRIMARY KEY, -- uses SEQ_AUDIT_LOG.NEXTVAL
    EVENT_TYPE      VARCHAR(30)     NOT NULL,
    SOURCE_SYSTEM   VARCHAR(30),
    ENTITY_TYPE     VARCHAR(20),
    ENTITY_ID       VARCHAR(30),
    DESCRIPTION     VARCHAR2(500),
    LOG_DATE        DATE            DEFAULT SYSDATE,
    USER_ID         VARCHAR(30)
);

-- ============================
-- BILLING_LEDGER
-- ============================
CREATE SEQUENCE SEQ_BILLING_LEDGER START WITH 1 INCREMENT BY 1;

CREATE TABLE BILLING_LEDGER (
    ENTRY_ID        NUMBER          PRIMARY KEY, -- uses SEQ_BILLING_LEDGER.NEXTVAL
    ORDER_ID        VARCHAR(30)     NOT NULL,
    CLIENT_ID       VARCHAR(20)     NOT NULL,
    GROSS_AMOUNT    NUMBER(15,4)    NOT NULL,
    COMMISSION_AMOUNT NUMBER(10,4)  NOT NULL,
    NET_AMOUNT      NUMBER(15,4)    NOT NULL,
    CHARGED_DATE    DATE            DEFAULT SYSDATE,
    STATUS          VARCHAR(15)     DEFAULT 'CHARGED'
);

CREATE INDEX IDX_BILLING_CLIENT ON BILLING_LEDGER(CLIENT_ID);
CREATE INDEX IDX_BILLING_ORDER ON BILLING_LEDGER(ORDER_ID);

-- ============================
-- PRICING_CACHE
-- ============================
CREATE TABLE PRICING_CACHE (
    SYMBOL          VARCHAR(10)     PRIMARY KEY,
    BID_PRICE       NUMBER(15,4),
    ASK_PRICE       NUMBER(15,4),
    LAST_PRICE      NUMBER(15,4),
    CURRENCY        VARCHAR(3)      DEFAULT 'USD',
    LAST_UPDATED    DATE            DEFAULT SYSDATE
);

-- Sample data for development
INSERT INTO CLIENTS VALUES ('C001', 'Acme Trading LLC', 'trading@acme.com', '555-0100', 'GOLD', 500000.00, 1, SYSDATE);
INSERT INTO CLIENTS VALUES ('C002', 'Henderson Capital', 'orders@henderson.com', '555-0200', 'PLATINUM', 5000000.00, 1, SYSDATE);
INSERT INTO CLIENTS VALUES ('C003', 'Smith & Associates', 'desk@smithassoc.com', '555-0300', 'SILVER', 250000.00, 1, SYSDATE);
INSERT INTO CLIENTS VALUES ('C004', 'MegaFund Inc', 'ops@megafund.com', '555-0400', 'GOLD', 1000000.00, 1, SYSDATE);
INSERT INTO CLIENTS VALUES ('C005', 'Pinnacle Investments', 'trade@pinnacle.com', '555-0500', 'BRONZE', 100000.00, 1, SYSDATE);

INSERT INTO PRICING_CACHE VALUES ('MSFT', 25.50, 25.75, 25.63, 'USD', SYSDATE);
INSERT INTO PRICING_CACHE VALUES ('IBM', 120.00, 120.50, 120.25, 'USD', SYSDATE);
INSERT INTO PRICING_CACHE VALUES ('ORCL', 15.25, 15.50, 15.38, 'USD', SYSDATE);
INSERT INTO PRICING_CACHE VALUES ('SUNW', 8.75, 9.00, 8.88, 'USD', SYSDATE);
INSERT INTO PRICING_CACHE VALUES ('CSCO', 22.00, 22.25, 22.13, 'USD', SYSDATE);
INSERT INTO PRICING_CACHE VALUES ('INTC', 30.50, 30.75, 30.63, 'USD', SYSDATE);
INSERT INTO PRICING_CACHE VALUES ('DELL', 35.00, 35.25, 35.13, 'USD', SYSDATE);
