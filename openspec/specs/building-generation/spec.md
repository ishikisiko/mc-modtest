# Building Generation

## Purpose

This spec captures the current procedural building generation baseline for an eventual town-generation system. It is temporary and mutable; proposed changes to module boundaries, pass order, archetype composition, or generated structure semantics should be discussed with the project owner first.

## Requirements

### Requirement: The generator uses a layered pipeline
The building generator SHALL use the following conceptual layers: Settlement Group, Style Profile, Building Archetype, Scale Tier, Massing Graph, Facade Grammar, Build Ops/Form Registry, Pass and Protection, Quality Check, and Resource Export. The active settlement group, when supplied, SHALL bind the style profile, archetype roster, and layout strategy.

#### Scenario: A building is generated
- **WHEN** `generate_building(style, archetype, scale_tier, seed)` is called
- **THEN** it SHALL run the configured generation passes in deterministic order for that seed
- **AND** it SHALL return a build context containing the generated graph, block grid, and pass metadata.

#### Scenario: A grouped building is generated
- **WHEN** `generate_building(style, archetype, scale_tier, seed, group_id)` is called with a group
- **THEN** the requested archetype SHALL be validated against that group's roster
- **AND** the generated graph metadata SHALL record the active `group_id`.

### Requirement: Archetypes define semantic massing, not direct blocks
Building archetypes SHALL construct a `MassingGraph` made of semantic nodes and metadata before block placement occurs.

#### Scenario: A small house massing is built
- **WHEN** the `small_house` archetype is generated
- **THEN** the massing graph SHALL include a main volume, a front door definition, interior zones, a path patch, exterior decoration patches, and either a chimney or alternate window-detail metadata
- **AND** block placement SHALL occur in later passes.

### Requirement: Building parts expose town-frontage metadata
Generated town building graphs SHALL expose which side faces the town street and where the shopfront or entry opening is located, so street-room and lived-in-tissue layers can attach to the correct face. When a part does not declare frontage, the largest open side SHALL be used as the default front.

#### Scenario: A part declares its street frontage
- **WHEN** a building part is generated for town use
- **THEN** it SHALL record its street-facing side and the cells of its shopfront or entry opening
- **AND** a part with no declared frontage SHALL default its front to its largest open side.

### Requirement: An importance tier drives massing and roof-grade selection
Town building selection SHALL accept an optional importance tier that biases massing height and roof grade, where a higher tier yields taller massing and a higher roof grade. The town's dominant landmark SHALL be selected at the top tier.

#### Scenario: Importance biases the building
- **WHEN** a town parcel requests a building at a given importance tier
- **THEN** a higher tier SHALL bias selection toward taller massing and a higher roof grade
- **AND** the dominant-landmark parcel SHALL request the top importance tier.

### Requirement: Supported archetypes and tiers are explicit
The current default generated building-library archetypes SHALL be `small_house`, `medium_house`, `blacksmith`, `small_shop`, `medium_shop`, and `big_house`. The current default scale tiers SHALL include `small`, `medium`, `large_lite`, shop variant tiers, and big-house variant tiers. Chinese courtyard sub-building archetypes `main_hall`, `side_wing`, `front_row`, and `gate_house` SHALL be available to the compound generator rather than emitted by the default medieval building-library loop. Civic archetypes `tavern` and `lord_manor` SHALL be available to the civic library generator rather than emitted by the default medieval building-library loop. Cultivation town archetypes SHALL be available to the `cultivation_town` town-generation system and reusable courtyard-street block parts, and cultivation sect archetypes SHALL be emitted only by the `cultivation_sect` group. The cultivation-specific massing of these archetypes (raised platforms, colonnades, pagoda story insets, the built three-bay mountain gate, the alchemy furnace, and the omission of Western domestic tells) is specified by the cultivation-massing-grammar capability and is not restated here.

#### Scenario: The building library is generated with count 10
- **WHEN** the library generator emits ten entries per archetype
- **THEN** `small_house` entries SHALL use the `small` tier
- **AND** `medium_house` entries 1 through 7 SHALL use `medium`
- **AND** `medium_house` entries 8 through 10 SHALL use `large_lite`
- **AND** `blacksmith` entries 1 through 2 SHALL use `small`
- **AND** `blacksmith` entries 3 through 8 SHALL use `medium`
- **AND** `blacksmith` entries 9 through 10 SHALL use `large_lite`
- **AND** shop and big-house archetypes SHALL emit their configured variant counts.

#### Scenario: A Chinese courtyard sub-building is generated
- **WHEN** the compound generator requests `main_hall`, `side_wing`, `front_row`, or `gate_house`
- **THEN** the building pipeline SHALL generate it from a Chinese massing builder
- **AND** `main_hall` MAY use the multi-story capability
- **AND** these Chinese archetypes SHALL NOT reuse the `small_house` massing.

#### Scenario: A civic archetype is generated
- **WHEN** the civic library generator requests `tavern` or `lord_manor`
- **THEN** the building pipeline SHALL generate it from the civic massing builder
- **AND** the medieval building-library loop SHALL NOT emit civic archetypes
- **AND** the compound generator SHALL NOT emit civic archetypes.

#### Scenario: A cultivation town archetype is generated for town use
- **WHEN** the town-generation system or courtyard-street block part generator requests `cultivation_house`, `cultivation_shop`, `cultivation_inn`, `cultivation_market`, or `town_shrine` under the `cultivation_town` group
- **THEN** the building pipeline SHALL use the `cultivation_town` style profile
- **AND** the sect-roster archetypes SHALL NOT be emitted by the town group.

#### Scenario: A cultivation sect archetype is generated
- **WHEN** the library generator requests `sect_gate`, `sect_main_hall`, `scripture_pavilion`, `alchemy_room`, or `disciple_quarters` under the `cultivation_sect` group
- **THEN** the building pipeline SHALL generate it from a sect massing builder
- **AND** it SHALL NOT reuse town or medieval housing massing as the archetype implementation.

#### Scenario: The new Western families are generated
- **WHEN** the library generator emits the shop and big-house families
- **THEN** `small_shop` SHALL produce at least five 1-story variants
- **AND** `medium_shop` SHALL produce at least five 2-story variants
- **AND** `big_house` SHALL produce at least five variants each with 2 or 3 stories.

### Requirement: Shop archetype family
The generator SHALL support a shop archetype family, classified as a functional/commercial archetype family that serves town generation by providing commercial buildings distinct from housing. The family SHALL provide two tiers: `small_shop` as a 1-story compact storefront and `medium_shop` as a 2-story ground-floor storefront with upstairs living. The `medium_shop` SHALL use the multi-story massing capability. A shop node MAY carry an optional `industry` meta field; generation output SHALL NOT depend on that field until industry-specific behavior is introduced.

#### Scenario: A small shop is generated
- **WHEN** the `small_shop` tier is generated
- **THEN** the massing graph SHALL be a single story with a storefront feature on the front wall
- **AND** block placement SHALL occur in later passes.

#### Scenario: A medium shop is generated
- **WHEN** the `medium_shop` tier is generated
- **THEN** the main volume SHALL have `stories == 2`
- **AND** the ground story SHALL carry a storefront feature and the upper story SHALL carry a residential-style window band.

#### Scenario: The industry field carries no behavior
- **WHEN** a shop node is created
- **THEN** an `industry` meta field MAY be present
- **AND** generation output SHALL NOT depend on the value of `industry`.

### Requirement: Big house archetype family
The generator SHALL support a `big_house` archetype family, classified as a housing archetype family that serves town generation by providing larger residences above `small_house` and `medium_house`. A `big_house` SHALL use the multi-story massing capability with 2 to 3 stories chosen per seed.

#### Scenario: A big house is generated
- **WHEN** the `big_house` archetype is generated
- **THEN** the main volume SHALL have `stories` between 2 and 3 inclusive
- **AND** the massing graph SHALL include floor slabs and a stairwell connecting the stories.

### Requirement: New families provide five strongly distinct variants
The shop and big-house families SHALL each provide at least five generated variants per tier. Variant distinction SHALL be stronger than `small_house` variation. Shop variants SHALL differ on a form axis such as story count, roof style, signage, awning/eave, footprint, or entrance. Big-house variants SHALL differ structurally by massing, story count, or roof differences rather than only by exterior decoration patches.

#### Scenario: The library generates five shop variants
- **WHEN** the library generator emits a shop tier
- **THEN** it SHALL produce at least five variants
- **AND** the variants SHALL differ on at least one form-axis attribute beyond decoration patches.

#### Scenario: The library generates five big-house variants
- **WHEN** the library generator emits the `big_house` family
- **THEN** it SHALL produce at least five variants
- **AND** the variants SHALL exhibit structural differences rather than only differing decoration patches.

### Requirement: Archetype expansion targets town variety
Future building-generation changes SHALL preserve the project direction of expanding toward varied town generation, including multiple house types, functional buildings, roads or town pieces, and possible NPC-supporting structures.

#### Scenario: A new archetype family is proposed
- **WHEN** a contributor proposes a new generated archetype family
- **THEN** the proposal SHALL identify whether it is housing, functional, infrastructure, civic, decorative, or NPC-supporting
- **AND** it SHALL explain how the archetype fits broader town generation rather than only a one-off village template.

### Requirement: Civic archetype classification
The civic archetype family SHALL be classified as civic/public buildings for the purposes of town generation. Civic archetypes SHALL NOT be classified as housing, functional-industry, or decorative. Each civic archetype SHALL declare the civic role blocks it depends on so that validators can confirm functional identity.

#### Scenario: A new civic archetype is proposed
- **WHEN** a contributor proposes a new civic archetype
- **THEN** the proposal SHALL identify the civic role blocks the archetype depends on
- **AND** it SHALL explain how the archetype serves broader town generation as a public/civic anchor.

### Requirement: Massing coordinates follow the current convention
Generated building massing coordinates SHALL treat low z as the front, high z as the back, positive x as east, negative x as west, and `y=0` as the bottom of the foundation.

#### Scenario: A front-facing entrance is planned
- **WHEN** an archetype records its primary door
- **THEN** it SHALL use wall `front`
- **AND** the corresponding outward direction SHALL be north.

### Requirement: Protected cells survive later normal writes
A `PROTECTED` cell SHALL NOT be overwritten by a later normal write. Because the grid enforces only `PROTECTED` and otherwise lets the last writer win, a pass that adds interior furnishing, an inter-volume connection, or a chimney SHALL NOT write into a cell that lies in another volume's wall plane; it SHALL decline the write or route around the occupied wall rather than rely on pass priority.

#### Scenario: A later detail pass writes into a protected window
- **WHEN** a normal grid write targets an existing protected window cell
- **THEN** the grid write SHALL return false
- **AND** the protected window blockstate SHALL remain unchanged.

#### Scenario: A door op cannot remove a protected entry step
- **WHEN** a later pass attempts to overwrite a `PROTECTED` entry-step cell
- **THEN** the write SHALL be refused unless it is explicitly forced.

#### Scenario: A chimney abuts an attached wing wall
- **WHEN** a chimney column would fall on a cell occupied by an abutting `side_wing` or shed wall
- **THEN** the chimney SHALL NOT force-overwrite that wall's facade cell
- **AND** it SHALL offset around the wing or re-seal the wall so material stays continuous.

### Requirement: The pass order is stable
The current core pass order SHALL be `massing_pass`, `structure_pass`, `mezzanine_floor_pass`, `floor_slab_pass`, `stair_pass`, `facade_detail_pass`, `roof_pass`, `roof_cleanup_pass`, `material_variation_pass`, `interior_furnishing_pass`, and `exterior_decoration_pass`. `mezzanine_floor_pass` SHALL run after `structure_pass` and before `floor_slab_pass`, and SHALL be a no-op for volumes without mezzanine metadata. `floor_slab_pass` and `stair_pass` SHALL run after `mezzanine_floor_pass` and before `facade_detail_pass`, and SHALL be no-ops for single-story volumes. `floor_slab_pass` SHALL skip stories flagged `mezzanine_story` because the mezzanine pass already placed their floor.

#### Scenario: A building is generated for export
- **WHEN** all core passes complete
- **THEN** quality checking SHALL occur before resource export
- **AND** exported reports SHOULD record `quality_check_pass` and `resource_export_pass` after successful completion.

#### Scenario: A multi-story building runs the new passes
- **WHEN** a volume with `stories > 1` is generated
- **THEN** `mezzanine_floor_pass` SHALL run first
- **AND** `floor_slab_pass` SHALL place the inter-story floor slabs after `mezzanine_floor_pass`
- **AND** `stair_pass` SHALL place the stairwell and aligned floor openings before `facade_detail_pass`.

#### Scenario: A single-story building skips the new passes
- **WHEN** a volume with `stories == 1` is generated
- **THEN** `mezzanine_floor_pass`, `floor_slab_pass`, and `stair_pass` SHALL make no changes to the block grid.

#### Scenario: A mezzanine volume runs only the mezzanine pass for its mezzanine story
- **WHEN** a volume carries a `mezzanine` meta field
- **THEN** `mezzanine_floor_pass` SHALL place the partial-height slab over the configured half-plane
- **AND** `floor_slab_pass` SHALL skip the mezzanine story
- **AND** `floor_slab_pass` SHALL still place full slabs for any non-mezzanine story boundaries.

### Requirement: Facade planning avoids flat, corner-opening walls
Facade planning SHALL split walls into post-bounded bays, keep openings away from building corners, avoid occluded attached-wall intervals, and guarantee at least the style profile's minimum planned window count where possible. Every wall tall enough to carry it SHALL retain a stone plinth of at least one row, and an inter-volume connection opening SHALL be carved only on a real (non-open) wall and clear of the parent wall's post, window, and door columns.

#### Scenario: A facade plan places a window
- **WHEN** a window candidate is selected
- **THEN** its along-wall coordinate SHALL be at least two cells away from both wall ends
- **AND** it SHALL NOT overlap the door bay, a post position, or an occluded interval.

#### Scenario: A short wing keeps its stone plinth
- **WHEN** `wall_frame` builds a wall whose height can carry a plinth
- **THEN** at least one stone plinth row SHALL be placed at its base.

#### Scenario: A connection opening avoids a timber post
- **WHEN** an inter-volume connection is carved into a parent wall
- **THEN** the opening SHALL be placed clear of the parent wall's post, window, and door columns, re-sealing any post column it must cross
- **AND** no connection SHALL be carved into an open shed that has no wall.

### Requirement: Side walls are fully enclosed and free of stray blocks
A generated building's side walls SHALL be complete and structurally coherent. Every cell of a closed volume's wall plane, from the foundation top to the roofline directly above it, SHALL be a non-air block unless it is a planned opening (door, window, or inter-volume connection). No interior furnishing or other block belonging to one volume SHALL be placed in the exterior wall plane of a different volume.

#### Scenario: A gable end wall is enclosed up to the ridge
- **WHEN** a gabled volume is roofed
- **THEN** the gable plane SHALL be filled to the true ridge height with no apex gap
- **AND** any cell whose only roof block is a stair SHALL be backed by a full block in the wall plane.

#### Scenario: A blacksmith's smithy furniture stays inside its own shed
- **WHEN** the smithy interior zone is furnished beside the main building
- **THEN** anvils, barrels, and furnaces SHALL mount only on the smithy shed's own wall surfaces
- **AND** they SHALL NOT be placed against the main building's exterior side wall.

### Requirement: Gable infill uses a style-appropriate material
The gable triangle SHALL be filled from a style-declared gable material, defaulting to the volume's primary `WALL_MAIN` material when the style declares no dedicated gable slot. The generator SHALL NOT hardcode the dark roof plank as gable infill, and a gable cell SHALL be tagged with the material slot it actually holds.

#### Scenario: A stone-walled style produces a solid gable
- **WHEN** a `cultivation_sect`, `chinese_courtyard`, or `cultivation_town` building is gabled and the style declares no gable-infill slot
- **THEN** the gable SHALL be filled with the `WALL_MAIN` material
- **AND** it SHALL NOT contain dark roof planks scattered through the wall.

#### Scenario: A style opts into timber-infill gables
- **WHEN** a style declares a dedicated gable-infill material
- **THEN** the gable SHALL use that material, and the cell's recorded slot SHALL match the material placed.

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
