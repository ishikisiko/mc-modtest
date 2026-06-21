## MODIFIED Requirements

### Requirement: Chinese one-courtyard axial layout
A Chinese courtyard compound SHALL be laid out along a central axis as two yards separated by exactly one inner gate (垂花门): an outer yard (外院) on the street side and a main yard (主院) on the inward side. The outer yard SHALL contain a 影壁 (screen wall) inside the street gate blocking the direct sightline to the main yard, and MAY contain `front_row` (倒座) buildings. The main yard SHALL contain exactly two `side_wing` buildings (east and west), a `main_hall` on the central axis at the inward end, and a 月台 (moon platform) apron between the main hall and the yard. All buildings and yards SHALL be enclosed by a four-sided `perimeter_wall`. A compound SHALL NOT have more than one 垂花门 (multi-进 layouts are out of scope for this requirement; the `jin_count` master axis is deferred per `docs/ai-kb/14_deferred_roadmap.md` §E).

#### Scenario: The two yards are placed with one inner gate between them
- **WHEN** the one-courtyard layout is generated with `layout_type="standard"`
- **THEN** the layout SHALL place an outer yard and a main yard as two z-band regions of the same `CompoundGraph`
- **AND** exactly one 垂花门 parcel node SHALL be placed between the two yard bands
- **AND** the outer yard band SHALL be closer to the street gate than the main yard band.

#### Scenario: The 影壁 blocks the direct sightline
- **WHEN** the outer yard is generated
- **THEN** a 影壁 parcel node SHALL be placed inside the street gate, between the gate and the 垂花门
- **AND** the 影壁 cells SHALL lie on the central axis so a sightline from the street gate to the main hall SHALL intersect the 影壁.

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

## ADDED Requirements

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
