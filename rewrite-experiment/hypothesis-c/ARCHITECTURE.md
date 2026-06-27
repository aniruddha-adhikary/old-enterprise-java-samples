# BigCorp Trade Order Management System - Modern Architecture

## System Overview

A complete modern rewrite of the BigCorp Trade Order Management System, originally built circa 2001-2002 as a J2EE application with EJBs, JMS messaging, JDBC persistence, and SOAP/SFTP integrations.

### Tech Stack

| Component | Original (2001) | Modern (2025) |
|-----------|----------------|---------------|
| Language | Java 1.3/1.4 (J2EE) | TypeScript 5.3 (Node.js) |
| API | Servlet + SOAP | Express REST API |
| Messaging | JMS / ActiveMQ | RabbitMQ (AMQP) with in-memory fallback |
| Database | Oracle 8i / HSQLDB | SQLite (dev) / PostgreSQL (prod) |
| File Transfer | SFTP (JSch) | ssh2-sftp-client with local fallback |
| Email | JavaMail / SMTP | Nodemailer |
| Testing | (none found) | Jest + ts-jest |
| Build | Ant | npm + TypeScript compiler |
| Container | WebLogic / JBoss | Docker Compose |

### Why TypeScript?

The domain is fundamentally about data transformation and business rules - not high-throughput computation. TypeScript provides:
- Strong typing without Java's ceremony
- First-class JSON handling for REST APIs
- Excellent test tooling (Jest)
- Modern async patterns replacing JMS listeners
- Easy deployment (Docker, serverless)

---

## Analysis Process

### 1. Joern Static Analysis

Joern CPG (Code Property Graph) outputs were pre-generated in `joern-workspace/output/`. Key files analyzed:

#### `03-business-rules.txt` (3392 lines)
**Query**: CPG traversal of all classes implementing the Rule interface, extracting method bodies, conditional branches, and string constants.

**Revealed**: All 17 business rules with their exact priorities, thresholds, and evaluation logic:
- Circuit breaker rules (MarketHalt priority=120, ClientKillSwitch priority=118) added post-2011 flash crash
- Surveillance rules (Layering priority=125, Spoofing priority=124, PositionLimit priority=123) added post-2015 SEC inquiry
- Compliance rules (KYC priority=115, DailyVolume priority=110, WashTrade priority=105) added post-2005 regulatory incident
- Original business rules (MaxOrderValue priority=100, RestrictedSymbol priority=95, ClientTier priority=90, MarketHours priority=80, ShortSale priority=75)
- Multi-currency and discount rules (MultiCurrency priority=60, VolumeDiscount priority=55, SpecialClients priority=50, LoyaltyBonus priority=45)

**Critical finding**: The rule priority comparator sorts **descending** (higher number runs first). This is documented as a bug (JIRA-5300) but never fixed because "changing it might break things."

#### `06-commission-and-pricing.txt` (3430 lines)
**Query**: Data flow analysis tracing all numeric constants through commission and pricing calculation methods.

**Revealed**:
- Two separate commission rate systems that don't agree:
  - `CommissionCalculator`: PLATINUM 0.5%, GOLD 1%, SILVER 1.5%, BRONZE 2%
  - `PricingServiceImpl`: flat 1.5% rate (comment says "intentional")
- Spread adjustments by tier: PLATINUM ±0.1%, GOLD ±0.2%, SILVER ±0.3%, BRONZE ±0.5%
- Hardcoded "temporary" fallback prices for 7 stock symbols (MSFT, IBM, ORCL, SUNW, CSCO, INTC, DELL) - added March 2000, last updated November 2001, still in use
- VaR formula: `notional * volatility * 2.33 * sqrt(holdingDays / 252)`

#### `07-integration-topology.txt` (3368 lines)
**Query**: String constant extraction for queue names, URL patterns, connection parameters, and protocol identifiers.

**Revealed**: 9 JMS queues across 3 modules:
- Order engine: `BIGCORP.TRADE.ORDERS`, `BIGCORP.TRADE.CONFIRMATIONS`, `BIGCORP.NOTIFICATIONS`
- Settlement: `BIGCORP.SETTLEMENT.EVENTS` (created for a cancelled project, can't be removed)
- Derivatives: `BIGCORP.DERIVATIVES.ORDERS`, `BIGCORP.DERIVATIVES.CONFIRMS`, `BIGCORP.DERIVATIVES.PRICING`
- Risk: `RISK.ORDERS.INBOUND`, `RISK.RESULTS.OUTBOUND`

Also revealed SFTP configuration for clearinghouse uploads (30s timeout, 1 retry, Sunday maintenance blackout).

#### `12-context-attributes.txt` (3319 lines)
**Query**: SET-but-never-READ analysis of RuleContext attributes across all rule implementations.

**Revealed dead code attributes** (SET but never consumed by downstream logic):
- `commission_override` - set by SpecialClientsRule but never read by pricing
- `loyalty_bonus` - set by LoyaltyBonusRule but never applied to commission
- `queued` - set by MarketHoursRule but no queue processor reads it
- `priority` - set by ClientTierRule but no dispatcher uses it
- `spoofing_status` - set by SpoofingPatternRule, logged but not acted on
- `position_status` - set by PositionLimitRule, logged but not acted on
- `layering_status` - set by LayeringDetectionRule, logged but not acted on
- `settlement_currency` - set by MultiCurrencyRule but settlement always uses USD
- `fx_rate_applied` - set but never used in actual price conversion
- Plus ~20 more listed in lines 327-369 of the analysis output

### 2. Graphify Analysis

Graphify built a knowledge graph of the codebase (1393 nodes, 3613 edges, 101 communities).

**Key findings from GRAPH_REPORT.md**:
- 81% EXTRACTED (high confidence), 19% INFERRED relationships
- Community clustering clearly separated the 8 modules (order-engine, settlement-gateway, etc.)
- Cross-module dependencies primarily through `common-lib` shared types
- Circular dependency between `settlement-gateway` and `common-lib` for SettlementRecord

---

## Module Architecture

### Domain Layer (`src/domain/`)
Pure TypeScript interfaces and enums representing business entities:
- `TradeOrder` with full lifecycle state machine: NEW → VALIDATED → PRICED → FILLED/REJECTED → SETTLED
- `Client` with tier-based classification (PLATINUM/GOLD/SILVER/BRONZE)
- `SettlementRecord`, `Notification`, `AuditEvent`, `BillingEntry`
- `RiskAssessment` with VaR tracking
- `DerivativeOrder` supporting FX_SPOT, FX_FORWARD, OPTION_CALL, OPTION_PUT

### Rule Engine (`src/rules/`)
Priority-based chain of responsibility pattern, preserving exact behavior:
- 17 rules with exact priority values and thresholds from the original
- Rule evaluation short-circuits on rejection
- Context carries attributes, messages, and warnings through the chain
- Priority sort bug preserved (JIRA-5300): higher number runs first by default

### Financial Calculations (`src/pricing/`, `src/risk/`, `src/derivatives/`)
Exact formulas preserved:
- Commission: tier-based (0.5% to 2%) and flat 1.5% (PricingService)
- VaR: `notional * vol * 2.33 * sqrt(1/252)`, threshold $50K
- Derivatives: FX commission 1.5%, options premium 5%, max notional $10M
- Hardcoded FX rates: EUR 1.10, GBP 1.55, JPY 0.009, CHF 0.72

### Settlement (`src/settlement/`)
Batch processing with file generation:
- T+3 settlement date calculation (weekend bug preserved)
- XML and fixed-width flat file generation for clearinghouse
- SFTP upload with local fallback for dev mode

### Integration (`src/integration/`)
Modern equivalents for legacy protocols:
- JMS → RabbitMQ (AMQP) with in-memory broker for testing
- SFTP → ssh2-sftp-client with local fallback
- All 9 original queue names preserved

---

## Known Bugs (Preserved for Behavioral Compatibility)

| # | Bug | Source | JIRA |
|---|-----|--------|------|
| 1 | Rule priority comparator reversed (descending) | RuleEngine.java | JIRA-5300 |
| 2 | Inactive rules still evaluated when toggled via XML | RuleEngine.java | - |
| 3 | Market hours uses server local time, not Eastern | MarketHoursRule.java:748 | - |
| 4 | Settlement T+3 fails on weekends (no skip logic) | BatchProcessor.java:438-443 | JIRA-2890 |
| 5 | SMS phone formatting strips '+' prefix | SMSDispatcher.java:213-217 | Ticket #4521 |
| 6 | PricingService uses 1.5% vs CommissionCalculator's tier rates | PricingServiceImpl.java | - |
| 7 | Wash trade check per-order, not cumulative | WashTradeDetectionRule.java | - |
| 8 | Status PENDING_REVIEW defined but never used | TradeOrder.java:28 | JIRA-2341 |
| 9 | Loyalty bonus hardcoded for C001-C003 only | LoyaltyBonusRule.java | - |
| 10 | FX rates duplicated between MultiCurrencyRule and DerivativeProcessor | Multiple files | - |
| 11 | Hardcoded fallback prices since 2000 ("TEMPORARY") | PricingServiceImpl.java:176-221 | - |
| 12 | MaxOrderValue buffer of 1.10 added for Henderson (JIRA-1892) | MaxOrderValueRule.java | JIRA-1892 |

---

## Dead Code Analysis (from Joern 12-context-attributes.txt)

Attributes that are SET by rules but never READ by any downstream processor:

```
commission_override    loyalty_bonus         queued
priority              spoofing_status        position_status
layering_status       settlement_currency    fx_rate_applied
early_access          multi_currency_priority volume_discount_applied
volume_discount       compliance_flags       short_sale_commission
restricted_check      kill_switch_checked    market_halt_checked
kyc_status            daily_volume_checked   wash_trade_checked
layering_checked      spoofing_checked       layering_order_count
spoofing_cancel_rate  current_position       position_limit_checked
surveillance_flags    pricing_tier_override
```

These represent ~29 context attributes that accumulate data during rule evaluation but are never consumed. They exist because the original architecture planned for a "context-aware pricing engine" that was never built.

---

## Special Client Configuration

Extracted from hardcoded `SpecialClientsRule.java` into configurable overrides:

| Client | Override | Notes |
|--------|----------|-------|
| C001 (Acme) | Early access | Legacy deal |
| C002 (Henderson) | Zero commission, PLATINUM tier, 1000 free/day | JIRA-1892 |
| C003 (Smith) | GOLD rate (1%), commission 0.01 | |
| C004 (MegaFund) | PLATINUM tier, commission 0.01 | |
| C005 (Pinnacle) | 50% discount, commission 0.005 | |
| C006 (Global Macro) | Zero commission + early access | |
| C007 (Velocity) | PLATINUM tier override | |
| C008 (Falcon) | 75% discount | |
| C009 (Apex) | GOLD tier override | |
| C010 (Sterling) | Zero commission + FX priority | |
