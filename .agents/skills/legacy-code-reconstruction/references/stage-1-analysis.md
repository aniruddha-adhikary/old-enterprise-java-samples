# Stage 1: Code Analysis with Joern + Graphify

## Overview

This stage builds two complementary analysis artifacts from the source code:
1. **Joern CPG** — Code Property Graph for semantic code analysis (data flow, call chains, attribute tracking)
2. **Graphify knowledge graph** — for community detection, god node identification, and architectural overview

## Step 1: Graphify Extraction (10 seconds)

```bash
cd $REPO_PATH
graphify update .
```

This creates `graphify-out/` with:
- `GRAPH_REPORT.md` — god nodes, communities, surprising connections, knowledge gaps
- `graph.json` — full graph (nodes + edges) queryable via CLI
- `*-callflow.html` — interactive call-flow diagrams

### Graphify CLI Queries

After extraction, use these queries to explore the graph:

```bash
# Find business rules, thresholds, priorities
graphify query "business rules thresholds priorities rejection"

# Understand a specific class
graphify explain "TradeOrder"
graphify explain "RuleEngine"

# Find paths between components
graphify path "OrderMessageListener" "SettlementRecord"

# Financial logic
graphify query "commission rates pricing spreads calculations"

# Integration points
graphify query "JMS queues SOAP endpoints SFTP settlement"
```

### What to Look For in GRAPH_REPORT.md

- **God nodes** (high edge count): These are your core domain aggregates. The class with the most edges is almost always the central business entity.
- **Community cohesion scores**: Low cohesion (< 0.10) means the community mixes concerns — good candidates for splitting. High cohesion (> 0.15) means the code is well-organized — preserve these boundaries.
- **Isolated nodes** (307 in our experiment): Undocumented or orphaned components that may be dead code or poorly integrated.
- **Surprising connections**: Cross-module dependencies that reveal hidden coupling.

## Step 2: Joern CPG Creation (2-5 minutes)

```bash
mkdir -p joern-workspace
cd joern-workspace

# Import the Java source code into a Code Property Graph
joern --script ../path/to/01-explore-cpg.sc
```

Or create the CPG via Joern's import command and then run scripts against it.

## Step 3: Run Analysis Scripts

Run the 12 analysis scripts in order. Each produces a text report:

| Script | Purpose | Key Output |
|--------|---------|------------|
| 01-explore-cpg.sc | Structural survey | Package hierarchy, type count, method count |
| 02-domain-models.sc | Entity extraction | Fields, types, constants, relationships |
| 03-business-rules.sc | Rule discovery | Conditions, thresholds, priorities |
| 04-data-flows.sc | Lifecycle tracing | Entry points → processing → persistence |
| 05-architectural-patterns.sc | Pattern recognition | DAO, DTO, Factory, Command, etc. |
| 06-commission-and-pricing.sc | Financial logic | Rates, spreads, formulas, calculations |
| 07-integration-topology.sc | Wiring diagram | JMS, SOAP, SFTP, SMTP, JDBC |
| 08-anomalies-and-debt.sc | Debt detection | Duplicates, dead code, TODOs, JIRA refs |
| 09-jira-references.sc | Issue tracking | JIRA/ticket references in code/comments |
| 10-state-machines.sc | Status transitions | setStatus() calls, state constants |
| 11-sql-schema.sc | Database schema | CREATE TABLE, column types, constraints |
| 12-context-attributes.sc | Attribute flow | SET vs READ analysis for context objects |

### Script 12 is Critical

The context attribute flow analysis (SET-but-never-READ detection) is Joern's most unique capability. It reveals:
- Dead code: attributes computed but never consumed
- Broken integrations: data written to context but lost before persistence
- Incomplete features: attributes set up for a feature that was never finished

In our experiment, this found 43 dead context attributes — volume discounts, loyalty bonuses, and commission overrides were all computed but never actually applied to pricing.

## Adapting Scripts to Your Codebase

The scripts in `references/joern-scripts/` are written for Java enterprise patterns. To adapt:

1. **Change package patterns**: Update regex patterns for your package naming
2. **Change rule interface**: Update `Rule` / `evaluate` to match your codebase's abstraction
3. **Change integration patterns**: Update JMS/SOAP/SFTP detection for your protocols
4. **Change financial patterns**: Update commission/pricing detection for your domain

The general technique — literal extraction, call chain tracing, conditional analysis, attribute flow tracking — works for any Java codebase.

## Output Directory Structure

After Stage 1 completes, you should have:

```
$REPO_PATH/
├── graphify-out/
│   ├── GRAPH_REPORT.md
│   ├── graph.json
│   └── *-callflow.html
└── joern-workspace/
    ├── output/
    │   ├── 01-explore-cpg.txt
    │   ├── 02-domain-models.txt
    │   ├── ...
    │   └── 12-context-attributes.txt
    └── scripts/
        ├── 01-explore-cpg.sc
        └── ...
```

All of this feeds into Stage 2 (extraction).
