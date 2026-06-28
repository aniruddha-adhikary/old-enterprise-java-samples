#!/bin/bash
# run-all.sh — Build a CPG (if needed) and run every analysis script, collecting output.
#
# Usage:
#   bash run-all.sh [SOURCE_DIR] [CPG_FILE] [OUTPUT_DIR]
# Defaults:
#   SOURCE_DIR  = current directory
#   CPG_FILE    = ./joern-workspace/project.cpg
#   OUTPUT_DIR  = ./joern-workspace/output
#
# Requires `joern` and `javasrc2cpg` on PATH. Install Joern from
# https://github.com/joernio/joern (joern-install.sh) and add joern-cli to PATH.

set -u  # NOTE: intentionally NOT `set -e` — one failing script must not abort the rest.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_DIR="${1:-$(pwd)}"
CPG_FILE="${2:-$(pwd)/joern-workspace/project.cpg}"
OUTPUT_DIR="${3:-$(pwd)/joern-workspace/output}"

if ! command -v joern >/dev/null 2>&1; then
    echo "ERROR: 'joern' not on PATH. Install Joern and add its joern-cli dir to PATH." >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR" "$(dirname "$CPG_FILE")"

echo "=== Joern Business Logic Extraction ==="
echo "Source: $SOURCE_DIR"
echo "CPG:    $CPG_FILE"
echo "Output: $OUTPUT_DIR"
echo ""

# Build the CPG from source if it doesn't exist yet.
if [ ! -f "$CPG_FILE" ]; then
    echo "Building CPG from source (javasrc2cpg)..."
    javasrc2cpg "$SOURCE_DIR" -o "$CPG_FILE" || { echo "CPG build failed" >&2; exit 1; }
    echo "CPG created."
fi

# 00-calibrate runs first; its profile (root package, modules) grounds the rest.
SCRIPTS=(
    "00-calibrate"
    "01-explore-cpg"
    "02-domain-models"
    "03-business-rules"
    "04-data-flows"
    "05-architectural-patterns"
    "06-commission-and-pricing"
    "07-integration-topology"
    "08-anomalies-and-debt"
    "09-jira-references"
    "10-state-machines"
    "11-sql-schema"
    "12-context-attributes"
    # Advanced semantic analyses (data flow, decision tables, failure behavior, sequencing,
    # constant provenance, entity lifecycle). Heavier than 01-12 but high-signal. These are
    # DOMAIN-NEUTRAL and tuned by a domain-profile.env if present. See
    # references/advanced-semantic-analyses.md and references/grounding-on-current-project.md.
    "13-critical-value-flows"
    "14-decision-tables-and-guards"
    "15-failure-semantics"
    "16-operation-sequence"
    "17-constant-provenance-and-clones"
    "18-entity-mutation-surface"
)

# Optional domain profile: tunes the advanced scripts to THIS project's domain. Authored by the
# agent from 00-calibrate output (see references/domain-profile.template.env). Looked for next to
# the source, the workspace, or cwd. Absent => scripts use generic defaults (still work).
for cand in "$SOURCE_DIR/domain-profile.env" "$(dirname "$CPG_FILE")/domain-profile.env" "$(dirname "$OUTPUT_DIR")/domain-profile.env" "./domain-profile.env"; do
    if [ -f "$cand" ]; then . "$cand"; echo "Loaded domain profile: $cand"; break; fi
done

# Per-script extra --param args (root package from calibration + domain-profile tuning).
# Values are comma-lists with no spaces, so unquoted expansion in the call is safe.
ROOT_PKG=""
extra_params() {
    case "$1" in
        07-integration-topology|18-entity-mutation-surface)
            [ -n "$ROOT_PKG" ] && echo "--param rootPackage=$ROOT_PKG" ;;
        13-critical-value-flows|16-operation-sequence)
            [ -n "${COMPUTE_VERBS:-}" ] && echo "--param computeVerbs=$COMPUTE_VERBS" ;;
        14-decision-tables-and-guards)
            [ -n "${STAKES_VERBS:-}" ] && echo "--param stakesVerbs=$STAKES_VERBS" ;;
        15-failure-semantics)
            [ -n "${CRITICAL_MODULES:-}" ] && echo "--param criticalModules=$CRITICAL_MODULES" ;;
    esac
}

for script in "${SCRIPTS[@]}"; do
    echo -n "Running $script... "
    joern --script "$SCRIPT_DIR/${script}.sc" \
        --param cpgFile="$CPG_FILE" $(extra_params "$script") \
        > "$OUTPUT_DIR/${script}.txt" 2>&1
    rc=$?
    lines=$(grep -vcE '^\[|^$' "$OUTPUT_DIR/${script}.txt")
    echo "done (rc=$rc, $lines report lines)"
    if [ "$script" = "00-calibrate" ]; then
        ROOT_PKG=$(grep -A1 '^--- ROOT PACKAGE ---' "$OUTPUT_DIR/${script}.txt" | tail -1 | tr -d ' ')
        echo "  -> detected root package: '${ROOT_PKG:-<none>}'"
    fi
done

echo ""
echo "=== All scripts complete ==="
ls -lh "$OUTPUT_DIR"/*.txt

# Generate combined summary (report content only, Joern INFO/log lines stripped).
{
    echo "# Joern Business Logic Extraction — Combined Report"
    echo "# Source: $SOURCE_DIR"
    echo ""
    for script in "${SCRIPTS[@]}"; do
        echo ""
        echo "################################################################"
        echo "# SECTION: $script"
        echo "################################################################"
        grep -vE '^\[(INFO|WARN|ERROR)' "$OUTPUT_DIR/${script}.txt" | grep -vE '^(closing|writing|closed) ' || true
        echo ""
    done
} > "$OUTPUT_DIR/combined-report.txt"

echo "Combined report: $OUTPUT_DIR/combined-report.txt ($(grep -c '' "$OUTPUT_DIR/combined-report.txt") lines)"
