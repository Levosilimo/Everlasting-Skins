#!/usr/bin/env bash
#
# gh-merge-bot.sh — bounded, single-pass merge automation for Everlasting-Skins PRs.
#
# Pattern (research-backed; see .slim/deepwork/merge-automation-playbook.md):
#   fire-and-forget auto-merge + bounded CI watch + out-of-band verify.
#
# Why this shape (lib-10/lib-11 research findings):
#   * `gh pr merge --squash --auto` is FIRE-AND-FORGET: one GraphQL mutation
#     returns immediately; GitHub waits for checks + reviews + up-to-date.
#     It does NOT auto-update a behind branch.
#   * Under strict protection ("require branches up to date"), a BEHIND
#     branch must be updated via `gh pr update-branch <N>` BEFORE auto-merge
#     can fire. If the base moves after auto-merge is armed, re-run
#     update-branch (a write-access actor keeps auto-merge enabled); do NOT
#     disable/re-enable unless a non-write actor pushed to the head branch.
#   * `gh pr checks <N> --watch --interval <sec> --fail-fast` is the bounded
#     replacement for hand-rolled sleep loops: exit 0 = all pass, 1 = any
#     failed (with --fail-fast: first failure), 8 = still pending. It covers
#     check conclusions only — not the up-to-date/review gates.
#   * mergeStateStatus is lazily computed; UNKNOWN is a transient "not yet
#     computed" state, never terminal. Do not hammer for CLEAN.
#
# Anti-patterns avoided (encoded contract):
#   * No loops at all — exactly one bounded pass, then a terminal outcome.
#   * No bare foreground `gh pr checks --watch` without `timeout`: a bounded
#     caller tool timeout can kill a bare --watch mid-poll and leave
#     ambiguous state (harness-kit #38).
#   * No polling past terminal state; no polling UNKNOWN.
#   * Never an ambiguous "waiting…" terminal — every run ends with
#     `TERMINAL: <DONE|DEFERRED|BLOCKED|FAILED|STALE>` on stdout
#     (--verify additionally reports READY_TO_MERGE / PENDING).
#   * The script never self-retries. BLOCKED means "re-dispatch later" —
#     that is the orchestrator's job, not the script's.
#
# Exit codes (caller contract):
#   0  DONE (merged) / READY_TO_MERGE (verify) / DEFERRED (auto-merge armed;
#      GitHub will merge when requirements met) / dry-run (nothing done)
#   1  FAILED — checks failed, merge could not be armed, or gh error
#   2  BLOCKED — branch protection requirements not met (verify mode)
#   3  STALE — branch behind base; update-branch + retry
#   4  FAILED — merge conflicts (DIRTY) or PR closed without merge
#   8  PENDING (verify: checks unsettled) / BLOCKED (normal mode: checks
#      pending past the watch bound — re-dispatch later)
#   64 usage error
#
# Caller timeout contract: the caller's tool timeout MUST exceed --timeout
# (default 600s). BLOCKED (8) -> orchestrator re-dispatches at the next
# natural boundary. DEFERRED (0) -> confirm out-of-band with `--verify` later.
#
# Usage:
#   scripts/gh-merge-bot.sh <PR> [--squash|--rebase] [--repo owner/repo] \
#       [--timeout <sec>] [--interval <sec>] [--dry-run]
#   scripts/gh-merge-bot.sh <PR> --verify
#
set -uo pipefail

REPO="Levosilimo/Everlasting-Skins"
TIMEOUT=600
INTERVAL=60
METHOD="--squash"
DRY_RUN=0
VERIFY=0
PR=""

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }

usage_err() {
  echo "gh-merge-bot: $*" >&2
  echo "usage: scripts/gh-merge-bot.sh <PR> [--squash|--rebase] [--repo owner/repo] [--timeout <sec>] [--interval <sec>] [--dry-run]" >&2
  echo "       scripts/gh-merge-bot.sh <PR> --verify" >&2
  exit 64
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --verify)   VERIFY=1; shift ;;
    --dry-run)  DRY_RUN=1; shift ;;
    --squash)   METHOD="--squash"; shift ;;
    --rebase)   METHOD="--rebase"; shift ;;
    --repo)     [[ $# -ge 2 ]] || usage_err "--repo needs a value"; REPO="$2"; shift 2 ;;
    --timeout)  [[ $# -ge 2 ]] || usage_err "--timeout needs a value"; TIMEOUT="$2"; shift 2 ;;
    --interval) [[ $# -ge 2 ]] || usage_err "--interval needs a value"; INTERVAL="$2"; shift 2 ;;
    -*)         usage_err "unknown flag: $1" ;;
    *)          [[ -z "$PR" ]] || usage_err "unexpected extra argument: $1"; PR="$1"; shift ;;
  esac
done

[[ -n "$PR" ]] || usage_err "missing <PR> argument"
[[ "$TIMEOUT" =~ ^[0-9]+$ ]] || usage_err "--timeout must be a positive integer (got '$TIMEOUT')"
[[ "$INTERVAL" =~ ^[0-9]+$ ]] || usage_err "--interval must be a positive integer (got '$INTERVAL')"

# Verify-state machine globals (set by verify_state):
#   V_OUTCOME, V_CODE, V_SHA, V_MSS, V_WHY
V_OUTCOME=""; V_CODE=0; V_SHA=""; V_MSS=""; V_WHY=""

blocked_why() { # $1 = pr-view JSON; prints a human reason for BLOCKED
  local json="$1" failing review
  failing="$(printf '%s' "$json" | jq -r '[.statusCheckRollup[]?
    | select((.conclusion // "") == "FAILURE" or (.state // "") == "FAILURE")
    | (.name // .context // "unknown-check")] | unique | join(", ")')"
  review="$(printf '%s' "$json" | jq -r '.reviewDecision // ""')"
  if [[ -n "$failing" ]]; then
    echo "failing check(s): $failing"
  elif [[ "$review" == "REVIEW_REQUIRED" ]]; then
    echo "required review"
  else
    echo "branch protection requirements not met"
  fi
}

verify_state() {
  local json state merged_at mss head_oid merge_oid
  json="$(gh pr view "$PR" --repo "$REPO" \
      --json state,mergedAt,mergeStateStatus,headRefOid,mergeCommit,reviewDecision,statusCheckRollup 2>&1)" \
    || { V_OUTCOME="FAILED"; V_CODE=1
         V_WHY="gh pr view failed: $(printf '%s' "$json" | tr '\n' ' ' | cut -c1-200)"; return 0; }
  if ! printf '%s' "$json" | jq -e 'type == "object"' >/dev/null 2>&1; then
    V_OUTCOME="FAILED"; V_CODE=1; V_WHY="gh pr view returned unparseable JSON"; return 0
  fi
  state="$(printf '%s' "$json" | jq -r '.state // ""')"
  merged_at="$(printf '%s' "$json" | jq -r '.mergedAt // ""')"
  mss="$(printf '%s' "$json" | jq -r '.mergeStateStatus // ""')"
  head_oid="$(printf '%s' "$json" | jq -r '.headRefOid // ""')"
  merge_oid="$(printf '%s' "$json" | jq -r '.mergeCommit.oid // ""')"
  V_MSS="$mss"
  if [[ "$state" == "MERGED" || -n "$merged_at" ]]; then
    V_OUTCOME="DONE"; V_CODE=0
    V_SHA="${merge_oid:-$head_oid}"
    V_WHY="merged"
  elif [[ "$state" == "CLOSED" ]]; then
    V_OUTCOME="FAILED"; V_CODE=4; V_WHY="closed without merge"
  else
    case "$mss" in
      CLEAN)     V_OUTCOME="READY_TO_MERGE"; V_CODE=0; V_WHY="mergeable" ;;
      BEHIND)    V_OUTCOME="STALE"; V_CODE=3; V_WHY="branch behind base" ;;
      DIRTY)     V_OUTCOME="FAILED"; V_CODE=4; V_WHY="conflicts" ;;
      BLOCKED)   V_OUTCOME="BLOCKED"; V_CODE=2; V_WHY="$(blocked_why "$json")" ;;
      DRAFT)     V_OUTCOME="BLOCKED"; V_CODE=2; V_WHY="draft PR" ;;
      HAS_HOOKS) V_OUTCOME="BLOCKED"; V_CODE=2; V_WHY="repo requires commit signing/hooks" ;;
      MERGING)   V_OUTCOME="PENDING"; V_CODE=8; V_WHY="merge in progress" ;;
      UNKNOWN)   V_OUTCOME="PENDING"; V_CODE=8; V_WHY="mergeability not yet computed" ;;
      UNSTABLE)  V_OUTCOME="PENDING"; V_CODE=8; V_WHY="mergeable but checks failing/unsettled" ;;
      "")        V_OUTCOME="PENDING"; V_CODE=8; V_WHY="mergeStateStatus not computed" ;;
      *)         V_OUTCOME="PENDING"; V_CODE=8; V_WHY="mergeStateStatus=$mss" ;;
    esac
  fi
}

verdict_line() { # $1 = outcome token, $2 = detail (optional), $3 = sha (optional)
  case "$1" in
    DONE)           echo "DONE ${3:-<no-sha>}" ;;
    READY_TO_MERGE) echo "READY_TO_MERGE" ;;
    STALE)          echo "STALE — update-branch + retry" ;;
    FAILED)         echo "FAILED — ${2:-unknown failure}" ;;
    BLOCKED)        echo "BLOCKED — ${2:-branch protection requirements not met}" ;;
    PENDING)        echo "PENDING — checks unsettled" ;;
    DEFERRED)       echo "DEFERRED — auto-merge enabled; GitHub will merge when requirements met" ;;
  esac
}

emit_verdict() { # $1 = outcome token, $2 = detail (optional), $3 = sha (optional)
  verdict_line "$@"
  echo "TERMINAL: $1"
}

main() {
  local watch_rc merge_out merge_rc ub_rc
  log "gh-merge-bot: PR=$PR repo=$REPO method=${METHOD#--} verify=$VERIFY dry_run=$DRY_RUN timeout=${TIMEOUT}s interval=${INTERVAL}s"

  verify_state
  log "verify: $V_OUTCOME (${V_WHY:-no detail}) mergeStateStatus=${V_MSS:-<none>}"

  if [[ $VERIFY == 1 ]]; then
    emit_verdict "$V_OUTCOME" "$V_WHY" "$V_SHA"
    exit "$V_CODE"
  fi

  if [[ $DRY_RUN == 1 ]]; then
    verdict_line "$V_OUTCOME" "$V_WHY" "$V_SHA"
    if [[ "$V_OUTCOME" == "DONE" || "$V_OUTCOME" == "FAILED" ]]; then
      echo "DRY-RUN — no commands would run (state $V_OUTCOME is terminal)"
    else
      echo "DRY-RUN — commands that would run for PR $PR (repo $REPO, state $V_OUTCOME):"
      if [[ "$V_OUTCOME" == "STALE" ]]; then
        echo "  gh pr update-branch $PR --repo $REPO"
      fi
      echo "  timeout $TIMEOUT gh pr checks $PR --watch --interval $INTERVAL --fail-fast --repo $REPO"
      echo "  gh pr merge $PR $METHOD --auto --repo $REPO"
    fi
    echo "TERMINAL: DONE"
    exit 0
  fi

  case "$V_OUTCOME" in
    DONE)   emit_verdict "DONE" "already merged" "$V_SHA"; exit 0 ;;
    FAILED) emit_verdict "FAILED" "$V_WHY"; exit "$V_CODE" ;;
  esac
  # READY_TO_MERGE / STALE / BLOCKED / PENDING proceed through the bounded pass.

  if [[ "$V_OUTCOME" == "STALE" ]]; then
    log "STALE: branch behind base — single update-branch trigger (CI re-runs)"
    gh pr update-branch "$PR" --repo "$REPO" >&2
    ub_rc=$?
    if [[ $ub_rc -ne 0 ]]; then
      log "WARN: update-branch failed (rc=$ub_rc); continuing — watch/merge will surface the real state"
    fi
  fi

  log "watching CI checks (bound ${TIMEOUT}s, interval ${INTERVAL}s, fail-fast)"
  timeout "$TIMEOUT" gh pr checks "$PR" --watch --interval "$INTERVAL" --fail-fast --repo "$REPO" >&2
  watch_rc=$?
  case "$watch_rc" in
    0) log "all checks passed" ;;
    8|124)
      emit_verdict "BLOCKED" "checks pending past bound; re-dispatch later"
      log "watch bound reached (rc=$watch_rc); auto-merge was NOT armed this pass"
      exit 8 ;;
    1)
      emit_verdict "FAILED" "checks failed"
      exit 1 ;;
    *)
      emit_verdict "FAILED" "checks exited $watch_rc"
      exit "$watch_rc" ;;
  esac

  log "arming fire-and-forget auto-merge: gh pr merge $PR $METHOD --auto"
  merge_out="$(gh pr merge "$PR" "$METHOD" --auto --repo "$REPO" 2>&1)"
  merge_rc=$?
  printf '%s\n' "$merge_out" >&2

  if [[ $merge_rc -ne 0 ]]; then
    if grep -qiE 'already( been)? merged' <<<"$merge_out"; then
      log "merge command reports PR already merged"
      emit_verdict "DONE" "already merged" "$V_SHA"
      exit 0
    fi
    emit_verdict "FAILED" "gh pr merge exited $merge_rc: $(printf '%s' "$merge_out" | head -1)"
    exit 1
  fi

  if grep -qi 'merged' <<<"$merge_out"; then
    log "merge command reports immediate merge"
    emit_verdict "DONE" "merged immediately" "$V_SHA"
    exit 0
  fi

  # Auto-merge armed but not merged synchronously — one bounded re-verify to
  # confirm (covers the CLEAN-at-verify race where the base moved mid-pass).
  verify_state
  if [[ "$V_OUTCOME" == "DONE" ]]; then
    emit_verdict "DONE" "merged" "$V_SHA"
    exit 0
  fi
  emit_verdict "DEFERRED" "auto-merge enabled; GitHub will merge when requirements met"
  exit 0
}

main
