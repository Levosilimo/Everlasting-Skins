#!/usr/bin/env bash
# Config-order gate: fail-fast guard for the :common-bundling regression
# class that broke every in-root forge module's Build check (PR #440,
# takes 1-2). Two layers:
#
# 1. STRUCTURAL GUARD — rejects any bare sourceSets form of the :common
#    bundling in the shared convention, the regression signature:
#      from(project(":common").sourceSets...  (take-1 eager)
#      from(project.provider { project(":common").sourceSets...  (take-1 lazy)
#    Both fail with "Extension with name 'sourceSets' does not exist"
#    because the jar task's ConfigurableFileCollection resolves its from()
#    sources at configuration-time task-graph queries, before :common's
#    Java plugin is applied. The canonical form is the lazy TaskProvider:
#      from(project(":common").tasks.named("classes"))
# 2. CI-MIRROR PROBE — runs the exact CI build shape that failed locally:
#      ./gradlew --no-daemon --offline --configure-on-demand \
#                --no-configuration-cache --stacktrace <module>:jar
#    (the WorkValidationException / sourceSets failure reproduced ONLY
#    under --configure-on-demand + no config cache; a plain warm build
#    hides it). Exit 1 + classification on failure.
#
# Usage: bash scripts/config-order-gate.sh
# Env: CONFIG_ORDER_MODULES overrides the probe target list (default
# ":forge-1.21:jar"; 26.x excluded by default — their Java 25 toolchain
# needs a network toolchain download, incompatible with --offline).
# Env: CONFIG_ORDER_OFFLINE=0 drops --offline from the probe. CI sets it
# (run 31551098537: a cold ~/.gradle cannot resolve the settings-level
# foojay plugin / FG userdev graph offline — the gate job's actions/cache
# key namespace is separate from the Build jobs' setup-gradle cache, so
# the first run is always cold). Local pre-push keeps the default --offline
# because the local cache is warm.
#
# shellcheck disable=SC2317  # false positive: shellcheck follows the sourced
#                              # ensure-jdk17.sh and sees its final `exit 1` (no JDK
#                              # found), so everything after the source looks
#                              # unreachable; the source returns 0 on success.

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || exit 1
cd "$ROOT" || exit 1

CONVENTION="buildSrc/src/main/kotlin/everlastingskins.forge-module.gradle.kts"
MODULES="${CONFIG_ORDER_MODULES:-:forge-1.21:jar}"
# Default 1 (offline): local pre-push runs against a warm cache. CI sets 0
# so a cold ~/.gradle resolves the plugin/dependency graph online instead
# of failing on the settings-level foojay plugin (see header).
OFFLINE_ARGS=""
if [ "${CONFIG_ORDER_OFFLINE:-1}" = "1" ]; then
  OFFLINE_ARGS="--offline"
fi
# Self-test mode: skip the gradle probe (the structural guard is the unit
# under test; the probe needs a working convention).
STRUCTURAL_ONLY="${CONFIG_ORDER_STRUCTURAL_ONLY:-0}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

# ---------------------------------------------------------------------------
# Layer 1: structural guard (no gradle needed — runs even offline/cold).
# ---------------------------------------------------------------------------
if [ ! -f "$CONVENTION" ]; then
  fail "convention file missing: $CONVENTION"
fi
# Reject any :common bundling that goes through sourceSets (eager or
# provider-wrapped): the regression signature from takes 1-2. Comment lines
# are filtered first (the convention's own warning comment mentions the
# form).
CODE_ONLY="$(grep -vE '^\s*(//|/\*|\*)' "$CONVENTION" || true)"
# The single signature: a from() that reaches into :common's sourceSets
# (eager or provider-wrapped). Own-module sourceSets uses (errorprone /
# gametest classpath wiring) are legitimate and must NOT match.
if printf '%s\n' "$CODE_ONLY" | grep -nE 'from\([^)]*project\(":common"\)\.sourceSets'; then
  fail "config-order regression signature in $CONVENTION: bare :common.sourceSets from() — use the lazy TaskProvider form (from(project(\":common\").tasks.named(\"classes\")) + processResources)"
fi
echo "config-order: structural guard OK ($CONVENTION has no bare :common.sourceSets from())"

# ---------------------------------------------------------------------------
# Layer 2: CI-mirror probe (the exact command shape that reproduced the
# failure in CI). Skipped with CONFIG_ORDER_STRUCTURAL_ONLY=1 (self-test of
# the structural guard only).
# ---------------------------------------------------------------------------
if [ "$STRUCTURAL_ONLY" = "1" ]; then
  echo "config-order: structural-only mode (probe skipped)"
  echo "config-order gate: PASS"
  exit 0
fi

# Idempotent: no-op when JAVA_HOME is already a JDK >= 17 (CI's
# setup-java); promotes an old JAVA_HOME (e.g. sdkman `current` on JDK 8
# for the legacy lanes) to the newest installed JDK >= 17.
# shellcheck source=scripts/ensure-jdk17.sh
source scripts/ensure-jdk17.sh

for MODULE in $MODULES; do
  echo "config-order: probing $MODULE (CoD + no config cache${OFFLINE_ARGS:+ + offline})..."
  # shellcheck disable=SC2086  # OFFLINE_ARGS is empty or exactly "--offline"
  if ! ./gradlew --no-daemon $OFFLINE_ARGS --configure-on-demand \
      --no-configuration-cache --stacktrace "$MODULE" > /tmp/config-order-gate.log 2>&1; then
    if grep -qE "Extension with name 'sourceSets' does not exist|WorkValidationException|Configuration cache" /tmp/config-order-gate.log; then
      fail "config-order probe FAILED for $MODULE with the config-order signature (sourceSets/WorkValidationException); tail follows"
    fi
    echo "config-order: probe FAILED for $MODULE (non-signature failure); tail follows" >&2
    tail -n 40 /tmp/config-order-gate.log >&2
    exit 1
  fi
  echo "config-order: probe OK ($MODULE)"
done

echo "config-order gate: PASS"
