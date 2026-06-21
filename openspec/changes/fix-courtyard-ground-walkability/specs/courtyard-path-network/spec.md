## Purpose

This spec defines the path network of a courtyard compound: how a walkable gravel path connects the street gate to every door and every landscape feature in the courtyard, and how the path crosses the main-yard plinth boundary as a single step.

The path is a single-block-deep overlay written on top of the ground tile (defined by `courtyard-ground-layer`) at the same y.

## Requirements

### Requirement: Path endpoints cover every reachable goal

A courtyard compound SHALL define a path endpoint at every reachable goal in the courtyard. The endpoint set SHALL include:

1. The street-gate entry cell — the first cell inside the perimeter wall at the gate opening.
2. One endpoint per `BuildingSlot` whose `door_info` is populated, at the cell `door_info["front"]`.
3. One endpoint per `water_feature` parcel node, at every cell in the water feature's `cells`.
4. One endpoint per `water_jar` parcel node, at every cell in the water jar's `cells`.
5. One endpoint per `planting` parcel node, at a single cell adjacent to the planting boundary (the cell whose 4-neighbor count in non-blocked non-planting cells is highest; deterministic tie-break by `(x, z)` lex order).
6. The front-most cell of `moon_platform` — the cell of the moon platform closest to the gate (min z if `gate_side == "south"`, max z if `gate_side == "north"`).

The endpoint collection is layout-agnostic: a small-courtyard unit without a moon platform simply has no moon-platform endpoint; the requirement is "every reachable goal in *this* compound".

#### Scenario: Every door is an endpoint

- **WHEN** a courtyard compound places a `BuildingSlot` whose `door_info` is populated
- **THEN** the cell `door_info["front"]` SHALL be in the path endpoint set.

#### Scenario: Every water feature is an endpoint

- **WHEN** a courtyard compound places a `water_feature` parcel node
- **THEN** every cell in the water feature's `cells` SHALL be in the path endpoint set.

#### Scenario: Every planting bed has one entry endpoint

- **WHEN** a courtyard compound places a `planting` parcel node
- **THEN** exactly one cell adjacent to the planting's `cells` SHALL be in the path endpoint set.

### Requirement: Multi-source BFS produces a connected path tree

The compound layer SHALL run a multi-source BFS starting from every endpoint simultaneously. The BFS SHALL expand 4-neighbor cells, treating the following cells as `blocked`:
- All cells in `BuildingSlot.footprint` for every slot, except the cell `door_info["front"]` itself (so the BFS can stop one cell short of the door).
- All cells in `water_feature.cells`, `water_jar.cells`, `planting.cells`, `courtyard_tree.cells`.
- All cells in `perimeter_wall.cells`, except the gate-opening cells.
- All cells in `inner_gate.cells`, except the cells in `inner_gate.meta.passage`.

The BFS SHALL be run on the lot interior `(1 ≤ x ≤ lot_w - 2, 1 ≤ z ≤ lot_d - 2)`. The result SHALL be a `dict[Cell2, int]` mapping every reached cell to its distance from the nearest endpoint. Every endpoint SHALL be reached (otherwise the validator fails with `endpoint_unreachable:<cell>`).

#### Scenario: Every endpoint is reached

- **WHEN** the multi-source BFS runs
- **THEN** every endpoint SHALL have a finite distance in the result.

#### Scenario: The path network is connected

- **WHEN** the multi-source BFS runs
- **THEN** for every pair of endpoints `(a, b)`, there SHALL exist a sequence of 4-neighbor cells from `a` to `b` where every intermediate cell is reached by the BFS.

### Requirement: Path block is written at the natural surface y

The path block SHALL be written at the cell's natural surface y (the same y used by `_place_yard_ground`):
- `y = -1` in the outer yard band.
- `y = -1` in the inner gate band.
- `y = plinth_h - 1` in the main yard band (on top of the main-yard plinth).

The path block SHALL resolve through the style's `GROUND_PATH` slot. The block SHALL be written with tags `["DETAIL", "GROUND", "PROTECTED"]` and priority `DETAIL (70)`. The PROTECTED tag prevents the `material_variation` pass from re-coloring the path.

#### Scenario: An outer-yard path cell is gravel at y = -1

- **WHEN** the multi-source BFS reaches an outer-yard cell `(x, z)`
- **THEN** the path pass SHALL write the style's `GROUND_PATH` primary block at compound `(x, -1, z)`.

#### Scenario: A main-yard path cell is gravel on the plinth top

- **WHEN** the multi-source BFS reaches a main-yard cell `(x, z)` and the variant has `platform_tier = "stone_2"` (so `plinth_h = 2`)
- **THEN** the path pass SHALL write the style's `GROUND_PATH` primary block at compound `(x, 1, z)`.

### Requirement: Plinth boundary is bridged by a single stair

The compound layer SHALL place a single `minecraft:stone_brick_stairs[facing=<boundary_normal>, half=bottom]` block at every cell where the path crosses the main-yard plinth boundary. A boundary is any pair of 4-neighbor path cells `(outer, plinth)` such that:
- `outer` is in the outer yard band or the inner gate band; `outer.y = -1`.
- `plinth` is in the main yard band; `plinth.y = plinth_h - 1`.

The stair block SHALL replace the path block at the boundary cell (it sits at the cell's natural surface y, which is `outer.y = -1` in the outer yard or `plinth.y = plinth_h - 1` on the main-yard side; the stair is drawn at the side the player approaches from, i.e. `outer.y = -1`). The stair `facing` SHALL be the direction from the boundary cell toward the plinth cell (so the stair rises into the plinth).

If no boundary pair exists, the stair pass SHALL be a no-op. The stair block SHALL use `minecraft:stone_brick_stairs` from the style's `PLATFORM_STONE` slot fallback (no new slot needed).

#### Scenario: A path crossing from outer yard onto the plinth gets a stair

- **WHEN** a path cell `(x, oy)` at `y = -1` is 4-adjacent to a path cell `(x, py)` at `y = plinth_h - 1`, both on the same x or z
- **THEN** the stair pass SHALL write `minecraft:stone_brick_stairs[facing=toward_py, half=bottom]` at `(x, -1, oy)`.

#### Scenario: A small-courtyard without a plinth gets no stairs

- **WHEN** a courtyard has no main-yard plinth (small-courtyard, or a one-jin variant with `platform_tier = "none"`)
- **THEN** the stair pass SHALL be a no-op.

### Requirement: Path does not overlap building doors

The path block SHALL NOT be written at any cell that is itself a `door_info.front` cell of any `BuildingSlot`. The path MAY be written one cell short of the door; the door cell SHALL be the building's own step block (placed by the building's door pass).

#### Scenario: A door cell is not a path cell

- **WHEN** the path block is being written at cell `(x, y, z)` and `(x, z)` is any `BuildingSlot`'s `door_info["front"]`
- **THEN** the path pass SHALL NOT write a block at `(x, y, z)`
- **AND** the door pass's step block SHALL be the block at `(x, y, z)` instead.

### Requirement: Path network uses vanilla-only block ids

The `GROUND_PATH` slot SHALL be defined on the style JSON with vanilla-only block ids. The vanilla profile SHALL resolve the path to `minecraft:gravel` (or another `minecraft:` entry); the full profile MAY resolve to an external-mod id.

#### Scenario: Vanilla profile resolves the path to gravel

- **WHEN** a `chinese_courtyard` compound is generated with `--profile vanilla`
- **THEN** every path cell SHALL be `minecraft:gravel` (or another `minecraft:` id from the slot).