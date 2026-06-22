## Purpose

**Status: design retention, not implemented.** This spec captures the form vocabulary of 徽派天井大屋 (Huizhou-style tianjing mansion) so a future implementation change does not redesign from scratch. All requirements are `FUTURE:`-prefixed; NO current validator covers them; NO code or NBT is implemented in `rebuild-jiangnan-mansion` or any current change. This spec exists because the form was explored in detail during the `rebuild-jiangnan-mansion` discovery (the 江南 vs 徽派 草图 comparison), and losing it would force a future change to redo that exploration.

The form is captured in contrast to 江南大宅 (see `chinese-mansion-compound`). Where 江南 is open, bright, and water-garden-centered, 徽派 is closed, vertical, and tianjing-centered. A future `rebuild-huipai-mansion` change SHALL realize the form below.

## Requirements

### Requirement (FUTURE): 徽派大屋 is a 堂—井—堂 vertical sequence, not a 院 sequence

A `chinese_huipai_mansion` compound (future family) SHALL be laid out as a vertical sequence of 堂 (halls) separated by 天井 (small sky-wells), NOT a horizontal sequence of yards separated by inner gates. The canonical sequence is: 门堂 (entry hall) → 天井一 → 享堂 (mid hall, family-ceremonial) → 天井二 → 寝堂 (rear hall, ancestors / private). Each 天井 SHALL be small (1×2 to 3×4 cells), deep, with roof-water collection (四水归堂). Each 堂 SHALL be larger than the 天井 and SHALL carry the principal function (entry / family / ancestors).

#### Scenario (FUTURE): A 3-堂 徽派大屋 has 2 天井

- **FUTURE: WHEN** a `chinese_huipai_mansion` compound is generated with 3 堂
- **FUTURE: THEN** the layout SHALL have 门堂 → 天井一 → 享堂 → 天井二 → 寝堂
- **FUTURE: AND** each 天井 SHALL be ≤ 3×4 cells.

### Requirement (FUTURE): 天井 is a small roofed-well, NOT an open yard

A 天井 parcel SHALL be a small enclosed open-sky shaft in the middle of the building, surrounded on four sides by 堂 / 廊庑 (covered corridors). The 天井 SHALL be ≤ 4 cells in any horizontal dimension. The 堂 and 廊庑 roofs SHALL slope inward toward the 天井 so rainwater collects in the 天井 (四水归堂). The 天井 floor SHALL be stone-paved with a drain (the 四水归堂 collection point).

#### Scenario (FUTURE): 天井 is small and surrounded

- **FUTURE: WHEN** a 天井 parcel is placed
- **FUTURE: THEN** all four sides of the 天井 SHALL be bounded by 堂 or 廊庑
- **FUTURE: AND** the 天井 horizontal dimensions SHALL be ≤ 4 cells each.

#### Scenario (FUTURE): Roof-water collects in the 天井

- **FUTURE: WHEN** the 堂 and 廊庑 roofs are generated
- **FUTURE: THEN** the roof slopes SHALL pitch inward toward the 天井
- **FUTURE: AND** the 天井 floor SHALL have a drain block at its center.

### Requirement (FUTURE): 堂 sequence carries progressive privacy (entry → family → ancestors)

The three 堂 SHALL carry progressive privacy: 门堂 (entry, public, greeting / 轿), 享堂 (mid, family-ceremonial, 红白事 / 议事), 寝堂 (rear, private, ancestors / 长辈起居). The 寝堂 SHALL be the tallest (highest roof ridge), carrying the principal family-altar function. The 寝堂 MAY be 2 stories (楼上住人, 楼下厅堂) per `multi-story-massing`.

#### Scenario (FUTURE): 寝堂 is the tallest 堂

- **FUTURE: WHEN** the three 堂 are generated
- **FUTURE: THEN** the 寝堂 roof ridge SHALL be higher than the 享堂 and 门堂 roof ridges
- **FUTURE: AND** the 寝堂 MAY be `stories=2` while 门堂 and 享堂 are `stories=1`.

### Requirement (FUTURE): 马头墙 (stepped gable) is the perimeter wall form

The perimeter wall SHALL be a 马头墙 — a stepped gable (阶梯式封火墙) rising above the roof line at the building's side walls. The 马头墙 SHALL step up in 2-3 stages (鞍马头 / 印斗状 / 鹰扬式 variants) and SHALL extend the full perimeter. The 马头墙 SHALL be higher than the surrounding roofs, providing fire-break between adjacent buildings (the historical function).

#### Scenario (FUTURE): The perimeter wall is a 马头墙

- **FUTURE: WHEN** the perimeter wall is generated for a `chinese_huipai_mansion`
- **FUTURE: THEN** the wall SHALL step up in 2-3 stages above the roof line
- **FUTURE: AND** the wall SHALL extend the full perimeter at uniform stepped form.

### Requirement (FUTURE): 徽派大屋 is closed and vertical, contrasted with 江南 open and horizontal

The 徽派大屋 SHALL present a closed, fortress-like exterior (high solid walls, few or no windows on the street side) and an inward-facing, vertically-stacked interior (天井 light wells, 2-3 story 堂). This is the explicit contrast with `chinese_mansion` (江南: open 敞厅, low 楼阁 count, horizontal 花园 extension). The 徽派 SHALL NOT have a 花园 parcel; the 天井 IS the outdoor space.

#### Scenario (FUTURE): The street facade is closed

- **FUTURE: WHEN** the street-facing perimeter of a 徽派大屋 is generated
- **FUTURE: THEN** the facade SHALL be high solid wall (马头墙) with few or no windows
- **FUTURE: AND** the only opening SHALL be the entry door.

### Requirement (FUTURE): 徽派大屋 is voxel-walkable per `courtyard-voxel-walkability`

A future `chinese_huipai_mansion` compound SHALL pass the `courtyard-voxel-walkability` checks. The 堂 ↔ 天井 transitions SHALL be step-able (the 堂 floor and 天井 floor SHALL be at the same y, or bridged by a single step). The 2-story 寝堂 SHALL have a walkable stairwell to the upper floor.

#### Scenario (FUTURE): 堂 ↔ 天井 is step-able

- **FUTURE: WHEN** the validator inspects a 堂 to 天井 boundary
- **FUTURE: THEN** the surface y of the 堂 floor and the 天井 floor SHALL differ by ≤ 1
- **FUTURE: OR** a `stone_brick_stairs` SHALL bridge the boundary.

### Requirement (FUTURE): 徽派大屋 implementation requires a new 天井 parcel model

Implementing 徽派 SHALL require a new 天井 parcel model that does NOT generalize from the existing 院子 (yard) model. Specifically:
- The 院子 model assumes an open-sky, large horizontal yard band with ground fill + path network per `courtyard-ground-layer` / `courtyard-path-network`.
- The 天井 model requires a small enclosed shaft with 四水归堂 roof-water collection, surrounded on four sides by 廊庑 or 堂, with NO ground fill or path network (the 天井 IS the floor).
- `_place_yard_ground` and `_route_complete_path` SHALL be re-derived for 天井 (they assume open-sky yard cells; 天井 cells are roofed shaft cells).

This requirement is captured as a design hazard for the future implementer: 徽派 is NOT a configuration of `chinese_mansion`, it is a distinct compound family with its own ground/path model.

#### Scenario (FUTURE): The 天井 model is distinct from 院子

- **FUTURE: WHEN** a future change implements `chinese_huipai_mansion`
- **FUTURE: THEN** the change SHALL add a 天井 parcel model (NOT reuse `_place_yard_ground` / `_route_complete_path` for 天井 cells)
- **FUTURE: AND** the change SHALL document the 天井 ground/path derivation in its design.

## Implementation Notes (non-normative)

- The 徽派 form was sketched during `rebuild-jiangnan-mansion` exploration (歙县民居 / 潜口民宅 as prototypes). The sketch lives in this change's design discussion history; future implementers should consult that discussion for the form's lived-in feel (closed / vertical / mysterious).
- A future `rebuild-huipai-mansion` change SHALL: (1) add the `chinese_huipai_mansion` family in `tools/buildgen/groups.py`, (2) implement the 天井 parcel model, (3) implement the 堂 sequence with progressive privacy, (4) implement the 马头墙 perimeter, (5) ship 6 `chinese_huipai_mansion_001..006.nbt`, (6) reuse `courtyard-voxel-walkability`, `mod-decor-block-family`, `chinese-vernacular-roof-vocabulary`.
- The 徽派 family is INCOMPATIBLE with the 花园 parcel (no garden in 徽派 — the 天井 IS the outdoor space). A future variant axis `has_garden: false` SHALL be the default for 徽派.
