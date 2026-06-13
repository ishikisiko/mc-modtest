## Why

The generator only produces single-story buildings: every volume is one wall height plus a gable roof, and "bigger" buildings only grow horizontally (`large_lite` attaches more volumes). There is no concept of stacked floors, so the project cannot express larger housing or a credible shop with a storefront below and living space above. The town-generation goal needs taller, visually distinct building families before any settlement layout work makes sense.

## What Changes

- Introduce a **multi-story capability**: the massing graph gains a `stories` / per-story wall-height concept, plus floor slabs and a stairwell that connects stories.
  - Stairwell position is chosen during massing so it avoids the door and window bays; per-story floor openings align vertically so the stair is walkable.
  - Window bands align across stories by default; facade grammar plans one window band per story instead of a single wall band.
  - New passes `floor_slab_pass` and `stair_pass` are inserted into the existing deterministic pass order.
- Add a **`shop` archetype family** (functional / commercial):
  - `small_shop` (1 story, compact storefront) and `medium_shop` (2 stories, ground-floor storefront + upstairs living).
  - 5 variants per shop tier, differentiated purely on a **form axis** (story count, roof style, signage, awning/eave, footprint, entrance). Variant distinction must be clearly stronger than `small_house`.
  - An optional `industry` meta field is reserved on shop nodes but carries no behavior this change.
- Add a **`big_house` archetype family** (housing):
  - Reuses the multi-story capability; 2–3 stories chosen at random.
  - 5 variants whose distinction is **structural** (massing / story / roof differences), not just exterior decoration patches, and clearly stronger than `small_house`.
- Update the `building-generation` spec to list the new archetypes, the new pass order, and to record the archetype-family classification (housing for `big_house`, functional/commercial for `shop`) per its existing "new archetype family" requirement.

Out of scope: the Chinese courtyard **府邸 / compound** (perimeter wall, water features, planting, multiple sub-buildings, new Chinese style profile) is deferred to a separate follow-up proposal.

## Capabilities

### New Capabilities
- `multi-story-massing`: stacked stories in the massing graph — per-story wall height, floor slabs, a massing-placed stairwell with vertically aligned floor openings, and per-story facade window bands.

### Modified Capabilities
- `building-generation`: add `shop` and `big_house` to the supported archetype families; document the `small_shop` / `medium_shop` story counts, the `big_house` 2–3 story range, the 5-variant-per-family expectation with form/structural distinction stronger than `small_house`, and insert `floor_slab_pass` and `stair_pass` into the documented pass order.

## Impact

- Code: `tools/buildgen/massing.py` (stories on nodes/graph), `tools/buildgen/archetypes.py` (new `build_shop` / `build_big_house`, multi-story main volumes), `tools/buildgen/passes.py` (new floor-slab and stair passes, pass order), `tools/buildgen/facade.py` (per-story window bands), and the library generators (`tools/generate_building_library.py`, `tools/generate_all_buildings.py` / `tools/generate_all_structures.py`) plus their validators to emit and check the new families.
- Specs: new `openspec/specs/multi-story-massing/spec.md`; delta to `openspec/specs/building-generation/spec.md`.
- Generated assets: new `shop` and `big_house` `.nbt` structures and `place/` mcfunctions in `src/main/resources/data/myvillage/`.
- No worldgen changes; resource-layer validation target is unchanged.
