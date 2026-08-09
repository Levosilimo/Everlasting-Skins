# Contributing

## Branch layout

Each Minecraft version has its own branch. Do not merge between incompatible
Minecraft version branches.

## Before committing

The repo uses Lefthook-managed git hooks (`lefthook.yml`); the pre-commit
hook runs `aislop scan --staged`. Fresh clones activate them with:

    bash scripts/setup-hooks.sh

## Code style

- No AI-slop patterns (excessive comments, defensive overengineering).
- Comments restate what the code cannot say, not what it does.
- Keep commits focused on one concern. Use `Backport-of:` trailers for
  cherry-picks that needed version-specific changes.

## Submitting changes

Open a pull request against the relevant Minecraft version branch, not `1.21`
unless the change is 1.21-specific.
