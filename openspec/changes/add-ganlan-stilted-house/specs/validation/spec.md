## ADDED Requirements

### Requirement: Ganlan validation checks stilt-house load-bearing cues
Validation for the Ganlan stilted-house slice SHALL fail a generated sample that
lacks a raised floor, support posts reaching the support plane, a mostly open
underside, a reachable raised entry, deep-eave/veranda cues, or reference
provenance. The validation result SHALL be included in the generated report for
the sample family.

#### Scenario: Missing raised floor fails validation
- **WHEN** a Ganlan sample report lacks raised-floor metadata or the living floor
  is not elevated above the support plane
- **THEN** Ganlan validation SHALL fail
- **AND** the error SHALL identify the missing raised-floor cue.

#### Scenario: Unsupported or filled underside fails validation
- **WHEN** a Ganlan sample has no support posts reaching the support plane or its
  underside is mostly filled
- **THEN** Ganlan validation SHALL fail
- **AND** the error SHALL identify unsupported or pedestal-like massing.

#### Scenario: Inaccessible raised entry fails validation
- **WHEN** a Ganlan sample lacks a stair, step, or landing route from the path
  plane to the living floor
- **THEN** Ganlan validation SHALL fail
- **AND** the error SHALL identify the missing raised-entry access.

#### Scenario: Reference provenance is preserved in report
- **WHEN** the Ganlan reference slice report is written
- **THEN** it SHALL identify `candidate_005` as reference provenance
- **AND** it SHALL state that the shipped structure is generated original output,
  not a copied source asset.
