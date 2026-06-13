## Context

The buildgen pipeline generates one building per `MassingGraph`; "bigger" means more attached volumes within that one graph. A Chinese 府邸 is not a building but a *parcel*: a walled lot with several buildings arranged on a central axis, plus inner water and planting. The graph's 2D overlap/attach math (`_rects_overlap`, `_free_spot`) is sized for one building's volumes, not a whole lot. Change `add-western-multistory-buildings` (proposal A) adds a reusable multi-story primitive; this change reuses it for the `main_hall` but is otherwise independent. The Chinese style is a new aesthetic, so it needs its own style profile rather than the `medieval_village` one.

## Goals / Non-Goals

**Goals:**
- A `CompoundGraph` parcel layer that arranges multiple sub-buildings plus landscape inside a perimeter wall, reusing the existing per-building pipeline for each sub-building.
- An authentic one-courtyard (一进) axial layout with gate, front row, paired wings, and main hall.
- Water and planting that are part of the layout (routed around), not afterthought decoration.
- Combinatorial variants from independent axes, sampled per seed.
- A new Chinese style profile cleanly separated from Western styles.

**Non-Goals:**
- Multi-courtyard (二进/三进) compounds, side跨院, rear-garden-only compounds (one courtyard with optional landscape this change).
- NPC systems, worldgen.
- Replacing or refactoring the per-building pipeline — the compound layer sits on top of it.
- Western archetype reuse for any Chinese sub-building.

## Decisions

### D1: New `CompoundGraph` layer, sub-buildings via the existing per-building pipeline
A `CompoundGraph` holds parcel nodes (`perimeter_wall`, `water_feature`, `planting`, `corridor`, `path`) and building slots; each slot calls the existing `generate_building`-style pipeline to produce a sub-building `MassingGraph`, which the compound then translates to its slot position.
- **Why:** keeps the per-building overlap math at building scale and isolates parcel concerns; matches the explored conclusion. The town system can reuse this parcel layer later.
- **Alternative considered:** one giant `MassingGraph` for the whole lot (rejected — forces the 2D overlap helpers to handle lot scale and tangles building vs parcel concerns).

### D2: Axis-relative layout grammar, parcel laid out in compound coordinates
The compound computes the lot dimensions from the courtyard-size axis, places slot anchor positions along the central N–S axis (gate south, main hall north, wings east/west), then offsets each generated sub-building into the parcel grid. Front = low z is preserved per building so existing facade/door conventions still hold.
- **Why:** reuses per-building conventions unchanged; the compound only needs translation + footprint reservation.

### D3: Landscape reserved before corridors/paths route
Water and planting cells are reserved on a parcel occupancy grid first; corridors and the central path are then routed on the remaining free cells (gate → courtyard → main hall must stay connected). Building footprints also reserve cells so nothing overlaps.
- **Why:** satisfies the spec's "route around water/planting" and "buildings don't overlap landscape" requirements deterministically.
- **Alternative considered:** decoration-pass landscape after layout (rejected per the explored decision — water is structural, in the inner courtyard / rear).

### D4: Combinatorial variant model (axes × options), seeded sampling
Variants are defined as independent axes — courtyard size {small, medium, large}, water form {pool, channel, third}, planting layout {A, B, C}, roof grade {硬山, 悬山, 歇山}, gate style {1, 2, 3}, symmetry {mild-asymmetry (default), strict-mirror}. A seed selects one option per axis. The library samples N (default 6) distinct combinations.
- **Why:** ~3^5 base combinations from a small table beats hand-authoring discrete variants; less code, more variety; directly encodes the owner's chosen axes.
- **Trade-off:** not every combination is guaranteed aesthetically ideal; quality gating + sampling distinct combos mitigates.

### D5: Symmetry as a variant axis; default mild asymmetry, strict mirror as an option
Default generation allows the east/west wings to differ within bounds while preserving the axial layout; one symmetry option forces strict mirror (wings are reflections across the axis).
- **Why:** matches the owner's call — full mirror is a recognizable variant, not the only mode.

### D6: New Chinese style profile JSON, reusing the existing profile schema
Add `tools/buildgen/styles/<chinese_id>.json` with the same keys as `medieval_village.json`, expressing sloped roofs/timber/white walls; `allowed_roof_types` includes 硬山/悬山/歇山; Western-only blocks go in `forbidden_blocks`.
- **Why:** no schema change needed; the quality gate's forbidden-block check then enforces style separation for free.

## Risks / Trade-offs

- **Compound export size / NBT limits** → a walled multi-building lot is large; validate against structure size limits and consider per-building placement via mcfunction if a single NBT is too big.
- **Corridor/path routing fails to connect gate→hall on tight lots** → enforce a minimum courtyard size per size option and assert connectivity in a quality check; resample on failure (reuse the `MAX_ATTEMPTS` retry pattern).
- **Multi-story `main_hall` depends on proposal A** → sequence implementation after A merges; if A slips, `main_hall` can degrade to single story behind a flag without blocking the rest of B.
- **Chinese roof grades (歇山 etc.) not expressible with current roof ops** → may need new roof ops; scope a roof-op spike early, fall back to the simplest sloped roof if a grade is too costly this change.
- **Mild-asymmetry looks like a bug, not a choice** → bound the asymmetry (e.g., wing length/detail only, footprint anchors stay axial) and visually smoke-test.

## Migration Plan

1. Land the Chinese style profile + Chinese sub-building archetypes (generate standalone for review first).
2. Add the `CompoundGraph` layer + one-courtyard layout + perimeter wall.
3. Add water/planting reservation + corridor/path routing.
4. Add combinatorial variant axes + library sampling + validators; regenerate assets.
5. Rollback: revert the change; Western archetypes and the per-building pipeline are untouched, so existing assets need no regeneration.

## Open Questions

- Compound output form: one large NBT vs a `place/` mcfunction that stamps each sub-building NBT plus landscape — decide against structure size limits.
- The third water form beyond pool/channel (e.g. well + water vats, or a bridged pond) — fix during implementation.
- Exact 硬山/悬山/歇山 roof realizations and whether all three are feasible this change or one is deferred.
- Whether planting uses schematic prop blueprints (see `examples/props/`) or inline block ops.
