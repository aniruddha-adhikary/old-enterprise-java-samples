# Advanced Semantic Analyses (scripts 13–18)

Scripts `01`–`12` survey structure, rules, integrations, and anomalies. Scripts `13`–`18` extract the *semantics* of the business logic using Joern's deeper capabilities — data flow, control flow, constant resolution, and cross-method reasoning. They are heavier and more heuristic than `01`–`12`, but each recovers something the others can't and feeds a specific part of the spec.

**These are behavior extractors, not bug finders.** The point of reconstruction is to capture what the system *actually does* so the rewrite reproduces it. When one of these scripts surfaces something that looks unintended (a check that passes on error, a value computed then dropped, two paths that disagree), that is **not a defect to fix here** — it is a behavior to record in the BRD/`spec.json`, usually under `knownBugs[]`/anomalies with a `preserveForBackwardCompatibility` decision (see `stage-2-extraction.md` and `stage-3-modernization.md`, "Bug Preservation vs Fix"). Treat every output as a lead to confirm in source.

All six are generic (no project names hardcoded; root package and constants are derived) and were validated against a real 100+ file enterprise Java trading system.

## How each feeds the spec

| Script | Recovers | Primary spec target |
|---|---|---|
| 13 critical-value-flows | the critical-value pipeline; which paths validate before acting | computation specs; data flow; rule×computation cross-ref |
| 14 decision-tables-and-guards | guard→action rules; commission/FX/price tables (constants resolved) | Business Rules Catalog; Financial Calculations |
| 15 failure-semantics | each check's error-handling behavior (fail-open/closed) | rule `failureBehavior`; Non-Functional Reqs; knownBugs |
| 16 operation-sequence | the ordered transaction algorithm; computed-but-unused values | per-rule behavior; State Machines; knownBugs/dead-feature |
| 17 constant-provenance-and-clones | whether duplicated logic is one rule or several divergent ones | Business Rules Catalog (dedup); Financial Calculations (drift) |
| 18 entity-mutation-surface | the true entity lifecycle, ownership, and invariants | Data Model; State Machines |

## 13 — Critical-value flows (`reachableByFlows`)
**Recovers:** how inputs (external data, key fields) flow into each *critical operation* — the computations, persistence writes, and external sends the rewrite must reproduce — and whether each validates first. **Domain-neutral:** the "compute" verbs come from `domain-profile.env` (`COMPUTE_VERBS`); persistence/send sinks are generic.

Uses Joern's interprocedural data flow (its signature feature, unused by 01–12). On the reference (trading) system, tuned with `computeVerbs=calculate,price,commission`, it showed the order-entry path computes on the validated broker quote while the audit/billing path recomputes on the client-supplied value *without* the rule engine. For reconstruction that asymmetry is **two business rules to capture** (and a candidate `knownBug` if unintended), not just a finding.

Caveats: "validates-first" is judged method-locally (a rule run one frame up in a caller is missed — extend with a 1–2 hop caller check); `reachableByFlows` is expensive, so sources/sinks/flows are all `.take(N)` capped; seed sources at getters too, since values often pass through entity fields and break field-insensitive flows.

## 14 — Decision tables & guards (control flow + constant resolution)
**Recovers:** where business logic concentrates, the `guard condition → action` rules, and — by resolving `static final` references through a constant map — the **actual numeric tables** (commission tiers, FX rates, price books), not just constant names.

On the reference system it reconstructed the commission table (PLATINUM 0.5% … BRONZE 2.0%, with default == BRONZE) and FX table, and the full reject-rule table — exactly the content of the BRD's rules catalog and financials sections.

Caveats: constant resolution is essential or the tables come back as opaque names; `else if` chains nest under one outer IF — take the shallowest return per branch; constant-comparison conditions sometimes render with the constant as receiver (`this.equals(x)`) — fall back to branch-body literals to label the case.

## 15 — Failure semantics (error-handling behavior)
**Recovers:** each business check's behavior on error — FAIL-OPEN (proceed as if passed), FAIL-CLOSED (block/reject), or SILENT-LOSS (swallow, void return). This is a genuine behavioral requirement: "on a DB error this rule returns pass" must be either preserved or consciously changed by the rewrite.

On the reference system the four market-abuse surveillance rules return pass on DB error, the kill-switch defaults to "off" on lookup failure, and KYC defaults to "reject". Capture each as the rule's `failureBehavior`; where the behavior looks unintended, record it under `knownBugs[]` with a preserve-vs-fix decision rather than silently "fixing" it in the spec.

Caveats: return polarity isn't universal (`true`=pass for `evaluate()` but `false`=permissive for `checkForWashTrade`) — confirm polarity from the consumer; a sentinel-then-guarded pattern (pricing returns `-1.0`, caller rejects on `<=0`) is fail-*closed* and must not be labeled open.

## 16 — Operation-sequence reconstruction (CFG ordering)
**Recovers:** the real ordered recipe of a transaction (ingest → validate → price → FX → charge → status → persist → notify), plus two ordering facts that are easy to get wrong in a rewrite: the relative order of those steps, and values computed then never consumed.

On the reference system a volume discount is computed into the rule context and never read back, so commission is charged on the undiscounted total; status is set before persistence. Record the algorithm in the per-rule behavior/state-machine sections, and the dropped discount as a candidate incomplete feature (`knownBugs`/dead-feature, preserve-vs-complete).

Caveats: `lineNumber`/`order` are `Option` (default safely); cross-method value-drop detection relies on set-key vs get-key matching (literal keys only); pick entry methods by domain-call density when none is supplied.

## 17 — Constant provenance & near-duplicate logic
**Recovers:** whether the same business quantity is defined once or in many places (and whether the copies agree), and whether two methods are really one rule copied and tweaked — i.e. how many *distinct* rules the catalog should contain.

On the reference system commission `0.02` and the FX rates appear across several classes (real drift to record as one rule or several), `1.10` is an accidental collision (FX rate vs a 10% buffer — do **not** merge), and two SFTP reconciliation parsers / two audit loggers are near-duplicates with divergent constants (one rule with two implementations).

Caveats: identical digits ≠ same concept — confirm by field name before treating as drift; normalize numerics (`0.01`==`0.010`) before grouping; the script already filters trivial 0/1/small-int noise; keep the similarity floor high and require shared numeric literals so generic CRUD shells don't flood the results.

## 18 — Entity mutation surface & lifecycle
**Recovers:** for each entity, which module writes which field, which fields are written together (atomic invariants), and which are write-only — i.e. the true, undocumented lifecycle the data model must encode.

On the reference system `TradeOrder`'s status lifecycle is driven from `orderengine` (not its nominal owner), settlement re-deserializes a whole `TradeOrder` from XML (a real coupling to model), and status is never written without `lastModified`+`notes` (an invariant the rewrite must keep).

Caveats: boolean fields are read via `is*` not `get*` (include both or you get false write-only hits); separate hydrators/deserializers (`unmarshal*`, `fromXml`, `mapResultSet*`) from genuine co-mutations; "owning module" is best supplied as an entity→module map since POJOs usually all live in one `model`/`dto` package.

## Performance note

Scripts 16 and 17 do cross-method / pairwise work (17 is O(n²) over non-trivial methods). They run in seconds on a ~1k-method system but can be slow on very large CPGs — raise the `>= 6 calls` / similarity thresholds, or run them on a single module's CPG, if needed.
