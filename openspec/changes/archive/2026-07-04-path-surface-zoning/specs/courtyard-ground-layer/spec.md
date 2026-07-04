## MODIFIED Requirements

### Requirement: Yard-fill pass writes a solid block at every non-building cell

The compound layer SHALL run a `_place_yard_ground` pass that writes a solid
block at every lot-interior cell that is not in `building_cells()`,
`water_feature.cells`, `water_jar.cells`, `planting.cells`, or
`courtyard_tree.cells`. The block written SHALL resolve through one of **four**
zone slots, chosen by the cell's `ground_kind`:

- `open_sky` cells → `GROUND_YARD_OPEN`.
- `under_eave` cells that lie in the eave-drip ring (the 1-cell Chebyshev ring
  around a `BuildingSlot.footprint`, NOT including `covered_gallery` or
  `moon_platform` cells) → `GROUND_YARD_HEART` (天井/院心, grey brick).
- `under_eave` cells that lie in a `covered_gallery` parcel → `PATH_GALLERY`
  (廊道, wood-stone mix).
- Cells that lie in a `moon_platform` parcel → `GROUND_YARD_HEART`.
- Cells that lie in a service 夹道 (the 倒座 `side_alley`) → `PATH_ALLEY`.

The remaining `under_eave` cells (any not matching the rules above) SHALL
resolve to `GROUND_YARD_UNDER_EAVE`, preserving the prior behavior for any
eave cell not promoted to a heart/gallery/alley zone.

The natural surface y for the ground block SHALL be:

- `y = -1` in the outer yard band (south of the inner gate).
- `y = -1` in the inner gate band (no plinth here).
- `y = plinth_h - 1` in the main yard band (on top of the main-yard plinth).

The block SHALL be written with tags `["DETAIL", "GROUND"]` and priority
`DETAIL (70)`.

#### Scenario: An open-sky cell gets a grass block

- **WHEN** a courtyard compound places an outer-yard cell with `ground_kind == "open_sky"` and the style's `GROUND_YARD_OPEN` slot resolves to `minecraft:grass_block`
- **THEN** the ground-fill pass SHALL write `minecraft:grass_block` at the cell's natural surface y.

#### Scenario: An eave-drip-ring cell gets a grey-brick heart block

- **WHEN** a courtyard compound places a cell in the 1-cell Chebyshev ring around
  a `BuildingSlot.footprint` (and not in a gallery or moon platform)
- **THEN** the ground-fill pass SHALL write the `GROUND_YARD_HEART` slot's block
  at the cell's natural surface y.

#### Scenario: A gallery cell gets a wood-stone floor

- **WHEN** a courtyard compound places a cell in a `covered_gallery` parcel
- **THEN** the ground-fill pass SHALL write the `PATH_GALLERY` slot's block
  at the cell's natural surface y.

#### Scenario: A service-alley cell gets a brick floor

- **WHEN** a courtyard compound places a cell in the 倒座 `side_alley`
- **THEN** the ground-fill pass SHALL write the `PATH_ALLEY` slot's block
  at the cell's natural surface y.

#### Scenario: A remaining eave cell keeps the stone-brick fallback

- **WHEN** an `under_eave` cell does not match the heart / gallery / alley / moon-platform rules
- **THEN** the ground-fill pass SHALL write the `GROUND_YARD_UNDER_EAVE` slot's block.

#### Scenario: A building cell is not overwritten by yard fill

- **WHEN** a cell lies in a `BuildingSlot.footprint`
- **THEN** the yard-fill pass SHALL NOT write a block at that cell
- **AND** the cell's eventual block SHALL come from the building pass, not the yard-fill pass.

### Requirement: Ground layer uses vanilla-only style slots

The five ground-layer slots (`GROUND_YARD_OPEN`, `GROUND_YARD_HEART`, `PATH_GALLERY`, `PATH_ALLEY`, `GROUND_YARD_UNDER_EAVE`) and the tour slot (`PATH_TOUR`) SHALL be defined on the style JSON (`tools/buildgen/styles/<style>.json`) with vanilla-only block ids. The vanilla profile (`--profile vanilla`) SHALL resolve every ground block to a `minecraft:` id; the full profile MAY resolve to an external-mod id if the style is the `full` profile. The `PATH_TOUR` slot SHALL resolve to `minecraft:mossy_stone_bricks` and SHALL NOT resolve to any cobblestone variant.

#### Scenario: Vanilla profile resolves the heart ground to a vanilla block

- **WHEN** a courtyard compound is generated with `--profile vanilla`
- **THEN** every eave-drip-ring cell SHALL be a `minecraft:` id from the
  `GROUND_YARD_HEART` slot
- **AND** every gallery cell SHALL be a `minecraft:` id from the `PATH_GALLERY`
  slot.

#### Scenario: The full profile may use external-decor ground blocks

- **WHEN** a courtyard compound is generated with `--profile full`
- **THEN** the ground layer MAY resolve to an external-mod block id from the slot
- **AND** the slot definition SHALL still list `minecraft:` ids as trailing fallbacks so the vanilla profile resolves correctly.

## ADDED Requirements

### Requirement: The 天井/院心 heart zone is the eave-drip ring

The 天井/院心 (`GROUND_YARD_HEART`) zone SHALL be exactly the union of: (a) the
1-cell Chebyshev ring around each `BuildingSlot.footprint` (the cells already
classified `under_eave` by the ring rule, excluding `covered_gallery` and
`moon_platform` cells), and (b) the `moon_platform` cells. The yard interior
(cells not in the ring, a gallery, a moon platform, or a building) SHALL remain
`open_sky` and resolve to `GROUND_YARD_OPEN` (grass). The heart zone SHALL NOT
cover the whole yard.

#### Scenario: The yard interior stays grass under the heart-zone rule

- **WHEN** a yard cell is not in the eave-drip ring, a gallery, or a moon
  platform
- **THEN** the cell SHALL remain `open_sky` and resolve to `GROUND_YARD_OPEN`.

#### Scenario: The heart zone does not pave the whole yard

- **WHEN** the heart-zone cells are counted
- **THEN** the count SHALL be strictly less than the total non-building yard cell
  count
- **AND** at least one yard interior cell SHALL resolve to `GROUND_YARD_OPEN`.
