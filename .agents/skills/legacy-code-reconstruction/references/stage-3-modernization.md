# Stage 3: Modern Rewrite

## Overview

Using the BRD.md + spec.json + GRAPH_REPORT.md from Stages 1-2, produce a complete modern codebase that preserves all business logic from the legacy system.

## Inputs

The builder agent receives these files and NOTHING ELSE (no source code access):

1. `BRD.md` — prose requirements with numbered FR-* IDs
2. `spec.json` — machine-readable specification
3. `GRAPH_REPORT.md` — Graphify community analysis (god nodes, cohesion scores)

The isolation is intentional. If the builder can reconstruct a working system from these documents alone, the extraction quality is proven.

## Architecture Decisions

### Tech Stack Selection

Choose based on domain and team context. In the worked example, independent agents converged on TypeScript/Express/PostgreSQL for a financial trading system — one data point, not a recommendation. Reasonable default mappings for enterprise Java rewrites:

| Legacy | Modern Equivalent |
|--------|------------------|
| Java 1.4 / J2EE | TypeScript, Kotlin, or Go |
| Ant | npm, Gradle, or Go modules |
| HSQLDB / Oracle | PostgreSQL |
| ActiveMQ (JMS) | RabbitMQ or Kafka |
| SOAP | REST or gRPC |
| SFTP (JSch) | Cloud storage or modern SFTP client |
| Raw JDBC | TypeORM, Prisma, or JOOQ |
| Servlets | Express, Spring Boot, or Gin |

### Community-Informed Decomposition

Use the Graphify GRAPH_REPORT.md to inform module boundaries:

1. **God nodes → core aggregates**: The entity with the most edges (e.g., TradeOrder with 89 edges) is your central domain object
2. **High-cohesion communities → preserve as modules**: Communities with cohesion > 0.15 have good internal consistency
3. **Low-cohesion communities → split**: Communities with cohesion < 0.10 mix concerns and should be decomposed
4. **Cross-community edges → integration points**: Connections between communities indicate where to define interfaces

### Bug Preservation vs Fix

For each known bug in spec.json:
- If `preserveForBackwardCompatibility: true` → preserve the behavior, document it
- If the bug is architectural (e.g., non-thread-safe singleton) and the modern platform eliminates the issue (e.g., Node.js is single-threaded) → mark as "Fixed by design"
- If fixing would change externally observable behavior → preserve and make configurable

## Output Structure

Create **one module per discovered community/module** from the profile + `GRAPH_REPORT.md`, plus cross-cutting infrastructure. The exact domain folders depend on the codebase — below, the domain modules are illustrative (from the worked example); the non-italic ones are structural and apply to most rewrites:

```
rewrite/
├── ARCHITECTURE.md          # Design decisions, tech stack, community-informed decomposition
├── src/
│   ├── domain/              # Entities from spec.json entities array
│   ├── rules/               # Rule engine + all rules from spec.json rules array
│   ├── <module-a>/          # one folder per discovered module (e.g. pricing, settlement, risk…)
│   ├── <module-b>/          #   named from the calibration profile, NOT a fixed list
│   ├── integration/         # Message broker, SFTP, SMTP, HTTP adapters (per discovered protocols)
│   ├── api/                 # REST/gRPC API (replaces servlets)
│   └── config/              # Environment-based configuration
├── tests/                   # Tests covering all rules, financials, state machines
├── schema.sql               # Database schema from spec.json entities
├── docker-compose.yml       # Infrastructure (DB, message broker, mail) — only what's actually used
└── package.json             # Build config
```

## ARCHITECTURE.md Requirements

The architecture document must:
1. Explain the tech stack choice and why
2. Map each legacy component to its modern equivalent
3. Document how Graphify communities informed module boundaries
4. List all known bugs with their disposition (preserved / fixed / fixed-by-design)
5. Show the integration mapping (legacy protocol → modern protocol)
6. Document dead code patterns from Joern attribute analysis

## Implementation Checklist (generic — instantiate against your spec.json)

### Rules (from spec.json rules array)
- [ ] Every rule implemented with its exact priority value
- [ ] Rule engine preserves the legacy evaluation pattern (often chain-of-responsibility)
- [ ] Default sort/evaluation order matches legacy exactly (document any non-obvious order, e.g. descending)
- [ ] Reject-vs-flag behavior preserved per rule (surveillance/monitoring rules typically flag, not reject)
- [ ] Failure semantics preserved (which failures stop the chain; which fail open vs closed)

### Financial Logic (from spec.json financials)
- [ ] Every rate/spread/tier value matches the legacy literal exactly
- [ ] Where two sources disagree by design, both preserved and the discrepancy documented
- [ ] Stale/hardcoded values preserved as-is if the legacy behavior depended on them
- [ ] Every formula uses the exact constants (z-scores, holding periods, day counts, thresholds)

### Integration (from spec.json integrations)
- [ ] All destination names preserved (including phantom/unused ones, flagged as such)
- [ ] Fallback chains preserved (e.g. remote service → DB → hardcoded default)
- [ ] Local/dev fallbacks preserved (e.g. local dir for SFTP, log-instead-of-send for email)

### Overrides / special cases (from spec.json specialClients or equivalent)
- [ ] All hardcoded per-entity overrides moved to configuration
- [ ] Default values match legacy behavior

### Tests
- [ ] Every business rule has at least one test
- [ ] Every financial calculation tested with known inputs/outputs
- [ ] Every state-machine transition tested
- [ ] Each preserved bug/anomaly has a test pinning the legacy behavior

## Expected Results

Scale and runtime depend entirely on the source system's size and complexity, so set expectations from *your* analysis (entity count, rule count, table count from Stage 1), not from a fixed number. See `references/worked-example.md` for the concrete figures the example system produced (LOC, test count, table count, score), as one calibration point.
