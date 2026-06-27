# Business Logic Reconstruction Agent Playbook

## Your Mission

You have access to a legacy enterprise Java codebase and Joern (a code analysis tool). Your task is to reconstruct the complete business logic of this application — what it does, why, and how — using ONLY the source code and Joern analysis. You have NO access to requirements documents, specifications, or domain experts.

## Environment Setup

Joern is installed at `/home/ubuntu/bin/joern/joern-cli`. The repo is at `/home/ubuntu/repos/old-enterprise-java-samples`.

```bash
export PATH="/home/ubuntu/bin/joern/joern-cli:$PATH"
cd /home/ubuntu/repos/old-enterprise-java-samples
```

A pre-built CPG (Code Property Graph) is available at `joern-workspace/bigcorp.cpg`. Pre-computed analysis reports are in `joern-workspace/output/`.

## What to Deliver

Create a file called `joern-workspace/output/RECONSTRUCTED-BUSINESS-LOGIC.md` containing:

### 1. System Purpose
What does this system do? What business domain does it serve? Who are its users?

### 2. Domain Model
- Core entities (classes that represent business concepts)
- Their attributes and data types
- Relationships between entities
- State machines (status fields and their transitions)

### 3. Business Rules
For each rule you can identify:
- What it checks (input conditions)
- What happens when it passes/fails (effects)
- Any thresholds, limits, or magic numbers
- Priority/ordering of rules
- Special cases and overrides

### 4. Business Processes
For each major process:
- Entry point (how it starts)
- Processing steps (what happens)
- Decision points (branching logic)
- Output/side effects (what it produces)
- Error handling (what can go wrong)

### 5. Financial Logic
- How are prices determined?
- How are commissions calculated?
- What rates/percentages are used?
- Settlement and reconciliation logic

### 6. Integration Points
- What external systems does it communicate with?
- What protocols/formats are used?
- What queues/topics are used for async messaging?

### 7. Architectural Decisions
- What design patterns are used and why?
- How is the codebase organized (modules, layers)?
- What are the key abstractions?

### 8. Anomalies and Observations
- Inconsistencies you found
- Dead code or unused features
- Technical debt patterns
- Evolution clues (how the system grew over time)

## How to Use Joern

### Running Pre-Built Scripts

Pre-built analysis scripts are in `joern-workspace/scripts/`. Run them:

```bash
joern --script joern-workspace/scripts/01-explore-cpg.sc --param cpgFile=joern-workspace/bigcorp.cpg
```

Available scripts:
- `01-explore-cpg.sc` — Package/class/method inventory
- `02-domain-models.sc` — Domain entity extraction
- `03-business-rules.sc` — Business rule analysis
- `04-data-flows.sc` — Data flow tracing
- `05-architectural-patterns.sc` — Pattern detection
- `06-commission-and-pricing.sc` — Financial logic
- `07-integration-topology.sc` — Integration mapping
- `08-anomalies-and-debt.sc` — Debt and anomaly detection

Pre-computed output is already in `joern-workspace/output/` — read those files directly.

### Writing Custom Joern Queries

You can also write your own Joern scripts for deeper investigation. Key Joern CPG query patterns:

```scala
// Find all methods in a specific class
cpg.typeDecl.name("ClassName").method.name.l

// Find all calls to a specific method
cpg.call.name("methodName").l

// Find string literals containing a pattern
cpg.literal.code(".*pattern.*").l

// Trace method calls from a starting point
cpg.method.name("startMethod").ast.isCall.name.l

// Find all SQL statements
cpg.literal.code(".*SELECT.*|.*INSERT.*|.*UPDATE.*").l

// Find numeric comparisons (thresholds)
cpg.call.name("<operator>.greaterThan").argument.isLiteral.code.l

// Find all classes implementing an interface
cpg.typeDecl.inheritsFromTypeFullName(".*InterfaceName.*").name.l
```

### Interactive Joern Session

For exploratory queries, start Joern interactively:

```bash
joern
# Then inside Joern:
importCpg("joern-workspace/bigcorp.cpg")
cpg.typeDecl.name.l  // list all types
```

## Strategy

1. **Start with the pre-computed reports** — read the output files in `joern-workspace/output/`
2. **Build your domain model first** — understanding the entities is prerequisite for everything
3. **Then trace the main business process** — follow an order from submission to settlement
4. **Use source code directly** for details Joern can't capture (comments, complex logic)
5. **Write custom Joern queries** when you need to investigate specific patterns deeper
6. **Cross-reference findings** — use multiple scripts' outputs to validate your understanding

## Important Notes

- Do NOT look at README.md, test-plan.md, or docs/ directory — these contain specifications
- Work ONLY from source code and Joern analysis
- Your goal is to prove that Joern + source code reading can reconstruct business logic
- Be specific: include actual values (rates, thresholds, queue names) not just descriptions
- Note any gaps: what business logic could NOT be determined from code alone?
