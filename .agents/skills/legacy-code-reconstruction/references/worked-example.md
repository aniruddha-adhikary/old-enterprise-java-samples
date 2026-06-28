# Worked Example: BigCorp Trade Order Management System

This is a complete, concrete run of the pipeline on one real system — a ~100–137 file enterprise Java trading platform (`com.bigcorp.*`). **Everything here is example-specific.** Do not copy these names, rules, or numbers into another project; reconstruct that project's own values via the calibration step. This file exists to show what good output looks like and to give one calibration data point.

## What `00-calibrate.sc` discovers on this system

Running calibration against the CPG produced:

- **Root package:** `com.bigcorp`
- **Modules (by type count):** `common` (50), `tradedesk` (16), `settlement` (12), `orderengine` (5), `derivatives` (5), `reporting` (5), `notifications` (5), `demo` (4), `risk` (4), `pricing` (3), `audit` (3)
- **Rule interface (auto-detected):** `com.bigcorp.common.rules.Rule` — 18 implementers. Also `com.bigcorp.tradedesk.command.Command` (5 implementers).
- **Queue/topic literals (no brand assumption):** `BIGCORP.TRADE.ORDERS`, `BIGCORP.NOTIFICATIONS`, `BIGCORP.TRADE.CONFIRMATIONS`, `BIGCORP.SETTLEMENT.EVENTS`, `BIGCORP.DERIVATIVES.{ORDERS,PRICING,CONFIRMS}`, **and** `RISK.ORDERS.INBOUND`, `RISK.RESULTS.OUTBOUND` (which a `BIGCORP`-prefixed filter would have missed)
- **Integration API usage:** jcraft/JSch (29), JMS (28), SFTP (23), HttpURLConnection (20), javax.mail (10), SOAP (8)
- **Marker prefixes (discovered, not assumed):** `REG-N-N` (16), `JIRA-N` (3), `ISO-N-N` (2), `DEMO-N` (1), `BIGCORP-N` (1)
- **Counts:** 112 internal types, 1228 methods, 12371 calls, 4870 literals

This is the "ground truth" the rest of the extraction is built on.

## Completeness checklist (the instantiated rubric, scored /30)

### Domain Model (6 points)
- [ ] TradeOrder with all 12 fields
- [ ] Client with all 10 fields (including tiers, KYC, killSwitch)
- [ ] SettlementRecord with all 12 fields
- [ ] Notification with all 9 fields
- [ ] State machines: TradeOrder (NEW→FILLED→SETTLED, REJECTED), SettlementRecord (PENDING→GENERATED→UPLOADED→CONFIRMED/FAILED/DISCREPANCY)
- [ ] Relationships: Client 1:N TradeOrder, TradeOrder 1:1 SettlementRecord, TradeOrder 1:N Notification

### Business Rules (8 points)
- [ ] All 17 rules present with correct priorities
- [ ] Correct reject vs flag behavior for each
- [ ] MaxOrderValue with 10% buffer (BUFFER_MULTIPLIER=1.10)
- [ ] Reversed priority comparator bug preserved (descending order)
- [ ] ShortSaleRule added programmatically (not in XML config)
- [ ] Surveillance rules (Layering, Spoofing) flag but never reject
- [ ] PositionLimit threshold: 100,000 shares
- [ ] DailyVolumeLimit: 50,000 shares per order (misleading name — it's per-order, not daily)

### Financial Logic (5 points)
- [ ] Commission rates: PLATINUM 0.5%, GOLD 1.0%, SILVER 1.5%, BRONZE 2.0%
- [ ] Pricing spreads per tier: PLATINUM 0.1%, GOLD 0.2%, SILVER 0.3%, BRONZE 0.5%
- [ ] FX rates: EUR 1.10, GBP 1.55, JPY 0.009, CHF 0.72
- [ ] VaR formula: |notional| * vol * 2.33 * sqrt(1/252)
- [ ] Price deviation threshold: 10%

### Integration Points (5 points)
- [ ] All JMS queues (BIGCORP.TRADE.ORDERS, BIGCORP.NOTIFICATIONS, BIGCORP.TRADE.CONFIRMATIONS, BIGCORP.SETTLEMENT.EVENTS, plus RISK.* queues)
- [ ] SOAP pricing service with DB fallback
- [ ] SFTP settlement file transfer (XML + fixed-width DAT format)
- [ ] SMTP email notifications
- [ ] Database (HSQLDB dev / Oracle prod)

### Special Client Logic (3 points)
- [ ] All 10 client overrides (C001-C010) documented
- [ ] Loyalty bonus for C001, C002, C003
- [ ] Henderson zero commission, Acme early access

### Anomalies Preserved (3 points)
- [ ] T+3 settlement doesn't skip weekends (single-digit `3` literal — must survive magic-number filtering)
- [ ] VALIDATED and PRICED statuses defined but never used (caught by script 10's defined-but-never-set)
- [ ] `active` XML attribute not applied to Rule objects (a SET-but-never-applied config attribute)

## What script 12 (attribute flow) found here

Real context-attribute keys flagged SET-but-never-READ included `loyalty_bonus`, `commission_override`, `pricing_tier_override`, `kyc_status`, `early_access`, `fx_rate_applied`, and `multi_currency_priority` — features computed but never applied to pricing. (After the key-extraction fix, value literals like `HIGH`/`USD`/`1.0` no longer pollute this list.) Some writes use a bulk-copy loop with non-literal keys; the script reports that count so they're confirmed in source rather than assumed dead.

## Rewrite outcome (one builder run)

- **~3,000 LOC** source, **~1,000 LOC** tests, **100+ tests** passing, **13 tables** in schema
- **30/30** on the rubric when the builder was given `BRD.md + spec.json` (no source)
- An agent given source + analysis tools scored **29.7/30** — within noise; the two-phase approach's advantage was mainly tighter, more steerable context
- **~30 minutes** agent runtime

These figures are a single data point. Expect different scale and timing on a different system — set expectations from your own Stage 1 counts.
