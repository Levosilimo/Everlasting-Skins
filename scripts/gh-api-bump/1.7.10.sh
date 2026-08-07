#!/usr/bin/env bash
#
# gh-api-bump/1.7.10.sh — bump `Build (forge-1.7.10)` into the required-check
# branch-protection contract.
#
# PATCH, not PUT: this script PATCHes /branches/<branch>/protection (the full
# protection object). PUT returns 404 on the protection subresource (verified
# empirically during the three-lane expansion deadlock-resolution sequence,
# 2026-08-06); the tracked scripts use PATCH consistently.
#
# Branch protection is enforced OUT-OF-REPO via the gh API (AGENTS.md
# "Branch policy & required checks"); CI only defines the job. Run this at
# PR-open time, after forge-26.2 and forge-1.8.9 have MERGED (merge order
# forge-26.2 -> forge-1.8.9 -> forge-1.7.10; contract 12 -> 13 -> 14 -> 15).
#
# Preconditions (guarded, idempotency check runs first):
#   * integration/m2-monorepo protection contexts == exactly 14
#   * contexts contain BOTH `Build (26.2)` AND `Build (forge-1.8.9)`
#   * contexts do NOT yet contain `Build (forge-1.7.10)`
#
# Usage:
#   gh-api-bump/1.7.10.sh [--apply] [--dry-run] [--verify <commit-sha>]
#
#   --apply         perform the PATCH (default when no flag given)
#   --dry-run       print the PATCH payload (contexts[] with Build (forge-1.7.10)
#                   appended) without applying anything
#   --verify SHA    after the first CI run, print the check-run status for
#                   Build (forge-1.7.10) on that commit
#   --help          this text
#
# Requires: gh (authenticated) + jq. Override repo with GH_REPO env.

set -euo pipefail

REPO="${GH_REPO:-}"
NEW_CONTEXT="Build (forge-1.7.10)"
BRANCHES=(main integration/m2-monorepo)
SOURCE_BRANCH="integration/m2-monorepo"
EXPECTED_CONTEXTS=14
PRECONDITION_A="Build (26.2)"        # forge-26.2 lane's required check
PRECONDITION_B="Build (forge-1.8.9)" # forge-1.8.9 lane's required check

usage() {
  sed -n '2,31p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

die() { echo "ERROR: $*" >&2; exit 1; }

[ $# -gt 0 ] || : # fall through; flags parsed below
DRY_RUN=0
VERIFY_SHA=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) : ;;  # default action; explicit for symmetry with the other scripts
    --dry-run) DRY_RUN=1 ;;
    --verify) VERIFY_SHA="${2:-}"; [ -n "$VERIFY_SHA" ] || usage 1; shift ;;
    -h|--help) usage 0 ;;
    *) usage 1 ;;
  esac
  shift
done

command -v gh >/dev/null 2>&1 || die "gh CLI not found (needs auth)"
command -v jq >/dev/null 2>&1 || die "jq not found"

if [ -z "$REPO" ]; then
  REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null)" \
    || die "could not detect repo; set GH_REPO=owner/name"
fi

protection_get() {
  gh api "repos/$REPO/branches/$SOURCE_BRANCH/protection" \
    --jq '{ contexts: .required_status_checks.contexts,
            strict: .required_status_checks.strict,
            enforce_admins: (.enforce_admins.enabled // .enforce_admins),
            reviews: .required_pull_request_reviews,
            restrictions: { users: [.restrictions.users[]?.login],
                            teams: [.restrictions.teams[]?.slug],
                            apps:  [.restrictions.apps[]?.slug] } }'
}

echo "== $0 (repo: $REPO) =="

# ── Verify mode: check-run status after the first CI run ────────────────
if [ -n "$VERIFY_SHA" ]; then
  gh api "repos/$REPO/commits/$VERIFY_SHA/check-runs" \
    --jq ".check_runs[] | select(.name == \"$NEW_CONTEXT\") |
          { name, status, conclusion, html_url }"
  exit 0
fi

# ── Guard: idempotency first, then contract == expected 14 with preconditions ─
PROTECTION="$(protection_get)" || die "GET protection failed for $SOURCE_BRANCH"
CONTEXTS="$(jq -r '.contexts[]' <<<"$PROTECTION")"

grep -qxF "$NEW_CONTEXT" <<<"$CONTEXTS" \
  && { echo "Already bumped: \"$NEW_CONTEXT\" present — nothing to do (idempotent no-op)."; exit 0; }

[ "$(jq -r '.contexts | length' <<<"$PROTECTION")" -eq "$EXPECTED_CONTEXTS" ] \
  || die "expected exactly $EXPECTED_CONTEXTS contexts, got \
$(jq -r '.contexts | length' <<<"$PROTECTION") — forge-26.2 / forge-1.8.9 \
must have merged first (contract 12->13->14->15)"

grep -qxF "$PRECONDITION_A" <<<"$CONTEXTS" \
  || die "context '$PRECONDITION_A' missing — forge-26.2 must have merged first"
grep -qxF "$PRECONDITION_B" <<<"$CONTEXTS" \
  || die "context '$PRECONDITION_B' missing — forge-1.8.9 must have merged first"

echo "Guard OK: $EXPECTED_CONTEXTS contexts incl. $PRECONDITION_A + $PRECONDITION_B."

# ── Build PATCH payload: append the new context, preserve everything else ──
PAYLOAD="$(jq --arg new "$NEW_CONTEXT" '
  .contexts = ([.contexts[] | select(. != $new)] + [$new]) |
  { required_status_checks: { strict: .strict, contexts: .contexts },
    enforce_admins: .enforce_admins,
    required_pull_request_reviews: .reviews,
    restrictions: .restrictions }' <<<"$PROTECTION")"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "== DRY-RUN: payload for PATCH repos/$REPO/branches/{main,integration/m2-monorepo}/protection =="
  jq . <<<"$PAYLOAD"
  echo "== dry-run only — nothing applied. Re-run without --dry-run to apply. =="
  exit 0
fi

# ── Apply: PATCH to main AND integration/m2-monorepo (identical contract) ──
TMP="$(mktemp)"; trap 'rm -f "$TMP"' EXIT
jq . <<<"$PAYLOAD" > "$TMP"
for BRANCH in "${BRANCHES[@]}"; do
  echo "PATCH protection -> $BRANCH"
  gh api "repos/$REPO/branches/$BRANCH/protection" \
    --method PATCH --input "$TMP" >/dev/null
done

echo "Done: $NEW_CONTEXT added ($((EXPECTED_CONTEXTS + 1)) contexts)."
echo "After the first CI run, verify with:"
echo "  $0 --verify <commit-sha>"
echo "  # or: gh api repos/$REPO/commits/<sha>/check-runs"
