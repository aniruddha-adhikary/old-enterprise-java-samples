# BigCorp Trading System — Evolution Simulation

## Overview

This simulation models how a legacy trading system's business rules and modules organically grow more complex and messy over ~10-12 years (circa 2000-2012). Each "wave" represents a different era and is implemented by an agent assigned a specific persona (engineer archetype). Some waves make good architectural choices; others accumulate tech debt deliberately.

## Wave Model

Each wave represents a time period (roughly 1-2 years) and is attributed to a persona that defines the engineering style. Waves are executed **strictly in order** — each wave builds on the codebase left by all previous waves.

### Personas

| Persona | Style | Config |
|---------|-------|--------|
| **architect** | Externalizes config, adds interfaces, writes tests, refactors duplication | `.agents/personas/architect.md` |
| **feature-rusher** | Copy-pastes constants, hardcodes client IDs, adds string-keyed context attributes, leaves TODO/JIRA comments, swallows exceptions | `.agents/personas/feature-rusher.md` |
| **contractor** | Adds a whole new module with its own conventions that don't match existing ones | `.agents/personas/contractor.md` |
| **compliance-bolt-on** | Adds regulatory rules reactively after an "incident", with defensive redundant checks | `.agents/personas/compliance-bolt-on.md` |

### Wave Sequence

| Wave | Era | Persona | Summary |
|------|-----|---------|---------|
| 0 | 2002 Q3 | architect | Set up simulation framework (this document) |
| 1 | 2003 Q1 | architect | Externalize rule configuration from hardcoded initRules() to XML/properties in config/real/ |
| 2 | 2003 Q4 | feature-rusher | Per-symbol trading restrictions (RestrictedSymbolRule, ShortSaleRule) |
| 3 | 2004 Q3 | contractor | New asset class: FX/options module (derivatives-engine/) |
| 4 | 2005 Q2 | compliance-bolt-on | Regulatory limits after an incident (DailyVolumeLimitRule, WashTradeDetectionRule, KYCStatusRule) |
| 5 | 2007 Q1 | architect | Pay down debt — centralize commission, typed RuleResult, fix priority comparator behind feature flag |
| 6 | 2009 Q2 | feature-rusher | Tiered/volume-based commission and special-client sprawl |
| 7 | 2011 Q4 | compliance-bolt-on | Audit everything / circuit breakers — market halt, kill switches, audit trail |

## Verification Gate

**Every wave MUST end with a verification gate.** This is non-negotiable.

### Gate procedure

1. Run `ant verify` (which runs `clean compile` plus the EndToEndTest suite)
2. If all tests pass: wave is complete
3. If tests fail:
   - **architect** persona: MUST fix failures before completing the wave
   - **feature-rusher** / **contractor** / **compliance-bolt-on** persona: MAY document failures as "Known issue / JIRA-XXXX" in `test-plan.md` and `docs/evolution/CHANGELOG-eras.md` — but the **build itself (compile) must always pass** so the next wave can start
4. The `verify` target is defined in `build.xml`

### Per-wave checklist (all items mandatory)

- [ ] (a) Add/extend tests in `test/src/com/bigcorp/test/EndToEndTest.java`
- [ ] (b) Append a new phase to `test-plan.md`
- [ ] (c) Append an era entry to `docs/evolution/CHANGELOG-eras.md`
- [ ] (d) Run the `verify` gate (`ant verify`)

## Expected Outcome

A git history that reads like a decade of incremental change — alternating clean refactors and hasty feature bolt-ons — with:

- A steadily growing rule set
- Multiple modules (including a siloed derivatives-engine)
- Accumulated string-keyed context attributes in RuleContext
- Duplicated constants across modules
- Persona-attributed CHANGELOG eras
- An EndToEndTest / test-plan that grows a new phase per wave
- Intentional, documented behavioral debt surfacing over time
- The codebase compiles at every wave boundary
