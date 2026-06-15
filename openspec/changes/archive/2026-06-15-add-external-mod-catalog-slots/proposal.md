## Why

The buildgen pipeline needs three *forms* vanilla can't supply (curved tiled roofs, real furniture, atmosphere props) to land the 市井 + 宗门修仙 aesthetic, and `exmod/` now stages the external mods that fill those gaps. Before any mod block can be placed, we need a machine-readable source of truth for what those mods contain (Phase 0) and a slot system that stays optional + vanilla-safe whether or not the mods are installed (Phase 1). This change builds that foundation without yet placing any mod content, so the fallback skeleton is proven on `vanilla` before mod ids are slotted in later.

## What Changes

- Add `tools/extract_mod_catalog.py`: a one-time extraction tool that reads `exmod/mod_assets.zip` (mod `assets/<modid>/blockstates/`) and emits `exmod/mod_block_catalog.json` mapping `modid → [block id, blockstate properties, texture names]`, merged with the design-intent (落点) notes from `exmod/deep-research-report.md`.
- Confirm and record the final external mod set in the catalog (Fetzi's Asian Decoration, Macaw's Furniture, Macaw's Windows, Supplementaries, Farmer's Delight, Ars Nouveau) so downstream phases reference one list.
- Introduce the **modset profile** concept (`vanilla`, `full`) as the set of active namespaces driving slot resolution.
- Extend style loading so `load_style(style_id, available_namespaces)` filters each material slot list to active namespaces at load time, while keeping the existing `primary()` / `alternates()` / `pick()` contracts byte-for-byte unchanged for downstream code.
- Establish the **fallback convention**: every material slot list SHALL end with a guaranteed vanilla id, so an empty active namespace set still resolves to vanilla and never yields air.
- Declare new **empty** semantic slots `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, and `MARKET_FITTINGS` (each carrying only its vanilla fallback for now; mod ids arrive in Phase 3).

This change places **no** mod blocks and changes **no** generated output under the `vanilla` profile — it is pure scaffolding plus a catalog artifact.

## Capabilities

### New Capabilities
- `mod-block-catalog`: A reproducible extraction tool and the `exmod/mod_block_catalog.json` schema (per-mod block ids, blockstate property grammar, texture names, and merged 落点 design intent), plus the confirmed external mod set that downstream phases consume.

### Modified Capabilities
- `style-profile`: Slot loading becomes namespace-aware (`load_style(style_id, available_namespaces)`) under a modset profile; every slot list ends with a guaranteed vanilla fallback id; the schema recognizes new optional slots `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, and `MARKET_FITTINGS`. Existing slot-resolution contracts are preserved.

## Impact

- **Code:** `tools/buildgen/style.py` (namespace-filtered `load_style`, fallback convention), the per-style JSONs in `tools/buildgen/styles/` (append vanilla fallbacks, add the four new empty slots). New tool `tools/extract_mod_catalog.py`.
- **Artifacts:** new `exmod/mod_block_catalog.json`; `exmod/mod_assets.zip` is unzipped read-only (the only zip opened in these two phases).
- **Out of scope (later phases):** orientation adapters (Phase 2), populating slots with mod ids + motifs (Phase 3), modset-aware validation (Phase 4), the Java runtime resolver and optional mod dependencies (Phase 5).
- **Dependencies:** no new runtime mod dependency is registered yet; generation under `vanilla` remains fully vanilla-compatible.
- **User gate:** the final mod set is confirmed after the catalog is generated (Phase 0 gate).
