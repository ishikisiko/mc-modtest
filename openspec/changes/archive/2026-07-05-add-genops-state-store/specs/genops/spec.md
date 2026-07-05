## ADDED Requirements

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
