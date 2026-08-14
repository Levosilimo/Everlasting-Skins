#!/usr/bin/env bash
# ============================================================================
# gradle-locked.sh — serialize concurrent Gradle builds across agents (OOM defense)
#
# AGENT CONTRACT
# --------------
# Agents MUST invoke Gradle through this wrapper instead of calling ./gradlew
# directly:
#
#     ./scripts/gradle-locked.sh <gradle args...>
#
# The wrapper holds an exclusive flock slot for the entire build, so at most
# GRADLE_BUILD_SLOTS (default 2) Gradle builds run at once on this machine —
# regardless of how many agents start them concurrently. Without the gate,
# concurrent builds each spawn a multi-GB Gradle daemon and OOM the 26 GB WSL2
# VM (research: lib-2 WSL2 OOM defenses, 2026-08-13, recorded in
# .slim/deepwork/wsl2-oom-defense.sh).
#
# SINGLE BUILD ENTRY POINT — FORK-HEAP CAPS (JAVA_TOOL_OPTIONS)
# ------------------------------------------------------------
# This wrapper is the one entry point agents' builds MUST use because it
# carries the fork-heap caps: it exports JAVA_TOOL_OPTIONS for every JVM it
# spawns (the Gradle daemon AND ForgeGradle's forked Mavenizer). ForgeGradle
# 7.0 launches the Mavenizer with NO -Xmx, so the JVM takes its default heap
# (25% of RAM = 6.5 GB) and the kernel OOMs this 26 GB box (lib-1);
# org.gradle.jvmargs governs only the daemon, never the forked Mavenizer.
# Agent shells are non-interactive and never source ~/.bashrc, so the caps
# cannot live in a dotfile — the build entry point itself must export them.
# If JAVA_TOOL_OPTIONS is already set with the caller's own -Xmx (e.g.
# -Xmx4g for a 26.x run), it is respected; otherwise the cap is appended.
# NOTE: FG 7.0's Mavenizer hardcodes -Xms4G for its 26.x decompile step, so
# 26.x runs need JAVA_TOOL_OPTIONS=-Xmx4g (or the Mavenizer's
# --decompile-memory arg) — the 3g default cap makes that step invalid/OOM.
#
# ENV KNOBS
# ---------
#   GRADLE_BUILD_SLOTS   number of concurrent build slots     (default 2)
#   GRADLE_LOCK_WAIT_SEC max seconds to wait for a free slot (default 3600)
#   GRADLE_LOCK_DIR      lock directory; MUST be on native ext4
#                        (default ${TMPDIR:-/tmp}/everlastingskins-gradle-locks)
#   GRADLE_LOCK_CMD      command to run under the gate (default ./gradlew).
#                        TEST-ONLY override — agents must not set it.
#
# WHY THE LOCK DIR MUST BE NATIVE EXT4
# ------------------------------------
# flock(2) semantics are not reliable on the WSL2 9P bridge (/mnt/c, /wsl$):
# 9P is a network filesystem and its lock emulation is broken/unsafe for
# cross-process exclusion. The default resolves under ${TMPDIR:-/tmp}, which is
# native ext4 inside the guest. The script REFUSES to run with the lock dir on
# /mnt/* or /wsl* (exit 125) rather than silently degrading.
#
# SLOT BUDGET RATIONALE (N=2)
# ---------------------------
# 26 GB WSL2 VM: ~5 GB base (Windows guest OS + editors/agents) + ~2 GB headroom
# + 2 × 8 GB Gradle builds = 23 GB <= 26 GB. Raise GRADLE_BUILD_SLOTS only after
# re-measuring with free -h during two concurrent builds. Pair with per-user
# caps in ~/.gradle/gradle.properties (NOT committed, operational):
#
#     org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
#     org.gradle.workers.max=4
#     org.gradle.daemon=true
#     org.gradle.parallel=true
#     org.gradle.caching=true
#
# LOCK LIFECYCLE
# --------------
# The slot flock lives on a file descriptor held by this process for the whole
# build. The kernel releases it when the process dies — including SIGKILL and
# OOM kills — so there is NEVER a stale lock to clean up. No pidfiles, no
# lockfile removal, no cleanup step.
#
# SYSTEMD SCOPE (agent protection)
# --------------------------------
# When a user session exists, the build runs inside a systemd scope with
# OOMPolicy=continue, MemoryHigh=5G, MemoryMax=6G, so an OOM kill stays inside
# the build's cgroup instead of taking out the agent/browser. NOTE: the
# research draft used `systemd-run --scope --wait`; systemd-run(1) forbids
# --wait with --scope on every version (verified on systemd 255: "may not be
# combined with --scope"), and --scope mode already runs synchronously in the
# foreground and propagates the child's exit status — so --wait is dropped.
# When systemd-run is absent or cannot create a scope (agent without a user
# session, e.g. polkit "Interactive authentication required"), the build falls
# back to a plain exec with a warning: the flock slot gate still caps
# concurrency, which is the primary OOM defense.
#
# EXIT CODES
# ----------
#   125  slot timeout (GRADLE_LOCK_WAIT_SEC exceeded) or lock-dir on 9P bridge
#   130  interrupted (SIGINT/SIGTERM; slot fd released)
#     2  usage error
#   else the build's own exit code (propagated through systemd-run --wait
#        semantics or the exec fallback)
#
# USAGE
# -----
#   ./scripts/gradle-locked.sh [-v] [--] <gradle args...>
#
#   -v, --verbose   print slot acquisition / waiting / holder info
#   -h, --help      show this help
#   --              end wrapper flags; everything after is passed to Gradle
#                   verbatim (wrapper flags must come first)
# ============================================================================
set -euo pipefail

SLOTS="${GRADLE_BUILD_SLOTS:-2}"
WAIT_SEC="${GRADLE_LOCK_WAIT_SEC:-3600}"
LOCK_DIR="${GRADLE_LOCK_DIR:-${TMPDIR:-/tmp}/everlastingskins-gradle-locks}"
CMD="${GRADLE_LOCK_CMD:-./gradlew}"
VERBOSE=0
SLOT_FD=""

# --- fork-heap caps: every forked JVM (daemon AND FG 7.0's Mavenizer)
# inherits JAVA_TOOL_OPTIONS; see "SINGLE BUILD ENTRY POINT" above. ----------
if [[ "${JAVA_TOOL_OPTIONS:-}" != *-Xmx* ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Xmx3g -XX:MaxMetaspaceSize=1g -XX:+UseG1GC"
fi

usage() {
  cat <<'EOF'
gradle-locked.sh — serialize concurrent Gradle builds across agents (OOM defense)

USAGE
  ./scripts/gradle-locked.sh [-v] [--] <gradle args...>

  -v, --verbose   print slot acquisition / waiting / holder info and the
                  effective JAVA_TOOL_OPTIONS (fork-heap caps)
  -h, --help      show this help
  --              end wrapper flags; everything after is passed to Gradle verbatim

AGENT CONTRACT
  Agents MUST call ./scripts/gradle-locked.sh instead of ./gradlew. The wrapper
  holds an exclusive flock slot for the whole build, so at most GRADLE_BUILD_SLOTS
  (default 2) Gradle builds run at once, regardless of concurrent agents.

ENV KNOBS
  GRADLE_BUILD_SLOTS   concurrent build slots (default 2)
  GRADLE_LOCK_WAIT_SEC max wait for a free slot, 5s increments (default 3600)
  GRADLE_LOCK_DIR      lock dir, MUST be native ext4, never /mnt/* or /wsl*
                       (default ${TMPDIR:-/tmp}/everlastingskins-gradle-locks)
  GRADLE_LOCK_CMD      command under the gate (default ./gradlew); test-only

EXIT CODES
  125 slot timeout / lock dir on 9P bridge    130 interrupted (slot released)
  2 usage error                               else the build's own exit code

Full rationale (ext4 requirement, N=2 budget, systemd scope, lock lifecycle):
see the header comment of this script.
EOF
}

# --- arg parsing: only leading -h/-v/-- are wrapper flags; everything else
# (including gradle's own -p/--offline/...) is forwarded verbatim. ----------
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    -v) VERBOSE=1; shift ;;
    --) shift; break ;;
    *) break ;;   # first non-wrapper arg: forward the rest untouched
  esac
done

# --- -v: surface the effective fork caps so agents can verify they're live ---
if [[ $VERBOSE -eq 1 ]]; then
  echo "gradle-locked: JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}"
fi

# --- env validation ----------------------------------------------------------
if ! [[ "$SLOTS" =~ ^[0-9]+$ ]] || (( SLOTS < 1 )); then
  echo "gradle-locked: GRADLE_BUILD_SLOTS must be a positive integer (got '$SLOTS')" >&2
  exit 2
fi
if ! [[ "$WAIT_SEC" =~ ^[0-9]+$ ]]; then
  echo "gradle-locked: GRADLE_LOCK_WAIT_SEC must be a non-negative integer (got '$WAIT_SEC')" >&2
  exit 2
fi

# --- 9P bridge guard: flock is not trustworthy on WSL2's network filesystems --
case "$LOCK_DIR" in
  /mnt/*|/wsl/*|/wsl)
    echo "gradle-locked: REFUSING lock dir '$LOCK_DIR' — /mnt/* and /wsl* are the WSL2 9P bridge, where flock is unreliable. Use native ext4 (default: \${TMPDIR:-/tmp}/everlastingskins-gradle-locks)." >&2
    exit 125 ;;
esac

mkdir -p "$LOCK_DIR"

# --- open one fd per slot. Bash user redirections survive exec (no CLOEXEC),
# so the flock is held by the wrapper AND by the exec'd build process alike.
slot_fds=()
for ((i = 0; i < SLOTS; i++)); do
  exec {fd}<>"$LOCK_DIR/slot-$i.lock"
  slot_fds+=("$fd")
done

# Best-effort: PIDs currently holding (or contending for) slot locks. Used only
# for -v diagnostics and the timeout message; never fatal. Writes HOLDERS (no
# stdout capture — a cmd-subst subshell would inherit the slot fds and list
# itself). Tier 1: the kernel lock table — precise on real Linux. WSL2's
# /proc/locks is known-incomplete for flock entries, so an empty result falls
# through to Tier 2: scan every process's open fds for the slot files (works on
# WSL2, but over-reports: waiting wrappers also hold slot fds open, so it lists
# contenders + holder — which is what a contention diagnostic wants anyway).
HOLDERS=""
slot_holders() {
  local f ino hex add p pid fd devino target
  local exclude="$$"
  exclude+=" $(cat /proc/$$/task/$$/children 2>/dev/null || true)"  # self + own subshells/sleeps
  HOLDERS=""
  for f in "$LOCK_DIR"/slot-*.lock; do
    ino=$(stat -c %i "$f" 2>/dev/null) || continue
    hex=$(printf '%x' "$ino")
    add=$(awk -v h="$hex" '$6 == h || $6 ~ (":" h "$") { printf "%s ", $5 }' /proc/locks 2>/dev/null) || true
    HOLDERS+="$add"
  done
  [[ -n "$HOLDERS" ]] && { HOLDERS=$(printf '%s' "$HOLDERS" | tr ' ' '\n' | sort -un | tr '\n' ' '); return; }
  for f in "$LOCK_DIR"/slot-*.lock; do
    target=$(stat -c '%d:%i' "$f" 2>/dev/null) || continue
    for p in /proc/[0-9]*; do
      pid="${p#/proc/}"
      case " $exclude " in *" $pid "*) continue ;; esac
      for fd in "$p"/fd/*; do
        [[ -e "$fd" ]] || continue
        devino=$(stat -Lc '%d:%i' "$fd" 2>/dev/null) || continue
        if [[ "$devino" = "$target" ]]; then
          HOLDERS+="$pid "
          break
        fi
      done
    done
  done
  HOLDERS=$(printf '%s' "$HOLDERS" | tr ' ' '\n' | sort -un | tr '\n' ' ')
}

acquire_slot() {
  local waited=0 fd
  while :; do
    for fd in "${slot_fds[@]}"; do
      if flock -n "$fd"; then
        SLOT_FD="$fd"
        [[ $VERBOSE -eq 1 ]] && echo "gradle-locked: acquired build slot (fd $fd) of $SLOTS"
        return 0
      fi
    done
    waited=$((waited + 5))
    if (( waited >= WAIT_SEC )); then
      slot_holders
      echo "gradle-locked: ERROR no free build slot after ${WAIT_SEC}s (${SLOTS} slots, lock dir ${LOCK_DIR}) — holders: $HOLDERS" >&2
      exit 125
    fi
    slot_holders
    [[ $VERBOSE -eq 1 ]] && echo "gradle-locked: all $SLOTS slots busy, retrying in 5s (waited ${waited}s/${WAIT_SEC}s) — holders: $HOLDERS"
    sleep 5
  done
}

# SIGINT/SIGTERM: release the slot fd (kernel drops the flock) and exit 130.
cleanup() {
  if [[ -n "$SLOT_FD" ]]; then
    eval "exec ${SLOT_FD}>&-" 2>/dev/null || true
    SLOT_FD=""
  fi
  trap - INT TERM
  exit 130
}
trap cleanup INT TERM

acquire_slot

# --- run the build -----------------------------------------------------------
run_build() {
  if command -v systemd-run >/dev/null 2>&1 \
     && systemd-run --scope -p OOMPolicy=continue -p MemoryHigh=5G -p MemoryMax=6G -- true >/dev/null 2>&1; then
    [[ $VERBOSE -eq 1 ]] && echo "gradle-locked: running in systemd scope (MemoryHigh=5G MemoryMax=6G OOMPolicy=continue)"
    if systemd-run --scope -p OOMPolicy=continue -p MemoryHigh=5G -p MemoryMax=6G -- "$CMD" "$@"; then
      return 0
    else
      return $?    # build failed inside the scope — forward its exit code
    fi
  fi

  # No usable systemd scope (agent without a user session, or systemd-run
  # absent). Plain exec: the flock slot gate still caps concurrency, which is
  # the primary OOM defense. fds survive exec, so the slot stays held.
  echo "gradle-locked: WARNING systemd-run unavailable or cannot create a scope in this session (no user session?) — running unconfined; flock slot gate still caps concurrency" >&2
  exec "$CMD" "$@"
}

run_build "$@"
