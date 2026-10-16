# Alchemy (0.52.3)

The thirteen existing items remain a modeled furnace, six infused herbs, three
learnable physical formulas and three pills. Survival acquisition uses vanilla
plants plus spirit stones. [README](../../README.md#alchemy-炼丹-0520) documents
controls, quality probabilities, effects and administrator fixtures.

## Sources of truth

- `data/myvillage/alchemy/rules.json` format2 owns recipes, costs, durations,
  output counts, shared interval, base effects, quality weights by ceiling and
  strength multipliers. `AlchemyRules` rejects malformed/out-of-bounds data;
  rebuild/restart for changes (not datapack hot reload).
- `LearnedFormulas` is immutable, persisted with an additive `learned_formulas`
  attachment and `copyOnDeath`. `AlchemyKnowledge` alone writes it on the server.
  Using a FormulaItem learns its recipe; survival consumes one copy on first
  learning only. Knowledge does not depend on cultivation-profile reset/root state.
- The furnace menu privately synchronizes each viewer's knowledge with vanilla
  DataSlots; the learned list sends only a bounded menu button index. Selection
  and start recheck the current nearby menu, living non-spectator player, recipe
  knowledge and idle state. No client-authored inventory or learned IDs are accepted.
- `AlchemyAutofill` plans transfers on copies. Incompatible existing ingredients
  return to the actor's main inventory/hotbar; full inventory rejects the whole
  switch. It fills one batch's deficit, preserves stack components, and leaves
  missing ingredients empty. One server-thread commit updates both inventories.
- `AlchemyFurnaceBlockEntity` retains five saved Items indices for compatibility:
  index0 is return-only legacy formula storage; 1/2 herbs, 3 fuel, 4 output.
  `SelectedRecipe` is additive; old saves infer it from their legacy formula.
  Valid existing Batch decisions resume without relearning or rerolling; the
  next start requires the initiating player's knowledge.
- Batch still freezes brewer UUID, recipe, aptitude, cap and outcome. Active
  inputs/selection are locked. Ingredients/fuel commit once with output; output
  blockage pauses without cost or reroll. Completion requires another manual start.
  Unload pauses; breaking preserves held items but abandons unfinished output.
  No hopper capability is advertised. This alchemy wire layout was introduced in protocol22; current builds require protocol25.
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
  advancement. Profile remains v4. This alchemy wire layout was introduced in protocol22; current builds require protocol25.
- `tools/gen_alchemy_assets.py` owns models, textures, bilingual keys, recipes,
  loot and tags. Never hand-edit generated outputs. `--check` is deterministic;
  `--preview` writes actual-model previews to `out/preview/alchemy/`.

## Gates and evidence

Use generator `--check`, `tools/validate_alchemy.py`, related Python tests and
Modal `--preset alchemy-build` for the affected Java tests and practical jar.
This focused path has no game or full release suite. The old physical-formula
capture fixtures/coordinates in `alchemy_evidence.py` and
`alchemy_quality_evidence.py` need migration before running their complete
presets against 0.52.3; retain their historical evidence as historical.

The 0.50.0 foundation and 0.51.0 aptitude results are historical, not evidence
for 0.52.0. Current Modal evidence passes 62 release gates and 42 mapped
quality/effect/restart/two-client checks; 15 screenshots were inspected.
See [recorded runs and retries](../testing/2026-10-16-alchemy-quality.md). Owner appearance, feel,
balance and wider multiplayer remain `not_verified`. Natural acquisition,
cold legacy-save migration and other unobserved lifecycle surfaces remain
`not_verified`. Wild plant worldgen, skill XP, toxicity,
furnace failure remain outside this slice. Forging production is covered by [KB54](54_forging.md).

See also: [alchemy-foundation](../contracts/cultivation.md#alchemy-foundation),
[alchemy-quality](../contracts/cultivation.md#alchemy-quality),
[crafting-aptitudes](../contracts/cultivation.md#crafting-aptitudes),
[resource-export](../contracts/assets.md#resource-export),
[validation](../contracts/tooling.md#validation),
[item workflow](22_mod_item_creation.md), [aptitudes](50_crafting_aptitudes.md),
[meditation](30_cultivation_playable_loop.md),
[spiritual power](45_spiritual_power_sword_qi.md).

0.52.3 focused tests, build and owner-client layout: [verification record](../testing/2026-10-16-learned-formulas.md).

Six herb items are plantable on permanent Spirit Soil since 0.53.0; see [spirit farming](51_spirit_farming.md).

## Quality frames (0.58.3)

`item.QualityDisplay` owns the shared green/blue/purple/gold palette and delegates
pill classification to `PillQuality.get`; missing components remain Lower rather
than the weapon-only Mundane display. `PillQuality.name` and `AlchemyPillItem`
use that palette for ceiling/name text, including a quality line for renamed pills.
`client.item.QualityFrames` registers all three pills alongside basic combat weapons
for icon decoration and tooltip border coloring. Stack counts remain above the
1px frame. Rendering never applies/replaces a saved quality component.

Modal `--preset pill-quality-preview` runs `PillQualityTest`, builds the jar, then
captures mapped inventory/chest/hotbar views with all pill types and qualities,
legacy/explicit lower and renamed items, count glyph preservation, and adjacent
weapon frame checks. It does not repeat brewing or medicine-effect acceptance.
