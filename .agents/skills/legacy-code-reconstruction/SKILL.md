---
name: legacy-code-reconstruction
description: Reconstruct business logic from legacy Java codebases using Joern (Code Property Graph) and Graphify (knowledge graph). Produces structured Business Requirements Documents and machine-readable specs from source code alone. Use this skill whenever the user asks to understand, document, reverse-engineer, or extract business rules from a legacy codebase, or when they want to reconstruct requirements that were never written down. Also use when the user mentions Joern, Graphify, CPG analysis, or code archaeology.
---

# Legacy Code Business Logic Reconstruction

Systematic pipeline for extracting business logic from legacy Java codebases using static analysis tools, producing structured requirements documents suitable for modernization or rewrite.

## When to Use This Skill

- "What does this legacy code actually do?"
- "Extract the business rules from this codebase"
- "Reverse-engineer the requirements"
- "We lost the original specs, reconstruct them from source"
- "Analyze this codebase with Joern/Graphify"
- "Document the business logic before we rewrite"

## Prerequisites

### Joern Installation
```bash
# Check if Joern is available
which joern || echo "Joern not installed"

# Install Joern if needed (~200MB)
curl -L https://github.com/joernio/joern/releases/latest/download/joern-install.sh | bash
export PATH="$HOME/bin/joern/joern-cli:$PATH"
```

### Graphify Installation
```bash
pip3 install graphifyy
```

## Pipeline Overview

This skill operates in 3 stages. Each stage builds on the previous one and can be run independently.

```
Stage 1: Analysis (Joern CPG + Graphify knowledge graph)
    |
    v
Stage 2: Extraction (structured BRD + machine-readable spec.json)
    |
    v
Stage 3: Modernization (modern rewrite from extracted specs)
```

Read `references/stage-1-analysis.md` for Stage 1 instructions.
Read `references/stage-2-extraction.md` for Stage 2 instructions.
Read `references/stage-3-modernization.md` for Stage 3 instructions.

## Quick Start (All 3 Stages)

For a full pipeline run on a Java codebase at `$REPO_PATH`:

### Stage 1: Build analysis artifacts (~10 min)
```bash
cd $REPO_PATH

# Graphify: fast code graph (10 seconds)
graphify update .

# Joern: deep CPG analysis (2-5 minutes depending on codebase size)
mkdir -p joern-workspace
joern-export --repr cpg --out joern-workspace/app.cpg $REPO_PATH
```
Then run the 12 Joern analysis scripts from `references/joern-scripts/` against the CPG. See Stage 1 for details.

### Stage 2: Extract requirements (~20 min)
Read all Joern outputs + Graphify report + source code. Produce:
- `BRD.md` — prose Business Requirements Document with numbered FR-* requirements
- `spec.json` — machine-readable specification (entities, rules, integrations, financials, bugs)

### Stage 3: Modern rewrite (~30 min)
Feed BRD.md + spec.json + GRAPH_REPORT.md to a builder agent that produces a complete modern codebase.

## Key Learnings from Experiments

These findings come from controlled experiments with isolated agents reconstructing business logic from a 137-file enterprise Java trading system:

1. **Two-phase (extract then build) outperforms direct translation.** An agent with BRD+spec.json and NO source code access scored 30/30 on business rule preservation. An agent with source code + analysis tools scored 29.7/30. The extraction step compresses and organizes information, making the builder more efficient.

2. **Joern's killer feature is attribute flow analysis** — tracking which context attributes are SET by one component but never READ by any other. This reveals dead code, incomplete features, and broken integration points that no other tool finds.

3. **Graphify's killer feature is community detection** — automatically grouping related code into communities with cohesion scores. God nodes (high edge count) identify core domain aggregates. Community boundaries inform module decomposition for rewrites.

4. **Use both tools together.** Graphify first (10 seconds, quick architectural map), then Joern (deeper semantic analysis of specific areas identified by Graphify).

5. **The BRD should use numbered requirements (FR-ORD-001, FR-RUL-002, etc.)** — this makes the output testable and traceable.

6. **The spec.json should include `knownBugs` with a `preserveForBackwardCompatibility` flag** — critical for rewrites that need to maintain behavioral compatibility.

7. **Commission rates, FX rates, and thresholds MUST be extracted with exact numeric values** — these are the most common source of rewrite bugs.

## Evaluation Criteria

When assessing reconstruction quality, check:
- All business rules found with correct thresholds
- All financial formulas with exact rates
- All integration points (queues, endpoints, protocols)
- All state machines with transitions
- All special/override cases documented
- Known bugs identified and flagged

See `references/evaluation-criteria.md` for a detailed scoring rubric.
