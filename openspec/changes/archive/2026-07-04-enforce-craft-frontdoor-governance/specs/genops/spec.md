## ADDED Requirements

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

The GenOps Commander SHALL classify owner requests against the
CRAFT-required intent list before choosing a mode. If a request is
CRAFT-required, the Commander SHALL create or continue a GenOps run before
authoring protected artifacts.

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
