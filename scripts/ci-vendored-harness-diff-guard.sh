#!/usr/bin/env bash
# Vendored SpecialSource harness adoption guard (lib-12 Option B graduation).
#
# Since the Option B graduation the vendored SpecialSource remap harness
# lives ONCE in harness/specialsource-harness.gradle (repo root) and each
# pre-1.7.10 lane consumes it via `apply from:` + a per-lane harnessConfig
# block (in-repo precedent: mc1.12.2/build.gradle:166). This guard fails CI
# when:
#   1. a vendored-harness lane does not apply the shared script, or
#   2. a lane re-defines any of the 7 harness tasks locally (copy-paste
#      regression — the subclass-owner divergence that Option B eliminates),
#   3. a lane's harnessConfig is missing a required key (vendoredInputs,
#      mcpVersion, archiveBaseName, mergeOrder), or
#   4. the shared script loses one of the 7 harness tasks.
#
# The previous normalized-diff mechanics (scripts/canonical/
# vendored-harness-normalized.txt + scripts/vendored-harness-fork-lines.txt)
# were removed in the same change: with one shared harness there is no
# per-lane body to diff against a canonical copy.
#
# Usage: bash scripts/ci-vendored-harness-diff-guard.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHARED="$ROOT/harness/specialsource-harness.gradle"

# Vendored-harness lanes, oldest first. Missing lanes are skipped (the guard
# picks them up automatically when they land).
LANES=(forge-1.6.4 forge-1.5.2 forge-1.4.7)

# The 7 harness tasks the shared script must define (compileJava is a
# built-in task the script configures, not defines — it is listed separately).
HARNESS_TASKS=(resolveVendored extractMcpConf deobfClasspath reobf assertNameDomain verifyNoMixin)
HARNESS_CONFIGURED_TASKS=(compileJava)

# Required harnessConfig keys (the 4 behavioral forks + identity keys).
REQUIRED_KEYS=(vendoredInputs mcpVersion archiveBaseName mergeOrder)

FAILED=0

if [ ! -f "$SHARED" ]; then
    echo "ERROR: shared harness script missing: $SHARED" >&2
    exit 1
fi

# The shared script must still define/configure all 7 tasks.
for task in "${HARNESS_TASKS[@]}"; do
    if ! grep -qE "task ${task}(\(|\{|\s|$)" "$SHARED"; then
        FAILED=1
        echo "FAIL: $SHARED lost task '${task}'" >&2
    fi
done
for task in "${HARNESS_CONFIGURED_TASKS[@]}"; do
    if ! grep -qE "^${task} \{" "$SHARED"; then
        FAILED=1
        echo "FAIL: $SHARED lost configuration of built-in task '${task}'" >&2
    fi
done

for lane in "${LANES[@]}"; do
    f="$ROOT/$lane/build.gradle"
    if [ ! -f "$f" ]; then
        echo "SKIP: $lane (lane not present yet)"
        continue
    fi

    lane_failed=0

    # 1. Must apply the shared script (relative path from the lane dir).
    if ! grep -qE "apply from: '\.\./harness/specialsource-harness\.gradle'" "$f"; then
        lane_failed=1
        echo "FAIL: $lane/build.gradle does not apply the shared harness script (expected: apply from: '../harness/specialsource-harness.gradle')" >&2
    fi

    # 2. Must NOT re-define any harness task locally (copy-paste regression).
    for task in "${HARNESS_TASKS[@]}"; do
        if grep -qE "task ${task} *(\{|$)" "$f"; then
            lane_failed=1
            echo "FAIL: $lane/build.gradle re-defines harness task '${task}' locally — the harness must live only in $SHARED" >&2
        fi
    done

    # 3. harnessConfig must carry the required keys.
    for key in "${REQUIRED_KEYS[@]}"; do
        if ! grep -qE "^\s*${key}:" "$f"; then
            lane_failed=1
            echo "FAIL: $lane/build.gradle harnessConfig is missing required key '${key}'" >&2
        fi
    done

    if [ "$lane_failed" -eq 0 ]; then
        echo "PASS: $lane/build.gradle applies the shared harness (harnessConfig: $(grep -oE '^\s*[a-zA-Z]+:' "$f" | tr -d ' :' | tr '\n' ' '))"
    else
        FAILED=1
    fi
done

if [ "$FAILED" -eq 1 ]; then
    echo "ERROR: vendored harness adoption drift detected. All harness logic must live" >&2
    echo "in $SHARED; lanes only configure it via harnessConfig." >&2
    exit 1
fi

echo "Vendored harness diff-guard OK ($(basename "$SHARED") defines all ${#HARNESS_TASKS[@]} tasks; all present lanes apply it)"
