# Design notes

## The two-layer model

A side wall is built by two passes that never coordinate:

| Layer | Pass | Op | Span |
|---|---|---|---|
| Rectangle (eave-down) | `facade_detail_pass` | `wall_frame()` | `fh … wall_top` |
| Triangle (eave-up, the "gable") | `roof_pass` | `gable_roof()` | `wall_top+1 … ridge_y` |

The lower rectangle is coherent. Every defect is either (a) something written into the
rectangle plane by a pass that should not be touching it (furniture, connection door,
chimney), or (b) the triangle plane being filled with the wrong material or stopping
short. The fixes therefore split cleanly into "keep other passes off the wall plane",
"fix the gable", and "validate the plane".

## Why priority cannot be relied on

`BlockGrid.set` stores a `priority` but only `PROTECTED` blocks a write
(`grid.py:62-73`); whoever writes last wins. So the furniture/chimney leaks are not
priority bugs — a later pass legitimately overwrites the wall. The fix is to make those
passes **decline** to write into a cell that belongs to another volume's wall, not to
re-order priorities. `roof_cleanup_pass` is the existing band-aid for the gable, but it
stops at the first non-air cell, so a stair halts the column fill and the apex gap above
a roof cell is never reached — which is why the gable needs a real geometry fix, not a
bigger cleanup net.

## Decision 1 — gable infill material is style-declared

Add an optional `GABLE_INFILL` material slot to each style. `gable_roof()` resolves the
infill as:

```
infill = style.primary("GABLE_INFILL")  if style.has_slot("GABLE_INFILL")
         else style.primary("WALL_MAIN")
```

- Stone styles (`cultivation_sect`, `chinese_courtyard`, `cultivation_town`) omit the
  slot → solid `WALL_MAIN` gable, no dark planks.
- `medieval_village` may declare `GABLE_INFILL` listing the timber planks if the
  half-timbered look is wanted — now an explicit, per-style choice instead of a
  hardcode. The 60/40 `rng` mix is dropped; variation comes from
  `material_variation_pass` like every other facade.
- The cell is tagged with the slot it actually holds (`GABLE_INFILL`/`WALL_MAIN`), not a
  lie of `WALL_MAIN` over a `ROOF_DARK` block.

Alternative considered: keep the mix but bias it by style family. Rejected — it keeps a
style-agnostic hardcode in an op and still mislabels the slot.

## Decision 2 — `spots_along_walls` mounts only on its own volume's wall

`spots_along_walls()` currently accepts any `STRUCTURE` neighbor as a mount surface. Add
the owning volume to the zone context and require the neighbor cell to belong to the
same volume (compare against the volume bounds, or tag wall cells with their volume id).
This fixes the smithy leak structurally even if a future zone is mis-sized, and is a
superset of the `archetypes.py:769` inset fix (do both: inset the zone *and* harden the
mount test).

## Decision 3 — gable geometry

1. Climb the triangle loop to the true `ridge_y` (drive it from the same overhang-aware
   span the slope uses, or simply iterate `yy` to `ridge_y` and fill every still-empty
   in-plane cell), so no apex gap remains.
2. After the slope is written, for every gable-plane cell that holds only a roof
   **stair**, write a full gable block in the wall plane one step *inboard* of the stair
   (or replace the in-plane stair with a full block where the stair is purely structural
   silhouette). Record every such cell in `gable_cells` so the existing `open_gable`
   check and the new plane check both see it.

## Decision 4 — connection / chimney stay off real wall posts

- `_carve_connection`: early-return when `vol.meta.get("open")` (open sheds have no
  wall). Compute `zmid`, then if it collides with a post/window/door column from the
  parent's `WallPlan`, shift to the nearest conflict-free column inside the shared span;
  re-seal the two post cells if the opening must cross one.
- Chimney: replace `force=True` over `FACADE`/`STRUCTURE` cells with a guarded write that
  skips a cell owned by an abutting `side_wing`/shed wall, or offsets the chimney column
  outward by one when that side is occupied.

## Decision 5 — validation is the regression contract

Two new quality checks (hard errors, since they gate export):

- **side-wall plane enclosure:** for each closed volume and each wall, every cell in the
  wall plane from `fh` to the roofline directly above it SHALL be non-air unless it is a
  planned `OPENING` (door/window/connection). A hole that is not a planned opening fails
  with `open_side_wall`.
- **stray exterior block:** no `INTERIOR`/`PROTECTED`-tagged, non-opening block SHALL sit
  in a cell that lies in a *different* volume's exterior wall plane. A leaked
  anvil/barrel fails with `furniture_on_wall`.

These make the apex gap, stair gaps, smithy leak, and chimney punch-through all
detectable in `reports/` so the bundle can be accepted and cannot silently regress.

## Sequencing

Land the validator checks first (they will fail on `main`, documenting the bug), then
fix sources until green, then regenerate libraries and run manual acceptance. Post
alignment and speckle tuning are last and may be dropped if they destabilize byte-output
without clear visual payoff.
