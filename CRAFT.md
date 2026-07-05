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
继续上次工作，把已确认的实现方向做完。
大宅花园不接受，太散；记录 verdict 后继续改。
跑完整回归并准备人工视觉验收。
```

The Commander Agent decides which backend tools to run, which pipeline applies,
which worker owns the next task, and what evidence must be returned.

For CRAFT-required work, the owner-facing surface is decision-only by default:
confirm the need, scope/depth, implementation direction, aesthetic/product
verdict, release approval when needed, and the next owner decision. The
Commander owns change names, run ids, pipelines, task ids, worker routing,
validation commands, archive, and evidence bookkeeping.

OpenSpec skills, OpenSpec CLI commands, pipeline YAML paths, run ids, task ids,
validators, prompts, logs, and worker mechanics are backend evidence. The
Commander may summarize those details when the owner asks for audit detail or
when a backend failure is the decision blocker, but they are not the normal
conversation surface.

## Front-door Governance

CRAFT is the required front door for high-impact project work. The Commander
must create or continue a GenOps run before editing protected artifacts when the
owner intent is CRAFT-required:

- explicit CRAFT or GenOps requests
- new OpenSpec change or proposal authoring
- OpenSpec apply or implementation work
- visual or aesthetic structure changes
- multi-worker, subagent, or parallel work
- release, version, jar, build handoff
- acceptance or visual-review handoff

Trivial read-only checks, status lookups, and direct answers can stay outside
CRAFT as long as they do not modify protected files. OpenSpec remains the
capability contract; CRAFT governs the process route into those artifacts.

OpenSpec exploration is therefore a run-internal role task. The
`openspec-change.full` pipeline maps current state, explores scope and conflicts
through `spec-guardian`, then lets downstream tasks write proposal/design/spec
artifacts only after that CRAFT evidence exists.

Protected work must be traceable to a run id, pipeline, task id, worker role,
changed artifacts, gate state, and any required human verdict. The local
front-door checker is `tools/genops/check_frontdoor.py`.

That traceability is mandatory internal evidence, not a list of choices the
owner must manage.

One bootstrap exception exists: the planning artifacts under
`openspec/changes/enforce-craft-frontdoor-governance/**` were allowed to exist
before `openspec-change.full` because this change creates that missing front
door. The exception closes once `openspec-change.full` and the checker land; it
does not authorize implementation code, generated resources, release metadata,
or any later OpenSpec proposal bypass.

The pre-existing `add-visual-reference-structure-pipeline` change was created
before this governance existed. It must be re-entered through
`openspec-change.full` before implementation continues.

## Actors

### Owner

The owner provides intent, constraints, aesthetic judgment, and final human
verdicts. The owner should not need to choose low-level task ids or type CLI
commands unless they explicitly want to debug the backend.

### Commander Agent

The Commander is the user-facing controller. It reads the owner's natural
language, pushes back when the goal is vague or aesthetically risky, chooses a
pipeline, runs local GenOps tools, and reports only the product-level status,
risk, validation result, and decision needed unless audit detail is requested.

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

For CRAFT-required work, role boundaries are still mandatory even when a task is
not delegated. Consequential role work must use the mapped Codex custom subagent
when available and practical. Lightweight read-only checks may be
Commander self-executed, but the task evidence must mark that self-execution
instead of pretending a worker boundary existed.

## Worker Roles

| GenOps role | Codex custom subagent | Primary ownership |
|---|---|---|
| `manager` | `genops-manager` | Task graph, manifests, gates, evidence |
| `context-cartographer` | `genops-context-cartographer` | Read-only repo/spec/doc mapping |
| `spec-guardian` | `genops-spec-guardian` | OpenSpec and KB impact checks |
| `pipeline-architect` | `genops-pipeline-architect` | Generator contracts and invariants |
| `generator-engineer` | `genops-generator-engineer` | Python generators and preview scripts |
| `java-runtime-engineer` | `genops-java-runtime-engineer` | Java item/block/runtime registration |
| `java-worldgen-engineer` | `genops-java-worldgen-engineer` | Java runtime/worldgen implementation |
| `resource-asset-steward` | `genops-resource-asset-steward` | Client/data resources, models, textures, lang, recipes, tags |
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
genops/pipelines/mod-item.full.yaml
genops/pipelines/openspec-change.full.yaml
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

`openspec-change.full` owns OpenSpec proposal/design/spec/task authoring and
review. It is the route for new changes, proposal updates, and apply planning
before protected artifacts are modified.

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

After the owner accepts a direction or asks CRAFT to proceed, the Commander may
continue through authoring, implementation, validation, and handoff without
asking for a second command at every phase. It must stop for unresolved scope
conflict, multiple valid aesthetic/product directions, missing visual verdict,
release/version/changelog approval, destructive generated-resource rewrites,
behavior changes outside accepted scope, failing gates, or missing evidence.

When a stop is only a required human verdict, the Commander asks the owner
directly whether the prepared evidence is OK, rejected, or accepted with
changes. The owner should not have to discover that a verdict is needed.

Archive is a Commander-owned closeout action. Once all artifacts and tasks are
complete, validation and front-door evidence are green, required verdicts are
recorded or not required, and no closeout stop condition remains, the Commander
archives the OpenSpec change and validates the affected baseline specs without
waiting for the owner to issue an archive command.

CRAFT may maintain a rebuildable local SQLite index at `.genops/state.sqlite` to
answer continuity questions such as "continue the previous intent", "what needs
my decision", "which change is closeout-ready", or "who touched this artifact".
That database is an operational cache only. OpenSpec specs, repo files, and
`reports/agent_runs/**` remain the truth; the index can be deleted and rebuilt.

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

The optional local state index lives outside the evidence tree:

```text
.genops/state.sqlite
```

It is ignored by git and rebuilt from run manifests, task results, evidence
files, OpenSpec active/archive state, and mirrored decision artifacts.

For CRAFT-required work, the default Commander summary should include:

```text
goal/status
what changed or will change
validation state
risk or blocker
human decision needed
next decision
```

Run id, pipeline, worker/task ownership, changed artifacts, and gate logs remain
available as audit evidence, but the Commander should not make the owner manage
them in the normal flow.

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
- OpenSpec proposal/spec/task artifacts are written through
  `openspec-change.full`, not as unscoped Commander edits.
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

Visual-reference decomposition (planning, not implementation):

```text
用 CRAFT 拆解 candidate_003 这个徽派参考建筑，先别动 generator。
```

This routes to the visual-reference decomposition workflow described in
`docs/ai-kb/20_visual_reference_structure_pipeline.md`. The owner should not
need to choose the pipeline YAML path or task ids. The decomposition output is
planning evidence: it routes downstream work, it does not implement it, and it
does not replace human visual verdict.

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
