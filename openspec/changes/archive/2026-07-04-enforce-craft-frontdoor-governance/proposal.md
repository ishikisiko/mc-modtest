## Why

CRAFT is currently documented as the natural-language front door for generator
and planning work, but that boundary is advisory rather than enforceable. Recent
planning work showed that an agent can still bypass CRAFT and write OpenSpec
artifacts directly, losing worker ownership, run evidence, and verdict state.

This change makes CRAFT/GenOps the required entry point for high-impact project
work, including OpenSpec proposal authoring itself, so future changes are
traceable to a run, pipeline, worker role, task contract, and human decision.

## What Changes

- Define "CRAFT-required" intent classes: explicit CRAFT/GenOps requests, new
  OpenSpec changes, apply/implementation work, visual/aesthetic structure work,
  multi-worker/subagent work, release/build handoff, and acceptance handoff.
- Require CRAFT-required work to be backed by a GenOps run manifest and task
  graph before OpenSpec artifacts, code, generated assets, or release metadata
  are edited.
- Add an `openspec-change.full` GenOps pipeline so OpenSpec proposals,
  designs, specs, and tasks are authored by scoped worker roles rather than by
  the Commander directly.
- Add front-door provenance checks that flag protected-path changes lacking
  corresponding CRAFT run evidence.
- Require Commander summaries for CRAFT-required work to report the run id,
  pipeline, worker/task ownership, artifacts, gates, verdict state, and next
  decision.
- Define a one-time bootstrap exception for this governance change, because the
  `openspec-change.full` pipeline does not exist until this change is
  implemented.
- De-prioritize `add-visual-reference-structure-pipeline` until this front-door
  governance exists; it can later be re-entered through the new OpenSpec-change
  pipeline.

## Capabilities

### New Capabilities

- `craft-frontdoor-governance`: Defines mandatory CRAFT entry criteria,
  provenance, protected paths, Commander reporting, and bootstrap exception
  handling.

### Modified Capabilities

- `genops`: Adds the OpenSpec-change pipeline requirement and front-door
  enforcement behavior to the existing GenOps orchestration contract.

## Impact

- Affected docs: `CRAFT.md`, `docs/ai-kb/19_genops.md`,
  `docs/ai-kb/INDEX.md`, and `genops/README.md`.
- Affected specs: new `craft-frontdoor-governance` spec and a delta to
  `genops`.
- Affected GenOps assets: `genops/commander.yaml`, `genops/agents/commander.md`,
  a new `genops/pipelines/openspec-change.full.yaml`, and any role prompt
  additions needed for OpenSpec authoring.
- Affected tooling: a new front-door provenance checker, likely
  `tools/genops/check_frontdoor.py`, plus tests/fixtures.
- No Minecraft runtime behavior, generated NBT resources, or Java worldgen code
  changes are intended by this governance change.
