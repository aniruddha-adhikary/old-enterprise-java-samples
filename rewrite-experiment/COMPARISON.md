# Legacy-to-Modern Rewrite: Hypothesis Comparison

## Experiment Design

**Goal**: Determine the best pipeline for automated legacy code → modern rewrite.

**Phase 1 (Shared)**: Extraction agent uses Joern + Graphify + source code to produce:
- `BRD.md` — 840-line prose Business Requirements Document
- `spec.json` — 1233-line machine-readable specification

**Phase 2 (3 Hypotheses)**: Three isolated builder agents, each receiving different inputs:

| Hypothesis | Input | No Access To |
|---|---|---|
| **A**: Document Handoff | BRD.md (prose only) | Source code, spec.json, analysis tools |
| **B**: Structured Spec | spec.json + GRAPH_REPORT.md | Source code, BRD.md, Joern outputs |
| **C**: Direct Translation | Source code + Joern outputs + Graphify | BRD.md, spec.json, README |

---

## Quantitative Comparison

| Metric | Hypothesis A | Hypothesis B | Hypothesis C |
|--------|-------------|-------------|-------------|
| **Source files** | 55 | 57 | 55 |
| **Source LOC** | 3,150 | 2,631 | 2,373 |
| **Test files** | 6 | 4 | 3 |
| **Test LOC** | 969 | 1,032 | 1,097 |
| **Tests passing** | 102/102 | 98/98 | 105/105 |
| **Schema tables** | 13 | 13 | 9 |
| **Architecture doc** | 128 lines | 122 lines | 192 lines |
| **Rules implemented** | 17/17 | 17/17 | 17/17 |
| **Known bugs documented** | 20/20 | 16/20 | 12/20 |
| **Docker Compose** | Yes (3 services) | Yes (4 services) | Yes (3 services) |
| **Tech stack** | TypeScript/Express | TypeScript/Express/Zod | TypeScript/Express |

---

## Qualitative Evaluation (Scored /30)

### Domain Model Completeness (6 points)

| Criterion | A | B | C |
|-----------|---|---|---|
| TradeOrder with all fields | 1 | 1 | 1 |
| Client with all fields | 1 | 1 | 1 |
| SettlementRecord with all fields | 1 | 1 | 1 |
| Notification with all fields | 1 | 1 | 1 |
| State machines (TradeOrder, Settlement) | 1 | 1 | 1 |
| Relationships documented | 1 | 1 | 1 |
| **Subtotal** | **6/6** | **6/6** | **6/6** |

### Business Rules (8 points)

| Criterion | A | B | C |
|-----------|---|---|---|
| All 17 rules present | 1 | 1 | 1 |
| Correct priorities | 1 | 1 | 1 |
| Correct reject vs flag behavior | 1 | 1 | 1 |
| MaxOrderValue 1.10 buffer | 1 | 1 | 1 |
| Reversed priority comparator bug | 1 | 1 | 1 |
| ShortSaleRule added manually | 1 | 1 | 1 |
| Surveillance flags not reject | 1 | 1 | 1 |
| PositionLimit 100K threshold | 1 | 1 | 1 |
| **Subtotal** | **8/8** | **8/8** | **8/8** |

### Financial Logic (5 points)

| Criterion | A | B | C |
|-----------|---|---|---|
| Commission rates (4 tiers) | 1 | 1 | 1 |
| Pricing spreads per tier | 1 | 1 | 1 |
| FX rates (EUR, GBP, JPY, CHF) | 1 | 1 | 1 |
| VaR formula correct | 1 | 1 | 1 |
| Price deviation 10% | 1 | 1 | 1 |
| **Subtotal** | **5/5** | **5/5** | **5/5** |

### Integration Points (5 points)

| Criterion | A | B | C |
|-----------|---|---|---|
| All JMS queues mapped | 1 | 1 | 1 |
| SOAP → REST pricing | 1 | 1 | 1 |
| SFTP settlement | 1 | 1 | 1 |
| SMTP notifications | 1 | 1 | 1 |
| Database schema | 1 | 1 | 0.7 |
| **Subtotal** | **5/5** | **5/5** | **4.7/5** |

### Special Client Logic (3 points)

| Criterion | A | B | C |
|-----------|---|---|---|
| All 10 client overrides | 1 | 1 | 1 |
| Loyalty bonus for C001-C003 | 1 | 1 | 1 |
| Configurable (not hardcoded) | 1 | 1 | 1 |
| **Subtotal** | **3/3** | **3/3** | **3/3** |

### Anomalies Preserved (3 points)

| Criterion | A | B | C |
|-----------|---|---|---|
| T+3 doesn't skip weekends | 1 | 1 | 1 |
| VALIDATED/PRICED unused statuses | 1 | 1 | 1 |
| Dead code documented | 1 | 1 | 1 |
| **Subtotal** | **3/3** | **3/3** | **3/3** |

### **TOTAL SCORES**

| Hypothesis | Score |
|---|---|
| **A** (BRD prose) | **30/30** |
| **B** (JSON spec + graph) | **30/30** |
| **C** (Direct translation) | **29.7/30** |

---

## Key Differentiators

### Hypothesis A: BRD Prose → Code

**Strengths:**
- Most source LOC (3,150) — most complete implementation
- Best test variety (6 test files covering rules, commission, pricing, VaR, settlement, derivatives separately)
- All 20 known bugs explicitly documented in architecture
- Highest code readability — the prose BRD produced natural, well-commented code

**Weaknesses:**
- Relies entirely on the quality of the BRD prose (garbage in → garbage out)
- No structural insight from the original codebase

### Hypothesis B: JSON Spec + Community Graph → Code

**Strengths:**
- Community-informed architecture: explicitly mapped Graphify communities to module boundaries
- Used god nodes (TradeOrder 89 edges, RuleContext 88 edges) to identify core aggregates
- Used cohesion scores to decide which communities to split (low-cohesion → split, high-cohesion → preserve)
- Schema-driven: Zod validation for runtime type checking
- Best Docker setup (4 services including app container)

**Weaknesses:**
- Slightly less source code than A (2,631 LOC)
- Fewer test files (4 vs 6)

### Hypothesis C: Direct Source + Analysis Tools → Code

**Strengths:**
- Best architecture document (192 lines) — describes exact analysis process
- Documents which Joern/Graphify queries revealed which findings
- Most tests (105 passing)
- Highest test density per LOC (1,097 test LOC for 2,373 src LOC = 46% ratio)
- Found specific JIRA references and source line numbers
- Identified dead code from Joern context-attribute analysis

**Weaknesses:**
- Fewest schema tables (9 vs 13) — missed some auxiliary tables
- Documented fewer known bugs explicitly (12 vs 20)
- Agent spent time reading source code instead of maximizing output

---

## Critical Analysis: What Actually Matters

### 1. Extraction Quality Dominates

All 3 hypotheses achieved 29.7-30/30 on the evaluation criteria. The difference between them is marginal. **The quality of Phase 1 extraction determined the outcome more than the Phase 2 approach.**

The extraction agent's BRD and spec.json were both so comprehensive that hypotheses A and B had everything they needed. Hypothesis C, despite having source code access, didn't score meaningfully higher because the extraction had already captured the essential information.

### 2. Prose BRD vs Structured JSON: Tie

Both formats produced equivalent results. The BRD was more readable for human review. The JSON spec was more precise for thresholds and formulas. For agent consumption, both worked equally well.

### 3. Community Graph Added Architectural Value

Hypothesis B's architecture document is the only one that explains *why* modules are bounded the way they are. The community cohesion scores provided objective justification for splitting/preserving module boundaries. This is valuable for human architects reviewing the rewrite.

### 4. Source Code Access Didn't Help Much

Hypothesis C had source code + analysis tools but produced the least polished output (fewer schema tables, fewer documented bugs). The agent spent ACUs reading source code that the extraction agent had already summarized. **The two-phase approach is more efficient.**

---

## Recommendation

### Winner: Hypothesis A (BRD Prose) + B's Community Graph Insight

**Best pipeline for production use:**

```
Phase 1: Joern + Graphify + Source Code
    ↓
    ├── BRD.md (prose requirements, for builder agent + human review)
    ├── spec.json (machine-readable, for validation + code generation)
    └── GRAPH_REPORT.md (community analysis, for architecture decisions)

Phase 2: Builder agent receives ALL THREE
    ↓
    Modern codebase with:
    - BRD-driven implementation (prose → natural code)
    - spec.json-driven validation (exact thresholds as test assertions)
    - Community-informed module boundaries
```

### Why Not Hypothesis C?

Direct source code access is **slower and less reliable** than a well-structured intermediate document. The extraction phase acts as a **compression + organization step** that makes the builder agent's job easier. Without it, the builder agent wastes effort re-discovering what the extraction already found.

### Practical Setup Required

1. **Joern** — install via `curl -L https://github.com/joernio/joern/releases/latest/download/joern-install.sh | bash`
2. **Graphify** — install via `pip3 install graphifyy`
3. **Extraction skills** — the 12 Joern scripts in `joern-workspace/scripts/` + Graphify CLI queries
4. **Builder prompt template** — the agent prompt from Hypothesis A (BRD input) augmented with B's community graph instruction

### Time Investment

| Phase | Time | Output |
|---|---|---|
| Joern CPG creation | ~2 min | Code Property Graph |
| Graphify extraction | ~10 sec | Knowledge graph + communities |
| Joern script execution | ~5 min | 12 analysis outputs |
| Extraction agent (BRD + JSON) | ~20 min | 840-line BRD + 1233-line spec.json |
| Builder agent | ~30 min | Complete modern codebase with tests |
| **Total** | **~1 hour** | **Full rewrite from code only** |
