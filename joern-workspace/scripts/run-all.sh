#!/bin/bash
# run-all.sh — Execute all Joern analysis scripts and collect output
# Usage: bash joern-workspace/scripts/run-all.sh [path/to/cpg]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_DIR="$(dirname "$WORKSPACE_DIR")"
OUTPUT_DIR="$WORKSPACE_DIR/output"
CPG_FILE="${1:-$WORKSPACE_DIR/bigcorp.cpg}"

# Ensure Joern is on PATH
export PATH="/home/ubuntu/bin/joern/joern-cli:$PATH"

mkdir -p "$OUTPUT_DIR"

echo "=== Joern Business Logic Extraction ==="
echo "CPG: $CPG_FILE"
echo "Output: $OUTPUT_DIR"
echo ""

# If CPG doesn't exist, create it
if [ ! -f "$CPG_FILE" ]; then
    echo "Creating CPG from source..."
    cd "$REPO_DIR"
    javasrc2cpg . -o "$CPG_FILE"
    echo "CPG created."
fi

SCRIPTS=(
    "01-explore-cpg"
    "02-domain-models"
    "03-business-rules"
    "04-data-flows"
    "05-architectural-patterns"
    "06-commission-and-pricing"
    "07-integration-topology"
    "08-anomalies-and-debt"
)

for script in "${SCRIPTS[@]}"; do
    echo -n "Running $script... "
    cd "$REPO_DIR"
    joern --script "$SCRIPT_DIR/${script}.sc" \
        --param cpgFile="$CPG_FILE" \
        > "$OUTPUT_DIR/${script}.txt" 2>&1
    lines=$(wc -l < "$OUTPUT_DIR/${script}.txt")
    echo "done ($lines lines)"
done

echo ""
echo "=== All scripts complete ==="
echo "Output files in: $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR"/*.txt

# Generate combined summary
echo ""
echo "=== Generating combined summary ==="
{
    echo "# Joern Business Logic Extraction — Combined Report"
    echo "# Generated: $(date -u '+%Y-%m-%d %H:%M UTC')"
    echo "# Source: $CPG_FILE"
    echo ""
    for script in "${SCRIPTS[@]}"; do
        echo ""
        echo "################################################################"
        echo "# SECTION: $script"
        echo "################################################################"
        # Extract only the report content (skip Joern INFO lines)
        grep -v "^\[INFO\]" "$OUTPUT_DIR/${script}.txt" | grep -v "^closing/" | grep -v "^$" || true
        echo ""
    done
} > "$OUTPUT_DIR/combined-report.txt"

echo "Combined report: $OUTPUT_DIR/combined-report.txt ($(wc -l < "$OUTPUT_DIR/combined-report.txt") lines)"
