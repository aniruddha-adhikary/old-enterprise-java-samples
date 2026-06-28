#!/bin/bash
# generate-companion-skill.sh — Emit a project-specific COMPANION SKILL from a domain profile.
#
# The generic legacy-code-reconstruction skill stays domain-neutral. This generator bakes a
# discovered domain profile into a thin, self-triggering wrapper skill so future work on THIS
# project (or this domain) activates with the right parameters and reconstructed domain notes,
# delegating the actual technique back to the generic skill.
#
# Usage:
#   generate-companion-skill.sh [PROFILE_ENV] [OUTPUT_DIR]
# Defaults:
#   PROFILE_ENV = ./domain-profile.env
#   OUTPUT_DIR  = <sibling of the generic skill>/<DOMAIN_SLUG>-reconstruction
#
# The profile is authored by the agent from 00-calibrate output; see
# references/domain-profile.template.env.

set -u
PROFILE="${1:-./domain-profile.env}"
GENERIC_SKILL_DIR="$(cd "$(dirname "$0")/.." && pwd)"          # .../legacy-code-reconstruction
SKILLS_ROOT="$(dirname "$GENERIC_SKILL_DIR")"                  # .../skills

[ -f "$PROFILE" ] || { echo "ERROR: profile not found: $PROFILE" >&2; exit 1; }
# shellcheck disable=SC1090
. "$PROFILE"

: "${DOMAIN_SLUG:?set DOMAIN_SLUG in the profile}"
: "${DOMAIN_LABEL:?set DOMAIN_LABEL in the profile}"
OUTPUT_DIR="${2:-$SKILLS_ROOT/${DOMAIN_SLUG}-reconstruction}"

# Build a readable trigger phrase from DOMAIN_KEYWORDS (fallback to the label).
KW="${DOMAIN_KEYWORDS:-}"
TRIGGER="${KW//,/, }"
[ -z "$TRIGGER" ] && TRIGGER="$DOMAIN_LABEL"

mkdir -p "$OUTPUT_DIR"
cp "$PROFILE" "$OUTPUT_DIR/domain-profile.env"

cat > "$OUTPUT_DIR/SKILL.md" <<EOF
---
name: ${DOMAIN_SLUG}-reconstruction
description: Reconstruct business logic and requirements for the ${DOMAIN_LABEL} system from its legacy source. Use when the user asks to understand, document, reverse-engineer, or extract business rules from this codebase, or mentions ${TRIGGER}. This is a domain-tuned wrapper over the generic legacy-code-reconstruction skill, pre-loaded with this project's discovered domain profile.
---

# ${DOMAIN_LABEL} — Business Logic Reconstruction (domain-tuned)

Auto-generated companion skill. It pins this project's **domain profile** so the generic
pipeline runs tuned to ${DOMAIN_LABEL} without re-deriving the domain each time.

> Method lives in the generic skill: \`../legacy-code-reconstruction/SKILL.md\`.
> This file only carries the domain adaptation. Regenerate after big refactors with
> \`../legacy-code-reconstruction/tools/generate-companion-skill.sh domain-profile.env\`.

## Discovered domain profile

| Field | Value |
|-------|-------|
| Domain | ${DOMAIN_LABEL} |
| Root package | ${ROOT_PACKAGE:-(auto-derived)} |
| Core entities | ${CORE_ENTITIES:-(see calibration)} |
| Compute verbs | ${COMPUTE_VERBS:-(generic defaults)} |
| High-stakes verbs | ${STAKES_VERBS:-(generic defaults)} |
| Critical modules | ${CRITICAL_MODULES:-(all internal)} |

## How to run (tuned)

\`\`\`bash
GEN=../legacy-code-reconstruction/references/joern-scripts
# domain-profile.env (shipped beside this skill) tunes scripts 13-16 automatically:
cp domain-profile.env "\$REPO/joern-workspace/domain-profile.env"
bash "\$GEN/run-all.sh" "\$REPO" "\$REPO/joern-workspace/project.cpg" "\$REPO/joern-workspace/output"
\`\`\`

Or pass the params directly to individual scripts, e.g.:
\`\`\`bash
joern --script "\$GEN/13-critical-value-flows.sc" \\
  --param cpgFile="\$REPO/joern-workspace/project.cpg" \\
  --param computeVerbs="${COMPUTE_VERBS:-calculate,compute,apply}"
\`\`\`

Then follow Stage 2 / Stage 3 of the generic skill, treating **${CORE_ENTITIES:-the core entities}**
as the spec's central value-objects.
EOF

echo "Generated companion skill:"
echo "  $OUTPUT_DIR/SKILL.md"
echo "  $OUTPUT_DIR/domain-profile.env"
