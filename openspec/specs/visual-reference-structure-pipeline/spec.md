# visual-reference-structure-pipeline Specification

## Purpose
TBD - created by archiving change add-visual-reference-structure-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Visual references are decomposed before implementation

The project SHALL provide a visual-reference structure pipeline that turns a
visual reference, source-structure candidate, screenshot set, or comparable
building sample into a Reference Breakdown Contract before any generator or NBT
implementation work is started.

#### Scenario: Candidate is decomposed before generator edits

- **WHEN** a user asks to use `research/source_structures/candidate_003` as a
  building reference
- **THEN** the pipeline SHALL produce a Reference Breakdown Contract for that
  candidate
- **AND** generator code, shipped NBT resources, Java runtime code, and version
  metadata SHALL remain unchanged by the decomposition step.

### Requirement: The Reference Breakdown Contract uses four typed buckets

A Reference Breakdown Contract SHALL include the four buckets
`direct_component`, `atomic_component`, `generative_grammar`, and
`calibration_only`. Each non-empty entry SHALL record the observed visual cue,
the rationale for its classification, the downstream route, and the evidence or
review needed before implementation.

#### Scenario: Reference features are routed by bucket

- **WHEN** a breakdown card records a Hui-style 马头墙 cue
- **THEN** it SHALL classify the cue as an `atomic_component` or
  `generative_grammar` entry with a downstream route
- **AND** it SHALL NOT leave the cue only as an unclassified note.

#### Scenario: Calibration-only references do not become assets

- **WHEN** a cue is placed in `calibration_only`
- **THEN** the contract SHALL state the visual question it calibrates
- **AND** the contract SHALL NOT route that cue to prefab import, form-registry
  implementation, or planner implementation.

### Requirement: Source facts are preserved separately from use decisions

The pipeline SHALL preserve source facts such as candidate id, title, URL,
recorded license, local-research decision, and attribution path separately from
the decomposition bucket decisions. A local-research candidate SHALL NOT be
treated as automatically importable or redistributable.

#### Scenario: Local research candidate is not copied

- **WHEN** a candidate has `usage_decision: local_research`
- **THEN** the breakdown SHALL preserve that source fact
- **AND** it SHALL classify reusable architectural language without claiming
  permission to copy or ship the third-party structure.

### Requirement: Worked examples define bucket boundaries

The pipeline SHALL include at least one worked example decomposition card. The
first worked example SHALL use `candidate_003` and SHALL demonstrate how a
single reference can split into direct component candidates, atomic components,
generative grammar, and calibration-only criteria.

#### Scenario: Hui-style example includes all route types

- **WHEN** the `candidate_003` worked example is inspected
- **THEN** it SHALL include at least one candidate entry in
  `atomic_component`, `generative_grammar`, and `calibration_only`
- **AND** it SHALL explicitly state whether any part of the reference is safe
  to consider as a `direct_component`.

### Requirement: Downstream routes are explicit

Every non-empty direct, atomic, or grammar entry SHALL identify a downstream
route such as a future OpenSpec capability, GenOps pipeline, buildgen form
registry, compound planner, prefab library, validator, or visual review task.

#### Scenario: Grammar route points to a future change

- **WHEN** a breakdown entry identifies the Hui-style 堂--井--堂 sequence as
  `generative_grammar`
- **THEN** it SHALL route that entry to a future OpenSpec or existing FUTURE
  spec such as `huipai-tianjing-mansion`
- **AND** it SHALL describe the validator or visual evidence expected before
  implementation can be accepted.

### Requirement: Human visual judgment gates aesthetic decomposition

The pipeline SHALL treat visual-reference decomposition as planning evidence
until the owner gives a human verdict. Automated classification, preview
artifacts, or CRAFT run status SHALL NOT be treated as final visual acceptance.

#### Scenario: Breakdown awaits owner verdict

- **WHEN** a reference-decomposition run produces a complete breakdown card
- **THEN** the run SHALL remain pending owner review or record an explicit human
  verdict
- **AND** the pipeline SHALL NOT claim that the decomposed style is visually
  accepted without that verdict.
