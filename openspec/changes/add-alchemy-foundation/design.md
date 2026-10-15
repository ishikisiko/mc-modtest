## Context

The current mod has spirit-stone mining, spiritual power, deterministic cultivation and three usable weapon styles. It has no alchemy inventory or plant cultivation loop. The approved scope is a foundation that can be tuned later; the owner explicitly authorized choosing initial logic and model details.

## Goals / Non-Goals

Goals: one tangible furnace and thirteen distinct obtainable items, deterministic production, useful bounded effects, saved state, comprehensible bilingual UI, generated art and review evidence.
Non-goals: planting/worldgen, learned-formula attachment, random failure/quality, optional ingredients, hopper automation, NPC trade, sect integration, permanent stats, breakthrough changes and owner acceptance inferred from tests.

## Decisions

### Shared item contract
All items belong to `myvillage:main`, are registered by `ModItems` (the furnace block by `ModBlocks`), and have English/Chinese names and `assets/myvillage/models/item/<id>.json`. Plain herbs and formulas stack to vanilla default; pills stack to 16. Furnace is a functional block item, herbs plain items, formula sheets physical recipe selectors with ingredient tooltips, pills functional items with server-owned effects. No new entity mob is needed: the modeled placed block has a block entity for saved inventory.

IDs and Chinese display names:
- alchemy_furnace / 青铜丹炉
- spirit_leaf / 聚灵叶
- dew_blossom / 凝露花
- sunroot / 赤阳根
- blood_berry / 血朱果
- cloud_fungus / 云芝
- calm_lotus / 清心莲
- formula_huiling / 回灵丹方, formula_yangyuan / 养元丹方, formula_ningshen / 凝神丹方
- pill_huiling / 回灵丹, pill_yangyuan / 养元丹, pill_ningshen / 凝神丹

Survival acquisition: spirit-stone infusion shapeless crafting turns renewable vanilla plant materials (fern, blue orchid, carrot, sweet berries, brown mushroom, lily pad respectively) into herbs. Craft formula sheets from paper, the corresponding two herbs and a spirit stone; formula sheets are not consumed by brewing. Furnace is crafted from copper, iron and a vanilla furnace. This is explicitly the initial acquisition route, not claimed wild-herb generation.

### Furnace transaction
Use vanilla menu/slot synchronization, a server block-entity ticker and no client-authoritative result payload. Five slots: formula (0), herb A (1), herb B (2), spirit stone fuel (3), output (4). Recipes accept the two herbs in either order, one each, and consume one spirit stone per batch. A ready matching batch advances automatically while the chunk ticks; the formula remains. Inputs remain until atomic completion; any mismatch/change resets progress so saved progress cannot be transferred to a different recipe. Full/incompatible output pauses without cost. Save inventory, recipe identity and progress. Breaking the furnace drops inventory once and the block once; progress does not survive breaking. Unloaded chunks pause without catch-up. No exposed hopper capability in this slice.

### Bundled data and initial balance
`data/myvillage/alchemy/rules.json` is the authored source, read by `AlchemyRules` using the existing bundled-resource style; no hot-reload promise. All balance/effect numbers go there. Schema: `version:1`, `use_ticks:32`, `shared_cooldown_ticks:600`, `recipes` array; each entry has `id`, `formula`, `ingredients` (two item-id strings), `fuel_count`, `duration_ticks`, `output`, `output_count`; `effects` object keyed `huiling`, `yangyuan`, `ningshen`.
Initial recipes: huiling = spirit_leaf + dew_blossom; yangyuan = sunroot + blood_berry; ningshen = cloud_fungus + calm_lotus. Each takes 400 ticks and yields two pills. Fuel is low_grade_spirit_stone.
Effects: huiling `{power_fraction:0.25}` instant bounded spiritual recovery; yangyuan `{duration_ticks:200, interval_ticks:20, heal_fraction:0.02}` gradual maximum-health-relative recovery canceled by received damage; ningshen `{duration_ticks:1200, stability_multiplier:1.5}` affects only actual post-cap stability meditation. No effect stacks. Shared remaining medication ticks and active effects persist through logout/restart; no offline recovery. Death clears active benefits and preserves remaining cooldown. Rejected/full/ineligible use consumes nothing. Cultivation profile changes remain behind CultivationService; ningshen never bypasses stage caps or grants progress/study points. Presentation and descriptive tooltips do not decide effects.

### Appearance and evidence
Small three-legged bronze furnace with separate body, rim, lid knob, handles and fire vent; avoid a textured full cube. Distinct herb silhouettes and colored formula seals/pills. Deterministic Python asset generator owns model/textures and offline contact sheet/3D preview. Block collision uses a conservative matching shape. Lit property provides a burning variant and light. Inspect generated preview before handoff; owner visual/gameplay verdict remains pending. The user authorized initial visual direction through foundation-first instruction; no additional design checkpoint required.

## Risks / Trade-offs

- Basic infusion recipes are accessible but not exploration specialization: clearly document this initial route and defer herb worldgen.
- Initial medicine strength may be too high/low: central data, no permanent buffs, shared saved spacing.
- New container lifecycle can duplicate items if completion/removal are careless: atomic completion and focused save/menu tests plus bounded server evidence.
- Offline art does not prove in-game appearance: keep physical-client owner acceptance not_verified.

## Migration Plan

Additive block/items and saved block entities; no changes to existing cultivation profile schema or progression. Bump feature version per openspec/config.yaml rules.tasks and package every generated resource. Removing this feature from a world loses its new items/blocks, so rollback requires a save backup.
