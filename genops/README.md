# MyVillage GenOps

GenOps is the repo-local, artifact-first orchestration layer for generator work.
It does not add a service, queue, or second repository. A single manager reads a
pipeline contract, prepares atomic role tasks, writes prompts/evidence under
`reports/agent_runs/<run_id>/`, checks patch scope, optionally runs gates, and
records the final run manifest.

## Owner Interface

The normal interface is a natural-language conversation with the Commander
Agent. The owner should state intent and decisions; the Commander chooses and
runs the backend tools.

```text
用 GenOps 规划一下宗门远景剪影怎么改，先别动代码。
继续上次 run，把 patch-python-preview 做了。
跑完整回归并准备人工视觉验收。
```

CRAFT-required work must enter through this interface before protected files are
edited. CRAFT-required work includes explicit CRAFT/GenOps requests, new
OpenSpec changes, OpenSpec apply/implementation, visual or aesthetic structure
changes, subagent/parallel work, release/version/build handoff, and
acceptance/visual-review handoff. Direct read-only checks remain exempt.

## Backend Run

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/sect-worldgen.full.yaml \
  --goal "提升宗门山体自然度和远景剪影" \
  --run-id 20260705-sect-worldgen-aesthetic
```

The default executor is `no_op`: it verifies that the pipeline can be planned and
materialized without changing files. Use `--executor manual` to generate
per-task prompts for an external coding agent or human patch workflow. Add
`--run-gates` only when the Commander wants the declared validation/build
commands to run. The owner should not need to type these commands in normal use.

OpenSpec proposal/design/spec/task work uses its own front-door pipeline:

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/openspec-change.full.yaml \
  --goal "创建或更新某个 OpenSpec change" \
  --run-id 20260705-openspec-change-example
```

The pre-existing `add-visual-reference-structure-pipeline` change was authored
before this route existed. It must be re-entered through
`openspec-change.full` before implementation continues.

## Visual-Reference Decomposition

Visual references under `research/source_structures/` are decomposed into a
Reference Breakdown Contract before any generator, NBT, Java, or version edit.
The contract classifies each observed cue into `direct_component`,
`atomic_component`, `generative_grammar`, and `calibration_only` buckets with
explicit downstream routes, and records a pending human verdict. See
`docs/ai-kb/20_visual_reference_structure_pipeline.md` and the
`visual-reference-structure-pipeline` capability for the workflow contract.

```bash
python3 tools/genops/run_pipeline.py genops/pipelines/reference-decomposition.full.yaml \
  --goal "用 CRAFT 拆解 candidate_003 徽派参考建筑" \
  --run-id 20260705-reference-decomposition-candidate-003
```

The pipeline's `human_review.required: true` reflects that decomposition is
planning evidence, not visual acceptance — a run ends as `human_review_pending`
until the owner records a verdict. The decomposition output routes downstream
work; it does not implement it.

## Codex Custom Subagents

Project-scoped Codex custom agents live under `.codex/agents/`. GenOps maps
pipeline role names to those custom agent names in `genops/subagents.yaml`.

Examples:

- `generator-engineer` -> `genops-generator-engineer`
- `java-worldgen-engineer` -> `genops-java-worldgen-engineer`
- `validator-engineer` -> `genops-validator-engineer`
- `visual-reviewer` -> `genops-visual-reviewer`

The Commander should spawn them only when the owner explicitly asks for
subagents or parallel agent work, and implementation workers must have disjoint
write sets.

## Contract

- `genops/pipelines/*.yaml` declares the task DAG, role, file scope, and gates.
- `genops/commander.yaml` defines natural-language intent routing.
- `genops/subagents.yaml` maps GenOps roles to project Codex custom agents.
- `genops/agents/commander.md` defines the user-facing Commander role.
- `genops/agents/*.md` defines role boundaries for prompt generation.
- `genops/schemas/*.json` documents the artifact contracts.
- `genops/rubrics/`, `genops/defects/`, `genops/style_bibles/`, and
  `genops/golden/` capture visual/aesthetic control data.
- `tools/genops/check_frontdoor.py` checks protected changed files against a
  GenOps run manifest and per-task artifact evidence.
- `reports/agent_runs/<run_id>/` is deterministic evidence and remains ignored
  with the rest of `reports/`.

GenOps complements OpenSpec: OpenSpec defines project capability behavior;
GenOps defines who may change what, which gates block the change, and where
review evidence lands.

For CRAFT-required handoff, Commander summaries should report `run_id`,
`pipeline`, worker/task ownership, changed artifacts, gates, human verdict
state, and the next decision. Task success alone is not visual or human
acceptance.
