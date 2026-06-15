## Why

Phases 0–4 made external-mod decor real **at generation time**: the shipped libraries
under `src/main/resources/data/myvillage/structure/` now carry confirmed catalog ids
(`ars_nouveau`, `farmersdelight`, `supplementaries`, `fetzisdisplays`, `mcwfurnitures`,
`mcwwindows`) at their intended 落点, and both `vanilla` and `full` profiles validate
clean against one catalog. But nothing protects those ids **at runtime**. When
`/myvillage town` (or `/myvillage place`) loads a template whose palette names a mod
block, Minecraft resolves it through the live block registry at NBT-load time —
and an absent mod id resolves to `minecraft:air`, not a fallback. So in any world
without the decor mods installed, every market canopy, sconce, brazier, and furniture
piece silently becomes a one-block hole. The whole "optional + fallback, never place
air" guarantee (architecture decision 3) is asserted in the Python slot lists but is
**not enforced where blocks actually get placed**. This change closes that gap (plan
Phase 5) and then regenerates, validates, and previews the result end-to-end so the
aesthetic can be accepted in-game (plan Phase 6) — completing the 7-phase integration.

## What Changes

- **Runtime block-fallback resolver (Phase 5).** Introduce a resolver that maps any
  block id absent from `BuiltInRegistries.BLOCK` to a deterministic vanilla fallback
  state, sourced from the **same** style slot data the generator uses (the trailing
  `minecraft:` fallback of whatever slot lists that mod id), exported once as a
  runtime data file so Python stays the single source of truth. No hardcoded
  `modid → block` map in Java.
- **Template placement degrades gracefully (Phase 5).** Route `/myvillage town`
  template placement through the resolver: before a structure's palette reaches the
  registry's AIR default, registry-absent palette names are rewritten to their mapped
  fallback. With the mods installed the real blocks place; without them the vanilla
  fallback places — never air, never a crash. Vanilla-only templates are byte-identical
  to today.
- **Optional mod dependencies (Phase 5).** Declare the six confirmed decor mods as
  `type = "optional"` dependencies in `neoforge.mods.toml` so the modpack loads them
  in the right order when present and the mod still loads standalone when they are not.
- **Regenerate, validate, preview, iterate (Phase 6).** Regenerate the libraries under
  `--profile full`, re-run the full validator suite under both profiles, refresh the
  offline previews, and capture a staged manual-acceptance pass (mods-on vs mods-off)
  to confirm fallbacks place instead of air.
- **Version + docs (Phase 6).** Bump the mod version, update the command manual / README,
  and record Phase 5–6 completion in the integration plan and CHANGELOG.

## Capabilities

### New Capabilities
- `runtime-mod-fallback`: A runtime resolver and its generated `mod_id → vanilla
  fallback state` data file that guarantee any block id missing from the live registry
  is placed as a deterministic vanilla fallback (never air, never a crash), plus the
  optional-dependency declarations that let the decor mods load when present. The
  fallback map is exported from the same style slot data the generator reads.

### Modified Capabilities
- `town-realization`: Runtime template placement SHALL resolve every palette block id
  through the fallback resolver, so a world missing a decor mod places the vanilla
  fallback rather than air, while a world with the mod places the real block; output is
  unchanged for templates that name only vanilla ids.

## Impact

- **Code:** new Java resolver (e.g. `town/ModBlockFallback.java`) consumed by
  `TownGenerator.realizeParcels` template loading and any future string-id placement;
  new generator step in `tools/buildgen/` (or `tools/`) that exports the fallback map
  data file from the style slot lists; `neoforge.mods.toml` optional-dependency block.
- **Artifacts:** new generated runtime fallback data file under
  `src/main/resources/data/myvillage/`; libraries regenerated under `--profile full`
  (expected byte-stable vs current `main`, since Phase 3 already populated the ids);
  refreshed validation reports and offline previews.
- **Dependencies:** `neoforge.mods.toml` gains optional deps on `ars_nouveau`,
  `farmersdelight`, `supplementaries`, `fetzisdisplays`, `mcwfurnitures`, `mcwwindows`;
  no new required deps, no new external class imports (ids stay strings).
- **Profiles:** `full` is the shipped tree; the resolver makes a `vanilla`-world install
  safe at runtime without regenerating to the `vanilla` profile.
- **Docs/version:** `gradle.properties`, `neoforge.mods.toml`, README jar-name examples,
  `CHANGELOG.md`, and `docs/external_mod_integration_plan.md` status updated together.
- **Out of scope:** new mod families or new 落点 (the catalog set is frozen for this
  change); programmatic motif blocks in `TownGenerator` stay vanilla today and only gain
  access to the resolver helper for future use.
