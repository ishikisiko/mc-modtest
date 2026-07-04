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

Good owner messages:

```text
用 GenOps 规划一下宗门远景剪影怎么改，先别动代码。
继续上次 run，把 generator 那个任务做了。
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

Protected paths include OpenSpec artifacts, GenOps configuration, KB docs,
generator and runtime code, generated structure resources, release metadata,
and user-facing command/build docs. The local guard is:

```bash
python3 tools/genops/check_frontdoor.py --run-id <run_id>
```

The checker is a review guardrail, not cryptographic proof. It compares changed
protected paths with GenOps task evidence so bypasses become visible.

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
- `tools/genops/commander.py` is an optional deterministic routing helper for the
  Commander Agent.
- `tools/genops/run_pipeline.py` is the local manager entry point that the
  Commander Agent may run.
- `tools/genops/check_frontdoor.py` checks protected changed files against
  GenOps run evidence.

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
```

They are not spawned automatically. The Commander Agent uses them only when the
owner explicitly asks for subagents or parallel agent work. For write-heavy
parallel work, the Commander must assign disjoint file ownership to avoid
conflicts.

## Backend planning pass

OpenSpec proposal/design/spec/task authoring uses the dedicated front-door
pipeline:

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/openspec-change.full.yaml \
  --goal "创建或更新某个 OpenSpec change" \
  --run-id 20260705-example-openspec-change
```

When the Commander Agent decides that a planning pass is useful, it runs the
manager with the default `no_op` executor. That materializes the run manifest,
task contracts, prompts, patch guard results, and evidence without modifying
project files or running expensive gates:

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/sect-worldgen.full.yaml \
  --goal "提升宗门山体自然度和远景剪影" \
  --run-id 20260705-sect-worldgen-aesthetic
```

Use `--executor manual` when the next step is for a human or external coding
agent to fill each task directory with a real patch and result. Use
`--run-gates` only when the declared validation/build commands should actually
execute. These are backend controls for the Commander, not instructions the
owner is expected to type.

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
verdict state and include `run_id`, `pipeline`, worker/task ownership,
artifacts, gates, `human_verdict`, and the next decision.

## Acceptance boundary

GenOps can prepare visual reports and route aesthetic defects into rubrics, but
it does not replace human visual acceptance. Pipelines with
`human_review.required: true` end as `human_review_pending` unless an explicit
human verdict is recorded. Minecraft client review is still required for final
appearance of custom `myvillage:` blocks when the existing visual checklist says
offline renderers are insufficient.
