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
