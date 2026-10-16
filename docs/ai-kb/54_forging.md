# Forging prototype (0.57.2)

The prototype closes learned patterns → material selection → one manually forged
weapon → actual equipment effects → repair. It uses the existing Qingfeng Sword
and Lingxiao Spear IDs, models, animations and combat registry. The table now uses the approved iron/copper open ding model; pattern books still use vanilla book visuals.

## Sources and boundaries

- `forging/ForgingRules` validates bundled `data/myvillage/forging/rules.json`:
  recipes, combined material costs, supported traits, bounded quality weights,
  per-quality effects and repair. Rebuild/restart for rule changes. The prototype
  layout supports two recipes and four combined materials per selection.
- `ForgingKnowledge` owns the independent `learned_forge_patterns` attachment,
  using the same immutable bounded recipe-set codec as alchemy in a separate
  attachment. `copyOnDeath` owns death copying; learning does not change aptitudes.
- `ForgingTableBlock` opens a private `ForgingMenu` for each player, without a
  BlockEntity. Vanilla close cleanup returns/drops both temporary slots, including
  at disconnect/death. No automation/hopper inventory exists.
- Vanilla menu DataSlots carry per-viewer knowledge and quality cap; buttons carry
  bounded indices only. Every request checks the owning active menu, life,
  spectator status, block and distance. Both sides require protocol25.
- `ForgingCosts.plan` works on copies of the main inventory/hotbar. Only after all
  preconditions and result construction succeed are costs and output committed
  together on the server. An occupied output blocks further crafting/repair.
  RNG is consumed only for a successful immediate forge, with the saved aptitude
  cap. There is no persisted half-finished job or background production.
- `ForgeMark` persists/networks stable trait IDs and quality1..4 on each original
  weapon. Old weapons stay unchanged. `ForgedEquipment` adds a unique main-hand
  attack modifier for sharp, changes vanilla max durability for tough, and
  discounts the existing server-owned Sword Qi cost for channeling. The latter
  rounds down with a minimum positive cost of1 and grants no technique. Spears
  currently disallow channeling because there is no matching active skill.
- Repair copies the existing stack and only reduces damage, preserving all
  metadata. Both forged and old unmarked configured weapons are repairable.
  Tough improves both lifespan and the amount restored per repair purchase.
- `tools/gen_forging_assets.py` owns block/item models, loot, acquisition recipes,
  bilingual strings and its tag entry. Existing combat generators remain owners
  of weapon art; the alchemy generator still owns the H-page foundation sentence.

## Focused checks

Local: `python3 -m unittest tools.tests.test_forging
 tools.tests.test_validate_crafting_aptitudes tools.tests.test_modal_runner`,
`python3 tools/gen_forging_assets.py --check`, the affected alchemy generator check
and item/aptitude validators. Commands above are one-line invocations.

Modal `--preset forging-build` builds committed resources (`-x generateAllStructures`)
and runs forging metadata/cost/effect tests plus learned-knowledge, combat item,
protocol and sword-qi regression tests. `--preset forging-runtime` adds
`tools/forging_evidence.py`: real mapped learning, crafting, repair, cost checks,
two private player sessions and restart/death persistence. RCON is only fixture
setup and readback. `--preset forging-lifecycle` is a narrower diagnostic that
builds the jar and runs learning/forging/restart/death, explicitly waiting for
positive server health after respawn before reading knowledge. It omits combat
and multiplayer checks. Per-run results belong in the delivery evidence record.

Unobserved appearance, sound, feel, balance, owner FPS, broad multiplayer and
natural survival acquisition stay `not_verified`. No timed crafting, minigame,
reforging/ascension, profession XP, new ore generation or special boss materials
are included. These are follow-up design scope, not hidden completed features.

Current delivery evidence: [2026-10-16 forging prototype](../testing/2026-10-16-forging-prototype.md).


## Ding presentation (0.57.1)

The registry and recipe IDs remain `forging_table`; the displayed name is 炼器鼎 /
Forging Ding. `tools/forging/ding.py` owns the approved wide iron belly, three low
feet, two upright ears and open hearth. `tools/forging/ding.json` owns the palette
and bounded feedback settings. `gen_forging_assets.py` exports both cold/lit models,
the atlas, item transforms, eight facing/lit variants and `forging/ding.json`.
Collision is derived from the same authored geometry; minor straps/rivets do not
become separate collision pieces. Use `.venv-preview/bin/python
 tools/gen_forging_assets.py --preview` for actual-model offline views.

`ForgingDingVisuals` validates/loads the generated shape and feedback data and
rotates collision to each horizontal direction. Propertyless old block states
fall back to north/unlit. `ForgingTableBlock.completed` runs only after a successful
server inventory transaction, synchronizing a bounded LIT pulse, light, sparks
and sound to nearby clients. A vanilla scheduled block tick ends it; repeated
successes during an existing pulse emit feedback without extending it indefinitely.
Scheduled ticks survive ordinary world saving. The pulse owns no jobs, costs or
items. Cold blocks produce no ambient particles. No BlockEntity was added.

`--preset forging-ding` performs the generated-resource check, focused block /
forging / protocol Java tests and jar, then mapped placement/forging/repair visual
capture plus an actual saved lit pulse and restart. This is a presentation-focused
run, not a repeat of all forging/multiplayer acceptance. Owner appearance, sound,
feel and FPS remain `not_verified` until separately observed.

Ding delivery evidence: [2026-10-16 forging ding](../testing/2026-10-16-forging-ding.md).


## Material acquisition (0.58.1)

Three ordinary additive items retain their registry IDs and consumers in
`forging/rules.json`. `xuantie_ingot` now displays as 玄铁 / Dark Iron, with a raw
mineral silhouette. Existing stacks and old forged weapons need no migration.
The removed 0.57.2 workbench recipes/advancements remain explicit retired outputs;
`gen_forging_assets.py --check` rejects their reappearance.

### Copper infusion

The existing ding menu opens `CopperInfusionMenu` through a bounded button intent.
It retains the same placed-block validity, living owner, current-menu and spectator
checks. A batch accepts `c:ingots/copper` members, an input slot and a take-only
output slot. Start/resume requires awakening; each server pulse plans a complete
batch charge before calling `CultivationService.spendSpiritualPower`. There is no
client-side cost or item mutation. A successful pulse updates the ingots or converts
the whole batch; insufficient power pauses without a partial charge.

`myvillage:copper_infusion` is bounded, persistent and network-synchronized **paid qi
per ingot**. Progress moves with the copper, so splitting cannot multiply investment,
and stacks with different paid progress do not merge. The rules plan only the
remaining cost, including a final shorter pulse. Closing, walking away, logout or
block removal returns both temporary slots through vanilla `clearContainer`; a full
inventory drops the items by vanilla rules. Normal death-drop/keepInventory behavior
applies. The paused progress survives item saves/restart; there is no offline job.
Using unfinished copper in another vanilla recipe can consume it normally and does
not refund its invested qi.

Each viewer owns their temporary slots. Another player using the same ding has a
separate batch. The ding's short shared light/particle pulse is cosmetic only. The
screen displays the input/output, remaining batch cost, available power and gradual
red runes. Any item with paid infusion metadata has a localized progress tooltip, even if a reload removes its copper tag membership. Compatibility
protocol 27 covers the new menu, component and block registries.

### Natural extraction

- `xuantie_ore` is a vanilla ore configured feature in Overworld stone/deepslate,
  restricted to the authored deep height range. Air exposure is retained. Iron-tier
  pickaxe drops raw `xuantie_ingot`; Silk Touch yields the block and Fortune applies.
- `edge_tempering_sand_deposit` is a stable compact sediment block, shovel-mineable.
  `EdgeSandFeature` examines real topography and replacement tags; it places bounded
  patches only beside water or in the authored shallow depth. Biome placement is
  limited by `has_edge_tempering_sand`, evaluated after surface-height placement. It never replaces deep floors or arbitrary
  inland ground. Silk Touch yields the block; ordinary extraction gives usable sand.

Both sources generate in new Overworld chunks only, are shared finite world blocks,
and have no local refill timer. Finding and clearing one deposit can supply several
weapons. Generation/harvest tuning is an initial balance candidate, not owner approval.

### Sources and focused checks

- `src/main/resources/data/myvillage/forging/acquisition.json`: sole authored infusion,
  ore and sand tuning. `InfusionRules` consumes the infusion section at runtime.
- `tools/forging/materials.json` schema v4: IDs, material tags, names, descriptions and acquisition
  hints. `materials.py`: item sprites and models.
- `tools/forging/acquisition.py`: tuning validation, deposit textures/models/loot/tags,
  configured/placed features, biome modifiers and infusion screen sprites/strings.
- `gen_forging_assets.py`: integrates these outputs; `--materials-preview` renders
  actual item PNGs. Never hand-edit the generated assets.
- `CopperInfusionTest`: progress codec/wire bounds, costs, partial resumption, splitting,
  batch limits and insufficient-power refusal. Python tests check generator drift,
  retired outputs, worldgen/drop/tool resources and the chunk-palette evidence parser.
- Modal `--preset forging-acquisition`: focused build plus real natural-world deposit
  readback, mapped mining/tool gates, infusion/refusal, interrupted restart, two-client
  batches and forging with the acquired materials. Administrative teleports, tools,
  copper/power and safe access tunnels are fixtures; they do not prove discovery time,
  owner balance, appearance or feel. Those remain `not_verified`.

Historical workbench evidence applies only to 0.57.2:
[2026-10-17 forging materials](../testing/2026-10-17-forging-materials.md).

Current acquisition evidence: [2026-10-17 forging acquisition](../testing/2026-10-17-forging-acquisition.md).


## Tag compatibility and allocation

Default trait costs use `#myvillage:forging_materials/{xuantie,chitong,edge_tempering_sand}`.
`materials.json` owns each tag ID and member identity; the generator emits item tags
and bilingual tag labels. `acquisition.py` additionally emits block/item
`c:ores/xuantie` -> `c:ores` and the independent sand-deposit classification.
Special materials are not members of ordinary copper/iron/sand tags.

`ForgingIngredients` resolves live item-tag membership. The cost map still accepts
concrete item IDs, including version-1 rules; current rules use schema version 2.
The client counts all matching stacks, displays an available member (or a defined
member when absent), and uses a localized tag name. Empty tags display a barrier
placeholder and cannot silently fall back to the original material.

`ForgingCosts` performs an integral flow allocation between inventory stacks and
requirements, then applies the result only to copies. Residual paths allow a broad
tag to release an item needed by a narrower requirement. A shared stack never pays
two costs with the same units; no feasible assignment means no consumption. Tests
compare 6,912 small overlap graphs against exhaustive assignments, in addition to
concrete-ID, empty-tag and mixed-stack cases.

Infusion slot insertion, shift-click and per-pulse eligibility all use the common
copper tag. Paid progress remains on the original input item, including external
copper ingots. Removal from the tag pauses eligibility without erasing the component;
restoring membership allows continuation. One batch is still one stack, so differing
item IDs or progress values are not merged. Tag recognition does not imply compatibility
with arbitrary custom behavior implemented by every third-party item.

Modal `--preset forging-tags` builds the jar and runs the focused tag collector.
It uses a disposable-world datapack with vanilla stand-ins to prove the actual
membership/reload path: common and special tags, alternative copper and restart,
alternative additives, overlap reallocation/refusal, and empty-tag refusal. It does
not repeat natural worldgen or claim a tested external modpack. The Java tests use
`--preset forging-build` separately.

Tag integration evidence: [2026-10-17 forging tags](../testing/2026-10-17-forging-tags.md).

## Quality presentation (0.58.2)

`item.QualityDisplay` owns the shared palette; `ForgingQualityDisplay` provides localized weapon quality labels:
unmarked weapons show white 凡品 / Mundane; qualities 1–4 show green, blue,
purple and gold. The display-only zero is never written into `ForgeMark`, whose
codec remains 1–4. Existing stacks need no migration and enchantment does not
promote forging quality.

`CombatWeaponItem` colors the name and quality tooltip, including ordinary anvil
names without mutating their saved components. `ForgingScreen` colors the ceiling
label from the same palette. Client-only `client.item.QualityFrames` registers an
`IItemDecorator` for registered `CombatWeaponItem`s and, since 0.58.3, `AlchemyPillItem`s,
plus a `RenderTooltipEvent.Color` listener.
This also keeps the non-forgeable basic gauntlet consistently white/Mundane,
without adding a gauntlet forging recipe.
The 1px icon frame lies outside the 16px icon, below count text, and follows the
normal item-decoration path in inventories, containers and the hotbar. Tooltip
borders use the same color; the background and layout remain vanilla.

Modal `--preset forging-quality-preview` checks generated resources, builds the
jar and runs presentation-only fixture captures. It selects all five qualities,
both weapons, hotbar/inventory/chest, renamed/enchanted/damaged items and saved
component non-mutation. This is not a crafting or broad compatibility rerun.

Delivery evidence: [2026-10-17 weapon quality colors and frames](../testing/2026-10-17-forging-quality.md).

## Quality picker (0.58.4)

`QualityPickerItem` is creative-tab/command acquisition only, with no recipe or
loot. `client.item.QualityPickerScreen` opens from mapped use input while the item
is held, including spectator mode; no game-mode or operator requirement is added.
The client searches registered `myvillage` items locally and submits only item ID,
quality, trait index and one/full-stack intent through `QualityPickerPayload`.
Protocol 28 covers the added item/intent. The server requires a living holder,
reconstructs the result using `PillQuality.apply` or `ForgedEquipment.apply`, and
plans inventory insertion on copies. Unsupported quality/trait combinations and
full inventories produce no item. Ordinary items are available only unchanged.

`tools/gen_quality_picker_assets.py` owns the knowledge-book model alias and
bilingual picker strings. Modal `--preset quality-picker-build` only compiles and
packages committed resources. The user explicitly requested no tests or runtime
validation for this delivery; gameplay/UI/multiplayer acceptance is `not_verified`.
