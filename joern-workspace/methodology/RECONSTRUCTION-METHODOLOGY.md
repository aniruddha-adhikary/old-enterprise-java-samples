# Business Logic Reconstruction Methodology Using Joern

## Overview

This methodology describes how to systematically reconstruct the business logic of a legacy enterprise Java application using Joern's Code Property Graph (CPG) analysis. The approach works purely from source code — no access to original requirements documents, specifications, or domain experts is assumed.

## Phase 1: CPG Creation and Structural Survey

**Script:** `01-explore-cpg.sc`

**Goal:** Build a complete inventory of the codebase's structural elements.

**What to extract:**
- Package hierarchy → reveals module boundaries and organizational structure
- Type declarations → identifies all classes, interfaces, and their purposes
- Method inventory → maps capabilities per class
- Aggregate statistics → gauges codebase size and complexity

**How this helps reconstruction:**
The package/class structure of enterprise Java applications encodes architectural intent. Packages like `.model.`, `.rules.`, `.dao.`, `.service.`, `.dto.`, `.consumer.` directly indicate the role of contained classes.

## Phase 2: Domain Model Extraction

**Script:** `02-domain-models.sc`

**Goal:** Identify core business entities, their attributes, relationships, and state machines.

**Techniques:**
1. **POJO detection:** Classes with >60% getter/setter methods are domain objects
2. **Member analysis:** Field types reveal entity relationships (e.g., `Client client` in `TradeOrder`)
3. **Constants extraction:** `static final` fields reveal domain vocabulary (status codes, tier names)
4. **Cross-reference mapping:** Which entities reference which others → entity relationship diagram

**Key patterns to look for:**
- Status fields (`getStatus()`/`setStatus()`) → state machines
- Enum-like constants (all-caps fields) → business vocabulary
- Transfer Objects (DTOs) → API/integration boundaries
- Builder/Assembler classes → complex object construction logic

## Phase 3: Business Rule Extraction

**Script:** `03-business-rules.sc`

**Goal:** Identify and document all business rules, validations, and decision logic.

**Techniques:**
1. **Rule class discovery:** Find classes implementing Rule/Validator interfaces
2. **Conditional analysis:** Extract if/switch conditions from `evaluate()` methods
3. **Threshold detection:** Find numeric comparisons (magic numbers = business thresholds)
4. **String matching:** `equals()` calls on domain values reveal categorical decisions
5. **Status transitions:** `setStatus()` calls map the state machine
6. **Exception patterns:** `throw` statements reveal rejection/validation logic

**Critical insight:** In legacy Java, business rules are often:
- Scattered across rule engine classes AND inline in processors
- Partially in XML config files (rule definitions) AND hardcoded in listeners
- Duplicated across modules with slight variations (intentional or accidental)

## Phase 4: Data Flow Tracing

**Script:** `04-data-flows.sc`

**Goal:** Map the complete lifecycle of business objects through the system.

**Techniques:**
1. **Entry point identification:** Servlets (`doGet`/`doPost`), JMS listeners (`onMessage`), main methods
2. **Call chain tracing:** Follow method calls from entry points through processing layers
3. **SQL extraction:** All SQL statements reveal database schema and data manipulation logic
4. **Queue topology:** JMS queue names show asynchronous message flows
5. **External service calls:** SOAP/HTTP/SFTP/SMTP calls reveal integration boundaries
6. **Configuration loading:** `getProperty()` calls reveal externalized behavior

**Order of reconstruction:**
1. Start at entry points (servlets, listeners)
2. Follow the "happy path" first (order submission → processing → settlement)
3. Then trace error/rejection paths
4. Finally trace batch/scheduled processes

## Phase 5: Architectural Pattern Recognition

**Script:** `05-architectural-patterns.sc`

**Goal:** Identify the design patterns that structure the application.

**Patterns to detect:**
| Pattern | Joern Signal | Business Significance |
|---------|-------------|----------------------|
| DAO | Classes ending in `DAO` with SQL literals | Data persistence layer |
| DTO/Transfer Object | Classes with mostly getters/setters + XML methods | Integration contracts |
| Service Locator | Singleton with `register()`/`lookup()` | Service discovery |
| Factory | Classes ending in `Factory` with `create()`/`get()` | Object creation strategy |
| Command | `execute()` method with request/response | Action dispatch |
| Front Controller | Single servlet dispatching to commands | Request routing |
| Filter Chain | `doFilter()` implementations | Cross-cutting concerns |
| Observer/Listener | JMS `MessageListener` implementations | Async event handling |

## Phase 6: Financial Logic Deep-Dive

**Script:** `06-commission-and-pricing.sc`

**Goal:** Extract precise financial calculations, rates, and thresholds.

**Techniques:**
1. **Multiplication operations with literals** → rate calculations (commission = value × rate)
2. **Conditional rate selection** → tiered pricing (PLATINUM=0.5%, GOLD=1.0%, etc.)
3. **Comparison operators with numeric literals** → thresholds (max order value, deviation limits)
4. **Settlement date calculations** → business day logic
5. **Reconciliation status mapping** → clearinghouse response codes

## Phase 7: Integration Topology Mapping

**Script:** `07-integration-topology.sc`

**Goal:** Build a complete wiring diagram of all system integrations.

**What to map:**
- JMS queues (producers → consumers)
- SOAP endpoints (clients → services)
- SFTP paths (upload/download directories)
- SMTP configuration (email templates, recipients)
- HTTP endpoints (REST/SOAP URLs)
- Database connections (JDBC URLs, table names)
- Cross-module method calls (which module depends on which)

## Phase 8: Anomaly and Debt Detection

**Script:** `08-anomalies-and-debt.sc`

**Goal:** Find inconsistencies, dead code, and technical debt that reveal evolution history.

**What to look for:**
1. **Duplicate constants:** Same value defined in multiple classes → copy-paste evolution
2. **Empty catch blocks:** Swallowed exceptions → known failure modes
3. **Hardcoded credentials:** Security technical debt
4. **Console output vs logging:** Debugging code left in production
5. **TODO/FIXME/JIRA markers:** Known issues and planned work
6. **Dead methods:** Never-called code → deprecated features
7. **Duplicated logic:** Same method signature in multiple classes → inconsistent implementations

## Reconstruction Workflow

### Step 1: Generate All Reports
```bash
bash joern-workspace/scripts/run-all.sh
```

### Step 2: Build Entity-Relationship Model
From Phase 2 output, draw the domain model:
- Core entities and their fields
- Relationships between entities
- Status/state machines per entity

### Step 3: Map Business Rules
From Phase 3 output, document each rule:
- Rule name and priority
- Input conditions (what it checks)
- Output effects (what it changes)
- Thresholds and magic numbers
- Special cases and overrides

### Step 4: Trace Business Processes
From Phase 4 output, document each process:
- Entry point → processing chain → output
- Happy path vs error/rejection paths
- Synchronous vs asynchronous steps
- External service dependencies

### Step 5: Document Architecture
From Phase 5 output, document:
- Pattern catalog with implementation locations
- Module boundaries and dependencies
- Layering (presentation → business → data)

### Step 6: Validate with Anomalies
From Phase 8 output, identify:
- Intentional vs accidental inconsistencies
- Evolution patterns (how the code grew over time)
- Technical debt that affects business logic correctness

## Tips for Agents

1. **Start with the domain model** — understanding entities is prerequisite for everything else
2. **Follow the money** — in financial systems, trace how values are calculated and where rates come from
3. **Trust the code, not the comments** — comments may be outdated; the code is the truth
4. **Look for "the other path"** — every if-statement has business logic in both branches
5. **Configuration files are business rules** — XML configs, properties files, and SQL schema define behavior
6. **Duplicated code means diverged behavior** — when the same logic exists in two places, they may have diverged
7. **String constants are the Rosetta Stone** — status codes, queue names, and error messages reveal the domain language
