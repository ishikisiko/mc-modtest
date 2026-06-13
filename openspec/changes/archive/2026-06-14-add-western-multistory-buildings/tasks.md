## 1. Multi-story massing primitive

- [x] 1.1 Add `stories` and `story_wall_h` handling to the main-volume builder in `tools/buildgen/archetypes.py` (`_main_volume`), with total wall height `stories * story_wall_h`; default `stories = 1` so existing archetypes are unchanged.
- [x] 1.2 Choose the stairwell column during massing (avoid the door bay and leave room for window bands) and record it in graph meta; add a helper that returns the reserved stair footprint.
- [x] 1.3 Add unit-style checks (script or asserts) that a `stories == 1` volume produces the same massing as before.

## 2. Floor slab and stair passes

- [x] 2.1 Implement `floor_slab_pass` in `tools/buildgen/passes.py`: lay an interior floor slab at each inter-story boundary, leaving the reserved stairwell opening; no-op when `stories == 1`.
- [x] 2.2 Implement `stair_pass`: place stair blocks connecting each story through vertically aligned openings; tag openings `PROTECTED`; no-op when `stories == 1`.
- [x] 2.3 Insert `floor_slab_pass` and `stair_pass` into the pass pipeline after `structure_pass` and before `facade_detail_pass`; update the pass-order docstring/constants.
- [x] 2.4 Verify legacy archetypes' generated structures and reports are byte-for-byte unchanged after the pass insertion.

## 3. Multi-story facade

- [x] 3.1 Extend `plan_building_facades` in `tools/buildgen/facade.py` to emit one window band per story for `stories > 1`, treating the stair column as a reserved/occluded interval.
- [x] 3.2 Align upper-story window positions to story-1 by default; keep all existing bay/post/corner/door rules per band.
- [x] 3.3 Confirm single-story facade planning output is unchanged.

## 4. Shop archetype family

- [x] 4.1 Implement `build_shop` in `archetypes.py` with `small_shop` (1 story, storefront feature) and `medium_shop` (2 stories, ground storefront + upstairs window band); reserve an optional `industry` meta field with no behavior.
- [x] 4.2 Define a 5-entry form-axis variant table per shop tier (roof style, signage, awning/eave, footprint, entrance) and wire variant selection by index.
- [x] 4.3 Add storefront / signage / awning build ops as needed in `tools/buildgen/ops.py`.

## 5. Big house archetype family

- [x] 5.1 Implement `build_big_house` in `archetypes.py` using multi-story (2–3 stories chosen per seed), including floor slabs and stairwell.
- [x] 5.2 Define a 5-entry variant table with structural differences (story count, massing, roof) rather than decoration-only variation.

## 6. Library generation and validation

- [x] 6.1 Register `shop` (as `small_shop` + `medium_shop`) and `big_house` in `ARCHETYPES` / `TIER_PLAN` and the library generators (`tools/generate_building_library.py`, `tools/generate_all_buildings.py`, `tools/generate_all_structures.py`).
- [x] 6.2 Ensure each new family/tier emits at least 5 variants; preserve legacy `count 10` behavior.
- [x] 6.3 Update `tools/validate_building_library.py` / `tools/validate_generated_structures.py` to recognize the new families and assert ≥5 variants and presence of multi-story features (slabs, stair openings) for multi-story outputs.
- [x] 6.4 Add a quality check that the roof sits above the top story and that stair openings are ≥2 cells tall and vertically aligned.

## 7. Regenerate, verify, and document

- [x] 7.1 Run the canonical batch generator and NBT validators; confirm new `.nbt` and `place/` mcfunctions appear under `src/main/resources/data/myvillage/` and legacy assets are unchanged.
- [x] 7.2 Smoke-test in game: place one `medium_shop` and one 3-story `big_house` via debug commands; confirm stairs are walkable and floors/windows read as multi-story.
- [x] 7.3 Update `README.md` generation/output sections to list the new families.
- [x] 7.4 Sync the `building-generation` and `multi-story-massing` spec deltas into `openspec/specs/` on archive.
