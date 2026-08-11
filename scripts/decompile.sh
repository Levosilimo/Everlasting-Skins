#!/usr/bin/env bash
# scripts/decompile.sh
# Vineflower 1.12.0 wrapper for reading the vendored-harness lanes
# (1.4.7 / 1.5.2 / 1.6.4). See the AGENTS.md "Reading vendored-lane
# bytecode" section for when to use this vs. bytecode.sh.
#
# Pinned tool (sha1-verified at every run; fetched once from Maven Central
# into ~/.cache/everlastingskins/tools/):
#   vineflower-1.12.0.jar  sha1 85570609a0a5941a7d2918b6260b209de810f66f
#   (org.vineflower:vineflower:1.12.0)
#
# Vineflower 1.12.0 requires a JVM 17+; the lanes themselves build on Java 8,
# so the script resolves a modern java (JAVA_HOME, then sdkman candidates
# 21/25/current, then PATH) instead of assuming `java` is new enough.
#
# Usage:
#   scripts/decompile.sh <in.jar> <out-src-dir>
#   scripts/decompile.sh --all-vendored
#
# Flags (fixed for MC-era reading): -din=1 -rbr=1 -dgs=1 -asc=1 -rsy=1
#   -iec=1 -jvn=1 -isl=0 -iib=1 -bsm=1 -dcl=1 -lit=1 -nls=1 -log=ERROR
#
# --all-vendored batch: walks ONLY the path-bound roots (this project never
# runs filesystem-wide searches — big slow HDDs): the raw jars in
# ~/.gradle/everlastingskins-vendored/<era>/ and the deobf'd dev jars in
# <repo>/forge-<era>/build/deobf/. Skips any output that already exists
# under ~/.gradle/everlastingskins-vendored/<era>/decompiled/<name>.src/.

set -euo pipefail

TOOLS_DIR="${HOME}/.cache/everlastingskins/tools"
VINEFLOWER_JAR="${TOOLS_DIR}/vineflower-1.12.0.jar"
VINEFLOWER_SHA1="85570609a0a5941a7d2918b6260b209de810f66f"
VINEFLOWER_URL="https://repo1.maven.org/maven2/org/vineflower/vineflower/1.12.0/vineflower-1.12.0.jar"
VENDORED_ROOT="${HOME}/.gradle/everlastingskins-vendored"
ERAS=(1.4.7 1.5.2 1.6.4)
FLAGS=(-din=1 -rbr=1 -dgs=1 -asc=1 -rsy=1 -iec=1 -jvn=1 -isl=0 -iib=1 -bsm=1 -dcl=1 -lit=1 -nls=1 -log=ERROR)

die() { echo "ERROR: $*" >&2; exit 1; }

# Resolve a JVM >= 17 (Vineflower requirement; the lanes' Java 8 is not enough).
resolve_java() {
  local candidates=()
  [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]] && candidates+=("${JAVA_HOME}/bin/java")
  candidates+=(
    "${HOME}/.sdkman/candidates/java/current/bin/java"
    "${HOME}/.sdkman/candidates/java/21.0.2-tem/bin/java"
    "${HOME}/.sdkman/candidates/java/25.0.2-tem/bin/java"
  )
  command -v java >/dev/null 2>&1 && candidates+=("$(command -v java)")
  local java major
  for java in "${candidates[@]}"; do
    [[ -x "${java}" ]] || continue
    major="$("${java}" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
    [[ "${major}" -ge 17 ]] 2>/dev/null && { echo "${java}"; return 0; }
  done
  die "no JVM >= 17 found (Vineflower 1.12.0 requirement); set JAVA_HOME"
}

ensure_tool() {
  if [[ ! -f "${VINEFLOWER_JAR}" ]]; then
    mkdir -p "${TOOLS_DIR}"
    echo "Fetching Vineflower 1.12.0 from Maven Central..."
    curl -fsSL -o "${VINEFLOWER_JAR}" "${VINEFLOWER_URL}"
  fi
  local actual
  actual="$(sha1sum "${VINEFLOWER_JAR}" | awk '{print $1}')"
  [[ "${actual}" == "${VINEFLOWER_SHA1}" ]] \
    || die "vineflower sha1 mismatch: got ${actual}, want ${VINEFLOWER_SHA1} (delete ${VINEFLOWER_JAR} to re-fetch)"
}

decompile_one() { # <in.jar> <out-dir>
  local in="$1" out="$2"
  [[ -f "${in}" ]] || die "input jar not found: ${in}"
  mkdir -p "$(dirname "${out}")"
  echo "Decompiling $(basename "${in}") -> ${out}"
  "$(resolve_java)" -Xmx4g -jar "${VINEFLOWER_JAR}" "${FLAGS[@]}" "${in}" "${out}"
}

if [[ "${1:-}" == "--all-vendored" ]]; then
  ensure_tool
  REPO_ROOT="$(git rev-parse --show-toplevel)"
  for era in "${ERAS[@]}"; do
    vendored="${VENDORED_ROOT}/${era}"
    [[ -d "${vendored}" ]] || continue
    out_root="${vendored}/decompiled"
    # Raw vendored jars (single dir, no recursion).
    for jar in "${vendored}"/*.jar; do
      [[ -f "${jar}" ]] || continue
      name="$(basename "${jar}" .jar)"
      out="${out_root}/${name}.src"
      if [[ -d "${out}" ]]; then
        echo "skip (already decompiled): ${out}"
      else
        decompile_one "${jar}" "${out}"
      fi
    done
    # Lane deobf'd dev jars.
    lane="${REPO_ROOT}/forge-${era}/build/deobf"
    [[ -d "${lane}" ]] || continue
    for jar in "${lane}"/*deobf.jar; do
      [[ -f "${jar}" ]] || continue
      name="$(basename "${jar}" .jar)"
      out="${out_root}/${name}.src"
      if [[ -d "${out}" ]]; then
        echo "skip (already decompiled): ${out}"
      else
        decompile_one "${jar}" "${out}"
      fi
    done
  done
  done_count=0
  for era in "${ERAS[@]}"; do
    [[ -d "${VENDORED_ROOT}/${era}/decompiled" ]] && done_count=$((done_count + 1))
  done
  echo "Batch complete: ${done_count} era(s) with output under ${VENDORED_ROOT}/<era>/decompiled/"
else
  [[ $# -eq 2 ]] || die "usage: scripts/decompile.sh <in.jar> <out-src-dir> | --all-vendored"
  ensure_tool
  decompile_one "$1" "$2"
fi
