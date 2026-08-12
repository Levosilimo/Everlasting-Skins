#!/usr/bin/env bash
# Dependency-analysis buildHealth: runs the autonomousapps buildHealth report
# in offline mode. Knip-equivalent dep hygiene (unused deps, wrong-config,
# undeclared transitives) for the Gradle root build.
#
# Per-lane failure semantics: :common exits 0 always (WARN-forever by
# design — aggregate-jar FPs, no Forge runtime). Graduated lanes
# (depAnalysis.graduateDuplicateClass=true, P2-6: forge-1.21/1.21.1/1.21.4/
# 1.21.8/26.1/26.2) FAIL on a duplicate-class finding: the convention sets
# duplicate-class severity to fail, so the projectHealth task itself exits
# nonzero exactly when that zero-FP category fires (BuildHealthException;
# verified against dependency-analysis-gradle-plugin 3.18.0). WARN-only
# categories (Forge-reflection FPs) never trip it. CI Health is a REQUIRED
# check (gh-api-bump/CI-Health.sh, lib-69), so the script propagates the
# failure; local runs and pre-push get the same signal.
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
    ":forge-26.1"
)

FAILED=0

for c in "${CONSUMERS[@]}"; do
    if [ -z "$c" ] || [[ "$c" =~ ^# ]]; then
        continue
    fi
    echo "[gradle-health] ${c}:projectHealth..."
    case "$c" in
        :common)
            # WARN-forever by design (aggregate-jar FPs, no Forge runtime):
            # the task can never fail here, so swallow its exit code.
            ./gradlew --no-daemon --offline --configure-on-demand "${c}:projectHealth" || true
            ;;
        *)
            # Graduated lane: duplicate-class severity is fail (P2-6), so a
            # nonzero exit means a duplicate-class finding — the zero-FP
            # category. WARN-only categories can never fail the task.
            if ! ./gradlew --no-daemon --offline --configure-on-demand "${c}:projectHealth"; then
                echo "[gradle-health] ${c}:projectHealth FAILED: duplicate-class finding on a graduated lane (see ${c#:}/build/reports/dependency-analysis/project-health-report.txt)" >&2
                FAILED=1
            fi
            ;;
    esac
    report="${c#:}/build/reports/dependency-analysis/project-health-report.txt"
    if [ -f "$report" ]; then
        echo "[gradle-health] ${c} report: $report"
    fi
done

if [ "$FAILED" -ne 0 ]; then
    echo "[gradle-health] FAILED: duplicate-class findings on a graduated lane (CI Health is a required check)" >&2
    exit 1
fi
exit 0
