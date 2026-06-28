# Results: Hypothesis B — Modern Rewrite from Spec + Graph

## What Was Built

A complete TypeScript/Node.js rewrite of the BigCorp Trade Order Management System, derived entirely from `spec.json` (machine-readable specification) and `GRAPH_REPORT.md` (code community analysis). No Java source code was read.

## Stats

- **17 rules** implemented with exact thresholds from spec.json
- **8 domain entities** with Zod schemas matching spec field definitions
- **9 JMS queues** mapped to modern AMQP equivalents
- **15 integration points** (SOAP, SFTP, SMTP, JMS, DB) mapped to modern stack
- **20 known bugs** documented; behavior preserved per `preserveForBackwardCompatibility`
- **10 special clients** with configurable overrides
- **Full financial logic**: tier-based commissions, FX rates, VaR calculation, derivatives premium
- **~80 tests** covering rules, financials, settlement, and integrations

## Architecture Decisions

1. **TypeScript over Java/Kotlin**: Type safety without J2EE ceremony. Zod provides runtime + compile-time validation.

2. **Community-driven decomposition**: Used Graphify's cohesion scores to decide boundaries:
   - Low-cohesion communities (0, 1) → split across multiple modules
   - High-cohesion communities (3, 5, 10, 29) → preserved as single modules
   - God nodes (TradeOrder, RuleContext) → explicit aggregate boundaries

3. **Preserved bug behavior**: All 20 spec.json `knownBugs` are documented in ARCHITECTURE.md. Behaviors flagged `preserveForBackwardCompatibility: true` are kept (e.g., T+3 not skipping weekends, priority sort reversed).

4. **Schema-driven entities**: Domain types generated directly from spec.json field definitions using Zod schemas. Database schema derived from the same source.

5. **Integration adapters**: Each legacy integration (JMS, SOAP, SFTP, SMTP) has a modern adapter with the same interface contract but pluggable implementation.

## What's Different from the Legacy System

| Aspect | Legacy | Modern |
|--------|--------|--------|
| Configuration | .properties files, System.getProperty | Environment variables, typed config |
| Service wiring | ServiceLocator + JNDI | Constructor injection via `bootstrap()` |
| State management | Mutable singletons | Immutable value objects + explicit stores |
| Error handling | try/catch with logging | Typed errors, fail-open/fail-closed per rule |
| Testing | Single EndToEndTest class | Isolated unit tests per module |
| Deployment | Ant + WAR | Docker Compose, single `npm start` |

## Verification

Run tests:
```bash
cd rewrite-experiment/hypothesis-b
npm install
npm test
```

Run the server:
```bash
npm run dev
# or with Docker:
docker-compose up
```

## Limitations

- **No UI**: The original had JSP pages; this rewrite is API-only (REST endpoints)
- **In-memory stores for dev**: Production would use PostgreSQL via the `pg` driver
- **SFTP/SMTP stubbed**: Integration adapters have interface contracts but connect to local stubs
- **No actual RabbitMQ connection**: MessageBroker uses in-memory dispatch for testing
