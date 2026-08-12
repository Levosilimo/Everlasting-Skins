# gh-api-bump — branch-protection bump scripts

One-shot `gh api` scripts that atomically add a new required-status context to
the branch-protection contract on `main` (and `integration/m2-monorepo` if it
still exists — AGENTS.md "Branch policy & required checks"). Branch protection
is enforced via the gh API — never in-repo — so CI job names must match the
required-check strings exactly.

## PATCH, not PUT

All ten scripts use PATCH. `gh api -X PUT` on
`/branches/<branch>/protection/required_status_checks` returns 404 — the
protection subresource is PATCH-only (verified empirically during the
three-lane expansion deadlock-resolution sequence, 2026-08-06). Nine scripts
PATCH `.../protection/required_status_checks` directly; `1.7.10.sh` PATCHes the
full `.../protection` object (`--method PATCH`).

## When to use

After a new lane's PR lands, run that lane's script with `--apply` BEFORE
opening the next lane PR: the newly required context gates the next PR's CI.
The contract advances one lane at a time (12 -> 13 -> 14 -> ... -> N).

| Script    | Adds                     | Expected baseline                     |
|-----------|--------------------------|---------------------------------------|
| `26.2.sh` | `Build (26.2)`           | 12 (pre-forge-26.2 contract)          |
| `1.8.9.sh`| `Build (forge-1.8.9)`    | 13 (after forge-26.2 merged)          |
| `1.7.10.sh`| `Build (forge-1.7.10)`  | 14 (after forge-1.8.9 merged)         |
| `CI-Health.sh` | `CI Health`         | 15 (after forge-1.7.10 merged)        |
| `26.1.sh` | `Build (26.1)`           | 16 (after CI Health promoted, lib-69) |
| `1.18.2.sh`| `Build (forge-1.18.2)`  | 17 (after 26.1 landed)                |
| `1.10.2.sh`| `Build (forge-1.10.2)`  | 18 (after 1.18.2 landed)              |
| `1.6.4.sh` | `Build (forge-1.6.4)`   | 19 (after 1.10.2 landed)              |
| `1.5.2.sh` | `Build (forge-1.5.2)`   | 20 (after 1.6.4 landed)               |
| `1.4.7.sh` | `Build (forge-1.4.7)`   | 21 (after 1.5.2 landed)               |

The contract now stands at 22 contexts (all ten applied; `Build (forge-1.4.7)`
landed as the 22nd via `1.4.7.sh`, 21 -> 22).

`integration/m2-monorepo` was retired 2026-08-07 (PR #314) and returns HTTP 404.
The three pre-retirement scripts (`26.2.sh`, `1.8.9.sh`, `1.7.10.sh`) still
carry both branches in their target list; the seven post-retirement scripts
(`CI-Health.sh` onward) target `main` only.

## Dead-state rules

Each script refuses to write unless the live contract matches its expected
baseline and the earlier lanes' contexts are present. Idempotent re-runs are
no-ops: the already-present check runs first, so a script whose context is
already required exits 0 even when the live contract has moved past its
baseline.

## Usage

    bash 26.2.sh --help            # usage
    bash 26.2.sh --dry-run         # read-only: print the planned PATCH payload
    bash 26.2.sh --apply           # write (admin rights; fails fast on guard)
    bash 26.2.sh --verify [ref]    # after first CI run: confirm the check-run

Requires: `gh` (authenticated, admin rights for `--apply`) + `jq`. The
scripts run from anywhere; only `--verify` needs a ref to the lane head.

For multi-lane expansions with cross-lane required contexts, use the
temporary-relax-then-restore dance documented in FINAL-REPORT.md under
"Post-Merge Deadlock Resolution".
