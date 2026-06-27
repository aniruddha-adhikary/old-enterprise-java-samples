# Reconstructed Business Logic: BigCorp Trade Order Management System

> Reconstructed entirely from Joern CPG analysis and Java source code reading.
> No documentation files (README, CHANGELOG, docs/) were consulted.

---

## 1. System Purpose

**Domain:** Financial trade order management (equities brokerage / institutional trading).

**What it does:** Accepts trade orders (BUY/SELL) from institutional clients via JMS messaging and a web frontend, validates them against a configurable chain of business and regulatory rules, obtains pricing from an internal SOAP service, fills orders, sends notifications, and processes end-of-day settlement with a clearinghouse via SFTP file exchange.

**Who are the users:**
- Institutional trading clients (Acme Trading, Henderson Capital, Smith & Associates, MegaFund, Pinnacle Investments, Global Macro Fund, Velocity Trading, etc.)
- Internal compliance team (monitors surveillance flags, manages kill switches)
- Internal operations/settlements team (manages batch processing)
- Internal sales team (negotiates special client arrangements)

**Technology era:** Java 1.4 / J2EE (1999-2002 core, with incremental additions through 2021). Uses Servlets 2.3, JMS (ActiveMQ for dev, IBM MQSeries in prod), JDBC (HSQLDB for dev, Oracle in prod), SOAP (hand-crafted), JSP for web UI.

---

## 2. Domain Model

### Core Entities

#### TradeOrder
| Field | Type | Description |
|-------|------|-------------|
| orderId | VARCHAR(30) | Primary key |
| clientId | VARCHAR(20) | FK to CLIENTS |
| symbol | VARCHAR(10) | Stock ticker |
| quantity | INTEGER | Number of shares |
| side | VARCHAR(4) | "BUY" or "SELL" |
| price | DECIMAL(15,4) | Fill price (0 until filled) |
| requestedPrice | DECIMAL(15,4) | Client's requested price |
| status | VARCHAR(20) | Current lifecycle state |
| orderDate | TIMESTAMP | When submitted |
| lastModified | TIMESTAMP | Last state change |
| notes | VARCHAR(500) | Free text (sometimes contains XML) |
| surveillanceFlags | VARCHAR(200) | Comma-separated flags (added 2015) |

#### Client
| Field | Type | Description |
|-------|------|-------------|
| clientId | VARCHAR(20) | Primary key (e.g. "C001") |
| clientName | VARCHAR(100) | Display name |
| email | VARCHAR(100) | For notifications |
| phone | VARCHAR(20) | Contact number |
| tier | VARCHAR(10) | PLATINUM, GOLD, SILVER, BRONZE |
| maxOrderValue | DECIMAL(15,2) | Per-order limit (default 100,000) |
| active | INTEGER | 1=active, 0=inactive |
| kycStatus | VARCHAR(20) | APPROVED, PENDING, EXPIRED, REJECTED |
| killSwitch | VARCHAR(1) | "Y" = all orders rejected, "N" = normal |

#### SettlementRecord
| Field | Type | Description |
|-------|------|-------------|
| recordId | VARCHAR(30) | "SR-{timestamp}-{hash}" |
| orderId | VARCHAR(30) | Source order |
| clientId | VARCHAR(20) | Client reference |
| symbol | VARCHAR(10) | Ticker |
| quantity | INTEGER | Shares |
| side | VARCHAR(4) | BUY/SELL |
| amount | DECIMAL(15,4) | qty * price |
| commission | DECIMAL(10,4) | Calculated via CommissionCalculator |
| tradeDate | TIMESTAMP | Original order date |
| settlementDate | TIMESTAMP | T+3 calendar days (bug: doesn't skip weekends) |
| status | VARCHAR(15) | Lifecycle state |
| batchId | VARCHAR(30) | "BATCH-yyyyMMdd-NNN" |
| externalRef | VARCHAR(50) | Clearinghouse reference |

#### Notification
| Field | Type | Description |
|-------|------|-------------|
| notificationId | VARCHAR(30) | Unique ID |
| type | VARCHAR(20) | ORDER_CONFIRM, ORDER_REJECT, SETTLEMENT, PRICE_ALERT |
| recipient | VARCHAR(100) | Email address |
| subject | VARCHAR(200) | Email subject |
| body | VARCHAR(2000) | Pipe-delimited template data |
| channel | VARCHAR(10) | EMAIL, SMS, FAX |
| status | VARCHAR(10) | PENDING, SENT, FAILED |
| orderId | VARCHAR(30) | Related order |
| retryCount | INTEGER | Number of send attempts |

### State Machines

#### TradeOrder Status Transitions
```
NEW --> VALIDATED --> PRICED --> FILLED --> SETTLED
 |                                           |
 +---> REJECTED                              +--> (end)
 |
 +---> CANCELLED
 |
 +---> PENDING_REVIEW (defined but never used - JIRA-2341)
```

**Actual observed flow in code:**
- `NEW` -> (rule engine passes) -> `FILLED` (VALIDATED and PRICED states are defined but skipped in `OrderMessageListener.processOrder()` which goes directly NEW->FILLED)
- `NEW` -> (rule engine fails OR price deviation OR client not found) -> `REJECTED`
- `FILLED` -> (settlement batch) -> `SETTLED`
- `CANCELLED` is referenced in surveillance queries but no code path sets it

#### SettlementRecord Status Transitions
```
PENDING --> GENERATED --> UPLOADED --> CONFIRMED
                                  |-> FAILED
                                  |-> DISCREPANCY
                                  |-> RECONCILED
```

### Relationships
```
Client (1) ---< (N) TradeOrder
TradeOrder (1) ---< (1) SettlementRecord
TradeOrder (1) ---< (N) Notification
Client (1) ---< (N) SettlementRecord
```

---

## 3. Business Rules

Rules are defined in `config/rules.xml` and evaluated by the `RuleEngine` singleton. The engine sorts rules by priority in **DESCENDING** order (higher number = runs first). This is a known reversed-comparator bug preserved for backward compatibility. A system property `bigcorp.rules.priority.fixed` can switch to ascending (correct) order.

**Rule chain behavior:** First rule to fail stops evaluation; order is rejected. Rules that only flag/warn (surveillance) always return `true`.

### Execution Order (Descending Priority)

| Priority | Rule | Category |
|----------|------|----------|
| 125 | LayeringDetection | Surveillance (2015) |
| 124 | SpoofingPattern | Surveillance (2015) |
| 123 | PositionLimit | Surveillance (2015) |
| 120 | MarketHalt | Circuit Breaker (2011) |
| 118 | ClientKillSwitch | Circuit Breaker (2011) |
| 115 | KYCStatus | Compliance (2005) |
| 110 | DailyVolumeLimit | Compliance (2005) |
| 105 | WashTradeDetection | Compliance (2005) |
| 100 | MaxOrderValue | Business (original) |
| 95 | RestrictedSymbol | Business (2003) |
| 90 | ClientTier | Business (original) |
| 80 | MarketHours | Business (original) |
| 75 | ShortSale | Business (2003) - **NOT in rules.xml** |
| 60 | MultiCurrency | Business (2014) |
| 55 | VolumeDiscount | Business (2009) |
| 50 | SpecialClients | Business (original) |
| 45 | LoyaltyBonus | Business (2009) |

**Note:** ShortSaleRule is added programmatically AFTER XML-loaded rules (`JIRA-4101` — "will add to XML config later"). This means it always runs regardless of XML config state.

### Detailed Rule Specifications

#### MarketHalt (priority 120)
- **Checks:** System property `bigcorp.market.halted`
- **Pass:** Property is absent, null, or "false"
- **Fail:** Property equals "true" (case-insensitive)
- **Effect on fail:** REJECTS order with "MARKET HALTED - trading suspended (REG-2011-001)"
- **Regulatory ref:** REG-2011-001 (SEC circuit breaker after flash crash)
- **No exceptions:** No overrides for any client

#### ClientKillSwitch (priority 118)
- **Checks:** CLIENTS.KILL_SWITCH column for the order's client
- **Pass:** Column is NULL, absent, or "N"
- **Fail:** Column equals "Y" (case-insensitive, trimmed)
- **Effect on fail:** REJECTS with "Client trading suspended - kill switch active (REG-2011-002)"
- **Regulatory ref:** REG-2011-002
- **Fail-open:** If DB lookup fails, defaults to "N" (allow)

#### LayeringDetection (priority 125)
- **Checks:** Count of non-CANCELLED orders in TRADE_ORDERS for same client+symbol
- **Threshold:** > 5 orders = suspicious
- **Pass:** Always passes (never rejects). Flags only.
- **Effect when flagged:** Sets `surveillance_flags` attribute to include "LAYERING", logs warning
- **Fail-open:** DB errors allow the trade

#### SpoofingPattern (priority 124)
- **Checks:** Ratio of CANCELLED orders to total orders for the client
- **Threshold:** > 60% cancellation rate = suspicious
- **Pass:** Always passes (never rejects). Flags only.
- **Effect when flagged:** Sets `surveillance_flags` to include "SPOOFING"
- **Fail-open:** DB errors allow the trade

#### PositionLimit (priority 123)
- **Checks:** NET_POSITION in POSITION_TRACKING table + proposed order quantity vs limit
- **Threshold:** |new_position| > 100,000 shares per client per symbol
- **Pass:** Position within limit or no position record exists (for orders < limit)
- **Fail:** REJECTS with "Position limit breach (REG-2015-003)"
- **BUY:** adds to position; **SELL:** subtracts from position
- **Fail-open:** If DB unavailable, allows the trade

#### KYCStatus (priority 115)
- **Checks:** CLIENTS.KYC_STATUS column
- **Pass:** Status equals "APPROVED"
- **Fail:** REJECTS if status is "PENDING", "EXPIRED", "REJECTED", or NULL (treated as PENDING)
- **Regulatory ref:** Post-2005 regulatory review

#### DailyVolumeLimit (priority 110)
- **Checks:** Single order quantity vs per-order limit
- **Threshold:** 50,000 shares per order
- **Pass:** quantity <= 50,000
- **Fail:** REJECTS with "Daily volume limit exceeded (REG-2005-001)"
- **Note:** Name is misleading - checks per-ORDER, not cumulative daily volume (original design simplified from cumulative check)

#### WashTradeDetection (priority 105)
- **Checks:** TRADE_ORDERS for same client + same symbol + OPPOSITE side within 5 minutes
- **Window:** 5 minutes (WASH_TRADE_WINDOW_MINUTES)
- **Pass:** No matching opposite-side order found in window
- **Fail:** REJECTS with "Potential wash trade detected (REG-2005-002)"
- **Excludes:** REJECTED orders from the check

#### MaxOrderValue (priority 100)
- **Checks:** order.quantity * order.requestedPrice vs client.maxOrderValue * 1.10
- **Buffer:** 10% over max allowed (BUFFER_MULTIPLIER = 1.10, added for Henderson complaint JIRA-1892)
- **Pass:** Order value within buffered limit
- **Fail:** REJECTS with "Order value X exceeds max allowed Y"
- **Per-client limits (from sample data):**
  - C001 Acme: $500,000
  - C002 Henderson: $5,000,000
  - C003 Smith: $250,000
  - C004 MegaFund: $1,000,000
  - C005 Pinnacle: $100,000
  - C006 Global Macro: $10,000,000
  - C007 Velocity: $2,000,000

#### RestrictedSymbol (priority 95)
- **Checks:** Order symbol against hardcoded restricted list
- **Restricted symbols:** `ENRN`, `WCOM`, `TYCO`, `ADLP`
- **Pass:** Symbol not in list
- **Fail:** REJECTS with "Restricted symbol: X - trading suspended"
- **Note:** These are tickers of companies involved in major fraud (Enron, WorldCom, Tyco, Adelphia)

#### ClientTier (priority 90)
- **Checks:** Client active status
- **Pass:** Client is active (or null client - warns but passes)
- **Fail:** REJECTS if client.isActive() is false
- **Execute (on pass):** Sets priority attribute - PLATINUM/GOLD get "HIGH", others get "NORMAL"

#### MarketHours (priority 80)
- **Checks:** Server local time against market hours
- **Market hours:** 9:30 AM - 4:00 PM (server local time, NOT Eastern)
- **Weekend:** Warns but PASSES (queues order for Monday)
- **Outside hours:** Warns but PASSES (queues order)
- **Never rejects** - only flags/warns. Originally rejected but sales complained.

#### ShortSale (priority 75)
- **Checks:** SELL orders with quantity > 1,000 shares
- **Threshold:** 1,000 shares
- **Applies to:** SELL orders only; BUY orders always pass
- **Pass:** SELL <= 1,000 shares, or any BUY
- **Fail:** REJECTS with "Short sale limit exceeded"
- **Execute (on pass):** Calculates commission using CommissionCalculator tier rate, stores in context

#### MultiCurrency (priority 60)
- **Checks:** Context attribute "currency"
- **Pass:** Always passes (never rejects)
- **Behavior:** If non-USD currency, looks up FX rate and stores in context
- **FX Rates (hardcoded, stale from 2014):**
  - EUR/USD: 1.10
  - GBP/USD: 1.55
  - JPY/USD: 0.009
  - CHF/USD: 0.72
- **Unknown currency:** Defaults to USD, logs warning
- **Execute:** No-op (actual conversion happens downstream)

#### VolumeDiscount (priority 55)
- **Checks:** Order quantity for commission discount eligibility
- **Pass:** Always passes (adjusts commission only)
- **Thresholds:**
  - quantity > 10,000 shares: 50% off commission
  - quantity > 5,000 shares: 25% off commission
- **Execute:** Stores `volume_discount` attribute (0.50 or 0.25) in context

#### SpecialClients (priority 50)
- **Checks:** Client ID against hardcoded list
- **Pass:** Always passes (adjusts attributes only)
- **Special arrangements:**
  - **C001** (Acme Trading): Early market access (can trade 10 min before open)
  - **C002** (Henderson Capital): Zero commission override
  - **C003** (Smith & Associates): Commission override to 1.0% (GOLD rate)
  - **C004** (MegaFund): PLATINUM pricing tier override (despite being GOLD tier)
  - **C005** (Pinnacle): Commission override to 1.0% (50% off BRONZE rate)
  - **C006** (Global Macro Fund): Zero commission + early market access
  - **C007** (Velocity Trading): PLATINUM pricing tier override
  - **C008** (Falcon Trading): Commission override to 0.5% (75% discount)
  - **C009** (Apex Capital): GOLD pricing tier override (on SILVER tier)
  - **C010** (Sterling Investments): Zero commission + multi-currency priority

#### LoyaltyBonus (priority 45)
- **Checks:** Client ID against hardcoded "long-standing" list
- **Pass:** Always passes
- **Eligible clients:** C001, C002, C003 (active since 1999-2000)
- **Effect:** Stores `loyalty_bonus` attribute = 0.10 (10% additional discount)
- **Note:** Tenure should come from DB but is hardcoded (JIRA-6002)

### Additional Manual Checks in OrderMessageListener

Beyond the rule engine, `OrderMessageListener` performs two redundant "belt and suspenders" checks:

1. **Price Deviation Check (MAX_PRICE_DEVIATION = 0.10)**
   - If `|quotedPrice - requestedPrice| / requestedPrice > 10%`, order is rejected
   - "Price deviation exceeds 10% limit"
   - Duplicates unnamed rule engine check (JIRA-2456)

2. **Manual Volume Warning (MANUAL_VOLUME_LIMIT = 50,000)**
   - If quantity > 50,000, logs compliance warning
   - Does NOT reject (the rule engine DailyVolumeLimitRule handles rejection)
   - REG-2005-003

---

## 4. Business Processes

### Order Lifecycle (Complete Flow)

```
[Client] --XML/JMS--> [BIGCORP.TRADE.ORDERS queue]
                              |
                    OrderMessageListener.processOrder()
                              |
                    1. Unmarshal XML to TradeOrder
                    2. Lookup Client from DB (OrderDAO.findClient)
                       - If not found: REJECT
                       - If inactive: REJECT
                    3. Run Rule Engine (16 rules in priority order)
                       - If any hard rule fails: REJECT
                    4. Manual volume warning check (logging only)
                    5. Get price quote (SOAP -> DB fallback)
                       - If price unavailable: REJECT
                    6. Manual price deviation check
                       - If > 10% deviation: REJECT
                    7. Fill Order:
                       - totalValue = quantity * quotedPrice
                       - commission = CommissionCalculator.calculate(totalValue, client)
                       - order.status = FILLED
                       - Save to DB (twice: saveOrder + updateOrderStatus)
                    8. Send Confirmation Notification -> BIGCORP.NOTIFICATIONS
                    9. Send Status Update -> BIGCORP.TRADE.CONFIRMATIONS
```

### Settlement Process (End-of-Day Batch)

```
BatchProcessor.processBatch() [runs at 6 PM EST via cron]
    |
    1. Query all FILLED orders (SettlementDAO.findFilledOrders)
    2. Generate batch ID: "BATCH-yyyyMMdd-NNN"
    3. For each order:
       a. Create SettlementRecord:
          - recordId = "SR-{timestamp}-{orderIdHash}"
          - amount = quantity * price
          - commission = CommissionCalculator.calculate(amount, clientTier)
          - settlementDate = tradeDate + 3 calendar days (T+3, no weekend skip)
          - status = PENDING
       b. Save to DB
       c. Update order status to SETTLED
    4. Generate settlement files:
       a. XML file: SETTLE_{batchId}.xml
       b. Flat file: SETTLE_{batchId}.dat (COBOL fixed-width format)
    5. Upload files to clearinghouse via SFTP
    6. Update record status to UPLOADED
    7. Send settlement notifications to BIGCORP.NOTIFICATIONS queue
```

### Reconciliation Process

```
ReconciliationProcessor.processInbound()
    |
    1. SftpPoller downloads from clearinghouse /outgoing/ directory
       (or reads from ./sftp-root/inbound/ in dev mode)
    2. Identify file type: .xml or .dat
    3. Parse reconciliation entries:
       - CONF -> CONFIRMED
       - REJC -> FAILED
       - DISC -> DISCREPANCY
       - PEND -> stays PENDING
    4. Update SettlementRecord statuses via SettlementDAO
    5. Move processed files to ./sftp-root/processed/
```

### Notification Process

```
NotificationListener (consumes BIGCORP.NOTIFICATIONS queue)
    |
    1. Receive notification XML message
    2. Unmarshal to Notification object
    3. Route by channel:
       - EMAIL: EmailDispatcher.sendEmail()
       - SMS: (stub/reference to sms.gateway.apikey)
       - FAX: (stub)
    4. On failure: retry (retry count tracked)
    
EmailDispatcher:
    - SMTP config from notification.properties
    - Dev mode: just logs (no actual send)
    - HTML emails for trade confirmations (Outlook 2000 compatible)
    - From: noreply@bigcorp.com
    - Body format: pipe-delimited template substitution
      Format: symbol|quantity|side|price|reason|amount|settlementDate
```

### Pricing Flow

```
OrderMessageListener needs price:
    |
    1. PricingServiceClient.getQuote(symbol)
       a. Try SOAP call to http://localhost:8080/pricing-service/services/PricingService
          - Hand-crafted SOAP envelope
          - Timeout: connect=10s, read=15s
       b. If SOAP fails: query PRICING_CACHE table directly (DB fallback)
       c. Returns ASK price (always uses ASK for BUY orders)
    |
    2. PricingServiceImpl (server side):
       a. lookupFromDatabase(symbol) - queries PRICING_CACHE table
       b. If DB fails: getHardcodedQuote(symbol) - fallback prices
       c. applyTierSpread(quote, clientTier) - adjusts bid/ask
```

---

## 5. Financial Logic

### Commission Calculation

**Central calculator:** `CommissionCalculator` (common-lib)

| Tier | Rate | Percentage |
|------|------|-----------|
| PLATINUM | 0.005 | 0.5% |
| GOLD | 0.010 | 1.0% |
| SILVER | 0.015 | 1.5% |
| BRONZE | 0.020 | 2.0% |
| DEFAULT (null tier) | 0.020 | 2.0% |

**Formula:** `commission = orderValue * tierRate`
where `orderValue = quantity * price`

### Commission Discrepancy: PricingService vs Order Engine

The PricingServiceImpl uses a DIFFERENT commission rate: **0.015 (1.5%)** — hardcoded independently from CommissionCalculator. This is documented as either "by design" or a bug that nobody wants to fix. The pricing service rate is used for price quote calculations only, not for actual order billing.

### Volume Discounts (applied after base commission)

| Condition | Discount |
|-----------|----------|
| quantity > 10,000 shares | 50% off commission |
| quantity > 5,000 shares | 25% off commission |

**Note:** VolumeDiscountRule uses a hardcoded base rate of 0.02 (copy-pasted, JIRA-6001) rather than calling CommissionCalculator. The discount is stored as a context attribute but it's unclear if downstream code actually applies it to the final commission calculation.

### Special Client Overrides (bypass normal rates)

| Client | Override |
|--------|----------|
| C002 Henderson Capital | 0% commission |
| C003 Smith & Associates | 1.0% commission |
| C005 Pinnacle Investments | 1.0% commission |
| C006 Global Macro Fund | 0% commission |
| C008 Falcon Trading | 0.5% commission |
| C010 Sterling Investments | 0% commission |

### Loyalty Bonus

Clients C001, C002, C003 receive a 10% additional discount on top of their commission (stored as context attribute `loyalty_bonus = 0.10`).

### Pricing Spreads (PricingServiceImpl)

Applied to mid-price to calculate bid/ask:

| Tier | Spread | Bid Formula | Ask Formula |
|------|--------|-------------|-------------|
| PLATINUM | 0.1% | mid * 0.999 | mid * 1.001 |
| GOLD | 0.2% | mid * 0.998 | mid * 1.002 |
| SILVER | 0.3% | mid * 0.997 | mid * 1.003 |
| BRONZE | 0.5% | mid * 0.995 | mid * 1.005 |
| DEFAULT | 0.5% | mid * 0.995 | mid * 1.005 |

### Hardcoded Fallback Prices (stale since 2000-2001)

| Symbol | Bid | Ask | Last |
|--------|-----|-----|------|
| MSFT | 25.00 | 25.50 | 25.25 |
| IBM | 119.00 | 120.00 | 119.50 |
| ORCL | 15.00 | 15.50 | 15.25 |
| SUNW | 8.50 | 9.00 | 8.75 |
| CSCO | 21.50 | 22.00 | 21.75 |
| INTC | 30.00 | 30.50 | 30.25 |
| DELL | 34.50 | 35.00 | 34.75 |
| Unknown | 10.00 | 10.50 | 10.25 |

### Settlement Calculation

```
amount = quantity * fillPrice
commission = CommissionCalculator.calculate(amount, clientTier)
settlementDate = tradeDate + 3 calendar days  // T+3, BUG: no weekend/holiday skip
```

### Price Deviation Threshold

Orders rejected if: `|quotedPrice - requestedPrice| / requestedPrice > 0.10 (10%)`

### FX Conversion Rates (stale, hardcoded from 2014)

| Currency Pair | Rate |
|---------------|------|
| EUR/USD | 1.10 |
| GBP/USD | 1.55 |
| JPY/USD | 0.009 |
| CHF/USD | 0.72 |

### Risk Engine (VaR Calculation)

```
notional = quantity * price
exposure = notional (BUY) or -notional (SELL)
VaR = |notional| * volatility * 2.33 * sqrt(1/252)
```

Volatility assumptions:
- Equity: 20% annual
- FX (contains "/"): 8% annual
- Commodity (GOLD, OIL, SILVER): 30% annual
- Default: 25% annual

VaR flag threshold: > $50,000 -> RISK_STATUS_FLAGGED

### Billing Ledger (BILLING_LEDGER table)

```
GROSS_AMOUNT = order value
COMMISSION_AMOUNT = calculated commission
NET_AMOUNT = gross + commission (or gross - commission, unclear from schema alone)
STATUS: default "CHARGED"
```

---

## 6. Integration Architecture

### JMS Queues

| Queue Name | Producer(s) | Consumer(s) | Purpose |
|------------|-------------|-------------|---------|
| `BIGCORP.TRADE.ORDERS` | OrderEntryServlet, FrontControllerServlet | OrderMessageListener | Incoming trade orders |
| `BIGCORP.TRADE.CONFIRMATIONS` | OrderMessageListener | Settlement, Audit systems | Filled/rejected order status |
| `BIGCORP.NOTIFICATIONS` | OrderMessageListener, BatchProcessor | NotificationListener | All notification types |
| `BIGCORP.SETTLEMENT.EVENTS` | (defined but unclear producer) | (unknown) | "Created for cancelled project; removing breaks settlement" |
| `BIGCORP.DERIVATIVES.ORDERS` | Derivatives frontend | DerivativeProcessor | Derivative trade orders |
| `BIGCORP.DERIVATIVES.CONFIRMS` | DerivativeProcessor | (consumers) | Derivative confirmations |
| `BIGCORP.DERIVATIVES.PRICING` | (pricing) | (derivative pricing) | Derivative pricing requests |
| `RISK.ORDERS.INBOUND` | (order flow) | Risk engine | Orders for risk assessment |
| `RISK.RESULTS.OUTBOUND` | Risk engine | (consumers) | Risk calculation results |
| `BIGCORP_REG_REPORT` | Regulatory reporting | (consumers) | Regulatory report events |

**Broker:** Embedded Apache ActiveMQ for development; IBM MQSeries in production.

### SOAP Endpoints

| Endpoint | URL | Method | Description |
|----------|-----|--------|-------------|
| PricingService | `http://localhost:8080/pricing-service/services/PricingService` | getQuote | Returns price quotes for symbols |

- Hand-crafted SOAP XML (Axis stubs abandoned due to classpath conflicts)
- SOAPAction header: "getQuote"
- Connection timeout: 10,000 ms
- Read timeout: 15,000 ms
- Fallback on failure: direct DB query to PRICING_CACHE table

### SFTP Integration

| Direction | Path | Purpose |
|-----------|------|---------|
| Outbound | `./sftp-root/outbound/` (dev) | Settlement files uploaded TO clearinghouse |
| Inbound | `./sftp-root/inbound/` (dev) | Reconciliation files FROM clearinghouse |
| Processed | `./sftp-root/processed/` | Archived reconciliation files |
| Remote inbound | `/incoming/` (clearinghouse server) | Where we upload settlement files |
| Remote outbound | `/outgoing/` (clearinghouse server) | Where we poll reconciliation files |

**SFTP Configuration (settlement.properties):**
- `sftp.host` (empty in dev)
- `sftp.port`: 22
- `sftp.username` / `sftp.password`
- `sftp.remote.dir`: `/incoming/`
- `sftp.remote.inbound.dir`: `/outgoing/`
- Connection timeout: 30,000 ms
- Max retries: 1
- Dev mode: local file copy (no actual SFTP)

**Settlement File Formats:**

1. **XML** (`SETTLE_{batchId}.xml`):
   - Root: `<settlementBatch>`
   - Header: batchId, generatedDate, recordCount
   - Records: recordId, orderId, clientId, symbol, quantity, side, amount, commission, tradeDate, settlementDate

2. **Fixed-width DAT** (`SETTLE_{batchId}.dat`):
   - Header: "H" + batchId(20) + date(8) + count(6)
   - Detail: "D" + recordId(20) + orderId(20) + symbol(10) + side(4) + qty(10) + amount(15) + commission(10)
   - Trailer: "T" + totalAmount(20) + totalCommission(15) + count(6)
   - **Column widths must NOT change** (clearinghouse mainframe parser is position-based)

### Email / SMTP

- SMTP host: configured via `smtp.host` (in prod: smtp-internal.bigcorp.com)
- SMTP port: 25
- From address: `noreply@bigcorp.com`
- HTML email: enabled (Outlook 2000 compatible inline CSS)
- Dev mode: logs to console instead of sending
- Known unreliable: 5:00-5:30 PM EST (market close period)
- Retry mechanism handled by NotificationListener

### HTTP/Web Entry Points

| Endpoint | Class | Description |
|----------|-------|-------------|
| OrderEntryServlet | Servlet | Web form for submitting orders |
| OrderStatusServlet | Servlet | Check order status |
| FrontControllerServlet | Servlet | General request routing |
| PricingEndpointServlet | Servlet | SOAP pricing service endpoint |

### Database

- **Development:** HSQLDB in-memory (`jdbc:hsqldb:mem:bigcorpdb`)
- **Production:** Oracle (schema managed by DBA via `schema-oracle.sql`)
- Auto-bootstrap via `DatabaseBootstrap.bootstrap()` in dev
- Tables: CLIENTS, TRADE_ORDERS, NOTIFICATIONS, SETTLEMENT_RECORDS, AUDIT_LOG, BILLING_LEDGER, RULE_AUDIT_LOG, DAILY_VOLUME_TRACKER, PRICING_CACHE, SURVEILLANCE_AUDIT_LOG, POSITION_TRACKING, RISK_ASSESSMENTS, REG_REPORT_LOG

---

## 7. Anomalies

### Intentional/Known Bugs

1. **Reversed Priority Comparator (RuleEngine):** Rules sort DESCENDING by priority (high number runs first). This was accidentally reversed in v1.1. Everyone adapted by using high numbers for important rules. System property `bigcorp.rules.priority.fixed` can fix it but nobody has enabled it.

2. **T+3 Settlement Date Doesn't Skip Weekends (JIRA-2890):** `calculateSettlementDate()` adds 3 calendar days without checking weekends or holidays. "The clearinghouse doesn't seem to care because they recalculate on their end anyway."

3. **XML `active` Attribute Bug:** `rules.xml` defines `active="true/false"` for each rule, but this attribute is NOT applied to the Rule object. Each Rule's `isActive()` is independent. Documented in RuleEngine javadoc as a known bug.

4. **STATUS_VALIDATED and STATUS_PRICED Never Set:** These TradeOrder states are defined as constants but the actual processing flow skips them, going directly from NEW to FILLED.

5. **STATUS_PENDING_REVIEW Defined But Never Used:** Added for JIRA-2341 but no code path transitions to it.

### Commission Rate Discrepancy

Three different commission rates exist:
- `CommissionCalculator`: tier-based (0.005-0.020) - **authoritative**
- `PricingServiceImpl.COMMISSION_RATE`: 0.015 - explicitly documented as intentionally different
- `VolumeDiscountRule.BASE_COMMISSION`: 0.02 - copy-pasted (JIRA-6001)
- `MultiCurrencyRule.COMMISSION_RATE`: 0.02 - copy-pasted (JIRA-7103)

### Duplicated Logic

1. **Price deviation check:** Done in rule engine AND manually in OrderMessageListener (JIRA-2456)
2. **Volume limit check:** DailyVolumeLimitRule (rejects) + manual check in OrderMessageListener (logs only, REG-2005-003)
3. **FX rates:** Hardcoded in both MultiCurrencyRule AND derivatives-engine FxPricingHelper (JIRA-7100)
4. **Commission calculation:** CommissionCalculator + copy-pasted constants in VolumeDiscountRule and MultiCurrencyRule
5. **Client tier spread:** PricingServiceImpl duplicates ClientTierRule logic "in case the rule engine is down"
6. **Order save:** OrderMessageListener calls BOTH `saveOrder()` AND `updateOrderStatus()` (settlement batch reads from the update path)

### Dead Code

- `QUEUE_SETTLEMENT_EVENTS`: "Created for a project that was cancelled but removing it breaks something"
- `OLD_RECORD_SEPARATOR`, `OLD_RECORD_ID_WIDTH` in SettlementFileGenerator (commented out)
- `partialBatchMode` / `partialBatchSize` in BatchProcessor (JIRA-2890, "never fully implemented")
- `STATUS_PENDING_REVIEW` constant (JIRA-2341, never used)
- ~50 methods with no callers identified by Joern CPG analysis

### Hardcoded Values That Should Be Configurable

- Restricted symbols list (ENRN, WCOM, TYCO, ADLP) - JIRA-4100
- Special client arrangements (C001-C010) - JIRA-7200
- Loyalty bonus client list (C001, C002, C003) - JIRA-6002
- FX rates (stale since 2014) - JIRA-7101
- Hardcoded fallback prices (stale since 2000-2001)
- Position limit of 100,000 shares - JIRA-8200
- Short sale threshold of 1,000 shares
- Volatility assumptions in risk engine

### Security Concerns

- Hardcoded credentials references in properties: `db.password`, `sms.gateway.apikey`, `sftp.password`
- 924 `System.out/err.println` calls instead of proper logging framework
- Non-thread-safe singleton pattern in RuleEngine
- Empty catch blocks in multiple locations

### JIRA References Found in Code

| JIRA | Description | Status |
|------|-------------|--------|
| JIRA-1102 | Unknown symbol handling in pricing | Fixed (default price returned) |
| JIRA-1892 | Henderson max order value complaint | Fixed (10% buffer added) |
| JIRA-2089 | Pricing timeout too low | Fixed (increased to 10s/15s) |
| JIRA-2340 | CPU usage from polling | Fixed (poll interval 1s->5s) |
| JIRA-2341 | PENDING_REVIEW status | Never implemented |
| JIRA-2456 | Duplicate price deviation check | Open (both checks kept) |
| JIRA-2501 | Commission rate centralization | Fixed (CommissionCalculator) |
| JIRA-2890 | T+3 weekend skip / partial batch | Open (never implemented) |
| JIRA-3401 | Pinnacle commission discount | Fixed |
| JIRA-3490 | Reconciliation processing decision | Referenced |
| JIRA-3501 | Settlement data storage | Referenced |
| JIRA-4100 | Restricted symbols to DB | Open |
| JIRA-4101 | ShortSaleRule to XML config | Open |
| JIRA-5100 | Volume warning to AUDIT_LOG | Open |
| JIRA-5300 | Priority comparator fix | Fixed (behind feature flag) |
| JIRA-6001 | VolumeDiscount use CommissionCalculator | Open |
| JIRA-6002 | Loyalty tenure from DB | Open |
| JIRA-7100 | FX rate sharing | Open |
| JIRA-7101 | FX rates to DB/config | Open |
| JIRA-7102 | JPY quoting differences | Open |
| JIRA-7103 | MultiCurrency use CommissionCalculator | Open |
| JIRA-7104 | Apply FX conversion in rule | Open |
| JIRA-7200 | Special clients to DB config | Open |
| JIRA-7201 | SpecialClientsRule too long | Open |
| JIRA-7202 | Apex Capital pricing override | Fixed |
| JIRA-8200 | Position limit configurable | Open |

### Architectural Anomalies

1. **ShortSaleRule added programmatically:** After loading all rules from XML, ShortSaleRule is always added manually in OrderMessageListener.initRules() with comment "will add to XML config later (JIRA-4101)". This means it runs regardless of XML configuration.

2. **Surveillance rules flag but never reject:** LayeringDetection and SpoofingPattern always return true. They store flags in context attributes and SURVEILLANCE_AUDIT_LOG but never actually block orders. The flags must be reviewed by compliance staff externally.

3. **Rule execute() exceptions swallowed:** If a rule's `execute()` method throws, the exception is caught and logged but the order is NOT rejected. This was a deliberate decision after "the 2001 incident where a logging rule crashed and rejected 500 orders."

4. **Dual save pattern:** OrderMessageListener saves filled orders twice (`saveOrder` + `updateOrderStatus`) because "the settlement batch job reads from this update path." This creates a potential inconsistency window.

5. **Pricing fallback chain:** SOAP -> DB -> hardcoded values from 2000. The "temporary" hardcoded fallback has survived 2+ years.

---

## Gaps: Business Logic NOT Determinable from Code Alone

1. **Actual production commission applied:** While CommissionCalculator defines tier rates, it's unclear whether the volume discount (`volume_discount` context attribute) and loyalty bonus (`loyalty_bonus` context attribute) are actually applied downstream or just stored as metadata.

2. **CANCELLED order origination:** No code path found that transitions orders to CANCELLED status, yet surveillance rules query for it.

3. **Multi-currency actual conversion:** The MultiCurrencyRule stores the FX rate in context but the `execute()` method is a no-op. Where (if anywhere) the conversion is applied to order value is not determinable from the order-engine code.

4. **Risk engine integration:** The risk engine appears to operate independently with its own queue (`RISK.ORDERS.INBOUND`) and stores results in RISK_ASSESSMENTS table, but no code in the order engine explicitly sends to or reads from the risk engine.

5. **Regulatory reporting:** REG_REPORT_LOG table and BIGCORP_REG_REPORT queue exist but the regulatory report generation logic is only partially visible in the CPG.

6. **Derivatives engine interaction:** Separate queue infrastructure (BIGCORP.DERIVATIVES.*) exists but the relationship to the core trade order flow is not explicitly linked in the source code.

7. **Holiday calendar:** Referenced in comments but no implementation found.

8. **SMS/FAX notification channels:** Defined as constants but implementation appears to be stubs.
