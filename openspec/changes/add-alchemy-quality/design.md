## Context
The approved next slice connects existing alchemy aptitude to three pill products. Heavy execution must be remote (Modal) because this host has a small RAM budget and prior unrelated global OOMs. Current capture and release code is reusable; changing where it runs must preserve actual input and artifact evidence.

## Goals / Non-Goals
Goals: manual one-batch starts, server-side brewer attribution, immutable batch quality across lifecycle, visible quality and measured medicine strength, cloud validation.
Non-goals: new models/textures, forging production, XP, toxicity, furnace failure, herb worldgen, secret player data uploads or local heavy-test fallback.

## Item Contract
- IDs/kind: existing `myvillage:alchemy_furnace` functional block item; existing `myvillage:pill_huiling`, `pill_yangyuan`, `pill_ningshen` functional items. The six herbs and three physical formulas retain IDs/behavior.
- Names: Bronze Alchemy Furnace/青铜丹炉; Spirit Recovery Pill/回灵丹, Vitality Pill/养元丹, Serenity Pill/凝神丹 (existing base translations are canonical); names prepend a localized quality label.
- Creative tab/registration: existing `myvillage:main`, `ModItems` and `ModBlocks`; no item multiplication by quality.
- Client assets: existing `assets/myvillage/models/item/<id>.json` and corresponding textures remain; no art regeneration beyond deterministic unchanged checks. Lang keys stay authored in `tools/gen_alchemy_assets.py`, generated into both language files. H Crafting page boundary text updated in owning language keys.
- Data: `data/myvillage/alchemy/rules.json` becomes format 2, with quality weights by cap and strictly increasing strength multipliers. Existing acquisition recipes, tags and loot stay.
- Behavior: vanilla menu button id0 carries start intent only. Server validates current menu/container, distance, alive/non-spectator, awakened initialized aptitude, valid ingredients/fuel, idle state and empty output. One click reserves one batch, not automatic chaining. Save owner UUID, score, quality cap and rolled quality; no client-provided score/outcome. Freeze inputs through menu policies until completion; other viewers cannot replace/restart the active batch. Death/logout of brewer do not alter the batch. Break drops existing inventory once without producing an unfinished result.
- Pill component: `myvillage:pill_quality`, integer1..4, persistent and network-synchronized; missing is lower quality. Newly made lower-quality pills omit the component to stack with legacy pills. Explicit component1 has the same effect, but normal vanilla component equality controls stacking. Invalid server/save values fail validation or fall back to lower, never grant high strength.
- Medicine: use a quality multiplier for recovery and per-tick healing; for stability scale only its bonus above1: `1+(base-1)*qualityMultiplier`. Duration/shared interval/eligibility/interruptions stay. Save sustained-effect quality and preserve through ticks, logout/restart and nondeath clone; old medication without quality reads as lower. Death/damage clear benefits as before.
- Acceptance: focused Python locally; full release/build/jar, clean stage1, one-client restart/layout/use and two-client start attribution checks on Modal. Inspect downloaded PNGs and serve preview. Owner feel/balance/visual remains pending.

## Decisions
- Require output empty at start. This avoids revealing the hidden rolled quality through compatibility with a pre-filled output stack. While active, blocked output pauses and resumes without reroll or cost.
- Resolve random quality once at server start and persist the outcome itself. Show only frozen aptitude/cap before completion; no preview/cancel loop reveals outcomes. A reload never calls the RNG for an active saved batch.
- Keep the component absent for lower quality, preserving legacy stack identity. All balance values are authored data. Weights by caps1..4 initially use `[100,0,0,0]`, `[70,30,0,0]`, `[55,30,15,0]`, `[45,30,20,5]`; multipliers1,1.25,1.5,2. Owner tuning remains pending.
- Shared runtime API: `AlchemyRules.qualityMultiplier(int)`, `qualityWeightTotal(int cap)`, `rollQuality(int cap,int draw)` (draw0..total-1); `PillQuality.get(ItemStack)`, `PillQuality.apply(ItemStack,int)`, `PillQuality.name(int)` returns localized component. Invalid int inputs reject rather than silently granting a stronger pill.
- Menu data extends to6 ints:0 progress,1 duration,2 status,3 frozen aptitude,4 frozen ceiling,5 active. Idle score/cap are0; no hidden outcome is sent. Status0..4 retain meanings,5 ready/start,6 unawakened rejection if needed. Player-specific rejection uses a localized message. UI button placement and bounded text must fit320x240GUI.
- Existing old in-progress furnace saves without a complete batch discard progress and wait for manual start; retain all items, so no material loss. Corrupt active batch data cannot resume or consume anything. New save data is additive. Network compatibility version is advanced because component registry/menu format changed.
- Remote presets must include full release and the exact alchemy-quality scripts, fixed commit provenance, bounded memory/lifetime, evidence collection and confirmed cleanup. Two-client tests need isolated client dir/username; preserve real mapped inputs for both actors.

## Risks / Trade-offs
- Active input slots cannot be rearranged: UI shows this and batch ends after one craft.
- Probability balance is provisional: expose data/README table and avoid claims of owner acceptance.
- Cloud failures may block delivery: retain reports, fix within scope and rerun remotely; never silently run heavy tasks on the local host.

## Migration Plan
Build0.52.0; update both client and server. Existing pills/medication keep baseline effects; old furnace input inventory retained. No automatic downgrade migration for new component-bearing items. Existing owner art remains unaccepted, new UI is separately reviewable.
