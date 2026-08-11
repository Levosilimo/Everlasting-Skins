#!/usr/bin/env bash
# Self-test for scripts/config-order-gate.sh: proves the gate CATCHES the
# regression class it guards. Builds a throwaway worktree fixture (overlaid
# with the CURRENT working-tree convention, so uncommitted fixes are tested)
# that re-introduces each historical bad form, then runs the gate's
# structural guard against it and asserts a non-zero exit with the
# signature. Also runs the full gate (incl. the CI-mirror probe) on the
# real tree to prove the happy path.
#
# Usage: bash scripts/config-order-gate-test.sh
# Exit 0 = gate proven; 1 = gate failed to catch a bad form (or the happy
# path broke).

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)" || exit 1
cd "$ROOT" || exit 1

CONVENTION="buildSrc/src/main/kotlin/everlastingskins.forge-module.gradle.kts"
FIXTURE_DIR="$ROOT/.slim/worktrees/config-order-gate-fixture"
FIXTURE_BRANCH=""

fail() {
  echo "SELFTEST FAIL: $*" >&2
  exit 1
}

cleanup() {
  if [ -n "$FIXTURE_BRANCH" ]; then
    git branch -q -D "$FIXTURE_BRANCH" 2>/dev/null || true
  fi
  git worktree remove "$FIXTURE_DIR" --force 2>/dev/null || true
  git worktree prune 2>/dev/null || true
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Fixture: a detached worktree at HEAD with the working-tree convention
# overlaid and one of the historical bad forms re-introduced in place of
# the canonical from() lines.
# ---------------------------------------------------------------------------
make_fixture() {
  local bad_line="$1"
  cleanup
  FIXTURE_BRANCH="config-order-gate-fixture"
  git worktree add -q --detach "$FIXTURE_DIR" HEAD || fail "worktree add"
  # Overlay the CURRENT working-tree convention + gate scripts (uncommitted
  # fixes included).
  cp "$ROOT/$CONVENTION" "$FIXTURE_DIR/$CONVENTION"
  cp "$ROOT/scripts/config-order-gate.sh" "$FIXTURE_DIR/scripts/config-order-gate.sh"
  # Drop the canonical TaskProvider from() lines + the doFirst guard, then
  # insert the bad line after the duplicatesStrategy line.
  sed -i '/from(project(":common").tasks.named(/d; /doFirst {/,/^    }$/d' \
    "$FIXTURE_DIR/$CONVENTION"
  sed -i "/duplicatesStrategy = DuplicatesStrategy.INCLUDE/a\\    $bad_line" \
    "$FIXTURE_DIR/$CONVENTION"
}

# Each case: the bad line. The gate must reject it (structural guard) with
# the regression-signature message.
run_case() {
  local name="$1" bad_line="$2"
  echo "--- self-test case: $name"
  make_fixture "$bad_line"
  if ( cd "$FIXTURE_DIR" && CONFIG_ORDER_STRUCTURAL_ONLY=1 \
      bash scripts/config-order-gate.sh ) \
      > /tmp/config-order-gate-test.log 2>&1; then
    fail "case '$name': gate exited 0 — the bad form was NOT caught"
  fi
  if ! grep -q "config-order regression signature" /tmp/config-order-gate-test.log; then
    fail "case '$name': gate failed but without the regression-signature message: $(tail -2 /tmp/config-order-gate-test.log)"
  fi
  echo "case '$name': gate rejected it (exit non-zero, signature logged) — OK"
}

# Historical bad forms (takes 1-2 of the PR #440 fix):
run_case "take-1 eager sourceSets" \
  'from(project(":common").sourceSets.main.get().output)'
run_case "take-1 lazy provider wrap" \
  'from(project.provider { project(":common").sourceSets.main.get().output })'
run_case "take-2 projectsEvaluated sourceSets" \
  'from(project(":common").sourceSets.main.get().output) // projectsEvaluated'

# ---------------------------------------------------------------------------
# Happy path: the full gate (structural guard + CI-mirror probe) must PASS
# on the real tree.
# ---------------------------------------------------------------------------
echo "--- self-test case: happy path (current tree, full gate incl. probe)"
if bash scripts/config-order-gate.sh > /tmp/config-order-gate-test.log 2>&1; then
  echo "happy path: gate PASSED — OK"
else
  tail -n 20 /tmp/config-order-gate-test.log
  fail "happy path: gate failed on the canonical tree (see tail)"
fi

echo "config-order gate self-test: PASS"
