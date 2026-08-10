#!/usr/bin/env bash
#
# gh-merge-bot.sh — bounded, single-pass merge automation for Everlasting-Skins PRs.
#
# Pattern (research-backed; see .slim/deepwork/merge-automation-playbook.md):
#   auto-merge FIRST (GitHub gates on required checks itself) + bounded
#   required-only diagnostic watch on refusal + out-of-band verify.
#
# "Informational checks never gate" (2026-08-10 incident, fix PR #420):
#   GitHub's own merge API enforces the required-check contract — `gh pr merge
#   --auto` merges as soon as REQUIRED checks, reviews, and up-to-date pass,
#   and informational jobs never block it. So this script attempts auto-merge
#   IMMEDIATELY (after update-branch when STALE) and does NOT watch the full
#   check list. Informational workflows (CodeQL "Analyze (java)", and 5 of 6
#   GameTest lanes — only GameTest (1.21) is required) can NEVER gate a merge.
#   The pre-fix version ran `gh pr checks --watch` over ALL checks first; on
#   2026-08-10 it stalled a merge fixer 30+ minutes watching the informational
#   CodeQL Analyze (java) job while every required check was already green.
#   The required contract is the 22-context branch-protection list (YAML Lint,
#   Build (common), 4x Build (1.21.x), Build (26.2), Build (26.1), Build
#   (mc1.12.2), E2E (mc1.12.2), GameTest (1.21), 10 out-of-band Build lanes,
#   aislop (M2), CI Health, Vendored harness diff-guard). gh exposes it as
#   `gh pr checks --required` (verified on gh 2.88.1, 2026-08-10).
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
#   * `gh pr checks --required --watch --interval <sec> --fail-fast` is the
#     bounded required-aware replacement for a full-list watch: it polls ONLY
#     the required contexts (exit 0 = all required pass, 1 = a required check
#     failed, 8 = required checks still pending). Informational checks are
#     excluded, so they can never hold the watch. It covers check conclusions
#     only — not the up-to-date/review gates.
#   * mergeStateStatus is lazily computed; UNKNOWN is a transient "not yet
#     computed" state, never terminal. Do not hammer for CLEAN.
#   * --fix-stale (--verify mode only, OPT-IN, default off): on a STALE
#     verify, ONE `gh pr update-branch` + ONE re-verify, then report the new
#     state — bounded, never a loop. The standing BEHIND-resolution owner is
#     .github/workflows/auto-update-pr-branches.yml (PAT-authenticated);
#     --fix-stale is the belt-and-suspenders manual fallback for a
#     write-access actor. Plain --verify stays strictly read-only.
#
# Anti-patterns avoided (encoded contract):
#   * No loops at all — exactly one bounded pass, then a terminal outcome.
#   * No bare foreground `gh pr checks --watch` without `timeout`: a bounded
#     caller tool timeout can kill a bare --watch mid-poll and leave
#     ambiguous state (harness-kit #38).
#   * No full-list watch. The watch — when it runs at all (only after a merge
#     refusal with required checks pending) — is `--required`-filtered with a
#     SHORT default bound (--timeout 180s). It is a diagnostic pre-flight,
#     never a gate: auto-merge was already attempted before it runs.
#   * No polling past terminal state; no polling UNKNOWN.
#   * Never an ambiguous "waiting…" terminal — every run ends with
#     `TERMINAL: <DONE|DEFERRED|BLOCKED|FAILED|STALE>` on stdout
#     (--verify additionally reports READY_TO_MERGE / PENDING).
#   * The script never self-retries: a single merge re-attempt after the
#     diagnostic watch completes the pass; it is not a retry loop. BLOCKED
#     means "re-dispatch later" — that is the orchestrator's job, not the
#     script's.
#
# Exit codes (caller contract):
#   0  DONE (merged) / READY_TO_MERGE (verify) / DEFERRED (auto-merge armed;
#      GitHub will merge when requirements met) / dry-run (nothing done)
#   1  FAILED — required check failed, merge could not be armed, or gh error
#   2  BLOCKED — branch protection requirements not met (verify mode) or
#      required review / draft / signing hooks (normal mode)
#   3  STALE — branch behind base; update-branch + retry (or re-run with
#      --verify --fix-stale for one automatic update-branch + re-verify)
#   4  FAILED — merge conflicts (DIRTY) or PR closed without merge
#   8  PENDING (verify: checks unsettled) / BLOCKED (normal mode: REQUIRED
#      checks pending past the watch bound — re-dispatch later)
#   64 usage error
#
# BLOCKED/FAILED now mean "required-check problem": required check failing or
# pending past the bound, required review, conflicts, draft. An informational
# job (CodeQL etc.) can never trigger them — auto-merge arms and the script
# reports DONE/DEFERRED while informational jobs are still running or red.
#
# Caller timeout contract: the caller's tool timeout MUST exceed --timeout
# (default 180s — the watch is a short diagnostic bound, not a gate).
# BLOCKED (8) -> orchestrator re-dispatches at the next natural boundary.
# DEFERRED (0) -> confirm out-of-band with `--verify` later.
#
# Usage:
#   scripts/gh-merge-bot.sh <PR> [--squash|--rebase] [--repo owner/repo] \
#       [--timeout <sec>] [--interval <sec>] [--dry-run]
#   scripts/gh-merge-bot.sh <PR> --verify [--fix-stale]
#
# --fix-stale applies only with --verify (usage error otherwise): on a STALE
# verify it triggers exactly one `gh pr update-branch` and re-verifies once.
# It never loops; a still-STALE result means "re-dispatch later".
#
set -uo pipefail

REPO="Levosilimo/Everlasting-Skins"
TIMEOUT=180
INTERVAL=60
METHOD="--squash"
DRY_RUN=0
VERIFY=0
FIX_STALE=0
PR=""

log() { printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }

usage_err() {
  echo "gh-merge-bot: $*" >&2
  echo "usage: scripts/gh-merge-bot.sh <PR> [--squash|--rebase] [--repo owner/repo] [--timeout <sec>] [--interval <sec>] [--dry-run]" >&2
  echo "       scripts/gh-merge-bot.sh <PR> --verify [--fix-stale]" >&2
  exit 64
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --verify)   VERIFY=1; shift ;;
    --fix-stale) FIX_STALE=1; shift ;;
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
[[ $FIX_STALE == 1 && $VERIFY != 1 ]] && usage_err "--fix-stale only applies in --verify mode"

# Verify-state machine globals (set by verify_state):
#   V_OUTCOME, V_CODE, V_SHA, V_MSS, V_WHY
V_OUTCOME=""; V_CODE=0; V_SHA=""; V_MSS=""; V_WHY=""

required_check_names() { # $1 = bucket selector (fail|pending); prints names
  local selector="$1" json
  json="$(gh pr checks "$PR" --required --json name,state,bucket --repo "$REPO" 2>/dev/null || echo '[]')"
  printf '%s' "$json" | jq -r --arg sel "$selector" \
    '[.[] | select(.bucket == $sel) | .name] | unique | join(", ")'
}

blocked_why() { # $1 = pr-view JSON; prints a human reason for BLOCKED
  local json="$1" failing review
  # Required-contexts only — an informational failure never explains a block.
  failing="$(required_check_names fail)"
  review="$(printf '%s' "$json" | jq -r '.reviewDecision // ""')"
  if [[ -n "$failing" ]]; then
    echo "failing required check(s): $failing"
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

# Globals set by merge_attempt / diagnose_refusal:
#   M_OUT, M_RC, R_OUTCOME, R_WHY, R_CODE
M_OUT=""; M_RC=0
R_OUTCOME=""; R_WHY=""; R_CODE=""

merge_attempt() {
  # Fire-and-forget auto-merge. GitHub's merge API enforces the required
  # check/review/up-to-date contract itself; informational jobs never block
  # it. Exits the script on success; returns 1 on refusal.
  M_OUT="$(gh pr merge "$PR" "$METHOD" --auto --repo "$REPO" 2>&1)"
  M_RC=$?
  printf '%s\n' "$M_OUT" >&2

  if [[ $M_RC -eq 0 ]]; then
    if grep -qi 'merged' <<<"$M_OUT"; then
      log "merge command reports immediate merge"
      emit_verdict "DONE" "merged immediately" "$V_SHA"
      exit 0
    fi
    # Auto-merge armed but not merged synchronously — one bounded re-verify
    # to confirm (covers the CLEAN-at-verify race where the base moved
    # mid-pass).
    verify_state
    if [[ "$V_OUTCOME" == "DONE" ]]; then
      emit_verdict "DONE" "merged" "$V_SHA"
      exit 0
    fi
    emit_verdict "DEFERRED" "auto-merge enabled; GitHub will merge when requirements met"
    exit 0
  fi

  if grep -qiE 'already( been)? merged' <<<"$M_OUT"; then
    log "merge command reports PR already merged"
    emit_verdict "DONE" "already merged" "$V_SHA"
    exit 0
  fi
  return 1
}

diagnose_refusal() { # classify a refused merge; sets R_OUTCOME/R_WHY/R_CODE
  local req_fail req_pend
  verify_state  # fresh mergeStateStatus post-refusal
  case "$V_OUTCOME" in
    DONE)   R_OUTCOME="DONE"; R_WHY="merged between attempts"; return ;;
    FAILED) R_OUTCOME="FAILED"; R_WHY="$V_WHY"; R_CODE="$V_CODE"; return ;;
    STALE)  R_OUTCOME="STALE"; R_WHY="branch behind base; update-branch + retry"; R_CODE=3; return ;;
  esac
  # Check-related states (BLOCKED/UNSTABLE/CLEAN/UNKNOWN): query the REQUIRED
  # contexts directly — informational checks are never part of the diagnosis.
  req_fail="$(required_check_names fail)"
  req_pend="$(required_check_names pending)"
  if [[ -n "$req_fail" ]]; then
    R_OUTCOME="FAILED"; R_WHY="required check(s) failing: $req_fail"; R_CODE=1; return
  fi
  if [[ -n "$req_pend" ]]; then
    R_OUTCOME="PENDING"; R_WHY="required check(s) pending: $req_pend"; return
  fi
  case "$V_MSS" in
    DRAFT)      R_OUTCOME="BLOCKED"; R_WHY="draft PR"; R_CODE=2; return ;;
    HAS_HOOKS)  R_OUTCOME="BLOCKED"; R_WHY="repo requires commit signing/hooks"; R_CODE=2; return ;;
    BLOCKED)    R_OUTCOME="BLOCKED"; R_WHY="required review or branch protection requirement not met"; R_CODE=2; return ;;
    CLEAN)      R_OUTCOME="CLEAN"; R_WHY="mergeable per mergeStateStatus; refusal transient" ;;
    *)          R_OUTCOME="PENDING"; R_WHY="mergeStateStatus=$V_MSS" ;;
  esac
}

main() {
  local ub_rc diag_rc
  log "gh-merge-bot: PR=$PR repo=$REPO method=${METHOD#--} verify=$VERIFY fix_stale=$FIX_STALE dry_run=$DRY_RUN timeout=${TIMEOUT}s interval=${INTERVAL}s"

  verify_state
  log "verify: $V_OUTCOME (${V_WHY:-no detail}) mergeStateStatus=${V_MSS:-<none>}"

  if [[ $VERIFY == 1 ]]; then
    if [[ "$V_OUTCOME" == "STALE" && $FIX_STALE == 1 ]]; then
      # Bounded single-shot: one update-branch, one re-verify, report the
      # new state. Never loops — a still-STALE result means re-dispatch.
      log "verify: STALE with --fix-stale — one update-branch trigger"
      if [[ $DRY_RUN == 1 ]]; then
        log "DRY-RUN: would run: gh pr update-branch $PR --repo $REPO"
      elif gh pr update-branch "$PR" --repo "$REPO" >&2; then
        log "update-branch ok — one re-verify"
        verify_state
        log "re-verify: $V_OUTCOME (${V_WHY:-no detail}) mergeStateStatus=${V_MSS:-<none>}"
      else
        log "WARN: update-branch failed; reporting pre-update state"
      fi
    fi
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
      echo "  gh pr merge $PR $METHOD --auto --repo $REPO"
      echo "  # on merge refusal only, with required checks pending:"
      echo "  timeout $TIMEOUT gh pr checks $PR --watch --required --interval $INTERVAL --fail-fast --repo $REPO"
    fi
    echo "TERMINAL: DONE"
    exit 0
  fi

  case "$V_OUTCOME" in
    DONE)   emit_verdict "DONE" "already merged" "$V_SHA"; exit 0 ;;
    FAILED) emit_verdict "FAILED" "$V_WHY"; exit "$V_CODE" ;;
  esac
  # READY_TO_MERGE / STALE / BLOCKED / PENDING proceed through the merge pass.

  if [[ "$V_OUTCOME" == "STALE" ]]; then
    log "STALE: branch behind base — single update-branch trigger (CI re-runs)"
    gh pr update-branch "$PR" --repo "$REPO" >&2
    ub_rc=$?
    if [[ $ub_rc -ne 0 ]]; then
      log "WARN: update-branch failed (rc=$ub_rc); merge attempt will surface the real state"
    fi
  fi

  # REQUIRED-CHECK GATING (2026-08-10 fix): GitHub's merge API enforces the
  # required-check contract itself, so attempt the merge FIRST — no full-list
  # watch in the common path. Informational jobs can never hold this up.
  log "arming fire-and-forget auto-merge (GitHub gates on required checks only): gh pr merge $PR $METHOD --auto"
  merge_attempt
  # merge_attempt exits the script on success; reaching here means refusal.
  log "merge refused (rc=$M_RC); diagnosing required-check state"

  diagnose_refusal
  case "$R_OUTCOME" in
    DONE)
      emit_verdict "DONE" "$R_WHY" "$V_SHA"
      exit 0 ;;
    FAILED)
      emit_verdict "FAILED" "$R_WHY"
      exit "${R_CODE:-1}" ;;
    STALE)
      emit_verdict "STALE" "$R_WHY"
      exit 3 ;;
    BLOCKED)
      emit_verdict "BLOCKED" "$R_WHY"
      exit 2 ;;
    CLEAN)
      # Transient refusal while mergeable — one re-attempt completes the pass.
      log "refusal transient (mergeStateStatus CLEAN); re-attempting merge once"
      merge_attempt
      emit_verdict "FAILED" "merge refused twice: $(printf '%s' "$M_OUT" | head -1)"
      exit 1 ;;
    PENDING)
      # Required checks pending — bounded required-only diagnostic watch
      # (informational checks excluded by --required), then one re-attempt.
      log "required check(s) pending ($R_WHY); bounded required-only watch (${TIMEOUT}s)"
      timeout "$TIMEOUT" gh pr checks "$PR" --watch --required --interval "$INTERVAL" --fail-fast --repo "$REPO" >&2
      diag_rc=$?
      case "$diag_rc" in
        0)
          log "required checks passed; re-attempting merge once"
          merge_attempt
          emit_verdict "FAILED" "merge refused after required checks passed: $(printf '%s' "$M_OUT" | head -1)"
          exit 1 ;;
        8|124)
          emit_verdict "BLOCKED" "required check(s) pending past ${TIMEOUT}s bound; re-dispatch later"
          log "watch bound reached (rc=$diag_rc); auto-merge was NOT armed this pass"
          exit 8 ;;
        1)
          emit_verdict "FAILED" "required check failed"
          exit 1 ;;
        *)
          emit_verdict "FAILED" "required-check watch exited $diag_rc"
          exit "$diag_rc" ;;
      esac ;;
  esac
}

main
