## Why

In-game `/myvillage place chinese_mansion_*` reads wrong on every axis the user
can see — and the defects are **not mansion-specific**, they are shared by every
family that consumes the compound planner:

1. **The gate is not a gate.** `_add_chinese_perimeter` "opens the gate" by
   carving a row of AIR cells in the south perimeter wall (compound.py gate set
   at the `gate = {...}` line). There is no gate building, no 门楼, no 门枕石, no
   eave — it literally reads as "a hole knocked in a wall". `gate_type`
   (manzi/jinzhu/guangliang) only changes *how wide* the hole is.

2. **Buildings pile up at the gate end.** The planner cuts the lot into fixed
   z-bands (`_compute_yard_bands`) and hard-codes building placement inside each
   band (`_layout_front_yard` drops `front_row` at `oy0+4`; `_layout_main_yard`
   pins `hall_z1 = lot_d-3`). "Yard" is treated as a pre-cut band; buildings are
   filler dropped into the band at magic coordinates. There is no concept of
   *buildings enclosing space* — so the space distribution has no organizing
   principle and reads as "half the houses crammed near the entrance".

3. **Building orientation is incoherent — the root cause of "杂乱无章".** Every
   sub-building is hard-south-facing (`-z`): `orientation="front"`
   (archetypes.py), door on `wall="front"`, `_translate_context` translates by a
   pure `(dx,dy,dz)` shift with **no rotation**. So in a mansion:
   - 正房 faces south ✓ (correct by coincidence)
   - 倒座 faces south → **its door opens onto the street, away from the yard** ✗
   - 东厢 faces south → its door faces the wrong way ✗
   - 西厢 faces south → its door faces the wrong way ✗

   Four of five buildings have their doors pointing the wrong way. With doors
   not facing the yard, the enclosure relationship is broken and "院" never
   reads. **This is the deepest defect and the reason the others cannot be fixed
   in isolation.**

4. **The gravel path does not reach every door.** Path routing is an *after-the-
   fact* BFS (`_route_complete_path`) run after buildings/landscape have already
   claimed cells. It is a patch on top of an unplanned layout, not an input to
   planning. With misplaced/orientation-broken buildings (defects 2+3), the path
   naturally cannot thread to every door.

**Root cause, single sentence:** the planner has no spatial *planning* — it
band-slices the lot and hard-codes coordinates; it lacks (a) a gate as a
building volume, (b) buildings as orientation-bearing masses that *enclose* the
yard, and (c) the path as a planning input. Because `chinese_courtyard`,
`chinese_mansion`, and `small_courtyard` all share this planner skeleton, the
defects repeat across all of them.

## What Changes

A **one-shot skeleton replacement** for `chinese_mansion` (the validation
ground), establishing the *enclosure-planning model* + *orientation variant
system* that future changes will propagate to `chinese_courtyard` and
`small_courtyard`. Scope: `chinese_mansion` only. The other two families keep
their current (defective) planner; a follow-up change adopts the proven skeleton.

The four defects map to four arcs, implemented together ("一次换骨"):

### Arc 1 — Orientation system: per-archetype facing variants (`_south`/`_north`/`_east`/`_west`)

Solve "杂乱无章" at the root. Per the chosen **mirror + selection** approach:

- Each mansion archetype that can face a non-south direction gains a facing
  variant table. A variant is the *same* massing graph with the geometry
  **mirrored** (not rotated) so the door ends up on the correct wall, plus a
  facing tag. Mirroring is cheaper than rotation (no massing/passes coordinate
  rewrites) and is faithful for the symmetric Chinese bays.
- The variant **selection is a form rule, not random**: a courtyard's 正房 faces
  south, 倒座 faces north (door toward yard), 东厢 faces west, 西厢 faces east.
  This rule table is the form spec.
- `_translate_context` gains the ability to place a mirrored variant (the mirror
  axis is along the building's local x so the door wall flips). Door-info front
  cell is mirrored correspondingly.
- `gate_house` (倒座-class) and the new entrance gate both pick a facing so their
  door faces *inward*.

### Arc 2 — Gate as a building volume (门屋), not a hole in the wall

Solve "门不像门". The south entrance becomes a real `gate_house` sub-building
(the `build_gate_house` builder already exists, archetypes.py) sitting *on* the
perimeter line with:
- A 门楼 roof over the opening (the gate_house's own roof).
- 门枕石 / 门框 from the gate_house facade.
- The passage carved *through* the gate_house (the door on its south wall faces
  the street; the opening on its north wall faces the front yard) — the player
  walks *through* a building, not a hole.

The entrance sequence becomes: 街 → 门屋(through-building) → 照壁 → 前院, with
the gate_house's inward face beginning the front-yard enclosure.

### Arc 3 — Enclosure-planning model (replace band-slicing)

Solve "堆在门口". The layout is no longer "cut z-bands, drop buildings at magic
coordinates". Instead:

1. **Place building volumes first**, each with a facing chosen so its door faces
   the yard it serves. 正房 on the north wall line facing south; 厢房 on east/west
   wall lines facing inward; 倒座 on the south wall line (beside the gate_house)
   facing north; 楼阁 in the 后院 off-axis facing the yard.
2. **The yard is the negative space enclosed by those volumes** — derived, not
   pre-cut. 进 (jin) are defined by *which buildings face which enclosed space*,
   not by z-band coordinates.
3. **A single axis governs the symmetry**; off-axis pieces (照壁, asymmetry) are
   placed *relative to the enclosure*, not to a band coordinate.

This replaces `_layout_front_yard` / `_layout_main_yard_mansion` /
`_layout_back_yard` with an enclosure planner that emits a placement manifest
`{(archetype, facing, anchor)}` and derives the yards from it.

### Arc 4 — Path as a planning input, not a patch

Solve "沙砾路到不了门口". The path network is declared as a *requirement* of the
enclosure plan: every building's door-cell must be on a path. The planner:

1. Records every door-cell (from each placed building's `door_info["front"]`) as
   a **mandatory path endpoint**.
2. Routes a gravel backbone from the gate-house inner opening to every door, in
   the *enclosure-derived* yard space (which is guaranteed free of buildings by
   construction).
3. The existing voxel-walkability validator (`_voxel_walk_bfs`, grid + door-info
   only, preserved) becomes the acceptance gate: if any door is unreachable, the
   plan is rejected and regenerated — the path can no longer be patched over a
   broken layout.

### Why one-shot, why mansion-only

- **One-shot ("一次换骨"):** the four arcs are interdependent. A gate-house (Arc 2)
  needs a facing (Arc 1); an enclosure plan (Arc 3) needs oriented buildings
  (Arc 1) and is where the path input (Arc 4) is declared. Doing them piecemeal
  leaves intermediate states that are neither the old nor the new form.
- **Mansion-only:** the mansion is the richest form (3 进 + 楼阁 + 花园) and the
  user's original complaint. Proving the skeleton here, then propagating to
  `chinese_courtyard` and `small_courtyard`, bounds the risk of a one-shot
  rewrite. `chinese_courtyard_*` NBTs are NOT regenerated in this change.

## Capabilities

### New Capabilities

- `compound-enclosure-planning`: the building-enclosure layout model — buildings
  placed as orientation-bearing masses that enclose derived yards, 进 defined by
  facing-relationship not z-band, path declared as a planning input. Replaces the
  band-slice-and-drop-at-coordinates model for `chinese_mansion`.
- `building-orientation-variants`: the facing-variant system — per-archetype
  `_south`/`_north`/`_east`/`_west` mirrored variants, the form-rule selection
  table (正房-south, 倒座-north, 东厢-west, 西厢-east), and the `_translate_context`
  mirror-placement contract.
- `mansion-gate-house`: the south entrance realized as a `gate_house` through-
  building (门楼 + 门框 + passage), replacing the hole-in-the-wall.

### Modified Capabilities

- `chinese-mansion-compound`: the layout requirement shifts from "z-band sequence"
  to "enclosure sequence" (gate-house → 照壁 → 前院 → 仪门 → 主院 → 二门 → 后院 →
  花园), with each building's facing mandated by the form rule. The band-coupled
  requirements (yard-band depths, magic coordinates) are removed.
- `validation`: `validate_mansion` is rewritten to check the enclosure model
  (every door faces its yard, every door on path, gate-house present) instead of
  band-ordering. The grid-only + voxel-walkability checks are preserved. The
  band-coupled checks stay in `validate_compound` for the (unchanged) courtyard.
- `courtyard-compound`: **no change this turn** — explicitly out of scope;
  marked as the next-change adopter in `docs/ai-kb/14_deferred_roadmap.md`.

## Impact

- **Code (planning layer, the rewrite)**:
  - `tools/buildgen/compound.py` — new `_plan_enclosure(variant)` returning a
    placement manifest `{archetype, facing, anchor}` + derived yards; new
    `generate_mansion` body that consumes it (replaces `_layout_front_yard` /
    `_layout_main_yard_mansion` / `_layout_back_yard`); `_translate_context`
    extended with mirror placement; new gate-house entrance pass; path-as-input
    routing in the enclosure space.
  - `tools/buildgen/archetypes.py` — facing-variant mirrors for mansion
    archetypes (正房/厢/倒座/楼阁/gate_house); a `VARIANT_BY_FACING` table.
- **Code (preserved, untouched)**:
  - `generate_subbuilding`, the `passes.PIPELINE`, `ops.py` roof/wall/door
    renderers, `rockery.py`/hero sculpt, `_voxel_walk_bfs`, `export.py` NBT
    writer (grid-only), the small-courtyard generator, the sect/town generators.
- **Structures**:
  - `chinese_mansion_001..006.nbt` regenerate with the enclosure form (real gate,
    oriented buildings, reachable paths). `chinese_courtyard_*` unchanged.
- **Reports**:
  - `compound_library_report.json` / `_validation.json` mansion entries update
    (new facing-per-slot stat, door-reachable rate = 100%).
- **Specs**: new `compound-enclosure-planning`, `building-orientation-variants`,
  `mansion-gate-house`; delta `chinese-mansion-compound`, `validation`.
- **Docs**:
  - `docs/ai-kb/10_civic_family.md` — mansion section rewritten to the enclosure
    model + a "planning skeleton" note.
  - `docs/ai-kb/14_deferred_roadmap.md` — add an entry: "propagate enclosure +
    orientation skeleton to `chinese_courtyard` + `small_courtyard`" as the next
    change.
  - `AGENTS.md` — replace the band-model paragraph with the enclosure-model
    paragraph; note facing variants.
  - `README.md` — command list unchanged (same `/place` ids); add a CHANGELOG
    note that mansion now reads as a real gate + enclosure.
  - `CHANGELOG.md` — large-feature bump per `openspec/config.yaml` rules.tasks.
- **Compatibility**:
  - `chinese_courtyard_*`, `cultivation_sect_*`, `cultivation_town_*`,
    `medieval_*` NBTs stay byte-stable (byte-stability guard extended to cover
    them).
  - Vanilla-profile output unchanged (no new external ids).
  - Pre-1.0 mod; no world migration promised for placed structures.
- **Out of scope (tracked in deferred roadmap)**:
  - Propagating the enclosure + orientation skeleton to `chinese_courtyard` and
    `small_courtyard` — the immediate next change.
  - 徽派天井大屋 implementation (still design-retention only).
  - 4-进 compounds, 假山 蹬道, additional decor block classes — unchanged from
    prior roadmap.
