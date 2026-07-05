## Context

The front-door governance change made CRAFT mandatory for high-impact work, but
there is still a control-surface leak: the agent can announce an OpenSpec skill
or CLI step directly to the owner before a CRAFT run has framed the work. That
is technically traceable after the fact, but it weakens the intended abstraction:
OpenSpec is the contract backend; CRAFT is the owner-facing control plane.

The missing piece is not "always spawn a worker for every tiny read." It is a
clear boundary between owner-visible Commander state and backend role/tool
mechanics, plus rules for when role isolation must become real subagent
execution.

## Decisions

### D1: CRAFT is the only owner-facing control plane for governed work

For CRAFT-required work, the Commander must start by creating or continuing a
run and reporting CRAFT state: run id, phase, pipeline, role outcomes, gates,
verdict state, risks, and next decision. It must not present OpenSpec skills,
OpenSpec CLI commands, or file-by-file spec discovery as the owner's workflow.

Backend details remain available in run evidence and can be shown when the
owner asks for audit detail.

### D2: OpenSpec exploration becomes a run-internal role task

`openspec-change.full` gets an explicit `explore-scope` task after current-state
mapping. This task is owned by `spec-guardian`; it can inspect OpenSpec status,
existing specs, and conflicts, but its output is a CRAFT artifact such as
`artifacts/openspec_exploration.md`, not a front-door prompt to the owner.

### D3: Subagents are required for consequential role work, not every read

Consequential CRAFT role work should use the mapped Codex custom subagent when
available, especially if it affects OpenSpec artifacts, design judgment, code,
generated resources, validation, or release state. Commander self-execution is
allowed only for lightweight read-only checks or when no subagent bridge is
available; in that case the task evidence must say it was
`commander_self_executed`.

This avoids fake isolation while still avoiding ritual subagent spawning for a
single status lookup.

### D4: Accepted directions auto-progress until a stop condition

Once the owner has accepted a direction or asked CRAFT to proceed, the Commander
may move from explore to author, execute, validate, and handoff without asking
for a second command at every phase.

The Commander must pause only on stop conditions:

- unresolved scope conflict;
- multiple valid aesthetic/product directions requiring human judgment;
- visual verdict required;
- release/version/changelog approval;
- destructive or large generated-resource rewrite;
- protected behavior boundary change not covered by the accepted scope;
- failing gate or missing evidence.

### D5: Verdict prompts are proactive

When a pipeline requires human review, the Commander should not merely report
`human_review_pending` and wait. It must ask the owner directly whether the
prepared evidence is OK, rejected, or accepted with changes, then record the
verdict before continuing to acceptance, release, or archive.

### D6: Archive is automatic closeout when green

Archiving a completed OpenSpec change is part of CRAFT closeout. If artifacts
and tasks are complete, strict change validation passes, front-door evidence
matches protected paths, required verdicts are recorded or not required, and no
closeout stop condition remains, the Commander archives the change and validates
the affected baseline specs without waiting for the owner to issue an archive
command.

The Commander pauses archive only for closeout stop conditions: pending required
verdict, release/version/changelog approval, failing gate, missing evidence,
implementation not landed, dirty unrelated protected paths, or spec baseline
conflict.

### D7: User summaries are compact; audit remains complete

The owner-facing summary should be short and structured. It should not list raw
backend commands by default. The run evidence still records prompts, task
contracts, changed artifacts, gates, and tool output paths for audit.

## Risks / Trade-offs

- **Risk: Hiding too much.** Mitigation: audit detail remains available on
  request and in run evidence.
- **Risk: Fake subagent boundaries.** Mitigation: consequential role work must
  prefer real subagents; self-execution is explicit evidence, not invisible.
- **Risk: Auto-progression overreaches.** Mitigation: stop conditions are
  normative and tied to visual, release, destructive, scope, and gate risk.
- **Risk: More governance overhead for small checks.** Mitigation: trivial
  read-only checks remain outside CRAFT.

## Migration Plan

1. Update Commander config with visibility, subagent, and auto-progression
   policy.
2. Add `explore-scope` to `openspec-change.full`.
3. Update narrative docs and prompt rules.
4. Add proactive verdict and auto-archive closeout policy.
5. Update the GenOps spec with the new owner-visible boundary and stop
   conditions.
6. Validate the change, run the front-door checker, then archive automatically
   when green.
