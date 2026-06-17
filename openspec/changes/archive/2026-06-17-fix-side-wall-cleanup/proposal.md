## Why

Two recurring complaints about generated buildings both trace to the **side wall**:
"无意义杂方块破坏墙面美观" (meaningless junk blocks ruining the wall surface) and
"侧墙不完整" (incomplete side walls). Two independent investigations of the side-wall
pipeline (this proposal merges both) found the causes are spread across **several
independent layers** of the same wall, which is why no single earlier fix and no
existing validator caught them:

```
        ╱╲   ← upper layer: gable triangle (above the eave line)
      ╱░░░░╲
  ═══════════  wall_top
  ║ wall body ║  ← lower layer: rectangular wall + interior/connection/chimney
  ▓▓▓▓▓▓▓▓▓▓▓
```

The "junk blocks" have **two** distinct sources, and the "incomplete wall" has **three**;
on top of that the quality check is blind to all of them because it only re-checks the
cells the gable op recorded placing, not whether the wall plane is actually enclosed.
This change fixes the side-wall generation across both layers and adds a plane-enclosure
+ stray-block validator so the class of defect cannot silently regress.

## What Changes

Grouped by the symptom each item removes. Code locations are current as of `main`.

### Junk blocks on the wall surface

- **Interior furniture lands on a neighbor's exterior wall (smithy zone).**
  `archetypes.py:769-770` defines the `smithy` interior zone with the open shed's
  **full** volume bounds `(work.x0, work.z0, work.x1, work.z1)` instead of the inset
  bounds used everywhere else (`forge`/`storage` use `main.x0 + 1 … main.x1 - 1`).
  Because the shed butts against the main building, `spots_along_walls()`
  (`ops.py:1658-1673`) sees the **main building's** `STRUCTURE` side wall as a valid
  furniture-mount surface and places anvil/barrel/furnace flush against it, tagged
  `["INTERIOR", "PROTECTED"]` so nothing can clean it up later. Fix: inset the smithy
  zone like the others, and make `spots_along_walls()` refuse to mount furniture on a
  wall cell that belongs to a *different* volume.
- **Gable triangle mixes dark roof planks into the wall (all gabled buildings).**
  `gable_roof()` (`ops.py:539-598`) fills each gable cell with
  `gable_state if rng.random() < 0.6 else style.slot_entry("ROOF_DARK", "_planks")` —
  a 60/40 random mix of a wall material and the **dark roof plank**. On stone-walled
  styles this scatters `dark_oak_planks` across a white calcite/quartz/diorite gable
  (`cultivation_sect`, `chinese_courtyard`, `cultivation_town`); only the wood styles
  ever read it as half-timbering. The cell is also tagged `slot="WALL_MAIN"` while
  holding a `ROOF_DARK` block, so downstream passes and validators reason about it
  wrongly. Fix: the gable infill SHALL come from a style-declared gable material
  (defaulting to `WALL_MAIN` for stone styles; the timber-infill look is opt-in per
  style), and the cell SHALL be tagged with the slot it actually holds.

### Incomplete / holed side walls

- **Connection door punches unchecked holes through the wall.**
  `_carve_connection()` (`passes.py:58-93`) carves a 1×2 opening in the parent's side
  wall at `zmid` without checking the facade plan's post/window/door positions, so it
  can gut a timber post from the middle or sit beside an opening; it is also run for
  **open sheds** that have no wall at all. Fix: skip carve for open sheds, nudge `zmid`
  off post/window/door columns, and re-seal any post column it crosses.
- **Gable apex and slope geometry leave gaps.** The roof slope climbs from
  `z0-overhang … z1+overhang` so `ridge_y` is set by the overhanging span, but the
  gable triangle climbs from `z0 … z1` with no overhang and `break`s at `yy > ridge_y`
  (`ops.py:561-604`), landing its apex ~`overhang` rows below the true ridge. The slope
  also writes **stair** blocks into the gable-plane columns; the triangle skips them
  (`grid.is_empty` is false) leaving see-through half-block gaps along the roofline.
  Fix: climb the gable to the true ridge, and back any wall-plane cell that holds only
  a roof stair with a full gable block.
- **Small `wall_h` drops the stone plinth.** `wall_frame()` (`ops.py:327-330`) computes
  `stone_rows = min(rows, wall_h - 2)`, which degenerates to `0` at `wall_h=2` and `-1`
  at `wall_h=1`, contradicting the in-code promise that "every wall type keeps a stone
  plinth" and leaving small wings/sheds as a single flat plank face. Fix: floor
  `stone_rows` at 1 for any wall tall enough to carry it.
- **Chimney force-punches the side wall.** The chimney column is placed at
  `main.x0 - 1` / `main.x1 + 1` at `STRUCTURE` priority with `force=True`, so where a
  `side_wing` or open shed abuts that side it overwrites the wall's `FACADE` cells with
  cobblestone, breaking material continuity. Fix: do not force-overwrite another
  volume's `FACADE`/`STRUCTURE` wall cells; route the chimney around an abutting volume
  or re-seal.

### Validation (regression guard)

- **Quality check is blind to all of the above.** `quality_check()` (`quality.py:104-111`)
  flags `open_gable` only over the cells `gable_roof` *recorded* placing — it never
  checks whether the gable/side-wall plane is actually enclosed, and never checks for
  stray `INTERIOR`/`PROTECTED` blocks sitting in another volume's exterior wall plane.
  Add a side-wall plane-enclosure check and a stray-exterior-block check so the apex
  gap, the stair gaps, and the furniture-on-wall leak all become hard errors.

### Lower-priority polish (bundled, not load-bearing)

- **Post alignment across opposite walls.** Per-wall `plan_wall()` posts share one rng
  stream, so a building's west/east walls get mismatched post columns. Make opposite
  walls of one volume share a post layout.
- **Material speckle tuning.** `material_variation_pass` speckle is a secondary, milder
  contributor to wall noise; clamp it on side/back facades once the dominant sources
  above are gone.

### Release

- Single validated fix bundle: keep the base version, ship as `0.8.1-fix1`, regenerate
  the shipped libraries, refresh `reports/`, and stage a mods-off/on manual acceptance.

## Capabilities

### Modified Capabilities
- `building-generation`: Side-wall construction SHALL NOT mount interior furniture on a
  different volume's exterior wall, SHALL fill gable infill from a style-appropriate
  material rather than the dark roof plank, SHALL enclose the gable plane up to the true
  ridge with full blocks behind any in-plane stair, SHALL preserve a stone plinth on
  every wall tall enough to carry it, and SHALL carve connection openings only on real
  walls and clear of posts/openings.
- `validation`: The build quality check SHALL gate export on side-wall plane enclosure
  and on the absence of interior/protected blocks placed in another volume's exterior
  wall plane.

## Impact

- **Code:** `tools/buildgen/archetypes.py` (smithy zone, chimney), `tools/buildgen/ops.py`
  (`gable_roof`, `wall_frame`, `spots_along_walls`), `tools/buildgen/passes.py`
  (`_carve_connection`), `tools/buildgen/facade.py` (post alignment),
  `tools/buildgen/quality.py` (new checks); optionally a new `GABLE_INFILL` slot in the
  four `tools/buildgen/styles/*.json`.
- **Generated data:** regenerated structures under
  `src/main/resources/data/myvillage/structure/` and refreshed `reports/`; gabled and
  blacksmith structures change blocks (expected), vanilla-only flat-roof structures
  should be byte-stable.
- **Docs/version:** `0.8.1-fix1` across `gradle.properties`,
  `src/main/resources/META-INF/neoforge.mods.toml`, README jar examples, `CHANGELOG.md`.
- **Risk:** the smithy-zone and chimney fixes shift block placement for blacksmith
  archetypes; the gable-material fix changes the look of every gabled building. Both are
  intended and gated by the new validator plus manual acceptance.
