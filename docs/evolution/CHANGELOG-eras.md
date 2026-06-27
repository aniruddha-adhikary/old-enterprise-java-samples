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
