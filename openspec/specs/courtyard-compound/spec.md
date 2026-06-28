# Courtyard Compound

## Purpose

This spec captures the current parcel-level compound implementations. A compound is a parcel-level structure made from generated sub-buildings plus perimeter, landscape, and circulation elements. The current compound families are the Chinese one-courtyard layout, the cultivation town courtyard-street block layout, and the cultivation sect terraced axial layout.

## Requirements

### Requirement: A compound is a parcel layer above the building graph
The generator SHALL represent a courtyard compound as a `CompoundGraph` that owns parcel-level elements such as perimeter wall, water, planting, corridors, and path, plus building slots. Each building slot SHALL be realized by generating a sub-building through the existing per-building `MassingGraph` and pass pipeline.

#### Scenario: A compound is generated
- **WHEN** a courtyard compound is generated for a seed
- **THEN** the result SHALL include a `CompoundGraph` with parcel elements and building slots
- **AND** each building slot SHALL contain a generated sub-building produced by the per-building pipeline.

### Requirement: Chinese one-courtyard axial layout
A Chinese courtyard compound SHALL be laid out along a central axis as two yards separated by exactly one inner gate (垂花门): an outer yard (外院) on the street side and a main yard (主院) on the inward side. The outer yard SHALL contain a 影壁 (screen wall) inside the street gate blocking the direct sightline to the main yard, and MAY contain `front_row` (倒座) buildings. The main yard SHALL contain exactly two `side_wing` buildings (east and west), a `main_hall` on the central axis at the inward end, and a 月台 (moon platform) apron between the main hall and the yard. All buildings and yards SHALL be enclosed by a four-sided `perimeter_wall`. The single-进 `chinese_courtyard` family SHALL have exactly one 垂花门; multi-进 forms are a separate compound family realized by `chinese-mansion-compound` (3-进 江南大宅), not a `jin_count` axis on this family.

#### Scenario: The two yards are placed with one inner gate between them
- **WHEN** the one-courtyard layout is generated with `layout_type="standard"`
- **THEN** the layout SHALL place an outer yard and a main yard as two z-band regions of the same `CompoundGraph`
- **AND** exactly one 垂花门 parcel node SHALL be placed between the two yard bands
- **AND** the outer yard band SHALL be closer to the street gate than the main yard band.

#### Scenario: The 照壁 stands off-axis (照壁侧立 form)
- **WHEN** the outer yard is generated
- **THEN** a 照壁 parcel node SHALL be placed inside the street gate, between the gate and the 垂花门
- **AND** the 照壁 cells SHALL NOT lie on the central axis (照壁侧立: off-axis to avoid blocking the central path while still blocking the oblique sightline)
- **AND** `meta.form` SHALL be `"jingbi"` for the 北京四合院 style or `"zhaobi"` for the 江南 style.

#### Scenario: The 倒座 leaves a side alley
- **WHEN** the outer yard contains a `front_row` (倒座) building
- **THEN** the `front_row` footprint SHALL leave a walkable alley of at least 1 cell between itself and the perimeter wall on at least one side (east or west)
- **AND** the alley SHALL connect the gate area to the 垂花门 area without requiring the player to pass through the 倒座.

#### Scenario: The main hall carries a 月台
- **WHEN** the main yard is generated
- **THEN** a 月台 parcel node SHALL be placed between the main hall footprint and the main yard interior
- **AND** the 月台 SHALL be raised at least one cell above the main yard ground.

#### Scenario: The main hall is on the central axis at the inward end
- **WHEN** the one-courtyard layout is generated
- **THEN** the `main_hall` SHALL be on the central axis at the inward edge of the lot
- **AND** exactly two `side_wing` buildings SHALL be placed, one east and one west of the main yard
- **AND** a `perimeter_wall` SHALL enclose all buildings on four sides.

#### Scenario: The street gate breaks the perimeter on the axis
- **WHEN** the perimeter wall is generated
- **THEN** the wall SHALL have a single gate opening where the street-facing gate meets the central axis
- **AND** the 垂花门 SHALL be a separate structure inside the perimeter, not the same gate as the street gate.

#### Scenario: The 垂花门 passage is at least 3 cells wide
- **WHEN** the 垂花门 (inner gate) is placed
- **THEN** the gate's `passage` meta SHALL contain at least 3 cells: `axis_x - 1`, `axis_x`, `axis_x + 1` for each z in the gate band
- **AND** each passage cell SHALL be voxel-walkable from both adjacent yards per the courtyard-voxel-walkability spec.

### Requirement: Water and planting are structural layout elements
Water and planting SHALL occupy parcel cells and participate in layout. Corridors and the central path SHALL route around water and planting rather than overlapping them, and building footprints SHALL NOT overlap water or planting cells.

#### Scenario: A path routes around water
- **WHEN** a compound places a water feature and a central path
- **THEN** the path cells SHALL NOT overlap the water cells
- **AND** the path SHALL remain traversable from the gate to the main hall.

#### Scenario: Buildings do not overlap landscape
- **WHEN** building slots and landscape elements are placed
- **THEN** no building footprint SHALL overlap water or planting cells.

### Requirement: Corridors connect wings along the courtyard
A compound SHALL place 抄手游廊 (covered galleries) connecting the 垂花门's two flanks along the main yard edges to the main hall's two flanks. Each 抄手游廊 SHALL be a roofed gallery at least 3 cells wide and 3 cells tall, with standoff columns at the inner edge and a single-eave roof tying into both the 垂花门 and the main hall eave lines. 抄手游廊 cells SHALL route around water and planting; they SHALL NOT be ground-path-only corridors.

#### Scenario: 抄手游廊 are covered galleries, not ground paths
- **WHEN** the main yard is generated
- **THEN** the side corridors SHALL be parcel nodes of type `covered_gallery`
- **AND** each gallery SHALL have standoff columns resolving through the `COLUMN` slot
- **AND** each gallery SHALL have a roof connecting the 垂花门 eave to the main hall eave.

#### Scenario: 抄手游廊 route around landscape
- **WHEN** the main yard places water or planting cells
- **THEN** the covered galleries SHALL NOT overlap water or planting cells
- **AND** the galleries SHALL remain traversable from the 垂花门 to the main hall.

### Requirement: Ground path connects every door and landscape feature
The compound layer SHALL place a connected ground path network that reaches every reachable goal (every door, every water feature, every planting bed, and the moon platform apron). The endpoint set, the single-source backbone paving, the plinth-boundary stair bridging, the door-overlap prohibition, and the hole-free invariant are all defined normatively by `courtyard-path-network` and `courtyard-ground-layer`; this requirement exists only to bind those specs to the compound layer and is not restated here. Reachability and hole-free invariants are validated by `validate_compound` and `validate_small_courtyard`.

The "corridor" terminology (a `covered_gallery` parcel node connecting 垂花门 to main hall) is unchanged — covered galleries are a roofed structure, not a ground path, and remain a separate concept. The `抄手游廊` covered-gallery geometry is governed by the requirement above (`Corridors connect wings along the courtyard`); the walkable ground path is governed by `courtyard-path-network`.

#### Scenario: The path network is connected and reaches every goal

- **WHEN** a courtyard compound is generated
- **THEN** the path network SHALL satisfy the `courtyard-path-network` endpoint, backbone, and bridge invariants
- **AND** `validate_compound` / `validate_small_courtyard` SHALL report no `endpoint_unreachable`, `ground_layer_hole`, or `path_overlaps_building_door` error.

### Requirement: Compound variants are combinatorial
Compound variation SHALL be produced by variant axes combined via a deterministic template table (one row per shipped NBT). The variant axes SHALL include `layout_type` (`standard` / `three-sided` (三合院, no `front_row`) / `mu` (目字, narrow outer-yard band)), `main_orientation` (`south` / `east` / `north`), `main_bays` (`3` / `5` / `7`), `roof_grade` (one of the four `chinese_*` forms), `platform_tier` (`none` / `stone_2` / `xumi_3`), `gate_type` (`guangliang` / `manzi` / `jinzhu`), plus the minor axes `water_form` and `planting_layout`. The template table SHALL be hand-authored so that each shipped NBT lands on a visibly distinct combination of `layout_type`, `main_bays`, and `roof_grade`; the minor axes MAY be RNG-derived.

#### Scenario: The library generates visibly distinct compounds
- **WHEN** the compound library is generated with defaults
- **THEN** it SHALL emit six compound instances
- **AND** each instance SHALL differ from every other instance on at least one of `layout_type`, `main_bays`, or `roof_grade`.

#### Scenario: 三合院 layout type omits the front row
- **WHEN** a compound is generated with `layout_type="three-sided"`
- **THEN** the outer yard SHALL NOT contain a `front_row` building
- **AND** the two `side_wing` buildings SHALL extend forward toward the street gate to close the U-shape.

### Requirement: Compound resources are part of mod acceptance prep
The Chinese courtyard compound library, cultivation town building/block libraries, and cultivation sect building/compound libraries SHALL be generated, validated, packed into the current mod jar, and documented in the available command list before staged manual acceptance. The detailed acceptance-prep handoff (preview generation, HTTP server, changelog) is defined by the validation spec; the scenarios here record the compound-specific artifacts and what a reviewer inspects in game.

#### Scenario: A courtyard compound is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested
- **THEN** `chinese_courtyard_001.nbt` through `chinese_courtyard_006.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** the command documentation SHALL include `/myvillage list`, `/myvillage place chinese_courtyard_001`, `/myvillage gallery`, and `/myvillage gallery original`
- **AND** the reviewer SHOULD place at least one compound in game to inspect axial layout, perimeter and gate, landscape, corridors, and the two-story main hall.

#### Scenario: A cultivation town block is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested
- **THEN** `cultivation_town_001.nbt` through `cultivation_town_006.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** the command documentation SHALL include `/myvillage place cultivation_town_001` and `/myvillage gallery cultivation`
- **AND** the reviewer SHOULD place at least one town block in game to inspect continuous frontage, shared party walls, street and lane traversability, and gate orientation.

#### Scenario: A cultivation sect compound is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested
- **THEN** `cultivation_sect_001.nbt` through `cultivation_sect_002.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** matching sect placement metadata SHALL be present under `src/main/resources/data/myvillage/settlement_meta/`
- **AND** the command documentation SHALL include `/myvillage place cultivation_sect_001` and `/myvillage gallery cultivation`
- **AND** the reviewer SHOULD place at least one sect compound in game to inspect the mountain terraces, gate, per-level courtyards, monumental stairs, covered-gallery/flying-bridge links, siting context, and summit hall/pagoda.

### Requirement: Small-courtyard unit layout
The compound layer SHALL provide a small-courtyard unit layout that produces a compact walled `CompoundGraph` reusing the existing parcel machinery and per-building pass pipeline. A small courtyard SHALL enclose two to four roster buildings around a single small 天井 with a four-sided `perimeter_wall` broken by exactly one gate, at a footprint smaller than the one-进 `chinese_courtyard` layout.

#### Scenario: A small courtyard is generated
- **WHEN** a small-courtyard unit is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` reusing `ParcelNode` and `BuildingSlot` parcel elements
- **AND** it SHALL enclose between two and four building slots around a single small 天井
- **AND** its `perimeter_wall` SHALL have exactly one gate opening.

#### Scenario: The small courtyard is more compact than the one-courtyard layout
- **WHEN** a small-courtyard unit and a one-进 `chinese_courtyard` compound are generated
- **THEN** the small courtyard's lot footprint SHALL be smaller than the one-courtyard layout's lot footprint.

#### Scenario: Small-courtyard buildings respect landscape and walls
- **WHEN** a small courtyard places its building slots, 天井, and perimeter wall
- **THEN** no building footprint SHALL overlap the 天井 landscape cells or the wall cells.

### Requirement: Cultivation town courtyard-street block layout
The compound layer SHALL provide a `courtyard_street_block` layout strategy that tiles small courtyards along streets and optional lanes into one flattened `CompoundGraph`. The block SHALL arrange courtyards on a row/column grid separated by `street` and `lane` parcel cells, and each courtyard's building slots SHALL be realized through the existing per-building pass pipeline. This layout MAY remain generated as a reusable parcel/review form even though the `cultivation_town` settlement group's primary runtime layout is `town_generation` (see settlement-group).

#### Scenario: A cultivation town block is generated
- **WHEN** a town block is generated
- **THEN** the result SHALL be a single `CompoundGraph` whose layout strategy is `courtyard_street_block`
- **AND** it SHALL contain at least two small courtyards
- **AND** each courtyard's building slots SHALL be filled by sub-buildings produced by the per-building pipeline
- **AND** it SHALL include `street` parcel cells and MAY include `lane` parcel cells
- **AND** no building, wall, tianjing, water, or planting cell SHALL overlap street or lane cells
- **AND** each gate SHALL be reachable from the street network.

### Requirement: Town blocks form continuous street frontage
Adjacent courtyards in a row SHALL share a single party wall on their common lot line so their 院墙 form continuous street frontage, while the outer edge of the block SHALL remain a continuous perimeter wall. Each courtyard's gate SHALL open onto the nearest street.

#### Scenario: Neighboring courtyards share one wall
- **WHEN** two courtyards are tiled adjacently in a row
- **THEN** their shared lot line SHALL contain exactly one wall thickness (no doubled wall, no gap)
- **AND** the outer block edge SHALL remain a continuous wall.

#### Scenario: Gates face the street
- **WHEN** a courtyard is placed in a block
- **THEN** its gate opening SHALL be on the side facing the nearest street.

### Requirement: Town blocks are combinatorial variants
Town-block variation SHALL be produced by independent variant axes combined per seed, including block shape (rows × courtyards per row), street width, lane presence, and corner-frontage. The default library SHALL emit a set of distinct town blocks differing in at least one axis.

#### Scenario: The library generates distinct town blocks
- **WHEN** the town-block library is generated with defaults
- **THEN** it SHALL emit multiple town-block instances
- **AND** each instance SHALL differ from the others in at least one variant axis.

### Requirement: Cultivation sect terraced axial layout
The compound layer SHALL provide a `cultivation_sect` layout strategy that arranges sect-roster sub-buildings along a central axis at monumental scale across three or more stacked terrace levels. The axis SHALL ascend from the mountain gate on the lowest terrace to the principal hall on the highest terrace. Each level SHALL form its own courtyard with building slots, and adjacent levels SHALL be connected by monumental stairway circulation. The sect strategy SHALL reuse the existing `CompoundGraph` parcel machinery and per-building pass pipeline. Importance grading by terrace level, terrain siting context, and covered-gallery/flying-bridge link circulation are specified by the cultivation-mountain-siting capability and are not restated here.

#### Scenario: A sect compound is generated
- **WHEN** a sect compound is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` whose building slots are filled by sect-roster sub-buildings
- **AND** the slots SHALL be arranged along a central axis from the gate at the lowest level to the principal hall at the highest
- **AND** the slots SHALL be distributed across three or more terrace levels
- **AND** each terrace level SHALL have a courtyard parcel node.

#### Scenario: The sect layout is distinct from the courtyard layout
- **WHEN** the sect layout strategy is selected
- **THEN** it SHALL be selected via the settlement group's layout binding
- **AND** it SHALL NOT be produced by the existing one-courtyard `chinese_courtyard` layout.

#### Scenario: A terraced sect compound connects levels
- **WHEN** a sect compound places building slots on more than one platform level
- **THEN** monumental stairway circulation SHALL connect each lower level to the next higher level
- **AND** no building footprint SHALL overlap water or planting cells.

### Requirement: Compound variant axes are plan-level
The `CompoundVariant` dataclass SHALL carry `layout_type`, `main_orientation`, `main_bays`, `roof_grade`, `platform_tier`, and `gate_type` as first-class fields. The `layout_type` field SHALL determine the count and placement of major buildings (e.g. 三合院 omits `front_row`); `main_orientation` SHALL determine which lot edge the street gate sits on; `main_bays` SHALL determine the main hall's footprint width and interior bay count (明间 / 次间 / 梢间 / 尽间 zones); `platform_tier` SHALL determine the main yard's plinth height (0, 2, or 3 cells); `gate_type` SHALL determine the street gate's form (gateway-through-building, flush-with-wall, or set-into-front-columns).

#### Scenario: layout_type drives the plan
- **WHEN** two compounds are generated with the same seed and orientation but different `layout_type` values
- **THEN** the two compounds SHALL differ in the count or placement of major buildings.

#### Scenario: main_bays drives the main hall width
- **WHEN** two compounds are generated with the same seed but different `main_bays` values
- **THEN** the main hall footprint widths SHALL differ
- **AND** the main hall interior bay count SHALL match the `main_bays` value.

#### Scenario: platform_tier drives the main yard plinth
- **WHEN** two compounds are generated with the same seed but different `platform_tier` values
- **THEN** the main yard plinth heights SHALL differ
- **AND** a compound with `platform_tier="xumi_3"` SHALL sit on a 3-cell-tall 须弥座-style plinth.

### Requirement: 影壁 parcel node is placed inside the street gate
The compound layer SHALL provide a 影壁 (screen wall) parcel node type: a free-standing wall of at least 6 cells of height placed on the central axis inside the street gate, with a cap ridge and at least one structural cell of footprint. The 影壁 SHALL be the first parcel node placed in the outer yard and SHALL block the sightline from the street gate to the 垂花门.

#### Scenario: 影壁 stands inside the gate
- **WHEN** the outer yard is generated
- **THEN** a `screen_wall` parcel node SHALL be placed on the central axis between the street gate and the 垂花门
- **AND** the screen wall SHALL be at least 6 cells tall.

### Requirement: 垂花门 parcel node separates yards
The compound layer SHALL provide a 垂花门 (inner gate) parcel node type: an independent roofed gate-house structure on the central axis separating the outer yard from the main yard. The 垂花门 SHALL carry the signature 垂莲柱 (hanging-lotus-column) detail at the inner eave corners. A compound SHALL NOT have more than one 垂花门.

#### Scenario: 垂花门 stands between the two yards
- **WHEN** the layout is generated
- **THEN** a `inner_gate` parcel node SHALL be placed on the central axis
- **AND** the parcel node SHALL have its z position between the outer yard band and the main yard band.

### Requirement: 月台 parcel node raises the main hall approach
The compound layer SHALL provide a 月台 (moon platform) parcel node type: a raised stone apron placed immediately in front of the `main_hall`, between the main hall footprint and the main yard interior. The 月台 SHALL be raised at least one cell above the main yard ground and SHALL be sized to a multiple of the main hall's bay count.

#### Scenario: 月台 fronts the main hall
- **WHEN** the main yard is generated
- **THEN** a `moon_platform` parcel node SHALL be placed adjacent to the `main_hall`'s entry face
- **AND** the 月台 SHALL be raised above the main yard ground.

### Requirement: 抄手游廊 parcel node is a covered gallery
The compound layer SHALL provide a `covered_gallery` parcel node type: a roofed corridor at least 3 cells wide and 3 cells tall with standoff columns at the inner edge. The 抄手游廊 SHALL connect the 垂花门's two flanks to the `main_hall`'s two flanks along the east and west edges of the main yard. Each compound SHALL have exactly two 抄手游廊 parcel nodes (one east, one west).

#### Scenario: Two 抄手游廊 per compound
- **WHEN** the layout is generated
- **THEN** exactly two `covered_gallery` parcel nodes SHALL be placed
- **AND** one SHALL run along the east edge of the main yard and one along the west edge.

### Requirement: Compound courtyard dressing carries a central tree
The compound layer SHALL place a 院中树 (courtyard tree) in the main yard, offset from the central axis so it does not block the sightline from the 垂花门 to the main hall. The compound MAY additionally place 鱼缸 / 石榴缸 (water jars) at the main yard corners. The tree SHALL be a large deciduous or fruit-bearing variant (e.g. 枣 / 槐 / 石榴) and SHALL NOT be placed on water, planting, 月台, 抄手游廊, or building cells.

#### Scenario: A central tree stands in the main yard
- **WHEN** the main yard is generated
- **THEN** a `courtyard_tree` parcel node SHALL be placed in the main yard interior
- **AND** the tree cells SHALL NOT overlap any building, 月台, gallery, water, or planting cell.

### Requirement: Perimeter wall has a cap and end piers
The `perimeter_wall` parcel node SHALL carry a cap ridge (a row of cap blocks running along the top of the wall, distinct from a single-slab cap) and SHALL place 墙垛 (wall piers) at each external corner and at regular intervals along long runs. The wall MAY additionally carry 漏窗 (lattice window) cutouts at configurable intervals. The perimeter wall SHALL extend down to the original ground even where it meets a raised main-yard platform (no floating wall cells).

#### Scenario: The wall has a cap ridge
- **WHEN** the perimeter wall is generated
- **THEN** the top of the wall SHALL carry a cap ridge of cap-blocks (not a single slab layer)
- **AND** 墙垛 piers SHALL be placed at each external corner.

#### Scenario: The wall does not float where it meets the platform
- **WHEN** the main yard sits on a raised platform and the perimeter wall meets it
- **THEN** the perimeter wall SHALL extend down to the original ground
- **AND** no perimeter wall cell SHALL have air below it.
