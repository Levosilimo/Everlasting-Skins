#!/usr/bin/env bash
# gh-api-bump/26.1.sh
#
# Add "Build (26.1)" to the required_status_checks contexts on main
# (integration/m2-monorepo was retired 2026-08-07 — PR #314 — so it is NOT
# targeted), bumping the contract from 16 to 17 contexts (AGENTS.md
# required-check section, lib-44).
#
# PATCH, not PUT: this script PATCHes /branches/<branch>/protection/
# required_status_checks. PUT returns 404 on the protection subresource
# (verified empirically during the three-lane expansion deadlock-resolution
# sequence, 2026-08-06).
#
# OUT-OF-REPO: branch protection is enforced via the gh API ("do not edit
# branch protection in-repo"). Run this AT PR-OPEN time for the 26.1 lane PR,
# BEFORE its first CI run completes, or the lane's check will not be required.
#
# Flow:
#   1. Guard   - gh auth status OK; main's current contexts must equal the
#                expected 16-entry baseline (and must NOT already contain
#                Build (26.1), i.e. idempotent re-run is a no-op).
#   2. Dry-run - read-only: fetch contexts, print the planned PATCH payload
#                and the exact command (no write). --method GET.
#   3. Apply   - perform the PATCH with the jq-appended payload.
#                --method PATCH. Fails fast if the guard fails.
#   4. Verify  - after the next CI run: list check-runs for the lane branch
#                head and confirm a check named "Build (26.1)" exists.
#
# Usage (run from the repo root; read-only unless --apply):
#   bash gh-api-bump/26.1.sh --help
#   bash gh-api-bump/26.1.sh --dry-run
#   bash gh-api-bump/26.1.sh --apply
#   bash gh-api-bump/26.1.sh --verify [ref]
#
# Requires: gh (authenticated), jq.
set -euo pipefail

REPO="Levosilimo/Everlasting-Skins"
# URL-encoded branch names for the gh api path. integration/m2-monorepo was
# retired 2026-08-07 (PR #314) — only main is protected now.
BRANCHES=("main")
CONTEXT="Build (26.1)"
# Lane branch head used by --verify (override with $2).
LANE_REF_DEFAULT="feature/lane-forge-26.1"

# The 16-context baseline BEFORE this lane lands (AGENTS.md; ci.yml job names
# must match these strings exactly).
EXPECTED=(
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
)

ACCEPT="Accept: application/vnd.github+json"

usage() {
  sed -n '2,36p' "$0" | sed 's/^# \{0,1\}//'
}

require_auth() {
  if ! command -v gh >/dev/null 2>&1; then
    echo "ERROR: gh CLI not found" >&2
    exit 1
  fi
  if ! gh auth status >/dev/null 2>&1; then
    echo "ERROR: gh auth status failed — run 'gh auth login' first" >&2
    exit 1
  fi
  if ! command -v jq >/dev/null 2>&1; then
    echo "ERROR: jq not found" >&2
    exit 1
  fi
}

# fetch_contexts <branch> -> prints one context per line (sorted)
fetch_contexts() {
  gh api -H "$ACCEPT" \
    "repos/$REPO/branches/$1/protection/required_status_checks" \
    | jq -r '.contexts[]' | sort
}

# guard <branch> — refuses unless the branch has exactly the expected 16
# contexts; treats an already-applied 17-context set as an idempotent no-op.
guard() {
  local branch="$1"
  require_auth
  local current
  current="$(fetch_contexts "$branch")"

  if grep -qxF "$CONTEXT" <<<"$current"; then
    echo ":: [$branch] '$CONTEXT' already present — nothing to do (idempotent no-op)"
    return 0
  fi

  if ! diff <(printf '%s\n' "${EXPECTED[@]}" | sort) <(printf '%s\n' "$current") >/dev/null; then
    echo ":: [$branch] REFUSING: contexts differ from the expected 16-entry baseline" >&2
    echo "::   expected:" >&2
    printf '::     %s\n' "${EXPECTED[@]}" >&2
    echo "::   actual:" >&2
    sed 's/^/::     /' <<<"$current" >&2
    return 1
  fi

  echo ":: [$branch] guard OK — exactly the 16 expected contexts"
}

# planned_payload <branch> -> jq-appended PATCH body (strict + contexts)
planned_payload() {
  gh api -H "$ACCEPT" \
    "repos/$REPO/branches/$1/protection/required_status_checks" \
    | jq '{strict, contexts: (.contexts + ["'"$CONTEXT"'"])}'
}

apply_one() {
  local branch="$1"
  local payload
  payload="$(planned_payload "$branch")"
  echo ":: [$branch] PATCH required_status_checks (16 -> 17 contexts)"
  echo "$payload" | gh api -X PATCH -H "$ACCEPT" \
    "repos/$REPO/branches/$branch/protection/required_status_checks" \
    --input - | jq -r '"::   now '$(echo "$payload" | jq -r '.contexts | length')' contexts: " + (.contexts | join(", "))'
}

dry_run() {
  for b in "${BRANCHES[@]}"; do
    guard "$b"
    echo ":: [$b] planned PATCH (dry-run, --method GET only — no write):"
    echo "    gh api -X PATCH -H '$ACCEPT' \\"
    echo "      repos/$REPO/branches/$b/protection/required_status_checks --input -"
    echo "    payload:"
    planned_payload "$b" | sed 's/^/      /'
  done
}

apply_all() {
  # Fail fast: guard the branch before writing.
  for b in "${BRANCHES[@]}"; do
    guard "$b"
  done
  for b in "${BRANCHES[@]}"; do
    apply_one "$b"
  done
  echo ":: done — 17 contexts now required on main. Verify after the"
  echo "   first CI run: bash $0 --verify"
}

verify() {
  local ref="${LANE_REF:-$LANE_REF_DEFAULT}"
  require_auth
  echo ":: check-runs on $REPO @ $ref:"
  gh api -H "$ACCEPT" "repos/$REPO/commits/$ref/check-runs" \
    | jq -r '.check_runs[].name' | sort | sed 's/^/    /'
  if gh api -H "$ACCEPT" "repos/$REPO/commits/$ref/check-runs" \
      | jq -e '.check_runs[] | select(.name == "'"$CONTEXT"'")' >/dev/null 2>&1; then
    echo ":: OK — '$CONTEXT' check-run present"
  else
    echo ":: WARN — '$CONTEXT' check-run NOT found yet (CI may not have started; re-run later)" >&2
    exit 1
  fi
}

case "${1:---help}" in
  --help|-h) usage ;;
  --dry-run) dry_run ;;
  --apply)   apply_all ;;
  --verify)  LANE_REF="${2:-$LANE_REF_DEFAULT}"; verify ;;
  *) echo "ERROR: unknown mode '$1'" >&2; usage; exit 1 ;;
esac
