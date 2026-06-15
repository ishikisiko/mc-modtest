## 1. Phase 2 — Orientation adapter (vanilla families first)

- [x] 1.1 Add an orientation-adapter module/interface in `tools/buildgen/` that resolves `(family, cell role, orientation) → blockstate` string, with a family registry keyed by name
- [x] 1.2 Register `vanilla_stairs` and `vanilla_slab` families that reproduce the current `stair_state` / `slab_state` output (`facing/half/shape/waterlogged`, `type/waterlogged`)
- [x] 1.3 Re-point `stair_state` / `slab_state` in `tools/buildgen/ops.py` to the adapter (or keep them as thin wrappers) so existing roof/eave handlers call through the adapter
- [x] 1.4 Make an unregistered family raise a clear error naming the family (no guessed/partial blockstate)
- [x] 1.5 Confirm existing roof generation (`gable_roof`, `tiered_eave_roof`, `lean_to_roof`) produces byte-identical blocks after re-pointing through the adapter

## 2. Phase 2 — Awning family (first non-vanilla grammar)

- [x] 2.1 Register the `supplementaries:awning` family with grammar `facing` + `bottom` + `slanted` (read from the catalog, no vanilla-only props)
- [x] 2.2 Map an eave/canopy cell role + facing to `facing=outward`, `slanted=true` for the sloped edge, and `bottom` per vertical position
- [x] 2.3 Verify awnings on opposite walls receive opposite (outward) facings, and a single eave edge slopes outward over the wall below
- [x] 2.4 Generate a preview of an awning eave and visually confirm orientation before broad use

## 3. Phase 3 — Populate material slots (data, front-loaded, fallback preserved)

- [x] 3.1 `ROOF_TILE` ← `supplementaries:awning*` + `supplementaries:{stone,blackstone}_tile_{stairs,slab}`, vanilla fallback kept last
- [x] 3.2 `PAPER_LANTERN` ← `ars_nouveau:{sconce,sourcestone_sconce,source_lamp}` + `supplementaries:candle_holder*`, vanilla fallback kept last
- [x] 3.3 `RITUAL_ANCHOR` ← `ars_nouveau:{brazier_relay,arcane_pedestal,arcane_core,agronomic_sourcelink,alchemical_sourcelink}`, vanilla fallback kept last
- [x] 3.4 `MARKET_FITTINGS` ← curated `farmersdelight` + `supplementaries` + `fetzisdisplays` props, vanilla fallback kept last
- [x] 3.5 Enrich existing `FURNITURE` (`mcwfurnitures`), `LIGHTING`, and window/wall slots (`mcwwindows`) at the front, fallback kept last
- [x] 3.6 Apply 3.1–3.5 across `chinese_courtyard`, `cultivation_sect`, and `cultivation_town` JSONs per each style's role relevance
- [x] 3.7 Confirm every inserted id's namespace is in the catalog's confirmed mod set (no `fetzisasiandeco` / unstaged namespaces)
- [x] 3.8 Confirm the Phase 1 fallback-convention check still passes (every required slot ends with a `minecraft:` id)

## 4. Phase 3 — Decoration motifs

- [x] 4.1 Add a `market_stall` motif: counter from `MARKET_FITTINGS`, canopy via the awning adapter, goods display; register it and add to relevant styles' `allowed_motifs`
- [x] 4.2 Route `incense_altar` / `spirit_array` focal blocks through the `RITUAL_ANCHOR` slot (brazier / arcane pedestal), supporting blocks via existing slots
- [x] 4.3 Add/extend a sect gate / 牌坊 motif resolving signage + `MARKET_FITTINGS` through slots
- [x] 4.4 Ensure each motif resolves via `slot_entry` / `optional_slot_entry` so an omitted optional slot skips that element rather than failing
- [x] 4.5 Confirm `validate_style_vocabulary` accepts the new/edited motifs for every style that lists them

## 5. Verification

- [x] 5.1 Generate every affected library under the `vanilla` profile and confirm output is byte-identical to pre-change `vanilla` (no mod ids, no air)
- [x] 5.2 Generate under the `full` profile and confirm mod ids appear at intended spots: awning eaves oriented correctly, `RITUAL_ANCHOR`/`MARKET_FITTINGS` ids in motifs
- [x] 5.3 Run a motif under both profiles and confirm vanilla degradation (all `minecraft:` ids, no air) and full population
- [x] 5.4 Run existing style/vocabulary checks to confirm no regression from the adapter, slot population, and motif changes
