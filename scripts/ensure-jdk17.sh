#!/usr/bin/env bash
# ensure-jdk17.sh — source-only helper that makes lefthook's Gradle
# invocations self-sufficient with respect to the JVM launcher version.
#
# Why: the root gradlew requires JVM 17+ to launch (AGENTS.md), but a
# default dev shell — e.g. sdkman `current` pointing at JDK 8 for the
# legacy out-of-band lanes — fails every lefthook Gradle command with
# "FAILURE: Build failed with an exception. Gradle requires JVM 17 or
# later to run." That failure is environmental, not a test failure, and
# it blocks every `git push` (pre-push unit-tests/gametest) and `git
# commit` (pre-commit verify-no-mixin/compile).
#
# This helper exports JAVA_HOME (and prepends PATH) to an installed
# JDK >= 17 when the current one is too old, and exits 1 with an
# actionable message when no such JDK exists. It is SOURCE-ONLY: export
# via `source`, not execution (a child process cannot export to the
# caller).
#
# Usage (in lefthook.yml run commands):
#   bash -c 'source scripts/ensure-jdk17.sh && exec ./gradlew ...'
#
# Discovery order:
#   1. $JAVA_HOME/bin/java if already >= 17 (no-op)
#   2. `java` on PATH if >= 17 (no-op)
#   3. SDKMAN candidates, then /usr/lib/jvm, then macOS
#      /Library/Java/JavaVirtualMachines — picks the NEWEST JDK >= 17

set -u

jdk_major() {
    # $1 = java binary; prints the major version (e.g. 21, or 8 for 1.8.x)
    local java_bin="$1" ver_str major
    ver_str="$("$java_bin" -version 2>&1 | sed -nE 's/.*version "([^"]+)".*/\1/p')"
    [ -n "$ver_str" ] || { printf '0'; return; }
    case "$ver_str" in
        1.*) major="$(printf '%s' "$ver_str" | cut -d. -f2)" ;;
        *)   major="$(printf '%s' "$ver_str" | cut -d. -f1)" ;;
    esac
    printf '%s' "${major:-0}"
}

is_jdk_ok() {
    # $1 = java binary; returns 0 if executable and major >= 17
    local java_bin="$1" major
    [ -x "$java_bin" ] || return 1
    major="$(jdk_major "$java_bin")"
    [ "$major" -ge 17 ] 2>/dev/null
}

# 1/2. Current environment already adequate?
if [ -n "${JAVA_HOME:-}" ] && is_jdk_ok "$JAVA_HOME/bin/java"; then
    return 0
fi
if command -v java >/dev/null 2>&1 && is_jdk_ok "$(command -v java)"; then
    return 0
fi

# 3. Probe known JDK roots for the newest candidate >= 17.
probe_dirs=()
for d in \
    "${SDKMAN_CANDIDATES_DIR:-$HOME/.sdkman/candidates}/java" \
    "$HOME/.sdkman/candidates/java" \
    /usr/lib/jvm \
    /Library/Java/JavaVirtualMachines; do
    [ -d "$d" ] && probe_dirs+=("$d")
done

best=""
best_major=0
for d in "${probe_dirs[@]}"; do
    for jh in "$d"/*/; do
        [ -d "$jh" ] || continue
        for java_bin in "$jh/bin/java" "$jh/Contents/Home/bin/java"; do
            [ -x "$java_bin" ] || continue
            if is_jdk_ok "$java_bin"; then
                major="$(jdk_major "$java_bin")"
                if [ "$major" -gt "$best_major" ]; then
                    best="$jh"
                    best_major="$major"
                fi
            fi
        done
    done
done

if [ -n "$best" ]; then
    export JAVA_HOME="${best%/}"
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
fi

echo "[ensure-jdk17] No JDK >= 17 found. Set JAVA_HOME to a JDK 17+ install" >&2
echo "[ensure-jdk17] (e.g. sdkman: sdk use java 21.0.2-tem) and retry." >&2
exit 1
