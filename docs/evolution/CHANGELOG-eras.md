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

**Wave 7 (FINAL)**

- Added MarketHaltRule (priority 120): system-wide circuit breaker via bigcorp.market.halted property (REG-2011-001)
- Added ClientKillSwitchRule (priority 118): per-client trading suspension via KILL_SWITCH column (REG-2011-002)
- Added RuleAuditLogger: logs every rule decision to RULE_AUDIT_LOG table (REG-2011-003)
- Modified RuleEngine.evaluate() to audit-log each rule result
- Added KILL_SWITCH column to CLIENTS, RULE_AUDIT_LOG table to DatabaseBootstrap
- Defensive: audit logging wrapped in try/catch to never block trading
- Final rule count: 13 rules spanning 10 years of incremental additions
- Known issue / JIRA-4102: T10.2 count now 12 (was 10, was 8, was 5, was 4)

---
