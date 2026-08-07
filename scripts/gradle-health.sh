#!/usr/bin/env bash
# Dependency-analysis buildHealth: runs the autonomousapps buildHealth report
# in offline mode. Knip-equivalent dep hygiene (unused deps, wrong-config,
# undeclared transitives) for the Gradle root build.
#
# WARN-only hygiene: exits 0 always; never a fail gate until false positives
# (Forge reflection) are catalogued.
#
# Usage: bash scripts/gradle-health.sh [-v]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERBOSE=0
if [ "${1:-}" = "-v" ]; then
  VERBOSE=1
fi

# Per-project projectHealth iteration. buildHealth is a root-level
# aggregate (DependencyAnalysisPlugin.applyForRoot guards
# `if (this == rootProject)`), so we run each consumer's
# per-project task individually. Consumer list is the script's source
# of truth — append future modules here as they adopt the convention.
CONSUMERS=(
    ":common"
    ":forge-1.21"
    ":forge-1.21.1"
    ":forge-1.21.4"
    ":forge-1.21.8"
    ":forge-26.2"
)

for c in "${CONSUMERS[@]}"; do
    if [ -z "$c" ] || [[ "$c" =~ ^# ]]; then
        continue
    fi
    echo "[gradle-health] ${c}:projectHealth..."
    ./gradlew --no-daemon --offline "${c}:projectHealth" || true
    case "$c" in
        :common) report="common/build/reports/dependency-analysis/project-health-report.txt" ;;
        *) report="${c#:}/build/reports/dependency-analysis/project-health-report.txt" ;;
    esac
    if [ -f "$report" ]; then
        echo "[gradle-health] ${c} report: $report"
    fi
done

exit 0
