# CRAFT Orchestration

CRAFT is the project-facing name for this repository's GenOps orchestration
system:

```text
Commander-driven
Role-scoped
Artifact-first
Feedback-gated
Task-validated
```

It exists so the project owner can direct generator work through natural
language, while Codex keeps the actual work bounded by pipelines, worker roles,
patch scope, validation gates, visual evidence, and human verdicts.

## Core Rule

The owner does not operate CRAFT by typing pipeline commands.

The owner talks to the Commander Agent:

```text
用 GenOps 规划一下宗门远景剪影怎么改，先别动代码。
继续上次 run，把 generator 那个任务做了。
大宅花园不接受，太散；记录 verdict 后继续改。
跑完整回归并准备人工视觉验收。
```

The Commander Agent decides which backend tools to run, which pipeline applies,
which worker owns the next task, and what evidence must be returned.

## Actors

### Owner

The owner provides intent, constraints, aesthetic judgment, and final human
verdicts. The owner should not need to choose low-level task ids or type CLI
commands unless they explicitly want to debug the backend.

### Commander Agent

The Commander is the user-facing controller. It reads the owner's natural
language, pushes back when the goal is vague or aesthetically risky, chooses a
pipeline, runs local GenOps tools, and reports the run id, manifest path, task
state, evidence, and next decision.

Configuration:

```text
genops/commander.yaml
genops/agents/commander.md
tools/genops/commander.py
```

### Manager

The Manager is the backend scheduler. It reads one pipeline, creates the task
graph, writes task contracts and context bundles, prepares prompts, records
patch-guard results, optionally runs gates, and writes the final manifest.

Backend entry point:

```text
tools/genops/run_pipeline.py
```

### Worker Subagents

Worker roles are configured as project-scoped Codex custom subagents under:

```text
.codex/agents/*.toml
```

GenOps role names map to Codex custom agent names in:

```text
genops/subagents.yaml
```

Workers are not spawned automatically. The Commander may spawn them only when
the owner explicitly asks for subagents or parallel agent work, or when the
current task clearly benefits from a delegated worker and the conversation has
authorized that style of work.

## Worker Roles

| GenOps role | Codex custom subagent | Primary ownership |
|---|---|---|
| `manager` | `genops-manager` | Task graph, manifests, gates, evidence |
| `context-cartographer` | `genops-context-cartographer` | Read-only repo/spec/doc mapping |
| `spec-guardian` | `genops-spec-guardian` | OpenSpec and KB impact checks |
| `pipeline-architect` | `genops-pipeline-architect` | Generator contracts and invariants |
| `generator-engineer` | `genops-generator-engineer` | Python generators and preview scripts |
| `java-worldgen-engineer` | `genops-java-worldgen-engineer` | Java runtime/worldgen implementation |
| `validator-engineer` | `genops-validator-engineer` | Validators, quality checks, tests |
| `visual-reviewer` | `genops-visual-reviewer` | Preview artifacts and visual reports |
| `aesthetic-critic` | `genops-aesthetic-critic` | Rubric scoring and defect critique |
| `docs-steward` | `genops-docs-steward` | README, KB, OpenSpec, GenOps docs |
| `regression-steward` | `genops-regression-steward` | Validation, generation, build gates |
| `release-steward` | `genops-release-steward` | Version, changelog, jar-name sync |

Write-capable workers must receive disjoint file ownership when multiple
subagents run in parallel.

## Pipelines

Pipelines are the executable process contracts:

```text
genops/pipelines/building-library.full.yaml
genops/pipelines/compound-library.full.yaml
genops/pipelines/mansion-visual.full.yaml
genops/pipelines/release.full.yaml
genops/pipelines/sect-worldgen.full.yaml
genops/pipelines/visual-acceptance.full.yaml
```

Each pipeline declares:

```text
tasks
dependencies
agent role
allowed files
forbidden files
outputs
gates
human review requirements
```

The Commander chooses the pipeline from the owner's goal. The owner should not
normally choose this by file path.

## Run Lifecycle

```text
Owner intent
  -> Commander interprets and challenges if needed
  -> Commander selects pipeline and run mode
  -> Manager writes task graph and task contracts
  -> Worker task is executed locally or delegated to a Codex custom subagent
  -> PatchGuard checks file scope
  -> Gates run when appropriate
  -> Visual evidence is generated when needed
  -> Aesthetic critique records defects and fix rules
  -> Owner gives human verdict when visual acceptance is required
  -> Manager finalizes run manifest
```

## Evidence Layout

Each run writes deterministic evidence under:

```text
reports/agent_runs/<run_id>/
```

Typical contents:

```text
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
visual/
artifacts/
```

`reports/agent_runs/` is ignored with generated reports by default. It is
evidence, not source.

## Safety Gates

CRAFT relies on layered controls:

```text
Natural-language Commander boundary
Pipeline task DAG
Worker role instructions
allowed_files / forbidden_files
PatchGuard
validator gates
visual evidence
aesthetic rubric
human verdict
final run manifest
```

Important boundaries:

- Generated NBT resources are not hand-edited.
- Release/version files belong to `release-steward`.
- Visual evidence does not equal visual acceptance.
- Minecraft client review is still required for custom `myvillage:` block
  appearance when offline renderers cannot prove it.
- Subagents should not be spawned for every task by default. Use them for
  explicit parallel work or bounded sidecar tasks.

## Typical Conversations

Planning without code edits:

```text
用 CRAFT 规划一下宗门山体自然化，先不要改代码。
```

Implementation after alignment:

```text
按刚才的 run 继续，先做 generator-engineer 那部分。
```

Parallel work:

```text
用 subagents 并行做：一个查 Java worldgen 不变量，一个查 Python sect 预览生成器，一个查 validator 缺口。先不要写代码。
```

Visual rejection:

```text
这版大宅花园不接受，水亭和假山关系太散。记录 verdict，然后继续下一轮。
```

Final acceptance prep:

```text
跑完整回归，生成视觉验收报告，准备我人工看图。
```

## Non-goals

CRAFT is not:

```text
a distributed service
a message queue
a second repository
a background daemon
a replacement for OpenSpec
a replacement for human visual verdicts
a license to let agents edit the whole repo freely
```

OpenSpec remains the capability contract. `docs/ai-kb/` remains factual
technical memory. CRAFT/GenOps is the process layer that decides who works on
what, what evidence is required, and which gates block acceptance.

## Reference Files

Start here when maintaining the system:

```text
CRAFT.md
genops/README.md
genops/commander.yaml
genops/subagents.yaml
genops/pipelines/*.yaml
.codex/agents/*.toml
docs/ai-kb/19_genops.md
openspec/specs/genops/spec.md
tools/genops/run_pipeline.py
tools/genops/commander.py
```

