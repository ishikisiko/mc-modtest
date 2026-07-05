## Why

CRAFT now has run artifacts, role-scoped tasks, human-verdict stops, and
Commander-owned closeout. The missing operational piece is fast recall: "continue
the last intent", "what needs my decision", "which change is closeout-ready", and
"who touched this artifact" still require scanning many JSON files and OpenSpec
directories manually.

This change adds a lightweight SQLite operational index that can be rebuilt from
existing artifacts. It improves CRAFT continuity without replacing the current
artifact-first audit model.

## What Changes

- Add `tools/genops/state_store.py`, a stdlib SQLite CLI for `.genops/state.sqlite`.
- Index CRAFT runs, tasks, artifacts, gates, decisions, closeout state, and
  events from `reports/agent_runs/**` plus OpenSpec archive/current state.
- Add commands for `init`, `rebuild`, `index-run`, `current`,
  `pending-decisions`, `closeout-ready`, `record-decision`, and
  `artifact-owner`.
- Add `.genops/` to `.gitignore` because the database is a local cache.
- Document the state-store role as an operational index, not source of truth.

## Capabilities

### Modified Capabilities

- `genops`: Adds a rebuildable SQLite operational index for CRAFT/GenOps run
  evidence and intent continuity.

## Impact

- Affected tools: `tools/genops/state_store.py`.
- Affected docs: `CRAFT.md`, `docs/ai-kb/19_genops.md`, `genops/README.md`.
- Affected specs: `openspec/specs/genops/spec.md`.
- No Java runtime behavior, generated structures, release metadata, or jar
  version changes.
