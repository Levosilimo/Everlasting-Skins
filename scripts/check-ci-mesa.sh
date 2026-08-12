#!/usr/bin/env bash
# Mesa software-GL apt guard (S1 systemic breakage).
#
# Ubuntu 24.04 (noble) renamed libasound2 -> libasound2t64 (the 64-bit-time
# transition); the plain libasound2 package no longer exists on the
# ubuntu-24.04 runner image, so any bare reference in the E2E apt lists
# makes `apt-get install` fail for every job sharing the step. This guard
# fails when ci.yml contains a bare `libasound2` token (i.e. not followed
# by `t64`), so the regression cannot land again via copy-paste of an old
# step or a new lane reusing the pre-fix install list.
#
# Usage: bash scripts/check-ci-mesa.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CI_YML="$ROOT/.github/workflows/ci.yml"

if [[ ! -f "$CI_YML" ]]; then
  echo "check-ci-mesa: $CI_YML not found" >&2
  exit 1
fi

# Bare libasound2 (not libasound2t64). -P is fine here: CI runners are
# GNU grep on ubuntu images, and the guard itself runs in CI's lint-yaml
# job (and optionally in pre-commit on the dev host).
if grep -nP 'libasound2(?!t64)' "$CI_YML"; then
  echo "check-ci-mesa: FAIL — bare 'libasound2' found in .github/workflows/ci.yml" >&2
  echo "  Ubuntu 24.04 renamed libasound2 -> libasound2t64; use libasound2t64" >&2
  echo "  in every E2E apt list (e2e-modern Mesa step et al)." >&2
  exit 1
fi

echo "check-ci-mesa: OK — no bare libasound2 references in ci.yml"
exit 0
