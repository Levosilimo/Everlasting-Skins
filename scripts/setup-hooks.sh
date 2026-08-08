#!/usr/bin/env bash
# scripts/setup-hooks.sh
# Boot Lefthook for a fresh clone of Everlasting-Skins.
# Verifies lefthook is installed, then wires the hooks via
# `lefthook install --reset-hooks-path` (which writes to .git/hooks/ and
# clears core.hooksPath).
#
# Usage: bash scripts/setup-hooks.sh
# Idempotent: re-running is safe.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Check lefthook is installed
if ! command -v lefthook >/dev/null 2>&1; then
  echo "ERROR: lefthook not found on PATH" >&2
  echo "Install: https://github.com/evilmartians/lefthook" >&2
  exit 1
fi
LEFTHOOK_VERSION=$(lefthook --version)
echo "Using lefthook ${LEFTHOOK_VERSION}"

# Verify lefthook.yml exists
if [ ! -f lefthook.yml ]; then
  echo "ERROR: lefthook.yml not found at repo root" >&2
  exit 1
fi

# Validate the config
lefthook validate

# Install hooks (and un-set core.hooksPath)
lefthook install --reset-hooks-path

# Verify
echo "core.hooksPath = $(git config core.hooksPath)"
ls -la .git/hooks/pre-commit .git/hooks/pre-push

echo "Lefthook installed. Smoke test: a real commit will trigger pre-commit."
