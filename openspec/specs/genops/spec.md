# GenOps

## Purpose

This spec captures the current repository-local GenOps orchestration baseline:
an artifact-first manager for generator work, natural-language Commander
routing, subagent task contracts, patch scope checks, validation gates, visual
evidence, aesthetic rubric outputs, and human verdict handoff.

See also (narrative): [docs/ai-kb/19_genops.md](../../../docs/ai-kb/19_genops.md).
## Requirements
### Requirement: The owner-facing GenOps interface is natural-language Commander conversation
The project owner SHALL be able to use GenOps by speaking naturally to a
Commander Agent. The owner SHALL NOT be required to manually type GenOps CLI
commands for ordinary planning, implementation, visual-review, regression, or
release workflows. CLI commands MAY exist as backend tools that the Commander
Agent runs and summarizes.

#### Scenario: The owner asks for a GenOps planning pass
- **WHEN** the owner says "用 GenOps 规划一下宗门远景剪影怎么改，先别动代码"
- **THEN** the Commander Agent SHALL infer the sect-worldgen pipeline
- **AND** it SHALL run the local manager tools itself when needed
- **AND** it SHALL report the run id, manifest path, task summary, and next decision instead of asking the owner to type the command.

#### Scenario: The owner asks to continue a previous run
- **WHEN** the owner refers to a run id or says to continue the previous GenOps run
- **THEN** the Commander Agent SHALL inspect the existing run manifest and task contracts
- **AND** it SHALL choose the next scoped action from the artifact evidence.

### Requirement: GenOps worker roles are available as project-scoped Codex custom agents
GenOps worker roles SHALL be configured as project-scoped Codex custom agents
under `.codex/agents/`, with role mapping recorded in `genops/subagents.yaml`.
Each custom agent file SHALL define `name`, `description`, and
`developer_instructions`.

#### Scenario: A pipeline task names a GenOps worker role
- **WHEN** a task declares `agent: generator-engineer`
- **THEN** `genops/subagents.yaml` SHALL map that role to `genops-generator-engineer`
- **AND** `.codex/agents/genops-generator-engineer.toml` SHALL exist.

#### Scenario: Parallel implementation is requested
- **WHEN** the owner explicitly asks for subagents or parallel agent work
- **THEN** the Commander Agent MAY spawn the mapped Codex custom agents
- **AND** write-capable workers SHALL be given disjoint file ownership.

### Requirement: GenOps remains repo-local and artifact-first
GenOps SHALL run inside this repository without a distributed service, message
queue, independent backend, or multi-repository scheduler. Each run SHALL write
structured evidence under `reports/agent_runs/<run_id>/`.

#### Scenario: A pipeline planning pass is run
- **WHEN** `python3 tools/genops/run_pipeline.py genops/pipelines/sect-worldgen.full.yaml --run-id example` is run
- **THEN** it SHALL create `reports/agent_runs/example/run_manifest.json`
- **AND** it SHALL create `reports/agent_runs/example/task_graph.json`
- **AND** it SHALL create per-task contract, prompt, result, evidence, and patch-guard files.

### Requirement: Pipelines declare task DAGs, roles, file scope, gates, and human-review needs
Each `genops/pipelines/*.yaml` file SHALL declare a pipeline id and at least one
task. Tasks SHALL declare an agent role and MAY declare dependencies,
`allowed_files`, `forbidden_files`, outputs, inputs, gates, and whether generated
outputs are allowed.

#### Scenario: A task depends on an unknown task
- **WHEN** the GenOps manager loads a pipeline whose task references an unknown dependency
- **THEN** loading SHALL fail before any task artifacts are written.

#### Scenario: A pipeline has human review enabled
- **WHEN** all tasks pass and `human_review.required` is true
- **THEN** the run manifest status SHALL be `human_review_pending` unless an explicit human verdict is supplied.

### Requirement: Patch guard blocks out-of-scope and protected files
The patch guard SHALL reject a task patch when any changed file is outside the
task's `allowed_files`, matches `forbidden_files`, modifies generated outputs
without `generated_outputs_allowed`, or modifies version/release files outside
the `release-steward` role.

#### Scenario: A generator task edits a built NBT resource
- **WHEN** a `generator-engineer` task patch changes `src/main/resources/data/myvillage/structure/example.nbt`
- **AND** the task does not set `generated_outputs_allowed: true`
- **THEN** patch guard SHALL fail with `generated_output_not_allowed`.

#### Scenario: A non-release task edits the mod version
- **WHEN** a task whose agent is not `release-steward` changes `gradle.properties`
- **THEN** patch guard SHALL fail with `version_file_requires_release_steward`.

### Requirement: Gates record command evidence
When the manager runs gates, each gate result SHALL record the command, working
directory, return code, duration, stdout log path, stderr log path, and pass/fail
status.

#### Scenario: Gates are skipped
- **WHEN** a pipeline is run without `--run-gates`
- **THEN** declared gates SHALL be recorded as `skipped`
- **AND** the manager SHALL NOT silently claim those commands passed.

#### Scenario: A gate command fails
- **WHEN** a gate returns a non-zero exit code
- **THEN** the corresponding task SHALL be marked `fail`
- **AND** the run manifest SHALL be marked `failed`.

### Requirement: Visual and aesthetic artifacts do not replace human verdicts
Visual-review tasks SHALL generate or index evidence for inspection. Aesthetic
critic tasks MAY score candidates and emit defect/fix-rule JSON, but they SHALL
NOT be treated as the final visual acceptance gate.

#### Scenario: Visual artifacts exist but no verdict is recorded
- **WHEN** a visual pipeline passes task and gate checks
- **AND** no human verdict is supplied
- **THEN** the final manifest SHALL remain `human_review_pending`.

### Requirement: Release metadata is owned by release-steward
Pipelines that modify release/version metadata SHALL route those changes through
`release-steward` and SHALL continue to follow the version/changelog rule in
`openspec/config.yaml`.

#### Scenario: A release pipeline prepares a version change
- **WHEN** the release pipeline changes `gradle.properties`
- **THEN** the same task scope SHALL include the matching mod metadata, README jar-name references, and `CHANGELOG.md` updates.

### Requirement: GenOps provides an OpenSpec-change pipeline
GenOps SHALL provide an `openspec-change.full` pipeline for creating or updating
OpenSpec proposal, design, spec, and task artifacts. The pipeline SHALL use
scoped worker roles for context mapping, spec impact review, scope/design
planning, artifact writing, and front-door evidence review.

#### Scenario: Owner requests a new OpenSpec proposal
- **WHEN** the owner asks to create a new OpenSpec proposal
- **THEN** the Commander SHALL route the request to the `openspec-change.full`
  pipeline
- **AND** the pipeline SHALL produce task contracts before proposal, design,
  spec, or task files are authored.

#### Scenario: Existing change is continued
- **WHEN** the owner asks to continue or revise an existing OpenSpec change
- **THEN** the Commander SHALL inspect the existing change status through the
  OpenSpec-change pipeline context task
- **AND** it SHALL route the update to scoped worker tasks rather than editing
  artifacts directly.

### Requirement: GenOps commander identifies CRAFT-required work
The GenOps Commander SHALL classify owner requests against the CRAFT-required
intent list before choosing a mode. If a request is CRAFT-required, the
Commander SHALL create or continue a GenOps run before authoring protected
artifacts.

#### Scenario: Visual/aesthetic change is front-door routed
- **WHEN** the owner asks to change a structure's visual form, material,
  courtyard, path, water feature, or aesthetic composition
- **THEN** the Commander SHALL route the request through a GenOps pipeline
- **AND** it SHALL begin with alignment or planning evidence before generator
  edits.

#### Scenario: Mechanical status check remains direct
- **WHEN** the owner asks a direct read-only question that does not modify
  protected files
- **THEN** the Commander MAY answer directly without a GenOps run.

### Requirement: GenOps includes a front-door checker
GenOps SHALL include a local front-door checker that compares protected changed
paths against GenOps run evidence. The checker SHALL support an explicit
`run_id` or equivalent evidence reference and SHALL report missing or mismatched
provenance for protected changes.

#### Scenario: Protected docs change has no evidence
- **WHEN** the checker inspects a diff that changes `docs/ai-kb/**`
- **AND** no matching run evidence is provided
- **THEN** it SHALL report a missing-provenance finding for that path.

#### Scenario: Release metadata change is owned by release-steward
- **WHEN** the checker inspects a diff that changes `gradle.properties` or
  `CHANGELOG.md`
- **AND** the supplied run evidence records a `release-steward` task owning
  those paths
- **THEN** it SHALL accept the release provenance.

### Requirement: GenOps run evidence indexes touched artifacts
For CRAFT-required work, GenOps run evidence SHALL index the artifacts produced
or changed by each worker task. This index SHALL be sufficient for the
front-door checker and Commander summary to connect protected paths to worker
ownership.

#### Scenario: Task result records changed OpenSpec artifacts
- **WHEN** a docs-steward task writes `proposal.md`, `design.md`, `tasks.md`, or
  a delta spec under `openspec/changes/**`
- **THEN** the task result or evidence SHALL list those artifact paths
- **AND** the final manifest or summary SHALL expose the changed-artifact list.

### Requirement: GenOps summaries distinguish task status from human verdict
GenOps summaries for CRAFT-required work SHALL distinguish task pass/fail status
from human verdict state. A run whose tasks pass but whose human review is
pending SHALL NOT be summarized as accepted.

#### Scenario: Tasks pass but verdict is pending
- **WHEN** a CRAFT-required run finishes all tasks
- **AND** human review is required but not recorded
- **THEN** the run summary SHALL report the task status separately from the
  pending verdict
- **AND** the Commander SHALL ask for the next decision rather than claiming
  acceptance.

### Requirement: CRAFT Commander is the visible control plane for governed work

For CRAFT-required work, the GenOps Commander SHALL present CRAFT run state as
the owner-facing workflow. Backend OpenSpec skills, OpenSpec CLI commands,
spec-file discovery steps, validator commands, and pipeline YAML paths SHALL be
treated as backend evidence unless the owner explicitly asks for audit detail.

#### Scenario: OpenSpec exploration is not announced as the front door

- **WHEN** the owner asks to explore, author, or continue an OpenSpec-backed
  change through CRAFT
- **THEN** the Commander SHALL create or continue a CRAFT run
- **AND** it SHALL report the run id, phase, pipeline, role outcomes, risk
  stops, and next decision
- **AND** it SHALL NOT present an OpenSpec skill or OpenSpec CLI command as the
  owner-facing entry point.

#### Scenario: Owner asks for audit detail

- **WHEN** the owner asks what backend checks or commands were used
- **THEN** the Commander MAY summarize OpenSpec commands, spec paths, prompts,
  and logs from the run evidence.

### Requirement: OpenSpec exploration is a run-internal role task

The `openspec-change.full` pipeline SHALL include a role-scoped exploration
task that inspects OpenSpec status, related specs, existing changes, conflicts,
and scope boundaries before artifact authoring. The task output SHALL be CRAFT
evidence, not a user-facing workflow name.

#### Scenario: OpenSpec-change run explores scope internally

- **WHEN** `openspec-change.full` is run for a new or continued change
- **THEN** it SHALL include an `explore-scope` task after current-state mapping
- **AND** that task SHALL be owned by `spec-guardian` or an equivalent
  governance role
- **AND** later artifact-writing tasks SHALL depend on that exploration result.

### Requirement: Consequential role work prefers real subagent execution

For CRAFT-required work, consequential role tasks SHALL be executed by the
mapped Codex custom subagent when available and practical. Commander
self-execution MAY be used for lightweight read-only checks or when the subagent
bridge is unavailable, but task evidence SHALL record that the role task was
`commander_self_executed`.

Consequential role work includes tasks that affect OpenSpec artifacts, design
judgment, generator or runtime code, generated resources, validation gates,
release metadata, or human verdict handoff.

#### Scenario: Spec-impact task uses a role boundary

- **WHEN** a CRAFT run needs to decide whether to create, revise, split, or
  reject an OpenSpec change scope
- **THEN** the task SHALL be represented as `spec-guardian` work in run
  evidence
- **AND** it SHALL use the mapped subagent when available and practical
- **AND** if the Commander self-executes it, the evidence SHALL mark
  `commander_self_executed`.

### Requirement: Accepted CRAFT directions auto-progress until a stop condition

After the owner accepts a direction or asks CRAFT to proceed, the Commander SHALL continue through explore, author, execute, validate, and handoff phases without requiring a new owner command for every phase unless a stop condition is reached. The Commander SHALL pause before continuing when a stop condition is reached.

Stop conditions include unresolved scope conflict, multiple valid
aesthetic/product directions requiring owner judgment, required visual verdict,
release/version/changelog approval, destructive or broad generated-resource
rewrite, protected behavior boundary change outside accepted scope, failing
gate, or missing evidence.

#### Scenario: Accepted scope continues without another command

- **WHEN** the owner approves a CRAFT design direction
- **AND** no stop condition is present
- **THEN** the Commander MAY continue to author or implementation tasks
- **AND** it SHALL summarize the automatic phase transition in the next CRAFT
  status report.

#### Scenario: Visual verdict stops progression

- **WHEN** a visual pipeline has produced task evidence and preview artifacts
- **AND** the owner has not supplied the required human verdict
- **THEN** the Commander SHALL stop before claiming acceptance or release
- **AND** it SHALL ask only for the verdict or decision needed.

### Requirement: Commander proactively requests required human verdicts

When a CRAFT pipeline requires a human verdict, the GenOps Commander SHALL ask the owner directly whether the prepared evidence is accepted, rejected, or accepted with changes. The Commander SHALL NOT rely on the owner to infer the need for a verdict from backend evidence.

#### Scenario: Visual evidence awaits owner judgment

- **WHEN** a visual-review or acceptance pipeline reaches `human_review_pending`
- **THEN** the Commander SHALL ask the owner whether the evidence is OK,
  rejected, or accepted with changes
- **AND** it SHALL record the verdict before continuing to acceptance, release,
  or archive.

### Requirement: Commander auto-archives eligible completed changes

When an OpenSpec-backed CRAFT run reaches closeout, the GenOps Commander SHALL archive the change without requiring an owner-issued archive command if all artifacts and tasks are complete, strict change validation passes, front-door evidence matches protected paths, required human verdicts are recorded or not required, and no closeout stop condition remains.

#### Scenario: Green change archives during closeout

- **WHEN** a CRAFT-required OpenSpec change has complete artifacts and tasks
- **AND** validation and front-door evidence pass
- **AND** no required human verdict is pending
- **AND** no closeout stop condition remains
- **THEN** the Commander SHALL archive the change
- **AND** it SHALL validate the affected baseline specs before reporting the
  closeout summary.

#### Scenario: Pending verdict blocks archive

- **WHEN** a completed change still requires an owner verdict
- **THEN** the Commander SHALL ask for that verdict
- **AND** it SHALL NOT archive the change until the verdict is recorded or the
  owner explicitly changes the closeout scope.

### Requirement: GenOps routes visual-reference decomposition through CRAFT

GenOps SHALL provide a Commander-routed pipeline for visual-reference structure
decomposition. When the owner asks in natural language to use CRAFT/GenOps to
拆解, analyze, or route a visual building reference, the Commander Agent SHALL
infer the reference-decomposition pipeline, run local manager tooling when
useful, and report the run id, manifest path, breakdown artifact paths, and next
decision.

#### Scenario: Owner asks CRAFT to decompose a reference building

- **WHEN** the owner says "用 CRAFT 规划拆解示例建筑"
- **THEN** the Commander Agent SHALL select the visual-reference decomposition
  pipeline
- **AND** it SHALL not ask the owner to manually choose a GenOps pipeline YAML
  path before planning.

### Requirement: Reference-decomposition pipeline is artifact-first and scoped

The reference-decomposition pipeline SHALL materialize task contracts and
evidence under `reports/agent_runs/<run_id>/` like other GenOps pipelines. Its
write-capable tasks SHALL be scoped to decomposition artifacts, docs, CRAFT
configuration, and OpenSpec/KB updates unless a later implementation change
explicitly authorizes generator or NBT edits.

#### Scenario: Planning pass writes evidence but no generator patch

- **WHEN** the reference-decomposition pipeline is run in planning mode
- **THEN** it SHALL write a run manifest and task graph under
  `reports/agent_runs/<run_id>/`
- **AND** it SHALL NOT modify `tools/buildgen/**`,
  `src/main/resources/data/myvillage/structure/*.nbt`, Java runtime code, or
  version metadata.

### Requirement: Reference-decomposition runs expose human verdict state

Reference-decomposition runs SHALL expose whether the breakdown is awaiting
owner review, accepted for downstream proposal work, rejected, or accepted with
changes. This verdict state SHALL be recorded separately from task pass/fail
status.

#### Scenario: Tasks pass but verdict is pending

- **WHEN** all reference-decomposition tasks complete without gate failures
- **AND** the owner has not supplied a verdict
- **THEN** the run SHALL report a pending human verdict
- **AND** the Commander summary SHALL describe the next decision needed rather
  than treating the decomposition as accepted.

### Requirement: GenOps provides a rebuildable SQLite operational index

GenOps SHALL provide a local SQLite operational index for CRAFT run continuity that can be rebuilt from run artifacts and OpenSpec state. The SQLite database SHALL NOT be the source of truth for project behavior, run audit evidence, or OpenSpec capability contracts.

#### Scenario: State store is initialized locally

- **WHEN** the Commander initializes the GenOps state store
- **THEN** it SHALL create or open a SQLite database under a gitignored local
  state path
- **AND** it SHALL use the database as an operational index rather than a
  replacement for JSON run artifacts.

#### Scenario: State store is rebuilt

- **WHEN** the Commander rebuilds the state store
- **THEN** it SHALL scan deterministic run artifacts and OpenSpec change/archive
  state
- **AND** it SHALL repopulate run, task, artifact, gate, decision, and closeout
  index records.

### Requirement: GenOps state queries support Commander continuity

GenOps SHALL expose local state-store queries for current intent, pending decisions, closeout-ready changes, and artifact ownership. These queries SHALL be usable by the Commander to continue an intent without asking the owner for low-level task ids, run ids, or archive commands.

#### Scenario: Owner asks what needs a decision

- **WHEN** the Commander queries pending decisions
- **THEN** the state store SHALL return runs or intents that require owner
  verdicts or decisions.

#### Scenario: Owner asks who touched an artifact

- **WHEN** the Commander queries an artifact path
- **THEN** the state store SHALL return indexed run, task, role, and artifact
  ownership records when available.

### Requirement: Recorded decisions are recoverable from run evidence

When the Commander records an owner decision through the state store and a run id is available, GenOps SHALL mirror that decision into the run evidence tree so the decision can be recovered by a future rebuild.

#### Scenario: Decision is recorded for a run

- **WHEN** the Commander records an `accept`, `reject`, `accept_with_changes`,
  `reopen_discussion`, or `pause` decision with a run id
- **THEN** the state store SHALL write the decision to SQLite
- **AND** it SHALL write a JSON decision artifact under that run's evidence
  directory.

### Requirement: Closeout readiness remains gate-aware

The GenOps state store SHALL NOT mark a completed active OpenSpec change as archive-ready solely because all tasks are checked. Closeout readiness SHALL require CRAFT closeout evidence or equivalent gate/verdict evidence.

#### Scenario: Completed active change lacks closeout evidence

- **WHEN** an active OpenSpec change has all tasks complete
- **AND** the state store has no matching CRAFT closeout evidence
- **THEN** the closeout index SHALL record a blocker rather than marking the
  change archive-ready.
