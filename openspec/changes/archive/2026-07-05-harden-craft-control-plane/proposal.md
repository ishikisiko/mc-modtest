## Why

CRAFT is meant to be the higher control plane for this repository, but the
current interaction can still leak backend OpenSpec skills, CLI commands, and
task mechanics into the owner-facing conversation. That makes CRAFT feel like a
thin wrapper around OpenSpec instead of a governed Commander workflow.

The owner should see intent routing, run state, role outcomes, risk stops, and
next decisions. OpenSpec exploration and CLI details should remain backend
contract work inside a CRAFT run unless the owner asks for audit detail.

## What Changes

- Make CRAFT Commander the only user-facing entry for CRAFT-required work.
- Add an explicit visibility policy: OpenSpec skills/CLI are backend details,
  not the front door.
- Add an `explore-scope` role task to `openspec-change.full` so OpenSpec
  exploration happens inside the run.
- Define when role tasks should use real subagents versus Commander
  self-execution.
- Define auto-progression rules so accepted directions can move from explore
  to author/execute/validate without a second command, while high-risk stops
  still require owner approval.
- Require the Commander to ask for required human verdicts directly instead of
  waiting for the owner to infer the need from evidence.
- Make OpenSpec archive a Commander-owned closeout action when validation,
  evidence, task completion, and verdict gates are green.

## Capabilities

### Modified Capabilities

- `genops`: Tightens the Commander visibility boundary, subagent/role-task
  expectations, OpenSpec-change exploration route, and automatic phase
  progression rules.

## Impact

- Affected docs: `AGENTS.md`, `CRAFT.md`, `docs/ai-kb/19_genops.md`.
- Affected OpenSpec specs: delta to `genops`.
- Affected GenOps files: `genops/commander.yaml`,
  `genops/pipelines/openspec-change.full.yaml`,
  `tools/genops/commander.py`, `tools/genops/check_frontdoor.py`.
- No Java runtime behavior, generated resources, release metadata, or jar
  version changes.
