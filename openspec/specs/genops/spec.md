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
