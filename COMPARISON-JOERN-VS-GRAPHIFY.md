# Joern vs Graphify: Business Logic Reconstruction Comparison

## Experimental Setup

Both tools were given the same task: reconstruct the complete business logic of the BigCorp Trade Order Management System (a J2EE-era multi-module Java application) without access to documentation (README, test-plan, CHANGELOG, docs/).

| Dimension | Joern | Graphify |
|-----------|-------|----------|
| **Tool Type** | Code Property Graph (CPG) — static analysis engine | Knowledge Graph — Tree-sitter AST + Leiden community detection |
| **Graph Representation** | CPG: types, methods, calls, literals, control flow, data flow | Knowledge graph: classes, methods, imports, references, communities |
| **Query Language** | Scala-based CPG queries (`.filter()`, `.ast`, `.isCall`) | CLI commands: `query`, `path`, `explain`, `affected` |
| **Requires LLM** | No (fully local) | No for code-only extraction; optional for doc/community labeling |
| **Installation** | ~2GB download (JVM-based) | `pip install graphifyy` (~50MB) |
| **CPG/Graph Build Time** | ~60s (javasrc2cpg) | ~10s (tree-sitter AST) |
| **Graph Stats** | 112 types, 1228 methods, 12371 calls, 4870 literals | 1393 nodes, 3613 edges, 101 communities |
| **Custom Scripts Written** | 12 Scala scripts (domain models, rules, flows, etc.) | 0 (used CLI queries only) |
| **Agent Iterations** | 2 (v1: 754 lines, v2: 846 lines) | 1 (1121 lines) |

## Reconstruction Quality Comparison

### System Purpose
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| Identified domain (equity trading) | YES | YES |
| Identified technology era (J2EE/1999-2002) | YES | YES |
| Identified user roles | YES | YES |
| Named specific client companies | YES (7 clients) | YES (7 clients) |
| Identified all modules | YES (listed 7+) | YES (listed 11 modules) |
| **Verdict** | **TIE** | **TIE** |

### Domain Model
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| TradeOrder fields (all 12) | YES | YES |
| Client fields (all 10) | YES | YES |
| SettlementRecord fields (all 12) | YES | YES |
| Notification fields (all 9) | YES | YES |
| Included SQL DDL | Partial (in text) | YES (full CREATE TABLE) |
| DerivativeOrder documented | Brief mention | Full field list |
| RiskOrder documented | Brief mention | Full field list |
| **Verdict** | **Graphify wins** (more complete with DDL + derivative/risk models) |

### Business Rules
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| Found all 17 rules | YES (listed 16 + ShortSale) | YES (listed all 17 numbered) |
| Correct priorities | YES | YES |
| Correct thresholds | YES | YES |
| Reject vs flag behavior | YES | YES |
| ShortSaleRule not in XML | YES | YES |
| Reversed priority comparator bug | YES | YES |
| `active` XML attribute not applied | YES | YES |
| Rule behavior summary table | YES | YES (clearer format) |
| Source file references | NO (just class names) | YES (full paths with line numbers) |
| **Verdict** | **Graphify wins** (source references, better formatting) |

### Business Processes
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| Complete order lifecycle | YES | YES |
| Settlement process | YES | YES |
| Reconciliation flow | YES | YES |
| Notification dispatch | YES | YES |
| Pricing fallback chain | YES (SOAP→DB→hardcoded) | YES (SOAP→DB→hardcoded) |
| Audit/billing flow | Brief | YES (detailed, identified AuditListener as billing creator) |
| **Verdict** | **Graphify slightly wins** (more detailed on audit path) |

### Financial Logic
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| Commission rates (4 tiers) | YES | YES |
| Pricing spreads per tier | YES | YES |
| FX rates (4 pairs) | YES | YES (+ AUD/USD in derivatives) |
| Hardcoded fallback prices (8 symbols) | YES | YES |
| VaR formula | YES | YES |
| Price deviation threshold (10%) | YES | YES |
| Commission discrepancy (3 locations) | YES | YES |
| Volume discount NOT applied downstream | YES (v2 finding) | Not explicitly stated |
| Loyalty bonus NOT applied downstream | YES (v2 finding) | Not explicitly stated |
| NET_AMOUNT = gross + commission | YES (v2 finding) | Not explicitly stated |
| **Verdict** | **Joern v2 wins** (dead attribute analysis is unique) |

### Integration Architecture
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| All JMS queues | YES (10 queues) | YES (6 core + derivatives/risk) |
| SOAP endpoints | YES | YES |
| SFTP paths | YES | YES |
| SMTP config | YES | YES |
| Database config | YES | YES |
| Settlement file formats (XML + DAT) | YES | YES |
| **Verdict** | **TIE** |

### Anomalies & Technical Debt
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| JIRA references extracted | 23 JIRAs | 43 JIRAs |
| Regulatory references | 6 REG-NNNN refs | 10 REG-NNNN refs |
| Known bugs documented | YES (5 major) | YES (6 major) |
| Dead code identified | YES | YES |
| Duplicated logic | YES | YES |
| Security concerns | YES (4 items) | YES (SQL injection specifically) |
| Architecture smells | Brief | YES (10 named items) |
| **Verdict** | **Graphify wins** (more JIRA refs, more regulatory refs, SQL injection finding) |

### Confidence Assessment
| Criterion | Joern Agent | Graphify Agent |
|-----------|------------|----------------|
| High/Medium/Low ratings | YES (detailed table per claim) | YES (per section) |
| Specific evidence cited | YES (line numbers, method names) | YES (source file paths) |
| Known limitations documented | YES (8 gaps) | YES (5 limitations) |
| **Verdict** | **Joern v2 wins** (per-claim granularity) |

## Unique Capabilities

### What Joern Does That Graphify Cannot

1. **Data flow analysis** — Joern can trace how a value flows through the code (e.g., from `setStatus()` through conditionals to `saveOrder()`). This enabled the context attribute flow analysis (SET vs READ) which discovered 43 dead attributes. Graphify has no equivalent.

2. **Literal extraction with operator context** — Joern can find all numeric literals used in multiplication operations, instantly revealing commission rates (0.005, 0.01, 0.015, 0.02) without reading each file. Graphify has no literal search.

3. **Control flow graph queries** — Joern can query `isControlStructure` to find all conditionals containing specific comparisons, revealing business rule thresholds systematically. Graphify's queries find nodes by keyword but don't understand code semantics.

4. **Call graph analysis** — Joern's CPG tracks all call sites, enabling "who calls this method" and "what does this method call" queries with full precision. Graphify infers call relationships from imports and references but with lower precision.

5. **Custom scriptability** — Joern's Scala query language allows building arbitrarily complex analyses (e.g., "find all methods that set status but are never called" or "find all string literals matching JIRA-NNNN and group by ticket number"). Graphify's CLI queries are keyword-based BFS/DFS traversals.

### What Graphify Does That Joern Cannot

1. **Community detection** — Leiden clustering automatically groups related code into 101 communities, revealing architectural boundaries. The agent used community structure to understand module relationships without reading all files. Joern has no clustering.

2. **Cross-file navigation via graph** — `graphify path "A" "B"` finds the shortest connection between any two concepts, guiding the agent to relevant source files. Joern requires manual exploration or pre-written scripts.

3. **God node analysis** — Graphify automatically identifies the most-connected classes (TradeOrder: 89 edges, RuleContext: 88 edges, Client: 53 edges), immediately revealing the core domain model. Joern requires scripted degree analysis.

4. **Zero-config extraction** — `graphify update .` runs in 10 seconds with no configuration. Joern requires CPG creation and custom Scala scripts.

5. **Multi-modal support** — Graphify can also process docs, PDFs, images (with LLM). Not relevant here since we excluded docs, but valuable for real-world codebases.

6. **Interactive HTML visualization** — `graph.html` provides a clickable, filterable graph that agents can reference. Joern produces text output only.

## Overall Verdict

| Dimension | Winner | Why |
|-----------|--------|-----|
| **Setup speed** | Graphify | pip install + 10s extraction vs 2GB download + 60s CPG build |
| **Ease of use** | Graphify | CLI queries need no scripting; Joern requires custom Scala |
| **Code semantics** | Joern | CPG understands data flow, control flow, call chains at AST level |
| **Business logic extraction** | Joern | Custom scripts extract precise thresholds, status transitions, attribute flows |
| **Architecture understanding** | Graphify | Community detection reveals module boundaries automatically |
| **Anomaly detection** | Tie | Both found bugs; Graphify found more JIRAs, Joern found dead attributes |
| **Reconstruction accuracy** | Tie (~93-95%) | Both agents produced correct, detailed reconstructions |
| **Output completeness** | Graphify | 1121 lines vs 846 lines; more source refs, more JIRA refs |
| **Unique insights** | Joern | Dead attribute analysis (SET-but-never-READ) is a category Graphify can't produce |
| **Scalability** | Graphify | 10s build, incremental updates; Joern CPG creation is heavier |

### Recommendation

**Use both tools together.** They are complementary, not competing:

1. **Start with Graphify** — quick setup, community detection gives you the lay of the land, god nodes reveal core abstractions, graph navigation guides you to the right files.

2. **Follow up with Joern** — once you know WHERE to look, write targeted CPG queries to extract precise business logic: thresholds, state transitions, data flows, call chains, dead code analysis.

3. **The killer combination**: Graphify's community map + Joern's data flow analysis. Graphify tells you "these classes form a settlement subsystem", Joern tells you "the volume_discount attribute is set by VolumeDiscountRule but never read by CommissionCalculator".

### For this specific codebase

Both tools produced agent reconstructions that matched ~93-95% of the actual documented business logic. The Graphify agent produced a longer, better-formatted document with more source references. The Joern agent (v2) produced unique insights about dead features (volume discounts, loyalty bonuses not actually applied) that the Graphify agent missed. The ideal approach would combine both.
