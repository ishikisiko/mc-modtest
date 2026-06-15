## Context

`exmod/` stages six external decoration mods (Fetzi's Asian Decoration, Macaw's Furniture, Macaw's Windows, Supplementaries, Farmer's Delight, Ars Nouveau) plus a design report, but nothing in the pipeline reads them yet. The buildgen layer already resolves blocks through abstract material slots (`tools/buildgen/style.py`, per-style JSONs in `tools/buildgen/styles/`) and emits registry-id strings, so mod blocks are "just `modid:block[...]` strings" with no class dependency. This change implements the two foundational phases from `docs/external_mod_integration_plan.md`: Phase 0 (extract a catalog) and Phase 1 (make slot loading namespace-aware with a vanilla fallback convention). No mod block is placed and no generated output changes under the `vanilla` profile.

Settled architecture constraints (from the plan) that bind this design: semantic slots are *added* never replaced; ids stay as strings; every mod is optional with a vanilla fallback; generation and runtime are two separate resolution layers (only the generation layer is in scope here); a slot is "material family", a motif is "composition" (no new motifs here).

## Goals / Non-Goals

**Goals:**
- A reproducible `tools/extract_mod_catalog.py` → `exmod/mod_block_catalog.json` that records, per mod, each block id, its blockstate property grammar, and texture names, merged with 落点 design intent.
- `load_style(style_id, available_namespaces)` filters slot lists by namespace at load time, with the existing slot-resolution contracts untouched.
- A fallback convention enforced across all style JSONs: every slot list ends with a `minecraft:` id.
- Four new optional slots declared empty-but-vanilla: `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, `MARKET_FITTINGS`.

**Non-Goals:**
- Placing any mod id into a slot (Phase 3).
- Orientation adapters / blockstate grammar translation (Phase 2).
- Modset-aware validation forbid/allow lists (Phase 4).
- Java runtime resolver and `neoforge.mods.toml` optional deps (Phase 5).
- Regeneration / in-game preview (Phase 6).

## Decisions

**1. Catalog is generated from assets, not jars, and stored as JSON.**
The extractor reads `assets/<modid>/blockstates/*.json` out of `exmod/mod_assets.zip` (with the jar zip as a fallback source if needed). Rationale: blockstate JSON already declares the property/variant grammar Phase 2 needs, and reading assets keeps the tool free of any classloading or mod-runtime dependency. Alternative considered — introspecting `mod_jars.zip` registries at runtime — rejected: it would couple catalog generation to a running game and violate the "no external class imports" constraint.

**2. `available_namespaces` is an optional second arg; single-arg `load_style` stays the default path.**
Filtering happens once at load, producing a `Style` whose `material_slots` already exclude inactive namespaces; `primary()/alternates()/pick()/slot_entry()` are unchanged because they operate on whatever list the Style holds. Rationale: zero downstream churn, and the `vanilla` profile is just `available_namespaces={"minecraft"}`. Alternative — filtering lazily inside each resolver — rejected: it would touch every call site and risk inconsistent views of a slot within one build.

**3. Fallback convention is a load-time invariant, checked, not assumed.**
Every slot list must end with a `minecraft:` id; a check flags any required slot whose last entry is non-vanilla. Rationale: after namespace filtering under an empty mod set, the trailing vanilla entry is the only thing guaranteeing a non-air resolution; making it a checked invariant prevents a future style edit from silently producing an empty slot. The check distinguishes required slots from legitimately-omitted optional slots (existing omit-and-skip behavior preserved).

**4. New slots ship vanilla-only this phase.**
`ROOF_TILE` → vanilla tiled-look fallback (e.g. a stair/slab family already used for roofs), `PAPER_LANTERN` → `minecraft:lantern`, `RITUAL_ANCHOR` → a vanilla anchor block, `MARKET_FITTINGS` → a vanilla fitting. Rationale: declaring the slot names now lets Phase 2/3 slot in mod ids without touching the loader again, while keeping `vanilla` output byte-stable. Concrete vanilla picks are an implementation detail of the style JSONs, not a spec requirement.

**5. Modset profile is a thin concept, not new infrastructure.**
`vanilla` = `{"minecraft"}`; `full` = `{"minecraft", <confirmed mod namespaces>}`. The confirmed mod set lives in the catalog so there is one source of truth. No profile registry/class is introduced; callers pass the namespace set.

## Risks / Trade-offs

- **Blockstate JSON grammar varies by mod (vanilla-style `variants` vs `multipart`)** → the extractor records property names/value domains generically and does not try to normalize grammar in Phase 0; normalization is Phase 2's adapter problem.
- **A future style edit could append a non-vanilla id last and break the fallback guarantee** → the load-time fallback-convention check fails fast and names the style+slot.
- **Catalog could drift from the actual jars if mods update** → the tool is re-runnable and deterministic, so regeneration is the remediation; the catalog records the confirmed mod set/versions context for traceability.
- **Scope creep into placing mod blocks** → explicitly deferred; this change asserts no output change under `vanilla`, which is the regression guard.

## Migration Plan

1. Land the extractor and generate `exmod/mod_block_catalog.json`; review the catalog and confirm the final mod set (Phase 0 user gate).
2. Add `available_namespaces` to `load_style` (default = all active) and the fallback-convention check.
3. Append trailing vanilla fallbacks where missing and add the four new slots to the style JSONs.
4. Verify: generating under `vanilla` produces output identical to today; loading under `full` retains mod entries (even though none are placed yet).

Rollback: revert the style.py and style JSON edits; the catalog JSON is inert data and can remain.

## Open Questions

- Exact vanilla fallback block for each new slot (`RITUAL_ANCHOR`, `MARKET_FITTINGS` especially) — resolved during implementation against existing palette, not blocking.
- Whether `full` should be split into a middle profile (Macaw's-only) — deferred to Phase 4 per the plan; not needed here.
