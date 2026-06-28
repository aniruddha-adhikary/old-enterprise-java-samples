# Stage 2 Extraction Task — Evaluation Run

You are testing the **updated** legacy-code-reconstruction skill. Your task is to perform Stage 2 (Requirements Extraction) following the skill's instructions exactly.

## What you have access to

1. **Joern analysis outputs** (all 19 scripts including the new 00-calibrate and 13-18 advanced semantic analyses) in `joern-workspace/output/` — read every file, especially `combined-report.txt`
2. **Graphify outputs** in `graphify-out/` — read `GRAPH_REPORT.md`
3. **Java source code** in `src/`
4. **The skill instructions** in `.agents/skills/legacy-code-reconstruction/references/stage-2-extraction.md`
5. **The calibration profile** in `joern-workspace/output/00-calibrate.txt`

## What you must NOT read

- `README.md`
- `docs/` directory
- `test-plan.md`
- `SIMULATION.md`
- Any documentation that describes the system's intended behavior

## What you must produce

Write these two files to `eval-run/`:

1. **`BRD.md`** — A Business Requirements Document following the structure in stage-2-extraction.md. Use requirement prefixes derived from the discovered modules (from 00-calibrate).
2. **`spec.json`** — A machine-readable specification following the JSON structure in stage-2-extraction.md.

## Key instructions from the updated skill

- **Ground on the calibration profile**: derive requirement prefixes from discovered modules, enumerate rules from the discovered rule interface, name integrations from discovered queue literals/API usage, build state machines from discovered status constants.
- **Use the new advanced analysis outputs**: scripts 13-18 provide critical-value flows, decision tables (with resolved constant values), failure semantics (fail-open/fail-closed per rule), operation sequence (the ordered transaction algorithm), constant provenance (drift detection), and entity mutation surface (lifecycle).
- **Do NOT assume anything from prior runs**: re-derive everything from the calibration output and analysis scripts.
- Be exhaustive: every threshold, rate, queue name, state transition, and anomaly must appear.
- Note when behavior differs from naming (e.g. "DailyVolumeLimitRule" that checks per-order volume).
- Include all special client overrides with exact values.
- Include all known bugs with preserve-for-backward-compatibility flags.
