#!/usr/bin/env bash
# Dependency-analysis buildHealth: runs the autonomousapps buildHealth report.
# Knip-equivalent dep hygiene (unused deps, wrong-config, undeclared
# transitives) for the Gradle root build.
#
# Per-lane failure semantics: :common exits 0 always (WARN-forever by
# design — aggregate-jar FPs, no Forge runtime). Graduated lanes
# (depAnalysis.graduateDuplicateClass=true, P2-6: forge-1.21/1.21.1/1.21.4/
# 1.21.8/26.1/26.2) FAIL on a projectHealth failure: the convention sets
# duplicate-class severity to fail, so the projectHealth task itself exits
# nonzero exactly when that zero-FP category fires (BuildHealthException;
# verified against dependency-analysis-gradle-plugin 3.18.0). WARN-only
# categories (Forge-reflection FPs) never trip it. CI Health is a REQUIRED
# check (gh-api-bump/CI-Health.sh, lib-69), so the script propagates the
# failure; local runs and pre-push get the same signal.
#
# Notes (hard-won, 2026-08-12):
# - Runs ONLINE by default. CI Health previously ran --offline, which made
#   the required check fail on a cold Gradle cache (foojay-resolver-convention
#   and the FG userdev classpath unresolvable) — the failure was masked by
#   `|| true` for months and surfaced the moment the script started
#   propagating exit codes. Pass --offline only with a warm cache.
# - NEVER pass --configure-on-demand here: it corrupts the dep-analysis task
#   graph (explodeJar sees an empty jar input and crashes with
#   "Index 6 out of bounds for length 0"). The config-order gate uses that
#   flag for a different purpose (the :forge-1.21:jar probe); this sweep
#   needs the full graph.
#
# Usage: bash scripts/gradle-health.sh [-v] [--offline] [--lanes :a,:b,...]
#   --lanes <csv>  restrict the sweep to the given projects (colon-prefixed,
#                  comma-separated). Defaults to the full consumer set below.
#                  CI Health (ci.yml) passes the graduated-lane list
#                  explicitly so the required check stays deterministic.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERBOSE=0
OFFLINE=()
LANES=()
args=("$@")
for ((i = 0; i < $#; i++)); do
  case "${args[$i]}" in
    -v) VERBOSE=1 ;;
    --offline) OFFLINE=(--offline) ;;
    --lanes) i=$((i + 1)); IFS=',' read -r -a LANES <<< "${args[$i]:-}" ;;
    *) echo "unknown arg: ${args[$i]}" >&2; exit 2 ;;
  esac
done

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

if [ "${#LANES[@]}" -gt 0 ]; then
    CONSUMERS=("${LANES[@]}")
fi

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
            ./gradlew --no-daemon "${OFFLINE[@]}" "${c}:projectHealth" || true
            ;;
        *)
            # Graduated lane: duplicate-class severity is fail (P2-6), so a
            # nonzero exit means a duplicate-class finding — the zero-FP
            # category. WARN-only categories can never fail the task.
            # Any other failure (resolution, compile) is a real problem too —
            # surface it with the gradle tail for diagnosis.
            if ! out="$(./gradlew --no-daemon "${OFFLINE[@]}" "${c}:projectHealth" 2>&1)"; then
                echo "[gradle-health] ${c}:projectHealth FAILED (see report: ${c#:}/build/reports/dependency-analysis/project-health-report.txt)" >&2
                echo "$out" | grep -E "^FAILURE|^> Task.*FAILED|What went wrong|^Execution failed|error:" | head -8 >&2
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
    echo "[gradle-health] FAILED: projectHealth failed on a graduated lane (CI Health is a required check)" >&2
    exit 1
fi
exit 0
