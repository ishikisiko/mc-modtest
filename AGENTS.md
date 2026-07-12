# Agent Notes

This repository is scaffolded for a Minecraft town mod and related blueprint conversion tools.
Spend time on thinking; you do not need to use the commentary channel to report progress to me.


## Working Conventions

- Start from `docs/ai-kb/INDEX.md`; it maps narrative docs in `docs/ai-kb/` and normative specs in `openspec/specs/`.
- Keep `docs/ai-kb/` concise and factual. New docs must be listed in `docs/ai-kb/INDEX.md` and cross-link same-topic specs.
- Keep examples small; prefer deterministic converters and validators in `tools/`.
- `src/main/resources/data/myvillage/` is the current implemented namespace. Treat `minecraft_town_mod` as a historical placeholder unless a dedicated rename updates code, resources, specs, and docs together.
- Implementation is not complete until generated resources, a practical jar build, and user-facing `README.md` command/usage docs are current.
- `reports/` is ignored deterministic output except `reports/town_distinctness_calibration.json` and `reports/cultivation_style_baseline_hashes.txt`; do not delete those two, and do not `git add` generated reports.
- Add settlement families through `tools/buildgen/groups.py`; do not infer families from `style_id` prefixes.
- Resolve modset legality through `tools/buildgen/modset.py` and `exmod/mod_block_catalog.json`; do not hardcode mod ids/namespaces in validators.
- Add roof/motif forms through registries in `tools/buildgen/ops.py` plus style allow-lists; do not dispatch by string-prefix matching.
- Add/recalibrate regions through JSON under `src/main/resources/data/myvillage/worldgen/` plus `tools/buildgen/region_topology.py`; runtime region binding is passive, and `next_rung_regions` stays a set at tier ties.
- For visual/aesthetic structure changes, inspect current preview/reference evidence and give a critical design judgment before editing generators.
- Version/changelog mechanics are single-sourced in `openspec/config.yaml` (`rules.tasks`); reference that rule rather than restating it.
- GenOps orchestration lives in `genops/` and `tools/genops/`; user-facing use is natural-language Commander conversation, while CLI commands are backend tools the agent runs itself.
- For CRAFT-required work, keep the owner interface decision-only: confirm need, scope/depth, direction, and verdicts; the Commander owns change names, run ids, pipelines, task ids, worker routing, checks, and archive unless audit detail or a backend failure is requested.
- Before manual visual review, run the acceptance checklist, build the artifact when practical, update command docs, generate the visual acceptance report, inspect representative PNGs, and serve `out/preview/` over HTTP until review ends.
- Chunky automation is a staged handoff, not visual acceptance. Headless Chunky renderer PNGs cannot prove custom `myvillage:` block appearance until a dedicated compatibility path exists.
- When commands or acceptance prep steps change, update `README.md`, this `AGENTS.md`, and relevant `openspec/specs/` documents together.
- Flying-sword changes must keep the custom payload input-only and run `tools/validate_rideable_flying_sword.py`, tests, build, and the acceptance server; riding and appearance still require in-game review.
- Cultivation-core changes must keep immutable profile replacement behind `CultivationService`, use Attachment `copyOnDeath` without a cultivation `PlayerEvent.Clone` copy, keep snapshots clientbound-only, and run the cultivation validator, tests, build, and a bounded acceptance-server smoke; lifecycle gameplay checks remain manual.
- Git workflow: branches off `main`; unless told otherwise, fast-forward-merge finished change branches back to `main` and push after committing. Keep branches/PRs open only when explicitly asked.
- 最后每次回报的时候用中文。

## Probes

- KB/governance: `docs/ai-kb/INDEX.md`, `openspec/specs/docs-knowledge-base/spec.md`.
- Reports/acceptance: `docs/ai-kb/09_validation_checklist.md`, `openspec/specs/validation/spec.md`.
- GenOps/CRAFT: `CRAFT.md`, `docs/ai-kb/19_genops.md`, `openspec/specs/genops/spec.md`, `genops/README.md`.
- Resources/commands: `openspec/specs/resource-export/spec.md`.
- Mod items: `docs/ai-kb/22_mod_item_creation.md`, `mod-item-pipeline`, `.codex/skills/mod-item-creation/SKILL.md`.
- Custom entities: `docs/ai-kb/26_custom_entities.md`, `genops/contracts/entities/`, `custom-entity-runtime`, `tools/validate_custom_entities.py`, `resource-export`, `validation`.
- Rideable flying sword: `docs/ai-kb/27_rideable_flying_sword.md`, `add-rideable-flying-sword`, `tools/validate_rideable_flying_sword.py`, `validation`.
- Cultivation core: `docs/ai-kb/28_cultivation_core.md`, `cultivation-player-profile`, `cultivation-definition-registries`, `cultivation-persistence-lifecycle`, `cultivation-state-synchronization`, `cultivation-debug-commands`, `cultivation-core-validation`, `tools/validate_cultivation_core.py`, `python3 tools/run_chunky_acceptance.py --stage 1`, `validation`.
- Groups/style/modset/forms: `settlement-group`, `style-profile`, `modset-profile`, `form-registry`, `chinese-vernacular-roof-vocabulary`, `cultivation-form-vocabulary`.
- Region runtime: `docs/ai-kb/13_region_topology.md`, `region-profile`, `region-topology`, `region-runtime-binding`.
- Courtyard/mansion surfaces: `docs/ai-kb/16_path_surface_zoning.md`, `courtyard-ground-layer`, `courtyard-path-network`, `path-surface-zoning`, `courtyard-voxel-walkability`.
- Jiangnan mansion: `docs/ai-kb/10_civic_family.md`, `chinese-mansion-compound`, `compound-enclosure-planning`, `building-orientation-variants`.
- Hui reference slice: `docs/ai-kb/21_huipai_reference_slice.md`, `huipai-tianjing-mansion`, `form-registry`, `settlement-group`, `validation`.
- Ganlan reference slice: `docs/ai-kb/23_ganlan_reference_slice.md`, `add-ganlan-stilted-house`, `form-registry`, `settlement-group`, `resource-export`, `validation`.
- Pagoda landmark: `docs/ai-kb/24_pagoda_landmark_rebuild.md`, `rebuild-pagoda-landmark`, `vertical-landmark`, `cultivation-massing-grammar`, `validation`.
- `myvillage:` decor/rockery: `docs/ai-kb/15_rockery_form_diagnosis.md`, `mod-decor-block-family`, `garden-rockery`.
- Cultivation settlements: `sect-compound-layout`, `sect-compound-realization`, `sect-worldgen-structure`, `sect-mountain-derivation`, `town-plan`, `town-districts`, `town-realization`, `settlement-group`.
- Rendering/handoff: `docs/ai-kb/17_chunky_acceptance.md`, `docs/ai-kb/18_chunky_path_traced_render.md`, `chunky-acceptance-automation`, `interactive-preview`.
