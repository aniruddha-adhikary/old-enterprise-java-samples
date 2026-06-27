# BigCorp Trade OMS - Modern Architecture

## Tech Stack

| Layer | Legacy | Modern |
|-------|--------|--------|
| Language | Java 1.4 | TypeScript 5.3 (Node.js 20) |
| Build | Ant | npm + tsc |
| Database | HSQLDB / Oracle | PostgreSQL 16 |
| Messaging | ActiveMQ (JMS) | RabbitMQ (AMQP) |
| API | Servlets 2.3 + SOAP | Express REST API |
| File Transfer | SFTP (JSch) | Pluggable StorageAdapter |
| Email | Raw SMTP | Nodemailer |
| Container | N/A | Docker Compose |
| Testing | Manual EndToEndTest | Jest |

## Module Decomposition

```
src/
├── domain/         # Pure domain: entities, value objects, enums, RuleContext
├── rules/          # Rule engine + all 17 rules (chain-of-responsibility)
├── pricing/        # PricingService (fallback chain), CommissionCalculator
├── settlement/     # BatchProcessor, Reconciliation, file generation
├── notifications/  # NotificationGateway, Email/SMS dispatchers
├── risk/           # ExposureCalculator, VaR computation
├── derivatives/    # FX/options processing, FxPricingHelper
├── audit/          # AuditService, RuleAuditService, billing
├── regulatory/     # Fixed-width + XML report generation
├── integration/    # MessageBroker (RabbitMQ + in-memory), StorageAdapter
├── api/            # Express REST routes
└── config/         # Externalized config (env vars, special client overrides)
```

## Design Decisions

### 1. Clean Architecture / Ports & Adapters
- Domain logic has zero infrastructure dependencies
- All external concerns (DB, MQ, SFTP) accessed through interfaces
- Repositories are injected, enabling in-memory testing

### 2. Rule Engine
- Chain-of-responsibility: rules sorted by priority, evaluated sequentially
- Default sort: DESCENDING (higher number first) — matches legacy behavior (BUG-001)
- `priorityFixed` config toggles to ASCENDING (JIRA-5300)
- `evaluate()` failure stops the chain; `execute()` failure is non-fatal (NFR-001)
- All 17 rules implemented with exact thresholds from the BRD

### 3. Special Client Overrides: Hardcoded → Configurable
- Legacy: 10 clients hardcoded in SpecialClientsRule.java
- Modern: `SpecialClientConfig[]` array in config, loaded from env/config
- Each override is a typed object: `commissionOverride`, `pricingTierOverride`, `earlyAccess`, etc.
- Easy to move to DB-backed config in future (JIRA-7200)

### 4. Messaging: JMS → RabbitMQ
- All 9 JMS queues mapped to RabbitMQ equivalents
- `InMemoryMessageBroker` for testing / standalone mode
- `RabbitMQBroker` for production with durable queues
- Phantom queue `bigcorp.settlement.events` preserved (BUG-009)

### 5. Settlement File Transfer: SFTP → StorageAdapter
- `StorageAdapter` interface supports pluggable backends
- `LocalStorageAdapter` for dev/testing with fallback directory
- Production: swap in S3/cloud adapter or SSH-based adapter

### 6. State Machines
- Explicit enum-based status types with valid transitions documented
- TypeScript unions provide compile-time safety on status values
- State machine transitions enforced at the service layer

## Known Bugs - Preservation vs Fix Decisions

| Bug ID | Description | Decision | Rationale |
|--------|-------------|----------|-----------|
| BUG-001 | Priority sort DESCENDING by default | **Preserved** | Backward compat; configurable via `RULES_PRIORITY_FIXED` |
| BUG-002 | Inactive XML rules still execute | **Fixed** | `isActive()` properly checked before `evaluate()` |
| BUG-003 | MarketHoursRule uses server local time | **Preserved** | Documented in code; easy to fix by switching to `America/New_York` |
| BUG-004 | T+3 skips no weekends/holidays | **Preserved** | Clearinghouse recalculates; changing breaks settlement matching |
| BUG-005 | Non-thread-safe singleton | **Fixed** | Node.js single-threaded by default; no singleton pattern needed |
| BUG-006 | Redundant order saves | **Preserved** | Settlement batch compatibility requires dual-write path |
| BUG-007 | Price deviation double-check | **Preserved** | Redundant but harmless; documented in code |
| BUG-008 | Notification INSERT-only (duplicates) | **Preserved** | INSERT-only behavior maintained for retry semantics |
| BUG-009 | Phantom settlement queue | **Preserved** | Queue created in QUEUES constant; no consumers |
| BUG-010 | Commission rate inconsistency | **Preserved** | Pricing service uses 1.5%; order engine uses tier-based; "by design" |
| BUG-011 | Hardcoded fallback prices | **Preserved** | Unknown symbols return default price (10.00/10.50/10.25) |
| BUG-012 | FX rates duplicated | **Partially fixed** | Centralized in `FxPricingHelper`; MultiCurrencyRule still has own copy for backward compat |
| BUG-013 | Partial batch not implemented | **Preserved** | `partialBatchMode` not implemented; documented |
| BUG-014 | DailyVolumeTracker unused | **Preserved** | Table exists in schema; not wired to real-time path |
| BUG-015 | Reconciliation reason code not stored | **Preserved** | Reason codes logged but not persisted (no field in model) |
| BUG-016 | VolumeDiscount uses hardcoded 0.02 | **Preserved** | Backward compat; documented with JIRA reference |
| BUG-017 | MultiCurrency uses hardcoded 0.02 | **Preserved** | Backward compat; documented with JIRA reference |
| BUG-018 | Loyalty bonus hardcoded clients | **Preserved** | C001/C002/C003 hardcoded; configurable in SpecialClientsRule |
| BUG-019 | ShortSaleRule not in XML config | **Fixed** | Registered via `registerRule()` after other rules |
| BUG-020 | Weekend orders not actually queued | **Preserved** | `queued=true` attribute set but no deferred execution |

## Integration Points

| Legacy | Modern | Notes |
|--------|--------|-------|
| JMS `BIGCORP.TRADE.ORDERS` | RabbitMQ `bigcorp.trade.orders` | POST `/api/orders` as REST alternative |
| JMS `BIGCORP.NOTIFICATIONS` | RabbitMQ `bigcorp.notifications` | Internal dispatch |
| JMS `BIGCORP.TRADE.CONFIRMATIONS` | RabbitMQ `bigcorp.trade.confirmations` | Audit/settlement consumers |
| SOAP PricingService | REST `GET /api/pricing/quote/:symbol` | + batch endpoint |
| SFTP upload | `StorageAdapter` interface | Local fallback preserved |
| SMTP email | Nodemailer (dev mode: console log) | HTML template preserved |
| Servlet order entry | REST `POST /api/orders` | JSON request/response |
| Servlet front controller | Express router | Pattern commands → route handlers |

## Commission Rate Summary

| Context | Rate | Source |
|---------|------|--------|
| Order engine (PLATINUM) | 0.5% | CommissionCalculator |
| Order engine (GOLD) | 1.0% | CommissionCalculator |
| Order engine (SILVER) | 1.5% | CommissionCalculator |
| Order engine (BRONZE/default) | 2.0% | CommissionCalculator |
| Pricing service | 1.5% flat | PricingService (BUG-010) |
| Derivatives (FX) | 1.5% flat | DerivativeProcessor |
| VolumeDiscountRule base | 2.0% | Hardcoded (BUG-016) |
| MultiCurrencyRule | 2.0% | Hardcoded (BUG-017) |

## Dead Code Patterns

- `volume_discount` / `volume_discount_applied` attributes: SET by VolumeDiscountRule but never READ downstream
- `DAILY_VOLUME_TRACKER` table: exists in schema, not used in real-time processing
- `VALIDATED`, `PRICED`, `PENDING_REVIEW` order statuses: defined but never set
- `CANCELLED` status: checked by surveillance rules but never programmatically set
- `queued` context attribute: set by MarketHoursRule but no downstream deferred execution
