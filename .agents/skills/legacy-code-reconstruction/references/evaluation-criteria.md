# Evaluation Criteria (generic template)

Use this to score a reconstruction (BRD/spec) or a rewrite against the legacy system. It is a **template**: derive the concrete checklist items from *your* Stage 1 analysis (the entities, rules, integrations, etc. that calibration and the scripts actually found). For a fully instantiated example, see `worked-example.md`.

Score each dimension as `captured / total`, where `total` is the count discovered in Stage 1. A perfect score means the reconstruction lost nothing relative to the code.

## Dimensions

### Domain Model
- [ ] Every entity present, with **all** fields (count fields from `02-domain-models.sc`; missing fields are the most common loss)
- [ ] Every state machine with its states and transitions (from `10-state-machines.sc`)
- [ ] Entity relationships (1:1, 1:N) preserved

### Business Rules
- [ ] Every rule present with its correct priority (from `03-business-rules.sc` + the discovered rule interface)
- [ ] Correct reject-vs-flag behavior per rule
- [ ] Exact thresholds preserved — including single-digit and "off-by-buffer" values (e.g. a ×1.10 buffer)
- [ ] Rules registered programmatically (not just from config/XML) are not missed
- [ ] Misleadingly named rules documented by actual behavior, not by name

### Financial / Calculation Logic
- [ ] Every rate, spread, and tier value exact (from `06-commission-and-pricing.sc`)
- [ ] Every formula uses exact constants (z-scores, day counts, holding periods, deviation thresholds)
- [ ] Stale-by-design or duplicated values preserved and flagged

### Integration Points
- [ ] Every queue/topic named (from `07-integration-topology.sc`), including unused/phantom ones
- [ ] Every external protocol covered (SOAP/REST, SFTP, SMTP, JDBC, …) with fallback chains
- [ ] Connection/config parameters captured

### Special / Override Cases
- [ ] Every per-entity override (per-client, per-account, …) documented with its values

### Anomalies & Known Bugs Preserved
- [ ] Behavior-affecting bugs identified and flagged with a preserve/fix disposition
- [ ] Defined-but-never-used states/attributes noted (from scripts 10 and 12)
- [ ] Config attributes that are read but not applied, or set but never read, noted

## Scoring guidance

- Weight dimensions by the system's nature (a trading system weights financial logic heavily; a workflow engine weights state machines).
- A reconstruction that scores well on counts but misses *exact values* is not done — exact thresholds/rates are where rewrites silently break.
- Record the denominators (how many entities/rules/etc. existed) so the score is interpretable.
