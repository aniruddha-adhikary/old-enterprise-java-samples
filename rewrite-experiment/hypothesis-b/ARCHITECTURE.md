# Architecture: BigCorp Trade Order Management System — Modern Rewrite

## Tech Stack Decision

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Language | TypeScript 5.3 | Type safety critical in financial domain; catches commission/price bugs at compile time |
| Runtime | Node.js 20 | Event-driven model natural fit for message-processing architecture |
| Validation | Zod | Schema-first domain entities derived directly from spec.json |
| API | Express | Mature, well-understood HTTP framework |
| Database | PostgreSQL 16 | Modern equivalent of Oracle; ACID, JSON support, robust |
| Messaging | RabbitMQ (AMQP) | Modern equivalent of ActiveMQ/JMS; durable queues, routing |
| File Transfer | ssh2-sftp-client | Modern SFTP equivalent of JSch |
| Email | Nodemailer | Modern SMTP equivalent of JavaMail |
| Testing | Jest + ts-jest | Industry standard for TypeScript testing |
| Containerization | Docker Compose | Local dev parity with production |

## Community Graph — Informed Decomposition

The Graphify analysis (1393 nodes, 3613 edges, 101 communities) directly informed module boundaries:

### God Nodes → Core Aggregates

| God Node | Edges | Design Decision |
|----------|-------|-----------------|
| `TradeOrder` (89 edges) | Cross-community bridge connecting 20+ communities | Central aggregate in `domain/`; immutable value objects with state machine |
| `RuleContext` (88 edges) | High betweenness centrality (0.045) bridging 22 communities | Explicit context-passing pattern instead of god-object; `Map<string, unknown>` for extensible attributes |
| `Client` (53 edges) | Connected to rules, pricing, settlement, notifications | First-class entity with tier-based behavior encapsulation |

### Low-Cohesion Communities → Splitting Strategy

| Community | Cohesion | Original Shape | Modern Split |
|-----------|----------|---------------|--------------|
| Community 0 (0.06) | OrderDAO, ReportGenerator, ConnectionHelper mixed | Split into `api/` (HTTP), `regulatory/` (reports), database layer |
| Community 1 (0.05) | OrderMessageListener, TradeOrder, MQ helpers mixed | Split into `api/OrderController` + `integration/MessageBroker` |
| Community 4 (0.09) | FrontControllerServlet + Command pattern | Replaced by typed Express routes |

### High-Cohesion Communities → Preserved Boundaries

| Community | Cohesion | Shape | Module |
|-----------|----------|-------|--------|
| Community 3 (0.09) | RiskDAO, ExposureCalculator, RiskOrder, RiskScheduler | `risk/` — kept as isolated bounded context |
| Community 5 (0.09) | BatchProcessor, SettlementFileGenerator, SftpUploader | `settlement/` — cohesive batch processing pipeline |
| Community 10 (0.16) | AuditListener, AuditDAO, BillingDAO, AuditEvent | `audit/` — audit + billing co-located (always change together) |
| Community 15 (0.09) | MarketHoursRule, VolumeDiscountRule, Rule, TypedRule | `rules/` — rule engine with pluggable implementations |
| Community 22 (0.14) | LayeringDetectionRule, RestrictedSymbolRule, RuleContext | `rules/impl/` — surveillance rules alongside business rules |
| Community 29 (0.20) | DispatcherMain, NotificationListener, SMSDispatcher | `notifications/` — multi-channel dispatch |
| Community 33 (0.18) | DAOFactory, HsqldbDAOFactory, OracleDAOFactory | Eliminated; single PostgreSQL target, no factory needed |

### Surprising Connections → Integration Points

- `OrderMessageListener → RuleEngine`: The order processing flow couples message consumption to rule evaluation. In the rewrite, `OrderController` directly invokes `RuleEngine.evaluateAll()` synchronously for immediate feedback, with async message publishing for downstream consumers.
- `EndToEndTest → AuditListener/NotificationListener/OrderMessageListener`: The original test wired all modules in-process. The rewrite preserves this via `bootstrap()` function that creates the entire dependency graph for integration testing.

### Knowledge Gaps → Defensive Design

307 isolated nodes indicate undocumented components. The rewrite:
1. Exposes all integration points as configurable adapters (SftpClient, EmailClient, MessageBroker) with stub implementations
2. Preserves BUG-009 (phantom SETTLEMENT.EVENTS queue) as a no-op subscription point
3. Documents each known bug with spec.json reference

## Module Architecture

```
src/
├── domain/          # Core entities (from god nodes: TradeOrder, Client, RuleContext)
│                    # Zod schemas → runtime validation + type inference
├── rules/           # Rule engine (Communities 15, 22, 26)
│   ├── Rule.ts      # Interface + base class
│   ├── RuleEngine.ts # Singleton chain-of-responsibility
│   └── impl/        # All 17 rules with exact spec.json thresholds
├── pricing/         # Pricing service (Community 7: PricingEndpointServlet)
│                    # CommissionCalculator, FxPricingHelper, PricingService
├── settlement/      # Settlement batch (Community 5: BatchProcessor + SftpUploader)
│                    # + Reconciliation (Community 11: ReconciliationProcessor)
├── notifications/   # Notification dispatch (Community 29: NotificationListener)
├── risk/            # Risk engine (Community 3: ExposureCalculator)
├── derivatives/     # Derivatives processing (from spec.json derivatives-engine)
├── audit/           # Audit + billing (Community 10: AuditListener + BillingDAO)
├── regulatory/      # Regulatory reporting (Community 0 split: ReportGenerator)
├── integration/     # Adapters (Communities 49: DB, Email, SFTP, JMS, SOAP)
│                    # MessageBroker (RabbitMQ), SftpClient, EmailClient
├── api/             # REST API (Community 4 modernized: FrontController → Express)
│                    # OrderController, ClientPortalApi
└── config/          # Environment configuration (replaces .properties files)
```

## Known Bugs — Disposition

All 20 known bugs from spec.json are documented and their behavior preserved:

| Bug | Status | Notes |
|-----|--------|-------|
| BUG-001 Priority reversed | **Preserved** | Default DESCENDING sort; `priorityFixed` config available |
| BUG-003 Server local time | **Preserved** | MarketHoursRule uses `new Date()` not Eastern Time |
| BUG-004 T+3 no weekends | **Preserved** | `calculateSettlementDate()` adds 3 calendar days |
| BUG-005 Non-thread-safe | **Fixed by design** | Node.js is single-threaded; no race condition possible |
| BUG-006 Redundant saves | **Preserved** | Order stored via both paths for settlement compat |
| BUG-007 Double deviation check | **Preserved** | Check in both RuleEngine and OrderController |
| BUG-008 INSERT-only notifications | **Preserved** | Retries create duplicate notification entries |
| BUG-009 Phantom queue | **Preserved** | SETTLEMENT_EVENTS queue registered but unused |
| BUG-010 Commission discrepancy | **Preserved** | PricingService uses 0.015 flat; order-engine uses tier-based |
| BUG-011 Hardcoded prices | **Preserved** | Fallback chain still returns stale 2000-2001 prices |
| BUG-012 FX rates 3 places | **Preserved** | Duplicated in MultiCurrencyRule, FxPricingHelper, DerivativeProcessor |
| BUG-016 Volume hardcoded | **Preserved** | VolumeDiscountRule uses 0.02 not CommissionCalculator |
| BUG-017 Multi-currency hardcoded | **Preserved** | MultiCurrencyRule uses 0.02 |
| BUG-018 Loyalty hardcoded | **Preserved** | C001/C002/C003 hardcoded list |
| BUG-019 ShortSale not in XML | **Preserved** | Added manually after config-loaded rules |
| BUG-020 Weekend not queued | **Preserved** | `queued=true` attribute set but never consumed |

## Integration Mapping

| Legacy | Modern | Notes |
|--------|--------|-------|
| ActiveMQ (JMS) | RabbitMQ (AMQP) | Same queue semantics, better management UI |
| SOAP (Axis) | REST/JSON | PricingService now internal module call |
| JDBC (raw pool) | pg (PostgreSQL driver) | Parameterized queries, connection pooling |
| JSch (SFTP) | ssh2-sftp-client | Same SFTP protocol, modern API |
| JavaMail (SMTP) | Nodemailer | Same SMTP protocol, HTML email support |
| Servlet/JSP | Express + JSON API | RESTful, no server-side rendering |
| HSQLDB (dev) | PostgreSQL (Docker) | Single DB for all environments |
| Oracle (prod) | PostgreSQL | Compatible SQL; schema.sql works directly |
