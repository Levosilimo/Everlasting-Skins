#!/usr/bin/env bash
# Vendored SpecialSource harness diff-guard (lib-12).
#
# The forge-1.6.4 / forge-1.5.2 / (future) forge-1.4.7 out-of-band lanes
# keep the vendored SpecialSource remap harness as copy-paste per the
# lane-separation doctrine (research-vendored-harness-abstraction.json,
# option C). Copy-paste already diverged once (the subclass-owner reobf
# fix landed in 1.5.2 but not 1.6.4), so this guard fails CI when a lane's
# build.gradle drifts from the canonical harness beyond the allowed
# substitution set and the whitelisted per-lane fork lines.
#
# Mechanics:
#   1. sed-normalize each lane's build.gradle: strip the allowed
#      substitution set (MC version, forge version, MCP version, mcp* conf
#      dir, forge* package, archivesBaseName, vendored artifact digests).
#   2. Drop lines matching the whitelisted per-lane fork patterns
#      (scripts/vendored-harness-fork-lines.txt) from BOTH sides.
#   3. diff against the checked-in canonical expected-normalized pattern
#      (scripts/canonical/vendored-harness-normalized.txt), generated from
#      forge-1.6.4 via --update. Any residual divergence fails the build.
#
# Regenerate the canonical pattern after a deliberate harness-wide
# evolution (and only then):
#   bash scripts/ci-vendored-harness-diff-guard.sh --update
#
# Usage: bash scripts/ci-vendored-harness-diff-guard.sh [--update]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_NAME="$(basename "$0")"
CANONICAL="$ROOT/scripts/canonical/vendored-harness-normalized.txt"
FORK_LINES="$ROOT/scripts/vendored-harness-fork-lines.txt"

# Vendored-harness lanes, oldest first. The first lane present is the
# canonical reference; later lanes must match it modulo the substitution
# set and the fork whitelist. Missing lanes are skipped (forge-1.5.2 and
# forge-1.4.7 do not exist in the monorepo yet; the guard picks them up
# automatically when they land).
LANES=(forge-1.6.4 forge-1.5.2 forge-1.4.7)

# sed-normalize a build.gradle: strip the allowed substitution set.
normalize() {
    sed -E \
        -e 's/forge-1\.(6\.4|5\.2|4\.7)/forge-LANE/g' \
        -e 's/1\.(6\.4|5\.2|4\.7)/MCVER/g' \
        -e 's/[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/FORGEVER/g' \
        -e 's/mcp[0-9]+/mcpMCP/g' \
        -e 's/MCPVersion ?= ?[0-9.]+/MCPVersion = MCPVER/g' \
        -e 's/MCP [0-9.]+ conf/MCP MCPVER conf/g' \
        -e 's/forge[0-9]+/forgePKG/g' \
        -e "s/archivesBaseName = '[^']*'/archivesBaseName = 'everlastingskins-LANE'/" \
        -e "s/digest: '[0-9a-fA-F]{16,64}'/digest: 'DIGEST'/g" \
        -e 's/[0-9a-f]{40}/SHA1/g' \
        "$1"
}

# Whitelisted per-lane fork patterns, one ERE per non-comment line.
fork_patterns() {
    grep -vE '^[[:space:]]*(#|$)' "$FORK_LINES" | paste -sd'|' -
}

# Normalize $1 then drop whitelisted fork lines.
filter() {
    local pat
    pat="$(fork_patterns)"
    normalize "$1" | grep -vE "$pat" || true
}

if [ "${1:-}" = "--update" ]; then
    canonical_lane=""
    for lane in "${LANES[@]}"; do
        if [ -f "$ROOT/$lane/build.gradle" ]; then
            canonical_lane="$lane"
            break
        fi
    done
    if [ -z "$canonical_lane" ]; then
        echo "ERROR: no vendored-harness lane found (looked for: ${LANES[*]})" >&2
        exit 1
    fi
    mkdir -p "$(dirname "$CANONICAL")"
    filter "$ROOT/$canonical_lane/build.gradle" > "$CANONICAL"
    echo "Updated canonical harness pattern from $canonical_lane/build.gradle: $CANONICAL"
    exit 0
fi

if [ ! -f "$CANONICAL" ]; then
    echo "ERROR: canonical pattern missing: $CANONICAL" >&2
    echo "Generate it: bash $SCRIPT_NAME --update" >&2
    exit 1
fi

FAILED=0
for lane in "${LANES[@]}"; do
    f="$ROOT/$lane/build.gradle"
    if [ ! -f "$f" ]; then
        echo "SKIP: $lane (lane not present yet)"
        continue
    fi
    tmp="$(mktemp)"
    filter "$f" > "$tmp"
    if ! diff -u "$CANONICAL" "$tmp" > "$tmp.diff"; then
        FAILED=1
        echo "FAIL: $lane/build.gradle diverges from the canonical harness (first 60 diff lines):" >&2
        sed -n '1,60p' "$tmp.diff" >&2
    else
        echo "PASS: $lane/build.gradle matches the canonical harness"
    fi
    rm -f "$tmp" "$tmp.diff"
done

if [ "$FAILED" -eq 1 ]; then
    echo "ERROR: vendored harness drift detected. Harness-wide changes must regenerate" >&2
    echo "the canonical pattern deliberately: bash $SCRIPT_NAME --update" >&2
    exit 1
fi

echo "Vendored harness diff-guard OK"
