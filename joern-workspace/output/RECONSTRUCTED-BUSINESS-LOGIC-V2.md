# Reconstructed Business Logic V2: BigCorp Trade Order Management System

> Second-iteration reconstruction. Improves on V1 via deeper source code reading,
> Joern CPG attribute-flow analysis, and targeted investigation of uncertain areas.
> NO documentation files (README, CHANGELOG, docs/) were consulted.

---

## 1. System Purpose

**Domain:** Financial trade order management (equities brokerage / institutional trading).

**What it does:** Accepts trade orders (BUY/SELL) from institutional clients via JMS messaging and a web frontend, validates them against a configurable chain of business and regulatory rules, obtains pricing from an internal SOAP service, fills orders, sends notifications, creates billing/audit records, and processes end-of-day settlement with a clearinghouse via SFTP file exchange.

**Who are the users:**
- Institutional trading clients (Acme Trading, Henderson Capital, Smith & Associates, MegaFund, Pinnacle Investments, Global Macro Fund, Velocity Trading, Falcon Trading, Apex Capital, Sterling Investments)
- Internal compliance team (monitors surveillance flags via SURVEILLANCE_AUDIT_LOG, manages kill switches)
- Internal operations/settlements team (manages batch processing, reconciliation)
- Internal risk team (reviews RISK_ASSESSMENTS, VaR flagging)
- Internal sales team (negotiates special client arrangements)

**Technology era:** Java 1.4 / J2EE (1999-2002 core, with incremental additions through 2021). Uses Servlets 2.3, JMS (ActiveMQ for dev, IBM MQSeries in prod), JDBC (HSQLDB for dev, Oracle in prod), SOAP (hand-crafted), JSP for web UI.

---

## 2. Database Schema (Full DDL)

### 2.1 CLIENTS

```sql
CREATE TABLE IF NOT EXISTS CLIENTS (
  CLIENT_ID VARCHAR(20) PRIMARY KEY,
  CLIENT_NAME VARCHAR(100) NOT NULL,
  EMAIL VARCHAR(100),
  PHONE VARCHAR(20),
  TIER VARCHAR(10) DEFAULT 'BRONZE',
  MAX_ORDER_VALUE DECIMAL(15,2) DEFAULT 100000.00,
  ACTIVE INTEGER DEFAULT 1,
  KYC_STATUS VARCHAR(20) DEFAULT 'APPROVED',
  KILL_SWITCH VARCHAR(1) DEFAULT 'N',
  CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### 2.2 TRADE_ORDERS

```sql
CREATE TABLE IF NOT EXISTS TRADE_ORDERS (
  ORDER_ID VARCHAR(30) PRIMARY KEY,
  CLIENT_ID VARCHAR(20) NOT NULL,
  SYMBOL VARCHAR(10) NOT NULL,
  QUANTITY INTEGER NOT NULL,
  SIDE VARCHAR(4) NOT NULL,
  PRICE DECIMAL(15,4) DEFAULT 0,
  REQUESTED_PRICE DECIMAL(15,4) DEFAULT 0,
  STATUS VARCHAR(20) DEFAULT 'NEW',
  ORDER_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  LAST_MODIFIED TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  NOTES VARCHAR(500),
  SURVEILLANCE_FLAGS VARCHAR(200) DEFAULT '',
  FOREIGN KEY (CLIENT_ID) REFERENCES CLIENTS(CLIENT_ID)
)
```

### 2.3 NOTIFICATIONS

```sql
CREATE TABLE IF NOT EXISTS NOTIFICATIONS (
  NOTIFICATION_ID VARCHAR(30) PRIMARY KEY,
  NOTIFICATION_TYPE VARCHAR(20) NOT NULL,
  RECIPIENT VARCHAR(100) NOT NULL,
  SUBJECT VARCHAR(200),
  BODY VARCHAR(2000),
  CHANNEL VARCHAR(10) NOT NULL,
  STATUS VARCHAR(10) DEFAULT 'PENDING',
  ORDER_ID VARCHAR(30),
  CREATED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  SENT_DATE TIMESTAMP,
  RETRY_COUNT INTEGER DEFAULT 0
)
```

### 2.4 SETTLEMENT_RECORDS

```sql
CREATE TABLE IF NOT EXISTS SETTLEMENT_RECORDS (
  RECORD_ID VARCHAR(30) PRIMARY KEY,
  ORDER_ID VARCHAR(30) NOT NULL,
  CLIENT_ID VARCHAR(20) NOT NULL,
  SYMBOL VARCHAR(10) NOT NULL,
  QUANTITY INTEGER NOT NULL,
  SIDE VARCHAR(4) NOT NULL,
  AMOUNT DECIMAL(15,4) NOT NULL,
  COMMISSION DECIMAL(10,4) DEFAULT 0,
  TRADE_DATE TIMESTAMP NOT NULL,
  SETTLEMENT_DATE TIMESTAMP,
  STATUS VARCHAR(15) DEFAULT 'PENDING',
  BATCH_ID VARCHAR(30),
  EXTERNAL_REF VARCHAR(50)
)
```

### 2.5 AUDIT_LOG

```sql
CREATE TABLE IF NOT EXISTS AUDIT_LOG (
  LOG_ID INTEGER IDENTITY PRIMARY KEY,
  EVENT_TYPE VARCHAR(30) NOT NULL,
  SOURCE_SYSTEM VARCHAR(30),
  ENTITY_TYPE VARCHAR(20),
  ENTITY_ID VARCHAR(30),
  DESCRIPTION VARCHAR(500),
  LOG_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  USER_ID VARCHAR(30)
)
```

### 2.6 BILLING_LEDGER

```sql
CREATE TABLE IF NOT EXISTS BILLING_LEDGER (
  ENTRY_ID INTEGER IDENTITY PRIMARY KEY,
  ORDER_ID VARCHAR(30) NOT NULL,
  CLIENT_ID VARCHAR(20) NOT NULL,
  GROSS_AMOUNT DECIMAL(15,4) NOT NULL,
  COMMISSION_AMOUNT DECIMAL(10,4) NOT NULL,
  NET_AMOUNT DECIMAL(15,4) NOT NULL,
  CHARGED_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  STATUS VARCHAR(15) DEFAULT 'CHARGED'
)
```

### 2.7 RULE_AUDIT_LOG

```sql
CREATE TABLE IF NOT EXISTS RULE_AUDIT_LOG (
  AUDIT_ID INTEGER IDENTITY PRIMARY KEY,
  RULE_NAME VARCHAR(50) NOT NULL,
  ORDER_ID VARCHAR(30),
  CLIENT_ID VARCHAR(20),
  RESULT VARCHAR(10) NOT NULL,
  EVALUATION_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  DETAILS VARCHAR(500)
)
```

### 2.8 DAILY_VOLUME_TRACKER

```sql
CREATE TABLE IF NOT EXISTS DAILY_VOLUME_TRACKER (
  CLIENT_ID VARCHAR(20) NOT NULL,
  TRADE_DATE DATE NOT NULL,
  TOTAL_SHARES INTEGER DEFAULT 0,
  LAST_UPDATED TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (CLIENT_ID, TRADE_DATE)
)
```

### 2.9 PRICING_CACHE

```sql
CREATE TABLE IF NOT EXISTS PRICING_CACHE (
  SYMBOL VARCHAR(10) PRIMARY KEY,
  BID_PRICE DECIMAL(15,4),
  ASK_PRICE DECIMAL(15,4),
  LAST_PRICE DECIMAL(15,4),
  CURRENCY VARCHAR(3) DEFAULT 'USD',
  LAST_UPDATED TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### 2.10 SURVEILLANCE_AUDIT_LOG

```sql
CREATE TABLE IF NOT EXISTS SURVEILLANCE_AUDIT_LOG (
  LOG_ID INTEGER IDENTITY PRIMARY KEY,
  RULE_NAME VARCHAR(100) NOT NULL,
  ORDER_ID VARCHAR(50),
  CLIENT_ID VARCHAR(20),
  SYMBOL VARCHAR(10),
  RESULT VARCHAR(20),
  SURVEILLANCE_FLAGS VARCHAR(200),
  EVALUATION_TIME TIMESTAMP,
  DETAILS VARCHAR(500)
)
```

### 2.11 POSITION_TRACKING

```sql
CREATE TABLE IF NOT EXISTS POSITION_TRACKING (
  CLIENT_ID VARCHAR(20) NOT NULL,
  SYMBOL VARCHAR(10) NOT NULL,
  NET_POSITION INTEGER DEFAULT 0,
  LAST_UPDATED TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (CLIENT_ID, SYMBOL)
)
```

### 2.12 RISK_ASSESSMENTS

```sql
CREATE TABLE IF NOT EXISTS RISK_ASSESSMENTS (
  RISK_ORDER_ID VARCHAR(50) PRIMARY KEY,
  SOURCE_ORDER_ID VARCHAR(50),
  CLIENT_ID VARCHAR(20),
  SYMBOL VARCHAR(10),
  QUANTITY INTEGER,
  SIDE VARCHAR(4),
  PRICE DECIMAL(15,4),
  NOTIONAL_VALUE DECIMAL(20,4),
  EXPOSURE_CONTRIBUTION DECIMAL(20,4),
  VAR_CONTRIBUTION DECIMAL(20,4),
  RISK_STATUS VARCHAR(20),
  ASSESSMENT_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### 2.13 REG_REPORT_LOG

```sql
CREATE TABLE IF NOT EXISTS REG_REPORT_LOG (
  LOG_ID INTEGER IDENTITY PRIMARY KEY,
  REPORT_TYPE VARCHAR(50) NOT NULL,
  FILE_PATH VARCHAR(500),
  RECORD_COUNT INTEGER DEFAULT 0,
  STATUS VARCHAR(50),
  GENERATION_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### 2.14 Seed Data

```sql
-- Clients
INSERT INTO CLIENTS VALUES ('C001', 'Acme Trading LLC', 'trading@acme.com', '555-0100', 'GOLD', 500000.00, 1, 'APPROVED', 'N', ...);
INSERT INTO CLIENTS VALUES ('C002', 'Henderson Capital', 'orders@henderson.com', '555-0200', 'PLATINUM', 5000000.00, 1, 'APPROVED', 'N', ...);
INSERT INTO CLIENTS VALUES ('C003', 'Smith & Associates', 'desk@smithassoc.com', '555-0300', 'SILVER', 250000.00, 1, 'APPROVED', 'N', ...);
INSERT INTO CLIENTS VALUES ('C004', 'MegaFund Inc', 'ops@megafund.com', '555-0400', 'GOLD', 1000000.00, 1, 'APPROVED', 'N', ...);
INSERT INTO CLIENTS VALUES ('C005', 'Pinnacle Investments', 'trade@pinnacle.com', '555-0500', 'BRONZE', 100000.00, 1, 'APPROVED', 'N', ...);
INSERT INTO CLIENTS VALUES ('C006', 'Global Macro Fund', 'globalfund@trading.com', '555-0600', 'PLATINUM', 10000000.00, 1, 'APPROVED', 'N', ...);
INSERT INTO CLIENTS VALUES ('C007', 'Velocity Trading LLC', 'trades@velocity.com', '555-0700', 'GOLD', 2000000.00, 1, 'APPROVED', 'N', ...);

-- Pricing Cache
INSERT INTO PRICING_CACHE VALUES ('MSFT', 25.50, 25.75, 25.63, 'USD', ...);
INSERT INTO PRICING_CACHE VALUES ('IBM', 120.00, 120.50, 120.25, 'USD', ...);
INSERT INTO PRICING_CACHE VALUES ('ORCL', 15.25, 15.50, 15.38, 'USD', ...);
INSERT INTO PRICING_CACHE VALUES ('SUNW', 8.75, 9.00, 8.88, 'USD', ...);
INSERT INTO PRICING_CACHE VALUES ('CSCO', 22.00, 22.25, 22.13, 'USD', ...);
INSERT INTO PRICING_CACHE VALUES ('INTC', 30.50, 30.75, 30.63, 'USD', ...);
INSERT INTO PRICING_CACHE VALUES ('DELL', 35.00, 35.25, 35.13, 'USD', ...);
```

---

## 3. State Machines (Detailed)

### 3.1 TradeOrder Status

```
                   ┌──────────────────────────────────────────────────────────┐
                   │                                                          │
                   ▼                                                          │
┌─────┐  rule engine passes   ┌────────┐  settlement batch   ┌─────────┐    │
│ NEW │ ─────────────────────► │ FILLED │ ──────────────────► │ SETTLED │    │
└─────┘  + pricing OK         └────────┘                     └─────────┘    │
   │                                                              │          │
   │  rule fails / price                          reconciliation  │          │
   │  deviation / client                          status codes:   ▼          │
   │  not found / inactive                                                   │
   │                          ┌──────────────┐   ◄── CONF ──  CONFIRMED     │
   └──────────────────────► │  REJECTED    │   ◄── REJC ──  FAILED         │
                             └──────────────┘   ◄── DISC ──  DISCREPANCY   │
                                                                             │
                             ┌──────────────┐                                │
                             │ VALIDATED    │ ◄─ DEFINED BUT NEVER SET       │
                             │ PRICED       │                                │
                             │ PENDING_REVIEW│ ◄─ JIRA-2341 (never impl)    │
                             │ CANCELLED    │ ◄─ No code path sets this     │
                             └──────────────┘                                │
```

**Exact Transition Conditions (from Joern analysis):**

| From | To | Condition | Location |
|------|----|-----------|----------|
| NEW | FILLED | `ruleEngine.evaluate(context) == true` AND `quotedPrice > 0` AND price deviation <= 10% | `OrderMessageListener.processOrder()` |
| NEW | REJECTED | Client not found OR `!client.isActive()` OR `ruleEngine.evaluate(context) == false` OR `quotedPrice <= 0` OR price deviation > 10% | `OrderMessageListener.rejectOrder()` |
| FILLED | SETTLED | Selected by `SettlementDAO.findFilledOrders()` during batch processing | `BatchProcessor.processBatch()` |
| SETTLED | CONFIRMED | Reconciliation file contains "CONF" status | `ReconciliationProcessor` / `SftpPoller` |
| SETTLED | FAILED | Reconciliation file contains "REJC" status | `ReconciliationProcessor` / `SftpPoller` |
| SETTLED | DISCREPANCY | Reconciliation file contains "DISC" status | `ReconciliationProcessor` / `SftpPoller` |

### 3.2 SettlementRecord Status

```
┌─────────┐  created by   ┌───────────┐  file gen    ┌──────────┐  SFTP upload  ┌──────────┐
│ PENDING │ ────────────► │ GENERATED │ ──────────► │ UPLOADED │ ────────────► │ CONFIRMED│
└─────────┘  BatchProcessor└───────────┘              └──────────┘              └──────────┘
                                                          │                         │
                                                          ├── reconciliation REJC ──►│ FAILED
                                                          ├── reconciliation DISC ──►│ DISCREPANCY
                                                          └── reconciliation CONF ──►│ RECONCILED
```

### 3.3 Notification Status

```
┌─────────┐  dispatch success  ┌──────┐
│ PENDING │ ──────────────────► │ SENT │
└─────────┘                    └──────┘
     │
     └── dispatch fails AND retries exhausted ──► FAILED
```

Condition: `if (dispatchSuccess)` → SENT; `if (currentRetry < MAX_RETRY_COUNT)` → FAILED (in `NotificationListener.processNotification`)

### 3.4 DerivativeOrder Status

```
┌─────┐  validationError != null   ┌──────────┐
│ NEW │ ──────────────────────────► │ REJECTED │
└─────┘                            └──────────┘
   │
   └── validationError == null ──► FILLED
```

### 3.5 RiskOrder Status

```
┌─────────┐  VaR <= $50,000   ┌──────────┐
│ PENDING │ ────────────────► │ ASSESSED │
└─────────┘                   └──────────┘
     │
     ├── VaR > $50,000 ────────► FLAGGED
     └── exception ────────────► ERROR
```

---

## 4. Business Rules

Rules are defined in `config/rules.xml` and evaluated by the `RuleEngine` singleton. The engine sorts rules by priority in **DESCENDING** order (higher number = runs first). This is a reversed-comparator bug preserved for backward compatibility. System property `bigcorp.rules.priority.fixed=true` switches to ascending order.

**Rule chain behavior:** First rule to fail stops evaluation; order is rejected. The `execute()` method is called ONLY for rules that pass. If `execute()` throws, the exception is caught and logged but the order is NOT rejected (deliberate decision after "the 2001 incident where a logging rule crashed and rejected 500 orders").

### 4.1 Execution Order (Descending Priority)

| Priority | Rule | Category | Can Reject? |
|----------|------|----------|-------------|
| 125 | LayeringDetection | Surveillance (2015) | No (flags only) |
| 124 | SpoofingPattern | Surveillance (2015) | No (flags only) |
| 123 | PositionLimit | Surveillance (2015) | **Yes** |
| 120 | MarketHalt | Circuit Breaker (2011) | **Yes** |
| 118 | ClientKillSwitch | Circuit Breaker (2011) | **Yes** |
| 115 | KYCStatus | Compliance (2005) | **Yes** |
| 110 | DailyVolumeLimit | Compliance (2005) | **Yes** |
| 105 | WashTradeDetection | Compliance (2005) | **Yes** |
| 100 | MaxOrderValue | Business (original) | **Yes** |
| 95 | RestrictedSymbol | Business (2003) | **Yes** |
| 90 | ClientTier | Business (original) | **Yes** |
| 80 | MarketHours | Business (original) | No (warns only) |
| 75 | ShortSale | Business (2003) | **Yes** |
| 60 | MultiCurrency | Business (2014) | No (enriches context) |
| 55 | VolumeDiscount | Business (2009) | No (enriches context) |
| 50 | SpecialClients | Business (original) | No (enriches context) |
| 45 | LoyaltyBonus | Business (2009) | No (enriches context) |

### 4.2 evaluate() vs execute() Flow Per Rule

For **each rule** in priority order:
1. `rule.isActive()` — if false, skip (but see known bug about XML `active` attribute)
2. If rule implements `TypedRule`: call `evaluateTyped()`, get `RuleResult`, call `result.applyToContext()`
3. Else: call `rule.evaluate(context)` — returns boolean
4. If evaluate returns **false**: set rejection reason → `return false` (chain stops)
5. If evaluate returns **true**: call `rule.execute(context)` (side effects). If execute throws, log and swallow.
6. Continue to next rule

### 4.3 Detailed Rule Specifications

*(Unchanged from V1 for most rules, corrections noted below)*

#### Corrections to V1:

1. **ShortSaleRule** — V1 states "Execute (on pass): Calculates commission using CommissionCalculator tier rate, stores in context." Verified from source: `evaluate()` (not `execute()`) does this check. The rule stores `short_sale_commission` attribute equal to `quantity * rate` where rate comes from CommissionCalculator. The `execute()` method is empty. **Correction:** The commission calculation happens in `evaluate()`, not `execute()`.

2. **PositionLimitRule** — V1 states threshold is "100,000 shares per client per symbol." Verified from source: the threshold constant is `POSITION_LIMIT = 100000`. Confirmed correct.

3. **MarketHoursRule** — V1 says "Never rejects". Verified from source: `evaluate()` always returns `true`. It sets `context.setAttribute("queued", Boolean.TRUE)` for off-hours orders. Confirmed correct.

---

## 5. Financial Logic (Corrected and Expanded)

### 5.1 Commission Calculation — The ACTUAL Flow

**Central calculator:** `CommissionCalculator` (common-lib) — this is the AUTHORITATIVE source.

| Tier | Rate | Percentage |
|------|------|-----------|
| PLATINUM | 0.005 | 0.5% |
| GOLD | 0.010 | 1.0% |
| SILVER | 0.015 | 1.5% |
| BRONZE | 0.020 | 2.0% |
| DEFAULT (null tier) | 0.020 | 2.0% |

**Formula:** `commission = orderValue * getRate(client.getTier())`

#### V2 FINDING: Volume discounts are NOT applied to final commission

The `VolumeDiscountRule.execute()` stores `volume_discount` (0.50 or 0.25) as a RuleContext attribute. However, **no downstream code reads this attribute.** The actual commission calculation in `OrderMessageListener.processOrder()` at line 277 is:

```java
double commission = CommissionCalculator.calculate(totalValue, client);
```

This calls `CommissionCalculator.calculate(orderValue, client)` which uses ONLY the client's tier. There is no code path that reads `volume_discount` from the context and applies it. The Joern attribute-flow analysis confirms `volume_discount` is in the "SET but never READ" list.

**Conclusion: Volume discounts are a dead feature.** They are computed and stored but never consumed.

#### V2 FINDING: Loyalty bonus is NOT applied to final commission

Similarly, `LoyaltyBonusRule.execute()` stores `loyalty_bonus = 0.10` in the context for clients C001, C002, C003. No downstream code reads this attribute. The Joern attribute-flow analysis confirms `loyalty_bonus` is in the "SET but never READ" list.

**Conclusion: Loyalty bonus is a dead feature.** It is computed but never consumed.

#### V2 FINDING: commission_override is NOT applied either

`SpecialClientsRule.execute()` stores `commission_override` values for specific clients. This is also in the "SET but never READ" list. The actual commission used for billing comes from `CommissionCalculator.calculate(grossAmount, clientTier)` called in `AuditListener.createBillingEntry()`.

**Conclusion: All special client commission overrides stored in RuleContext are dead.** The only actual commission logic is `CommissionCalculator.getRate(tier)`.

### 5.2 Billing Ledger — NET_AMOUNT Calculation

**V2 FINDING: NET_AMOUNT = GROSS_AMOUNT + COMMISSION_AMOUNT**

From `AuditListener.createBillingEntry()` (audit-service):
```java
double grossAmount = order.getQuantity() * order.getPrice();
double commission = CommissionCalculator.calculate(grossAmount, clientTier);
double netAmount = grossAmount + commission;
```

This means NET_AMOUNT represents the **total amount charged to the client** (the trade value plus the commission fee). The client pays gross + commission.

This is also confirmed by the notification body in `OrderMessageListener.sendConfirmationNotification()`:
```java
body.append((order.getQuantity() * price) + commission)  // amount field = gross + commission
```

### 5.3 Where Billing is Created

The billing entry is created by the **AuditListener** (not the order-engine), which:
1. Consumes messages from `BIGCORP.TRADE.CONFIRMATIONS` queue
2. When it receives a FILLED order, calls `createBillingEntry(order)`
3. Looks up `clientTier` fresh from CLIENTS table
4. Calls `CommissionCalculator.calculate(grossAmount, clientTier)` — ignores all RuleContext attributes
5. Inserts into BILLING_LEDGER with status "CHARGED"

**Critical implication:** The commission calculated during order processing (in OrderMessageListener) using `CommissionCalculator.calculate(totalValue, client)` may differ from the commission stored in BILLING_LEDGER because:
- OrderMessageListener uses the Client object from `findClient()` (which includes the full Client with tier)
- AuditListener calls `lookupClientTier()` separately and passes just the tier string
- Both should produce the same result, but there is a window for inconsistency if the client's tier changes between order fill and audit processing

### 5.4 Commission in OrderMessageListener vs Settlement

OrderMessageListener computes commission at fill time:
```java
double commission = CommissionCalculator.calculate(totalValue, client);
```
This value is logged in the order notes (`"commission=" + commission`) and included in the notification body.

The settlement `BatchProcessor` computes commission independently:
```java
String clientTier = lookupClientTier(order.getClientId());
double commission = CommissionCalculator.calculate(amount, clientTier);
```

Both use CommissionCalculator so should agree, but they are independent calculations.

---

## 6. Surveillance Flags — The Persistence Gap

### V2 FINDING: surveillance_flags context attribute is NOT persisted to TRADE_ORDERS table

The full chain:
1. `LayeringDetectionRule.evaluate()` and `SpoofingPatternRule.evaluate()` set `context.setAttribute("surveillance_flags", "LAYERING"/"SPOOFING")`
2. The `TRADE_ORDERS` table has a `SURVEILLANCE_FLAGS VARCHAR(200)` column (added by ALTER TABLE)
3. **However**, `OrderDAO.saveOrder()` does NOT include SURVEILLANCE_FLAGS in its INSERT or UPDATE SQL
4. `OrderDAO.updateOrderStatus()` only updates STATUS, PRICE, and LAST_MODIFIED
5. The `SurveillanceAuditLogger.logSurveillanceDecision()` writes to `SURVEILLANCE_AUDIT_LOG` — but **it is never called from any production rule code** (only from `EndToEndTest.java`)

**Conclusion:** Surveillance flags are:
- Computed by rules and stored in RuleContext (in-memory)
- The TRADE_ORDERS.SURVEILLANCE_FLAGS column exists but is NEVER written to by production code
- The SurveillanceAuditLogger exists but is NEVER called by the surveillance rules themselves
- The only persistence path would be through the RULE_AUDIT_LOG (via RuleAuditLogger which logs every rule decision), but that logs pass/fail, not the specific flag values

**This is a significant integration gap.** The surveillance rules compute flags, the database has columns for them, and a dedicated audit logger exists — but the wiring between them is incomplete.

---

## 7. The Dead Attribute Problem

### Context Attributes SET but Never READ

From Joern CPG attribute-flow analysis, the following attributes are written to RuleContext but **never consumed by any downstream code**:

| Attribute | Set By | Intended Purpose | Status |
|-----------|--------|-----------------|--------|
| `commission_override` | SpecialClientsRule | Override tier commission rate | **DEAD** — never read |
| `volume_discount` | VolumeDiscountRule | Apply % discount on commission | **DEAD** — never read |
| `volume_discount_applied` | VolumeDiscountRule | Flag that discount was applied | **DEAD** — never read |
| `loyalty_bonus` | LoyaltyBonusRule | Additional 10% discount | **DEAD** — never read |
| `pricing_tier_override` | SpecialClientsRule | Override pricing tier for spread calc | **DEAD** — never read |
| `early_access` | SpecialClientsRule | Allow pre-market trading | **DEAD** — never read |
| `multi_currency_priority` | SpecialClientsRule | Priority FX processing | **DEAD** — never read |
| `priority` | ClientTierRule | Order processing priority | **DEAD** — never read |
| `queued` | MarketHoursRule | Order queued for next session | **DEAD** — never read |
| `fx_rate_applied` | MultiCurrencyRule | Applied FX conversion rate | **DEAD** — never read |
| `settlement_currency` | MultiCurrencyRule | Currency for settlement | **DEAD** — never read |
| `short_sale_commission` | ShortSaleRule | Commission for short sale | **DEAD** — never read |
| `restricted_check` | RestrictedSymbolRule | Passed restricted check | **DEAD** — never read |
| `kill_switch_checked` | ClientKillSwitchRule | Confirmed kill switch was checked | **DEAD** — never read |
| `market_halt_checked` | MarketHaltRule | Confirmed market halt was checked | **DEAD** — never read |
| `kyc_status` | KYCStatusRule | KYC status of client | **DEAD** — never read |
| `daily_volume_checked` | DailyVolumeLimitRule | Confirmed volume was checked | **DEAD** — never read |
| `compliance_flags` | DailyVolumeLimitRule | Compliance check results | **DEAD** — never read |
| `wash_trade_checked` | WashTradeDetectionRule | Confirmed wash trade was checked | **DEAD** — never read |
| `layering_checked` | LayeringDetectionRule | Confirmed layering was checked | **DEAD** — never read |
| `layering_order_count` | LayeringDetectionRule | Number of recent orders | **DEAD** — never read |
| `layering_status` | LayeringDetectionRule | FLAGGED/CLEAR/SKIPPED | **DEAD** — never read |
| `spoofing_checked` | SpoofingPatternRule | Confirmed spoofing was checked | **DEAD** — never read |
| `spoofing_cancel_rate` | SpoofingPatternRule | Cancellation rate | **DEAD** — never read |
| `spoofing_status` | SpoofingPatternRule | FLAGGED/CLEAR/etc | **DEAD** — never read |
| `position_limit_checked` | PositionLimitRule | Confirmed position was checked | **DEAD** — never read |
| `current_position` | PositionLimitRule | Current net position | **DEAD** — never read |
| `position_status` | PositionLimitRule | WITHIN_LIMIT/REJECTED/etc | **DEAD** — never read |

### Context Attributes READ but Never SET (Missing Producers)

| Attribute | Read By | Issue |
|-----------|---------|-------|
| `currency` | MultiCurrencyRule | Read from context but **no rule or code sets it** — always null, so MultiCurrencyRule always takes the "no currency = USD" path |
| `authenticated.user` | AuthenticationFilter | Reads from servlet request attributes (set by container), not RuleContext |

### Only Active Data Flow

| Attribute | Set By | Read By |
|-----------|--------|---------|
| `surveillance_flags` | LayeringDetectionRule, SpoofingPatternRule | LayeringDetectionRule (appends), SpoofingPatternRule (appends) |

The `surveillance_flags` attribute is the ONLY one that participates in active data flow — both rules read it to append their flags (e.g., "LAYERING,SPOOFING"). But as documented in Section 6, this value is never persisted.

---

## 8. JIRA Traceability (Enriched)

### JIRA References Found In Source Code

| JIRA | Context/Quote | File | Status |
|------|---------------|------|--------|
| JIRA-3490 | "Processing anyway (per JIRA-3490 decision)" | `DatReconciliationParser.parse()` | Decision: process even on error |
| JIRA-3501 | "not stored, see JIRA-3501" | `SftpPoller.processNewFormatReconciliation()` | Not stored by design |
| JIRA-7202 | "Apex Capital: GOLD pricing override (JIRA-7202)" | `SpecialClientsRule.execute()` | Fixed (override added) |
| JIRA-2340 | Poll interval 1s→5s | `OrderMessageListener` (POLL_TIMEOUT_MS=5000) | Fixed |
| JIRA-2456 | "this duplicates what the rule engine does" | `OrderMessageListener.processOrder()` | Open (both checks kept) |
| JIRA-2501 | "commission rate is now derived from client tier" | `OrderMessageListener`, `CommissionCalculator` | Fixed |
| JIRA-4101 | "will add to XML config later" | `OrderMessageListener.initRules()` | Open (ShortSaleRule still added programmatically) |
| JIRA-5100 | "TODO: write to AUDIT_LOG table" | `OrderMessageListener.processOrder()` | Open (still console-only) |
| JIRA-5300 | Priority comparator fix via system property | `RuleEngine` | Fixed (behind feature flag) |
| JIRA-6001 | "use CommissionCalculator instead of copy-pasting" | `VolumeDiscountRule` | Open (BASE_COMMISSION still hardcoded 0.02) |
| JIRA-6002 | "client tenure should come from DB but hardcoding" | `LoyaltyBonusRule` | Open (clients C001-C003 hardcoded) |

### Regulatory References

| Reference | Context | Rule/Location |
|-----------|---------|---------------|
| REG-2011-001 | "MARKET HALTED — trading suspended" | MarketHaltRule |
| REG-2011-002 | "Client trading suspended — kill switch active" | ClientKillSwitchRule |
| REG-2011-003 | Audit trail for every rule decision | RuleEngine (RuleAuditLogger calls) |
| REG-2005-001 | "Daily volume limit exceeded" | DailyVolumeLimitRule |
| REG-2005-002 | "Potential wash trade detected" | WashTradeDetectionRule |
| REG-2005-003 | Manual volume warning (belt-and-suspenders) | OrderMessageListener |
| REG-2015-001 | Layering pattern detection | LayeringDetectionRule |
| REG-2015-002 | Spoofing pattern detection (cancel rate) | SpoofingPatternRule |
| REG-2015-003 | Position limit breach | PositionLimitRule |
| REG-2015-004 | Separate surveillance audit trail | SurveillanceAuditLogger |
| REG-2021-001 | Regulatory reporting tables | REG_REPORT_LOG table |

---

## 9. Business Processes (Corrected)

### 9.1 Order Lifecycle (Complete Flow)

```
[Client] --XML/JMS--> [BIGCORP.TRADE.ORDERS queue]
                              |
                    OrderMessageListener.processOrder()
                              |
                    1. Unmarshal XML to TradeOrder (XmlHelper)
                       - If null: skip, log error
                    2. Lookup Client from DB (OrderDAO.findClient)
                       - If not found: REJECT "Client not found"
                       - If inactive: REJECT "Client account is inactive"
                    3. Create RuleContext(order, client)
                    4. Run Rule Engine: ruleEngine.evaluate(context)
                       - For each rule (sorted by priority DESC):
                         a. Check isActive() — skip if false
                         b. Call evaluate(context) — if false, reject
                         c. If passed, call execute(context) — swallow exceptions
                         d. Log decision to RULE_AUDIT_LOG
                       - If any hard rule fails: REJECT with context.getRejectionReason()
                    5. Manual volume warning check (logging only, REG-2005-003)
                       - Does NOT reject
                    6. Get price quote: PricingServiceClient.getQuote(symbol)
                       - SOAP call → DB fallback → hardcoded fallback
                       - If quotedPrice <= 0: REJECT "Price unavailable"
                    7. Manual price deviation check
                       - If |quotedPrice - requestedPrice| / requestedPrice > 10%: REJECT
                    8. Fill Order:
                       - totalValue = quantity * quotedPrice
                       - commission = CommissionCalculator.calculate(totalValue, client)
                       - order.status = FILLED
                       - order.price = quotedPrice
                       - Append to notes: "Filled at {price}, commission={commission}"
                       - orderDAO.saveOrder(order) [full row INSERT/UPDATE]
                       - orderDAO.updateOrderStatus(orderId, FILLED, price) [redundant]
                    9. Send Confirmation Notification → BIGCORP.NOTIFICATIONS
                       - Body: symbol|quantity|side|price||totalValue+commission|
                   10. Send Status Update → BIGCORP.TRADE.CONFIRMATIONS
                       - Consumed by AuditListener → AUDIT_LOG + BILLING_LEDGER
```

### 9.2 Audit/Billing Flow (Downstream of Order Fill)

```
AuditListener (polls BIGCORP.TRADE.CONFIRMATIONS):
    |
    1. Receive order XML
    2. Unmarshal to TradeOrder
    3. processOrderEvent():
       a. Derive event type from status (FILLED/REJECTED/SETTLED)
       b. Insert AUDIT_LOG entry
       c. If status == FILLED:
          - lookupClientTier(clientId) → DB query
          - grossAmount = quantity * price
          - commission = CommissionCalculator.calculate(grossAmount, clientTier)
          - netAmount = grossAmount + commission
          - billingDAO.insertBillingEntry(orderId, clientId, gross, commission, net)
          - Insert second AUDIT_LOG entry (BILLING_CHARGED event)
```

### 9.3 Settlement Process

*(Unchanged from V1)*

### 9.4 Reconciliation Process

```
ReconciliationProcessor.processInbound()
    |
    1. SftpPoller downloads from clearinghouse /outgoing/ directory
    2. Identify file type: .xml or .dat
    3. Parse reconciliation entries (DatReconciliationParser):
       - Header validation — on mismatch: "Processing anyway (per JIRA-3490 decision)"
       - Data lines: extract status code (fixed-width position)
    4. Map status codes:
       - "CONF" → CONFIRMED (via mapDatStatus)
       - "REJC" → FAILED
       - "DISC" → DISCREPANCY
       - "PEND" → remains PENDING (no update)
    5. Update SettlementRecord statuses via SettlementDAO.updateSettlementStatus()
    6. For XML format: SftpPoller.processNewFormatReconciliation()
       - "not stored, see JIRA-3501" — some data intentionally discarded
```

---

## 10. Integration Architecture

### JMS Queues

| Queue Name | Producer(s) | Consumer(s) | Purpose |
|------------|-------------|-------------|---------|
| `BIGCORP.TRADE.ORDERS` | OrderEntryServlet, FrontControllerServlet | OrderMessageListener | Incoming trade orders |
| `BIGCORP.TRADE.CONFIRMATIONS` | OrderMessageListener | AuditListener | Filled/rejected order status |
| `BIGCORP.NOTIFICATIONS` | OrderMessageListener, BatchProcessor | NotificationListener | All notification types |
| `BIGCORP.SETTLEMENT.EVENTS` | (none found) | (none found) | Dead queue — "Created for cancelled project" |
| `BIGCORP.DERIVATIVES.ORDERS` | Derivatives frontend | DerivativeProcessor | Derivative trade orders |
| `BIGCORP.DERIVATIVES.CONFIRMS` | DerivativeProcessor | (unknown) | Derivative confirmations |
| `BIGCORP.DERIVATIVES.PRICING` | (unknown) | (unknown) | Derivative pricing requests |
| `RISK.ORDERS.INBOUND` | (unknown) | RiskScheduler | Orders for risk assessment |
| `RISK.RESULTS.OUTBOUND` | Risk engine | (unknown) | Risk calculation results |
| `BIGCORP_REG_REPORT` | RegulatoryExportJob | (unknown) | Regulatory report events |

---

## 11. Anomalies and Technical Debt

### 11.1 Critical Integration Gaps

1. **Surveillance flags never persisted to TRADE_ORDERS:** Column exists, rules compute flags, but OrderDAO never writes them.
2. **SurveillanceAuditLogger never called from rules:** The logger exists and works, but no rule ever calls `logSurveillanceDecision()`. Only the EndToEndTest does.
3. **Volume discount / loyalty bonus never applied:** Context attributes computed but CommissionCalculator ignores them entirely.
4. **commission_override never applied:** SpecialClientsRule sets it but CommissionCalculator.calculate() only uses tier.
5. **pricing_tier_override never applied:** SpecialClientsRule sets it but PricingServiceClient doesn't read context.
6. **early_access never enforced:** Set for C001, C006 but MarketHoursRule doesn't read it.
7. **currency attribute never set:** MultiCurrencyRule reads `context.getAttribute("currency")` but no code sets it, so it's always null → USD path.

### 11.2 Known Bugs (Preserved)

1. **Reversed Priority Comparator:** Rules sort DESCENDING (high number = runs first). System property `bigcorp.rules.priority.fixed=true` fixes but nobody enables it.
2. **T+3 Settlement Date Doesn't Skip Weekends (JIRA-2890)**
3. **XML `active` attribute not applied to Rule objects**
4. **STATUS_VALIDATED and STATUS_PRICED never set** — flow goes NEW→FILLED directly
5. **STATUS_PENDING_REVIEW defined but never used (JIRA-2341)**
6. **STATUS_CANCELLED never set** — no code path transitions to it, but surveillance rules query for it
7. **SQL injection in OrderDAO** — string concatenation instead of PreparedStatement
8. **Non-thread-safe singleton** in RuleEngine — comment says "the app server is single-threaded" (it isn't)
9. **Dual save in OrderMessageListener** — calls both `saveOrder()` and `updateOrderStatus()` for every fill

### 11.3 Commission Rate Discrepancy

| Source | Rate | Used For |
|--------|------|----------|
| `CommissionCalculator` | tier-based (0.005–0.020) | **Actual billing** (OrderMessageListener, AuditListener, BatchProcessor) |
| `PricingServiceImpl.COMMISSION_RATE` | 0.015 | Price quote spread (intentionally different) |
| `VolumeDiscountRule.BASE_COMMISSION` | 0.02 | Dead code (never applied) |
| `MultiCurrencyRule.COMMISSION_RATE` | 0.02 | Dead code (never applied) |

---

## 12. Confidence Assessment

### High Confidence (verified via source code reading + Joern CPG)

| Claim | Evidence |
|-------|----------|
| Volume discounts are NOT applied | Source shows CommissionCalculator.calculate(totalValue, client) is the only commission path; Joern confirms volume_discount is SET-never-READ |
| Loyalty bonus is NOT applied | Same as above; loyalty_bonus is SET-never-READ |
| NET_AMOUNT = gross + commission | AuditListener line 130: `double netAmount = grossAmount + commission` |
| Surveillance flags NOT persisted to TRADE_ORDERS | OrderDAO.saveOrder() SQL does not include SURVEILLANCE_FLAGS column |
| SurveillanceAuditLogger never called from rules | grep shows only EndToEndTest calls it |
| Order flow is NEW→FILLED (skipping VALIDATED/PRICED) | OrderMessageListener.processOrder() directly sets STATUS_FILLED |
| Rule execute() exceptions are swallowed | RuleEngine.evaluate() lines 196-201: catch + log + continue |
| Commission overrides in SpecialClientsRule are dead | Joern SET-never-READ; CommissionCalculator only takes tier |
| T+3 doesn't skip weekends | Calendar.add(Calendar.DAY_OF_MONTH, 3) in BatchProcessor |
| Billing is created by AuditListener (not order-engine) | AuditListener.createBillingEntry() is the only call to BillingDAO |

### Medium Confidence (verified from source but could have alternative paths)

| Claim | Uncertainty |
|-------|-------------|
| CANCELLED status never set by application code | No setStatus(CANCELLED) found via Joern, but external scripts or manual DB updates could set it |
| Risk engine operates independently | No direct calls from order-engine to risk-engine found, but RISK.ORDERS.INBOUND queue implies async integration |
| Settlement date is always T+3 | Only one code path found, but production Oracle DB may have triggers |
| Pricing fallback order: SOAP → DB → hardcoded | Verified in PricingServiceClient/PricingServiceImpl, but network config could change endpoint |
| FX conversion never actually applied to order values | MultiCurrencyRule execute() is described as no-op; currency attribute never set |

### Low Confidence (structural observation, limited verification)

| Claim | Why low confidence |
|-------|-------------------|
| Derivatives engine is fully independent from trade orders | Limited source coverage; queue names exist but integration points may be in deployment config |
| Regulatory reporting generates correct reports | RegulatoryExportJob exists but no downstream validation visible |
| SMS/FAX channels are stubs | Only observed as constants with no implementation; could be in external library |
| The "early_access" feature for C001/C006 works at all | No code reads it; may have been removed or implemented externally |
| DAILY_VOLUME_TRACKER table is written to | Table created but no INSERT found in analyzed source |

---

## 13. Answers to Investigation Questions

### Q1: Are volume discounts actually applied to final commission?

**NO.** `VolumeDiscountRule.execute()` stores `volume_discount` (0.50 or 0.25) in the RuleContext. But `OrderMessageListener.processOrder()` calls `CommissionCalculator.calculate(totalValue, client)` which only uses `client.getTier()`. The AuditListener billing path also calls `CommissionCalculator.calculate(grossAmount, clientTier)`. Neither path reads the `volume_discount` attribute. **Volume discounts are computed but never consumed.**

### Q2: Is the loyalty_bonus attribute consumed anywhere downstream?

**NO.** `LoyaltyBonusRule.execute()` stores `loyalty_bonus = 0.10` for clients C001, C002, C003. No code anywhere reads `context.getAttribute("loyalty_bonus")`. The Joern analysis confirms it is in the "SET but never READ" category. **Loyalty bonus is a dead feature.**

### Q3: What exactly does execute() do after evaluate() passes — trace for each rule

| Rule | execute() behavior |
|------|--------------------|
| LayeringDetection | Empty method body — no-op |
| SpoofingPattern | Not explicitly shown but same pattern as Layering (no-op) |
| PositionLimit | Not shown — likely no-op (surveillance rules don't have side effects beyond evaluate) |
| MarketHalt | Not visible — likely no-op |
| ClientKillSwitch | Not visible — likely no-op |
| KYCStatus | Not visible — likely no-op |
| DailyVolumeLimit | Not visible — likely no-op |
| WashTradeDetection | Not visible — likely no-op |
| MaxOrderValue | Not visible — likely no-op |
| RestrictedSymbol | Not visible — likely no-op (sets restricted_check in evaluate) |
| **ClientTier** | Sets `context.setAttribute("priority", "HIGH"/"NORMAL")` based on PLATINUM/GOLD vs other |
| **MarketHours** | Not visible — likely sets queued attribute (but that's in evaluate) |
| **ShortSale** | Empty method body (verified) — commission calc is in evaluate() |
| **MultiCurrency** | Not visible — documented as "no-op" in V1 |
| **VolumeDiscount** | Sets `volume_discount` and `volume_discount_applied` (but these are dead) |
| **SpecialClients** | Sets `commission_override`, `early_access`, `pricing_tier_override`, `multi_currency_priority` (ALL dead) |
| **LoyaltyBonus** | Sets `loyalty_bonus = 0.10` (dead) |

### Q4: How does the billing ledger calculate NET_AMOUNT?

**NET_AMOUNT = GROSS_AMOUNT + COMMISSION_AMOUNT**

From `AuditListener.createBillingEntry()`:
```java
double grossAmount = order.getQuantity() * order.getPrice();
double commission = CommissionCalculator.calculate(grossAmount, clientTier);
double netAmount = grossAmount + commission;
```

This represents the **total cost to the client** (trade value plus fee). It is NOT gross minus commission.

### Q5: What happens to surveillance_flags after they're set?

**They are effectively lost.** The full lifecycle:
1. `LayeringDetectionRule.evaluate()` / `SpoofingPatternRule.evaluate()` append "LAYERING"/"SPOOFING" to the `surveillance_flags` RuleContext attribute
2. These rules internally re-read the attribute to append (e.g., "LAYERING,SPOOFING")
3. The RuleContext lives for the duration of one `processOrder()` call
4. After rule evaluation, `processOrder()` proceeds to fill/reject the order
5. `OrderDAO.saveOrder()` does NOT write SURVEILLANCE_FLAGS to the database
6. The TRADE_ORDERS.SURVEILLANCE_FLAGS column is never populated
7. `SurveillanceAuditLogger.logSurveillanceDecision()` exists but is NEVER called from rule code
8. The RuleAuditLogger logs rule pass/fail to RULE_AUDIT_LOG, but not the flag values

**The surveillance flags exist only transiently in memory and are garbage-collected when processOrder() returns.** The infrastructure to persist them (DB column, audit logger) exists but is not wired up.

---

## 14. Gaps Remaining After V2 Analysis

1. **DAILY_VOLUME_TRACKER table is never written to** — the table is created but no INSERT statement targets it in analyzed source. DailyVolumeLimitRule checks per-order quantity (misleading name) rather than tracking cumulative daily volume.

2. **Derivatives engine integration details** — DerivativeProcessor processes derivative orders independently but the relationship to core trade orders is unclear.

3. **Risk engine feed** — `RiskScheduler.fetchUnassessedOrders()` queries TRADE_ORDERS directly (not via queue), suggesting it operates on a polling schedule rather than event-driven. The RISK.ORDERS.INBOUND queue may be for a different flow.

4. **Holiday calendar** — Referenced in comments but no implementation found anywhere.

5. **What triggers CANCELLED status** — No code sets it, but surveillance rules explicitly exclude CANCELLED orders from their queries. This status may only be set via direct DB manipulation by operations staff.
