#!/usr/bin/env bash
# gh-api-bump/1.8.9.sh — bump the required-check contract by appending
# "Build (forge-1.8.9)" to the required_status_checks contexts on `main` AND
# `integration/m2-monorepo`.
#
# PATCH, not PUT: this script PATCHes /branches/<branch>/protection/
# required_status_checks. PUT returns 404 on the protection subresource
# (verified empirically during the three-lane expansion deadlock-resolution
# sequence, 2026-08-06); PATCH is the documented method for the
# required_status_checks subresource.
#
# Merge order: forge-26.2 first (12 -> 13, adds "Build (26.2)"), then
# forge-1.8.9 (this script: 13 -> 14), then forge-1.7.10 (14 -> 15, its own
# PR). Run this AT PR-OPEN, BEFORE merging the lane PR, so the new CI cell is
# enforced as required rather than informational.
#
# Idempotent re-run is a no-op even if the live contract has moved past the
# expected baseline: the already-present check runs first.
#
# Usage:
#   ./1.8.9.sh             # apply to both branches (admin rights)
#   ./1.8.9.sh --dry-run   # print the payload without applying
#   ./1.8.9.sh --verify <sha>   # confirm the context after first CI run
set -euo pipefail

REPO="Levosilimo/Everlasting-Skins"
BRANCHES=("main" "integration/m2-monorepo")
NEW_CONTEXT="Build (forge-1.8.9)"
# Expected 13-context contract AFTER forge-26.2 merged (12 original +
# "Build (26.2)"). CI job names must match the required-check strings exactly.
EXPECTED_13=(
  "YAML Lint"
  "Build (common)"
  "Build (1.21)"
  "Build (1.21.1)"
  "Build (1.21.4)"
  "Build (1.21.8)"
  "Build (mc1.12.2)"
  "E2E (mc1.12.2)"
  "GameTest (1.21)"
  "Build (26.2)"
  "Build (forge-1.16.5)"
  "Build (forge-1.20.1)"
  "aislop (M2)"
)

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
}

DRY_RUN=0
case "${1:-}" in
  --dry-run) DRY_RUN=1 ;;
  --apply|"") ;;
  --verify)
    [ $# -lt 2 ] && { echo "usage: $0 --verify <commit-sha>" >&2; exit 2; }
    echo "==> check-runs for $2 (expect \"$NEW_CONTEXT\" among them)"
    gh api "repos/$REPO/commits/$2/check-runs" --jq '.check_runs[].name' \
      | tee /dev/stderr \
      | grep -qx "$NEW_CONTEXT" && { echo "OK: $NEW_CONTEXT present."; exit 0; } \
      || { echo "NOT YET: $NEW_CONTEXT missing (CI may not have run)." >&2; exit 1; }
    ;;
  --help|-h) usage; exit 0 ;;
  *) echo "usage: $0 [--apply | --dry-run | --verify <commit-sha>]" >&2; exit 2 ;;
esac

command -v gh >/dev/null 2>&1 || { echo "gh CLI required" >&2; exit 2; }

# 1) GET the current contexts[] from integration/m2-monorepo protection
echo "==> GET current required_status_checks on $REPO integration/m2-monorepo"
current="$(
  gh api "repos/$REPO/branches/integration/m2-monorepo/protection/required_status_checks" \
    --jq '{strict, contexts}'
)" || { echo "GET failed — is gh authenticated and branch protection readable?" >&2; exit 1; }

mapfile -t cur_ctx < <(printf '%s\n' "$current" | jq -r '.contexts[]')

# 2) Idempotency first: already bumped => no-op regardless of baseline drift
if printf '%s\n' "${cur_ctx[@]}" | grep -qx "$NEW_CONTEXT"; then
  echo "Already bumped: \"$NEW_CONTEXT\" is present. Nothing to do (idempotent no-op)."
  exit 0
fi

# 3) Guard: refuse unless the current contract is exactly the expected 13
count=${#cur_ctx[@]}
echo "    current context count: $count"

if [ "$count" -ne "${#EXPECTED_13[@]}" ]; then
  echo "REFUSE: expected ${#EXPECTED_13[@]} contexts, found $count." >&2
  printf '  %s\n' "${cur_ctx[@]}" >&2
  echo "  forge-26.2 must have merged first (12 -> 13). No change applied." >&2
  exit 1
fi
missing=()
for want in "${EXPECTED_13[@]}"; do
  found=0
  for have in "${cur_ctx[@]}"; do [ "$have" = "$want" ] && found=1 && break; done
  [ "$found" -eq 1 ] || missing+=("$want")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "REFUSE: current contexts do not match the expected 13. Missing:" >&2
  printf '  %s\n' "${missing[@]}" >&2
  echo "  No change applied." >&2
  exit 1
fi
# forge-26.2 must have merged: its context must be in the current list
printf '%s\n' "${cur_ctx[@]}" | grep -qx "Build (26.2)" \
  || { echo "REFUSE: \"Build (26.2)\" not in current contexts — forge-26.2 must merge first." >&2; exit 1; }

# 4) Build the 14-context payload, preserving the current strict flag
payload="$(printf '%s\n' "$current" | jq -c --arg new "$NEW_CONTEXT" \
  '{strict: (.strict // true), contexts: (.contexts + [$new])}')"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "==> DRY RUN — payload that would be PATCHed to both branches:"
  echo "$payload" | jq .
  echo "==> (no changes applied)"
  exit 0
fi

# 5) PATCH to both main AND integration/m2-monorepo
for branch in "${BRANCHES[@]}"; do
  echo "==> PATCH $branch required_status_checks"
  gh api -X PATCH "repos/$REPO/branches/$branch/protection/required_status_checks" \
    -H 'Accept: application/vnd.github+json' \
    --input - <<<"$payload" >/dev/null
done

echo "==> Done: 13 -> 14 (added \"$NEW_CONTEXT\") on ${BRANCHES[*]}."
echo "==> After the first CI run, verify:"
echo "    $0 --verify \$(git rev-parse HEAD)"
