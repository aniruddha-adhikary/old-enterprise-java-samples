# Stage 1: Code Analysis with Joern + Graphify

## Overview

This stage builds complementary analysis artifacts from source:
1. **Graphify knowledge graph** — community detection, god-node identification, architectural overview
2. **Joern CPG** — Code Property Graph for semantic analysis (data flow, call chains, attribute tracking)
3. **A project profile** — auto-calibrated conventions (see `grounding-on-current-project.md`)

Run Graphify first for a fast map, then Joern for depth.

## Step 1: Graphify Extraction (seconds)

```bash
cd "$REPO"
graphify update .
```

Produces `graphify-out/`:
- `GRAPH_REPORT.md` — god nodes, communities + cohesion scores, surprising connections, knowledge gaps
- `graph.json` — full graph (nodes + edges), queryable via the CLI
- `graph.html` — interactive visualization

### Graphify CLI queries (adapt the terms to the domain you discovered)

```bash
graphify query "business rules thresholds priorities rejection"
graphify query "commission rates pricing spreads calculations"
graphify query "queues endpoints settlement integration"
graphify explain "<CoreClassName>"          # e.g. the god node from GRAPH_REPORT.md
graphify path "<EntryPoint>" "<PersistedEntity>"
```

### What to look for in GRAPH_REPORT.md
- **God nodes** (high edge count): core domain aggregates. The highest-edge class is usually the central entity.
- **Cohesion scores**: low cohesion (mixes concerns) → split candidates; high cohesion → preserve as a module boundary.
- **Isolated nodes**: orphaned/undocumented components — possible dead code or poor integration.
- **Surprising connections**: cross-module dependencies that reveal hidden coupling.

## Step 2: Build the Joern CPG (seconds to minutes)

```bash
export PATH="$HOME/bin/joern/joern-cli:$PATH"
mkdir -p "$REPO/joern-workspace"
javasrc2cpg "$REPO" -o "$REPO/joern-workspace/project.cpg"
```

`javasrc2cpg` parses Java source into a CPG — it does **not** compile the code or need dependencies resolved. (Do not use `joern-export` for this; that exports *from* an existing CPG, it does not create one from source.) Point it at a specific source root if the repo contains unrelated trees.

## Step 3: Calibrate, then run the analysis scripts

Run everything via the runner (it calibrates first, then runs `01`–`12`):

```bash
SCRIPTS=.agents/skills/legacy-code-reconstruction/references/joern-scripts
bash "$SCRIPTS/run-all.sh" "$REPO" "$REPO/joern-workspace/project.cpg" "$REPO/joern-workspace/output"
```

Or run individual scripts:
```bash
joern --script "$SCRIPTS/00-calibrate.sc" --param cpgFile="$REPO/joern-workspace/project.cpg"
joern --script "$SCRIPTS/03-business-rules.sc" --param cpgFile="$REPO/joern-workspace/project.cpg"
# script 07 accepts an optional rootPackage from the calibration output:
joern --script "$SCRIPTS/07-integration-topology.sc" \
      --param cpgFile="$REPO/joern-workspace/project.cpg" --param rootPackage="com.example"
```

### The script suite

| Script | Purpose | Key output |
|--------|---------|------------|
| 00-calibrate.sc | **Auto-discover conventions + domain** | Root package, modules, rule interface, queues, status vocab, markers, **domain nouns/verbs, candidate critical ops** |
| 01-explore-cpg.sc | Structural survey | Packages, types, methods per type, counts |
| 02-domain-models.sc | Entity extraction | Fields, types, constants, relationships |
| 03-business-rules.sc | Rule discovery | Conditions, thresholds (incl. single-digit), priorities |
| 04-data-flows.sc | Lifecycle tracing | Entry points → processing → persistence |
| 05-architectural-patterns.sc | Pattern recognition | DAO, DTO, Factory, Command, Singleton, … |
| 06-commission-and-pricing.sc | Calculation logic (domain-flavored) | Rates, spreads, formulas — finance-named; the generic equivalent is calibration's critical-ops + 14 + 17 |
| 07-integration-topology.sc | Wiring diagram | Queues/topics, SOAP, SFTP, SMTP, JDBC, cross-module edges |
| 08-anomalies-and-debt.sc | Debt detection | Duplicates, dead methods, swallowed exceptions, markers |
| 09-jira-references.sc | Issue traceability | **Any** `TOKEN-NNN` marker namespace + evolution markers |
| 10-state-machines.sc | Status transitions | Per-entity state machines, defined-but-never-set states |
| 11-sql-schema.sc | Database schema | CREATE/INSERT/SELECT/UPDATE/DELETE, table names |
| 12-context-attributes.sc | Attribute flow | SET-vs-READ analysis on context objects |
| **13-critical-value-flows.sc** | Data-flow (critical pipeline) | How inputs flow into each critical computation/persist; which paths validate first |
| **14-decision-tables-and-guards.sc** | Decision tables | "Brain" methods; guard→action rules; lookup tables (constants resolved) |
| **15-failure-semantics.sc** | Error-handling behavior | Per-check fail-open/fail-closed behavior (a spec requirement to preserve-or-fix) |
| **16-operation-sequence.sc** | Algorithm order | Ordered transaction recipe; operation ordering; computed-but-unused values |
| **17-constant-provenance-and-clones.sc** | One-rule-or-many | Constant drift vs collision; near-duplicate business methods (dedup the rules) |
| **18-entity-mutation-surface.sc** | Entity lifecycle | Cross-module field writes; co-mutation invariants; write-only fields |

Scripts **13–18 are advanced semantic analyses** — heavier and more heuristic than 01–12, leaning on Joern's data-flow, control-flow, and constant-resolution. They are **domain-neutral**: scripts 13–16 take the domain's compute/stakes verbs and critical modules from `domain-profile.env` (auto-loaded by `run-all.sh`), so they extract *this* project's critical pipeline, decision tables, error-handling behavior, transaction algorithm, one-rule-or-many duplication, and entity lifecycle — finance or otherwise. They are **behavior extractors, not bug finders** — where they surface something that looks unintended, record it in the spec's `knownBugs`/anomalies with a preserve-vs-fix decision rather than fixing it. See `references/advanced-semantic-analyses.md` (what each contributes to which spec section + caveats) and `grounding-on-current-project.md` §1.5 (the domain profile). Treat output as leads to confirm in source.

### Scripts 12 and 10 are the highest-signal, with caveats

- **`12-context-attributes.sc`** finds attributes SET but never READ — dead features, lost data, unfinished integrations. It compares **keys only** (argument index 1), so values no longer pollute the result. It can only see *literal* keys; it prints how many writes/reads used dynamic or constant keys it couldn't resolve. Confirm hits in source.
- **`10-state-machines.sc`** synthesizes a state machine per entity from observed status setters and flags "defined but never set" constants. Because status is often set via constant references (not string literals), treat dead-state flags as candidates to verify, not proof.

All scripts are self-calibrating: they derive the root package and module list from the CPG, so there is nothing to edit for a standard project. See `grounding-on-current-project.md` if a heuristic doesn't fit your codebase.

## Output layout after Stage 1

```
$REPO/
├── graphify-out/
│   ├── GRAPH_REPORT.md
│   ├── graph.json
│   └── graph.html
└── joern-workspace/
    ├── project.cpg
    └── output/
        ├── 00-calibrate.txt
        ├── 01-explore-cpg.txt
        ├── ...
        ├── 12-context-attributes.txt
        └── combined-report.txt
```

All of this feeds Stage 2 (extraction).
