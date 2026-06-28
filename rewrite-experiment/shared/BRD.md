# BigCorp Trade Order Management System - Business Requirements Document

## 1. Executive Summary

The BigCorp Trade Order Management System is a J2EE-era (1999-2002+) enterprise trading platform for processing equity trade orders, FX/derivatives, settlement, and regulatory reporting. The system spans multiple modules: order-engine, settlement-gateway, pricing-service, notification-gateway, risk-engine, derivatives-engine, audit-service, reporting-service, and trade-desk (web UI).

**Domain**: Financial services / securities trading (equities + FX derivatives).

**Users**: Trading desk operators, clients (via web portal), compliance team, operations/settlement staff, sales team.

**Technology Context**: Java 1.4, Ant build, HSQLDB (dev) / Oracle (prod), ActiveMQ (JMS), SOAP web services, SFTP (JSch), SMTP email, Servlet 2.3 / JSP web UI, JDBC.

The system follows a message-driven architecture: orders enter via JMS queue, pass through a rule engine (chain-of-responsibility pattern with 18 rules), get priced via SOAP, and produce settlement files uploaded via SFTP. Notifications are dispatched via email/SMS. A separate derivatives engine handles FX spot/forward and options. Risk assessment runs asynchronously with parametric VaR calculation.

---

## 2. Functional Requirements

### FR-ORD: Order Management

**FR-ORD-001**: The system SHALL accept trade orders via JMS queue `BIGCORP.TRADE.ORDERS` as XML text messages.

**FR-ORD-002**: Each order SHALL be unmarshalled from XML into a `TradeOrder` object with fields: orderId, clientId, symbol, quantity, side (BUY/SELL), requestedPrice, status, orderDate, lastModified, notes.

**FR-ORD-003**: The order ID SHALL follow the format `ORD-{timestamp}` where timestamp is `System.currentTimeMillis()`.

**FR-ORD-004**: The system SHALL look up the client record from the CLIENTS table by clientId. If client is not found, the order SHALL be rejected with reason "Client not found: {clientId}".

**FR-ORD-005**: If the client's `active` flag is false (0), the order SHALL be rejected with reason "Client account is inactive".

**FR-ORD-006**: All orders SHALL pass through the RuleEngine. If any rule fails, the order is rejected and the chain stops immediately.

**FR-ORD-007**: After rules pass, the system SHALL obtain a price quote from the PricingServiceClient (SOAP call to `http://localhost:8080/pricing-service/services/PricingService`). If the quoted price is <= 0, the order SHALL be rejected with reason "Price unavailable for symbol: {symbol}".

**FR-ORD-008**: A manual price deviation check SHALL be performed after the SOAP pricing call. If `|quotedPrice - requestedPrice| / requestedPrice > 0.10` (10%), the order SHALL be rejected with reason "Price deviation exceeds 10% limit". This is intentionally redundant with the rule engine check (JIRA-2456).

**FR-ORD-009**: A manual volume compliance warning SHALL be logged (but NOT reject) if `quantity > 50000` shares (REG-2005-003). This duplicates the DailyVolumeLimitRule check.

**FR-ORD-010**: On successful fill, the order SHALL be updated with: `price = quotedPrice`, `status = FILLED`, `lastModified = now()`, notes appended with "Filled at {price}, commission={commission}".

**FR-ORD-011**: The order SHALL be saved to the TRADE_ORDERS table via both `saveOrder()` (insert or update) and `updateOrderStatus()` (redundant update for settlement batch compatibility).

**FR-ORD-012**: On fill, a confirmation notification SHALL be sent to the BIGCORP.NOTIFICATIONS queue with type ORDER_CONFIRM, channel EMAIL, recipient = client email.

**FR-ORD-013**: On rejection, a rejection notification SHALL be sent to the BIGCORP.NOTIFICATIONS queue with type ORDER_REJECT if the client has an email address.

**FR-ORD-014**: After fill or rejection, a status update XML message SHALL be sent to the `BIGCORP.TRADE.CONFIRMATIONS` queue for downstream consumers (settlement, audit).

**FR-ORD-015**: The OrderMessageListener SHALL poll the JMS queue with a 5000ms timeout (JIRA-2340, increased from 1000ms after CPU usage incident). On message processing error, it SHALL sleep 2000ms before retrying.

**FR-ORD-016**: Notification body SHALL use pipe-delimited format: `symbol|quantity|side|price|reason|amount|settlementDate` for template substitution in EmailDispatcher.

### FR-RUL: Rule Engine

**FR-RUL-001**: The RuleEngine SHALL be a singleton that evaluates a chain of Rule implementations against a RuleContext containing the TradeOrder and Client.

**FR-RUL-002**: Rules SHALL be sorted by priority. The default (legacy) sort is DESCENDING (higher priority number runs first). A system property `bigcorp.rules.priority.fixed` can switch to ASCENDING order (JIRA-5300).

**FR-RUL-003**: If any rule's `evaluate()` returns false, the chain SHALL stop immediately and the order SHALL be rejected.

**FR-RUL-004**: If a rule's `evaluate()` throws an exception, the order SHALL be rejected with reason "Rule error: {ruleName} - {message}".

**FR-RUL-005**: If a rule's `execute()` throws an exception after passing evaluation, the exception SHALL be logged but SHALL NOT fail the order (deliberate decision after 2001 incident where a logging rule crashed and rejected 500 orders).

**FR-RUL-006**: Inactive rules (isActive() == false) SHALL be skipped, but there is a known bug where XML-loaded rules toggled inactive after loading still get their execute() called.

**FR-RUL-007**: Every rule decision SHALL be logged to the RULE_AUDIT_LOG table (REG-2011-003). Audit logging failures SHALL never prevent order processing.

**FR-RUL-008**: Rules SHALL be loaded from `config/rules.xml` via reflection. If no config file is found, fall back to hardcoded registration of MaxOrderValueRule, ClientTierRule, MarketHoursRule, SpecialClientsRule.

**FR-RUL-009**: ShortSaleRule SHALL always be added manually after config-loaded rules (JIRA-4101 - not yet added to XML config).

**FR-RUL-010**: The TypedRule interface provides a `evaluateTyped()` method returning a structured `RuleResult`, which is applied back to the context for backward compatibility. Legacy Rule implementations continue to work unchanged.

#### Individual Rules (in descending priority execution order)

**FR-RUL-011 (LayeringDetectionRule, priority=125)**: SHALL count all non-CANCELLED orders for the same client+symbol in the TRADE_ORDERS table. If count > 5, the order SHALL be FLAGGED (attribute `surveillance_flags` += "LAYERING") but NOT rejected. Compliance team reviews flagged orders. DB errors SHALL fail open (allow the trade). (REG-2015-001)

**FR-RUL-012 (SpoofingPatternRule, priority=124)**: SHALL compute `cancelRate = cancelledOrders / totalOrders` for the client. If `cancelRate > 0.60` (60%), the order SHALL be FLAGGED (attribute `surveillance_flags` += "SPOOFING") but NOT rejected. DB errors fail open. (REG-2015-002)

**FR-RUL-013 (PositionLimitRule, priority=123)**: SHALL look up net position from POSITION_TRACKING table for client+symbol. If `|currentPosition + orderQty| > 100000` (for BUY) or `|currentPosition - orderQty| > 100000` (for SELL), the order SHALL be REJECTED. If no position record exists and `orderQty > 100000`, also reject. DB errors fail open. (REG-2015-003)

**FR-RUL-014 (MarketHaltRule, priority=120)**: SHALL check system property `bigcorp.market.halted`. If "true", ALL orders SHALL be rejected with "MARKET HALTED -- trading suspended (REG-2011-001)". No exceptions, no overrides. Defaults to "false" (market open). SecurityManager exceptions default to not halted. (REG-2011-001)

**FR-RUL-015 (ClientKillSwitchRule, priority=118)**: SHALL query `KILL_SWITCH` column from CLIENTS table. If value is "Y" (case-insensitive, trimmed), ALL orders from that client SHALL be rejected with "Client trading suspended -- kill switch active (REG-2011-002)". If column not found or DB error, defaults to "N" (allow). (REG-2011-002)

**FR-RUL-016 (KYCStatusRule, priority=115)**: SHALL query `KYC_STATUS` from CLIENTS table. Only "APPROVED" status allows trading. "PENDING", "EXPIRED", "REJECTED" or null (treated as "PENDING") SHALL reject the order. KYC status is stored in context attribute `kyc_status`.

**FR-RUL-017 (DailyVolumeLimitRule, priority=110)**: SHALL reject any single order where `quantity > 50000` shares with reason "Daily volume limit exceeded (REG-2005-001)". Despite the name, this is a per-order check, not cumulative daily tracking. Sets context attributes `daily_volume_checked` and `compliance_flags`.

**FR-RUL-018 (WashTradeDetectionRule, priority=105)**: SHALL query TRADE_ORDERS for any order from the same client+symbol with opposite side and non-REJECTED status within the last 5 minutes (`WASH_TRADE_WINDOW_MINUTES = 5`). If found, reject with "Potential wash trade detected (REG-2005-002)". DB errors fail open.

**FR-RUL-019 (MaxOrderValueRule, priority=100)**: SHALL compute `orderValue = quantity * requestedPrice`. If `orderValue > client.maxOrderValue * 1.10` (10% buffer, JIRA-1892 for Henderson complaint), reject with "Order value {orderValue} exceeds max allowed {maxAllowed}". If client is null, reject with "No client record found".

**FR-RUL-020 (RestrictedSymbolRule, priority=95)**: SHALL reject orders for symbols in the restricted list: `ENRN`, `WCOM`, `TYCO`, `ADLP`. Rejection reason: "Restricted symbol: {symbol} -- trading suspended".

**FR-RUL-021 (ClientTierRule, priority=90)**: SHALL check client is active. If not, reject. On pass, sets context attribute `priority` to "HIGH" for PLATINUM/GOLD clients, "NORMAL" for SILVER/BRONZE.

**FR-RUL-022 (MarketHoursRule, priority=80)**: Market hours are 9:30 AM to 4:00 PM server local time (NOT Eastern -- known bug). Weekend orders SHALL be queued (not rejected) with warning. Outside-hours orders SHALL be queued (not rejected) with warning. Sets context attribute `queued = true`.

**FR-RUL-023 (ShortSaleRule, priority=75)**: SHALL reject SELL orders where `quantity > 1000` with reason "Short sale limit exceeded". For passing SELL orders, calculates and stashes commission using CommissionCalculator.getRate(tier).

**FR-RUL-024 (MultiCurrencyRule, priority=60)**: SHALL check context attribute `currency`. If null or "USD", sets `fx_rate_applied=1.0`, `settlement_currency=USD`. For other currencies (EUR, GBP, JPY, CHF), looks up hardcoded FX rate and sets context attributes. Unknown currencies default to USD with warning (not rejected). Actual conversion happens downstream.

**FR-RUL-025 (VolumeDiscountRule, priority=55)**: Always passes. On execute, sets commission discount: `quantity > 10000` -> 50% discount; `quantity > 5000` -> 25% discount. Sets context attributes `volume_discount` and `volume_discount_applied`.

**FR-RUL-026 (SpecialClientsRule, priority=50)**: Always passes. Applies per-client overrides (see Section 8 for full details).

**FR-RUL-027 (LoyaltyBonusRule, priority=45)**: Always passes. For clients C001, C002, C003 (hardcoded tenure check), sets `loyalty_bonus = 0.10` (10% additional discount).

### FR-SET: Settlement

**FR-SET-001**: The settlement batch processor SHALL find all FILLED orders from the TRADE_ORDERS table and create settlement records.

**FR-SET-002**: Each settlement record SHALL have: recordId (format `SR-{timestamp}-{orderId.hashCode}`), orderId, clientId, symbol, quantity, side, amount (`quantity * price`), commission (via CommissionCalculator using client tier), tradeDate, settlementDate (T+3), status PENDING, batchId.

**FR-SET-003**: Settlement date SHALL be calculated as tradeDate + 3 calendar days (T+3). Known bug: does NOT skip weekends or holidays (JIRA-2890).

**FR-SET-004**: Batch ID SHALL follow format `BATCH-yyyyMMdd-NNN` where NNN is a zero-padded sequence number.

**FR-SET-005**: After creating settlement records, order status SHALL be updated to SETTLED.

**FR-SET-006**: The system SHALL generate XML and flat (.dat) settlement files for upload to the clearinghouse.

**FR-SET-007**: Settlement files SHALL be uploaded via SFTP to the clearinghouse. SFTP config: host, port, username, password, remote dir from `settlement.properties`. If SFTP is unavailable, files SHALL be copied to local fallback directory `./sftp-root/outbound/`. SFTP upload retries on failure.

**FR-SET-008**: Settlement file output directory defaults to `./sftp-outbound/`.

**FR-SET-009**: A settlement notification (TYPE_SETTLEMENT, CHANNEL_EMAIL) SHALL be sent for each settlement record.

**FR-SET-010**: In production, the batch runs at 6 PM EST via cron. The Timer-based scheduling in BatchScheduler is for testing/demo.

**FR-SET-011**: Partial batch mode was requested (JIRA-2890) but never fully implemented.

### FR-REC: Reconciliation

**FR-REC-001**: The system SHALL poll for inbound reconciliation files from the clearinghouse SFTP server (`sftp.remote.inbound.dir`) or local directory `./sftp-root/inbound/`.

**FR-REC-002**: Two file formats SHALL be supported: XML (.xml) and fixed-width DAT (.dat, COBOL-style from mainframe).

**FR-REC-003**: DAT status codes SHALL be mapped to internal statuses:
- `CONF` -> RECONCILED
- `REJC` -> DISCREPANCY
- `DISC` -> DISCREPANCY
- `PEND` -> skipped (clearinghouse hasn't finished processing)

**FR-REC-004**: DAT file format: Header line (HDR, 80 chars), data lines (positions 1-20: recordId, 21-30: externalRef, 31-40: status, 41-50: amount, 51-58: date YYYYMMDD, 59-78: reasonCode), trailer (TRL + record count).

**FR-REC-005**: Processed files SHALL be moved to `./sftp-root/processed/`. If file already exists, timestamp prefix added.

**FR-REC-006**: Reason codes from rejected/discrepancy entries SHALL be logged but NOT stored in the data model (SettlementRecord lacks a reasonCode field, JIRA-3522).

### FR-NOT: Notifications

**FR-NOT-001**: The notification gateway SHALL consume messages from `BIGCORP.NOTIFICATIONS` JMS queue with 5000ms poll timeout.

**FR-NOT-002**: Notifications SHALL be dispatched via EMAIL (SMTP), SMS, or FAX channels. FAX is deprecated (2002) but still handled -- logged as warning, marked FAILED.

**FR-NOT-003**: Email dispatch SHALL use SMTP config from `notification.properties`: smtp.host, smtp.port (default 25), smtp.from (default noreply@bigcorp.com), email.html.enabled (default true).

**FR-NOT-004**: If SMTP host is empty or not configured, email dispatch SHALL operate in "dev mode" (log email content to console instead of sending).

**FR-NOT-005**: SMTP relay does NOT use authentication (internal network relay).

**FR-NOT-006**: HTML email SHALL wrap content in BigCorp-branded HTML template with inline CSS (for Outlook 2000 compatibility). Header: "#003366" background with white text. Footer: "Do not reply..." disclaimer.

**FR-NOT-007**: Email body SHALL support pipe-delimited template substitution: `${symbol}|${quantity}|${side}|${price}|${reason}|${amount}|${settlementDate}`.

**FR-NOT-008**: Email templates SHALL be loaded from classpath: `com/bigcorp/notifications/templates/order_confirm.xml`, `order_reject.xml`, `settlement.xml`.

**FR-NOT-009**: Failed notifications SHALL be retried up to 3 times (MAX_RETRY_COUNT = 3) by re-queuing to the NOTIFICATIONS queue with incremented retryCount.

**FR-NOT-010**: After successful send, notification status SHALL be set to SENT with sentDate. After max retries exhausted, status SHALL be FAILED.

**FR-NOT-011**: Notification records SHALL be persisted to the NOTIFICATIONS table (INSERT only, no updates).

**FR-NOT-012**: SMS dispatch SHALL send XML-formatted messages via the SMSDispatcher with sender tag `<sender>BIGCORP</sender>`.

**FR-NOT-013**: Notification ID format: `N-{orderId}-{timestamp}` for confirmations, `N-{orderId}-REJ-{timestamp}` for rejections, `NOTIF-{timestamp}-{recordId.hashCode}` for settlement.

### FR-PRC: Pricing

**FR-PRC-001**: The pricing service SHALL expose a SOAP endpoint at `/pricing-service/services/PricingService` supporting single quote (GetQuote) and batch quote (BatchQuote) operations.

**FR-PRC-002**: SOAP namespace: `xmlns:pric="http://bigcorp.com/pricing"`, response namespace: `xmlns:types="http://pricing.bigcorp.com/types"`. SOAPAction header required.

**FR-PRC-003**: WSDL SHALL be served from `/WEB-INF/wsdl/PricingService.wsdl` via GET requests.

**FR-PRC-004**: Pricing SHALL first look up from PRICING_CACHE database table, then fall back to hardcoded prices if DB fails.

**FR-PRC-005**: Hardcoded fallback prices (from PricingServiceImpl):
| Symbol | Bid | Ask | Last |
|--------|------|------|------|
| MSFT | 25.00 | 25.50 | 25.25 |
| IBM | 119.00 | 120.00 | 119.50 |
| ORCL | 15.00 | 15.50 | 15.25 |
| SUNW | 8.50 | 9.00 | 8.75 |
| CSCO | 21.50 | 22.00 | 21.75 |
| INTC | 30.00 | 30.50 | 30.25 |
| DELL | 34.50 | 35.00 | 34.75 |
| (unknown) | 10.00 | 10.50 | 10.25 |

**FR-PRC-006**: Database pricing (PRICING_CACHE):
| Symbol | Bid | Ask | Last |
|--------|------|------|------|
| MSFT | 25.50 | 25.75 | 25.63 |
| IBM | 120.00 | 120.50 | 120.25 |
| ORCL | 15.25 | 15.50 | 15.38 |
| SUNW | 8.75 | 9.00 | 8.88 |
| CSCO | 22.00 | 22.25 | 22.13 |
| INTC | 30.50 | 30.75 | 30.63 |
| DELL | 35.00 | 35.25 | 35.13 |

**FR-PRC-007**: Tier-based spread adjustments (PricingServiceImpl):
| Tier | Spread |
|------|--------|
| PLATINUM | 0.001 (0.1%) |
| GOLD | 0.002 (0.2%) |
| SILVER | 0.003 (0.3%) |
| BRONZE | 0.005 (0.5%) |
| DEFAULT | 0.005 (0.5%) |

Spread applied as: `bidPrice = lastPrice * (1.0 - spread)`, `askPrice = lastPrice * (1.0 + spread)`.

**FR-PRC-008**: The pricing service has its own commission rate of 0.015 (1.5%) which is DIFFERENT from the order-engine's rate (0.02 / tier-based). This discrepancy is documented as "by design".

**FR-PRC-009**: PricingServiceClient in order-engine first tries SOAP call, then falls back to database lookup (`getQuoteFromDatabase`), then to hardcoded prices. SOAP response parsing extracts `<price>` element.

**FR-PRC-010**: Batch quotes (`getBatchQuotes`) SHALL call `getQuote` in a loop (not optimized, JIRA from Q3 2001).

### FR-AUD: Audit & Billing

**FR-AUD-001**: The audit service SHALL consume from the `BIGCORP.TRADE.CONFIRMATIONS` queue and create AUDIT_LOG entries for order lifecycle events.

**FR-AUD-002**: Audit event types: ORDER_FILLED, ORDER_REJECTED, ORDER_SETTLED, BILLING_CHARGED. Source systems: order-engine, settlement-gateway, audit-service. Entity types: ORDER, BILLING.

**FR-AUD-003**: For FILLED orders, the audit service SHALL create a billing entry in the BILLING_LEDGER table with: orderId, clientId, grossAmount, commissionAmount, netAmount, status=CHARGED.

**FR-AUD-004**: Commission for billing SHALL be calculated using CommissionCalculator based on client tier looked up from the CLIENTS table.

**FR-AUD-005**: Rule audit logging (RULE_AUDIT_LOG) SHALL record: ruleName, orderId, clientId, result (PASS/FAIL/SKIP), evaluationTime, details. Every rule evaluation is logged (REG-2011-003).

**FR-AUD-006**: Surveillance audit logging (SURVEILLANCE_AUDIT_LOG) SHALL record: ruleName, orderId, clientId, symbol, result, surveillanceFlags, evaluationTime, details.

### FR-RSK: Risk Engine

**FR-RSK-001**: The risk engine SHALL assess trade orders independently using its own model (RiskOrder) with fields: riskOrderId, sourceOrderId, clientId, symbol, quantity, side, price, notionalValue, exposureContribution, varContribution, riskStatus.

**FR-RSK-002**: Notional value SHALL be calculated as `quantity * price`.

**FR-RSK-003**: Exposure contribution SHALL be directional: BUY = +notional, SELL = -notional. Unknown side = +notional.

**FR-RSK-004**: VaR SHALL be calculated using simplified parametric (delta-normal) method:
```
VaR = |notional| * volatility * zScore * sqrt(holdingPeriod / tradingDaysPerYear)
```
Where:
- zScore = 2.33 (99% confidence)
- holdingPeriod = 1.0 day
- tradingDaysPerYear = 252.0

**FR-RSK-005**: Volatility assumptions (hardcoded):
| Asset Class | Annual Vol |
|------------|-----------|
| Equity (default) | 0.20 (20%) |
| FX (symbol contains '/') | 0.08 (8%) |
| Commodity (GOLD, OIL, SILVER) | 0.30 (30%) |
| Unknown | 0.25 (25%) |

**FR-RSK-006**: If VaR > 50000.0, the risk order SHALL be FLAGGED. Otherwise, ASSESSED.

**FR-RSK-007**: Risk engine uses its own JMS queues: `RISK.ORDERS.INBOUND`, `RISK.RESULTS.OUTBOUND`.

**FR-RSK-008**: Risk assessments SHALL be stored in the RISK_ASSESSMENTS table.

**FR-RSK-009**: RiskScheduler polls for unassessed orders and runs ExposureCalculator. On error, risk status is set to ERROR.

### FR-DRV: Derivatives

**FR-DRV-001**: The derivatives engine SHALL process FX and options orders independently from the equities rule engine, using its own model (DerivativeOrder) with fields: orderId, clientId, contractType, underlying, strikePrice, quantity, expiry, status, premium.

**FR-DRV-002**: Valid contract types: FX_SPOT, FX_FORWARD, OPTION_CALL, OPTION_PUT.

**FR-DRV-003**: Validation rules (DerivativeProcessor):
- orderId, clientId, contractType, underlying must be non-null/non-empty
- contractType must be one of the four valid types
- quantity must be > 0
- strikePrice must be > 0
- notional (`quantity * strikePrice`) must be <= 10,000,000 (MAX_NOTIONAL)

**FR-DRV-004**: Premium calculation:
- FX_SPOT, FX_FORWARD: `premium = notional * 0.015` (FX_COMMISSION rate)
- OPTION_CALL, OPTION_PUT: `premium = notional * 0.05` (flat 5%, placeholder for Black-Scholes)

**FR-DRV-005**: Derivatives commission rate is 0.015 (1.5%), separate from equities (2%).

**FR-DRV-006**: Hardcoded FX rates (DerivativeProcessor + FxPricingHelper):
| Pair | Rate |
|------|------|
| EUR/USD | 1.10 |
| GBP/USD | 1.55 |
| JPY/USD | 0.009 |
| CHF/USD | 0.72 |
| AUD/USD | 0.68 |

**FR-DRV-007**: FX spread: 0.002 (0.2%). Bid = mid * (1 - spread/2), Ask = mid * (1 + spread/2).

**FR-DRV-008**: Derivatives uses its own JMS queues: `BIGCORP.DERIVATIVES.ORDERS`, `BIGCORP.DERIVATIVES.CONFIRMS`, `BIGCORP.DERIVATIVES.PRICING`.

**FR-DRV-009**: DerivativeOrder has its own XML marshalling (inline, no XmlHelper dependency) and its own status constants (NEW, FILLED, REJECTED).

### FR-REG: Regulatory Reporting

**FR-REG-001**: The system SHALL generate daily regulatory reports in two formats: fixed-width (.dat, REG-FW-001) and XML (.xml, REG-XML-001). (REG-2021-001)

**FR-REG-002**: Reports SHALL include all FILLED and SETTLED orders from TRADE_ORDERS, ordered by ORDER_DATE ascending.

**FR-REG-003**: Fixed-width format:
- Header: "HDR" + firm name (30 chars) + timestamp (19 chars)
- Data lines: "DTL" + orderId(20) + clientId(10) + symbol(10) + side(4) + qty(12, right-justified) + price(15, right-justified, 4 decimal places) + status(12) + orderDate(19)
- Trailer: "TRL" + record count (10, right-justified) + timestamp (19)

**FR-REG-004**: XML format:
```xml
<RegulatoryReport>
  <Header>
    <Firm>BIGCORP</Firm>
    <ReportDate>yyyy-MM-ddTHH:mm:ss</ReportDate>
    <ReportType>DAILY_TRADE_REPORT</ReportType>
  </Header>
  <Trades><Trade>...</Trade></Trades>
  <Trailer><RecordCount>N</RecordCount></Trailer>
</RegulatoryReport>
```

**FR-REG-005**: Report generation SHALL be logged to the REG_REPORT_LOG table with: reportType, filePath, recordCount, status (SUCCESS/FAILED), generationTime.

**FR-REG-006**: Output directory defaults to `./regulatory-output/`. File names: `REG_REPORT_{yyyyMMdd_HHmmss}.dat` and `REG_REPORT_{yyyyMMdd_HHmmss}.xml`.

**FR-REG-007**: All fields SHALL have defensive null checks. If a null field is submitted to the regulator, their parser crashes and BigCorp gets fined.

### FR-SRV: Surveillance

**FR-SRV-001**: Three surveillance rules SHALL run as part of the order validation pipeline: LayeringDetectionRule (priority 125), SpoofingPatternRule (priority 124), PositionLimitRule (priority 123).

**FR-SRV-002**: Layering detection SHALL flag orders (not reject) when a client has > 5 non-CANCELLED orders for the same symbol. (REG-2015-001)

**FR-SRV-003**: Spoofing detection SHALL flag orders (not reject) when a client's cancellation rate exceeds 60%. (REG-2015-002)

**FR-SRV-004**: Position limits SHALL reject orders that would push a client's net position beyond +/- 100,000 shares for any symbol. (REG-2015-003)

**FR-SRV-005**: Surveillance flags SHALL accumulate in a comma-separated context attribute `surveillance_flags` (e.g., "LAYERING,SPOOFING").

**FR-SRV-006**: Surveillance decisions SHALL be logged to SURVEILLANCE_AUDIT_LOG table.

**FR-SRV-007**: Surveillance rule failures (DB errors, etc.) SHALL always fail open (allow the trade) -- trading must never be blocked by surveillance infrastructure failure.

---

## 3. Data Model

### DM-001: CLIENTS
| Column | Type | Constraints |
|--------|------|-------------|
| CLIENT_ID | VARCHAR(20) | PK |
| CLIENT_NAME | VARCHAR(100) | NOT NULL |
| EMAIL | VARCHAR(100) | |
| PHONE | VARCHAR(20) | |
| TIER | VARCHAR(10) | DEFAULT 'BRONZE'. Values: PLATINUM, GOLD, SILVER, BRONZE |
| MAX_ORDER_VALUE | DECIMAL(15,2) | DEFAULT 100000.00 |
| ACTIVE | INTEGER | DEFAULT 1 (boolean: 1=active, 0=inactive) |
| KYC_STATUS | VARCHAR(20) | DEFAULT 'APPROVED'. Values: APPROVED, PENDING, EXPIRED, REJECTED |
| KILL_SWITCH | VARCHAR(1) | DEFAULT 'N'. Values: Y, N |
| CREATED_DATE | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |

### DM-002: TRADE_ORDERS
| Column | Type | Constraints |
|--------|------|-------------|
| ORDER_ID | VARCHAR(30) | PK. Format: ORD-{timestamp} |
| CLIENT_ID | VARCHAR(20) | NOT NULL, FK -> CLIENTS(CLIENT_ID) |
| SYMBOL | VARCHAR(10) | NOT NULL |
| QUANTITY | INTEGER | NOT NULL |
| SIDE | VARCHAR(4) | NOT NULL. Values: BUY, SELL |
| PRICE | DECIMAL(15,4) | DEFAULT 0 (filled price) |
| REQUESTED_PRICE | DECIMAL(15,4) | DEFAULT 0 |
| STATUS | VARCHAR(20) | DEFAULT 'NEW' |
| ORDER_DATE | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| LAST_MODIFIED | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| NOTES | VARCHAR(500) | |
| SURVEILLANCE_FLAGS | VARCHAR(200) | DEFAULT '' (added Wave 10/2015) |

### DM-003: NOTIFICATIONS
| Column | Type | Constraints |
|--------|------|-------------|
| NOTIFICATION_ID | VARCHAR(30) | PK |
| NOTIFICATION_TYPE | VARCHAR(20) | NOT NULL. Values: ORDER_CONFIRM, ORDER_REJECT, SETTLEMENT, PRICE_ALERT |
| RECIPIENT | VARCHAR(100) | NOT NULL |
| SUBJECT | VARCHAR(200) | |
| BODY | VARCHAR(2000) | |
| CHANNEL | VARCHAR(10) | NOT NULL. Values: EMAIL, SMS, FAX |
| STATUS | VARCHAR(10) | DEFAULT 'PENDING'. Values: PENDING, SENT, FAILED |
| ORDER_ID | VARCHAR(30) | |
| CREATED_DATE | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| SENT_DATE | TIMESTAMP | |
| RETRY_COUNT | INTEGER | DEFAULT 0 |

### DM-004: SETTLEMENT_RECORDS
| Column | Type | Constraints |
|--------|------|-------------|
| RECORD_ID | VARCHAR(30) | PK. Format: SR-{timestamp}-{orderId.hashCode} |
| ORDER_ID | VARCHAR(30) | NOT NULL |
| CLIENT_ID | VARCHAR(20) | NOT NULL |
| SYMBOL | VARCHAR(10) | NOT NULL |
| QUANTITY | INTEGER | NOT NULL |
| SIDE | VARCHAR(4) | NOT NULL |
| AMOUNT | DECIMAL(15,4) | NOT NULL |
| COMMISSION | DECIMAL(10,4) | DEFAULT 0 |
| TRADE_DATE | TIMESTAMP | NOT NULL |
| SETTLEMENT_DATE | TIMESTAMP | |
| STATUS | VARCHAR(15) | DEFAULT 'PENDING'. Values: PENDING, GENERATED, UPLOADED, CONFIRMED, FAILED, RECONCILED, DISCREPANCY |
| BATCH_ID | VARCHAR(30) | |
| EXTERNAL_REF | VARCHAR(50) | |

### DM-005: AUDIT_LOG
| Column | Type | Constraints |
|--------|------|-------------|
| LOG_ID | INTEGER IDENTITY | PK |
| EVENT_TYPE | VARCHAR(30) | NOT NULL |
| SOURCE_SYSTEM | VARCHAR(30) | |
| ENTITY_TYPE | VARCHAR(20) | |
| ENTITY_ID | VARCHAR(30) | |
| DESCRIPTION | VARCHAR(500) | |
| LOG_DATE | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| USER_ID | VARCHAR(30) | |

### DM-006: BILLING_LEDGER
| Column | Type | Constraints |
|--------|------|-------------|
| ENTRY_ID | INTEGER IDENTITY | PK |
| ORDER_ID | VARCHAR(30) | NOT NULL |
| CLIENT_ID | VARCHAR(20) | NOT NULL |
| GROSS_AMOUNT | DECIMAL(15,4) | NOT NULL |
| COMMISSION_AMOUNT | DECIMAL(10,4) | NOT NULL |
| NET_AMOUNT | DECIMAL(15,4) | NOT NULL |
| CHARGED_DATE | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| STATUS | VARCHAR(15) | DEFAULT 'CHARGED' |

### DM-007: RULE_AUDIT_LOG
| Column | Type | Constraints |
|--------|------|-------------|
| AUDIT_ID | INTEGER IDENTITY | PK |
| RULE_NAME | VARCHAR(50) | NOT NULL |
| ORDER_ID | VARCHAR(30) | |
| CLIENT_ID | VARCHAR(20) | |
| RESULT | VARCHAR(10) | NOT NULL |
| EVALUATION_TIME | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| DETAILS | VARCHAR(500) | |

### DM-008: DAILY_VOLUME_TRACKER
| Column | Type | Constraints |
|--------|------|-------------|
| CLIENT_ID | VARCHAR(20) | PK (composite) |
| TRADE_DATE | DATE | PK (composite) |
| TOTAL_SHARES | INTEGER | DEFAULT 0 |
| LAST_UPDATED | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |

### DM-009: PRICING_CACHE
| Column | Type | Constraints |
|--------|------|-------------|
| SYMBOL | VARCHAR(10) | PK |
| BID_PRICE | DECIMAL(15,4) | |
| ASK_PRICE | DECIMAL(15,4) | |
| LAST_PRICE | DECIMAL(15,4) | |
| CURRENCY | VARCHAR(3) | DEFAULT 'USD' |
| LAST_UPDATED | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |

### DM-010: SURVEILLANCE_AUDIT_LOG
| Column | Type | Constraints |
|--------|------|-------------|
| LOG_ID | INTEGER IDENTITY | PK |
| RULE_NAME | VARCHAR(100) | NOT NULL |
| ORDER_ID | VARCHAR(50) | |
| CLIENT_ID | VARCHAR(20) | |
| SYMBOL | VARCHAR(10) | |
| RESULT | VARCHAR(20) | |
| SURVEILLANCE_FLAGS | VARCHAR(200) | |
| EVALUATION_TIME | TIMESTAMP | |
| DETAILS | VARCHAR(500) | |

### DM-011: POSITION_TRACKING
| Column | Type | Constraints |
|--------|------|-------------|
| CLIENT_ID | VARCHAR(20) | PK (composite) |
| SYMBOL | VARCHAR(10) | PK (composite) |
| NET_POSITION | INTEGER | DEFAULT 0 |
| LAST_UPDATED | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |

### DM-012: RISK_ASSESSMENTS
| Column | Type | Constraints |
|--------|------|-------------|
| RISK_ORDER_ID | VARCHAR(50) | PK |
| SOURCE_ORDER_ID | VARCHAR(50) | |
| CLIENT_ID | VARCHAR(20) | |
| SYMBOL | VARCHAR(10) | |
| QUANTITY | INTEGER | |
| SIDE | VARCHAR(4) | |
| PRICE | DECIMAL(15,4) | |
| NOTIONAL_VALUE | DECIMAL(20,4) | |
| EXPOSURE_CONTRIBUTION | DECIMAL(20,4) | |
| VAR_CONTRIBUTION | DECIMAL(20,4) | |
| RISK_STATUS | VARCHAR(20) | |
| ASSESSMENT_DATE | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |

### DM-013: REG_REPORT_LOG
| Column | Type | Constraints |
|--------|------|-------------|
| LOG_ID | INTEGER IDENTITY | PK |
| REPORT_TYPE | VARCHAR(50) | NOT NULL |
| FILE_PATH | VARCHAR(500) | |
| RECORD_COUNT | INTEGER | DEFAULT 0 |
| STATUS | VARCHAR(50) | |
| GENERATION_TIME | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |

### Domain Objects (Java)

**DM-OBJ-001: TradeOrder** (common-lib) - serialVersionUID=100L. Statuses: NEW, VALIDATED (unused), PRICED (unused), REJECTED, FILLED, SETTLED, PENDING_REVIEW (unused, JIRA-2341), CANCELLED. Sides: BUY, SELL.

**DM-OBJ-002: Client** (common-lib) - serialVersionUID=101L. Tiers: PLATINUM, GOLD, SILVER, BRONZE. Default tier=BRONZE, default maxOrderValue=100000.0.

**DM-OBJ-003: Notification** (common-lib) - serialVersionUID=102L. Channels: EMAIL, SMS, FAX (deprecated). Types: ORDER_CONFIRM, ORDER_REJECT, SETTLEMENT, PRICE_ALERT. Statuses: PENDING, SENT, FAILED.

**DM-OBJ-004: SettlementRecord** (common-lib) - serialVersionUID=103L. Statuses: PENDING, GENERATED, UPLOADED, CONFIRMED, FAILED, RECONCILED, DISCREPANCY.

**DM-OBJ-005: AuditEvent** (common-lib) - serialVersionUID=105L.

**DM-OBJ-006: DerivativeOrder** (derivatives-engine) - Separate from TradeOrder. Contract types: FX_SPOT, FX_FORWARD, OPTION_CALL, OPTION_PUT. Statuses: NEW, FILLED, REJECTED.

**DM-OBJ-007: RiskOrder** (risk-engine) - Separate from TradeOrder. Risk statuses: PENDING, ASSESSED, FLAGGED, ERROR.

**DM-OBJ-008: RuleContext** (common-lib) - Wraps TradeOrder + Client. Has key-value `attributes` map, messages list, warnings list, rejected flag + rejectionReason.

---

## 4. Integration Specifications

### INT-JMS: JMS Queues (ActiveMQ)

| Queue Name | Producer | Consumer | Description |
|------------|----------|----------|-------------|
| BIGCORP.TRADE.ORDERS | trade-desk (OrderEntryServlet, FrontControllerServlet), DemoRunner | OrderMessageListener (order-engine) | Inbound trade orders as XML |
| BIGCORP.TRADE.CONFIRMATIONS | OrderMessageListener (order-engine) | AuditListener (audit-service), settlement-gateway | Order status updates (filled/rejected) |
| BIGCORP.NOTIFICATIONS | OrderMessageListener, BatchProcessor (settlement) | NotificationListener (notification-gateway) | Email/SMS notification requests |
| BIGCORP.SETTLEMENT.EVENTS | (cancelled project) | (unknown) | Created for a cancelled project; removing breaks settlement |
| BIGCORP.DERIVATIVES.ORDERS | (derivatives clients) | derivatives-engine | Inbound derivative orders |
| BIGCORP.DERIVATIVES.CONFIRMS | derivatives-engine | (downstream) | Derivative order confirmations |
| BIGCORP.DERIVATIVES.PRICING | (pricing feed) | derivatives-engine | Derivative pricing data |
| RISK.ORDERS.INBOUND | (order-engine or manual) | RiskScheduler (risk-engine) | Orders for risk assessment |
| RISK.RESULTS.OUTBOUND | ExposureCalculator (risk-engine) | (downstream) | Risk assessment results |

**INT-JMS-001**: Default broker URL: `vm://localhost?broker.persistent=false` (embedded). Production: `tcp://localhost:61616` (standalone ActiveMQ via mq.properties).

**INT-JMS-002**: All JMS sessions use AUTO_ACKNOWLEDGE mode.

### INT-SOAP: SOAP Web Services

**INT-SOAP-001**: Pricing Service endpoint: `http://localhost:8080/pricing-service/services/PricingService`

**INT-SOAP-002**: SOAP envelope uses `xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"` and `xmlns:pric="http://bigcorp.com/pricing"`.

**INT-SOAP-003**: Operations: GetQuote (single symbol), BatchQuote (multiple symbols).

**INT-SOAP-004**: Response parsing extracts `<price>` element text. Warnings logged if element not found or response malformed.

### INT-SFTP: SFTP Integration

**INT-SFTP-001**: SFTP config from `settlement.properties`: host=localhost, port=2222, username=bigcorp_settle, password=settle_pass.

**INT-SFTP-002**: Upload directory: `/incoming/` (sftp.remote.dir). Inbound polling directory: `/outgoing/` (sftp.remote.inbound.dir).

**INT-SFTP-003**: Local directories: output=`./sftp-outbound/`, inbound poll=`./sftp-root/inbound/`, processed=`./sftp-root/processed/`, fallback=`./sftp-root/outbound/`.

**INT-SFTP-004**: Upload retries on failure. If all retries fail, falls back to local file copy.

### INT-SMTP: Email

**INT-SMTP-001**: SMTP config from `notification.properties`: host, port (default 25), from address (default noreply@bigcorp.com). No authentication.

**INT-SMTP-002**: Production relay: `smtp-internal.bigcorp.com` (unreliable during 5:00-5:30 PM EST market close).

**INT-SMTP-003**: Support email: helpdesk@bigcorp.com ext. 4357 (displayed in web UI footer).

### INT-DB: Database

**INT-DB-001**: Development: HSQLDB in-memory (`jdbc:hsqldb:mem:bigcorpdb`, driver: `org.hsqldb.jdbc.JDBCDriver`).

**INT-DB-002**: Production: Oracle (detected via `DatabaseProductName`; DDL skipped, schema managed by DBA via `sql/schema-oracle.sql`).

**INT-DB-003**: If `db.properties` not found, defaults to HSQLDB in-memory.

**INT-DB-004**: Oracle connection properties loaded from `db.properties`.

---

## 5. Business Rules Catalog

| ID | Rule Name | Priority | Category | Behavior | Condition | Threshold/Formula |
|----|-----------|----------|----------|----------|-----------|-------------------|
| RUL-001 | LayeringDetectionRule | 125 | Surveillance | FLAG | count(non-CANCELLED orders for same client+symbol) > threshold | threshold=5 orders |
| RUL-002 | SpoofingPatternRule | 124 | Surveillance | FLAG | cancelledOrders/totalOrders > threshold | threshold=0.60 (60%) |
| RUL-003 | PositionLimitRule | 123 | Surveillance | REJECT | abs(currentPosition +/- orderQty) > limit | limit=100,000 shares |
| RUL-004 | MarketHaltRule | 120 | Circuit Breaker | REJECT | system property bigcorp.market.halted == "true" | N/A |
| RUL-005 | ClientKillSwitchRule | 118 | Circuit Breaker | REJECT | CLIENTS.KILL_SWITCH == 'Y' | N/A |
| RUL-006 | KYCStatusRule | 115 | Compliance | REJECT | CLIENTS.KYC_STATUS != 'APPROVED' | Allowed: APPROVED only |
| RUL-007 | DailyVolumeLimitRule | 110 | Compliance | REJECT | order.quantity > maxShares | maxShares=50,000 |
| RUL-008 | WashTradeDetectionRule | 105 | Compliance | REJECT | opposite-side order same client+symbol within window | window=5 minutes |
| RUL-009 | MaxOrderValueRule | 100 | Business | REJECT | qty * requestedPrice > client.maxOrderValue * buffer | buffer=1.10 (10%) |
| RUL-010 | RestrictedSymbolRule | 95 | Business | REJECT | symbol in restricted list | ENRN, WCOM, TYCO, ADLP |
| RUL-011 | ClientTierRule | 90 | Business | REJECT/PASS | client.active == false -> REJECT; else PASS | Sets priority attribute |
| RUL-012 | MarketHoursRule | 80 | Business | PASS (queue) | Outside 9:30-16:00 or weekend | Uses server local time (bug) |
| RUL-013 | ShortSaleRule | 75 | Business | REJECT | side==SELL && quantity > threshold | threshold=1,000 shares |
| RUL-014 | MultiCurrencyRule | 60 | Business | PASS | Sets FX rate context attributes | EUR=1.10, GBP=1.55, JPY=0.009, CHF=0.72 |
| RUL-015 | VolumeDiscountRule | 55 | Pricing | PASS | qty > 10000 -> 50% discount; qty > 5000 -> 25% discount | Discounts on commission |
| RUL-016 | SpecialClientsRule | 50 | Business | PASS | Per-client overrides (see Section 8) | See Section 8 |
| RUL-017 | LoyaltyBonusRule | 45 | Pricing | PASS | C001, C002, C003 -> 10% additional discount | Hardcoded client list |

**Edge Cases**:
- RUL-001/002: Surveillance rules flag but do NOT reject -- compliance reviews manually.
- RUL-003: Position limit fails open on DB error (allows trade).
- RUL-004: SecurityManager errors reading system property default to "not halted".
- RUL-005: Missing KILL_SWITCH column defaults to 'N' (allow).
- RUL-006: Missing KYC record treated as PENDING (reject).
- RUL-008: Wash trade DB check failure allows the trade (fail open).
- RUL-012: Uses server local time, not Eastern (known bug from 2001).
- RUL-013: Added manually, not yet in rules.xml (JIRA-4101).
- All surveillance/DB-dependent rules fail open on database errors.

---

## 6. Financial Calculations

### Commission Rates (CommissionCalculator)

| Tier | Rate | Percentage |
|------|------|-----------|
| PLATINUM | 0.005 | 0.5% |
| GOLD | 0.010 | 1.0% |
| SILVER | 0.015 | 1.5% |
| BRONZE | 0.020 | 2.0% |
| DEFAULT (null tier) | 0.020 | 2.0% |

**Formula**: `commission = orderValue * getRate(clientTier)` where `orderValue = quantity * price`.

### Commission Rate Discrepancies (Known, "By Design")
- CommissionCalculator (common-lib, used by order-engine and settlement): tier-based rates above
- PricingServiceImpl (pricing-service): flat 0.015 (1.5%) for all tiers
- DerivativeProcessor (derivatives-engine): flat 0.015 (1.5%) for FX/derivatives
- VolumeDiscountRule: uses 0.02 (2%) as BASE_COMMISSION (copy-pasted, JIRA-6001)
- MultiCurrencyRule: uses 0.02 (2%) as COMMISSION_RATE (copy-pasted, JIRA-7103)

### Volume Discounts (VolumeDiscountRule)
| Quantity | Discount |
|----------|---------|
| > 10,000 shares | 50% off commission |
| > 5,000 shares | 25% off commission |

### Loyalty Bonus (LoyaltyBonusRule)
Clients C001, C002, C003: additional 10% commission discount (hardcoded tenure check, JIRA-6002).

### Pricing Spreads (PricingServiceImpl)
| Tier | Spread |
|------|--------|
| PLATINUM | 0.001 (0.1%) |
| GOLD | 0.002 (0.2%) |
| SILVER | 0.003 (0.3%) |
| BRONZE/DEFAULT | 0.005 (0.5%) |

### FX Rates (hardcoded, duplicated in MultiCurrencyRule, FxPricingHelper, DerivativeProcessor)
| Pair | Rate |
|------|------|
| EUR/USD | 1.10 |
| GBP/USD | 1.55 |
| JPY/USD | 0.009 |
| CHF/USD | 0.72 |
| AUD/USD | 0.68 (FxPricingHelper only) |
| USD/USD | 1.0 |

FX bid/ask spread: 0.002 (0.2%). Bid = mid * (1 - 0.001), Ask = mid * (1 + 0.001).

### VaR Calculation (ExposureCalculator)
```
VaR = |notional| * vol * 2.33 * sqrt(1/252)
```
VaR flag threshold: 50,000.0

### Derivatives Premium
- FX_SPOT / FX_FORWARD: `notional * 0.015`
- OPTION_CALL / OPTION_PUT: `notional * 0.05` (flat 5%, placeholder)
- Max notional per derivative order: 10,000,000.0

---

## 7. State Machines

### TradeOrder Status Machine
```
NEW --> FILLED        (rule engine passes + price OK + manual checks pass)
NEW --> REJECTED      (any rule fails / price unavailable / price deviation > 10% / client not found / client inactive)
FILLED --> SETTLED    (settlement batch processes the order)
SETTLED --> RECONCILED    (clearinghouse confirms via CONF status)
SETTLED --> DISCREPANCY   (clearinghouse returns REJC or DISC status)
```
**Unused statuses defined but never set**: VALIDATED, PRICED, PENDING_REVIEW (JIRA-2341), CANCELLED (only checked, never programmatically set in the order lifecycle).

### Notification Status Machine
```
PENDING --> SENT      (dispatch successful)
PENDING --> PENDING   (dispatch failed, retryCount < 3, re-queued)
PENDING --> FAILED    (dispatch failed, retryCount >= 3)
```

### SettlementRecord Status Machine
```
PENDING --> GENERATED    (settlement file generated)
GENERATED --> UPLOADED   (SFTP upload successful)
UPLOADED --> RECONCILED  (clearinghouse CONF)
UPLOADED --> DISCREPANCY (clearinghouse REJC or DISC)
UPLOADED --> CONFIRMED   (clearinghouse XML CONFIRMED)
PENDING --> FAILED       (processing error)
```

### DerivativeOrder Status Machine
```
NEW --> FILLED      (validation passes, premium computed)
NEW --> REJECTED    (validation fails: missing fields, invalid type, notional > 10M)
```

### RiskOrder Status Machine
```
PENDING --> ASSESSED   (VaR <= 50,000)
PENDING --> FLAGGED    (VaR > 50,000)
PENDING --> ERROR      (assessment exception)
```

---

## 8. Special Client Arrangements

| Client ID | Name | Tier | Max Order Value | Override | Details |
|-----------|------|------|----------------|----------|---------|
| C001 | Acme Trading LLC | GOLD | 500,000 | Early market access | Can trade 10 min before market open. Per email from VP of Sales, 2000-03-15. Loyalty bonus eligible (10%). |
| C002 | Henderson Capital | PLATINUM | 5,000,000 | Zero commission | commission_override=0.0. No commission on first 1000 shares/day. Negotiated by Jim in sales, 1999. Loyalty bonus eligible (10%). |
| C003 | Smith & Associates | SILVER | 250,000 | GOLD-tier commission | commission_override=0.01 (1.0%). Per email from VP Sales 2009-03-10. Loyalty bonus eligible (10%). |
| C004 | MegaFund Inc | GOLD | 1,000,000 | PLATINUM pricing | pricing_tier_override=PLATINUM. Per verbal agreement with CEO, "do not change without checking with Larry." |
| C005 | Pinnacle Investments | BRONZE | 100,000 | 50% commission discount | commission_override=0.01 (50% off BRONZE 2%). JIRA-3401, implemented after 10 years. |
| C006 | Global Macro Fund | PLATINUM | 10,000,000 | Zero commission + early access | commission_override=0.0, early_access=true. CEO directive 2009-06-01. |
| C007 | Velocity Trading LLC | GOLD | 2,000,000 | PLATINUM pricing | pricing_tier_override=PLATINUM. Sales promised PLATINUM rates. |
| C008 | Falcon Trading Group | (unknown) | (unknown) | 75% commission discount | commission_override=0.005. Per email from Sales Director, 2014-01-15. |
| C009 | Apex Capital | (SILVER implied) | (unknown) | GOLD pricing | pricing_tier_override=GOLD. Copy-pasted from C004 logic, JIRA-7202. |
| C010 | Sterling Investments | (unknown) | (unknown) | Zero commission + FX priority | commission_override=0.0, multi_currency_priority=true. Added same day as multi-currency feature. |

**Notes**:
- Only C001-C007 are seeded in the sample data. C008-C010 have SpecialClientsRule logic but no sample CLIENTS rows.
- SpecialClientsRule always passes (returns true from evaluate()). Overrides are applied in execute() by setting context attributes.
- JIRA-7200: TODO to move all special clients to database config. JIRA-7201: class is too long.

---

## 9. Known Bugs & Technical Debt

**BUG-001 (Priority Comparator)**: RuleEngine sorts rules in DESCENDING order by priority number (higher number = runs first). This is the opposite of what most developers expect. System property `bigcorp.rules.priority.fixed` can fix this but defaults to false for backward compatibility (JIRA-5300).

**BUG-002 (Inactive Rule Bug)**: Rules loaded from XML with `active="false"` still get their `execute()` called if they were already in the chain from a previous run. The `isActive()` flag is set on the XML element, not the Rule object. The XML config's `active` attribute is NOT applied to the Rule object (each Rule returns its own `isActive()`).

**BUG-003 (MarketHoursRule Timezone)**: MarketHoursRule uses server local time instead of Eastern Time. This caused a 6-month outage in 2001 when the server clock was wrong.

**BUG-004 (T+3 Settlement Date)**: `calculateSettlementDate()` adds 3 calendar days without skipping weekends or holidays (JIRA-2890). Clearinghouse recalculates on their end.

**BUG-005 (Non-Thread-Safe Singleton)**: RuleEngine.getInstance() is not thread-safe. Comment: "It's fine, the app server is single-threaded." (It isn't.)

**BUG-006 (Redundant Order Saves)**: Order is saved via both `saveOrder()` AND `updateOrderStatus()`. The second path exists because the settlement batch job reads from this update path.

**BUG-007 (Price Deviation Double-Check)**: Price deviation is checked both in the rule engine AND manually in OrderMessageListener (JIRA-2456). The manual check is kept because "it caught a bug once."

**BUG-008 (Notification INSERT-Only)**: NotificationListener does INSERT-only to the NOTIFICATIONS table. Retried notifications create duplicate rows rather than updating existing records.

**BUG-009 (Settlement Queue Phantom)**: BIGCORP.SETTLEMENT.EVENTS queue was created for a cancelled project but cannot be removed because "removing it breaks something in settlement (we don't know what)."

**BUG-010 (Commission Rate Inconsistency)**: PricingServiceImpl uses flat 0.015 rate while order-engine uses tier-based rates via CommissionCalculator. Documented as "by design" but possibly a bug.

**BUG-011 (Hardcoded Fallback Prices)**: PricingServiceImpl has hardcoded fallback prices from year 2000-2001 that are certainly stale. Was "TEMPORARY" -- still present. Unknown symbols return a default price (bid=10.00, ask=10.50, last=10.25) rather than failing (JIRA-1102).

**BUG-012 (FX Rates Duplicated)**: FX rates are copy-pasted in three places: MultiCurrencyRule, FxPricingHelper, DerivativeProcessor. All hardcoded "as of 2004-07-15" and AUD/USD only exists in FxPricingHelper. JIRA-7101: move to shared config.

**BUG-013 (Partial Batch Not Implemented)**: `partialBatchMode` flag exists in BatchProcessor but the logic was never completed (JIRA-2890). If batch fails midway, partial batches can occur with no rollback.

**BUG-014 (DailyVolumeTracker Table Unused)**: DAILY_VOLUME_TRACKER table was created for cumulative daily volume tracking but the DBA said joins would be "too slow for the hot path." Only used for batch reporting, not real-time.

**BUG-015 (Reconciliation Reason Code Not Stored)**: DAT reconciliation files include reason codes for rejections/discrepancies but SettlementRecord has no reasonCode field (JIRA-3522).

**BUG-016 (Volume Discount BASE_COMMISSION)**: VolumeDiscountRule uses hardcoded 0.02 instead of CommissionCalculator (JIRA-6001).

**BUG-017 (MultiCurrency Commission)**: MultiCurrencyRule uses hardcoded 0.02 commission instead of CommissionCalculator (JIRA-7103).

**BUG-018 (Loyalty Bonus Hardcoded)**: LoyaltyBonusRule hardcodes client tenure check for C001/C002/C003 rather than reading from database (JIRA-6002).

**BUG-019 (ShortSaleRule Not in XML Config)**: ShortSaleRule is added manually after config-loaded rules, not in rules.xml (JIRA-4101).

**BUG-020 (Weekend Orders Not Actually Queued)**: MarketHoursRule sets `queued=true` attribute but no downstream code actually defers execution -- the order proceeds normally.

---

## 10. Non-Functional Requirements

**NFR-001 (Error Isolation)**: Rule execution errors in `execute()` SHALL be logged but SHALL NOT cause order rejection. Notification dispatch failures SHALL NOT fail the order. Audit logging failures SHALL NOT prevent order processing.

**NFR-002 (Fail-Open Surveillance)**: All surveillance rules (LayeringDetection, SpoofingPattern, PositionLimit) SHALL fail open on database errors -- trading must never be blocked by surveillance infrastructure failure.

**NFR-003 (Retry Mechanism)**: Notification dispatch SHALL retry up to 3 times via re-queuing. SFTP upload SHALL retry on failure.

**NFR-004 (Polling Intervals)**: OrderMessageListener poll timeout: 5000ms. NotificationListener poll timeout: 5000ms. Error backoff: 2000ms (order engine), 1000ms (notification gateway).

**NFR-005 (Thread Safety)**: RuleEngine singleton is NOT thread-safe. This is a known limitation.

**NFR-006 (Serialization)**: Domain objects use Java Serializable with fixed serialVersionUIDs: TradeOrder=100L, Client=101L, Notification=102L, SettlementRecord=103L, AuditEvent=105L. These UIDs must NOT change (breaks MQ compatibility).

**NFR-007 (Database Portability)**: Schema uses Oracle-compatible SQL where possible. Development uses HSQLDB in-memory. Oracle detection via `DatabaseProductName` skips DDL (DBA-managed schema).

**NFR-008 (Graceful Shutdown)**: All listeners (OrderMessageListener, NotificationListener, BatchScheduler) support graceful shutdown via boolean `running` flag and JVM shutdown hooks.

**NFR-009 (Connection Management)**: JMS connections are created per send operation in MessageQueueHelper. DB connections are obtained per query and closed in finally blocks via `ConnectionHelper.closeQuietly()`.

**NFR-010 (Defensive Null Checks)**: All compliance-added rules include extensive defensive null checks on context, order, client, clientId. Regulatory export code checks every field for null before formatting.

**NFR-011 (Batch Processing)**: Settlement batch runs at 6 PM EST in production (cron). No rollback mechanism -- partial batches possible on failure. Batch sequence counter resets daily in production (not in code).

**NFR-012 (Logging)**: All logging is via `System.out.println` and `System.err.println`. No logging framework (log4j etc.) is used. Timestamps included in batch job output.

**NFR-013 (Web UI)**: Trade desk uses Servlet 2.3 + inline HTML generation (no JSP templating for most pages). Front Controller pattern with Command objects. AuthenticationFilter on `/app/*` path returns 401 for unauthorized requests.

**NFR-014 (XML Processing)**: XML marshalling/unmarshalling uses hand-rolled string manipulation (XmlHelper) and JAXP DocumentBuilder for SOAP. No JAXB, no third-party XML libraries beyond the JDK.
