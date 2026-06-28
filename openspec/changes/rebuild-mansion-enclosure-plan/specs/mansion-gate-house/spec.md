## Purpose

Defines the **south entrance as a `gate_house` through-building**, replacing the
previously-used "carve a hole in the perimeter wall" gate. The gate-house is a
real building volume with a 门楼 roof, 门框, and a passage the player walks
through — producing the gate feel that the hole-in-the-wall could not.

## Requirements

### Requirement: The south entrance is a gate_house sub-building straddling the perimeter

The `chinese_mansion` south entrance SHALL be realized as a `gate_house`
sub-building placed so its south wall is on the perimeter line and its body
projects inward into the 前院. The gate_house SHALL be built by the existing
`build_gate_house` archetype builder. The entrance SHALL NOT be a row of carved
air cells in the perimeter wall.

#### Scenario: A gate_house occupies the south perimeter at the axis

- **WHEN** a `chinese_mansion` compound is generated
- **THEN** a `gate_house` building slot SHALL be present
- **AND** the gate_house footprint SHALL straddle the south perimeter line
  (z=0 band) centered on the axis
- **AND** no carved-air gate opening SHALL be the sole entrance.

### Requirement: The player walks through the gate_house, not past a hole

The gate_house SHALL carry a street-facing door on its south wall (the
`_south`-class facing) and SHALL open onto the 前院 on its north wall (the
passage). The player SHALL walk through the gate_house — under its 门楼 roof and
past its 门框 — to enter the compound. The passage SHALL be voxel-walkable.

#### Scenario: The gate-house passage is walkable end-to-end

- **WHEN** the voxel-walkability BFS runs from outside the gate_house
- **THEN** the player SHALL reach the 前院 by passing through the gate_house
- **AND** the path SHALL go under the gate_house roof (not around an open hole).

### Requirement: The perimeter walls around the gate_house with no gap

The perimeter wall SHALL be built with a gap exactly matching the gate_house
footprint, and the gate_house's own side walls SHALL close that gap, so the
perimeter stays sealed except through the gate_house passage. The compound SHALL
NOT leak to the outside except via the gate_house.

#### Scenario: The perimeter is sealed around the gate_house

- **WHEN** the realized layout is examined
- **THEN** every south-perimeter cell not under the gate_house SHALL be wall
- **AND** the gate_house side walls SHALL span the gap
- **AND** `validate_mansion`'s perimeter-integrity check SHALL pass.

### Requirement: gate_type selects the gate_house footprint and roof grade

The `gate_type` (manzi / jinzhu / guangliang, derived from the variant's
`gate_form`) SHALL select the gate_house footprint (from `SCALE_TIERS["gate_house"]`)
and roof grade, rather than selecting the width of a carved hole. The gate_house
SHALL be centered on the axis regardless of gate_type.

#### Scenario: A guangliang (广亮门) gate uses the largest footprint

- **WHEN** the variant's `gate_form` resolves to `guangliang`
- **THEN** the gate_house SHALL use the largest `gate_house` footprint
- **AND** the gate_house SHALL be centered on the axis
- **AND** no carved-hole width SHALL be derived from `gate_form`.

### Requirement: The gate_house faces inward to begin the 前院 enclosure

The gate_house's facing SHALL be recorded as inward (its passage opens onto the
前院), making it the first element of the 前院 enclosure. The 照壁 SHALL stand
off-axis inside the 前院, completing the entry sequence (街 → 门屋 → 照壁 → 前院),
per the existing `courtyard-compound` 照壁侧立 rule.

#### Scenario: The entry sequence reads 街 → 门屋 → 照壁 → 前院

- **WHEN** the realized layout is examined
- **THEN** the gate_house SHALL be the southernmost building
- **AND** the 照壁 SHALL stand off-axis inside the 前院, behind the gate_house
- **AND** the sightline from the gate_house passage SHALL intersect the 照壁.
