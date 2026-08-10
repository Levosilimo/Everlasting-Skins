#!/usr/bin/env bash
# scripts/e2e/lib.sh — shared real-client E2E layer (master-plan contract).
#
# Sourced by e2e-common.sh and the era driver scripts. Provides:
#   - artifact fetch + sha1-verify into ~/.cache/everlastingskins/e2e/<lane>
#     (pre-seedable; fails hard on checksum drift — the client jar pin is
#     what makes the in-jar driver's obf-domain reflection names stable)
#   - the ${RUNNER_TMP}/e2e-result.json contract helpers (final-doc assembly
#     + exit-code mapping: 0 all green | 1 assertion failed | 2 retryable
#     infra | 3 build failure)
#   - log/step helpers with greppable ES_E2E_* markers
#
# Usage: source "$(dirname "$0")/lib.sh"

set -euo pipefail

E2E_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$E2E_ROOT/.." && pwd)"

# ---------------------------------------------------------------------------
# Environment contract (set by the caller / CI job)
# ---------------------------------------------------------------------------
: "${RUNNER_TMP:=/tmp/everlastingskins-e2e}"
: "${E2E_CACHE_DIR:=$HOME/.cache/everlastingskins/e2e}"
: "${E2E_LANE:=1.6.4}"
: "${E2E_USERNAME:=TestPlayer}"
: "${E2E_JAVA8:=$JAVA_HOME}"

RESULT_JSON="$RUNNER_TMP/e2e-result.json"

mkdir -p "$RUNNER_TMP" "$E2E_CACHE_DIR/$E2E_LANE"

e2e_log() { echo "[e2e:$E2E_LANE] $*"; }
e2e_warn() { echo "[e2e:$E2E_LANE][warn] $*" >&2; }
# Hard failure (missing config, checksum drift): exit 3.
e2e_fail() { echo "[e2e:$E2E_LANE][fail] $*" >&2; exit 3; }
# Assertion failure (contract): exit 1.
e2e_assert_fail() { echo "[e2e:$E2E_LANE][assert-fail] $*" >&2; exit 1; }
# Retryable infra failure: exit 2.
e2e_infra_fail() { echo "[e2e:$E2E_LANE][infra-fail] $*" >&2; exit 2; }

# ---------------------------------------------------------------------------
# Artifact fetch (curl + sha1 verify; cache dir is pre-seedable for offline)
# ---------------------------------------------------------------------------
# fetch_artifact <cache-name> <url> <sha1>
fetch_artifact() {
    local name="$1" url="$2" sha1="$3"
    local target="$E2E_CACHE_DIR/$E2E_LANE/$name"
    if [ -f "$target" ]; then
        local have
        have=$(sha1sum "$target" | awk '{print $1}')
        if [ "$have" = "$sha1" ]; then
            e2e_log "artifact $name: cached+verified"
            return 0
        fi
        e2e_warn "artifact $name: cache sha1 drift ($have != $sha1), refetching"
        rm -f "$target"
    fi
    e2e_log "artifact $name: fetching $url"
    curl -sfL --max-time 120 -o "$target.part" "$url" || { rm -f "$target.part"; e2e_infra_fail "artifact $name: download failed"; }
    local got
    got=$(sha1sum "$target.part" | awk '{print $1}')
    if [ "$got" != "$sha1" ]; then
        rm -f "$target.part"
        e2e_fail "artifact $name: sha1 mismatch (expected $sha1, got $got)"
    fi
    mv "$target.part" "$target"
    e2e_log "artifact $name: fetched+verified"
}

# ---------------------------------------------------------------------------
# Result contract helpers
# ---------------------------------------------------------------------------
# assemble_result <server_booted> — merges script-side facts into the final
# ${RUNNER_TMP}/e2e-result.json. The in-jar driver writes its client-side
# document (client_joined, command_executed, renderer_state,
# renderer_verified, duration_ms, exit_code, artifacts) to the client
# gameDir; this replaces the client's server_booted:false placeholder.
assemble_result() {
    local server_booted="$1" client_json="${2:-}"
    if [ -z "$client_json" ] || [ ! -f "$client_json" ]; then
        e2e_fail "assemble_result: client result file missing ($client_json)"
    fi
    python3 - "$client_json" "$RESULT_JSON" "$server_booted" <<'PY'
import json, sys
client, out, booted = sys.argv[1], sys.argv[2], sys.argv[3] == "true"
doc = json.load(open(client))
doc["lane"] = doc.get("lane", "1.6.4")
doc["server_booted"] = booted
with open(out, "w") as f:
    json.dump(doc, f, indent=2)
PY
    e2e_log "final result: $RESULT_JSON"
}

# assert_result <field> <expected> — hard assertion on the final doc.
assert_result() {
    local field="$1" expected="$2"
    local actual
    actual=$(python3 -c "import json,sys; print(json.load(open('$RESULT_JSON')).get('$field'))")
    if [ "$actual" != "$expected" ]; then
        e2e_assert_fail "e2e-result.json.$field = $actual (expected $expected)"
    fi
    e2e_log "assert ok: $field=$actual"
}

# result_exit_code — the driver's exit code from the final doc.
result_exit_code() {
    python3 -c "import json; print(json.load(open('$RESULT_JSON')).get('exit_code', 3))"
}

# ---------------------------------------------------------------------------
# Process-tree helpers (setsid + group-kill; no pkill by project policy)
# ---------------------------------------------------------------------------
# start_guarded <logfile> <cmd...> — runs <cmd> in its own session, tracks
# the PGID so cleanup can kill the whole tree (xvfb + java client).
start_guarded() {
    local log="$1"
    shift
    setsid "$@" > "$log" 2>&1 &
    echo $!
}

kill_tree() {
    local pid="$1"
    local pgid
    pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ' || true)
    if [ -n "$pgid" ] && [ "$pgid" != "$$" ]; then
        kill -- "-$pgid" 2>/dev/null || true
    else
        kill "$pid" 2>/dev/null || true
    fi
}

# wait_for_log <logfile> <pattern> <timeout_s> — returns 0 when the pattern
# appears in the log (tail-poll; cheap on the small e2e logs).
wait_for_log() {
    local log="$1" pattern="$2" timeout_s="$3"
    local i=0
    while [ "$i" -lt "$timeout_s" ]; do
        if [ -f "$log" ] && grep -qE "$pattern" "$log" 2>/dev/null; then
            return 0
        fi
        sleep 1
        i=$((i + 1))
    done
    return 1
}
