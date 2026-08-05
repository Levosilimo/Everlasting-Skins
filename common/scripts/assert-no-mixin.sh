#!/usr/bin/env bash
# assert-no-mixin.sh — No-Mixin pre-flight gate (local and CI).
#
# Mixin policy: forge subprojects consume the mixin-free /common module.
# This script fails if any of the following leak back in:
#   1. mixingradle on the buildscript/plugin classpath
#   2. the org.spongepowered.mixin Gradle plugin id
#   3. a `mixin { }` configuration block in any build script
#   4. Mixin annotations in any source file
#   5. bundling of the empty everlastingskins.mixins.json config
#
# Usage: bash common/scripts/assert-no-mixin.sh [repo-root]
# Defaults to the current directory. Exit 0 = clean, 1 = violations found.
set -u

ROOT="$(cd "${1:-.}" && pwd)"
failures=""

# Build scripts of every forge subproject under ROOT (Gradle internals,
# node_modules and future buildSrc/ convention code are out of scope).
build_scripts="$(
  find "$ROOT" -type f \
    \( -name 'build.gradle' -o -name 'build.gradle.kts' \
       -o -name 'settings.gradle' -o -name 'settings.gradle.kts' \
       -o -name 'gradle.properties' \) 2>/dev/null \
    | grep -vE '/build/|/\.gradle/|/node_modules/|/buildSrc/'
)"

# Source and resource files under any src/ tree.
source_files="$(
  find "$ROOT" -type f \
    \( -name '*.java' -o -name '*.json' -o -name '*.properties' \) \
    -path '*/src/*' 2>/dev/null \
    | grep -vE '/build/|/\.gradle/|/node_modules/'
)"

fail() {
  local label="$1"
  local matches="$2"
  if [ -n "$matches" ]; then
    failures="${failures}${label}:\n${matches}\n"
  fi
}

# 1. mixingradle on the classpath (literal coordinate).
# (/dev/null guards the no-matches-found case: grep without file args would
# block reading stdin.)
fail "mixingradle classpath reference" \
  "$(grep -rnF 'org.spongepowered:mixingradle' $build_scripts /dev/null 2>/dev/null)"

# 2. org.spongepowered.mixin Gradle plugin id.
fail "mixin Gradle plugin id" \
  "$(grep -rnE "id[ (][\"']org\\.spongepowered\\.mixin" $build_scripts /dev/null 2>/dev/null)"

# 3. `mixin { }` configuration block.
fail "mixin {} block" \
  "$(grep -rnE '^[[:space:]]*mixin[[:space:]]*\{' $build_scripts /dev/null 2>/dev/null)"

# 4. Mixin annotations in source (must be zero).
fail "Mixin annotation in source" \
  "$(grep -rnF '@Mixin' $source_files /dev/null 2>/dev/null)"

# 5. Bundling the (empty) mixins config.
fail "everlastingskins.mixins.json bundling" \
  "$(grep -rnF 'everlastingskins.mixins.json' $build_scripts $source_files /dev/null 2>/dev/null)"

if [ -n "$failures" ]; then
  printf 'Mixin policy violated:\n%b' "$failures"
  printf '\nMixin policy: consumers contain ZERO Mixin usage. If a consumer requires\n'
  printf 'Mixin support, fork the consumer — it is a sign that should not happen in /common.\n'
  exit 1
fi

echo "Mixin check passed: zero @Mixin annotations, zero mixingradle references."
exit 0
