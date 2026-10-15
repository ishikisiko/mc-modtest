# Alchemy Foundation (0.50.0)

The first alchemy slice contains thirteen distinct items: a modeled furnace,
six infused herbs, three reusable physical formula sheets and three pills.
Survival acquisition is vanilla-plant plus spirit-stone crafting, not herb
world generation. User controls, recipes, initial balance and commands are in
[README](../../README.md#alchemy-foundation-炼丹-0500).

## Sources of truth

- `data/myvillage/alchemy/rules.json`: recipes, costs, time, counts, use duration,
  shared interval and medicine strengths. `AlchemyRules` reads bundled resources,
  rejects malformed/duplicate/out-of-bounds data and loads during common setup.
  Rebuild/restart for changes; this is not datapack hot reload.
- `AlchemyFurnaceBlockEntity`: five saved slots (`Items`), `Progress`, `Recipe`.
  `BrewingProgress` is the pure progress machine. The formula is retained;
  inputs and fuel are deducted atomically only on completion. Output blockage
  pauses without spending. Ingredient order is interchangeable; input identity
  changes reset progress. Unloaded chunks pause; block removal drops and clears
  inventory once. No hopper capability is advertised.
- `AlchemyMenu` / `client/alchemy/AlchemyScreen`: vanilla container networking,
  bounded slot policies and server progress synchronization. No custom result
  or client-authoritative timer payload.
- `MedicineRuntime` / `MedicineState` / `MedicineEffects`: server-only consumption,
  eligibility and remaining online counters in `NeoForgeData.myvillage_medicine`
  (`cooldown_ticks`, `effect`, `effect_ticks`, `healing_elapsed`). Active benefits
  do not stack; death clears benefits but keeps interval; non-death clone and
  logout/restart retain them. Vanilla cooldown shading is presentation only.
- `CultivationService.recoverSpiritualPower`: clamped power recovery. Ningshen
  passes its multiplier into `BasicBreathingSettlement` only from actual normal/
  spirit meditation; progress, study, mastery, stage caps and advancement retain
  existing rules. The cultivation profile schema is unchanged.
- `tools/gen_alchemy_assets.py`: models, textures, bilingual alchemy keys,
  acquisition recipes, furnace loot and pickaxe tag. Never hand-edit these outputs.
  `--check` is deterministic; `--preview` renders the actual model JSON with the
  existing CPU rasterizer and writes `out/preview/alchemy/`.

## Gates and evidence

Run the generator check, `tools/validate_alchemy.py`, focused Python tests and
Java `alchemy.*` plus affected cultivation/item tests; full build/release includes
jar resource/class verification. New asset files must be packed into the jar.
`python3 tools/alchemy_evidence.py` follows clean stage-1 acceptance and owns its
capture sessions/shared heavy lock. Mapped input tests the menu and timed item
use; RCON is only fixture setup/readback. Save/restart is a second session reusing
that capture world. `--visual-only --language en_us --out out/preview/alchemy/visual-runtime`
starts a separate fresh session for the placed model and three menu sizes only.
Keep failures and phase timing in evidence JSON.

Offline art, developer captures and automated checks do not establish owner
appearance/feel/balance or multiplayer acceptance. Wild plants, farming, optional
herbs, formula learning, quality gambling and sect trading are not implemented.

See also: [alchemy-foundation](../../openspec/specs/alchemy-foundation/spec.md),
[resource-export](../../openspec/specs/resource-export/spec.md),
[validation](../../openspec/specs/validation/spec.md),
[mod item workflow](22_mod_item_creation.md),
[meditation](30_cultivation_playable_loop.md),
[spiritual power](45_spiritual_power_sword_qi.md).
