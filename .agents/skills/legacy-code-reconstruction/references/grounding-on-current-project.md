# Grounding on the Current Project

This skill was distilled from a specific trading-system experiment, but the pipeline is meant to run on **any** legacy Java codebase. Before you extract requirements, you must *ground* the analysis on the project actually in front of you — otherwise you risk importing assumptions (package names, rule abstractions, queue conventions, status vocabularies) that do not hold.

Do this every time you start on a new codebase. It takes a few minutes and prevents a whole class of silent errors.

## Step 0: Build the CPG

```bash
export PATH="$HOME/bin/joern/joern-cli:$PATH"
javasrc2cpg "$REPO" -o "$REPO/joern-workspace/project.cpg"
```

`javasrc2cpg` parses source; it does not need the project to compile or its dependencies to resolve. If the repo contains non-Java rewrites or generated trees you don't care about, point it at the specific source root (e.g. `"$REPO/src"`) to keep the CPG focused.

## Step 1: Run auto-calibration and READ it

```bash
joern --script "$SCRIPTS/00-calibrate.sc" --param cpgFile="$REPO/joern-workspace/project.cpg"
```

This emits a **PROJECT PROFILE** derived entirely from the CPG. Capture each field — it is the ground truth you will reuse everywhere downstream:

| Profile field | What it gives you | Where you use it |
|---|---|---|
| **Root package** | longest common package prefix | `--param rootPackage=` for script 07; sanity-checks |
| **Modules** | package segment under the root, by type count | module decomposition (Stage 3), `FR-*` prefix design |
| **Rule/strategy interfaces** | interfaces with 3+ implementers | the business-rule abstraction to enumerate in script 03 / Stage 2 |
| **Integration literals** | dotted UPPER-CASE queue/topic names | integration spec (`INT-*`), no brand assumption |
| **Integration API usage** | JMS / SFTP / SOAP / mail / JDBC call counts | which protocols actually exist here |
| **Status constants + values** | `STATUS_*` fields and the real status vocabulary | state machines (script 10), entity lifecycles |
| **Issue marker prefixes** | discovered `TOKEN-NNN` namespaces | traceability (script 09); know whether it's `JIRA-`, a project key, or `REG-` |
| **Domain nouns / verbs** | vocabulary tokenized from class & method names | **name the domain**; pick the domain's compute/stakes verbs |
| **Candidate critical operations** | internal methods that decide/compute and branch | what the advanced scripts (13–16) should treat as high-stakes |

If a field comes back empty or surprising, that itself is a finding (e.g. no rule interface → rules may be inlined; no status constants → state is implicit).

## Step 1.5: Discover the domain and adapt the pipeline to it

This skill assumes **no domain** — not finance, not anything. The advanced analyses (13–16) are
generic techniques (data flow, decision tables, failure behavior, sequencing) that must be told
what *this* project's critical operations are. You supply that through a small **domain profile**.

1. **Name the domain.** Read the calibration's `DOMAIN NOUNS`, `DOMAIN VERBS`, and
   `CANDIDATE CRITICAL OPERATIONS`. They are derived purely from the code — e.g. nouns like
   `order, settlement, pricing, risk` → "securities trade order management"; or `patient,
   encounter, dose, claim` → "clinical care/billing". The naming is *your* judgment call; the
   signals are the evidence.

2. **Author the profile.** Copy `references/domain-profile.template.env` to
   `<repo>/joern-workspace/domain-profile.env` and fill it from the signals:
   - `COMPUTE_VERBS` — the domain's computation/decision verbs (trading: `calculate,price,commission`; healthcare: `dose,score,eligibility`; logistics: `route,rate,schedule`) → scripts 13, 16
   - `STAKES_VERBS` — high-stakes effect verbs (`reject,approve,charge,dispatch,...`) → script 14
   - `CRITICAL_MODULES` — packages where the important logic lives → script 15
   - `CORE_ENTITIES`, `ROOT_PACKAGE`, `DOMAIN_LABEL`, `DOMAIN_KEYWORDS`

3. **Run tuned.** `run-all.sh` auto-loads `domain-profile.env` and passes these to the right
   scripts. Without a profile, the scripts fall back to generic defaults and still run — just
   less sharply focused.

4. **Generate a companion skill (optional, persists the adaptation).**
   ```bash
   bash ../legacy-code-reconstruction/tools/generate-companion-skill.sh \
        <repo>/joern-workspace/domain-profile.env
   ```
   This emits a thin `<DOMAIN_SLUG>-reconstruction` skill (sibling of the generic one) that
   triggers on the domain's vocabulary and pins the profile, delegating method back to the
   generic skill. Future work on this project/domain activates pre-tuned.

5. **Persist a project memory** capturing the named domain + profile so future sessions skip
   re-derivation (see the memory guidance; type `project`).

The point: the *same* generic scripts produce a trading profile for a trading system and a
clinical profile for a hospital system. The domain lives in the profile, never in the scripts.

## Step 2: Decide whether any script assumption is violated

The scripts auto-derive most things, but a few detection heuristics are tuned for common Java-enterprise idioms. Check these against the profile and adapt if needed:

1. **Rule abstraction** — script `03-business-rules.sc` looks for types named `*Rule`, types under a `.rules.` package, or implementers of a `Rule` interface. If calibration shows your rule interface is named differently (e.g. `Validator`, `Policy`, `Check`), update the filter in script 03 (search for `td.name.contains("Rule")`).

2. **Rule entry-point methods** — script 03 treats `evaluate` / `execute` / `apply` / `check` as the rule body. Add your method name if the codebase uses another (e.g. `validate`, `isSatisfied`).

3. **Context object API** — script `12-context-attributes.sc` matches `setAttribute` / `getAttribute` / `put` / `getProperty` etc. If your "context"/"blackboard" object uses different accessors, add them to the setter/getter name lists.

4. **Status setters** — script `10-state-machines.sc` matches any `set…Status` / `set…State`. If status is mutated via a differently named method or a public field assignment, extend `isStatusSetter`.

5. **Integration naming** — scripts 04/07 detect queues/topics as dotted UPPER-CASE tokens (`FOO.BAR.ORDERS`) plus `queue`/`topic` keywords. If destinations are lower-case or read from config/properties, lean on the "INTEGRATION API USAGE" section and grep the config files directly.

6. **Marker prefixes** — script `09` discovers all `TOKEN-NNN` markers and filters out encoding noise (`UTF-8` etc.). To restrict to a known tracker, pass `--param markerPrefixes="PROJ,REG"`.

When you change a heuristic, **re-run that one script and eyeball the output** before trusting it. The scripts are cheap to run (seconds each).

## Step 3: Cross-check the auto-profile against reality

Pick two or three concrete facts from the profile and confirm them in source:
- Open the top "god node" / highest-implementer class — is it really the core aggregate?
- Open one class flagged under "financial/calc hints" — are the rates hardcoded literals (good) or loaded from config (look there instead)?
- Take one "SET but never READ" attribute from script 12 and confirm in source it is genuinely unused (remember: constant-keyed writes are invisible to the literal scan — the script prints how many it could not see).

This three-fact check catches a mis-calibration early, before it propagates into the BRD.

## Step 4: Lock the profile into the extraction

Carry the profile into Stage 2 explicitly:
- Derive requirement-area prefixes from the **modules** (e.g. module `settlement` → `FR-SET-*`).
- Enumerate rules from the discovered **rule interface**, not a guessed naming pattern.
- Name integrations from the discovered **queue literals / API usage**, not from any example project.
- Build state machines from the discovered **status constants**, and explicitly list "defined but never set" states as candidate dead code.

If you are running on the example trading system itself, `references/worked-example.md` shows exactly what the grounded profile and resulting BRD look like — use it as a template, not as defaults to copy.

## What NOT to assume from prior runs

- ❌ The root package is `com.bigcorp` — derive it.
- ❌ Rules implement an interface literally called `Rule` — derive it.
- ❌ Queues are prefixed `BIGCORP.` — derive them.
- ❌ The tracker is JIRA — derive the marker prefixes.
- ❌ Statuses are `NEW/FILLED/SETTLED/...` — derive them.
- ❌ The numbers (commission tiers, FX rates, T+N) carry over — re-extract every literal.

Grounding is the difference between "reconstructed the requirements of *this* system" and "described the example system again."
