## ADDED Requirements

### Requirement: Plaque placement integrates with entry-detail and paifang passes
The facade-detail pass SHALL consult `plaque_bindings.json` for any archetype before placing doorway signage, and the paifang motif pass SHALL do the same before placing the central tablet. Plaque placement SHALL honor the building's facade orientation and the binding's declared `mount` (wall-mounted plaques sit on the wall above the door or beside it; hanging plaques hang from the lintel or from a paifang crossbeam with chains). Horizontal wall-mounted plaques SHALL assign `col` parts in exterior-view order so inscriptions read in source-PNG order from the building front, including north- and east-facing facades where visual left differs from increasing world coordinate order.

#### Scenario: A shop doorway receives a horizontal wall plaque
- **WHEN** the facade-detail pass runs for a `cultivation_shop` archetype with a `plaque_bindings.json` entry specifying `orientation=horizontal, mount=wall`
- **THEN** a horizontal `myvillage:wall_plaque` SHALL be placed above the doorway
- **AND** its blockstate-resolved plaque textures SHALL contain the bound inscription.

#### Scenario: A north-facing manor plaque reads in source order
- **WHEN** the facade-detail pass places a horizontal wall plaque on a north-facing `lord_manor` facade
- **THEN** the leftmost part as seen by a player outside the facade SHALL use `col=left`
- **AND** the rightmost part as seen by that player SHALL use `col=right`.

#### Scenario: An inn doorway receives a vertical hanging plaque
- **WHEN** the facade-detail pass runs for a `cultivation_inn` archetype with a binding specifying `orientation=vertical, mount=hanging`
- **THEN** a vertical `myvillage:hanging_plaque_vertical` SHALL be placed beside the doorway
- **AND** vanilla `minecraft:chain[axis=y]` SHALL be placed above it.

#### Scenario: A scripture pavilion receives a 5w×2h 大字 plaque
- **WHEN** the facade-detail pass runs for a `scripture_pavilion` archetype with a binding specifying `frame=sect_treasure_gilded_5w_2h`
- **THEN** a 5w×2h `myvillage:wall_plaque` SHALL be placed using the 2D multipart `row × col` geometry
- **AND** the `5w_2h` bucket inscription SHALL be baked into the shared full plaque texture sampled by the placed plaque part models.

#### Scenario: A paifang central tablet uses a hanging plaque
- **WHEN** the paifang motif runs for a sect compound with a binding specifying `mount=hanging`
- **THEN** a `myvillage:hanging_plaque` SHALL be placed centered on the paifang crossbeam
- **AND** vanilla chains SHALL be placed from the crossbeam down to the plaque's top edge.
