# Stage 2: Requirements Extraction

## Overview

Transform the raw analysis outputs from Stage 1 into structured requirements documents. This is the compression + organization step that makes downstream work (rewrite, documentation, migration) dramatically more efficient.

## Inputs

- Joern analysis outputs (01 through 12 text files)
- Graphify graph report and CLI access
- Java source code (for cross-referencing exact values)
- XML configs, properties files, SQL schema files

## DO NOT Read

- README, docs, wikis, or any existing documentation (the point is code-only reconstruction)
- Test files that describe expected behavior (this leaks requirements)

## Output 1: Business Requirements Document (BRD.md)

Structure the BRD as follows. Every requirement gets a unique testable ID.

### Required Sections

1. **Executive Summary** — system purpose, domain, users, technology context

2. **Functional Requirements** — organized by capability area:
   - Use prefix codes: `FR-ORD` (orders), `FR-RUL` (rules), `FR-SET` (settlement), `FR-NOT` (notifications), `FR-PRC` (pricing), `FR-AUD` (audit), `FR-RSK` (risk), etc.
   - Each requirement: `FR-XXX-NNN: The system SHALL [action] when [condition].`
   - Include exact thresholds, formulas, and magic numbers inline

3. **Data Model** — every table/entity with columns, types, constraints, relationships
   - Use `DM-NNN` prefix
   - Include Java domain object details (serialVersionUID, status constants)

4. **Integration Specifications** — every external interface
   - Use `INT-XXX` prefix (INT-JMS, INT-SOAP, INT-SFTP, INT-SMTP, INT-DB)
   - Queue names, endpoint URLs, connection parameters, protocols

5. **Business Rules Catalog** — summary table:
   | ID | Rule Name | Priority | Category | Behavior | Condition | Threshold |

6. **Financial Calculations** — exact rates and formulas
   - Commission rates per tier
   - Pricing spreads per tier
   - FX rates
   - VaR/risk formulas
   - Settlement calculations

7. **State Machines** — all status transition diagrams with conditions

8. **Special Arrangements** — per-client/per-account overrides and exceptions

9. **Known Bugs & Technical Debt** — use `BUG-NNN` prefix
   - Description, source file, JIRA reference if found
   - Whether it must be preserved for backward compatibility

10. **Non-Functional Requirements** — error handling, retry logic, thread safety, logging

### Writing Tips

- Be exhaustive. Every threshold, every rate, every queue name must appear.
- Include JIRA/ticket references found in code comments.
- Note when behavior differs from what the code's naming suggests (e.g., "DailyVolumeLimitRule" that actually checks per-order volume).
- When two components implement the same logic differently, document both versions and note the discrepancy.
- Add source file references for traceability.

## Output 2: Machine-Readable Specification (spec.json)

```json
{
  "system": {
    "name": "...",
    "domain": "...",
    "purpose": "...",
    "technology": { "language": "...", "build": "...", "database": "..." },
    "modules": ["module-a", "module-b"]
  },
  "entities": [
    {
      "name": "EntityName",
      "table": "TABLE_NAME",
      "fields": [
        {
          "name": "fieldName",
          "column": "COLUMN_NAME",
          "type": "String",
          "dbType": "VARCHAR(30)",
          "constraints": "PK, NOT NULL, format XYZ"
        }
      ],
      "stateMachine": {
        "states": ["STATE_A", "STATE_B"],
        "unusedStates": ["NEVER_SET"],
        "transitions": [
          { "from": "A", "to": "B", "condition": "..." }
        ]
      }
    }
  ],
  "rules": [
    {
      "id": "RUL-001",
      "name": "RuleClassName",
      "priority": 100,
      "category": "Business|Compliance|Surveillance",
      "behavior": "REJECT|FLAG|PASS",
      "condition": "human-readable condition",
      "threshold": { "key": "value" },
      "failOpen": false,
      "jiraRef": "JIRA-1234"
    }
  ],
  "integrations": [
    {
      "type": "JMS|SOAP|SFTP|SMTP|JDBC",
      "name": "QUEUE.NAME",
      "producer": "ClassA",
      "consumer": "ClassB",
      "config": { "host": "...", "port": 1234 }
    }
  ],
  "financials": {
    "commissionRates": { "TIER_A": 0.005 },
    "pricingSpreads": { "TIER_A": 0.001 },
    "fxRates": { "EUR/USD": 1.10 },
    "varCalculation": { "zScore": 2.33, "tradingDays": 252 }
  },
  "specialClients": [
    {
      "clientId": "C001",
      "name": "...",
      "overrides": { "commissionOverride": 0.0 }
    }
  ],
  "knownBugs": [
    {
      "id": "BUG-001",
      "description": "...",
      "sourceFile": "ClassName.java",
      "jiraRef": "JIRA-5300",
      "preserveForBackwardCompatibility": true
    }
  ],
  "sampleData": { }
}
```

### Why Both Formats?

- **BRD.md** produces more readable, well-commented code when given to a builder agent (prose → natural code)
- **spec.json** produces more precise thresholds in test assertions (exact values for automated validation)
- **Together** they scored 30/30 in our experiments; either alone scored 29.7-30/30

## Quality Checklist

Before declaring Stage 2 complete, verify:

- [ ] Every Joern analysis output file was consulted
- [ ] Every rule has a priority, threshold, and behavior documented
- [ ] Every financial formula has exact numeric constants
- [ ] Every JMS queue / integration endpoint is named
- [ ] Every state machine has transitions with conditions
- [ ] Known bugs reference source files and JIRA IDs where available
- [ ] Special client overrides are individually documented
- [ ] Dead code / SET-but-never-READ attributes are flagged
