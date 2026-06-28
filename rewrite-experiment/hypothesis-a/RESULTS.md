# Hypothesis A - BRD-Only Modern Rewrite Results

## What Was Built

A complete modern TypeScript/Node.js rewrite of the BigCorp Trade Order Management System, working purely from the Business Requirements Document (BRD) with zero access to the original Java source code.

### Deliverables

- **17 business rules** implemented with exact thresholds, priorities, and behaviors from the BRD
- **Rule engine** with chain-of-responsibility pattern, configurable priority sort (descending default / ascending fix)
- **Pricing service** with 3-tier fallback chain (DB → in-memory DB prices → hardcoded) and tier-based spreads
- **Commission calculator** with exact tier-based rates (PLATINUM 0.5%, GOLD 1.0%, SILVER 1.5%, BRONZE 2.0%)
- **Settlement batch processor** with T+3 date calculation, XML/DAT file generation, SFTP upload with local fallback
- **Reconciliation processor** supporting XML and COBOL-style fixed-width DAT files
- **Notification gateway** with email (HTML template), SMS, FAX (deprecated), retry logic (max 3)
- **Risk engine** with parametric VaR: `|notional| × vol × 2.33 × √(1/252)`, flag threshold 50,000
- **Derivatives processor** for FX spot/forward (1.5% commission) and options (5% flat premium), max notional 10M
- **Audit service** with billing ledger, rule audit log, surveillance audit log
- **Regulatory reporter** generating fixed-width DAT and XML daily trade reports
- **REST API** replacing servlets + SOAP with Express routes
- **Docker Compose** with PostgreSQL, RabbitMQ, and MailHog
- **PostgreSQL schema** with all 13 tables from the data model
- **10 special client overrides** moved from hardcoded to configurable
- **93 unit tests** covering all rules, commission calculations, settlement dates, VaR, pricing, and derivatives

### Key Decisions

1. **TypeScript over Java**: Better type safety, cleaner async patterns, modern tooling
2. **PostgreSQL over Oracle**: Open-source, same SQL semantics, better dev experience
3. **RabbitMQ over ActiveMQ**: Modern AMQP broker, better management UI, widely adopted
4. **REST over SOAP**: JSON payloads, standard HTTP methods, no XML envelope overhead
5. **Bug preservation**: Most legacy bugs preserved for backward compatibility (see ARCHITECTURE.md)
6. **BUG-002 and BUG-005 fixed**: Inactive rule bug and singleton thread-safety — both eliminated by the modern architecture
7. **Special clients configurable**: Moved from hardcoded SpecialClientsRule to config array, easy to migrate to DB

### What's NOT Included (by design)

- No web UI (trade desk JSP pages) — REST API is the modern equivalent
- No cron scheduling for settlement batch — use external scheduler (Kubernetes CronJob, etc.)
- No SFTP client implementation — `StorageAdapter` interface provided for pluggable backends
- No actual SMTP integration — Nodemailer configured, dev mode logs to console
