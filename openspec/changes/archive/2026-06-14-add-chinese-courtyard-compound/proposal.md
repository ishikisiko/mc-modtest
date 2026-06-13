## Why

The generator produces individual buildings, but the town goal needs coherent *parcels* — a perimeter, multiple buildings arranged by rules, and landscape between them. A Chinese courtyard manor (府邸) is the first such parcel: it is defined by an axial layout, a perimeter wall, paired wings, and inner water/planting, none of which the single-building `MassingGraph` can express. This introduces the parcel layer the broader town system will reuse, in an authentic Chinese style distinct from the existing Western buildings.

## What Changes

- Introduce a **`CompoundGraph`** parcel layer above the existing `MassingGraph`. The compound owns the perimeter wall, water, planting, corridors, and paths, and holds **building slots** — each slot generates a sub-building by reusing the per-building `MassingGraph` + pass pipeline.
- Implement the **one-courtyard (一进)** layout grammar along a central axis: `gate_house` (门楼) at the south end, `front_row` (倒座房), two `side_wing` (厢房) east/west, and `main_hall` (正房) at the north, enclosed by a four-sided `perimeter_wall`.
- Treat **water (`water_feature`) and planting (`planting`) as structural grid elements** that occupy parcel cells and participate in layout; corridors (`corridor`, 廊) and the central `path` route around them.
- Add **new Chinese sub-building archetypes** `main_hall`, `side_wing`, `front_row` that use a **new Chinese style profile** (sloped roofs, timber frame, white walls). These SHALL NOT reuse Western blueprints (`small_house`, etc.). `main_hall` MAY be two stories (reusing the multi-story capability from change `add-western-multistory-buildings`); `side_wing` and `front_row` are single story.
- **Variants are combinatorial**: independent axes, each with three options, randomly combined per seed — courtyard size (small/medium/large), water form (pool/channel/third), planting layout (3), roof grade (硬山/悬山/歇山), gate style (3) — plus a symmetry axis (mild asymmetry by default; strict mirror as one option). The library samples several compound instances (default 6 distinct combinations).

Out of scope: multi-courtyard (二进/三进) compounds, side跨院 paths, NPC systems, and worldgen. Dependency: implementation follows change `add-western-multistory-buildings` (its multi-story capability is reused for `main_hall`); that change does not depend on this one.

## Capabilities

### New Capabilities
- `courtyard-compound`: the `CompoundGraph` parcel layer, the one-courtyard axial layout grammar, perimeter wall, water/planting as structural layout elements, corridors and central path that route around them, and the combinatorial variant axes (size, water form, planting, roof grade, gate style, symmetry) with seeded sampling.

### Modified Capabilities
- `building-generation`: add `main_hall`, `side_wing`, and `front_row` Chinese sub-building archetypes (classification: housing/civic), generated as compound building slots; document that a compound is composed of multiple sub-building `MassingGraph`s arranged by the compound layer.
- `style-profile`: add a new Chinese style profile (`tools/buildgen/styles/<chinese_id>.json`) with sloped-roof, timber-frame, and white-wall vocabulary used by the Chinese sub-building archetypes.

## Impact

- Code: new compound module (e.g. `tools/buildgen/compound.py`) for `CompoundGraph` and the one-courtyard layout grammar; `tools/buildgen/archetypes.py` (Chinese `main_hall` / `side_wing` / `front_row`); `tools/buildgen/style.py` + a new style JSON under `tools/buildgen/styles/`; compound-level passes for perimeter wall, water, planting, corridors, and paths; a new library/export entry point for compounds and matching validators.
- Specs: new `openspec/specs/courtyard-compound/spec.md`; deltas to `openspec/specs/building-generation/spec.md` and `openspec/specs/style-profile/spec.md`.
- Generated assets: compound `.nbt` structures and `place/` mcfunctions under `src/main/resources/data/myvillage/` (a compound is a larger multi-building structure).
- Dependency: reuses the multi-story massing capability from `add-western-multistory-buildings`; no worldgen changes.
