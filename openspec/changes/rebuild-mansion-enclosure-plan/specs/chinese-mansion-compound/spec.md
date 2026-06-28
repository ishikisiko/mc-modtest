## Purpose

This is a **delta** to the `chinese-mansion-compound` capability spec. The
capability is currently defined (but not yet archived to baseline) by the
`rebuild-jiangnan-mansion` change at
`openspec/changes/rebuild-jiangnan-mansion/specs/chinese-mansion-compound/spec.md`.
This change supersedes that spec's **layout requirements** (the z-band-slice
model) with the **enclosure-planning model**, while keeping its variant-table,
进-sequence, and garden requirements.

Read alongside: `compound-enclosure-planning` (the new model),
`building-orientation-variants` (the facing system), `mansion-gate-house` (the
entrance).

## MODIFIED Requirements

### Requirement: ~~A 江南大宅 is a multi-进 z-band sequence on a central axis~~ → A 江南大宅 is a multi-进 enclosure sequence on a central axis

> **Supersedes** the `rebuild-jiangnan-mansion` requirement "A 江南大宅 is a
> multi-进 z-band sequence on a central axis". The `jin_count` axis and the
> canonical 进 sequence are retained; the z-band-slice mechanism is replaced by
> the enclosure model.

A `chinese_mansion` compound SHALL be laid out along a central axis as an
ordered sequence of 进 (yards), each realized as the **enclosed negative space**
of buildings placed against the perimeter with form-rule facings (per
`compound-enclosure-planning` + `building-orientation-variants`), separated by
inner gates, ending in a 花园 band. The number of 进 SHALL be controlled by
`CompoundVariant.jin_count ∈ {3, 4}`; the shipped library SHALL use `jin_count=3`.
The 花园 band SHALL open directly off the 后院 with no inner gate between them.

**REMOVED** (from the superseded requirement): the z-band-depth requirements
("each yard band SHALL be at least 8 cells deep", "each inner gate band SHALL be
exactly 3 cells deep"). Yard depth is now *derived* from the building enclosure,
not a pre-cut band parameter. The 3-cell inner-gate passage width is retained
(via the inner-gate passage requirement below).

#### Scenario: A 3-进 mansion has the canonical enclosure sequence

- **WHEN** a `chinese_mansion` compound is generated with `jin_count=3`
- **THEN** the realized layout SHALL produce the ordered 进: 前院 → 仪门 → 主院 → 二门 → 后院 → 花园
- **AND** each 进 SHALL be the enclosed negative space of its facing-buildings
- **AND** no z-band tuple comparison SHALL be used to assert the sequence.

### Requirement: ~~The 照壁 stands off-axis~~ → The 照壁 stands off-axis inside the 前院 enclosure (retained, re-anchored)

> **Retained** from `rebuild-jiangnan-mansion`; the 照壁 placement is re-anchored
> to the enclosure model: it stands off-axis inside the 前院 (the negative space
> enclosed by the gate_house + 倒座), not at a band-relative z-offset.

A `chinese_mansion` compound SHALL place a 照壁 (screen wall) parcel node **off
the central axis**, inside the 前院 enclosure, such that the sightline from the
gate-house passage to the 主院 axis is blocked at an oblique angle. The 照壁
SHALL NOT occupy any cell on the central axis. The 照壁 SHALL be a free-standing
panel (1-2 cells wide, 5-6 cells tall) with a cap ridge, distinct from the
perimeter wall.

#### Scenario: The 照壁 never blocks the central axis

- **WHEN** the 照壁 parcel is placed
- **THEN** no cell of the 照壁 SHALL have `x == axis_x`
- **AND** the central-axis column from the gate-house inward SHALL remain voxel-walkable.

## REMOVED Requirements

> The following `rebuild-jiangnan-mansion` requirements are **removed** because
> they encode the z-band-slice mechanism that the enclosure model replaces.

### Requirement (REMOVED): The 敞厅 ... on the central axis at the inward end

> **Removed as a layout requirement.** The 敞厅's *existence* and *open facade*
> are retained (it is the 主院's principal building, anchor north, facing south,
> `FACADE_OPEN` slot). What is removed is the z-band-positioning claim
> ("at the inward end", "main yard band"). Under the enclosure model the 敞厅
> anchors the north wall of the 主院 enclosure by construction; no band
> coordinate names its position.

The 敞厅's facade contract (open front, columns, no full-height wall) is governed
by `building-orientation-variants` (敞厅 exemption) and `cultivation-form-vocabulary`
(`FACADE_OPEN`), unchanged.

### Requirement (REMOVED): Inner gates open at least 3 cells for passage (band form)

> **Replaced** by the enclosure-model equivalent below. The 3-cell-passage intent
> is retained; the band-relative "for every z in the inner-gate band" phrasing is
> replaced by "at the adjacency boundary between two consecutive yards".

## ADDED Requirements

### Requirement: Inner gates sit at the adjacency boundary between consecutive yards

Each inner gate (仪门 between 前院 and 主院; 二门 between 主院 and 后院) SHALL be placed
at the adjacency boundary between its two enclosing yards, opening at least 3
cells for passage (the central axis cell plus one cell on each side). The gate's
position SHALL be derived from the realized enclosure, not from a z-band tuple.

#### Scenario: 仪门 borders 前院 and 主院

- **WHEN** the realized layout is examined
- **THEN** the 仪门 SHALL border the 前院 enclosed space on one side and the 主院
  enclosed space on the other
- **AND** the 仪门 passage SHALL contain at least the cells `{(axis_x-1, z),
  (axis_x, z), (axis_x+1, z)}` for the relevant z.

### Requirement: Every mansion building faces its yard per the form rule

Every building in a `chinese_mansion` compound SHALL face the yard it encloses
per the form rule in `building-orientation-variants`: 正房/open_hall face south;
倒座 faces north; 西厢 faces east; 东厢 faces west; the gate_house faces inward
(toward 前院); 楼阁 faces its enclosing yard. No building's door SHALL open onto
the street or away from its yard.

#### Scenario: The 倒座 faces the 前院, not the street

- **WHEN** the realized layout is examined
- **THEN** the 倒座's door SHALL be on its north (high-z) wall, facing the 前院
- **AND** the 倒座's door SHALL NOT open onto the street (south).

## RETAINED Requirements (from rebuild-jiangnan-mansion, unchanged)

The following requirements from the `rebuild-jiangnan-mansion`
`chinese-mansion-compound` spec are **retained verbatim** and are not restated
here; they remain authoritative:

- "The 后院 SHALL contain at least one 楼阁 (tower house)" — 楼阁 with `stories=2`,
  off-axis, `tower_count ∈ {1, 2}`. (Positioning now via enclosure manifest; the
  `stories`/`tower_count` contract unchanged.)
- "The 花园 is a non-axis parcel zone behind the 后院" — pond + rockery + pavilion
  + 曲径. (Garden realization unchanged by this change.)
- "倒座 leaves a side alley" — now a consequence of the enclosure manifest's
  offset logic rather than a band-relative rule; intent retained.
- "江南大宅 variants are combinatorial" — the 6-row template table is retained;
  facing is **not** a template axis (per `building-orientation-variants` form rule).
- "The 江南大宅 SHALL pass voxel-walkability end-to-end" — retained; now the
  acceptance gate for the enclosure realization (per `compound-enclosure-planning`).
- "(FUTURE EXTENSION) 4-进" — retained, still deferred.
