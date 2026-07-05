## ADDED Requirements

### Requirement: Hui-style reference slice is implemented as original generated output
The generator SHALL ship a first `chinese_huipai_mansion` reference slice as
original generated output derived from the `candidate_003` breakdown grammar,
not as copied or imported third-party structure data. The slice SHALL preserve
`candidate_003` only as research/reference provenance and SHALL NOT redistribute
source NBT, schematic, litematic, or world-save assets.

#### Scenario: Reference slice emits original structures
- **WHEN** the Hui-style reference slice is generated
- **THEN** it SHALL emit `chinese_huipai_mansion_NNN` structure resources from
  project generator code
- **AND** no shipped resource SHALL be copied from
  `research/source_structures/candidate_003`.

### Requirement: Hui-style reference slice carries the recognizable Tianjing sequence
Each generated `chinese_huipai_mansion_NNN` sample SHALL contain a recognizable
门堂 → 天井一 → 享堂 → 天井二 → 寝堂 sequence. The 天井 cells SHALL be small
enclosed sky-wells flanked by paired side wings / 厢房, and the sample SHALL NOT
include a 花园 or garden-pavilion parcel.

#### Scenario: Sample contains hall and sky-well sequence
- **WHEN** a generated Hui-style sample is inspected
- **THEN** its report or compound graph SHALL identify 门堂, 天井一, 享堂, 天井二,
  and 寝堂 in order
- **AND** both 天井 footprints SHALL be no larger than six cells in either
  horizontal dimension
- **AND** both 天井 SHALL be flanked by side-wing massing on the west and east
  sides so the sample does not read as three freestanding hall rows
- **AND** the side wings SHALL remain restrained enough to leave side-yard
  breathing room inside the expanded review-lot perimeter wall
- **AND** adjacent hall / sky-well sequence elements SHALL retain enough clear
  ground between them that the sample does not read as tightly stacked roofs
- **AND** the halls and side wings SHALL have enough footprint and vertical
  mass to remain legible as mansion buildings inside the expanded lot.

#### Scenario: Sample is not a Jiangnan garden mansion
- **WHEN** a generated Hui-style sample is inspected
- **THEN** it SHALL report no 花园, garden pavilion, large pond, or rockery
  garden parcel
- **AND** the 天井 SHALL be the only outdoor focus.

### Requirement: Hui-style reference slice has closed facade and stepped gable wall cues
Each generated Hui-style sample SHALL present a high closed street-facing
facade with a single primary entry and a stepped 马头墙 / fire-wall cue rising
above the adjacent roof line.

#### Scenario: Closed facade is present
- **WHEN** the generated sample is inspected from the street-facing side
- **THEN** the facade SHALL have one primary entry
- **AND** it SHALL not expose a wide open hall or garden opening on that side.

#### Scenario: Stepped gable cue is present
- **WHEN** the generated sample perimeter is inspected
- **THEN** it SHALL contain a stepped gable wall cue with at least two visible
  height stages above the adjacent wall or roof edge
- **AND** the cue SHALL have enough visual thickness, coping/cap treatment, and
  short return-wall hints that it does not read as a detached flat plate.

### Requirement: Hui-style reference slice remains partial until accepted visually
The first Hui-style slice SHALL be marked as a partial implementation until
validator checks, preview evidence, and owner visual verdict all pass. It SHALL
NOT claim completion of every FUTURE requirement in the baseline
`huipai-tianjing-mansion` spec.

#### Scenario: Slice requires visual verdict
- **WHEN** automated generation and validation pass for the Hui-style slice
- **THEN** the run SHALL still require owner visual verdict before the slice is
  treated as accepted
- **AND** the report SHALL state that the broader FUTURE vocabulary remains
  incomplete unless separately implemented.
