## Purpose

This is a **delta** to the `validation` capability spec (baseline at
`openspec/specs/validation/spec.md`). It adds the enclosure-model invariants that
`validate_mansion` SHALL enforce for `chinese_mansion`, and notes that the
band-coupled `validate_compound` checks remain in force for the (unchanged)
`chinese_courtyard` family.

## ADDED Requirements

### Requirement: Mansion validation enforces enclosure-model invariants

`validate_mansion` SHALL, for `chinese_mansion` compounds, enforce the enclosure-
model invariants in addition to the grid-only checks it already performs
(perimeter floats, ground-layer holes, voxel-walkability, silhouette). The
enclosure invariants SHALL be:

- A `gate_house` building slot is present and its footprint straddles the south
  perimeter line (the entrance is a through-building, per `mansion-gate-house`).
- Every `building_slots` entry's facing (recorded in its slot meta) matches its
  role's form-rule facing per `building-orientation-variants` (正房→south,
  倒座→north, 西厢→east, 东厢→west, gate_house→inward).
- Every door-cell (`door_info["front"]`) is on a path cell (path-as-input
  guarantee, per `compound-enclosure-planning`).
- The 进 sequence is well-formed: 仪门 borders 前院 and 主院; 二门 borders 主院 and
  后院 — verified by derived-yard adjacency, NOT by z-band tuple comparison.

`validate_mansion` SHALL NOT use z-band tuple comparison (`meta["outer_yard_band"]`
etc.) to assert any enclosure invariant. The band-coupled checks SHALL remain in
`validate_compound` for the `chinese_courtyard` family (unchanged this turn).

#### Scenario: A mansion with a hole-in-the-wall gate fails validation

- **WHEN** `validate_mansion` runs on a compound whose south entrance is a carved
  air hole rather than a `gate_house`
- **THEN** validation SHALL fail with a `gate_house_missing` error.

#### Scenario: A mansion with a south-facing 倒座 fails validation

- **WHEN** `validate_mansion` runs on a compound whose `front_row` slot records
  `facing=south` (door onto the street)
- **THEN** validation SHALL fail with an `enclosure_facing_violation:front_row`
  error.

#### Scenario: A mansion with an unreachable door fails validation

- **WHEN** `validate_mansion` runs on a compound where some `door_info["front"]`
  cell is not on a path cell
- **THEN** validation SHALL fail with a `door_off_path:<slot_id>` error.

#### Scenario: A well-formed mansion passes all enclosure invariants

- **WHEN** `validate_mansion` runs on a realized enclosure-plan mansion
- **THEN** no `gate_house_missing`, `enclosure_facing_violation`,
  `door_off_path`, or `voxel_*` error SHALL fire
- **AND** the report SHALL record a `facing_per_slot` map and a
  `door_reachable_rate` of 1.0.

## MODIFIED Requirements

### Requirement: The `chinese_mansion` library is validated as a 6-NBT group with spread ≥ 15

The generation pipeline SHALL produce 6 `chinese_mansion_001..006.nbt` files
validated by `validate_mansion`. The compound library check SHALL confirm:
(a) 6 distinct variant keys, (b) silhouette score spread ≥ 15, (c) every NBT
passes the enclosure-model invariants (gate_house present, form-rule facings,
every door on path). Mansion validation SHALL use `validate_mansion`, not
`validate_compound`.

> **Modified** from baseline: the mansion library check now also asserts the
> enclosure invariants per slot across all 6 NBTs, and records the new
> `facing_per_slot` / `door_reachable_rate` stats. The silhouette-spread ≥ 15
> rule and the 6-distinct-variant rule are unchanged.

#### Scenario: chinese_mansion library is generated under the enclosure model

- **WHEN** `generate_compound_library.py --group chinese_mansion --count 6` is run
- **THEN** it SHALL produce 6 NBT files, a gallery function, and a report
- **AND** the report SHALL record `passed: true`, `distinct_variants: 6`,
  `silhouette_spread >= 15`, and `door_reachable_rate: 1.0` for every NBT
- **AND** every NBT SHALL have a `facing_per_slot` map matching the form rule.

## RETAINED (unchanged)

All other baseline `validation` requirements remain in force, including the
voxel-walkability error codes (`voxel_unreachable_door`, `voxel_unreachable_endpoint`,
`voxel_step_cliff`, `voxel_blocked_by_solid`) which `validate_mansion` continues
to emit. The `validate_compound` band-coupled checks (screen-wall-off-axis,
inner-gate ordering, covered-gallery, moon-platform) are **unchanged** and
continue to govern the `chinese_courtyard` family.
