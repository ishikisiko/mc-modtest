## Why

Phases 0–1 (change `add-external-mod-catalog-slots`) built the catalog and a namespace-aware slot system, but no mod block is placed yet: the `full` profile still generates byte-identical to `vanilla`. Two things block actual mod decor from appearing. First, some mod families use blockstate grammar the current role-based string picker can't orient — vanilla stairs are `facing/half/shape`, but the staged `supplementaries:awning` family is `facing/bottom/slanted`, so substituting the id alone misplaces it (plan Phase 2). Second, the new semantic slots (`ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, `MARKET_FITTINGS`) and the existing ones still carry only vanilla fallbacks, and the catalog's mod families aren't composed into any 市井 / 修仙 props (plan Phase 3). This change makes mod blocks actually appear, in the right place, with correct orientation, under the `full` profile — while keeping `vanilla` output unchanged.

Note: the staged `exmod/mod_assets.zip` contains `fetzisdisplays` (Fetzi's Displays), **not** the `fetzisasiandeco` curved-roof/paper-lantern mod the design report assumed (recorded in the catalog `notes`). So there is no curved tiled-roof family to anchor Phase 2 on. We instead anchor the orientation adapter on `supplementaries:awning` (slanted eaves / market canopies — the closest present family with novel grammar) and populate `ROOF_TILE` / `PAPER_LANTERN` with the best present substitutes, leaving the door open to swap in real Asian-decor families when they are staged.

## What Changes

- **Orientation adapter (Phase 2).** Introduce a `(family, cell role, orientation) → blockstate` adapter so families whose blockstate grammar differs from vanilla stairs/slabs are placed with correct props, not just a swapped id. The `supplementaries:awning` family (`facing` + `bottom` + `slanted`) is the first family implemented and the proving case; the existing `stair_state` / `slab_state` helpers are folded in as the trivial vanilla-grammar instances of the same interface.
- **Populate slots with mod ids (Phase 3).** Insert catalog ids at the **front** of the matching slot lists per style (`chinese_courtyard`, `cultivation_sect`, `cultivation_town`), preserving the trailing `minecraft:` fallback so `vanilla` is unaffected. Concrete 落点: `ROOF_TILE` ← awnings + `supplementaries` tile stairs/slabs; `PAPER_LANTERN` ← `ars_nouveau` sconces / `supplementaries` candle holders / source lamp; `RITUAL_ANCHOR` ← `ars_nouveau` brazier / arcane pedestal / sourcelinks; `MARKET_FITTINGS` ← `farmersdelight` + `supplementaries` + `fetzisdisplays` props; plus enriching existing `FURNITURE` (Macaw's Furniture), `LIGHTING`, and window/wall slots.
- **Author decoration motifs (Phase 3).** Add a new `market_stall` motif (counter + canopy via awning adapter + food display), and route the existing `incense_altar` / `spirit_array` and a sect-gate / 牌坊 motif through the new `RITUAL_ANCHOR` and `MARKET_FITTINGS` slots so they compose mod blocks instead of vanilla stand-ins. Every motif degrades to its vanilla fallback under the `vanilla` profile.
- **No Java/runtime changes.** Generation-layer only; the runtime resolver and optional mod deps remain Phase 5. Modset-aware validation remains Phase 4.

## Capabilities

### New Capabilities
- `orientation-adapter`: A generation-layer adapter that resolves a block family + cell role + orientation into a fully-propertied blockstate string, so families with non-vanilla blockstate grammar (e.g. `awning`'s `bottom`/`slanted`) orient correctly. Vanilla stair/slab grammar is one registered family among others.
- `mod-decor-motif`: Composition motifs (`market_stall`, ritual altar, sect gate / 牌坊) that assemble external-mod blocks via semantic slots and the orientation adapter, each with a vanilla-safe fallback.

### Modified Capabilities
- `style-profile`: Slot lists are populated with confirmed external-mod ids at the front per design-intent role, while every list still ends with its `minecraft:` fallback; under the `full` profile generation places mod ids, under `vanilla` it places only the unchanged fallbacks.

## Impact

- **Code:** new orientation-adapter module/interface in `tools/buildgen/` (folding in `stair_state`/`slab_state` from `ops.py`); new + edited motif handlers in `tools/buildgen/ops.py`; mod-id population in `tools/buildgen/styles/{chinese_courtyard,cultivation_sect,cultivation_town}.json`; `allowed_motifs` gains `market_stall`.
- **Artifacts:** mod ids sourced from `exmod/mod_block_catalog.json` (read-only); no new artifact files. `exmod/` zips are not reopened.
- **Profiles:** `full` output now contains mod ids; `vanilla` output stays byte-identical to current `main` (regression guard).
- **Out of scope (later phases):** modset-aware validation forbid/allow lists (Phase 4); Java runtime resolver + `neoforge.mods.toml` optional deps (Phase 5); regenerate/preview/iterate (Phase 6).
- **User gate:** the per-family 落点 above were confirmed for this change; swapping in real `fetzisasiandeco` roof/lantern families is deferred until those assets are staged.
