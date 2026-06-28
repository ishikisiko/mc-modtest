## Context

The defect analysis (see proposal §Why) landed on four interdependent arcs. This
design resolves the *tension between them* and the *tension with the existing
code*, then fixes the mechanism choices that the proposal named but did not pin
down. Reading order: D1 (the deepest mechanism) → D2 (gate) → D3 (enclosure
model) → D4 (path) → D5/D6/D7 (scope/compat/risks).

Three prior facts from the codebase constrain everything below:

1. **NBT export is grid-only** (`export.grid_to_structure_data` reads only
   `compound.grid`; `generate_compound_library.py` passes only `compound.grid`).
   The planning layer can be rewritten wholesale without touching export. This
   is the largest safety margin.
2. **Sub-buildings are hard-south-facing**: `orientation="front"`
   (archetypes.py:575), `_door` writes `wall="front"` (archetypes.py:593),
   `_translate_context` shifts by `(dx,dy,dz)` with no rotation
   (compound.py:391-396). Door-info, roof, facade all assume front = low-z.
3. **The existing wing geometry is *already* courtyard-correct in shape, wrong
   only in door-wall**: `build_side_wing` uses `roof_axis="z"` (ridge
   north-south) and a `7×15` footprint (long axis north-south) — exactly the
   geometry a 厢房 wants. The *only* defect is that `_door` puts the door on the
   `front` (south, short) wall instead of the `west`/`east` (long) wall that
   faces the yard. The main hall is correct because it genuinely faces south.

Fact 3 is decisive: it confirms the user-chosen **mirror + selection** approach
is not merely "cheaper than rotation" — it matches the buildings' intrinsic
geometry. Rotating would disturb an already-correct ridge line; relocating the
door wall is the surgical fix.

## Goals / Non-Goals

**Goals**

- Every mansion building's door faces the yard it serves (正房→south, 倒座→north,
  东厢→west, 西厢→east, gate_house→inward). No "door onto the street".
- The south entrance is a `gate_house` through-building (门楼 + 门框 + passage),
  not a hole in the perimeter wall.
- The layout is building-enclosure-driven: buildings placed as oriented masses,
  yards derived as enclosed negative space. No magic z-band coordinates.
- The gravel path is a declared planning input reaching every door-cell; the
  preserved `_voxel_walk_bfs` is the acceptance gate (any unreachable door =
  rejected plan).
- `chinese_mansion_001..006.nbt` regenerate and read as a real 江南大宅.

**Non-Goals (deferred)**

- Propagating the enclosure + orientation skeleton to `chinese_courtyard` and
  `small_courtyard` — the immediate next change (recorded in deferred roadmap).
- 徽派天井大屋 implementation (still design-retention only).
- 4-进 compounds, 假山 蹬道, additional decor block classes — unchanged.
- Generic 90/180/270° building rotation. Only the **door-wall relocation** needed
  for the form rule is implemented (D1). Full rotation is not required by any
  current form and is left for a future capability.

## Decisions

### D1: Orientation = door-wall relocation (the "variant" mechanism), NOT full rotation

**Choice.** A facing variant is the *same* massing graph with two changes:
(a) the door is placed on the wall that faces the yard, and (b) a `facing` tag
records which wall carries the door. The building's volume, footprint, and roof
ridge are **unchanged** — because for every mansion archetype the existing
footprint + `roof_axis` are already geometrically correct for their slot; only
the door wall was wrong.

Concretely, the variant table per archetype:

| archetype   | variant suffix | door wall | ridge axis | footprint long axis |
|-------------|----------------|-----------|------------|---------------------|
| 正房 main_hall / open_hall | `_south` | front (low-z) | x (E-W) | E-W ✓ unchanged |
| 倒座 front_row / gate_house | `_north` | back (high-z) | x (E-W) | E-W ✓ unchanged |
| 东厢 side_wing (east slot) | `_west` | west (-x) | z (N-S) | N-S ✓ unchanged |
| 西厢 side_wing (west slot) | `_east` | east (+x) | z (N-S) | N-S ✓ unchanged |
| 楼阁 tower_house (back yard, off-axis) | faces the yard it borders | as placed | — | — |

Note: 厢房 and 倒座 need only a door-wall change because their footprints already
run along the perimeter. No new geometry, no rotation, no mirror of the bay
layout — the bays are symmetric about the door anyway.

**Mechanism.** A new `facing` parameter flows through the build chain:

1. `_door(graph, main, rng, wall="front")` gains a `wall` argument; for
   `wall ∈ {west, east, back}` it picks the door position *along the long axis*
   (z for west/east walls, x for back) instead of along x, and records
   `graph.meta["door"] = {"volume", "wall", "pos"}`.
2. `facade_detail_pass` reads `door["wall"]` and calls `ops.doorway(...)` on the
   correct wall. `ops.doorway` already accepts a wall argument (it carves the
   door on whichever wall the facade plan targets); the only change is plumbing
   the chosen wall through.
3. `door_info` already records `{"wall", "along", "y", "front"}`; the `front`
   cell is computed from the door wall's outward direction. For a west-wall door
   the front cell is at `(x0-1, y, door_z)`; for east `(x1+1, ...)`; for back
   `(door_x, y, z1+1)`. This is a small extension of the existing front-cell
   arithmetic.
4. `_translate_context` is **unchanged** for placement (still a pure shift) —
   because the door-wall change is *inside* the sub-building's own grid, baked
   during `generate_subbuilding`. The compound sees a finished, correctly-oriented
   building and just shifts it.

**Why not mirror (x-flip)?** Considered and rejected for the door-wall case: an
x-flip would also mirror the bay asymmetry, the stairwell side, and any
one-sided colonnade — all of which are currently correct. Relocating the door
wall touches *only* the door. (Mirror remains the right tool if a future form
needs a handed variant, e.g. a left-vs-right 跨院; not needed now.)

**Why not rotation?** Rejected (see Goals Non-Goals): no current form needs 90°
rotation of the whole volume, and rotation would force rewriting the
massing/passes coordinate handling for no gain. The form rule needs only
door-wall relocation.

**Alternatives considered**

- *Full rotation (4 facings via 90° turns).* Rejected: disturbs the already-
  correct ridge axis and footprint; large massing/passes rewrite; no form needs it.
- *Mirror (x-flip).* Rejected for the door case (mirrors unrelated asymmetry);
  kept as a tool for future handed-variant forms.
- *Pre-built 4 archetype copies.* Rejected: 4× the archetype code for a one-line
  door-wall difference; the `wall` parameter is the DRY form.

**Rationale.** Fact 3 shows the buildings are geometrically right and only the
door wall is wrong. The minimal, faithful fix is to let the door land on the
yard-facing wall. One parameter (`wall`) carries it through; nothing downstream
of `door_info` cares which wall it was.

### D2: The south entrance is a `gate_house` through-building, not a wall hole

**Choice.** Replace the `_add_chinese_perimeter` "carve a hole" gate with a real
`gate_house` sub-building placed *straddling* the south perimeter line:

- The `gate_house` (builder `build_gate_house` already exists, footprint 9×5 /
  11×5 / 11×7) sits so its south wall is on the perimeter line and its body
  projects inward into the 前院.
- Its south wall carries the street-facing door (`_south`-class facing); its
  north wall opens onto the 前院 (the passage). The player walks *through* the
  building — under its 门楼 roof, past 门框 — instead of through a hole.
- The perimeter wall is built *with a gap* where the gate_house sits (the
  gate_house's own side walls close the gap), so the perimeter stays sealed
  except through the gate_house.
- `gate_type` (manzi/jinzhu/guangliang) now selects the gate_house footprint +
  roof grade instead of the hole width.

**Alternatives considered**

- *Keep the hole, add a 门楼 roof over it.* Rejected: a roof over a hole still
  reads as "wall with a canopy"; there is no 门框 / 门枕石 / interior volume. The
  user's complaint is precisely "no gate feel" — a roof patch doesn't fix it.
- *Free-standing gatehouse in front of the wall (街门).* Rejected for this
  change: places a building outside the lot perimeter, complicating worldgen
  placement. The through-building keeps the entrance inside the lot.
- *Two separate doors (street door + yard door) as two carved openings.*
  Rejected: still holes, not a volume.

**Rationale.** A through-building is the smallest change that produces an actual
gate volume with roof, frame, and passage. `build_gate_house` already produces
the massing; the planning layer just has to use it and let the perimeter wall
gap around it.

### D3: Enclosure-planning model — place oriented buildings, derive yards as negative space

**Choice.** Replace the band-slice + magic-coordinate layout with a placement
manifest produced by a planner, then derive everything else from it.

The planner emits:

```
PlacementManifest = List[Placement]
Placement = (archetype, facing, anchor_wall, offset_along_wall, importance)
```

where `anchor_wall ∈ {north, south, east, west}` is the *perimeter wall the
building backs onto*, and `offset_along_wall` positions it along that wall. The
yard is then *derived*: a cell is in yard Y iff it is enclosed by the buildings
and walls that face Y.

For `chinese_mansion` (3 进), the manifest encodes the form directly:

```
进 1 (前院, enclosed by):
   gate_house  facing=inward   anchor=south   (through-building, the entrance)
   front_row   facing=north    anchor=south   (倒座, beside the gate, door→yard)
   screen_wall (照壁, free panel off-axis)
进 2 (主院, enclosed by):
   open_hall   facing=south    anchor=north   (正房/敞厅, door→yard)
   west_wing   facing=east     anchor=west    (西厢, door→yard)
   east_wing   facing=west     anchor=west... east (东厢, door→yard)
   yimen_gate  (仪门, between 进1 and 进2)
进 3 (后院, enclosed by):
   tower_house facing=yard     anchor=north/offset (楼阁, off-axis)
   ermen_gate  (二门, between 进2 and 进3)
花园 (garden band, behind 后院):
   garden_rockery + garden_pond + garden_pavilion (unchanged from current)
```

The planner:
1. Places each building against its anchor wall with the correct facing (D1),
   honoring the central axis for principal buildings and off-axis offsets for
   厢/楼阁/照壁.
2. Derives the yards as the negative space between facing-buildings.
3. Inserts the inner gates (仪门/二门) at the进 boundaries *as derived from the
   building placement*, not from pre-cut z-bands.

This replaces `_layout_front_yard`, `_layout_main_yard_mansion`,
`_layout_back_yard` (which hard-code `oy0+4`, `lot_d-3`, etc.) with a single
`_plan_mansion_enclosure(variant) -> manifest` + a `_realize_manifest(...)`
that places buildings and derives yards.

**Why derive yards instead of keeping bands?** The band model is what produced
"buildings crammed at the gate end" — the bands were sized by fixed proportions
(20%/30%/22%/28%) and buildings dropped at band-relative magic coordinates,
ignoring the enclosure relationship. Deriving the yard from the buildings makes
the yard *exactly* the space the buildings enclose, by construction.

**Alternatives considered**

- *Keep bands, just fix the coordinates.* Rejected: the bands are the defect's
  vehicle; patching coordinates leaves the model that produces the pile-up.
- *Hand-author 6 layouts (one per NBT).* Rejected: not algorithmic, doesn't
  generalize, and the byte-stability/variant-differentiation machinery expects a
  generator.
- *Full constraint solver (place buildings, solve for non-overlap + reachability).*
  Rejected: over-engineered for 6 deterministic layouts; the form rule fully
  determines placement, no search needed.

**Rationale.** The form rule (正房-north-wall-south-facing, etc.) is fully
deterministic — there is nothing to search. A manifest that encodes the rule,
plus yard-derivation, captures the enclosure model with no magic coordinates.

### D4: Path is a declared planning input; voxel-walkability is the gate

**Choice.** After the manifest is realized, every building's `door_info["front"]`
cell is collected as a **mandatory path endpoint**. The path backbone is routed
in the *derived* yard space (guaranteed building-free) from the gate_house inner
opening to every door endpoint. The existing `_voxel_walk_bfs` (grid + door-info
only, preserved) then runs as the acceptance gate: any door not in the visited
set → the manifest realization is rejected and the planner retries (in practice
never triggers, because the derived yard is contiguous by construction).

**Why this fixes "path doesn't reach doors".** Today the path is an after-the-
fact BFS over an unplanned layout (defect 4 in proposal). Making the doors
*inputs* to routing, in a yard space that is *by construction* free of buildings,
means reachability is structural, not luck.

**Alternatives considered**

- *Keep path as post-process, just run it better.* Rejected: a better BFS over a
  broken layout still can't reach misoriented doors.
- *Skip the path, rely on voxel-walkability alone.* Rejected: voxel-walkability
  checks *can* the player walk, but the gravel path is a visual/readability
  feature the user explicitly wants ("沙砾路到不了门口").

**Rationale.** The user named the path as a defect; the fix is to make it a
planning input, not a patch. The preserved voxel validator then becomes a true
gate (reject bad plans) rather than a green light over a broken layout.

### D5: Scope is `chinese_mansion` only; propagation is the next change

**Choice.** This change rewrites only `chinese_mansion`. `chinese_courtyard`,
`small_courtyard`, sect, and town are untouched (byte-stability guard extended
to cover them). The enclosure + orientation skeleton is proven on the mansion
(the richest form, the user's complaint), then propagated.

**Alternatives considered**

- *Rewrite all three courtyard families at once.* Rejected: triples the change
  size, triples the regression surface, and the user chose mansion-only.
- *Rewrite `chinese_courtyard` first (simpler form), then mansion.* Rejected:
  the mansion's 3 进 + 楼阁 + 花园 stress the skeleton hardest; proving it there
  first de-risks the simpler propagation.

**Rationale.** User decision (scope question). Mansion-only bounds the one-shot
rewrite risk while still proving every mechanism the skeleton needs.

### D6: `validate_mansion` rewritten to enclosure invariants; grid-only checks preserved

**Choice.** `validate_mansion` keeps all grid-only checks (perimeter floats,
ground holes, voxel walkability, silhouette) and replaces its band-coupled
checks with enclosure invariants:

- Every `building_slots` entry's door wall faces its yard (read from the new
  `facing` tag) — i.e. 倒座 door is north, 厢 doors are inward, etc.
- A `gate_house` slot is present and its footprint straddles the south perimeter.
- Every door-cell is on the path (path-as-input guarantee).
- The进 sequence is well-formed (仪门 between 前院 and 主院, 二门 between 主院 and
  后院) — checked via the *derived* yard adjacency, not z-band tuples.

The band-coupled `validate_compound` is **unchanged** (the courtyard family is
not rewritten this turn).

**Rationale.** The validator must match the model it checks. Grid-only checks
port for free; the band-coupled ones would reject the enclosure model, so they
are replaced with enclosure-coupled ones.

### D7: Variant template table retained; facing derived from the form rule, not the table

**Choice.** The 6-row `MANSION_TEMPLATES` table (gate_form, garden_scale,
tower_count, roof_grade, open_hall_bays, courtyard_size) is retained — it still
drives visible variation. Facing is **not** a template axis: it is *derived* from
each building's role by the form rule (D1 table). A template does not and cannot
randomize 倒座's facing — it is always north, because that is what 倒座 *is*.

**Rationale.** Facing is form, not variation. Mixing them would let a template
produce a 倒座 facing south (door onto the street) — exactly the defect.

## Risks / Trade-offs

- **[Door-wall relocation may collide with the stairwell / colonnade for
  multi-door buildings]** → `_door`'s `avoid` param already reserves the
  stairwell bay; extending it to pick the door *wall* must keep the avoid logic.
  **Mitigation:** the `wall` param is additive; the avoid logic is per-wall.
  Acceptance: `tower_house` (2-story, has stairwell) places its door without
  overlapping the stairwell.
- **[gate_house through-passage may break perimeter sealing]** → if the
  gate_house side walls don't meet the perimeter gap exactly, the compound leaks.
  **Mitigation:** the perimeter builder measures the gate_house footprint and
  gaps the wall to match; the gate_house side walls are guaranteed to span the
  gap. `validate_mansion` checks perimeter integrity (grid-only, already there).
- **[Derived yards may produce a non-contiguous yard for an unusual variant]** →
  e.g. a `tower_count=2` variant with towers on both sides could pinch the 后院.
  **Mitigation:** the planner's offsets are chosen so the yard stays contiguous;
  voxel-walkability catches any miss; the byte-stability guard covers the other
  families.
- **[Mirror-not-rotation may be insufficient for a future 徽派 马头墙 form]** →
  徽派's stepped gable is handed (left vs right); pure door-wall relocation won't
  produce the mirror. **Mitigation:** 徽派 is design-retention only (not
  implemented); when it is, the mirror tool (kept available per D1) handles it.
- **[Rewriting `validate_mansion` could mask a real regression]** → new
  invariants might pass a structurally-broken layout the old validator caught.
  **Mitigation:** all grid-only checks (the structural ones) are preserved
  verbatim; only the band-coupled *shape* checks are replaced.
- **[One-shot rewrite has no intermediate acceptance]** → no "gate fixed but yard
  still old" checkpoint. **Mitigation:** the change is decomposed into tasks that
  can be unit-tested in isolation (orientation variants testable alone; gate_house
  testable alone) even though they ship together.

## Migration Plan

1. **Orientation mechanism (D1)** — add `wall` param to `_door`; plumb through
   `facade_detail_pass` and `door_info`; unit-test a `_west`-facing wing renders
   its door on the west wall with a correct `front` cell. No compound change yet.
2. **Gate-house entrance (D2)** — wire `build_gate_house` into the mansion's
   south entrance; teach `_add_chinese_perimeter` to gap around it. Unit-test the
   passage is sealed + walkable.
3. **Enclosure planner (D3)** — implement `_plan_mansion_enclosure` + manifest
   realization; derive yards. Replace the three `_layout_*_yard` calls in
   `generate_mansion`.
4. **Path-as-input (D4)** — collect door-cells as endpoints; route in derived
   yard; wire `_voxel_walk_bfs` as the gate.
5. **Validator (D6)** — rewrite `validate_mansion` to enclosure invariants.
6. **Regenerate + accept** — regenerate `chinese_mansion_001..006.nbt`; run
   validation; byte-stability guard on the other families; staged manual
   acceptance (the user's "feels right" gate).

No runtime world migration (pre-1.0 mod).

## Open Questions

- **Should the 楼阁 (tower_house) face the 后院 yard or the 主院 yard?** It sits
  in the 后院 off-axis; facing the 后院 reads as "looking back at the garden
  approach", facing the 主院 reads as "completing the 主院 enclosure from behind".
  Default: face the 后院 (it belongs to the 后院 enclosure). Defer to visual review.
- **Should the 仪门/二门 remain parcel-node walls (current form) or become small
  through-buildings like the gate_house?** The current form is a roofed wall with
  a 3-cell passage; making them through-buildings would unify the entrance
  vocabulary but adds scope. Default: keep as roofed-wall passages this turn;
  unify in the propagation change. Defer.
- **Does the open_hall's open facade (敞厅, `FACADE_OPEN` slot) interact with the
  new `wall` param?** The 敞厅 has no front wall at all (columns only), so "door
  wall" is moot — its yard-side is fully open. Default: 敞厅 skips the door-wall
  logic; its `door_info` is the open-front center. Confirm during implementation.
