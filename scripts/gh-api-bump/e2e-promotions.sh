#!/usr/bin/env bash
# gh-api-bump/e2e-promotions.sh — promote the informational real-client E2E
# jobs to required-status contexts on `main` (Slice-2/3/4 completion of the
# master plan real-client-e2e-plan.md).
#
# PATCH, not PUT: this script PATCHes /branches/<branch>/protection/
# required_status_checks. PUT returns 404 on the protection subresource
# (verified empirically during the three-lane expansion deadlock-resolution
# sequence, 2026-08-06); PATCH is the documented method for the
# required_status_checks subresource.
#
# Scope (2026-08-14): the 22-context baseline (all ten Build-lane scripts
# applied) + the informational E2E jobs that have been green on the last >=3
# consecutive main runs, verified against `gh run list --workflow ci.yml
# --branch main --limit 10`:
#   - e2e-headlessmc trio (forge-1.7.10 / forge-1.8.9 / forge-1.10.2) — the
#     mandated Slice-2 promotion (same HeadlessMC driver as the already-
#     required E2E (mc1.12.2)).
#   - e2e-pre18 trio (forge-1.6.4 / forge-1.5.2 / forge-1.4.7) — 10/10 green.
#   - e2e-modern-121: forge-1.21 / 1.21.1 / 1.21.8 — 10/10 green (forge-1.21.4
#     NOT included: red in run 31771305288, HTTP 429 cold-cache dependency
#     resolution — not the lib-58 'Cache miss!' signature, so it counts as a
#     recent flake; keep informational until 3 consecutive greens).
#   - e2e-modern: forge-1.16.5 / 1.18.2 / 1.20.1 / 26.1 / 26.2 — 10/10 green
#     (26.1's 3 older failures were lib-58 Mavenizer 'Cache miss!' + asset-
#     download HttpTimeoutException infra noise; last 5 consecutive green).
#   - e2e-modern-1165-injar (forge-1.16.5 full in-jar) — 10/10 green.
#
# Idempotent re-run is a no-op: if every new context is already present, the
# script exits 0 without touching the contract, regardless of baseline drift.
#
# Usage:
#   ./e2e-promotions.sh             # apply to main (admin rights)
#   ./e2e-promotions.sh --dry-run   # print the payload without applying
#   ./e2e-promotions.sh --verify <sha>  # confirm the contexts after first CI run
set -euo pipefail

REPO="Levosilimo/Everlasting-Skins"
BRANCHES=("main")
# Expected 22-context contract AFTER all ten Build-lane bumps applied
# (README.md table; `Build (forge-1.4.7)` landed 21 -> 22). CI job names must
# match the required-check strings exactly.
EXPECTED_22=(
  "YAML Lint"
  "Build (common)"
  "Build (1.21)"
  "Build (1.21.1)"
  "Build (1.21.4)"
  "Build (1.21.8)"
  "Build (mc1.12.2)"
  "Build (forge-1.16.5)"
  "Build (forge-1.20.1)"
  "E2E (mc1.12.2)"
  "GameTest (1.21)"
  "aislop (M2)"
  "Build (26.2)"
  "Build (forge-1.7.10)"
  "Build (forge-1.8.9)"
  "CI Health"
  "Build (26.1)"
  "Build (forge-1.18.2)"
  "Build (forge-1.10.2)"
  "Build (forge-1.6.4)"
  "Build (forge-1.5.2)"
  "Build (forge-1.4.7)"
)
NEW_CONTEXTS=(
  "E2E (forge-1.7.10)"
  "E2E (forge-1.8.9)"
  "E2E (forge-1.10.2)"
  "E2E (forge-1.6.4)"
  "E2E (forge-1.5.2)"
  "E2E (forge-1.4.7)"
  "E2E (forge-1.21)"
  "E2E (forge-1.21.1)"
  "E2E (forge-1.21.8)"
  "E2E (forge-1.16.5)"
  "E2E (forge-1.16.5 full in-jar)"
  "E2E (forge-1.18.2)"
  "E2E (forge-1.20.1)"
  "E2E (forge-26.1)"
  "E2E (forge-26.2)"
)

usage() {
  sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
}

DRY_RUN=0
case "${1:-}" in
  --dry-run) DRY_RUN=1 ;;
  --apply|"") ;;
  --verify)
    [ $# -lt 2 ] && { echo "usage: $0 --verify <commit-sha>" >&2; exit 2; }
    echo "==> check-runs for $2 (expect all ${#NEW_CONTEXTS[@]} new E2E contexts among them)"
    # --paginate: the head commit now carries 40+ check runs (15 new E2E
    # contexts on top of the 22-context contract), past the endpoint's
    # 30-per-page default — page 2 holds the rest (observed 2026-08-14).
    names="$(gh api --paginate "repos/$REPO/commits/$2/check-runs" --jq '.check_runs[].name' | sort)"
    missing=0
    for want in "${NEW_CONTEXTS[@]}"; do
      printf '%s\n' "$names" | grep -qx "$want" \
        && echo "OK: $want present." \
        || { echo "NOT YET: $want missing (CI may not have run)." >&2; missing=1; }
    done
    [ "$missing" -eq 0 ] && exit 0 || exit 1
    ;;
  --help|-h) usage; exit 0 ;;
  *) echo "usage: $0 [--apply | --dry-run | --verify <commit-sha>]" >&2; exit 2 ;;
esac

command -v gh >/dev/null 2>&1 || { echo "gh CLI required" >&2; exit 2; }

# 1) GET the current contexts[] from main protection
echo "==> GET current required_status_checks on $REPO main"
current="$(
  gh api "repos/$REPO/branches/main/protection/required_status_checks" \
    --jq '{strict, contexts}'
)" || { echo "GET failed — is gh authenticated and branch protection readable?" >&2; exit 1; }

mapfile -t cur_ctx < <(printf '%s\n' "$current" | jq -r '.contexts[]')

# 2) Idempotency first: all new contexts already present => no-op regardless
# of baseline drift
already=1
for want in "${NEW_CONTEXTS[@]}"; do
  if ! printf '%s\n' "${cur_ctx[@]}" | grep -qx "$want"; then already=0; break; fi
done
if [ "$already" -eq 1 ]; then
  echo "Already bumped: all ${#NEW_CONTEXTS[@]} E2E contexts are present. Nothing to do (idempotent no-op)."
  exit 0
fi

# 3) Guard: refuse unless the current contract is exactly the expected 22
# (set-equality, order-insensitive — the live order reflects PATCH history)
count=${#cur_ctx[@]}
echo "    current context count: $count"

if [ "$count" -ne "${#EXPECTED_22[@]}" ]; then
  echo "REFUSE: expected ${#EXPECTED_22[@]} contexts, found $count." >&2
  printf '  %s\n' "${cur_ctx[@]}" >&2
  echo "  All ten Build-lane bumps must have applied first (22-context baseline). No change applied." >&2
  exit 1
fi
missing=()
for want in "${EXPECTED_22[@]}"; do
  found=0
  for have in "${cur_ctx[@]}"; do [ "$have" = "$want" ] && found=1 && break; done
  [ "$found" -eq 1 ] || missing+=("$want")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "REFUSE: current contexts do not match the expected 22. Missing:" >&2
  printf '  %s\n' "${missing[@]}" >&2
  echo "  No change applied." >&2
  exit 1
fi

# 4) Build the 37-context payload, preserving the current strict flag
payload="$(printf '%s\n' "$current" | jq -c --argjson new "$(printf '%s\n' "${NEW_CONTEXTS[@]}" | jq -R . | jq -s .)" \
  '{strict: (.strict // true), contexts: (.contexts + $new)}')"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "==> DRY RUN — payload that would be PATCHed to main:"
  echo "$payload" | jq .
  echo "==> (no changes applied)"
  exit 0
fi

# 5) PATCH to main
for branch in "${BRANCHES[@]}"; do
  echo "==> PATCH $branch required_status_checks"
  gh api -X PATCH "repos/$REPO/branches/$branch/protection/required_status_checks" \
    -H 'Accept: application/vnd.github+json' \
    --input - <<<"$payload" >/dev/null
done

echo "==> Done: 22 -> $((22 + ${#NEW_CONTEXTS[@]})) (added ${#NEW_CONTEXTS[@]} E2E contexts) on ${BRANCHES[*]}."
echo "==> After the first CI run, verify:"
echo "    $0 --verify \$(git rev-parse HEAD)"
