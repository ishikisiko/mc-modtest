## ADDED Requirements

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
