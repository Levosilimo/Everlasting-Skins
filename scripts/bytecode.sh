#!/usr/bin/env bash
# scripts/bytecode.sh
# Bytecode-precision reading for the vendored-harness lanes
# (1.4.7 / 1.5.2 / 1.6.4). See the AGENTS.md "Reading vendored-lane
# bytecode" section for when to use this vs. decompile.sh.
#
# Pinned tools (sha1-verified at every run; fetched once from Maven Central
# into ~/.cache/everlastingskins/tools/):
#   asm-9.10.1.jar       sha1 ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236
#   asm-util-9.10.1.jar  sha1 7bb9d450e8d4cbf9f9e04096c44bbfe7fba80b15
#   (org.ow2.asm:asm:9.10.1, org.ow2.asm:asm-util:9.10.1)
#
# Usage:
#   scripts/bytecode.sh <classfile>                 ASM Textifier -nodebug
#   scripts/bytecode.sh --offsets <classfile>       Textifier, numeric label offsets
#   scripts/bytecode.sh --cp <era>:<class>          javap -v (raw constant-pool dump)
#   scripts/bytecode.sh <era>:<class> [--offsets]   unzip -p from the vendored
#                                                   client/server jar, then Textifier
#
# <class> is a slash-separated binary name (e.g.
# net/minecraft/src/ThreadDownloadImageData). <era> is 1.4.7 / 1.5.2 / 1.6.4;
# the jar is looked up under ~/.gradle/everlastingskins-vendored/<era>/
# (client jar first, then server jar). A direct .class path also works.

set -euo pipefail

TOOLS_DIR="${HOME}/.cache/everlastingskins/tools"
ASM_JAR="${TOOLS_DIR}/asm-9.10.1.jar"
ASM_UTIL_JAR="${TOOLS_DIR}/asm-util-9.10.1.jar"
ASM_SHA1="ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236"
ASM_UTIL_SHA1="7bb9d450e8d4cbf9f9e04096c44bbfe7fba80b15"
ASM_URL="https://repo1.maven.org/maven2/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar"
ASM_UTIL_URL="https://repo1.maven.org/maven2/org/ow2/asm/asm-util/9.10.1/asm-util-9.10.1.jar"
VENDORED_ROOT="${HOME}/.gradle/everlastingskins-vendored"

die() { echo "ERROR: $*" >&2; exit 1; }

fetch_sha1_verified() { # <url> <out> <want>
  local url="$1" out="$2" want="$3" actual
  if [[ ! -f "${out}" ]]; then
    mkdir -p "${TOOLS_DIR}"
    echo "Fetching $(basename "${out}") from Maven Central..."
    curl -fsSL -o "${out}" "${url}"
  fi
  actual="$(sha1sum "${out}" | awk '{print $1}')"
  [[ "${actual}" == "${want}" ]] \
    || die "$(basename "${out}") sha1 mismatch: got ${actual}, want ${want} (delete ${out} to re-fetch)"
}

ensure_tools() {
  fetch_sha1_verified "${ASM_URL}" "${ASM_JAR}" "${ASM_SHA1}"
  fetch_sha1_verified "${ASM_UTIL_URL}" "${ASM_UTIL_JAR}" "${ASM_UTIL_SHA1}"
}

ensure_driver() {
  ensure_tools
  if [[ ! -f "${TOOLS_DIR}/OffsetsDriver.class" ]]; then
    local src="${REPO_ROOT}/scripts/asm-offsets/OffsetsDriver.java"
    [[ -f "${src}" ]] || die "offset driver source not found: ${src}"
    echo "Compiling offset driver into ${TOOLS_DIR}..."
    javac -cp "${ASM_JAR}:${ASM_UTIL_JAR}" -d "${TOOLS_DIR}" "${src}"
  fi
}

classfile_from_era() { # <era:class> -> echoes extracted temp class file
  local arg="$1" era class jar
  era="${arg%%:*}"
  class="${arg#*:}"
  [[ "${class}" == "${arg}" ]] && die "expected <era>:<class>, got: ${arg}"
  local found=""
  for jar in "${VENDORED_ROOT}/${era}"/minecraft_client.*.jar "${VENDORED_ROOT}/${era}"/minecraft_server.*.jar; do
    [[ -f "${jar}" ]] || continue
    if unzip -l "${jar}" "${class}.class" >/dev/null 2>&1; then
      found="${jar}"
      break
    fi
  done
  [[ -n "${found}" ]] || die "class ${class} not found in ${VENDORED_ROOT}/${era}/ client/server jars"
  mkdir -p "${TOOLS_DIR}/tmp"
  local tmp
  tmp="${TOOLS_DIR}/tmp/$(basename "${found}" .jar)-${class//\//_}.class"
  unzip -p "${found}" "${class}.class" > "${tmp}"
  echo "${tmp}"
}

# --- mode / argument parsing ---
MODE="textifier"
ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --offsets) MODE="offsets" ;;
    --cp) MODE="javap-v" ;;
    *) ARGS+=("$1") ;;
  esac
  shift
done
[[ ${#ARGS[@]} -eq 1 ]] || die "usage: scripts/bytecode.sh [--offsets|--cp] <classfile | <era>:<class>>"

REPO_ROOT="$(git rev-parse --show-toplevel)"
TARGET="${ARGS[0]}"

if [[ "${TARGET}" == *:* ]]; then
  TARGET="$(classfile_from_era "${TARGET}")"
fi
[[ -f "${TARGET}" ]] || die "class file not found: ${TARGET}"

case "${MODE}" in
  textifier)
    ensure_tools
    java -cp "${ASM_JAR}:${ASM_UTIL_JAR}" org.objectweb.asm.util.Textifier -nodebug "${TARGET}"
    ;;
  offsets)
    ensure_driver
    java -cp "${TOOLS_DIR}:${ASM_JAR}:${ASM_UTIL_JAR}" OffsetsDriver -nodebug "${TARGET}"
    ;;
  javap-v)
    javap -v "${TARGET}"
    ;;
esac
