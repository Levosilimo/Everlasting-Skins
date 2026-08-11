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
# FML 4.7/5.2 relauncher library pre-seed (MERGE-era lanes only)
# ---------------------------------------------------------------------------
# The legacy FMLRelauncher (1.4.7/1.5.2) demands its CoreFMLLibraries set in
# <home>/lib with BYTE-EXACT checksums and hard-fails when a download fails
# or an existing file mismatches — and the original
# files.minecraftforge.net/fmllibs host has been dead (404 HTML) for years.
# files.prismlauncher.org/fmllibs (PrismLauncher's legacy FML mirror)
# carries the identical bytes; every sha1 below was verified against FML's
# hardcoded checksums (CoreFMLLibraries static{}). The 1.5.2
# deobfuscation_data zip is the REAL srg (FML 5.2's remapper is checksum-
# enforced, so the 1.6.4-style identity path is unavailable): every class
# through the RelaunchClassLoader is remapped obf→srg consistently — MC,
# FML and the reobf'd mod alike — which keeps the reobf'd jar coherent.
seed_fml_libdir() {
    local target="$1" lane="$2"
    mkdir -p "$target/lib"
    local base="https://files.prismlauncher.org/fmllibs"
    case "$lane" in
        1.5.2)
            fetch_artifact "argo-small-3.2.jar" "$base/argo-small-3.2.jar" "58912ea2858d168c50781f956fa5b59f0f7c6b51"
            fetch_artifact "guava-14.0-rc3.jar" "$base/guava-14.0-rc3.jar" "931ae21fa8014c3ce686aaa621eae565fefb1a6a"
            fetch_artifact "asm-all-4.1.jar" "$base/asm-all-4.1.jar" "054986e962b88d8660ae4566475658469595ef58"
            fetch_artifact "bcprov-jdk15on-148.jar" "$base/bcprov-jdk15on-148.jar" "960dea7c9181ba0b17e8bab0c06a43f0a5f04e65"
            fetch_artifact "scala-library.jar" "$base/scala-library.jar" "458d046151ad179c85429ed7420ffb1eaf6ddf85"
            fetch_artifact "deobfuscation_data_1.5.2.zip" "$base/deobfuscation_data_1.5.2.zip" "446e55cd986582c70fcf12cb27bc00114c5adfd9"
            for f in argo-small-3.2.jar guava-14.0-rc3.jar asm-all-4.1.jar \
                bcprov-jdk15on-148.jar scala-library.jar deobfuscation_data_1.5.2.zip; do
                cp "$E2E_CACHE_DIR/$lane/$f" "$target/lib/$f"
            done
            ;;
        1.4.7)
            fetch_artifact "argo-2.25.jar" "$base/argo-2.25.jar" "bb672829fde76cb163004752b86b0484bd0a7f4b"
            fetch_artifact "guava-12.0.1.jar" "$base/guava-12.0.1.jar" "b8e78b9af7bf45900e14c6f958486b6ca682195f"
            fetch_artifact "asm-all-4.0.jar" "$base/asm-all-4.0.jar" "98308890597acb64047f7e896638e0d98753ae82"
            fetch_artifact "bcprov-jdk15on-147.jar" "$base/bcprov-jdk15on-147.jar" "b6f5d9926b0afbde9f4dbe3db88c5247be7794bb"
            for f in argo-2.25.jar guava-12.0.1.jar asm-all-4.0.jar bcprov-jdk15on-147.jar; do
                cp "$E2E_CACHE_DIR/$lane/$f" "$target/lib/$f"
            done
            ;;
        *)
            return 1
            ;;
    esac
    e2e_log "FML lib dir pre-seeded: $(basename "$target")/lib ($(ls "$target/lib" | tr '\n' ' '))"
}

# ---------------------------------------------------------------------------
# Result contract helpers
# ---------------------------------------------------------------------------
# assemble_result <server_booted> [actor_json] [observer_json] — merges
# script-side facts into the final ${RUNNER_TMP}/e2e-result.json. The in-jar
# driver writes its client-side document (client_joined, command_executed,
# renderer_state, renderer_verified, duration_ms, exit_code, artifacts) to
# the client gameDir; the observer driver writes its own document
# (observer_joined, observer_renderer_state, observer_renderer_verified,
# observer_exit_code, ...). This replaces the client's server_booted:false
# placeholder and appends every observer_* field (additive, backward-
# compatible: actor fields are untouched).
assemble_result() {
    local server_booted="$1" client_json="${2:-}" observer_json="${3:-}"
    if [ -z "$client_json" ] || [ ! -f "$client_json" ]; then
        e2e_fail "assemble_result: client result file missing ($client_json)"
    fi
    python3 - "$client_json" "$RESULT_JSON" "$server_booted" "$observer_json" <<'PY'
import json, sys
client, out, booted, obs = sys.argv[1], sys.argv[2], sys.argv[3] == "true", sys.argv[4]
doc = json.load(open(client))
doc["lane"] = doc.get("lane", "1.6.4")
doc["server_booted"] = booted
if obs:
    observer = json.load(open(obs))
    for k, v in observer.items():
        if k.startswith("observer_"):
            doc[k] = v
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

# observer_exit_code — the observer driver's exit code from the final doc.
observer_exit_code() {
    python3 -c "import json; print(json.load(open('$RESULT_JSON')).get('observer_exit_code', 3))"
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
