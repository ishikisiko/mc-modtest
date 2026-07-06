# GenOps Orchestration

GenOps is the repository-local orchestration layer for generator development. It
does not introduce a server, queue, distributed worker, or separate repository.
It turns a goal into artifact-first task contracts and records evidence under
`reports/agent_runs/<run_id>/`.

See also: [genops spec](../../openspec/specs/genops/spec.md), [validation checklist](09_validation_checklist.md).

## User-facing model

The project owner should use GenOps by talking to the Commander Agent in natural
language, not by manually typing GenOps CLI commands. The Commander Agent chooses
the pipeline, run mode, task scope, and validation path, then runs local tools
itself and reports the evidence.

For CRAFT-required work, the owner-facing workflow is Commander conversation:
`goal_status`, `scope_or_direction`, `validation_state`, `risk_or_blocker`,
`human_decision_needed`, and `next_decision`. Run ids, pipeline names, task ids,
worker ownership, artifacts, gates, raw logs, manifest paths, OpenSpec skill
names, OpenSpec CLI commands, pipeline YAML paths, validator commands, and
prompts are backend evidence. The Commander exposes them only when the owner
asks for audit detail or when a backend failure is the decision blocker.

The default owner interface is narrower than the audit record: confirm the
need, choose scope/depth or direction when there are real alternatives, give
aesthetic/product verdicts, and approve release-sensitive choices when needed.
The Commander owns change names, run ids, pipelines, task ids, worker routing,
checks, archive, and evidence bookkeeping.

Good owner messages:

```text
用 GenOps 规划一下宗门远景剪影怎么改，先别动代码。
继续上次工作，把已确认的实现方向做完。
大宅花园不接受，太散；记录 verdict 后继续改。
跑完整回归并准备人工视觉验收。
```

The CLI remains useful as a reproducible backend and for CI-like debugging, but
it is not the primary user interface.

## Front-door governance

GenOps is mandatory for CRAFT-required work. The Commander must create or
continue a run before modifying protected artifacts when the owner asks for:

- explicit CRAFT/GenOps work;
- new OpenSpec proposal/change authoring;
- OpenSpec apply or implementation work;
- visual or aesthetic structure changes;
- multi-worker, subagent, or parallel work;
- release, version, jar, build handoff;
- acceptance or visual-review handoff.

Direct read-only checks remain exempt. For example, checking `git status`,
listing active OpenSpec changes, or answering a narrow factual question does not
need a run unless the task proceeds to protected edits.

OpenSpec exploration is not a separate owner-facing entry point. New or
continued OpenSpec work enters through `openspec-change.full`, where the
`spec-guardian` role explores scope, related specs, active changes, conflicts,
and stop conditions as run evidence before artifact writing proceeds.

Protected paths include OpenSpec artifacts, GenOps configuration, KB docs,
generator code, Java runtime code, client/data resources, generated structure
resources, release metadata, and user-facing command/build docs. The local
guard is:

```bash
python3 tools/genops/check_frontdoor.py --run-id <run_id>
```

The checker is a review guardrail, not cryptographic proof. It compares changed
protected paths with GenOps task evidence so bypasses become visible.
`run_pipeline.py` embeds the checker result into the final manifest for
run-owned protected artifacts and blocks a green final status if provenance,
role ownership, or task/patch/git changed-file consistency fails.

The only bootstrap exception is the planning artifact set for
`enforce-craft-frontdoor-governance`. The older
`add-visual-reference-structure-pipeline` proposal must be re-entered through
`openspec-change.full` before implementation continues.

## Directory roles

- `genops/pipelines/*.yaml` declares manager-readable task DAGs, file scopes,
  protected invariants, gates, and human-review requirements.
- `genops/commander.yaml` and `genops/agents/commander.md` define the natural
  language routing contract for the Commander Agent.
- `genops/subagents.yaml` maps GenOps worker roles to project-scoped Codex
  custom agents.
- `genops/agents/*.md` defines role boundaries used when task prompts are
  generated.
- `genops/schemas/*.json` documents the pipeline, task, agent output, run
  manifest, visual review, defect, generator contract, and human verdict
  artifacts.
- `genops/rubrics/*.yaml`, `genops/defects/defect_dictionary.yaml`,
  `genops/style_bibles/*.yaml`, and `genops/golden/*.yaml` hold aesthetic and
  regression-control data.
- `tools/genops/commander.py` is the deterministic Commander state machine:
  it classifies goals, starts/indexes runs, continues current intent, reports
  status/summary/next decision, records verdicts, and closes out.
- `tools/genops/run_pipeline.py` is the local manager entry point that the
  Commander Agent may run; final manifests include the front-door check result.
- `tools/genops/check_frontdoor.py` checks protected changed files against
  GenOps run evidence and uses granular path categories rather than a broad
  `src/main/**` bucket.
- `tools/genops/validate_pipelines.py` compiles pipeline governance rules:
  roles, write scopes, human-review artifacts, required gates, release
  ownership, OpenSpec boundaries, visual review, and task evidence outputs.

## Project Codex subagents

The worker roles are configured as Codex custom agents under `.codex/agents/`.
These files are project-scoped config, so Codex loads them only when the project
is trusted. Examples:

```text
genops-generator-engineer
genops-java-worldgen-engineer
genops-validator-engineer
genops-visual-reviewer
genops-aesthetic-critic
genops-regression-steward
genops-java-runtime-engineer
genops-resource-asset-steward
```

They are not spawned automatically. The Commander Agent uses them only when the
owner explicitly asks for subagents or parallel agent work. For write-heavy
parallel work, the Commander must assign disjoint file ownership to avoid
conflicts.

For consequential CRAFT role work, the Commander must use the mapped custom
subagent when available and practical. Consequential work includes OpenSpec
artifact impact, design judgment, code/resource edits, validation, release
metadata, and human verdict handoff. Lightweight read-only checks may be
Commander self-executed, but task evidence should mark that fact rather than
implying a worker was spawned.

## Backend planning pass

OpenSpec proposal/design/spec/task authoring uses the dedicated front-door
pipeline:

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/openspec-change.full.yaml \
  --goal "创建或更新某个 OpenSpec change" \
  --run-id 20260705-example-openspec-change
```

Mod item work has its own skill-backed route:

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/mod-item.full.yaml \
  --goal "创建或修订一个 myvillage mod 物品" \
  --run-id 20260705-mod-item-example
```

The owner should still speak naturally; the command is backend evidence. The
route starts from `.codex/skills/mod-item-creation/SKILL.md` and an Item
Contract, then splits Java registration, resource assets, validation, docs, and
regression across atomic roles.

When the Commander Agent decides that a planning pass is useful, it runs the
manager with the default `no_op` executor. That materializes the run manifest,
task contracts, prompts, patch guard results, and evidence without modifying
project files or running expensive gates. No-op tasks are recorded as
`plan_materialized`, and the run manifest reports `planning_ready`; this is not
task completion.

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/sect-worldgen.full.yaml \
  --goal "提升宗门山体自然度和远景剪影" \
  --run-id 20260705-sect-worldgen-aesthetic
```

Use `--executor manual` when the next step is for a human or external coding
agent to fill each task directory with a real patch and result; those tasks are
`manual_ready`. Real or delegated execution is the only route that may report
task `pass`/`fail`/`blocked`. Use `--run-gates` only when the declared
validation/build commands should actually execute. These are backend controls
for the Commander, not instructions the owner is expected to type.

After the owner accepts a direction or asks CRAFT to proceed, the Commander may
auto-progress through authoring, implementation, validation, and handoff until a
stop condition appears. Stop conditions include unresolved scope conflict,
multiple valid aesthetic/product directions, required visual verdict,
release/version/changelog approval, destructive generated-resource rewrites,
behavior changes outside accepted scope, failing gates, or missing evidence.

If the only blocker is a required human verdict, the Commander asks the owner
whether the prepared evidence is OK, rejected, or accepted with changes. The
owner is not expected to infer that a verdict is needed from backend evidence.

Archive is part of CRAFT closeout, not an owner-initiated second command. When a
change has complete artifacts and tasks, passing validation, matching
front-door evidence, no pending required verdict, and no closeout stop
condition, the Commander archives it and validates the affected baseline specs
before reporting the closeout summary.

## Local state index

GenOps may maintain a rebuildable SQLite operational index at
`.genops/state.sqlite`. It is local cache, not source of truth. The index helps
the Commander answer continuity questions quickly: current intent, pending
decisions, closeout-ready changes, failed tasks, gate history, and artifact
ownership.

Backend commands:

```bash
python3 tools/genops/commander.py classify "用 CRAFT 规划某个目标"
python3 tools/genops/commander.py start-run "用 CRAFT 规划某个目标"
python3 tools/genops/commander.py continue-current
python3 tools/genops/commander.py status
python3 tools/genops/commander.py next-decision
python3 tools/genops/commander.py record-verdict accept --summary "证据通过"
python3 tools/genops/commander.py closeout
python3 tools/genops/commander.py summary
python3 tools/genops/state_store.py init
python3 tools/genops/state_store.py rebuild
python3 tools/genops/state_store.py current
python3 tools/genops/state_store.py pending-decisions
python3 tools/genops/state_store.py closeout-ready
python3 tools/genops/state_store.py artifact-owner genops/commander.yaml
python3 tools/genops/validate_pipelines.py
```

The database can be deleted and rebuilt from `reports/agent_runs/**`, OpenSpec
active/archive state, and decision artifacts mirrored under run evidence.
The Commander state machine uses these same tables; stop conditions such as
missing evidence, failing gates, reported blockers, pending human verdict,
release approval, and direction-required pauses are evaluated in code before
state advances. Verdict values are normalized to `pending`, `accept`, `reject`,
`accept_with_changes`, `not_required`, `pause`, and `reopen_discussion`; a
recorded decision updates indexed run state so `pending-decisions` does not
keep reporting a resolved verdict.
`closeout-ready` is set only when a completed active OpenSpec change has
matching closeout evidence, front-door pass, OK verdict (`accept`,
`accept_with_changes`, or `not_required`), and validation pass.

## Evidence shape

A run writes:

```text
reports/agent_runs/<run_id>/
  run_manifest.json
  task_graph.json
  final_summary.md
  context/
  tasks/<task_id>/
    task_contract.json
    context_bundle.md
    prompt.md
    patch.diff
    patch_guard.json
    task_result.json
    evidence.json
    logs/
```

Files under `reports/agent_runs/` are deterministic run evidence and are ignored
with the rest of `reports/` by default.

For CRAFT-required work, the final summary must separate task status from human
verdict state. The default owner-facing summary should include `goal_status`,
`scope_or_direction`, `validation_state`, `risk_or_blocker`,
`human_decision_needed`, and `next_decision`. Run id, pipeline, task id, worker
ownership, artifacts, gates, raw logs, manifest path, and raw verdict records
remain available as audit evidence when requested or when a backend failure
blocks the decision.

## Acceptance boundary

GenOps can prepare visual reports and write deterministic aesthetic review JSON
from rubric dimensions, defect dictionaries, and the visual acceptance report,
but it does not replace human visual acceptance. Each `aesthetic-review` task
emits `candidate`, numeric `scores`, `blocking_defects`, `fix_rules`, and
`human_verdict_state: pending`. Pipelines with
`human_review.required: true` end as `human_review_pending` unless an explicit
human verdict is recorded. Minecraft client review is still required for final
appearance of custom `myvillage:` blocks when the existing visual checklist says
offline renderers are insufficient.
