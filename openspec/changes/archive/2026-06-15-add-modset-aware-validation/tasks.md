## 1. Modset resolver (shared source of truth)

- [x] 1.1 Add `tools/buildgen/modset.py` with `load_modset(profile, catalog_path=None) -> ModsetProfile`, reusing `style.modset_namespaces` for namespaces and reading legal mod ids from `exmod/mod_block_catalog.json` (`entry["id"]` over `confirmed_mod_namespaces`)
- [x] 1.2 `ModsetProfile` is frozen and exposes `name`, `namespaces`, `mod_block_ids`, plus `palette_block_errors(palette) -> list[str]` returning `forbidden_mod_blocks` (namespace not allowed) and `unknown_mod_blocks` (namespace allowed but id absent from catalog); never touches `minecraft:` ids
- [x] 1.3 Unknown profile name raises a clear `KeyError`/`ValueError` naming the profile (reuse the `modset_namespaces` error path)

## 2. Generators take `--profile`

- [x] 2.1 Add `--profile {vanilla,full}` (default `full`) to `generate_building_library.py`, `generate_compound_library.py`, `generate_civic_library.py`, resolving `load_style(style_id, available_namespaces=modset_namespaces(profile))`
- [x] 2.2 Thread `--profile` through `generate_all_structures.py` to each sub-generator invocation (default `full`)
- [x] 2.3 Confirm `--profile full` output is byte-identical to the current no-filter output for every affected library (no-filter equivalence)
- [x] 2.4 Confirm `--profile vanilla` output contains no non-`minecraft` id

## 3. Validators take `--profile` and enforce mod-id legality

- [x] 3.1 Add `--profile {vanilla,full}` (default `full`) to `validate_building_library.py`; scope its existing `unknown_block_ids` check to the `minecraft` namespace and add `ModsetProfile.palette_block_errors` results to each structure's errors
- [x] 3.2 Add `--profile` (default `full`) to `validate_compound_library.py` and apply `palette_block_errors` per NBT palette
- [x] 3.3 Add `--profile` (default `full`) to `validate_generated_structures.py` and apply `palette_block_errors` per file palette
- [x] 3.4 Make `validate_structure_json.py` modset-aware: under `full`, accept confirmed-namespace ids present in the catalog; under `vanilla`, keep the current non-`minecraft` rejection; vanilla `minecraft` registry check unchanged

## 4. Verification

- [x] 4.1 `full` validation of the shipped artifacts passes clean for every validator (regression guard)
- [x] 4.2 Generate every affected library under `--profile vanilla` into a temp dir and confirm `--profile vanilla` validation passes clean with no mod ids
- [x] 4.3 Inject a non-confirmed-namespace id → `forbidden_mod_blocks`; inject an in-namespace non-catalog id → `unknown_mod_blocks`
- [x] 4.4 Confirm `vanilla` validation of the (full) shipped artifacts fails with `forbidden_mod_blocks` (the forbid path is real)
- [x] 4.5 Run `check_style_policy.py`, `check_cultivation_forms.py`, `validate_town_generation.py`, `validate_runtime_town_plan.py` to confirm no regression
- [x] 4.6 Update `AGENTS.md` validate-command list and `docs/external_mod_integration_plan.md` status (Phase 4 done)
