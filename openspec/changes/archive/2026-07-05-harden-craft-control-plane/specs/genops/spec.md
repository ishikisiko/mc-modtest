## ADDED Requirements

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
