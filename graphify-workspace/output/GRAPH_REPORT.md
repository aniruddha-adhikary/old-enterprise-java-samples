# Graph Report - old-enterprise-java-samples  (2026-06-27)

## Corpus Check
- 137 files · ~705,883 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1393 nodes · 3613 edges · 101 communities (67 shown, 34 thin omitted)
- Extraction: 81% EXTRACTED · 19% INFERRED · 0% AMBIGUOUS · INFERRED: 683 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `781ac879`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 61|Community 61]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 63|Community 63]]
- [[_COMMUNITY_Community 64|Community 64]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 66|Community 66]]
- [[_COMMUNITY_Community 67|Community 67]]
- [[_COMMUNITY_Community 68|Community 68]]
- [[_COMMUNITY_Community 69|Community 69]]
- [[_COMMUNITY_Community 70|Community 70]]
- [[_COMMUNITY_Community 71|Community 71]]
- [[_COMMUNITY_Community 72|Community 72]]
- [[_COMMUNITY_Community 73|Community 73]]
- [[_COMMUNITY_Community 74|Community 74]]
- [[_COMMUNITY_Community 75|Community 75]]
- [[_COMMUNITY_Community 76|Community 76]]
- [[_COMMUNITY_Community 77|Community 77]]
- [[_COMMUNITY_Community 78|Community 78]]
- [[_COMMUNITY_Community 79|Community 79]]
- [[_COMMUNITY_Community 80|Community 80]]
- [[_COMMUNITY_Community 81|Community 81]]
- [[_COMMUNITY_Community 82|Community 82]]
- [[_COMMUNITY_Community 83|Community 83]]
- [[_COMMUNITY_Community 84|Community 84]]
- [[_COMMUNITY_Community 85|Community 85]]
- [[_COMMUNITY_Community 86|Community 86]]
- [[_COMMUNITY_Community 87|Community 87]]
- [[_COMMUNITY_Community 88|Community 88]]
- [[_COMMUNITY_Community 89|Community 89]]
- [[_COMMUNITY_Community 90|Community 90]]
- [[_COMMUNITY_Community 91|Community 91]]
- [[_COMMUNITY_Community 92|Community 92]]
- [[_COMMUNITY_Community 93|Community 93]]
- [[_COMMUNITY_Community 94|Community 94]]
- [[_COMMUNITY_Community 95|Community 95]]
- [[_COMMUNITY_Community 96|Community 96]]
- [[_COMMUNITY_Community 97|Community 97]]
- [[_COMMUNITY_Community 98|Community 98]]
- [[_COMMUNITY_Community 99|Community 99]]
- [[_COMMUNITY_Community 100|Community 100]]

## God Nodes (most connected - your core abstractions)
1. `TradeOrder` - 89 edges
2. `RuleContext` - 88 edges
3. `Client` - 53 edges
4. `Notification` - 50 edges
5. `SettlementRecord` - 45 edges
6. `Rule` - 45 edges
7. `EndToEndTest` - 43 edges
8. `SettlementTransferObject` - 39 edges
9. `RiskOrder` - 38 edges
10. `OrderTransferObject` - 33 edges

## Surprising Connections (you probably didn't know these)
- `EndToEndTest` --references--> `AuditListener`  [EXTRACTED]
  test/src/com/bigcorp/test/EndToEndTest.java → audit-service/src/com/bigcorp/audit/consumer/AuditListener.java
- `SimpleTestRule` --implements--> `Rule`  [EXTRACTED]
  test/src/com/bigcorp/test/EndToEndTest.java → common-lib/src/com/bigcorp/common/rules/Rule.java
- `OrderMessageListener` --references--> `RuleEngine`  [EXTRACTED]
  order-engine/src/com/bigcorp/orderengine/consumer/OrderMessageListener.java → common-lib/src/com/bigcorp/common/rules/RuleEngine.java
- `EndToEndTest` --references--> `NotificationListener`  [EXTRACTED]
  test/src/com/bigcorp/test/EndToEndTest.java → notification-gateway/src/com/bigcorp/notifications/consumer/NotificationListener.java
- `EndToEndTest` --references--> `OrderMessageListener`  [EXTRACTED]
  test/src/com/bigcorp/test/EndToEndTest.java → order-engine/src/com/bigcorp/orderengine/consumer/OrderMessageListener.java

## Import Cycles
- None detected.

## Communities (101 total, 34 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.06
Nodes (14): ReportConfig, Connection, OrderDAO, ReportingDAO, ConnectionHelper, TransferObjectAssembler, ReportGenerator, File (+6 more)

### Community 1 - "Community 1"
Cohesion: 0.05
Nodes (9): ViewOrderStatusCommand, ConnectionFactory, OrderMessageListener, OrderServiceDelegate, MessageConsumer, TradeOrder, MessageQueueHelper, Session (+1 more)

### Community 2 - "Community 2"
Cohesion: 0.07
Nodes (12): Class, FxPricingHelper, Date, DocumentBuilderFactory, BigCorpLogger, Serializable, SimpleDateFormat, ReportTemplateEngine (+4 more)

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (4): RiskDAO, ExposureCalculator, RiskOrder, RiskScheduler

### Community 4 - "Community 4"
Cohesion: 0.09
Nodes (13): Command, CommandFactory, DashboardCommand, ListOrdersCommand, SubmitOrderCommand, UnknownCommand, FrontControllerServlet, Hashtable (+5 more)

### Community 5 - "Community 5"
Cohesion: 0.09
Nodes (6): BatchProcessor, BatchScheduler, DemoRunner, SettlementFileGenerator, SftpUploader, Timer

### Community 6 - "Community 6"
Cohesion: 0.08
Nodes (3): SettlementRecord, ReconciliationEntry, String

### Community 7 - "Community 7"
Cohesion: 0.13
Nodes (5): Document, PricingEndpointServlet, PriceQuote, PricingServiceImpl, OrderStatusServlet

### Community 10 - "Community 10"
Cohesion: 0.16
Nodes (4): AuditListener, AuditDAO, BillingDAO, AuditEvent

### Community 11 - "Community 11"
Cohesion: 0.12
Nodes (6): SettlementDAO, Element, DatReconciliationParser, ReconciliationProcessor, RuleConfigLoader, SftpPoller

### Community 15 - "Community 15"
Cohesion: 0.09
Nodes (4): MarketHoursRule, VolumeDiscountRule, Rule, TypedRule

### Community 16 - "Community 16"
Cohesion: 0.18
Nodes (4): EngineMain, DatabaseBootstrap, RuleAuditLogger, Thread

### Community 19 - "Community 19"
Cohesion: 0.22
Nodes (8): Filter, AuthenticationFilter, CharacterEncodingFilter, RequestLoggingFilter, FilterChain, FilterConfig, ServletRequest, ServletResponse

### Community 22 - "Community 22"
Cohesion: 0.14
Nodes (3): LayeringDetectionRule, RestrictedSymbolRule, RuleContext

### Community 23 - "Community 23"
Cohesion: 0.11
Nodes (18): 1. System Purpose, 2. Domain Model, 3. Business Rules, 4. Business Processes, 5. Financial Logic, 6. Integration Points, 7. Architectural Decisions, 8. Anomalies and Observations (+10 more)

### Community 24 - "Community 24"
Cohesion: 0.11
Nodes (18): Business Logic Reconstruction Methodology Using Joern, Overview, Phase 1: CPG Creation and Structural Survey, Phase 2: Domain Model Extraction, Phase 3: Business Rule Extraction, Phase 4: Data Flow Tracing, Phase 5: Architectural Pattern Recognition, Phase 6: Financial Logic Deep-Dive (+10 more)

### Community 25 - "Community 25"
Cohesion: 0.11
Nodes (17): 2002 Q3 — Simulation framework setup — engineer: architect, 2003 Q1 — Externalized rule configuration — engineer: architect, 2003 Q4 — Per-symbol trading restrictions — engineer: feature-rusher, 2004 Q3 — New asset class: FX/options module — engineer: contractor, 2005 Q2 — Regulatory limits after incident — engineer: compliance-bolt-on, 2007 Q1 — Pay down debt — engineer: architect, 2009 Q2 — Tiered commission and special-client sprawl — engineer: feature-rusher, 2011 Q4 — Audit everything / circuit breakers — engineer: compliance-bolt-on (+9 more)

### Community 26 - "Community 26"
Cohesion: 0.11
Nodes (18): ClientKillSwitch (priority 118), ClientTier (priority 90), DailyVolumeLimit (priority 110), Detailed Rule Specifications, KYCStatus (priority 115), LayeringDetection (priority 125), LoyaltyBonus (priority 45), MarketHalt (priority 120) (+10 more)

### Community 27 - "Community 27"
Cohesion: 0.20
Nodes (3): BaseDAO, DAOInterface, Object

### Community 28 - "Community 28"
Cohesion: 0.12
Nodes (15): Build Tool, Common Issues, Devin Secrets Needed, Evolution Simulation Verification, Infrastructure (for real mode testing), Key Assertions to Verify, Overview, Prerequisites (+7 more)

### Community 29 - "Community 29"
Cohesion: 0.20
Nodes (3): DispatcherMain, NotificationListener, SMSDispatcher

### Community 30 - "Community 30"
Cohesion: 0.13
Nodes (15): 2.10 SURVEILLANCE_AUDIT_LOG, 2.11 POSITION_TRACKING, 2.12 RISK_ASSESSMENTS, 2.13 REG_REPORT_LOG, 2.14 Seed Data, 2.1 CLIENTS, 2.2 TRADE_ORDERS, 2.3 NOTIFICATIONS (+7 more)

### Community 31 - "Community 31"
Cohesion: 0.13
Nodes (14): BigCorp End-to-End Test Plan, Execution Approach, Overview, Phase 6: XML Marshalling/Unmarshalling Round-Trip, Phase 7: Pricing Service, Phase 8: Multi-Order Stress Scenario, Phase 9: Integration Verification, T6.1 — XmlHelper round-trip (+6 more)

### Community 33 - "Community 33"
Cohesion: 0.18
Nodes (3): DAOFactory, HsqldbDAOFactory, OracleDAOFactory

### Community 34 - "Community 34"
Cohesion: 0.15
Nodes (13): 5. Financial Logic, Billing Ledger (BILLING_LEDGER table), Commission Calculation, Commission Discrepancy: PricingService vs Order Engine, FX Conversion Rates (stale, hardcoded from 2014), Hardcoded Fallback Prices (stale since 2000-2001), Loyalty Bonus, Price Deviation Threshold (+5 more)

### Community 35 - "Community 35"
Cohesion: 0.17
Nodes (11): Detailed Findings, Evaluation: Agent v1 Reconstruction vs Actual Business Logic, Joern's CPG is Highly Effective For:, Methodology, Recommendations for Methodology Improvement, Scoring Summary, What the Agent Got PARTIALLY RIGHT, What the Agent Got RIGHT (impressive findings from code-only) (+3 more)

### Community 36 - "Community 36"
Cohesion: 0.17
Nodes (11): 1. System Purpose, 2. Domain Model, 3. Business Rules, Additional Manual Checks in OrderMessageListener, Execution Order (Descending Priority), Gaps: Business Logic NOT Determinable from Code Alone, Reconstructed Business Logic: BigCorp Trade Order Management System, Relationships (+3 more)

### Community 37 - "Community 37"
Cohesion: 0.17
Nodes (11): 10. Integration Architecture, 14. Gaps Remaining After V2 Analysis, 1. System Purpose, 6. Surveillance Flags — The Persistence Gap, 7. The Dead Attribute Problem, Context Attributes READ but Never SET (Missing Producers), Context Attributes SET but Never READ, JMS Queues (+3 more)

### Community 39 - "Community 39"
Cohesion: 0.18
Nodes (10): Architecture, BigCorp Trade Order Management System, Build Targets, Business Rules (Rule Engine), Commission Rates (by Client Tier), License, Modules, Prerequisites (+2 more)

### Community 40 - "Community 40"
Cohesion: 0.20
Nodes (9): BigCorp Trading System — Evolution Simulation, Expected Outcome, Gate procedure, Overview, Per-wave checklist (all items mandatory), Personas, Verification Gate, Wave Model (+1 more)

### Community 43 - "Community 43"
Cohesion: 0.22
Nodes (9): 7. Anomalies, Architectural Anomalies, Commission Rate Discrepancy, Dead Code, Duplicated Logic, Hardcoded Values That Should Be Configurable, Intentional/Known Bugs, JIRA References Found in Code (+1 more)

### Community 46 - "Community 46"
Cohesion: 0.25
Nodes (8): 5.1 Commission Calculation — The ACTUAL Flow, 5.2 Billing Ledger — NET_AMOUNT Calculation, 5.3 Where Billing is Created, 5.4 Commission in OrderMessageListener vs Settlement, 5. Financial Logic (Corrected and Expanded), V2 FINDING: commission_override is NOT applied either, V2 FINDING: Loyalty bonus is NOT applied to final commission, V2 FINDING: Volume discounts are NOT applied to final commission

### Community 49 - "Community 49"
Cohesion: 0.29
Nodes (7): 6. Integration Architecture, Database, Email / SMTP, HTTP/Web Entry Points, JMS Queues, SFTP Integration, SOAP Endpoints

### Community 58 - "Community 58"
Cohesion: 0.33
Nodes (6): 4. Business Processes, Notification Process, Order Lifecycle (Complete Flow), Pricing Flow, Reconciliation Process, Settlement Process (End-of-Day Batch)

### Community 59 - "Community 59"
Cohesion: 0.33
Nodes (6): 13. Answers to Investigation Questions, Q1: Are volume discounts actually applied to final commission?, Q2: Is the loyalty_bonus attribute consumed anywhere downstream?, Q3: What exactly does execute() do after evaluate() passes — trace for each rule, Q4: How does the billing ledger calculate NET_AMOUNT?, Q5: What happens to surveillance_flags after they're set?

### Community 60 - "Community 60"
Cohesion: 0.33
Nodes (6): 3.1 TradeOrder Status, 3.2 SettlementRecord Status, 3.3 Notification Status, 3.4 DerivativeOrder Status, 3.5 RiskOrder Status, 3. State Machines (Detailed)

### Community 61 - "Community 61"
Cohesion: 0.33
Nodes (5): Coding Style, Era Attribution, Identity, Persona: Architect, When Running the Verification Gate

### Community 62 - "Community 62"
Cohesion: 0.33
Nodes (5): Coding Style, Era Attribution, Identity, Persona: Compliance Bolt-On, When Running the Verification Gate

### Community 63 - "Community 63"
Cohesion: 0.33
Nodes (5): Coding Style, Era Attribution, Identity, Persona: Contractor, When Running the Verification Gate

### Community 64 - "Community 64"
Cohesion: 0.33
Nodes (5): Coding Style, Era Attribution, Identity, Persona: Feature Rusher, When Running the Verification Gate

### Community 65 - "Community 65"
Cohesion: 0.33
Nodes (6): Phase 13: Compliance Rules (post-2005 regulatory incident), T13.1 — DailyVolumeLimitRule passes for normal-sized order, T13.2 — DailyVolumeLimitRule rejects oversized order, T13.3 — KYCStatusRule passes for approved client, T13.4 — WashTradeDetectionRule passes when no wash trade pattern exists, T13.5 — Compliance context attributes set after all three rules

### Community 66 - "Community 66"
Cohesion: 0.33
Nodes (6): Phase 14: Wave 5 — Typed RuleResult, Priority Flag, Commission Cleanup, T14.1 — RuleResult round-trip, T14.2 — TypedRule interface works when implemented by a test rule, T14.3 — Priority flag defaults to old behavior (descending = high runs first), T14.4 — Setting bigcorp.rules.priority.fixed=true changes ordering, T14.5 — ShortSaleRule uses CommissionCalculator (no hardcoded commission)

### Community 67 - "Community 67"
Cohesion: 0.33
Nodes (6): Phase 16: Wave 7 — Audit Trail, Circuit Breakers, Kill Switches, T16.1 — MarketHaltRule passes when market is not halted (default), T16.2 — MarketHaltRule rejects when bigcorp.market.halted=true, T16.3 — ClientKillSwitchRule passes for normal client (KILL_SWITCH='N'), T16.4 — Verify RULE_AUDIT_LOG is populated after rule decision logging, T16.5 — Verify audit trail contains entries for each rule evaluated

### Community 68 - "Community 68"
Cohesion: 0.33
Nodes (6): Phase 19: Wave 10 — Trade Surveillance (compliance-bolt-on), T19.1 — LayeringDetectionRule passes for normal order count, T19.2 — SpoofingPatternRule passes for client with no cancellations, T19.3 — PositionLimitRule passes for order within limits, T19.4 — SURVEILLANCE_AUDIT_LOG table accepts entries, T19.5 — POSITION_TRACKING table exists

### Community 69 - "Community 69"
Cohesion: 0.33
Nodes (6): Phase 3: Rule Engine Edge Cases, T3.1 — MaxOrderValue rejection, T3.2 — MaxOrderValue with Henderson buffer, T3.3 — Invalid client ID, T3.4 — Price deviation check, T3.5 — Unknown symbol

### Community 73 - "Community 73"
Cohesion: 0.40
Nodes (5): Client, Core Entities, Notification, SettlementRecord, TradeOrder

### Community 74 - "Community 74"
Cohesion: 0.40
Nodes (5): 4.1 Execution Order (Descending Priority), 4.2 evaluate() vs execute() Flow Per Rule, 4.3 Detailed Rule Specifications, 4. Business Rules, Corrections to V1:

### Community 75 - "Community 75"
Cohesion: 0.40
Nodes (5): 9.1 Order Lifecycle (Complete Flow), 9.2 Audit/Billing Flow (Downstream of Order Fill), 9.3 Settlement Process, 9.4 Reconciliation Process, 9. Business Processes (Corrected)

### Community 76 - "Community 76"
Cohesion: 0.40
Nodes (5): Phase 10: Config-Driven Rule Loading, T10.1 — RuleConfigLoader parses rules.xml from classpath, T10.2 — Config loads exactly 4 expected rule types, T10.3 — Config-loaded rules have correct priorities, T10.4 — Rule engine evaluates correctly with config-loaded rules

### Community 77 - "Community 77"
Cohesion: 0.40
Nodes (5): Phase 15: Wave 6 — Volume Discounts, Loyalty Bonus, Special-Client Sprawl, T15.1 — SpecialClientsRule handles C005 (Pinnacle commission override), T15.2 — VolumeDiscountRule sets volume_discount for large orders, T15.3 — LoyaltyBonusRule sets loyalty_bonus for loyal clients, T15.4 — Priority interaction bug (JIRA-6003)

### Community 78 - "Community 78"
Cohesion: 0.40
Nodes (5): Phase 17: Wave 8 — Reporting Subsystem (contractor), T17.1 — ReportingDAO daily P&L query, T17.2 — ReportTemplateEngine HTML generation, T17.3 — ReportTemplateEngine CSV generation, T17.4 — ReportGenerator file output

### Community 79 - "Community 79"
Cohesion: 0.40
Nodes (5): Phase 18: Wave 9 — Multi-Currency & More Special Clients (feature-rusher), T18.1 — MultiCurrencyRule passes for USD order, T18.2 — MultiCurrencyRule sets EUR rate, T18.3 — SpecialClientsRule C008 commission override, T18.4 — Rule count after Wave 9 additions

### Community 80 - "Community 80"
Cohesion: 0.40
Nodes (5): Phase 20: Wave 11 — DAO Consolidation (architect), T20.1 — BaseDAO.queryList works for simple query, T20.2 — BaseDAO.querySingle with parameterized query, T20.3 — BaseDAO.countRows works, T20.4 — DAOInterface contract exists

### Community 81 - "Community 81"
Cohesion: 0.40
Nodes (5): Phase 21: Wave 12 — Risk Engine (contractor), T21.1 — ExposureCalculator computes correct notional and VaR, T21.2 — RiskOrder model round-trip, T21.3 — RISK_ASSESSMENTS table accepts data, T21.4 — RiskScheduler queue constants defined

### Community 82 - "Community 82"
Cohesion: 0.40
Nodes (5): Phase 22: Wave 13 — Client Portal API (feature-rusher), T22.1 — ClientPortalAPI authentication, T22.2 — ClientPortalAPI.getOrdersForClient, T22.3 — ClientPortalAPI.getClientBalance, T22.4 — ClientPortalAPI.getOrderStatusJson

### Community 83 - "Community 83"
Cohesion: 0.40
Nodes (5): Phase 23: Wave 14 — Regulatory Reporting (compliance-bolt-on), T23.1 — RegulatoryExportJob generates fixed-width file, T23.2 — Fixed-width file passes validation, T23.3 — RegulatoryExportJob generates XML report, T23.4 — REG_REPORT_LOG table exists

### Community 84 - "Community 84"
Cohesion: 0.40
Nodes (5): Phase 24: Wave 15 — Logging/Observability Cleanup (architect), T24.1 — BigCorpLogger instantiation and info logging, T24.2 — BigCorpLogger respects global log level, T24.3 — BigCorpLogger derives module name from package, T24.4 — BigCorpLogger log level constants

### Community 85 - "Community 85"
Cohesion: 0.40
Nodes (5): Phase 4b: Audit & Billing Verification, T4b.1 — AUDIT_LOG populated after order processing, T4b.2 — BILLING_LEDGER populated for filled orders, T4b.3 — Commission tier rates correct, T4b.4 — AuditEvent XML round-trip

### Community 86 - "Community 86"
Cohesion: 0.50
Nodes (4): 11.1 Critical Integration Gaps, 11.2 Known Bugs (Preserved), 11.3 Commission Rate Discrepancy, 11. Anomalies and Technical Debt

### Community 87 - "Community 87"
Cohesion: 0.50
Nodes (4): 12. Confidence Assessment, High Confidence (verified via source code reading + Joern CPG), Low Confidence (structural observation, limited verification), Medium Confidence (verified from source but could have alternative paths)

### Community 88 - "Community 88"
Cohesion: 0.50
Nodes (4): Phase 11: Per-Symbol Trading Restrictions, T11.1 — RestrictedSymbolRule rejects restricted symbol, T11.2 — ShortSaleRule passes for small SELL, T11.3 — restricted_check attribute set on passing order

### Community 89 - "Community 89"
Cohesion: 0.50
Nodes (4): Phase 12: Derivatives Engine (FX/Options — contractor module), T12.1 — DerivativeOrder XML round-trip, T12.2 — DerivativeProcessor processes FX_SPOT order, T12.3 — FxPricingHelper returns expected rates

### Community 90 - "Community 90"
Cohesion: 0.50
Nodes (4): Phase 1: Build & Infrastructure, T1.1 — Clean build from scratch, T1.2 — Package artifacts, T1.3 — Database bootstrap

### Community 91 - "Community 91"
Cohesion: 0.50
Nodes (4): Phase 2: Happy Path — Standard Order Flow, T2.1 — Submit BUY order (C001, MSFT, 500 shares @ $25.75), T2.2 — Submit BUY order for different client (C002, IBM, 100 shares), T2.3 — Submit SELL order (C003, ORCL, 200 shares)

### Community 92 - "Community 92"
Cohesion: 0.50
Nodes (4): Phase 4: Settlement Batch Processing, T4.1 — Settlement with filled orders, T4.2 — Settlement file format validation, T4.3 — Empty batch

### Community 93 - "Community 93"
Cohesion: 0.50
Nodes (4): Phase 5: Notification System, T5.1 — Email template substitution (ORDER_CONFIRM), T5.2 — Email template substitution (ORDER_REJECT), T5.3 — Notification DB persistence

### Community 95 - "Community 95"
Cohesion: 0.67
Nodes (3): 8. JIRA Traceability (Enriched), JIRA References Found In Source Code, Regulatory References

## Knowledge Gaps
- **307 isolated node(s):** `destroy-infra.sh script`, `load-schema.sh script`, `start-infra.sh script`, `stop-infra.sh script`, `run-all.sh script` (+302 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **34 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RuleContext` connect `Community 22` to `Community 0`, `Community 1`, `Community 6`, `Community 15`, `Community 17`, `Community 18`, `Community 20`, `Community 21`, `Community 27`, `Community 42`, `Community 44`, `Community 45`, `Community 47`, `Community 48`, `Community 52`, `Community 53`, `Community 54`, `Community 55`, `Community 56`, `Community 57`, `Community 71`, `Community 72`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Why does `TradeOrder` connect `Community 1` to `Community 0`, `Community 2`, `Community 4`, `Community 5`, `Community 6`, `Community 10`, `Community 12`, `Community 15`, `Community 17`, `Community 18`, `Community 20`, `Community 21`, `Community 22`, `Community 32`, `Community 42`, `Community 44`, `Community 53`, `Community 55`, `Community 57`, `Community 70`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `SettlementTransferObject` connect `Community 8` to `Community 2`, `Community 6`?**
  _High betweenness centrality (0.017) - this node is a cross-community bridge._
- **What connects `destroy-infra.sh script`, `load-schema.sh script`, `start-infra.sh script` to the rest of the system?**
  _307 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.06293706293706294 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.05153153153153153 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.06568832983927324 - nodes in this community are weakly interconnected._