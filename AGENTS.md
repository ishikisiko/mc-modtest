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
- Before manual visual review, run the acceptance checklist, build the artifact when practical, update command docs, generate the visual acceptance report, inspect representative PNGs, and serve `out/preview/` with `python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview` until review ends. While this host keeps its current public IP, report the aggregate route as `http://43.156.135.198:8765/index.html`; for a focused preview, report its direct path below that same public base URL rather than a localhost or tunnel URL.
- Chunky automation is a staged handoff, not visual acceptance. Headless Chunky renderer PNGs cannot prove custom `myvillage:` block appearance until a dedicated compatibility path exists.
- When commands or acceptance prep steps change, update `README.md`, this `AGENTS.md`, and relevant `openspec/specs/` documents together.
- Flying-sword changes must keep the custom payload input-only and run `tools/validate_rideable_flying_sword.py`, tests, build, and the acceptance server; riding and appearance still require in-game review.
- Qingfeng sword-combat changes must use the exact untracked root `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar`, keep both combat C2S payloads empty and all timing/hit/damage/step authority on the server, keep PAL imports client-only, and run `tools/validate_sword_combat_foundation.py`, its tests, mod-item and existing gameplay validators, Gradle tests/build, jar inspection, the acceptance server, and a final physical-client smoke. Use `-Pcombat_smoke_server`, `-Pcombat_smoke_game_dir`, and `-Pcombat_smoke_username` for isolated final/two-client runs; `/myvillage_pal_smoke move <1-5>` is only a client visual-pose probe and never substitutes for mapped-click or server evidence. Gate A/B/C and every real-client/two-player item stay separate; never infer the owner's texture, animation, or gameplay verdict.
- Cultivation-core/initiation changes must keep immutable profile replacement behind `CultivationService`, use Attachment `copyOnDeath` without a cultivation `PlayerEvent.Clone` copy, keep snapshots clientbound-only, keep every English `/myvillage cultivation` command structurally equivalent to its documented pinyin alias under both `cultivation` and `xiulian`, and run both cultivation validators, tests, build, and a bounded acceptance-server smoke. Initiation review must exercise the testing and inheritance steles as separate steps; every unobserved lifecycle, interaction, command, and H-screen item remains `not_verified`.
- Cultivation playable-loop changes must keep key and H-button client input bounded to action intent and profile replacement behind `CultivationService`; progress-only gain before cap, affinity-paced stability only after cap, stage-derived stability limits, and advancement halving remain server-owned. The aggregate acceptance gate is the strict baseline, including the capability specs synchronized from the five archived foundation changes and affinity/UI revision, plus `validate_cultivation_core.py`, `validate_cultivation_initiation.py`, `validate_spirit_stone_resources.py`, `validate_cultivation_lifespan.py`, `validate_cultivation_meditation.py`, `validate_cultivation_gain.py`, `validate_cultivation_advancement.py`, validator tests, Gradle tests/build, and bounded stage-1 acceptance-server smoke, followed by the README real-client ledger with every unobserved item left `not_verified`.
- GuideME cultivation-guide changes must keep root `guidebook/` as the only authored Markdown tree, resolve the hard dependency from Maven rather than the root jar, avoid the 21.1.17 `custom_colors` path, leave GuideME's default `G` item-index hotkey unreserved by default (`X` is MyVillage's default stop-meditation key), and add no GuideME-specific key interception or automatic binding migration. Run the focused validator/tests, Gradle tests/build, bounded `runGuide`, and acceptance-server smoke. Startup is not rendering or interaction evidence; every unobserved language, navigation, search, item-index, component/model, reload, key-remap, handbook, and regression surface remains `not_verified`.
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
- Qingfeng sword combat/PAL: `docs/ai-kb/32_pal_combat_integration.md`, `add-sword-combat-foundation`, `player-animation-integration`, `sword-combat-foundation`, `tools/validate_sword_combat_foundation.py`, `mod-item-pipeline`, `resource-export`, `validation`.
- Cultivation core/initiation: `docs/ai-kb/28_cultivation_core.md`, `docs/ai-kb/29_cultivation_initiation_ritual.md`, `cultivation-player-profile`, `cultivation-definition-registries`, `cultivation-persistence-lifecycle`, `cultivation-state-synchronization`, `cultivation-debug-commands`, `cultivation-core-validation`, `cultivation-initiation-ritual`, `tools/validate_cultivation_core.py`, `tools/validate_cultivation_initiation.py`, `python3 tools/run_chunky_acceptance.py --stage 1`, two-stele manual acceptance, `validation`.
- Cultivation playable loop: `docs/ai-kb/30_cultivation_playable_loop.md`, `add-spirit-stone-resources`, `add-cultivation-lifespan-calendar`, `add-cultivation-meditation`, `add-basic-breathing-cultivation-gain`, `add-qi-refining-advancement`, `revise-cultivation-affinity-meditation-ui`, the seven focused validators, and the README pass/fail/`not_verified` ledger.
- GuideME cultivation guide: `docs/ai-kb/31_guideme_cultivation_guide.md`, `add-guideme-cultivation-guide-slice`, `tools/validate_guideme_cultivation_guide.py`, `runGuide`, required `BOTH` dependency, root `guidebook/`, and the README GuideME `not_verified` ledger.
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
