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

## Execution Approach

All tests will be implemented as a single `EndToEndTest.java` harness that:
1. Boots up infrastructure (HSQLDB + ActiveMQ)
2. Starts order-engine, notification-gateway, and audit-service listeners
3. Runs test scenarios sequentially
4. Asserts expected outcomes via DB queries (incl. AUDIT_LOG and BILLING_LEDGER)
5. Validates generated files
6. Reports pass/fail for each test case
7. Exits cleanly
