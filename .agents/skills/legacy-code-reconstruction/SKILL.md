---
name: legacy-code-reconstruction
description: Reconstruct business logic from legacy Java codebases using Joern (Code Property Graph) and Graphify (knowledge graph). Produces structured Business Requirements Documents and machine-readable specs from source code alone. Use this skill whenever the user asks to understand, document, reverse-engineer, or extract business rules from a legacy codebase, or when they want to reconstruct requirements that were never written down. Also use when the user mentions Joern, Graphify, CPG analysis, or code archaeology.
---

# Legacy Code Business Logic Reconstruction

Systematic pipeline for extracting business logic from legacy Java codebases using static analysis, producing structured requirements documents suitable for modernization or rewrite.

This skill is **codebase-agnostic**. The analysis scripts auto-calibrate to whatever project they run against (root package, modules, rule interfaces, queue names, status vocabulary, and ticket-marker conventions are all discovered, not hardcoded). A fully worked example on a real 100+ file enterprise trading system lives in `references/worked-example.md` — read it to see what good output looks like, but do not treat its specific rules/rates/queues as defaults for other projects.

## When to Use This Skill

- "What does this legacy code actually do?"
- "Extract the business rules from this codebase"
- "Reverse-engineer the requirements"
- "We lost the original specs, reconstruct them from source"
- "Analyze this codebase with Joern/Graphify"
- "Document the business logic before we rewrite"

## Prerequisites

### Joern (Code Property Graph engine)
```bash
# Check first
command -v joern || echo "Joern not installed"

# Install (~2 GB download). Adds a joern-cli dir; put it on PATH.
curl -L https://github.com/joernio/joern/releases/latest/download/joern-install.sh -o /tmp/joern-install.sh
sh /tmp/joern-install.sh --install-dir="$HOME/bin/joern"   # non-interactive; no sudo with a user dir
export PATH="$HOME/bin/joern/joern-cli:$PATH"
```
Verified working with the latest Joern release on JDK 25 (you'll see harmless `native-access` warnings). A JDK 17+ is required to *run* Joern; the code being analyzed can be any Java version — `javasrc2cpg` parses source, it does not compile it.

### Graphify (knowledge graph)
```bash
pip3 install graphifyy      # the PyPI package is "graphifyy"; the CLI it installs is "graphify"
command -v graphify
```

## Pipeline Overview

Three stages. Each builds on the previous and can be run independently.

```
Stage 1: Analysis     (Joern CPG + Graphify graph + auto-calibration)
    |
    v
Stage 2: Extraction   (structured BRD.md + machine-readable spec.json)
    |
    v
Stage 3: Modernization (modern rewrite from the extracted specs)
```

- `references/grounding-on-current-project.md` — **read this first when running on a new project.** How to calibrate the pipeline to the codebase in front of you.
- `references/stage-1-analysis.md` — Stage 1 instructions.
- `references/advanced-semantic-analyses.md` — scripts 13–18: semantics for the spec (money pipeline, rule/rate tables, error-handling behavior, transaction sequence, one-rule-or-many, entity lifecycle). Behavior extractors, not bug finders.
- `references/stage-2-extraction.md` — Stage 2 instructions.
- `references/stage-3-modernization.md` — Stage 3 instructions.
- `references/worked-example.md` — a complete end-to-end example with real numbers.

## Quick Start (full pipeline on a codebase at `$REPO`)

### Stage 1 — build analysis artifacts
```bash
export PATH="$HOME/bin/joern/joern-cli:$PATH"
cd "$REPO"

# Graphify: fast architectural map (seconds, no LLM needed for code graphs)
graphify update .                       # -> graphify-out/{GRAPH_REPORT.md, graph.json, graph.html}

# Joern: build the CPG, then run all analysis scripts (auto-calibrating)
SCRIPTS=.agents/skills/legacy-code-reconstruction/references/joern-scripts
bash "$SCRIPTS/run-all.sh" "$REPO" "$REPO/joern-workspace/project.cpg" "$REPO/joern-workspace/output"
```
`run-all.sh` builds the CPG with `javasrc2cpg` if it doesn't exist, runs `00-calibrate` first to discover project conventions, then runs scripts `01`–`12` (survey) and `13`–`18` (advanced semantic analyses), and writes a `combined-report.txt`.

### Stage 2 — extract requirements
Read the calibration profile + all Joern outputs + the Graphify report + source, then produce:
- `BRD.md` — prose Business Requirements Document with numbered `FR-*` requirements
- `spec.json` — machine-readable specification (entities, rules, integrations, financials, bugs)

### Stage 3 — modern rewrite
Feed `BRD.md` + `spec.json` + `GRAPH_REPORT.md` to a builder that produces a modern codebase.

## Grounding on the Current Project (do this before extraction)

The single most important step when reusing this skill on a *new* codebase is to run the
auto-calibration and adopt its findings, rather than assuming the conventions of any prior project.

```bash
joern --script "$SCRIPTS/00-calibrate.sc" --param cpgFile="$REPO/joern-workspace/project.cpg"
```

`00-calibrate.sc` discovers, directly from the CPG:
- **Root package** (longest common prefix) and **modules** (segment under the root)
- **Rule / strategy interfaces** (interfaces with 3+ implementers — your business-rule abstraction)
- **Integration vocabulary** (dotted UPPER-CASE queue/topic literals; JMS/SFTP/SOAP/mail/JDBC API usage)
- **Status constants** and the real status value vocabulary
- **Issue/ticket marker prefixes** (`JIRA-`, `REG-`, project keys — discovered, not assumed)
- **Financial/calc class hints**

Use that profile to fill in the project-specific names everywhere downstream (requirement prefixes, module decomposition, rule catalog). See `references/grounding-on-current-project.md` for the full procedure, including how to adapt the scripts if your codebase breaks an assumption.

### The skill adapts to the project's domain (it assumes none)

The advanced analyses (scripts 13–16) are generic techniques that must be told what *this*
project's critical operations are — they do **not** assume finance or any domain. Calibration
emits domain signals (`DOMAIN NOUNS`, `DOMAIN VERBS`, `CANDIDATE CRITICAL OPERATIONS`); from those
you **name the domain** and author a small **domain profile**:

```bash
cp references/domain-profile.template.env "$REPO/joern-workspace/domain-profile.env"
# fill COMPUTE_VERBS / STAKES_VERBS / CRITICAL_MODULES / CORE_ENTITIES from the calibration signals
```

`run-all.sh` auto-loads it and tunes scripts 13–16 to the domain (trading → `calculate,price,commission`; healthcare → `dose,score,eligibility`). To persist the adaptation, generate a project-specific **companion skill**:

```bash
bash tools/generate-companion-skill.sh "$REPO/joern-workspace/domain-profile.env"
```

This emits a thin `<DOMAIN_SLUG>-reconstruction` skill that triggers on the domain's vocabulary, pins the profile, and delegates the method back to this generic skill. See `references/grounding-on-current-project.md` §1.5.

## Key Learnings (from a single controlled experiment — directional, not laws)

These come from one experiment reconstructing business logic from a 137-file enterprise Java trading system. Treat them as informed priors, not guarantees.

1. **Two-phase (extract, then build) was at least as good as direct translation and easier to steer.** An agent given `BRD + spec.json` and *no* source scored 30/30 on rule preservation; an agent with source + tools scored 29.7/30. The gap is within noise on one codebase — the real win is that the extraction step compresses and organizes information so the builder stays focused.
2. **Joern's most distinctive capability is attribute-flow analysis** — finding context attributes that are SET but never READ (`12-context-attributes.sc`). This surfaces dead code, incomplete features, and broken integrations. Caveat: it only sees *literal* attribute keys; constant-reference or computed keys need source confirmation (the script flags how many it couldn't see).
3. **Graphify's most distinctive capability is community detection** — grouping related code with cohesion scores; high-edge "god nodes" mark core aggregates and community boundaries inform module decomposition.
4. **Use both, Graphify first** (quick architectural map) then Joern (deeper semantic analysis of the areas Graphify highlighted).
5. **Number every requirement** (`FR-ORD-001`, `FR-RUL-002`, …) so output is testable and traceable.
6. **Record known bugs with a `preserveForBackwardCompatibility` flag** — critical when a rewrite must stay behaviorally compatible.
7. **Extract commission rates, FX rates, and thresholds as exact numeric values** — these are the most common source of rewrite bugs. (The scripts deliberately keep single-digit literals like a `T+3` settlement offset, which naive magic-number filters drop.)

## Evaluation Criteria

When assessing reconstruction quality, check that you captured:
- All business rules with correct thresholds and reject-vs-flag behavior
- All financial formulas with exact rates
- All integration points (queues, endpoints, protocols)
- All state machines with transitions
- All special/override cases
- Known bugs / anomalies, flagged

`references/evaluation-criteria.md` gives a generic scoring template; `references/worked-example.md` shows it filled in for the example system.
