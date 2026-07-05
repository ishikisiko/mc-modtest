## 1. Control Plane Contract

- [x] 1.1 Add OpenSpec delta requirements for Commander visibility, role-task
  exploration, subagent/self-execution boundaries, and auto-progression.
- [x] 1.2 Update `AGENTS.md` with the short front-door rule so future agents do
  not announce OpenSpec skills/CLI as the user-facing workflow.

## 2. CRAFT / GenOps Docs

- [x] 2.1 Update `CRAFT.md` with the visibility boundary, subagent execution
  policy, and auto-progression stop conditions.
- [x] 2.2 Update `docs/ai-kb/19_genops.md` with the same narrative contract and
  see-also continuity.

## 3. Commander and Pipeline Configuration

- [x] 3.1 Add visibility, subagent execution, and auto-progression policy to
  `genops/commander.yaml`.
- [x] 3.2 Add an `explore-scope` role task to
  `genops/pipelines/openspec-change.full.yaml`.
- [x] 3.3 Update `tools/genops/commander.py` so route recommendations expose
  the new policy fields for Commander use.
- [x] 3.4 Protect `tools/genops/**` in the front-door checker so Commander and
  manager helper changes are governed like `genops/**`.

## 4. Verification

- [x] 4.1 Start this work through the CRAFT front door with run
  `20260705-craft-control-plane`.
- [x] 4.2 Run strict OpenSpec validation for this change.
- [x] 4.3 Run the GenOps front-door checker against the run evidence.

## 5. Closeout Automation

- [x] 5.1 Add proactive human-verdict prompting policy to Commander config,
  CRAFT docs, KB docs, and GenOps delta specs.
- [x] 5.2 Add auto-archive closeout policy to Commander config, CRAFT docs, KB
  docs, and GenOps delta specs.
- [x] 5.3 Add `closeout-readiness` to the OpenSpec-change pipeline so verdict
  and archive blockers are recorded before closeout.
- [x] 5.4 Allow the front-door checker to recognize regression-steward
  closeout ownership for archived OpenSpec paths.
- [x] 5.5 Validate this change and archive it automatically when green.
