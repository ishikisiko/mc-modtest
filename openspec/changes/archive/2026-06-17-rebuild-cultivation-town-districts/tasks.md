## 1. Phase 1 — District plan & scale (`town-districts`, `settlement-group`, `town-plan`)

- [x] 1.1 Replace `cultivation_town`'s flat `soft_functional_brief` in [tools/buildgen/groups.py](../../../tools/buildgen/groups.py) with a `district_brief` (ordered `gate`/`market`/`residential`/`civic_core`/`fringe`, each with density, storey band, material register, archetype roster)
- [x] 1.2 Rewrite `generate_town_plan` in [tools/buildgen/town.py](../../../tools/buildgen/town.py) to partition the footprint into districts and subdivide parcels within each, inheriting district briefs; derive importance tier from district kind
- [x] 1.3 Express the ritual axis (plaza/paifang/lanterns) inside the `civic_core` district instead of spanning the whole town; keep the shrine as sole dominant landmark
- [x] 1.4 Raise the supported footprint to ~160×160 in the planner and `estimate_block_budget`; tune per-district density to a reviewable block budget
- [x] 1.5 Extend `validate_town_plan`/[tools/validate_runtime_town_plan.py](../../../tools/validate_runtime_town_plan.py) to assert district partition, core-outranks-fringe hierarchy, and determinism
- [x] 1.6 Mirror the districted plan in [TownGenerator.java](../../../src/main/java/com/example/myvillage/town/TownGenerator.java) `plan()`; raise `MAX_FOOTPRINT_AXIS` to 160
- [x] 1.7 Replace the `loaded()` hard refusal with chunk-ticket forced loading across the footprint, released in a `finally`; report (not silently skip) regions that cannot be loaded
- [x] 1.8 Regenerate town-plan previews (`generate_town_plan_preview.py`) and confirm `validate_runtime_town_plan.py` passes the new invariants

## 2. Phase 2 — Street frontage & alleys (`street-frontage`)

- [x] 2.1 Add frontage alignment to `town.py` plan: tag `market`/`residential` parcels with a frontage edge and shared-gable adjacency; emit leftover narrow gaps as typed `alley` regions
- [x] 2.2 Implement party-wall frontage placement in `TownGenerator.java` (align street-facing wall to frontage edge, butt neighbors at gable lines, break runs on corners/alleys/slope); remove the centered-lot plinth ring for frontage parcels
- [x] 2.3 Keep yard/courtyard behind frontage; ensure alleys receive no plinth
- [x] 2.4 Add a frontage-sparsity invariant to the validator (fail centered-lot gaps, pass continuous party-wall rows) and extend [tools/validate_town_generation.py](../../../tools/validate_town_generation.py)

## 3. Phase 3 — Vertical landmarks & skyline (`vertical-landmark`)

- [x] 3.1 Register `pagoda`, `pavilion`, `bell_drum_tower` forms in [tools/buildgen/ops.py](../../../tools/buildgen/ops.py) from existing terrace + `tiered_eave_roof` vocabulary (no string-prefix dispatch); list them in the cultivation town style's `allowed_roof_types`/`allowed_motifs`
- [x] 3.2 Add the three archetypes to [tools/buildgen/archetypes.py](../../../tools/buildgen/archetypes.py) and the `cultivation_town` roster's `civic_core` district
- [x] 3.3 Add the skyline rule to the plan (civic core requires ≥N volumes above a height threshold, ≥1 being a tall archetype) and a skyline invariant to the validator
- [x] 3.4 Generate the new vertical-landmark templates and confirm `check_cultivation_forms.py`/`check_style_policy.py` pass and `silhouette_score` improves in the building report

## 4. Phase 4 — Cultivation street life (`cultivation-street-life`)

- [x] 4.1 Replace placeholder street/open-region dressing in `TownGenerator.java` with a cultivation vocabulary (幌子 banners, 药圃/灵田 beds, 炼丹炉, 法器摊, 阵纹 floors)
- [x] 4.2 Route external-decor fixtures (staged `fetzisdisplays`) through profile-gated slots resolved from [tools/buildgen/modset.py](../../../tools/buildgen/modset.py)/`exmod` so `vanilla` falls back and `full` uses decor blocks
- [x] 4.3 Place villager (and optional spirit-beast) inhabitants across districts, scaled to parcel count
- [x] 4.4 Verify `validate_mod_block_fallbacks.py` and generation pass under both `--profile vanilla` and `--profile full`

## 5. Acceptance, resources & docs

- [x] 5.1 Run `python3 tools/generate_all_structures.py` and `python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure`
- [x] 5.2 Run the full validator suite from AGENTS.md (compound/civic libraries, runtime town plan, town generation, style policy, cultivation forms, plaque bindings/fallbacks)
- [x] 5.3 Reclassify the `cultivation_town_NNN` compound library as district fill in `settlement-group`/docs; keep `/myvillage place cultivation_town_001` as a documented fragment
- [x] 5.4 Regenerate previews (`preview_structure.py --all`, `generate_town_plan_preview.py --count 3`) and start the public review server per AGENTS.md
- [x] 5.5 Update `README.md` (`/myvillage town` footprint/behavior), `AGENTS.md` (runtime town composition + compound-library role), and `CHANGELOG.md` (minor feature bump across `gradle.properties`, `neoforge.mods.toml`, README jar names)
- [x] 5.6 `./gradlew build` and confirm the buildable jar before requesting visual review
