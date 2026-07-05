## Context

GenOps already writes deterministic evidence under `reports/agent_runs/<run_id>/`.
That evidence is audit-friendly but awkward for interactive continuity. The owner
should be able to ask natural questions such as "continue the previous intent" or
"what is waiting for me" without the Commander scanning every run directory by
hand each time.

The design constraint is important: SQLite must not become hidden truth. The
repo source, OpenSpec specs, and run artifacts remain authoritative.

## Decisions

### D1: SQLite is a rebuildable operational index

The state store lives at `.genops/state.sqlite`, is gitignored, and may be
deleted at any time. `state_store.py rebuild` reconstructs it from
`reports/agent_runs/**`, OpenSpec active/archive state, and local evidence files.

### D2: First version is CLI-only and stdlib-only

The first implementation uses Python's `sqlite3` module and a local CLI. It does
not add a service, daemon, dependency, queue, or background process.

### D3: Queries target Commander continuity

The initial CLI supports current intent lookup, pending decisions,
closeout-ready entries, artifact ownership lookup, run indexing, rebuild, and
decision recording. Worker leasing and task claims are intentionally deferred.

### D4: Decisions may be mirrored to run artifacts

`record-decision` writes to SQLite and, when a run id is provided, writes a JSON
decision artifact under `reports/agent_runs/<run_id>/artifacts/decisions/` so a
future rebuild can recover it.

### D5: Conservative closeout indexing

The state store can see active complete OpenSpec changes, but it does not pretend
they are archive-ready unless closeout evidence says so. This prevents SQLite
from bypassing CRAFT's validation/verdict/front-door gates.

## Risks / Trade-offs

- **Risk: SQLite becomes hidden truth.** Mitigation: keep it ignored and
  rebuildable, and document JSON artifacts as audit truth.
- **Risk: Rebuild misses decisions.** Mitigation: mirror recorded decisions to
  run artifacts when possible.
- **Risk: Closeout appears automatic without gates.** Mitigation: mark complete
  active changes as needing CRAFT closeout evidence unless a run records
  readiness.

## Migration Plan

1. Add `.genops/` ignore rule.
2. Add `tools/genops/state_store.py` and smoke tests.
3. Document state-store commands and truth-source boundaries.
4. Add GenOps spec requirements.
5. Validate and build.
