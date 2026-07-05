## ADDED Requirements

### Requirement: Hui-style reference slice validation checks load-bearing cues
Validation for the Hui-style reference slice SHALL fail a generated sample that
lacks the required hall/sky-well sequence, paired side-wing enclosure, closed
facade, stepped gable wall cue, stepped-gable visual thickness/coping/return
wall treatment, or no-garden constraint. The validation result SHALL be
included in the generated report for the sample family.

#### Scenario: Missing Tianjing sequence fails validation
- **WHEN** a Hui-style sample report lacks 门堂, 天井一, 享堂, 天井二, or 寝堂
- **THEN** the Hui-style validation SHALL fail
- **AND** the error SHALL identify the missing sequence cue.

#### Scenario: Garden drift fails validation
- **WHEN** a Hui-style sample includes a 花园, garden pavilion, large pond, or
  rockery garden parcel
- **THEN** the Hui-style validation SHALL fail
- **AND** the error SHALL identify the Jiangnan garden drift.

#### Scenario: Three detached halls fail validation
- **WHEN** a Hui-style sample contains the hall sequence but lacks paired side
  wings flanking both sky-wells
- **THEN** the Hui-style validation SHALL fail
- **AND** the error SHALL identify the missing side-wing enclosure.

#### Scenario: Overfilled or undersized footprint fails validation
- **WHEN** a Hui-style sample uses side wings that fill the side yards, crowd
  the sky-well slice, or omits the expanded review-lot footprint mode
- **THEN** the Hui-style validation SHALL fail
- **AND** the error SHALL identify side-wing overfill or missing footprint mode.

#### Scenario: Packed sequence gaps fail validation
- **WHEN** adjacent hall / sky-well sequence elements are packed without clear
  ground between them
- **THEN** the Hui-style validation SHALL fail
- **AND** the error SHALL identify a too-tight sequence gap.

#### Scenario: Undersized building mass fails validation
- **WHEN** halls or side wings are too small or too low relative to the expanded
  review lot
- **THEN** the Hui-style validation SHALL fail
- **AND** the error SHALL identify undersized hall, side-wing, or vertical mass.

#### Scenario: Reference provenance is preserved in report
- **WHEN** the Hui-style reference slice report is written
- **THEN** it SHALL identify `candidate_003` as reference provenance
- **AND** it SHALL state that the shipped structure is generated original
  output, not a copied source asset.
