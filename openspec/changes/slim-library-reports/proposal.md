## Why

The library report JSONs under `reports/` ballooned to tens of thousands of lines — the largest (`cultivation_town_compound_library_report.json`) is **164 269 lines / 3.2 MB**, and three others exceed 70 000 lines. Inspection traced every offender to one root cause: each library generator serializes the full `graph.to_dict()` into the report, and that payload embeds **per-cell coordinate lists** (`parcel_nodes[].cells`, `building_slots[].footprint`, and node `meta` dicts that carry roof/storefront plans) that have **no downstream consumer**.

Verified consumer audit (the sole reader of `compound_graph` is `tools/validate_compound_library.py`):

- **cultivation_town** path reads only `building_slots[].id`, `building_slots[].massing_graph.meta.frontage.{side,opening_cells,volume}`, and the resolved volume node's `origin/size` — the 8–11 interior `zone_*`/`path_*`/`colonnade` nodes per slot are never read.
- **cultivation_sect** path reads only `meta.terrace_levels`.
- **chinese_courtyard** path reads **nothing** from `compound_graph` at all.
- `building_library_report.json` / `civic_library_report.json` `massing_graph` field: `validate_building_library.py` never reads it (it re-validates NBT from disk and reads top-level `size`/`block_count`/`glass`/`function_blocks`).

The byte-stability regression test (`tools/buildgen/tests/test_chinese_courtyard_regression.py`) hashes `.nbt` files only, not report JSON — so slimming the reports is safe. `to_dict()` has no consumer besides the reports (no sidecar, no preview, no Java reader).

## What Changes

- Add compact `to_summary_dict()` methods alongside the existing `to_dict()` (the in-memory `to_dict()` is left untouched — it is type-internal and stays available for any future per-cell debugging):
  - `ParcelNode.to_summary_dict()`: drops `cells` → `cell_count` + `bbox`.
  - `BuildingSlot.to_summary_dict()`: drops `footprint` → `footprint_count` + `footprint_bbox`; keeps the massing graph via its own summary form.
  - `CompoundGraph.to_summary_dict()`: same top-level shape as `to_dict()`, with parcel/building slots in summary form; `meta` kept in full (sect needs `terrace_levels`).
  - `MassingGraph.to_summary_dict()`: keeps `meta` in full (cultivation_town needs `meta.frontage` including its `opening_cells`); keeps only **volume-type** nodes (`VOLUME_TYPES`) with stripped per-node `meta`, because the frontage side-bounds check resolves `frontage.volume` (always a volume node set in `archetypes._ensure_frontage`) and reads its origin/size.
  - `Node.to_summary_dict()`: structural fields (`id/type/origin/size/orientation/tags`) only.
- Switch the three library generators to emit the summary form into the report:
  - `tools/generate_compound_library.py` (`compound.to_summary_dict()` for compound graphs, `ctx.graph.to_summary_dict()` for the standalone reviews).
  - `tools/generate_building_library.py` (`ctx.graph.to_summary_dict()`).
  - `tools/generate_civic_library.py` (`ctx.graph.to_summary_dict()`).
- Regenerate every affected report with the canonical seeds/profiles.
- Note the report-format change in `AGENTS.md` (one paragraph).

## Impact

- **Code**: `tools/buildgen/compound.py` (3 new methods), `tools/buildgen/massing.py` (2 new methods), `tools/generate_compound_library.py`, `tools/generate_building_library.py`, `tools/generate_civic_library.py` (1-line edits each). No generation logic changes — `.nbt` output is byte-identical (proven: `chinese_courtyard_001.nbt` SHA-256 after regeneration matches the pre-edit value).
- **Reports**: all `*_library_report.json` shrink. Largest reductions: `cultivation_town_compound` 164 269 → 8 469 lines (95%), `compound` 126 048 → 6 094 (95%), `cultivation_sect_compound` 72 775 → 2 561 (96%), `building` 14 893 → 5 731 (62%).
- **Repo hygiene**: `.gitignore` now excludes `reports/*` (deterministic generator/validator outputs) with `!`-exceptions for two hand-curated inputs the build cannot re-derive — `reports/town_distinctness_calibration.json` (tuning floors read by `validate_runtime_town_plan.py`) and `reports/cultivation_style_baseline_hashes.txt` (a pre-migration historical hash snapshot). The 20 previously-committed generated reports were `git rm --cached`'d (kept on disk). An audit confirmed none of the 20 is read back as a golden baseline by any test, spec, gradle task, or Java code; the generators/validators continue to read/write them on disk exactly as before.
- **Assets**: none. `.nbt` files, mcfunction galleries, and `settlement_meta` sidecars are unchanged by this change (their working-tree diffs vs HEAD are pre-existing from `fix-courtyard-ground-walkability` / `rebuild-chinese-courtyard`).
- **Specs**: none. The `validation` spec governs what validators check, not the report's internal structure; no spec delta is needed.
- **Docs**: `AGENTS.md` (summary-form note + ignore-policy note); `CHANGELOG.md` (slim + ignore entries); README jar-name example bump (`0.15.0-fix1` → `0.15.0-fix2`).
- **Compatibility**: pure diagnostic report change. No gameplay, no command surface, no world-data change. The shipped tree stays `full`-profile.
- **Out of scope**: slimming the in-memory `to_dict()` (kept for debugging); the `RegionGraph.to_dict()` used by region topology/runtime fixtures (unrelated same-named method); any report field that a validator reads (`meta.frontage`, `meta.terrace_levels`, volume node `origin/size`, top-level `variant`/`silhouette_scores`/`passed`/`name`).
