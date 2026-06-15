## 1. Fallback map generator (Phase 5 data source)

- [x] 1.1 Add a generator step under `tools/` that reads the per-style slot lists via `tools/buildgen/style.py` + `tools/buildgen/modset.py` and, for each non-`minecraft` id placed by any style, resolves the trailing `minecraft:` fallback of the slot(s) that list it.
- [x] 1.2 Define and document the deterministic precedence for an id that appears in slots with differing fallbacks (e.g. by style order then slot order), and a single global default-of-last-resort (proposed `minecraft:cobblestone`) so the map is total; log a generation-time note on any collision.
- [x] 1.3 Emit `src/main/resources/data/myvillage/mod_block_fallbacks.json` as `{ "modid:block": "minecraft:fallback[props]" }` and assert re-running with no source change is byte-identical.
- [x] 1.4 Add a check (validator or unit test) that every catalog mod id appearing in any shipped structure palette has an entry mapping to a valid `minecraft:` block state.

## 2. Java runtime resolver (Phase 5)

- [x] 2.1 Add `town/ModBlockFallback.java` that loads `mod_block_fallbacks.json` once at server start into a `Map<ResourceLocation, BlockState>` (parse values with `BlockStateParser`), with the default-of-last-resort and a one-time log on any parse failure.
- [x] 2.2 Add a template-load helper that fetches a structure's `CompoundTag`, walks `palette` and `palettes`, rewrites every entry whose `Name` is absent from `BuiltInRegistries.BLOCK` to its mapped fallback `Name` + `Properties`, then loads via `StructureTemplate.load(holderGetter, tag)`.
- [x] 2.3 Route `TownGenerator.realizeParcels` template loading (`TownGenerator.java:230-249`) through the fallback-patching helper instead of `level.getStructureManager().get(id)`.
- [x] 2.4 Route `/myvillage place` template placement through the same helper; emit a one-line summary count when any palette id is substituted (acceptance triage).
- [x] 2.5 Expose a `resolveBlockState(ResourceLocation)` helper on `ModBlockFallback` for future string-id placement; leave the existing programmatic `Blocks.<X>` motif placement vanilla (no behavior change).

## 3. Optional mod dependencies (Phase 5)

- [x] 3.1 Add six `[[dependencies.myvillage]]` blocks to `src/main/resources/META-INF/neoforge.mods.toml` for `ars_nouveau`, `farmersdelight`, `supplementaries`, `fetzisdisplays`, `mcwfurnitures`, `mcwwindows` with `type = "optional"`, `ordering = "AFTER"`, `side = "BOTH"`, and an open `versionRange`; verify the mod still loads standalone.

## 4. Regenerate, validate, preview (Phase 6)

- [x] 4.1 Regenerate libraries under `--profile full` (`python3 tools/generate_all_structures.py`); confirm the shipped tree is byte-stable vs `main`, and explain any diff before shipping.
- [x] 4.2 Regenerate `mod_block_fallbacks.json` and land all outputs into `src/main/resources/data/myvillage/`, packing them into the mod jar.
- [x] 4.3 Run the full validator suite under both `--profile full` and `--profile vanilla` (`validate_generated_structures.py`, `validate_compound_library.py` incl. `--group cultivation_town`, `validate_civic_library.py`, `validate_town_generation.py`, `validate_runtime_town_plan.py`, `check_style_policy.py`, `check_cultivation_forms.py`); refresh the reports under `reports/`.
- [x] 4.4 Refresh offline previews (`python3 tools/preview_structure.py --all`, `python3 tools/generate_town_plan_preview.py --count 3`) and review via the `out/preview/` entry point.
- [x] 4.5 Build the jar with `./gradlew build`.
- [ ] 4.6 Stage manual acceptance: run `/myvillage town` and `/myvillage place` once with the decor mods installed and once without, confirm authored mod blocks place real-block / vanilla-fallback respectively with no air holes and no crash, and capture screenshots.

## 5. Version, docs, and command manual (Phase 6)

- [x] 5.1 Bump the mod version `0.6.0-fix3` → `0.7.0` (large feature) across `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together.
- [x] 5.2 Update the README usage / command manual (`/myvillage town`, `/myvillage place`) to note that placement degrades gracefully to vanilla fallbacks when the optional decor mods are absent, and keep the documented `/myvillage list`/`place`/`gallery` entries current.
- [ ] 5.3 Mark Phase 5 and Phase 6 `done` in `docs/external_mod_integration_plan.md` status, referencing change `add-mod-runtime-fallback`.
