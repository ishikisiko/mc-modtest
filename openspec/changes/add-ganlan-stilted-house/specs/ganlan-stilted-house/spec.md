## ADDED Requirements

### Requirement: Ganlan stilted-house slice is original generated output
The generator SHALL ship a first `ganlan_stilted_house` reference slice as
original generated output derived from `candidate_005` breakdown grammar, not as
copied or imported third-party structure data. The slice SHALL preserve
`candidate_005` only as research/reference provenance and SHALL NOT redistribute
source NBT, schematic, litematic, jigsaw-pool, or world-save assets.

#### Scenario: Slice emits original structures
- **WHEN** the Ganlan stilted-house slice is generated
- **THEN** it SHALL emit `ganlan_stilted_house_NNN` structure resources from
  project generator code
- **AND** no shipped resource SHALL be copied from
  `research/source_structures/candidate_005` or upstream `mcs:village/ganlan`
  source assets.

### Requirement: Ganlan houses have raised floors and structural stilts
Each generated `ganlan_stilted_house_NNN` sample SHALL place the occupied living
floor above the local ground/water contact plane and SHALL provide visible
structural posts from the underside of the floor down to the support surface.
The underside SHALL remain mostly open so the sample reads as stilt
architecture rather than a normal building on a filled base.

#### Scenario: Raised floor is supported by posts
- **WHEN** a Ganlan sample is inspected
- **THEN** its report or generation metadata SHALL identify a raised floor
- **AND** the bottom of the living floor SHALL be at least two blocks above the
  support plane
- **AND** multiple support posts SHALL connect from the floor underside to the
  support plane
- **AND** the underside SHALL remain mostly open.

### Requirement: Ganlan houses have accessible raised entries
Each generated Ganlan sample SHALL provide a walkable access route from the
settlement/path plane to the raised living floor. The route SHALL include a stair
or step sequence and SHALL connect to a door or raised veranda.

#### Scenario: Entry stair reaches the living floor
- **WHEN** a Ganlan sample is validated
- **THEN** the validator SHALL find an access stair or equivalent step route
  from the path plane to the raised floor
- **AND** the route SHALL end at a door, landing, or raised veranda cell.

### Requirement: Ganlan houses carry veranda and deep-eave cues
Each generated Ganlan sample SHALL include a raised veranda or deck edge with a
rail/fence/trapdoor cue and a deep rain-shelter roof overhang. The roof SHALL
protect the body and veranda without hiding the raised-floor identity.

#### Scenario: Veranda and deep eaves are present
- **WHEN** a Ganlan sample is inspected from an oblique preview
- **THEN** it SHALL expose a raised veranda or deck edge
- **AND** it SHALL include a rail, fence, trapdoor, or similar edge cue
- **AND** the roof overhang SHALL extend beyond the wall core.

### Requirement: Ganlan slice remains partial until accepted visually
The first Ganlan slice SHALL be marked as a partial implementation until
validator checks, preview evidence, and owner visual verdict all pass. It SHALL
NOT claim completion of a full Ganlan village, biome placement, or worldgen
integration.

#### Scenario: Slice requires visual verdict
- **WHEN** automated generation and validation pass for the Ganlan slice
- **THEN** the run SHALL still require owner visual verdict before the slice is
  treated as visually accepted
- **AND** the report SHALL state that broader village/worldgen integration
  remains future work unless separately implemented.
