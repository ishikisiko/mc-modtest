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
