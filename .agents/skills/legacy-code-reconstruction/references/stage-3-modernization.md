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

Choose based on domain analysis. In our experiments, all three agents independently chose TypeScript/Express/PostgreSQL for a financial trading system. Good defaults for enterprise Java rewrites:

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

```
rewrite/
├── ARCHITECTURE.md          # Design decisions, tech stack, community-informed decomposition
├── src/
│   ├── domain/              # Entities from spec.json entities array
│   ├── rules/               # Rule engine + all rules from spec.json rules array
│   ├── pricing/             # Financial calculations from spec.json financials
│   ├── settlement/          # Settlement batch from spec.json settlement
│   ├── notifications/       # Notification dispatch
│   ├── risk/                # Risk engine (VaR)
│   ├── derivatives/         # FX/options processing
│   ├── audit/               # Audit & billing
│   ├── regulatory/          # Report generation
│   ├── integration/         # Message broker, SFTP, SMTP adapters
│   ├── api/                 # REST API (replaces servlets)
│   └── config/              # Environment-based configuration
├── tests/                   # Tests covering all rules, financials, settlement
├── schema.sql               # Database schema from spec.json entities
├── docker-compose.yml       # Infrastructure (DB, message broker, mail)
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

## Implementation Checklist

### Rules (from spec.json rules array)
- [ ] All rules implemented with exact priority values
- [ ] Rule engine preserves chain-of-responsibility pattern
- [ ] Default sort order matches legacy (document if descending)
- [ ] evaluate() failure stops chain; execute() failure is non-fatal
- [ ] Surveillance rules FLAG but do not REJECT
- [ ] DB-dependent rules fail open on errors

### Financial Logic (from spec.json financials)
- [ ] Commission rates match per-tier values exactly
- [ ] Multiple commission rate sources preserved (if discrepancy is "by design")
- [ ] FX rates match hardcoded values (note: rates are intentionally stale)
- [ ] VaR formula uses exact z-score, holding period, trading days
- [ ] Price deviation threshold correct

### Integration (from spec.json integrations)
- [ ] All queue names preserved (even phantom/unused ones)
- [ ] Fallback chains preserved (SOAP → DB → hardcoded)
- [ ] Local fallback directories for SFTP
- [ ] Dev mode for email (log instead of send)

### Special Clients (from spec.json specialClients)
- [ ] All overrides moved from hardcoded to configurable
- [ ] Default values match legacy behavior

### Tests
- [ ] Every business rule has at least one test
- [ ] Commission calculation tested for each tier
- [ ] VaR formula tested with known inputs
- [ ] Settlement date calculation tested (including the weekend non-skip bug if preserved)
- [ ] State machine transitions tested

## Expected Results

From our experiments:
- **~3,000 LOC** source code
- **~1,000 LOC** tests
- **100+ tests** passing
- **13 database tables** in schema
- **30/30** on evaluation criteria when using BRD + spec.json input
- **~30 minutes** agent runtime
