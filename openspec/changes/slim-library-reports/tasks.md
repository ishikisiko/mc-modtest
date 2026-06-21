## 1. Compact report serializers

- [x] 1.1 In `tools/buildgen/massing.py`, add `Node.to_summary_dict()` (structural fields only: `id/type/origin/size/orientation/tags`, plus optional `attach_to`/`side`; drop `meta`).
- [x] 1.2 In `tools/buildgen/massing.py`, add `MassingGraph.to_summary_dict()` returning `{meta, nodes}` where `meta` is kept in full and `nodes` is restricted to `VOLUME_TYPES` via `Node.to_summary_dict()`.
- [x] 1.3 In `tools/buildgen/compound.py`, add `ParcelNode.to_summary_dict()` (`id/type/cell_count/bbox/meta`, dropping `cells`).
- [x] 1.4 In `tools/buildgen/compound.py`, add `BuildingSlot.to_summary_dict()` (`id/archetype/origin/footprint_count/footprint_bbox/massing_graph/quality/door_info`, dropping `footprint`).
- [x] 1.5 In `tools/buildgen/compound.py`, add `CompoundGraph.to_summary_dict()` mirroring `to_dict()`'s shape but routing parcel/building slots through their summary forms; keep `meta` full.

## 2. Switch generators to the summary form

- [x] 2.1 `tools/generate_compound_library.py`: replace every `compound.to_dict()` with `compound.to_summary_dict()` (compound graphs) and `ctx.graph.to_dict()` with `ctx.graph.to_summary_dict()` (standalone reviews).
- [x] 2.2 `tools/generate_building_library.py`: replace `ctx.graph.to_dict()` with `ctx.graph.to_summary_dict()`.
- [x] 2.3 `tools/generate_civic_library.py`: replace `ctx.graph.to_dict()` with `ctx.graph.to_summary_dict()`.

## 3. Regenerate reports (canonical seeds, `--profile full`)

- [x] 3.1 `python tools/generate_compound_library.py --count 6 --profile full` (`compound_library_report.json`).
- [x] 3.2 `python tools/generate_compound_library.py --group cultivation_town --count 6 --base-seed 20260617 --profile full`.
- [x] 3.3 `python tools/generate_compound_library.py --group cultivation_sect --count 2 --base-seed 20260616 --profile full`.
- [x] 3.4 `python tools/generate_building_library.py --profile full` (`building_library_report.json`).
- [x] 3.5 `python tools/generate_building_library.py --group cultivation_town --count 3 --base-seed 20260613 --profile full`.
- [x] 3.6 `python tools/generate_building_library.py --group cultivation_sect --count 2 --profile full`.
- [x] 3.7 `python tools/generate_civic_library.py --profile full`.
- [x] 3.8 Note the generator ordering rule: for groups that share a gallery file between the building generator and the compound generator (cultivation_town, cultivation_sect), run the compound generator LAST so its compound entries win in the shared `gallery/<style>.mcfunction`.

## 4. Validation

- [x] 4.1 `python tools/validate_compound_library.py --count 6 --profile full` passes.
- [x] 4.2 `python tools/validate_compound_library.py --group cultivation_town --count 6 --profile full` passes.
- [x] 4.3 `python tools/validate_compound_library.py --group cultivation_sect --count 2 --profile full` passes.
- [x] 4.4 `python tools/validate_building_library.py --profile full` passes.
- [x] 4.5 `python tools/validate_civic_library.py --profile full` passes.
- [x] 4.6 `python tools/validate_generated_structures.py src/main/resources/data/myvillage/structure --profile full` passes.
- [x] 4.7 `python tools/buildgen/tests/test_chinese_courtyard_regression.py` passes (47 cultivation_sect/medieval NBTs byte-stable).
- [x] 4.8 Confirm `.nbt` output is byte-identical to the pre-edit state (spot-check: `chinese_courtyard_001.nbt` SHA-256 matches the pre-edit value recorded in the report).

## 5. Docs and version

- [x] 5.1 `AGENTS.md`: add a one-paragraph note that the library reports store the compound/building graph in summary form (per-cell lists folded into counts + bbox); readers needing the full per-cell graph should use `to_dict()` in code or read the `.nbt`.
- [x] 5.2 Bump the mod version per `openspec/config.yaml` — fix bump `0.15.0-fix1 → 0.15.0-fix2` — updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together.

## 6. Git-ignore `reports/` as generated output

Consumer audit (see proposal) confirmed 20 of 22 `reports/` files are deterministic generator/validator outputs that nothing reads back as a baseline. The other two are hand-curated inputs the build cannot re-derive.

- [x] 6.1 `.gitignore`: add `reports/*` with `!`-exceptions for `reports/town_distinctness_calibration.json` (tuning floors read by `tools/validate_runtime_town_plan.py`) and `reports/cultivation_style_baseline_hashes.txt` (pre-migration historical hash snapshot).
- [x] 6.2 `git rm --cached` the 20 ignore-safe report files (library reports, library validations, `generated_structure_validation.json`, `mod_block_fallback_validation.json`, `plaque_binding_validation.json`, `plaque_frame_assets.json`, `region_topology_validation.json`, `sect_generation_validation.json`, `town_generation_validation.json`, `phase5_blueprint_gallery_manifest.json`) — files stay on disk, just untracked.
- [x] 6.3 Confirm `git check-ignore` excludes a generated report and does NOT exclude the two must-keep files.
- [x] 6.4 Confirm generators/validators still read/write reports on disk after the untrack (`validate_compound_library.py` run + `test_chinese_courtyard_regression.py` pass).
- [x] 6.5 `AGENTS.md`: add the reports ignore-policy note (the two must-keep exceptions and why).
- [x] 6.6 `CHANGELOG.md`: add the ignore entry under Unreleased/Fixed.
