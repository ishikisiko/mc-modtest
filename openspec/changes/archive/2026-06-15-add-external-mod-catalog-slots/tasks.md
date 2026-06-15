## 1. Phase 0 — Mod block catalog extraction

- [x] 1.1 Add `tools/extract_mod_catalog.py` that opens `exmod/mod_assets.zip` and iterates `assets/<modid>/blockstates/*.json` per mod namespace (jar zip as fallback source), importing no mod classes
- [x] 1.2 For each block, record block id (`<modid>:<block>`), blockstate property names + value domains, and referenced texture names
- [x] 1.3 Parse `exmod/deep-research-report.md` for 落点 design intent and associate block families with role labels aligned to `ROOF_TILE` / `PAPER_LANTERN` / `RITUAL_ANCHOR` / `MARKET_FITTINGS`
- [x] 1.4 Record the confirmed external mod set (list of namespaces) in the catalog output
- [x] 1.5 Emit `exmod/mod_block_catalog.json` grouped by mod namespace; make the run deterministic (stable ordering) so re-runs are equivalent
- [x] 1.6 Handle missing `exmod/mod_assets.zip` with a clear error and no partial catalog write
- [x] 1.7 Run the extractor, review `mod_block_catalog.json`, and confirm the final mod set (Phase 0 user gate)

## 2. Phase 1 — Namespace-aware slot loading

- [x] 2.1 Add optional `available_namespaces` arg to `load_style(style_id, available_namespaces=None)` in `tools/buildgen/style.py`; when `None`, keep all slot entries (existing default behavior)
- [x] 2.2 Filter each `material_slots` list to entries whose block-id namespace is in `available_namespaces`, at load time, before validation/resolution
- [x] 2.3 Confirm `primary()`, `alternates()`, `pick()`, `slot_entry()`, and `optional_slot_entry()` behave unchanged on the filtered `Style` (no call-site edits needed)
- [x] 2.4 Define modset profiles as namespace sets: `vanilla = {"minecraft"}`, `full = {"minecraft", *confirmed_mod_namespaces}` (sourced from the catalog)

## 3. Phase 1 — Fallback convention

- [x] 3.1 Add a load-time check that every required material slot list ends with a `minecraft:` id; flag violations with style id + slot name (respect existing omit-and-skip for optional slots)
- [x] 3.2 Audit `tools/buildgen/styles/*.json` and append a trailing vanilla fallback to any slot list missing one
- [x] 3.3 Verify that loading with `available_namespaces={"minecraft"}` leaves every required slot non-empty (resolves to its vanilla fallback, never air)

## 4. Phase 1 — New decoration slots

- [x] 4.1 Recognize new optional slots `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, `MARKET_FITTINGS` in the style schema/loader (treat as optional like `SPIRIT_CRYSTAL` / `RITUAL_METAL`)
- [x] 4.2 Add the four new slots to the relevant style JSONs with only their vanilla fallback entry (no mod ids yet)
- [x] 4.3 Confirm a generator requesting an omitted new slot skips placement rather than failing style loading

## 5. Verification

- [x] 5.1 Generate a building library under the `vanilla` profile and confirm output is unchanged vs. current `main` (no mod ids, no air)
- [x] 5.2 Load every style under the `full` profile and confirm mod + vanilla entries coexist in declared order (even though no mod ids are placed yet)
- [x] 5.3 Run existing style/validation checks to confirm no regression from the loader and slot changes
