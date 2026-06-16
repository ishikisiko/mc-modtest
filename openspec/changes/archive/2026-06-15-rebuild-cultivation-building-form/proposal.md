## Why

The cultivation libraries were recently re-skinned with external-mod blocks, but visual review shows they still read as a European medieval village in Chinese clothing. The cause is structural, not material: every cultivation archetype is an *alias of a Western builder* — `cultivation_house` → `build_small_house`, `cultivation_shop`/`cultivation_market` → `build_shop`, `cultivation_inn` → `build_tavern`, `town_shrine` → `build_lord_manor` (a corner-tower manor) — and `alchemy_room` even places a brick chimney (`archetypes.py:1135-1167`, `1277-1296`). The 修仙 identity lives in built form — sweeping upturned eaves (飞檐翘角), raised stone platforms (台基), colonnaded verandas (檐廊), galleried pavilions and tapering pagodas (楼阁/塔), ridge ornament (宝顶/鸱吻) — none of which the engine can produce. The one cultivation roof, `tiered_eave_roof`, is just two Western triangular gables stacked (`ops.py:642-687`): no curve, no upturned corner, no finial. Re-skinning cannot fix a silhouette; the form vocabulary and massing grammar must be rebuilt.

## What Changes

- Add a **cultivation roof vocabulary with real silhouette**: `sweeping_eave_roof` (飞檐翘角 — corners that step up-and-out over a deep overhang), `hip_roof` (庑殿, four-sided), and `pyramidal_roof` (攒尖, converging to a finialed apex, for pavilions and pagoda crowns). **Redefine** `tiered_eave_roof` so each tier is a sweeping eave (curved 重檐), not a stacked straight gable.
- Add **roof ornament forms**: a crowning finial (宝顶) and ridge-end pieces (鸱吻/正脊), and **斗拱 (dougong)** bracket sets under deep eaves, replacing the thin fence-under-eave rhythm.
- Add a **cultivation massing grammar** so cultivation archetypes stop aliasing Western builders: 台基 (a thick raised stone platform as a first-class massing element), 檐廊 (a colonnade/veranda of standoff columns under a deep eave), 楼阁 (galleried multi-story pavilion with balustraded balconies), 塔 (tapering, per-tier-inset pagoda) for the scripture pavilion, and a built three-bay 山门牌坊 gate (volumetric, not a flat decal).
- **Remove the Western tells** from cultivation builds: chimneys, fence-post porches, woodpiles, barrel-clusters, and fence-patches SHALL NOT appear. `alchemy_room` replaces its chimney with a **丹炉** (alchemy furnace); `town_shrine` gets its own 神庙/道观 massing instead of the lord-manor corner-tower.
- Extend the **style-profile schema** with cultivation form slots — `COLUMN` (檐柱), `PLATFORM_STONE` (台基), `RIDGE_ORNAMENT` (脊饰), `BALUSTRADE` (栏杆), each with a vanilla fallback; retune cultivation `proportions` (deep overhang, tall platform, ~½ roof ratio) and drop Western motifs from cultivation `allowed_motifs`.

Out of scope (deferred to the companion change `rebuild-cultivation-settlement-form`): how these buildings sit together — terraced mountain axis, 廊桥/飞桥 links, climbing courtyards, cliff/water/suspended siting. This change makes each building right *in isolation*; the companion composes them on the mountain.

## Capabilities

### New Capabilities
- `cultivation-massing-grammar`: 修仙-specific massing elements (台基 platform, 檐廊 colonnade, 楼阁 galleried pavilion, 塔 tapering pagoda, 山门牌坊 gate, 丹炉 furnace) that replace the Western-builder aliases; the rule that cultivation builds omit Western domestic tells.

### Modified Capabilities
- `cultivation-form-vocabulary`: adds `sweeping_eave_roof` / `hip_roof` / `pyramidal_roof`, ridge ornament (宝顶/鸱吻) and 斗拱 detail; redefines `tiered_eave_roof` as a curved double eave.
- `style-profile`: adds `COLUMN` / `PLATFORM_STONE` / `RIDGE_ORNAMENT` / `BALUSTRADE` slots, lists the new cultivation roof forms, drops Western domestic motifs from cultivation styles, and retunes cultivation proportions.

## Impact

- **Code**: `tools/buildgen/ops.py` (new roof handlers + ridge/dougong detail + 丹炉 motif), `tools/buildgen/archetypes.py` (cultivation builders rewritten onto the new grammar; remove `build_small_house`/`build_shop`/`build_tavern`/`build_lord_manor` aliases and `_chimney`/`_porch`/woodpile calls), `tools/buildgen/style.py` (new slots), `tools/buildgen/styles/cultivation_town.json` + `cultivation_sect.json` (slots, proportions, allowed roofs/motifs), `tools/buildgen/quality.py` (forbid Western tells in cultivation; gate the new forms), possibly `exmod/mod_block_catalog.json` (new slot roles).
- **Specs**: new `cultivation-massing-grammar`; deltas to `cultivation-form-vocabulary` and `style-profile`.
- **Compatibility**: medieval / Chinese-courtyard / civic libraries must stay byte-stable (they never invoke cultivation forms — regression-guarded). Vanilla-profile cultivation output must resolve every new slot to its `minecraft:` fallback (no air, no mod-only dependency). **BREAKING** for cultivation NBT (regenerated) and for any code importing the cultivation builders.
- **External-mod note**: the eave curve is generated geometry (vanilla stairs/slabs), independent of any Asian-decor mod; the currently-unstaged curved-roof/lantern mod (fetzisasiandeco-class) remains an optional future skin layered through the new slots, not a dependency of this change.
