## MODIFIED Requirements

### Requirement: Corridors connect wings along the courtyard

The compound layer SHALL place a connected path network that reaches every reachable goal in the compound, including every door, every water feature, every planting bed, and the moon platform apron. The path network SHALL be produced by a multi-source BFS whose endpoint set is defined by the `courtyard-path-network` spec. The path SHALL be written on top of the ground layer defined by the `courtyard-ground-layer` spec. The path SHALL route around water and planting cells, SHALL NOT overlap building door cells, and SHALL bridge the main-yard plinth boundary with a single stairs block as specified by `courtyard-path-network`. The reachability and hole-free invariants are validated by `validate_compound` and `validate_small_courtyard`.

The previous "corridor" terminology (a `covered_gallery` parcel node connecting 垂花门 to main hall) is unchanged — covered galleries are a roofed structure, not a ground path, and remain a separate concept. The "corridors connect wings" wording is replaced by the path-network spec; the covered-gallery geometry is governed by `courtyard-compound` "Corridors connect wings along the courtyard" (the covered-gallery parcel requirement) and is not restated here.

#### Scenario: The path network reaches every door

- **WHEN** a courtyard compound is generated
- **THEN** every `BuildingSlot`'s `door_info["front"]` cell SHALL be in the path endpoint set
- **AND** the multi-source BFS SHALL reach every endpoint
- **AND** the path SHALL be a single connected component.

#### Scenario: The path network reaches landscape features

- **WHEN** a courtyard compound places a `water_feature` (well), `water_jar` (fish jar), or `planting` parcel node
- **THEN** the path network SHALL include an endpoint for each
- **AND** the multi-source BFS SHALL reach every endpoint.

#### Scenario: The path network does not overlap doors

- **WHEN** the path block is written
- **THEN** no path cell SHALL overlap any `BuildingSlot`'s `door_info["front"]` cell.

#### Scenario: The path bridges the plinth boundary

- **WHEN** the main yard sits on a raised plinth and the path enters the main yard
- **THEN** a `minecraft:stone_brick_stairs` block SHALL be placed at the boundary cell as specified by the `courtyard-path-network` spec.