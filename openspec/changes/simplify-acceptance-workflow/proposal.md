## Why

Alchemy acceptance retries replay unrelated scenarios and prepare a second workspace after release. Separate selectable scenarios and a combined fixed-source run reduce that repetition without removing final evidence.

## What Changes

- Add independently initialized brewing, medicine, persistence, UI and multiplayer cases with explicit selected/completed/unrun coverage.
- Add a combined release-alchemy preset: one upload, one Sandbox/workspace, release once, clean stage1 and complete runtime evidence.
- Remove alchemy-specific preliminary Java selection from the generic release preset.
- Preserve fixed-commit provenance, clean final validation, failure history and confirmed cleanup; no automatic retries.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `modal-test-runner`: selected feature cases and combined release/runtime execution.

## Impact

Python evidence/controller scripts, focused tests, README/AGENTS/KB and release metadata. No gameplay changes. Chunky decoupling and general impact-based test selection are deferred.
