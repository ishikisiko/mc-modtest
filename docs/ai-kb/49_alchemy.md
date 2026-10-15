# Alchemy (0.52.0)

The thirteen existing items remain a modeled furnace, six infused herbs, three
reusable physical formulas and three pills. Survival acquisition uses vanilla
plants plus spirit stones. [README](../../README.md#alchemy-炼丹-0520) documents
controls, quality probabilities, effects and administrator fixtures.

## Sources of truth

- `data/myvillage/alchemy/rules.json` format2 owns recipes, costs, durations,
  output counts, shared interval, base effects, quality weights by ceiling and
  strength multipliers. `AlchemyRules` rejects malformed/out-of-bounds data;
  rebuild/restart for changes (not datapack hot reload).
- `AlchemyFurnaceBlockEntity` owns five saved `Items` slots, `Progress`, `Recipe`
  and an additive `Batch` compound. `AlchemyBatch` stores version, brewer UUID,
  recipe, aptitude score, ceiling and outcome. Vanilla menu button0 carries only
  start intent; the server checks the current nearby menu, living non-spectator
  actor, awakened initialized aptitude, recipe, fuel, idle state and empty output.
  Each start freezes these values and rolls once. The client receives progress,
  duration, status, frozen score/ceiling and active flag, never the hidden outcome.
- Active formula/herb/fuel slots are locked, including shift-click transfers.
  Another viewer cannot replace inputs or restart the batch. Formula is retained;
  herbs/fuel are deducted once, atomically with output. Completion requires a new
  start even if more ingredients remain. Full/incompatible output pauses without
  costs or reroll. Changed/invalid inputs defensively abandon the batch.
- Unload pauses; valid saved batches resume without offline work. Brewer death,
  logout and later aptitude changes do not alter the frozen batch. Breaking drops
  held inventory once and abandons unfinished output. Legacy progress without a
  complete valid batch resets to idle while preserving all items. No hopper
  capability is advertised.
- `PillQuality` uses bounded persistent/networked `myvillage:pill_quality`, integers
  1..4 (lower/middle/upper/perfect), on the three existing pill IDs. Missing is
  lower; ordinary new lower pills omit it and stack with legacy pills. Explicit
  component1 has baseline strength but vanilla component equality still controls
  stacking. Invalid data rejects or safely falls back to lower, never higher.
- `MedicineRuntime` / `MedicineState` / `MedicineEffects` own consumption,
  eligibility and online counters in `NeoForgeData.myvillage_medicine`, including
  sustained-effect `quality`. Recovery and each healing tick multiply base
  strength by quality. Ningshen scales only the bonus above1:
  `1 + (baseStabilityMultiplier - 1) * qualityMultiplier`. Duration, interval,
  eligibility and interruption rules remain; missing saved quality is lower.
  Death clears benefits but keeps interval; non-death clone/logout/restart retain
  remaining counters/quality. Vanilla cooldown shading is presentation only.
- Power replacement stays behind `CultivationService`. Ningshen affects only
  post-cap normal/spirit meditation stability, never progress, study, mastery or
  advancement. Profile remains v4. Client and server require protocol21.
- `tools/gen_alchemy_assets.py` owns models, textures, bilingual keys, recipes,
  loot and tags. Never hand-edit generated outputs. `--check` is deterministic;
  `--preview` writes actual-model previews to `out/preview/alchemy/`.

## Gates and evidence

Local focused checks: generator, `tools/validate_alchemy.py` and relevant Python
validators. Heavy full release/build/jar, clean stage1 and real-client/two-client
checks default to Modal; cloud failure does not automatically fall back locally.
Use fixed committed sources for `release` and `alchemy-quality` presets, with
explicit product/build equivalence for evidence-only follow-ups (see
[note46](46_modal_test_runner.md)). The latter runs clean stage1 then
`tools/alchemy_quality_evidence.py`, which owns capture sessions/shared heavy
lock, mapped start/input/use, bilingual layouts, restart and a second isolated
real client. RCON is fixture setup/readback only. Preserve failures and timings.
Legacy `alchemy_evidence.py` is historical foundation evidence; `--visual-only`
establishes model/menu presentation only, never current quality behavior.

The 0.50.0 foundation and 0.51.0 aptitude results are historical, not evidence
for 0.52.0. Current Modal evidence passes 62 release gates and 42 mapped
quality/effect/restart/two-client checks; 15 screenshots were inspected.
See [recorded runs and retries](../testing/2026-10-16-alchemy-quality.md). Owner appearance, feel,
balance and wider multiplayer remain `not_verified`. Natural acquisition,
cold legacy-save migration and other unobserved lifecycle surfaces remain
`not_verified`. Wild plants/farming, formula learning, skill XP, toxicity,
furnace failure and forging production remain outside this slice.

See also: [alchemy-foundation](../../openspec/specs/alchemy-foundation/spec.md),
[alchemy-quality](../../openspec/specs/alchemy-quality/spec.md),
[crafting-aptitudes](../../openspec/specs/crafting-aptitudes/spec.md),
[resource-export](../../openspec/specs/resource-export/spec.md),
[validation](../../openspec/specs/validation/spec.md),
[item workflow](22_mod_item_creation.md), [aptitudes](50_crafting_aptitudes.md),
[meditation](30_cultivation_playable_loop.md),
[spiritual power](45_spiritual_power_sword_qi.md).
