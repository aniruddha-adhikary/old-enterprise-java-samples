# Reconstructed Business Logic: BigCorp Trade Order Management System

**Analysis Method:** Graphify knowledge graph analysis + Java source code reading
**Analyst:** Devin (code archaeologist)
**Date:** 2026-06-27
**Source Branch:** `devin/1782573156-joern-workspace`

---

## 1. System Purpose

### What It Does

BigCorp Trade Order Management System is a J2EE-era equity trading platform that manages the complete lifecycle of trade orders: entry, validation, pricing, execution (fill/reject), settlement, reconciliation, audit logging, billing, notification dispatch, risk assessment, and regulatory reporting.

### Who Uses It

- **Traders / desk operators** submit orders via a servlet/JSP web application (`trade-desk`), an HTML form with inline CSS targeting IE6/Outlook 2000 era browsers.
- **Institutional clients** (Acme Trading, Henderson Capital, Smith & Associates, MegaFund, Pinnacle Investments, Global Macro Fund, Velocity Trading) with tiered service levels.
- **Back-office / settlements** run end-of-day batch processing for clearinghouse file generation and reconciliation.
- **Compliance / audit** consumes rule evaluation audit trails and surveillance flags.
- **Operations** monitors JMS queues and SFTP file transfers.

### Technology Era

| Aspect | Detail |
|--------|--------|
| Language | Java 1.4 (pre-generics: raw `List`, `HashMap`, `Comparator`, `StringBuffer`) |
| Build | Apache Ant (`build.xml`) |
| App Server | WebLogic (referenced in comments; SOAP namespace conflicts noted) |
| Database | HSQLDB in-memory (dev/demo); Oracle (production, NLS session hacks in `ConnectionHelper`) |
| Messaging | Apache ActiveMQ (embedded `vm://` broker in dev; IBM MQSeries referenced for production) |
| Web Services | Hand-crafted SOAP/XML over HTTP (tried Axis stubs, gave up due to classpath conflicts) |
| Web UI | Servlet 2.x with inline HTML (JSP "will be added later" since 1999) |
| SFTP | JSch library for clearinghouse file transfer |
| Email | JavaMail SMTP (relay at `smtp-internal.bigcorp.com`, no auth) |
| Architecture | Multi-module monorepo with JMS-mediated event-driven microservices (before the term existed) |
| Initial Date | ~1999-2000 (Great Database Outage of Q1 2000 referenced; code comments date to 1999) |
| Active Development | 1999-2021+ (14+ "waves" of features over ~22 years) |

---

## 2. Domain Model

### 2.1 TradeOrder

**Source:** `common-lib/src/com/bigcorp/common/model/TradeOrder.java`

| Field | Type | Description |
|-------|------|-------------|
| `orderId` | `String` | Primary key, format `ORD-{timestamp}` |
| `clientId` | `String` | FK to Client |
| `symbol` | `String` | Stock ticker (MSFT, IBM, ORCL, SUNW, CSCO, INTC, DELL) |
| `quantity` | `int` | Number of shares |
| `side` | `String` | `BUY` or `SELL` |
| `price` | `double` | Actual fill price (set by pricing engine) |
| `requestedPrice` | `double` | Client's limit price |
| `status` | `String` | Lifecycle status (see state machine below) |
| `orderDate` | `Date` | When order was created |
| `lastModified` | `Date` | Last update timestamp |
| `notes` | `String` | Free-text annotations appended during processing |

**Status Constants:**

| Constant | Value | Meaning |
|----------|-------|---------|
| `STATUS_NEW` | `"NEW"` | Just submitted |
| `STATUS_VALIDATED` | `"VALIDATED"` | Passed rule engine |
| `STATUS_PRICED` | `"PRICED"` | Price quote obtained |
| `STATUS_REJECTED` | `"REJECTED"` | Failed validation or pricing |
| `STATUS_FILLED` | `"FILLED"` | Executed at quoted price |
| `STATUS_SETTLED` | `"SETTLED"` | Settlement record created, files generated |
| `STATUS_PENDING_REVIEW` | `"PENDING_REVIEW"` | Flagged for manual review |
| `STATUS_CANCELLED` | `"CANCELLED"` | Order cancelled |

**Side Constants:** `SIDE_BUY = "BUY"`, `SIDE_SELL = "SELL"`

**DB Schema (TRADE_ORDERS table):**

```sql
CREATE TABLE TRADE_ORDERS (
  ORDER_ID VARCHAR(30) PRIMARY KEY,
  CLIENT_ID VARCHAR(20) NOT NULL REFERENCES CLIENTS(CLIENT_ID),
  SYMBOL VARCHAR(10) NOT NULL,
  QUANTITY INTEGER NOT NULL,
  SIDE VARCHAR(4) NOT NULL,
  PRICE DECIMAL(15,4) DEFAULT 0,
  REQUESTED_PRICE DECIMAL(15,4) DEFAULT 0,
  STATUS VARCHAR(20) DEFAULT 'NEW',
  ORDER_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  LAST_MODIFIED TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  NOTES VARCHAR(500),
  SURVEILLANCE_FLAGS VARCHAR(200) DEFAULT ''
);
```

### 2.2 Client

**Source:** `common-lib/src/com/bigcorp/common/model/Client.java`

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `clientId` | `String` | - | PK, format `C0XX` |
| `name` | `String` | - | Company name |
| `email` | `String` | - | Notification recipient |
| `phone` | `String` | - | Contact phone |
| `tier` | `String` | `"BRONZE"` | Service tier |
| `maxOrderValue` | `double` | `100000.0` | Max allowed order value (USD) |
| `active` | `boolean` | `true` | Account active flag |

**Tier Constants:** `TIER_PLATINUM`, `TIER_GOLD`, `TIER_SILVER`, `TIER_BRONZE`

Historical note (from Client.java lines 8-10): "BRONZE was added in 2001 when sales wanted everyone to feel special. PLATINUM was added in 2002 for the Henderson account specifically."

**DB Schema (CLIENTS table):**

```sql
CREATE TABLE CLIENTS (
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
);
```

**Sample Client Data:**

| Client ID | Name | Tier | Max Order Value |
|-----------|------|------|----------------|
| C001 | Acme Trading LLC | GOLD | $500,000 |
| C002 | Henderson Capital | PLATINUM | $5,000,000 |
| C003 | Smith & Associates | SILVER | $250,000 |
| C004 | MegaFund Inc | GOLD | $1,000,000 |
| C005 | Pinnacle Investments | BRONZE | $100,000 |
| C006 | Global Macro Fund | PLATINUM | $10,000,000 |
| C007 | Velocity Trading LLC | GOLD | $2,000,000 |

### 2.3 Notification

**Source:** `common-lib/src/com/bigcorp/common/model/Notification.java`

| Field | Type | Description |
|-------|------|-------------|
| `notificationId` | `String` | PK, format `N-{orderId}-{timestamp}` or `NOTIF-{timestamp}-{hash}` |
| `type` | `String` | Notification type constant |
| `recipient` | `String` | Email address or client ID |
| `subject` | `String` | Email subject line |
| `body` | `String` | Content (pipe-delimited for template substitution) |
| `channel` | `String` | Delivery channel |
| `status` | `String` | Delivery status |
| `orderId` | `String` | Related order |
| `createdDate` | `Date` | When created |
| `sentDate` | `Date` | When sent |
| `retryCount` | `int` | Number of delivery attempts |

**Channel Constants:** `CHANNEL_EMAIL = "EMAIL"`, `CHANNEL_SMS = "SMS"`, `CHANNEL_FAX = "FAX"` (deprecated 2002)

**Status Constants:** `STATUS_PENDING = "PENDING"`, `STATUS_SENT = "SENT"`, `STATUS_FAILED = "FAILED"`

**Type Constants:** `TYPE_ORDER_CONFIRM = "ORDER_CONFIRM"`, `TYPE_ORDER_REJECT = "ORDER_REJECT"`, `TYPE_SETTLEMENT = "SETTLEMENT"`, `TYPE_PRICE_ALERT = "PRICE_ALERT"`

### 2.4 SettlementRecord

**Source:** `common-lib/src/com/bigcorp/common/model/SettlementRecord.java`

| Field | Type | Description |
|-------|------|-------------|
| `recordId` | `String` | PK, format `SR-{timestamp}-{hash}` |
| `orderId` | `String` | Source order |
| `clientId` | `String` | Client reference |
| `symbol` | `String` | Traded symbol |
| `quantity` | `int` | Share count |
| `side` | `String` | BUY/SELL |
| `amount` | `double` | Gross trade value (qty * price) |
| `commission` | `double` | Commission charged (tier-based) |
| `tradeDate` | `Date` | Original order date |
| `settlementDate` | `Date` | T+3 date (weekends/holidays NOT skipped, JIRA-2890) |
| `status` | `String` | Settlement lifecycle status |
| `batchId` | `String` | Settlement batch identifier |
| `externalRef` | `String` | Clearinghouse reference |

**Status Constants:**

| Constant | Value |
|----------|-------|
| `STATUS_PENDING` | `"PENDING"` |
| `STATUS_GENERATED` | `"GENERATED"` |
| `STATUS_UPLOADED` | `"UPLOADED"` |
| `STATUS_CONFIRMED` | `"CONFIRMED"` |
| `STATUS_FAILED` | `"FAILED"` |
| `STATUS_RECONCILED` | `"RECONCILED"` |
| `STATUS_DISCREPANCY` | `"DISCREPANCY"` |

### 2.5 AuditEvent

**Source:** `common-lib/src/com/bigcorp/common/model/AuditEvent.java`

| Field | Type | Description |
|-------|------|-------------|
| `logId` | `int` | Auto-generated PK |
| `eventType` | `String` | Event type constant |
| `sourceSystem` | `String` | Originating module |
| `entityType` | `String` | `"ORDER"` or `"BILLING"` |
| `entityId` | `String` | Related entity ID |
| `description` | `String` | Free-text summary |
| `logDate` | `Date` | Event timestamp |
| `userId` | `String` | Acting client/user ID |

**Event Types:** `EVENT_ORDER_FILLED`, `EVENT_ORDER_REJECTED`, `EVENT_ORDER_SETTLED`, `EVENT_BILLING_CHARGED`

**Source Systems:** `SOURCE_ORDER_ENGINE = "order-engine"`, `SOURCE_SETTLEMENT = "settlement-gateway"`, `SOURCE_AUDIT_SERVICE = "audit-service"`

### 2.6 RuleContext

**Source:** `common-lib/src/com/bigcorp/common/rules/RuleContext.java`

A container object passed through the rule engine chain. Holds:
- `order` (TradeOrder) and `client` (Client)
- `attributes` (HashMap) -- rules set key-value attributes for downstream consumers
- `messages` (List) -- informational messages
- `warnings` (List) -- warning messages
- `rejected` (boolean) -- set to true by `reject(reason)` method
- `rejectionReason` (String) -- reason for rejection

### 2.7 PriceQuote

**Source:** `pricing-service/src/com/bigcorp/pricing/service/PriceQuote.java`

| Field | Type | Description |
|-------|------|-------------|
| `symbol` | `String` | Ticker symbol |
| `bidPrice` | `double` | Bid (buy-side) price |
| `askPrice` | `double` | Ask (sell-side) price |
| `lastPrice` | `double` | Last traded / mid price |
| `currency` | `String` | Default `"USD"` |
| `timestamp` | `Date` | Quote timestamp |

### 2.8 DerivativeOrder

**Source:** `derivatives-engine/src/com/bigcorp/derivatives/core/DerivativeOrder.java`

Separate domain model for the FX/options desk (does NOT extend TradeOrder).

| Field | Type | Description |
|-------|------|-------------|
| `orderId` | `String` | Derivative order ID |
| `clientId` | `String` | Client reference |
| `contractType` | `String` | `FX_SPOT`, `FX_FORWARD`, `OPTION_CALL`, `OPTION_PUT` |
| `underlying` | `String` | Underlying instrument |
| `strikePrice` | `double` | Option strike price |
| `quantity` | `int` | Contract quantity |
| `expiry` | `String` | Expiration date |
| `status` | `String` | `NEW`, `FILLED`, `REJECTED` |
| `premium` | `double` | Option premium |

### 2.9 RiskOrder

**Source:** `risk-engine/src/com/bigcorp/risk/model/RiskOrder.java`

Separate domain model for the risk engine (does NOT extend TradeOrder).

| Field | Type | Description |
|-------|------|-------------|
| `riskOrderId` | `String` | Risk-specific order ID |
| `sourceOrderId` | `String` | Reference to original TradeOrder |
| `clientId` | `String` | Client |
| `symbol` | `String` | Instrument |
| `quantity` | `int` | Shares |
| `side` | `String` | BUY/SELL |
| `price` | `double` | Trade price |
| `notionalValue` | `double` | qty * price (computed) |
| `exposureContribution` | `double` | Directional exposure (computed) |
| `varContribution` | `double` | Value-at-Risk contribution (computed) |
| `riskStatus` | `String` | `PENDING`, `ASSESSED`, `FLAGGED`, `ERROR` |
| `assessmentDate` | `Date` | When assessed |

### 2.10 Relationships (from Graphify graph analysis)

```
TradeOrder --[belongs_to]--> Client (via clientId)
TradeOrder --[generates]--> SettlementRecord (via orderId)
TradeOrder --[triggers]--> Notification (via orderId)
TradeOrder --[audited_by]--> AuditEvent (via entityId)
TradeOrder --[evaluated_by]--> RuleContext --[contains]--> Rule[]
SettlementRecord --[uploaded_via]--> SftpUploader
SettlementRecord --[reconciled_by]--> ReconciliationProcessor
Client --[has_tier]--> CommissionCalculator (tier-based rates)
Notification --[dispatched_via]--> EmailDispatcher | SMSDispatcher
TradeOrder --[assessed_by]--> RiskOrder (via sourceOrderId)
```

---

## 3. Business Rules

### 3.1 Rule Engine Architecture

**Source:** `common-lib/src/com/bigcorp/common/rules/RuleEngine.java`

The RuleEngine is a **singleton** implementing a chain-of-responsibility pattern. It maintains a list of `Rule` implementations, sorts them by priority, and evaluates them sequentially against a `RuleContext`.

**Priority Ordering Bug (JIRA-5300):**

- **Legacy mode (default):** Rules sorted DESCENDING by priority number. Higher number = runs first. This is the backwards behavior.
- **Fixed mode:** Enabled by system property `bigcorp.rules.priority.fixed=true`. Rules sorted ASCENDING. Lower number = runs first (correct behavior).

**Rule Interface:**

```java
interface Rule {
    String getName();
    int getPriority();
    boolean evaluate(RuleContext context);  // true=pass, false=reject
    void execute(RuleContext context);       // called only after evaluate passes
    boolean isActive();
}
```

**TypedRule Interface (Wave 5, 2007 Q1):**

Extended version returning `RuleResult` (with passed boolean, ruleName, message, attributes HashMap) instead of bare boolean. Provides `applyToContext()` for backward compatibility.

**Rule Config Loading:**

Rules can be loaded from `config/rules.xml` via reflection (`RuleConfigLoader`), or registered programmatically in `OrderMessageListener.initRules()`. The XML config is tried first; if empty, falls back to hardcoded registration of 4 basic rules plus `ShortSaleRule` added manually (JIRA-4101).

**Audit Logging (REG-2011-003):**

Every rule evaluation (PASS, FAIL, SKIP) is logged to the `RULE_AUDIT_LOG` table via `RuleAuditLogger`. Audit logging failures are silently swallowed to never block order processing.

**Known Bug: Inactive rules may still execute** (documented in RuleEngine.java lines 134-148).

### 3.2 Complete Rule Inventory (17 Rules)

Rules listed in execution order under **legacy (default) descending priority** -- highest priority number runs first:

#### Rule 1: LayeringDetectionRule (Priority 125)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/LayeringDetectionRule.java`

| Property | Value |
|----------|-------|
| Priority | 125 |
| Behavior | **FLAGS** (does not reject) |
| Threshold | `LAYERING_THRESHOLD = 5` recent orders for same client+symbol |
| DB Query | Counts recent orders from `TRADE_ORDERS` table |
| Attributes Set | `surveillance_flags`, `layering_status` |
| Reference | REG-2015-001 (SEC inquiry on layering patterns) |

#### Rule 2: SpoofingPatternRule (Priority 124)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/SpoofingPatternRule.java`

| Property | Value |
|----------|-------|
| Priority | 124 |
| Behavior | **FLAGS** (does not reject) |
| Threshold | `CANCEL_RATE_THRESHOLD = 0.60` (60% cancellation rate) |
| Attributes Set | `surveillance_flags`, `spoofing_status` |
| Reference | REG-2015-002 |

#### Rule 3: PositionLimitRule (Priority 123)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/PositionLimitRule.java`

| Property | Value |
|----------|-------|
| Priority | 123 |
| Behavior | **REJECTS** |
| Threshold | `DEFAULT_POSITION_LIMIT = 100000` shares |
| DB Query | Looks up `NET_POSITION` from `POSITION_TRACKING` table |
| Logic | Rejects if `abs(currentPosition + orderQuantity) > 100000` |
| Reference | REG-2015-003 |

#### Rule 4: MarketHaltRule (Priority 120)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/MarketHaltRule.java`

| Property | Value |
|----------|-------|
| Priority | 120 |
| Behavior | **REJECTS** (all orders, no exceptions) |
| Trigger | System property `bigcorp.market.halted` is set |
| Attributes Set | `market_halt_checked = true` |
| Reference | REG-2011-001 (SEC circuit breaker requirement) |

#### Rule 5: ClientKillSwitchRule (Priority 118)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/ClientKillSwitchRule.java`

| Property | Value |
|----------|-------|
| Priority | 118 |
| Behavior | **REJECTS** |
| DB Query | Looks up `KILL_SWITCH` column from `CLIENTS` table |
| Logic | Rejects if `KILL_SWITCH = 'Y'` |
| Attributes Set | `kill_switch_checked = true` |
| Reference | REG-2011-002 (rogue algorithm incident) |

#### Rule 6: KYCStatusRule (Priority 115)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/KYCStatusRule.java`

| Property | Value |
|----------|-------|
| Priority | 115 |
| Behavior | **REJECTS** |
| Required Status | `KYC_APPROVED = "APPROVED"` |
| DB Query | Looks up `KYC_STATUS` from `CLIENTS` table |
| Other Statuses | `PENDING`, `EXPIRED`, `REJECTED` all cause rejection |

#### Rule 7: DailyVolumeLimitRule (Priority 110)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/DailyVolumeLimitRule.java`

| Property | Value |
|----------|-------|
| Priority | 110 |
| Behavior | **REJECTS** |
| Threshold | `MAX_SHARES_PER_ORDER = 50000` shares |
| Logic | Rejects if `quantity > 50000` |
| Attributes Set | `daily_volume_checked = true`, `compliance_flags = "VOLUME_CHECKED"` |
| Reference | REG-2005-001 (2005 volume manipulation incident) |

#### Rule 8: WashTradeDetectionRule (Priority 105)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/WashTradeDetectionRule.java`

| Property | Value |
|----------|-------|
| Priority | 105 |
| Behavior | **REJECTS** |
| Threshold | `WASH_TRADE_WINDOW_MINUTES = 5` |
| Logic | Rejects if same client has opposite-side order for same symbol within 5 minutes |
| Regulatory Basis | SEC Rule 10b-5 (anti-manipulation) |
| Reference | REG-2005-002 |

#### Rule 9: MaxOrderValueRule (Priority 100)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/MaxOrderValueRule.java`

| Property | Value |
|----------|-------|
| Priority | 100 |
| Behavior | **REJECTS** |
| Buffer | `BUFFER_MULTIPLIER = 1.10` (10% buffer, per JIRA-1892 Henderson complaint) |
| Logic | Rejects if `(quantity * requestedPrice) > client.maxOrderValue * 1.10` |

#### Rule 10: RestrictedSymbolRule (Priority 95)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/RestrictedSymbolRule.java`

| Property | Value |
|----------|-------|
| Priority | 95 |
| Behavior | **REJECTS** |
| Restricted Symbols | `ENRN`, `WCOM`, `TYCO`, `ADLP` |
| Logic | Rejects any order for a restricted symbol |

#### Rule 11: ClientTierRule (Priority 90)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/ClientTierRule.java`

| Property | Value |
|----------|-------|
| Priority | 90 |
| Behavior | **ALWAYS PASSES** (sets attributes only) |
| Logic | Sets `priority` attribute to `"HIGH"` for PLATINUM/GOLD, `"NORMAL"` for others |

#### Rule 12: MarketHoursRule (Priority 80)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/MarketHoursRule.java`

| Property | Value |
|----------|-------|
| Priority | 80 |
| Behavior | **ALWAYS PASSES** (queues order, does not reject) |
| Market Open | 9:30 AM |
| Market Close | 4:00 PM |
| Off-Hours | Sets `queued = Boolean.TRUE` attribute |
| Weekends | Sets `queued = Boolean.TRUE` attribute |

#### Rule 13: ShortSaleRule (Priority 75)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/ShortSaleRule.java`

| Property | Value |
|----------|-------|
| Priority | 75 |
| Behavior | **REJECTS** |
| Logic | Rejects SELL orders with quantity > 1000 shares |
| Note | Uses `CommissionCalculator` for commission lookup in execute() |

#### Rule 14: MultiCurrencyRule (Priority 60)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/MultiCurrencyRule.java`

| Property | Value |
|----------|-------|
| Priority | 60 |
| Behavior | **ALWAYS PASSES** (sets attributes only) |
| FX Rates (hardcoded, "current as of 2014-03") | See Financial Logic section |
| Logic | Converts order value to USD using hardcoded rates, sets `fx_converted_value` attribute |

#### Rule 15: VolumeDiscountRule (Priority 55)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/VolumeDiscountRule.java`

| Property | Value |
|----------|-------|
| Priority | 55 |
| Behavior | **ALWAYS PASSES** (sets attributes only) |
| BASE_COMMISSION | `0.02` (copy-pasted from CommissionCalculator, TODO JIRA-6001) |
| Discount Tiers | >10,000 shares: `volume_discount = 0.50` (50% off) |
| | >5,000 shares: `volume_discount = 0.25` (25% off) |
| Attributes Set | `volume_discount`, `volume_discount_applied = true` |

#### Rule 16: SpecialClientsRule (Priority 50)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/SpecialClientsRule.java`

| Property | Value |
|----------|-------|
| Priority | 50 |
| Behavior | **ALWAYS PASSES** (sets attributes only) |
| Hardcoded Overrides | See table below |

**Special Client Overrides:**

| Client ID | Name | Override |
|-----------|------|----------|
| C001 | Acme | `early_access = true` |
| C002 | Henderson | `commission_override = 0.0` (zero commission) |
| C003 | Smith | `commission_override = 0.01` (1%) |
| C004 | MegaFund | `pricing_tier_override = PLATINUM` |
| C005 | Pinnacle | `commission_override = 0.01` (1%) |
| C006 | Global Macro | `commission_override = 0.0`, `early_access = true` |
| C007 | Velocity | `pricing_tier_override = PLATINUM` |
| C008 | Falcon (2014) | `commission_override = 0.005` (0.5%) |
| C009 | Apex | `pricing_tier_override = GOLD` |
| C010 | Sterling | `commission_override = 0.0`, `multi_currency_priority = true` |

#### Rule 17: LoyaltyBonusRule (Priority 45)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/LoyaltyBonusRule.java`

| Property | Value |
|----------|-------|
| Priority | 45 |
| Behavior | **ALWAYS PASSES** (sets attributes only) |
| Eligible Clients | `C001`, `C002`, `C003` (hardcoded IDs) |
| Bonus | `loyalty_bonus = 0.10` (10% additional discount on commission) |
| Known Hack | Tenure should come from DB, currently hardcoded (line 45) |

### 3.3 Rules XML Configuration

**Source:** `config/rules.xml`

The XML configuration registers 16 of the 17 rules (ShortSaleRule is added programmatically in `OrderMessageListener.initRules()`, JIRA-4101). The `active` XML attribute is read but **NOT applied** to the Rule object (known bug documented in rules.xml comment and RuleEngine javadoc).

### 3.4 Rule Behavior Summary

| Rule | Priority | Rejects? | Flags? | Attributes Only? |
|------|----------|----------|--------|-------------------|
| LayeringDetectionRule | 125 | | YES | |
| SpoofingPatternRule | 124 | | YES | |
| PositionLimitRule | 123 | YES | | |
| MarketHaltRule | 120 | YES | | |
| ClientKillSwitchRule | 118 | YES | | |
| KYCStatusRule | 115 | YES | | |
| DailyVolumeLimitRule | 110 | YES | | |
| WashTradeDetectionRule | 105 | YES | | |
| MaxOrderValueRule | 100 | YES | | |
| RestrictedSymbolRule | 95 | YES | | |
| ClientTierRule | 90 | | | YES |
| MarketHoursRule | 80 | | | YES (queues) |
| ShortSaleRule | 75 | YES | | |
| MultiCurrencyRule | 60 | | | YES |
| VolumeDiscountRule | 55 | | | YES |
| SpecialClientsRule | 50 | | | YES |
| LoyaltyBonusRule | 45 | | | YES |

---

## 4. Business Processes

### 4.1 Order Lifecycle

#### Phase 1: Order Entry (trade-desk module)

**Source:** `trade-desk/src/com/bigcorp/tradedesk/servlet/OrderEntryServlet.java`

1. Trader fills HTML form (client, symbol, quantity, side, price)
2. `OrderEntryServlet.doPost()` performs basic validation (non-null, positive quantity)
3. Creates `TradeOrder` with `orderId = "ORD-" + System.currentTimeMillis()`
4. Delegates to `TradeMessageProducer.submitOrder()` which:
   - Marshals order to XML via `XmlHelper.marshalTradeOrder()`
   - Sends XML to JMS queue `BIGCORP.TRADE.ORDERS`
   - Also inserts into database
5. Redirects to order status page

#### Phase 2: Order Validation & Processing (order-engine module)

**Source:** `order-engine/src/com/bigcorp/orderengine/consumer/OrderMessageListener.java`

1. `OrderMessageListener` polls `BIGCORP.TRADE.ORDERS` queue (5s timeout, JIRA-2340)
2. Unmarshal XML to `TradeOrder`
3. Look up `Client` from database via `OrderDAO.findClient()`
4. Check if client is active (reject if not)
5. Run rule engine: `ruleEngine.evaluate(context)`
   - If rules fail: reject order with reason
6. **Belt-and-suspenders volume check** (REG-2005-003): manual check `quantity > 50000` -- logs warning only, does not reject (duplicates DailyVolumeLimitRule)
7. Get price quote from SOAP pricing service: `pricingClient.getQuote(symbol)`
   - Falls back to PRICING_CACHE DB table if SOAP fails
8. **Manual price deviation check** (JIRA-2456): rejects if `abs(quoted - requested) / requested > 10%`
   - Duplicates rule engine check, kept because "Bob insists"
9. Calculate commission: `CommissionCalculator.calculate(totalValue, client)`
10. Set order status to `FILLED`, set fill price, append notes
11. Save to DB via `OrderDAO.saveOrder()` AND `OrderDAO.updateOrderStatus()` (redundant but settlement reads from the latter)
12. Send confirmation notification to `BIGCORP.NOTIFICATIONS` queue
13. Send status update to `BIGCORP.TRADE.CONFIRMATIONS` queue

#### Phase 3: Settlement (settlement-gateway module)

**Source:** `settlement-gateway/src/com/bigcorp/settlement/batch/BatchProcessor.java`

End-of-day batch (runs 6 PM EST via cron in production, Timer-based in demo):

1. Query filled orders from DB via `SettlementDAO.findFilledOrders()`
2. Generate batch ID: `"BATCH-" + yyyyMMdd + "-" + paddedSequence`
3. For each filled order:
   a. Create `SettlementRecord` with tier-based commission via `CommissionCalculator`
   b. Calculate settlement date = trade date + 3 days (T+3, **does NOT skip weekends/holidays**, JIRA-2890)
   c. Save settlement record to DB
   d. Update order status to `SETTLED`
4. Generate settlement files:
   a. **XML file** (`SETTLE_{batchId}.xml`): DOM-based XML generation per clearinghouse spec (fax ref CH-SPEC-004, 1999)
   b. **Flat file** (`SETTLE_{batchId}.dat`): COBOL-style fixed-width format per clearinghouse mainframe spec
5. Upload files to clearinghouse via SFTP (`SftpUploader`)
   - Falls back to local `./sftp-root/outbound/` in dev mode
   - Update record statuses to `UPLOADED`
6. Send settlement notifications to `BIGCORP.NOTIFICATIONS` queue

**Settlement Flat File Format (from SettlementFileGenerator):**

```
Header:  "H" + batchId(20) + date(8) + recordCount(6 right-justified)
Detail:  "D" + recordId(20) + orderId(20) + symbol(10) + side(4)
         + quantity(10 right) + amount(15 right 2dec) + commission(10 right)
Trailer: "T" + totalAmount(20 right) + totalCommission(15 right) + recordCount(6)
```

Column widths are critical -- changing them causes clearinghouse rejection (2-week recovery, incident 2000-03-14).

#### Phase 4: Reconciliation (settlement-gateway module)

**Source:** `settlement-gateway/src/com/bigcorp/settlement/reconciliation/ReconciliationProcessor.java` and `SftpPoller.java`

1. `SftpPoller` downloads files from clearinghouse SFTP `/outgoing/` directory
   - Falls back to local `./sftp-root/inbound/` in dev mode
   - Processes `.xml` and `.dat` files
2. **XML reconciliation** supports two formats:
   - Old format (pre-2001): `<reconciliation>` root, `<item>` elements, numeric status codes: `1 = CONFIRMED`, `2 = DISCREPANCY`
   - New format (2001+): `<reconciliationBatch>` root, `<record>` elements, string statuses: `"CONFIRMED"`, `"REJECTED"`, `"DISCREPANCY"`, `"PENDING"` (skipped)
3. **DAT reconciliation** (fixed-width format):
   - Status codes: `CONF` -> RECONCILED, `REJC`/`DISC` -> DISCREPANCY, `PEND` -> skip
4. Update settlement record status in DB
5. Move processed files to `./sftp-root/processed/`

#### Phase 5: Audit & Billing (audit-service module)

**Source:** `audit-service/src/com/bigcorp/audit/consumer/AuditListener.java`

1. Polls `BIGCORP.TRADE.CONFIRMATIONS` queue (5s timeout)
2. Unmarshal trade order from XML
3. Derive event type from order status (FILLED -> ORDER_FILLED, REJECTED -> ORDER_REJECTED, SETTLED -> ORDER_SETTLED)
4. Insert `AuditEvent` to `AUDIT_LOG` table
5. For FILLED orders, create billing ledger entry:
   - Look up client tier from DB
   - Calculate commission via `CommissionCalculator.calculate(grossAmount, tier)`
   - Insert into `BILLING_LEDGER` table (ORDER_ID, CLIENT_ID, GROSS_AMOUNT, COMMISSION_AMOUNT, NET_AMOUNT)
   - Also log a `BILLING_CHARGED` audit event

#### Phase 6: Notifications (notification-gateway module)

**Source:** `notification-gateway/src/com/bigcorp/notifications/consumer/NotificationListener.java`

1. Polls `BIGCORP.NOTIFICATIONS` queue (5s timeout)
2. Unmarshal `Notification` from XML
3. Route by channel:
   - `EMAIL` -> `EmailDispatcher.sendEmail()` via SMTP
   - `SMS` -> `SMSDispatcher.sendSMS()`
   - `FAX` -> logged as unsupported (deprecated 2002)
4. **Retry logic:** max 3 retries (`MAX_RETRY_COUNT = 3`), re-queued on failure
5. On final failure: status set to `FAILED`
6. Persist notification record to `NOTIFICATIONS` table (INSERT only, no updates)

**Email Dispatch (EmailDispatcher):**

- SMTP config from `notification.properties` (host, port 25, from `noreply@bigcorp.com`)
- Dev mode: logs email to console instead of sending
- Template-based: loads templates from classpath for ORDER_CONFIRM, ORDER_REJECT, SETTLEMENT
- Pipe-delimited body parsed for template substitution: `symbol|quantity|side|price|reason|amount|settlementDate`
- HTML email support with inline CSS (for Outlook 2000 compatibility)
- SMTP relay unreliable during market close (5:00-5:30 PM EST)

#### Phase 7: Risk Assessment (risk-engine module)

**Source:** `risk-engine/src/com/bigcorp/risk/engine/ExposureCalculator.java`

Independent risk assessment pipeline:

1. Uses own JMS queues: `RISK.ORDERS.INBOUND`, `RISK.RESULTS.OUTBOUND`
2. Calculates for each `RiskOrder`:
   - Notional value = qty * price
   - Directional exposure: BUY = +notional, SELL = -notional
   - VaR (parametric delta-normal): `notional * vol * z_score * sqrt(t/252)`
3. VaR flag threshold: `> $50,000` -> `FLAGGED`, else `ASSESSED`

#### Phase 8: Reporting (reporting-service module)

**Source:** `reporting-service/src/com/bigcorp/reporting/engine/ReportGenerator.java`

Generates reports in HTML and CSV:

| Report | SQL Source | Columns |
|--------|-----------|---------|
| Daily P&L | `getDailyPnlSummary()` | TRADE_DATE, SYMBOL, SIDE, TOTAL_QTY, TOTAL_VALUE, ORDER_COUNT |
| Monthly Volume | `getMonthlyVolumeReport()` | MONTH, TOTAL_ORDERS, TOTAL_VOLUME, TOTAL_VALUE |
| Billing Summary | `getBillingSummary()` | CLIENT_ID, TOTAL_GROSS, TOTAL_COMMISSION, TOTAL_NET, ENTRY_COUNT |
| Settlement Summary | `getSettlementSummary()` | STATUS, RECORD_COUNT, TOTAL_AMOUNT, TOTAL_COMMISSION |

### 4.2 Order State Machine

```
                    +--> PENDING_REVIEW
                    |
NEW --> [rules] --> VALIDATED --> [pricing] --> PRICED --> FILLED --> SETTLED --> RECONCILED
          |                          |                                             |
          +--> REJECTED              +--> REJECTED                           DISCREPANCY
          |
          +--> CANCELLED
```

Note: In practice, the code in `OrderMessageListener.processOrder()` goes directly from rule evaluation to pricing to FILLED without explicitly setting VALIDATED or PRICED intermediate statuses. The intermediate statuses exist in the model but are unused in the main processing path.

---

## 5. Financial Logic

### 5.1 Commission Rates (Tier-Based)

**Source:** `common-lib/src/com/bigcorp/common/billing/CommissionCalculator.java`

| Tier | Rate | Percentage |
|------|------|------------|
| PLATINUM | 0.005 | 0.5% |
| GOLD | 0.010 | 1.0% |
| SILVER | 0.015 | 1.5% |
| BRONZE | 0.020 | 2.0% |
| DEFAULT (unknown tier) | 0.020 | 2.0% |

**Formula:** `commission = orderValue * tierRate`

Where `orderValue = quantity * price`

**Commission Override via SpecialClientsRule:** Certain clients have hardcoded overrides set as context attributes that downstream processors may use:
- C002 Henderson: 0.0 (zero commission)
- C003 Smith: 0.01 (1%)
- C005 Pinnacle: 0.01 (1%)
- C006 Global Macro: 0.0 (zero commission)
- C008 Falcon: 0.005 (0.5%)
- C010 Sterling: 0.0 (zero commission)

**Volume Discount (VolumeDiscountRule):**
- Orders > 10,000 shares: 50% discount on commission
- Orders > 5,000 shares: 25% discount on commission
- BASE_COMMISSION in VolumeDiscountRule is 0.02 (copy-pasted from CommissionCalculator, TODO JIRA-6001 to fix)

**Loyalty Bonus (LoyaltyBonusRule):**
- Clients C001, C002, C003: additional 10% discount on commission

### 5.2 Pricing Spreads

**Source:** `pricing-service/src/com/bigcorp/pricing/service/PricingServiceImpl.java`

| Tier | Spread | Percentage |
|------|--------|------------|
| PLATINUM | 0.001 | 0.1% |
| GOLD | 0.002 | 0.2% |
| SILVER | 0.003 | 0.3% |
| BRONZE | 0.005 | 0.5% |
| DEFAULT | 0.005 | 0.5% |

**Formula:** `bidPrice = midPrice * (1.0 - spread)`, `askPrice = midPrice * (1.0 + spread)`

**Pricing Service Commission Rate Discrepancy:**
The pricing service uses `COMMISSION_RATE = 0.015` (1.5%) internally, which differs from the order engine's tier-based rates. This is documented as "by design" -- "business wanted different rates in different systems. Or maybe it IS a bug and nobody noticed."

### 5.3 FX Conversion Rates

#### MultiCurrencyRule rates (common-lib)

**Source:** `common-lib/src/com/bigcorp/common/rules/impl/MultiCurrencyRule.java`

| Pair | Rate (vs USD) | Note |
|------|---------------|------|
| EUR/USD | 1.10 | "Current as of 2014-03" |
| GBP/USD | 1.55 | |
| JPY/USD | 0.009 | |
| CHF/USD | 0.72 | |
| USD/USD | 1.0 | Default (no conversion) |

#### FxPricingHelper rates (derivatives-engine)

**Source:** `derivatives-engine/src/com/bigcorp/derivatives/core/FxPricingHelper.java`

| Pair | Rate (vs USD) | Note |
|------|---------------|------|
| EUR/USD | 1.10 | "Current as of 2004-07-15" |
| GBP/USD | 1.55 | |
| JPY/USD | 0.009 | |
| CHF/USD | 0.72 | |
| AUD/USD | 0.68 | Additional pair (not in MultiCurrencyRule) |
| USD/USD | 1.0 | |

FX Spread: `FX_SPREAD = 0.002` (0.2%)
- Bid = mid * (1 - spread/2)
- Ask = mid * (1 + spread/2)

### 5.4 Hardcoded Fallback Prices

**Source:** `pricing-service/src/com/bigcorp/pricing/service/PricingServiceImpl.java` (added 2000-03-15 during the "Great Database Outage")

| Symbol | Bid | Ask | Last |
|--------|-----|-----|------|
| MSFT | 25.00 | 25.50 | 25.25 |
| IBM | 119.00 | 120.00 | 119.50 |
| ORCL | 15.00 | 15.50 | 15.25 |
| SUNW | 8.50 | 9.00 | 8.75 |
| CSCO | 21.50 | 22.00 | 21.75 |
| INTC | 30.00 | 30.50 | 30.25 |
| DELL | 34.50 | 35.00 | 34.75 |
| Default | 10.00 | 10.50 | 10.25 |

### 5.5 Risk Metrics

**Source:** `risk-engine/src/com/bigcorp/risk/engine/ExposureCalculator.java`

**Volatility Assumptions (hardcoded):**

| Asset Class | Annual Vol | Classification Method |
|-------------|------------|----------------------|
| Equity | 20% | Default for all non-FX, non-commodity symbols |
| FX | 8% | Symbol contains `/` |
| Commodity | 30% | Symbol is GOLD, OIL, or SILVER |
| Default | 25% | Unknown symbols |

**VaR Parameters:**
- Confidence level: 99% (z-score = 2.33)
- Holding period: 1 trading day
- Trading days per year: 252
- Formula: `VaR = |notional| * vol * 2.33 * sqrt(1/252)`
- Flag threshold: VaR > $50,000 -> status `FLAGGED`

### 5.6 Price Deviation Limit

**Source:** `order-engine/src/com/bigcorp/orderengine/consumer/OrderMessageListener.java`

`MAX_PRICE_DEVIATION = 0.10` (10%): If `abs(quotedPrice - requestedPrice) / requestedPrice > 10%`, order is rejected.

---

## 6. Integration Architecture

### 6.1 JMS Queues (ActiveMQ)

**Source:** `common-lib/src/com/bigcorp/common/mq/MessageQueueHelper.java`

| Queue Name | Purpose | Producer(s) | Consumer(s) |
|------------|---------|-------------|-------------|
| `BIGCORP.TRADE.ORDERS` | Incoming trade orders | TradeMessageProducer (trade-desk) | OrderMessageListener (order-engine) |
| `BIGCORP.TRADE.CONFIRMATIONS` | Order status updates | OrderMessageListener (order-engine) | AuditListener (audit-service) |
| `BIGCORP.NOTIFICATIONS` | Notification dispatch | OrderMessageListener, BatchProcessor | NotificationListener (notification-gateway) |
| `BIGCORP.SETTLEMENT.EVENTS` | *Unused* -- "created for a cancelled project, removing breaks settlement" | (none known) | (none known) |
| `RISK.ORDERS.INBOUND` | Risk assessment requests | (TBD) | Risk engine |
| `RISK.RESULTS.OUTBOUND` | Risk assessment results | Risk engine | (TBD) |

**Broker Configuration:**
- Dev: Embedded ActiveMQ `vm://localhost?broker.persistent=false`
- Production: IBM MQSeries (referenced in comments, never migrated)
- Config file: `mq.properties` on classpath

**Message Format:** XML marshalled via `XmlHelper.marshalTradeOrder()` / `marshalNotification()`

**Poll Intervals:** All consumers use 5000ms poll timeout.

### 6.2 SOAP Web Service (Pricing)

**Source:** `order-engine/src/com/bigcorp/orderengine/soap/PricingServiceClient.java`

| Property | Value |
|----------|-------|
| Endpoint URL | `http://localhost:8080/pricing-service/services/PricingService` |
| SOAP Action | `getQuote` |
| SOAP Namespace | `http://bigcorp.com/pricing` |
| Connect Timeout | 10,000ms (increased from 5,000 per JIRA-2089) |
| Read Timeout | 15,000ms |
| Response Parsing | Manual indexOf/substring on `<price>` tag |
| Namespace Handling | Tries `<price>`, `<ns1:price>`, `<ns2:price>` (JIRA-2201 WebLogic 8.1) |
| Fallback | Direct DB query on PRICING_CACHE table (ASK_PRICE used for all orders) |
| Technology | Hand-crafted SOAP envelope (Axis stubs abandoned due to WebLogic classpath conflicts) |

**SQL Injection Vulnerability:** The DB fallback in `PricingServiceClient.getQuoteFromDatabase()` uses string concatenation: `"SELECT ASK_PRICE FROM PRICING_CACHE WHERE SYMBOL = '" + symbol + "'"` -- not parameterized.

### 6.3 SFTP (Settlement File Transfer)

**Source:** `settlement-gateway/src/com/bigcorp/settlement/sftp/SftpUploader.java` and `SftpPoller.java`

| Property | Value |
|----------|-------|
| Library | JSch |
| Config File | `settlement.properties` |
| Upload Dir (remote) | `/incoming/` (clearinghouse) |
| Download Dir (remote) | `/outgoing/` (clearinghouse) |
| Local Fallback (upload) | `./sftp-root/outbound/` |
| Local Fallback (download) | `./sftp-root/inbound/` |
| Processed Files Dir | `./sftp-root/processed/` |
| Connect Timeout | 30,000ms |
| Max Upload Retries | 1 (then falls back to local) |
| Host Key Checking | Disabled (`StrictHostKeyChecking=no`) |
| Server Alive Interval | 15,000ms |
| File Types | `.xml` (new/old format), `.dat` (COBOL fixed-width) |

### 6.4 SMTP (Email Notifications)

**Source:** `notification-gateway/src/com/bigcorp/notifications/email/EmailDispatcher.java`

| Property | Value |
|----------|-------|
| Config File | `notification.properties` |
| Default From | `noreply@bigcorp.com` |
| Default Port | 25 |
| Auth | None (internal relay) |
| HTML | Enabled by default, inline CSS for Outlook 2000 |
| Dev Mode | Logs to console instead of sending |
| Retry | Handled by NotificationListener (max 3 retries) |
| Known Issue | SMTP relay unreliable 5:00-5:30 PM EST |

### 6.5 Database

**Source:** `common-lib/src/com/bigcorp/common/db/ConnectionHelper.java` and `DatabaseBootstrap.java`

| Property | Value |
|----------|-------|
| Dev/Demo | HSQLDB in-memory (`jdbc:hsqldb:mem:bigcorpdb`) |
| Production | Oracle (with NLS_DATE_FORMAT session hack) |
| Config File | `db.properties` |
| Connection Pooling | None ("we'll add when we need it") |
| Schema Management | `DatabaseBootstrap.java` for HSQLDB; manual DBA script for Oracle |

**Tables:**

| Table | Purpose | Added |
|-------|---------|-------|
| CLIENTS | Client master data | Original |
| TRADE_ORDERS | Order records | Original |
| NOTIFICATIONS | Notification records | Original |
| SETTLEMENT_RECORDS | Settlement lifecycle | v1.1 |
| AUDIT_LOG | Audit trail | v2.1 |
| BILLING_LEDGER | Commission charges | v2.1 |
| RULE_AUDIT_LOG | Rule decision audit trail (REG-2011-003) | 2011 Q4 |
| DAILY_VOLUME_TRACKER | Regulatory volume tracking (REG-2005-001) | 2005 |
| PRICING_CACHE | Price quotes | Original |
| SURVEILLANCE_AUDIT_LOG | Surveillance rule audit (REG-2015-001+) | 2015 |
| POSITION_TRACKING | Net position per client/symbol | 2015 |
| RISK_ASSESSMENTS | Risk calculation results | 2017 |
| REG_REPORT_LOG | Regulatory report generation log (REG-2021-001) | 2021 |

### 6.6 Web UI (Servlet/JSP)

**Source:** `trade-desk/src/com/bigcorp/tradedesk/servlet/OrderEntryServlet.java`

- URL pattern: `/trade-desk/order/entry` (GET for form, POST for submission)
- Order status: `/trade-desk/order/status`
- Architecture: Front Controller pattern (`FrontControllerServlet`) with Command objects (`SubmitOrderCommand`, `ViewOrderStatusCommand`, `ListOrdersCommand`)
- `ClientPortalAPI` (separate API endpoint)
- Inline HTML with 1990s-era styling (TABLE-based layout, `<FONT>` tags, Win95 `outset/inset` border styling)
- IE6 cache workaround (JIRA-1287): `Cache-Control: no-cache` + `Pragma: no-cache`

---

## 7. Anomalies

### 7.1 Known Bugs

| ID | Description | Location | Severity |
|----|-------------|----------|----------|
| JIRA-5300 | Rule engine priority ordering is backwards (descending instead of ascending). System property `bigcorp.rules.priority.fixed` enables correct behavior. | `RuleEngine.java:83-102` | Medium |
| JIRA-2890 | T+3 settlement date calculation does NOT skip weekends or holidays. "We'll fix it when we have time." Clearinghouse recalculates anyway. | `BatchProcessor.java:168-194` | Low |
| JIRA-2456 | Price deviation is checked both by rule engine AND manually in `OrderMessageListener.processOrder()`. Redundant "belt and suspenders." | `OrderMessageListener.java:65-67, 262-273` | Low |
| REG-2005-003 | Manual volume limit check (50,000 shares) duplicates `DailyVolumeLimitRule`. Compliance insists on keeping both. | `OrderMessageListener.java:69-71, 241-251` | Low |
| (undocumented) | Inactive rules may still execute. RuleEngine checks `isActive()` but the behavior is unreliable. | `RuleEngine.java:134-148` | Medium |
| (undocumented) | XML config `active` attribute is read but NOT applied to Rule objects. Each Rule returns its own `isActive()` regardless. | `config/rules.xml` comment, `RuleConfigLoader` | Medium |
| (undocumented) | `PricingServiceClient.getQuoteFromDatabase()` is vulnerable to SQL injection (string concatenation with symbol parameter). | `PricingServiceClient.java:207` | High |

### 7.2 Dead Code

| Item | Location | Note |
|------|----------|------|
| `QUEUE_SETTLEMENT_EVENTS` | `MessageQueueHelper.java:36` | Created for cancelled project; removing "breaks something in settlement" |
| `partialBatchMode` / `partialBatchSize` | `BatchProcessor.java:48-49` | Requested in JIRA-2890, never implemented |
| Old record separator constants | `SettlementFileGenerator.java:46-47` | Commented out, old format |
| `STATUS_PENDING_REVIEW` | `TradeOrder.java:28` | Defined but never set in main processing path |
| `STATUS_CANCELLED` | `TradeOrder.java:29` | Defined but no cancellation flow implemented |
| `STATUS_VALIDATED`, `STATUS_PRICED` | `TradeOrder.java:22-23` | Intermediate statuses defined but skipped in `processOrder()` |
| `netAmount` calculation | `OrderMessageListener.java:278` | Computed but commented out ("not used yet - for settlement") |
| FAX channel handling | `NotificationListener.java:122-127` | Deprecated 2002, still in code path |

### 7.3 Duplicated Logic

| What | Location 1 | Location 2 | Notes |
|------|-----------|-----------|-------|
| Commission rate | `CommissionCalculator` (tier-based) | `PricingServiceImpl` (flat 0.015) | Different rates "by design" |
| Commission base rate | `CommissionCalculator` (0.02 default) | `VolumeDiscountRule` (0.02 BASE_COMMISSION) | Copy-pasted, TODO JIRA-6001 |
| Volume limit check | `DailyVolumeLimitRule` (50,000) | `OrderMessageListener` (50,000 manual) | REG-2005-003 |
| Price deviation check | Rule engine | `OrderMessageListener` (10% manual) | JIRA-2456 |
| Client tier spread logic | `ClientTierRule` | `PricingServiceImpl.applyTierSpread()` | Business wanted both |
| FX rates | `MultiCurrencyRule` | `FxPricingHelper` (derivatives) | Copy-pasted with minor differences (AUD/USD only in derivatives) |
| `lookupClientTier()` method | `BatchProcessor.java:225-246` | `AuditListener.java:167-188` | Identical JDBC code in both files |
| `moveToProcessed()` method | `ReconciliationProcessor.java:219-241` | `SftpPoller.java:310-323` | Duplicate, refactoring on backlog JIRA-3525 |
| Order save | `orderDAO.saveOrder()` | `orderDAO.updateOrderStatus()` | Both called for same order in `processOrder()` |

### 7.4 JIRA References Found in Source

| JIRA ID | Description | Status |
|---------|-------------|--------|
| JIRA-892 | Add tier spread logic to pricing | Implemented |
| JIRA-1102 | Unknown symbol caused pricing blowup | Fixed (default fallback price added) |
| JIRA-1287 | IE6 caching issue on order form | Fixed (no-cache headers) |
| JIRA-1847 | MQ integration for PriceQuote | Not implemented |
| JIRA-1892 | Henderson complaint about max order value | Fixed (10% buffer added) |
| JIRA-2045 | Add proper form validation | Not implemented |
| JIRA-2089 | SOAP connect timeout too short | Fixed (increased to 10s) |
| JIRA-2201 | WebLogic 8.1 namespace prefix | Fixed (ns2 prefix support) |
| JIRA-2340 | CPU usage from 1s poll interval | Fixed (increased to 5s) |
| JIRA-2456 | Redundant price deviation check | Acknowledged, not removed |
| JIRA-2501 | Commission rate hardcoded | Fixed (centralized in CommissionCalculator) |
| JIRA-2890 | Partial batch mode / weekend skip | Not implemented |
| JIRA-3200 | FX rates table creation | Not implemented (DBA backlog) |
| JIRA-3455 | DAT reconciliation parsing | Partially implemented (DatReconciliationParser added) |
| JIRA-3501 | Store externalRef from reconciliation | Not implemented |
| JIRA-3520 | Proper XML reconciliation counting | Not implemented |
| JIRA-3522 | Store reason code for rejections | Not implemented (model lacks field) |
| JIRA-3525 | Refactor duplicate moveToProcessed | Not implemented |
| JIRA-4101 | Add ShortSaleRule to XML config | Not implemented (added programmatically) |
| JIRA-5100 | Write manual volume warning to AUDIT_LOG | Not implemented |
| JIRA-5300 | Fix rule priority ordering | Implemented (system property toggle) |
| JIRA-6001 | Fix copy-pasted BASE_COMMISSION in VolumeDiscountRule | Not implemented |
| JIRA-7100 | Multi-currency support | Implemented (MultiCurrencyRule) |

### 7.5 Regulatory References

| Reference | Description | Year | Impact |
|-----------|-------------|------|--------|
| REG-2005-001 | Volume manipulation incident | 2005 | DailyVolumeLimitRule, DAILY_VOLUME_TRACKER table |
| REG-2005-002 | Wash trade detection | 2005 | WashTradeDetectionRule |
| REG-2005-003 | Belt-and-suspenders volume check | 2005 | Manual volume check in OrderMessageListener |
| REG-2011-001 | SEC circuit breaker requirement | 2011 | MarketHaltRule |
| REG-2011-002 | Rogue algorithm incident | 2011 | ClientKillSwitchRule |
| REG-2011-003 | Rule audit trail requirement | 2011 | RuleAuditLogger, RULE_AUDIT_LOG table |
| REG-2015-001 | SEC inquiry on layering patterns | 2015 | LayeringDetectionRule, SURVEILLANCE_AUDIT_LOG |
| REG-2015-002 | Spoofing detection | 2015 | SpoofingPatternRule |
| REG-2015-003 | Position limits | 2015 | PositionLimitRule, POSITION_TRACKING |
| REG-2021-001 | Regulatory reporting | 2021 | REG_REPORT_LOG table |

### 7.6 Architecture Smells

1. **No connection pooling** -- raw `DriverManager.getConnection()` every time
2. **Singleton RuleEngine** with mutable state and test-only `reset()` hack
3. **Hardcoded client IDs** in SpecialClientsRule and LoyaltyBonusRule
4. **Hardcoded FX rates** in two locations, both acknowledged as stale
5. **Hardcoded fallback prices** from 2000-2001 still in production path
6. **No transaction boundaries** -- partial batch failures leave inconsistent state
7. **Three separate order models** (TradeOrder, DerivativeOrder, RiskOrder) that don't share a type hierarchy
8. **XML marshalling via indexOf/substring** instead of proper XML parsing (PricingServiceClient SOAP response)
9. **SQL injection vulnerability** in PricingServiceClient DB fallback
10. **Notification INSERT-only** -- "easier to just insert a new row" (acknowledged bad decision)

---

## 8. Confidence Assessment

| Section | Confidence | Justification |
|---------|-----------|---------------|
| **1. System Purpose** | HIGH | Confirmed from multiple source files, code comments, and Graphify graph structure. Module purposes are clearly documented in javadoc. |
| **2. Domain Model** | HIGH | All fields, types, and constants extracted directly from Java source code and DDL in `DatabaseBootstrap.java`. Database schema verified from `CREATE TABLE` statements. |
| **3. Business Rules** | HIGH | All 17 rule implementations read in full. Priority numbers, thresholds, and exact constant values extracted from source. Behavior (reject vs flag) verified by reading `evaluate()` and `execute()` methods. Rules XML config cross-referenced. |
| **4. Business Processes** | HIGH | Complete order flow traced through source code: `OrderEntryServlet` -> JMS -> `OrderMessageListener` -> `BatchProcessor` -> `ReconciliationProcessor`. Each step verified in code with exact method calls and queue names. |
| **5. Financial Logic** | HIGH | All commission rates, spread values, and FX rates extracted as exact decimal constants from source code. Multiple commission calculation paths (CommissionCalculator, PricingServiceImpl, VolumeDiscountRule) documented with their discrepancies. |
| **6. Integration Architecture** | HIGH | All queue names, SOAP endpoints, SFTP configuration, and database details extracted from source code constants and configuration loading code. Dev vs production differences documented. |
| **7. Anomalies** | HIGH | All JIRA references collected from code comments. Dead code, duplicated logic, and known bugs identified by reading implementation details. SQL injection vulnerability identified by code inspection. |
| **8. Confidence Assessment** | N/A | Self-assessment based on completeness of source reading. |

### Limitations

- **Production configuration** (Oracle connection strings, MQSeries broker URLs, actual SFTP credentials, SMTP relay host) could not be verified -- only dev/demo defaults were in source.
- **RuleConfigLoader** implementation was not fully read -- trust that it uses reflection to instantiate rules from XML config.
- **derivatives-engine and risk-engine** have separate JMS queue infrastructure (`RISK.ORDERS.INBOUND/OUTBOUND`) whose integration with the main order flow is not fully wired in the source (likely connected via external configuration or a demo harness).
- **Graphify graph structure** (101 communities, 1393 nodes, 3613 edges) provided navigation guidance but the exact business logic values come from source code reading.
- **HTML email templates** (XML files in `com/bigcorp/notifications/templates/`) were not read -- only the template loading and substitution logic was analyzed.

---

*Generated from Graphify knowledge graph analysis (1393 nodes, 3613 edges, 101 communities) + direct Java source code reading of 60+ files across 11 modules.*
