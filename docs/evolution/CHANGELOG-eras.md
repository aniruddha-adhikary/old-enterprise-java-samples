# BigCorp Trading System — Era Changelog

Each entry records one wave of the evolution simulation. See `SIMULATION.md` for the full model.

---

## 2002 Q3 — Simulation framework setup — engineer: architect

**Wave 0**

- Created `docs/evolution/SIMULATION.md` describing the wave model, personas, and verification gate
- Created persona profiles in `.agents/personas/` (architect, feature-rusher, contractor, compliance-bolt-on)
- Added `ant verify` target to `build.xml` (runs `clean compile` + EndToEndTest)
- Documented verification gate procedure: every wave must run `ant verify` and either pass or document known issues
- Baseline: 4 rules (MaxOrderValue, ClientTier, MarketHours, SpecialClients), 7 modules, 44 passing tests
- Known bugs carried forward: RuleEngine reversed-priority comparator, isActive() XML flag bug

---

## 2003 Q1 — Externalized rule configuration — engineer: architect

**Wave 1**

- Created RuleConfigLoader in common-lib to load rules from XML config
- Added config/rules.xml with the 4 existing rules and their priorities
- Modified OrderMessageListener.initRules() to prefer config-driven loading with hardcoded fallback
- Added test phase for config-driven rule loading
- Deliberately left the RuleEngine isActive() XML bug in place

---

## 2003 Q4 — Per-symbol trading restrictions — engineer: feature-rusher

**Wave 2**

- Added RestrictedSymbolRule with hardcoded restricted symbols (ENRN, WCOM, TYCO, ADLP) — TODO: move to database (JIRA-4100)
- Added ShortSaleRule with hardcoded 1000-share limit and copy-pasted commission constant
- RestrictedSymbolRule registered via XML config; ShortSaleRule hardcoded in initRules() (inconsistency — JIRA-4101)
- New string-keyed attributes: restricted_check, short_sale_commission
- Minimal test coverage (Phase 11: 3 tests)
- Known issue: T10.2 now fails (expects 4 rules in config, but RestrictedSymbolRule makes it 5) — JIRA-4102

---

## 2004 Q3 — New asset class: FX/options module — engineer: contractor

**Wave 3**

- Added derivatives-engine/ module with own package conventions (com.bigcorp.derivatives.*)
- DerivativeOrder: own model class, not extending TradeOrder, own XML marshalling
- DerivativeProcessor: own pricing logic, own commission rate (0.015 vs 0.02), does NOT use RuleEngine
- FxPricingHelper: hardcoded FX rates, copy-paste of pricing logic
- Own queue constants (BIGCORP.DERIVATIVES.ORDERS/CONFIRMS), own logger utility
- New compile target in build.xml (compile-derivatives)
- Minimal integration with common-lib (ConnectionHelper only)

---

## 2005 Q2 — Regulatory limits after incident — engineer: compliance-bolt-on

**Wave 4**

- Added DailyVolumeLimitRule (priority 110): per-order 50k share limit (REG-2005-001)
- Added WashTradeDetectionRule (priority 105): checks for same-client opposite-side patterns (REG-2005-002)
- Added KYCStatusRule (priority 115): verifies client KYC status before trading
- Added DAILY_VOLUME_TRACKER table and KYC_STATUS column to CLIENTS
- Added redundant manual volume check in OrderMessageListener (belt-and-suspenders, REG-2005-003)
- New context attributes: compliance_flags, wash_trade_checked, kyc_status, daily_volume_checked
- Known issue / JIRA-5200: T2.3 intermittently fails (race condition — compliance DB queries add latency)
- Known issue / JIRA-5201: T10.4 fails (compliance rules reject test order missing clientId on TradeOrder)
- Known issue / JIRA-4102: T10.2 count now 8 (was 5, was 4)

---

## 2007 Q1 — Pay down debt — engineer: architect

**Wave 5**

- Fixed RuleEngine priority comparator behind feature flag (bigcorp.rules.priority.fixed system property, defaults false for backward compat) — JIRA-5300
- Introduced typed RuleResult class for structured rule output (replaces ad-hoc string-keyed attributes)
- Added TypedRule interface extending Rule, with evaluateTyped() method
- RuleEngine now supports both TypedRule and legacy Rule implementations
- Fixed ShortSaleRule copy-pasted commission constant to use CommissionCalculator (JIRA-2501 cleanup)
- Could not fully clean up: derivatives-engine still has its own FX_COMMISSION, string-keyed attributes still widely used

---

## 2009 Q2 — Tiered commission and special-client sprawl — engineer: feature-rusher

**Wave 6**

- Extended SpecialClientsRule with C003, C005, C006, C007 hardcoded overrides (sprawl continues)
- Finally resolved JIRA-3401 (Pinnacle reduced fees) after 10 years
- Added C006 (Global Macro Fund) and C007 (Velocity Trading) to client data
- Added VolumeDiscountRule (priority 55) with copy-pasted commission constant
- Added LoyaltyBonusRule (priority 45) with hardcoded loyal client list
- Known bug: JIRA-6003 — VolumeDiscountRule and SpecialClientsRule overlap on commission attributes due to priority ordering
- New context attributes: volume_discount, volume_discount_applied, loyalty_bonus

---

## 2011 Q4 — Audit everything / circuit breakers — engineer: compliance-bolt-on

**Wave 7**

- Added MarketHaltRule (priority 120): system-wide circuit breaker via bigcorp.market.halted property (REG-2011-001)
- Added ClientKillSwitchRule (priority 118): per-client trading suspension via KILL_SWITCH column (REG-2011-002)
- Added RuleAuditLogger: logs every rule decision to RULE_AUDIT_LOG table (REG-2011-003)
- Modified RuleEngine.evaluate() to audit-log each rule result
- Added KILL_SWITCH column to CLIENTS, RULE_AUDIT_LOG table to DatabaseBootstrap
- Defensive: audit logging wrapped in try/catch to never block trading
- Rule count: 12 rules spanning 10 years of incremental additions
- Known issue / JIRA-4102: T10.2 count now 12 (was 10, was 8, was 5, was 4)

---

## 2013 Q2 — Reporting subsystem — engineer: contractor

**Wave 8**

- Added reporting-service/ module with own package conventions (com.bigcorp.reporting.*)
- ReportingDAO: duplicates JDBC boilerplate from common-lib (contractor pattern — own connection management)
- ReportTemplateEngine: string-concatenated HTML/CSV report templates (no template library)
- ReportGenerator: orchestrates daily P&L, monthly volume, billing, and settlement reports
- ReportLogger: own logging utility (does NOT use System.out or any shared logger)
- ReportConfig: own configuration holder with own date format (dd/MM/yyyy vs main app's yyyy-MM-dd)
- Added compile-reporting target in build.xml
- No integration with existing rules or order pipeline
- Known issue / JIRA-8100: report output directory not auto-created by generateDailyPnlReport()

---

## 2014 Q1 — Multi-currency and more special clients — engineer: feature-rusher

**Wave 9**

- Added MultiCurrencyRule (priority 60): hardcoded FX rates copy-pasted from derivatives-engine's FxPricingHelper
- Hardcoded rates: EUR/USD=1.10, GBP/USD=1.55, JPY/USD=0.009, CHF/USD=0.72 — TODO: move to DB (JIRA-7100)
- Extended SpecialClientsRule with C008 (Falcon Trading, 75% discount), C009 (Apex Capital, GOLD override), C010 (Sterling, zero commission + FX priority)
- New string-keyed context attributes: currency, fx_rate_applied, settlement_currency, multi_currency_priority
- TODO: this class is getting way too long (JIRA-7201)
- TODO: handle JPY properly (JIRA-7102)
- Known issue / JIRA-4102: T10.2 count now 16 (was 12)

---

## 2015 Q1 — Trade surveillance — engineer: compliance-bolt-on

**Wave 10**

- Added LayeringDetectionRule (priority 125): counts recent orders per client/symbol, flags if > 5 (REG-2015-001)
- Added SpoofingPatternRule (priority 124): calculates cancellation rate, flags if > 60% (REG-2015-002)
- Added PositionLimitRule (priority 123): rejects if net position exceeds 100k shares (REG-2015-003)
- Added SurveillanceAuditLogger: mirrors RuleAuditLogger but writes to SURVEILLANCE_AUDIT_LOG (REG-2015-004)
- Added SURVEILLANCE_AUDIT_LOG and POSITION_TRACKING tables to DatabaseBootstrap
- Added SURVEILLANCE_FLAGS column to TRADE_ORDERS table
- All surveillance rules fail open (catch exceptions, return true) except PositionLimitRule which rejects
- Redundant DB queries in SpoofingPatternRule (separate counts for total and cancelled)
- Rule count now 16 spanning 13 years

---

## 2016 Q1 — Pay down debt: DAO consolidation — engineer: architect

**Wave 11**

- Introduced BaseDAO abstract class in common-lib (com.bigcorp.common.dao) consolidating JDBC boilerplate
- Added DAOInterface with findById, findAll, save contract
- Template methods: queryList, querySingle, executeUpdate, countRows
- Migration status:
  - OrderDAO: candidate for migration (not migrated — too many callers depend on string-concat SQL)
  - AuditDAO: candidate for migration (not migrated — identity column insert pattern)
  - ReportingDAO: NOT migrated — contractor module with own connection management
  - SettlementDAO: NOT migrated — SFTP/file-writing interleaved with DB access
- All new code should use BaseDAO going forward

---

## 2017 Q1 — Risk engine module — engineer: contractor

**Wave 12**

- Added risk-engine/ module with own package conventions (com.bigcorp.risk.*)
- RiskOrder: own model class (not reusing TradeOrder), includes exposure and VaR fields
- ExposureCalculator: parametric VaR with hardcoded volatility (equity=20%, FX=8%, commodity=30%)
- RiskDAO: own connection management (does NOT use ConnectionHelper), own close() helpers
- RiskScheduler: polls TRADE_ORDERS for unassessed orders, runs risk calculations
- Own queue constants (RISK.ORDERS.INBOUND, RISK.RESULTS.OUTBOUND)
- Added RISK_ASSESSMENTS table to DatabaseBootstrap
- Added compile-risk target in build.xml
- No integration with RuleEngine or order pipeline

---

## 2019 Q1 — Client portal API — engineer: feature-rusher

**Wave 13**

- Added ClientPortalAPI in trade-desk module (com.bigcorp.tradedesk.api)
- Read API for client order status, balances, and JSON order lookup
- "Authentication" via hardcoded API keys (bigcorp-internal-2019, client-portal-key-2019)
- Copy-pasted query logic from OrderDAO and ReportingDAO
- Hand-built "JSON" output (no JSON library) — TODO: add proper JSON lib (JIRA-9104)
- TODO: add proper authentication (JIRA-9100), rate limiting (JIRA-9101), error handling (JIRA-9102)
- Swallows exceptions and returns empty results

---

## 2021 Q1 — Regulatory reporting (MiFID/CAT-style) — engineer: compliance-bolt-on

**Wave 14**

- Added RegulatoryExportJob in settlement-gateway (com.bigcorp.settlement.regulatory)
- Generates fixed-width (REG-FW-001) and XML (REG-XML-001) regulatory files
- Mirrors settlement-gateway file generation patterns (header/trailer records)
- Defensive null checks on every field ("if we submit a null, the regulator fines us")
- Added REG_REPORT_LOG table to track report generation (success/failure)
- Redundant validation layer: validateFixedWidthFile() checks HDR/TRL format
- Duplicate code between fixed-width and XML generators (both query same data)

---

## 2023 Q1 — Logging/observability cleanup — engineer: architect

**Wave 15**

- Introduced BigCorpLogger unified logging facade in common-lib (com.bigcorp.common.logging)
- Structured log format: timestamp [LEVEL] [module] className - message
- Log levels: DEBUG=0, INFO=1, WARN=2, ERROR=3 with global level control
- Auto-derives module name from class package
- Migration status:
  - common-lib/BaseDAO: migrated (uses BigCorpLogger)
  - common-lib/RuleEngine: NOT migrated (callers depend on exact output format)
  - order-engine: NOT migrated (risk of breaking MQ error handling)
  - derivatives-engine: NOT migrated (contractor module, tightly coupled DerivativeLogger)
  - reporting-service: NOT migrated (contractor module, uses ReportLogger)
  - settlement-gateway/RegulatoryExportJob: partially migrated
  - trade-desk: NOT migrated (JSP/servlet layer)
- Remaining inconsistencies documented in class javadoc

---

**Wave 16**

- Mainframe back-office integration via JCA/CCI resource adapter (Apex Consulting contractor engagement, JIRA-7200)
- New top-level module `connector/` with hand-rolled CCI-style API:
  - MainframeConnectionFactory, MainframeConnection, MainframeInteraction (CCI surface)
  - ResourceAdapterConfig reads from `connector/config/ra.properties` (vendor's own config convention, NOT from config/real/)
  - ConnectorException (modeled after javax.resource.ResourceException)
- AccountRecord value object deliberately overlaps with existing Client model (JIRA-7201 — vendor insists mainframe COBOL copybook is "source of truth")
- MainframeAccountService facade for order-engine integration
- Remote-with-DB-fallback pattern: tries TCP connect to CICS host, falls back to CLIENTS table via JDBC (same shape as PricingServiceClient)
- Bolt-on integration in OrderMessageListener.processOrder(): after existing isActive() check, calls mainframe account verification; rejects if EIS reports SUSPENDED/CLOSED; tightens credit limit if mainframe limit is lower (JIRA-7202)
- Existing DB-based client checks remain in place (dual validation, "belt and suspenders" per vendor recommendation)
- Build: compile-connector target, rar-connector produces dist/connector.rar (JAR + META-INF/ra.xml descriptor)
- Known issues:
  - JIRA-7203: AccountRecord/Client model duplication should be unified — but mainframe team won't agree to a shared schema
  - JIRA-7204: connector uses its own config directory (connector/config/) instead of config/real — vendor refused to follow project conventions

---
