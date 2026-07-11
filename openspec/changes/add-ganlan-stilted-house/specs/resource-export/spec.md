## ADDED Requirements

### Requirement: Ganlan stilted-house resources are exported for review
The generated resource pipeline SHALL export the Ganlan stilted-house sample
family as `myvillage` structure templates plus matching place and gallery
functions so reviewers can inspect the slice in-game.

#### Scenario: Ganlan gallery is exported
- **WHEN** the `ganlan_stilted_house` group is generated
- **THEN** the exporter SHALL write
  `src/main/resources/data/myvillage/function/gallery/ganlan_stilted_house.mcfunction`
- **AND** it SHALL write
  `src/main/resources/data/myvillage/function/place/ganlan_stilted_house_*.mcfunction`
  for each generated Ganlan stilt-house structure.

### Requirement: Canonical generation includes the Ganlan review slice
The canonical mod generation entrypoint SHALL include the Ganlan stilted-house
review slice in the generated `myvillage` structure resources.

#### Scenario: Canonical generation emits Ganlan samples
- **WHEN** `generate_all_structures.py` runs with default arguments
- **THEN** the output structure directory SHALL contain
  `ganlan_stilted_house_001.nbt` through `ganlan_stilted_house_002.nbt`
- **AND** generated place and gallery functions for the Ganlan sample family
  SHALL exist.
