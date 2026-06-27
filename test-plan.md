# BigCorp End-to-End Test Plan

## Overview
Comprehensive test to verify the full trade order lifecycle across all 7 modules.
Infrastructure: embedded HSQLDB + ActiveMQ + local SFTP fallback.

---

## Phase 1: Build & Infrastructure

### T1.1 — Clean build from scratch
```
ant clean clean-all
ant deps
ant compile
```
**Verify:** BUILD SUCCESSFUL, all 8 compile targets pass (common, tradedesk, orderengine, pricing, notifications, settlement, audit, demo)

### T1.2 — Package artifacts
```
ant package
```
**Verify:** `dist/bigcorp-common.jar`, `dist/trade-desk.war`, `dist/pricing-service.war` exist

### T1.3 — Database bootstrap
**Verify:**
- 7 tables created (CLIENTS, TRADE_ORDERS, NOTIFICATIONS, SETTLEMENT_RECORDS, AUDIT_LOG, PRICING_CACHE, BILLING_LEDGER)
- 5 clients inserted (C001–C005)
- 7 pricing records inserted (MSFT, IBM, ORCL, SUNW, CSCO, INTC, DELL)

---

## Phase 2: Happy Path — Standard Order Flow

### T2.1 — Submit BUY order (C001, MSFT, 500 shares @ $25.75)
**Flow:** trade-desk -> MQ -> order-engine -> pricing -> DB -> notification
**Verify:**
- TradeMessageProducer marshals XML and sends to BIGCORP.TRADE.ORDERS queue
- OrderMessageListener picks up message, unmarshals TradeOrder
- Client C001 (Acme Trading, GOLD tier) looked up from DB
- 4 rules evaluated in order: MaxOrderValue(100), ClientTier(90), MarketHours(80), SpecialClients(50)
- SpecialClients sets `early_access=true` for C001
- PricingServiceClient calls SOAP (fails), falls back to DB cache, returns $25.75
- Order status updated to FILLED
- Notification sent to BIGCORP.NOTIFICATIONS queue
- EmailDispatcher logs email in dev mode (recipient: trading@acme.com)
- Notification saved to NOTIFICATIONS table with status SENT
- Confirmation sent to BIGCORP.TRADE.CONFIRMATIONS queue

### T2.2 — Submit BUY order for different client (C002, IBM, 100 shares)
**Verify:**
- Henderson Capital (PLATINUM tier) gets zero commission override via SpecialClientsRule
- IBM price $120.50 from DB cache
- Order FILLED

### T2.3 — Submit SELL order (C003, ORCL, 200 shares)
**Verify:**
- Smith & Associates (SILVER tier) processes normally
- ORCL price ~$15.50 from DB cache
- Order FILLED

---

## Phase 3: Rule Engine Edge Cases

### T3.1 — MaxOrderValue rejection
**Setup:** C005 (Pinnacle, BRONZE, max=$100,000)
**Action:** Submit order with value > $110,000 (100k * 1.10 buffer)
**Example:** 5000 shares @ $25 = $125,000
**Verify:** Order REJECTED with reason containing "exceeds max allowed"

### T3.2 — MaxOrderValue with Henderson buffer
**Setup:** C002 (Henderson, PLATINUM, max=$5,000,000)
**Action:** Submit order with value between $5M and $5.5M (within 10% buffer)
**Verify:** Order FILLS (buffer applies to all clients, not just Henderson)

### T3.3 — Invalid client ID
**Action:** Submit order with clientId "C999"
**Verify:** Order REJECTED with "Client not found"

### T3.4 — Price deviation check
**Action:** Submit order with requestedPrice very different from DB price
**Example:** MSFT requested at $50 (DB has $25.75, deviation > 10%)
**Verify:** Order REJECTED with "Price deviation exceeds" message

### T3.5 — Unknown symbol
**Action:** Submit order for symbol "AAPL" (not in PRICING_CACHE)
**Verify:** PricingServiceClient returns fallback/0, order REJECTED with "Price unavailable"

---

## Phase 4: Settlement Batch Processing

### T4.1 — Settlement with filled orders
**Pre-condition:** Orders from Phase 2 are FILLED in DB
**Action:** Run BatchProcessor.processBatch()
**Verify:**
- Finds all FILLED orders
- Creates SETTLEMENT_RECORDS with correct amounts (qty * price)
- Commission = tier-based rate (PLATINUM 0.5%, GOLD 1.0%, SILVER 1.5%, BRONZE 2.0%)
- Settlement date = trade date + 3 days (doesn't skip weekends — known bug)
- Generates XML file in ./sftp-outbound/
- Generates flat file (.dat) in ./sftp-outbound/
- SFTP upload falls back to local copy (./sftp-root/outbound/)
- Order status updated to SETTLED
- Settlement notification sent to BIGCORP.NOTIFICATIONS queue

### T4.2 — Settlement file format validation
**Verify XML file:**
- Root element `<settlementBatch>`
- Header with batchId, date, recordCount
- Detail records with orderId, amount, commission
- Valid XML (parseable)

**Verify flat file:**
- Header record starts with "H"
- Detail records start with "D"
- Trailer record starts with "T"
- Column widths match spec (orderId=20, symbol=10, etc.)
- Trailer totals match sum of detail records

### T4.3 — Empty batch
**Pre-condition:** No FILLED orders in DB (all already settled)
**Action:** Run BatchProcessor.processBatch() again
**Verify:** "No orders to process. Batch complete." — no files generated

---

## Phase 4b: Audit & Billing Verification

### T4b.1 — AUDIT_LOG populated after order processing
**Verify:** AUDIT_LOG table has entries with correct EVENT_TYPE (ORDER_FILLED, ORDER_REJECTED)

### T4b.2 — BILLING_LEDGER populated for filled orders
**Verify:**
- BILLING_LEDGER has entries for each filled order
- GROSS_AMOUNT = quantity * price
- COMMISSION_AMOUNT = GROSS_AMOUNT * tier-based rate
- NET_AMOUNT = GROSS_AMOUNT + COMMISSION_AMOUNT
- STATUS = 'CHARGED'

### T4b.3 — Commission tier rates correct
**Verify:**
- PLATINUM client (C002) gets 0.5% commission
- GOLD client (C001) gets 1.0% commission
- SILVER client (C003) gets 1.5% commission
- BRONZE client (C005) gets 2.0% commission

### T4b.4 — AuditEvent XML round-trip
**Action:** Create AuditEvent, marshal to XML, unmarshal back
**Verify:** All fields preserved (eventType, sourceSystem, entityType, entityId, userId)

---

## Phase 5: Notification System

### T5.1 — Email template substitution (ORDER_CONFIRM)
**Verify:** Template placeholders replaced:
- `${orderId}` -> actual order ID
- `${symbol}` -> stock symbol
- `${quantity}` -> share count
- `${side}` -> BUY/SELL
- `${price}` -> execution price
- `${clientName}` -> recipient email

### T5.2 — Email template substitution (ORDER_REJECT)
**Verify:** Rejection template used with `${reason}` populated

### T5.3 — Notification DB persistence
**Verify:** NOTIFICATIONS table has records with:
- NOTIFICATION_TYPE = ORDER_CONFIRM or ORDER_REJECT
- CHANNEL = EMAIL
- STATUS = SENT (in dev mode)
- ORDER_ID matches the trade order

---

## Phase 6: XML Marshalling/Unmarshalling Round-Trip

### T6.1 — XmlHelper round-trip
**Action:** Create TradeOrder, marshal to XML, unmarshal back
**Verify:** All fields preserved (orderId, clientId, symbol, quantity, side, price, status)

### T6.2 — StringXmlBuilder output
**Action:** Create TradeOrder, build XML via StringXmlBuilder
**Verify:** Valid XML string produced (note: does NOT escape special chars — known bug)

---

## Phase 7: Pricing Service

### T7.1 — Direct PricingServiceImpl test
**Action:** Call PricingServiceImpl.getQuote("MSFT")
**Verify:** Returns PriceQuote with bid=$25.50, ask=$25.75, last=$25.63

### T7.2 — Batch quotes
**Action:** Call PricingServiceImpl.getBatchQuotes(["MSFT", "IBM", "DELL"])
**Verify:** Returns 3 PriceQuote objects with correct prices

### T7.3 — Unknown symbol fallback
**Action:** Call PricingServiceImpl.getQuote("AAPL")
**Verify:** Returns hardcoded fallback or null (depending on implementation)

---

## Phase 8: Multi-Order Stress Scenario

### T8.1 — Submit 5 orders from different clients for different symbols
**Orders:**
1. C001, BUY 500 MSFT @ $25.75
2. C002, SELL 100 IBM @ $120.00
3. C003, BUY 1000 ORCL @ $15.50
4. C004, BUY 200 DELL @ $35.00
5. C005, BUY 300 CSCO @ $22.00

**Verify:**
- All 5 orders process through the pipeline
- Each gets a notification
- Settlement batch processes all 5
- Settlement files contain all 5 records
- Flat file trailer totals are correct

---

## Phase 9: Integration Verification

### T9.1 — Existing demo runner
```
ant run-demo
```
**Verify:** Clean run with:
- BUILD SUCCESSFUL
- Order FILLED
- Email logged
- Settlement files generated
- No unexpected errors (except SOAP connection refused — expected)

---

## Phase 10: Config-Driven Rule Loading

### T10.1 — RuleConfigLoader parses rules.xml from classpath
**Action:** Call RuleConfigLoader.loadRules()
**Verify:** Returns non-empty list of Rule objects

### T10.2 — Config loads exactly 4 expected rule types
**Action:** Call RuleConfigLoader.loadRules() and check types
**Verify:**
- List contains exactly 4 rules
- MaxOrderValueRule, ClientTierRule, MarketHoursRule, SpecialClientsRule all present

**Known issue / JIRA-4102:** Now fails because Wave 2 added RestrictedSymbolRule to rules.xml (count is now 5). Test assertion needs updating but no time to refactor test infrastructure.

### T10.3 — Config-loaded rules have correct priorities
**Action:** Call RuleConfigLoader.loadRules() and check priorities
**Verify:**
- MaxOrderValue priority = 100
- ClientTier priority = 90
- MarketHours priority = 80
- SpecialClients priority = 50

### T10.4 — Rule engine evaluates correctly with config-loaded rules
**Action:** Evaluate a valid order through the config-loaded rule engine
**Verify:** Order passes all rules (same behavior as hardcoded)

---

## Phase 11: Per-Symbol Trading Restrictions

### T11.1 — RestrictedSymbolRule rejects restricted symbol
**Action:** Create order with symbol "ENRN" and evaluate via RestrictedSymbolRule
**Verify:** Rule returns false, context is rejected with reason containing "Restricted symbol"

### T11.2 — ShortSaleRule passes for small SELL
**Action:** Create SELL order with 100 shares and evaluate via ShortSaleRule
**Verify:** Rule returns true, context is not rejected

### T11.3 — restricted_check attribute set on passing order
**Action:** Create order with non-restricted symbol (MSFT), evaluate via RestrictedSymbolRule
**Verify:** `restricted_check` attribute is set to "passed" on the RuleContext

---

## Phase 12: Derivatives Engine (FX/Options — contractor module)

### T12.1 — DerivativeOrder XML round-trip
**Action:** Create DerivativeOrder with all fields, marshal to XML via toXml(), unmarshal via fromXml()
**Verify:** All fields preserved (orderId, clientId, contractType, underlying, strikePrice, quantity, expiry, status)

### T12.2 — DerivativeProcessor processes FX_SPOT order
**Action:** Create FX_SPOT order (EUR/USD, 10000 qty, strike 1.10), call processOrder()
**Verify:**
- Status = FILLED
- Premium = 10000 * 1.10 * 0.015 = 165.0 (FX commission rate)

### T12.3 — FxPricingHelper returns expected rates
**Action:** Call FxPricingHelper.getRate() for EUR/USD, GBP/USD, JPY/USD, and unknown pair
**Verify:**
- EUR/USD = 1.10
- GBP/USD = 1.55
- JPY/USD = 0.009
- Unknown pair returns -1.0

---

## Phase 13: Compliance Rules (post-2005 regulatory incident)

### T13.1 — DailyVolumeLimitRule passes for normal-sized order
**Action:** Create order with 1,000 shares, evaluate via DailyVolumeLimitRule
**Verify:** Rule returns true, context is not rejected (< 50,000 share limit)

### T13.2 — DailyVolumeLimitRule rejects oversized order
**Action:** Create order with 60,000 shares, evaluate via DailyVolumeLimitRule
**Verify:** Rule returns false, context rejected with reason containing "REG-2005-001"

### T13.3 — KYCStatusRule passes for approved client
**Action:** Create order for client C001 (KYC_STATUS='APPROVED'), evaluate via KYCStatusRule
**Verify:** Rule returns true, context attribute `kyc_status` is set

### T13.4 — WashTradeDetectionRule passes when no wash trade pattern exists
**Action:** Create BUY order for C001/INTC, evaluate via WashTradeDetectionRule
**Verify:** Rule returns true, no wash trade detected (no recent opposite-side order)

### T13.5 — Compliance context attributes set after all three rules
**Action:** Run DailyVolumeLimitRule, WashTradeDetectionRule, and KYCStatusRule on a valid order
**Verify:**
- `daily_volume_checked` = Boolean.TRUE
- `compliance_flags` = "VOLUME_CHECKED"
- `wash_trade_checked` = Boolean.TRUE
- `kyc_status` is not null

**Known issue / JIRA-5200:** T2.3 (SELL ORCL) intermittently fails with status=NEW due to race condition — compliance rule DB queries (KYCStatus, WashTradeDetection) add latency that exceeds the 5-second wait in submitAndVerify. T4.5 fails as a consequence (order never reached FILLED so can't be SETTLED).

**Known issue / JIRA-5201:** T10.4 now fails because the config-loaded RuleEngine includes compliance rules (KYCStatus, DailyVolumeLimit, WashTradeDetection) which reject the test order — the test creates a TradeOrder without setting clientId on the order object, so WashTradeDetectionRule rejects it with "Client ID is null".

**Known issue / JIRA-4102:** T10.2 fails because it expects 4 rules in config but Wave 2 added RestrictedSymbolRule and Wave 4 added 3 compliance rules (now 8 total).

---

## Phase 14: Wave 5 — Typed RuleResult, Priority Flag, Commission Cleanup

### T14.1 — RuleResult round-trip
**Action:** Create a RuleResult with passed=true, ruleName, message, and attributes
**Verify:** All fields are retrievable and match what was set

### T14.2 — TypedRule interface works when implemented by a test rule
**Action:** Create a TypedRule implementation, register with RuleEngine, evaluate
**Verify:**
- Engine calls evaluateTyped() (not legacy evaluate())
- RuleResult attributes are applied to context via applyToContext()
- execute() is still called after passing

### T14.3 — Priority flag defaults to old behavior (descending = high runs first)
**Action:** Clear system property, add rules with priority 10 and 100, evaluate
**Verify:** Rule with priority 100 runs first (descending order preserved by default)

### T14.4 — Setting bigcorp.rules.priority.fixed=true changes ordering
**Action:** Set system property bigcorp.rules.priority.fixed=true, add rules with priority 10 and 100
**Verify:** Rule with priority 10 runs first (ascending = correct behavior)

### T14.5 — ShortSaleRule uses CommissionCalculator (no hardcoded commission)
**Action:** Create SELL order for GOLD client (500 shares), evaluate via ShortSaleRule
**Verify:** Stashed short_sale_commission matches CommissionCalculator.getRate("GOLD") * 500 = 5.0 (not 10.0 from old hardcoded 0.02)

---

## Phase 15: Wave 6 — Volume Discounts, Loyalty Bonus, Special-Client Sprawl

### T15.1 — SpecialClientsRule handles C005 (Pinnacle commission override)
**Action:** Create order for C005 (Pinnacle, BRONZE), evaluate via SpecialClientsRule
**Verify:** commission_override = 0.01 (50% off BRONZE 2% rate — JIRA-3401 finally resolved)

### T15.2 — VolumeDiscountRule sets volume_discount for large orders
**Action:** Create order with qty=7500, evaluate via VolumeDiscountRule
**Verify:**
- volume_discount = 0.25 (25% off for qty > 5000)
- volume_discount_applied = Boolean.TRUE

### T15.3 — LoyaltyBonusRule sets loyalty_bonus for loyal clients
**Action:** Create order for C001 (loyal client since 1999), evaluate via LoyaltyBonusRule
**Verify:** loyalty_bonus = 0.10 (10% additional discount)

### T15.4 — Priority interaction bug (JIRA-6003)
**Action:** Simulate reversed priority ordering — VolumeDiscount(55) runs before SpecialClients(50). Both set commission-related attributes for C005.
**Verify:**
- VolumeDiscountRule sets volume_discount = 0.25
- SpecialClientsRule sets commission_override = 0.01
- Both attributes coexist but don't compose — no rule combines them
- Known issue: JIRA-6003 — VolumeDiscountRule's discount is overwritten by SpecialClientsRule due to evaluation order. The volume_discount attribute is set but ignored because commission_override takes precedence downstream.

**Known issue / JIRA-6003:** VolumeDiscountRule and SpecialClientsRule overlap on commission attributes due to priority ordering. VolumeDiscount (priority 55) runs BEFORE SpecialClients (priority 50) in reversed comparator mode, but SpecialClients overwrites the commission_override. The two rules don't compose their discounts.

**Known issue / JIRA-4102:** T10.2 count now 10 (was 8, was 5, was 4) — VolumeDiscountRule and LoyaltyBonusRule added to config.

---

## Execution Approach

All tests will be implemented as a single `EndToEndTest.java` harness that:
1. Boots up infrastructure (HSQLDB + ActiveMQ)
2. Starts order-engine, notification-gateway, and audit-service listeners
3. Runs test scenarios sequentially
4. Asserts expected outcomes via DB queries (incl. AUDIT_LOG and BILLING_LEDGER)
5. Validates generated files
6. Reports pass/fail for each test case
7. Exits cleanly
