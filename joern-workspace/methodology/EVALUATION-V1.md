# Evaluation: Agent v1 Reconstruction vs Actual Business Logic

## Methodology
Compared the agent's `RECONSTRUCTED-BUSINESS-LOGIC.md` (754 lines, produced from Joern output + source code only) against the actual README.md and test-plan.md (the ground truth).

## Scoring Summary

| Section | Accuracy | Completeness | Notes |
|---------|----------|-------------- |-------|
| System Purpose | 95% | 95% | Correctly identified domain, users, tech era |
| Domain Model | 90% | 90% | All entities found; field lists accurate |
| Business Rules | 95% | 98% | All 16+ rules found with correct thresholds |
| Business Processes | 90% | 90% | Complete lifecycle traced; minor gaps |
| Financial Logic | 95% | 95% | All commission rates, spreads, FX rates correct |
| Integration Architecture | 90% | 90% | All queues, endpoints, SFTP paths found |
| Anomalies | 95% | 95% | Found all major bugs, JIRA refs, duplications |

**Overall: ~93% accuracy and completeness**

## Detailed Findings

### What the Agent Got RIGHT (impressive findings from code-only)

1. **All 16 business rules** identified with correct priorities, thresholds, and behavior
2. **Commission rates** exactly correct (PLATINUM 0.5%, GOLD 1.0%, SILVER 1.5%, BRONZE 2.0%)
3. **Reversed priority comparator** bug correctly identified
4. **ShortSaleRule not in rules.xml** — correctly identified the programmatic addition
5. **10% buffer on MaxOrderValue** — correctly identified with JIRA-1892 context
6. **Henderson Capital zero commission** — correctly identified
7. **T+3 settlement without weekend skip** — correctly identified as a bug
8. **Price deviation threshold** of 10% — correctly identified
9. **All JMS queue names** — correctly identified
10. **SOAP fallback chain** (SOAP → DB → hardcoded) — correctly identified
11. **All FX rates** (EUR 1.10, GBP 1.55, JPY 0.009, CHF 0.72) — correctly identified
12. **VolumeDiscount/SpecialClients priority interaction bug** (JIRA-6003) — correctly identified
13. **Commission discrepancy** across CommissionCalculator, PricingServiceImpl, VolumeDiscountRule — correctly identified
14. **Pricing tier spreads** — correctly identified with exact values
15. **All JIRA references** extracted from code (23 JIRAs)
16. **Risk engine VaR formula** — correctly reconstructed including the 2.33 z-score and sqrt(1/252)
17. **Surveillance rules flag-only behavior** — correctly identified
18. **Special client arrangements for all 10 clients** (C001-C010) — correctly identified

### What the Agent Got PARTIALLY RIGHT

1. **State machine**: Correctly noted NEW→FILLED→SETTLED and REJECTED paths. Correctly identified VALIDATED, PRICED, PENDING_REVIEW as defined but unused. Minor: didn't catch the full reconciliation status detail (CONF/REJC/DISC/PEND mapping was in the process section but could be more explicit in the state machine section).

2. **Settlement file format**: Got the basics right (XML + DAT, header/detail/trailer). The column widths were approximately correct but not all verified against source.

3. **Module count**: README says 7 modules; reconstruction listed more because it included derivatives-engine, risk-engine, reporting-service which were added in later waves.

### What the Agent MISSED or Got WRONG

1. **Minor: Hardcoded fallback prices** — Agent listed correct symbols and prices but noted "SUNW" without explaining it's Sun Microsystems (minor, era knowledge).

2. **Gap acknowledgment was excellent**: The agent correctly identified 8 gaps in what couldn't be determined from code alone (e.g., whether volume discounts are actually applied downstream, CANCELLED order origination, multi-currency actual conversion point).

3. **Minor: The agent said "1999-2021 era"** — the README says "late 1990s / early 2000s". The agent inferred evolution through 2021 from the code (surveillance rules from 2015, etc.), which is actually MORE accurate than the README's simplified description.

## What This Proves About Joern for Business Logic Reconstruction

### Joern's CPG is Highly Effective For:

1. **Discovering all business rules** — the rule engine pattern (evaluate/execute methods) is perfectly suited to CPG analysis. Every rule class, its thresholds, and its conditions were fully extracted.

2. **Extracting financial constants** — numeric literals in multiplication operations directly reveal commission rates, deviation thresholds, and pricing spreads.

3. **Mapping integration topology** — string literals containing queue names, URLs, and paths provide a complete wiring diagram.

4. **Finding anomalies** — duplicate constants across classes, same-named methods with different implementations, and dead code are all perfectly suited to graph queries.

5. **Understanding data models** — POJO field analysis + SQL DDL extraction gives complete entity definitions.

6. **Tracing data flows** — call chain analysis from entry points (servlets, JMS listeners) through processing to persistence gives the full lifecycle.

### Where Joern CPG Analysis Falls Short:

1. **Runtime behavior** — which code paths actually execute in production can't be determined statically. The agent correctly flagged uncertainty about whether volume discounts are applied downstream.

2. **Configuration state** — what values are in the database at runtime (client records, pricing cache) must be inferred from bootstrap SQL or hardcoded test data.

3. **Cross-system integration** — the CPG doesn't capture what the clearinghouse expects or what IBM MQSeries does differently from embedded ActiveMQ.

4. **Evolution intent** — why changes were made can only be partially reconstructed from JIRA references in code comments.

5. **Non-code business rules** — any rules implemented only in config files, stored procedures, or external systems won't appear in the Java CPG.

## Recommendations for Methodology Improvement

1. **Add a script that extracts JIRA references** — the agent found them manually but a Joern script should systematically extract all `JIRA-NNNN` patterns.

2. **Add a script for SQL DDL extraction** — specifically target DatabaseBootstrap to get complete table schemas.

3. **Add a script for configuration file analysis** — parse rules.xml, properties files, and web.xml to complement the Java CPG.

4. **Add a "state machine extractor"** — specifically look for `setStatus()` calls paired with their enclosing conditionals to auto-generate state diagrams.

5. **Consider a "gap detection" script** — identify context attributes that are set but never read (indicating unused features or incomplete implementations).
