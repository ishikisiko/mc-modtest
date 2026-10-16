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


## Dedicated materials (0.57.2)

`tools/forging/materials.json` owns three materials' names/descriptions and
acquisition ingredient counts; `materials.py` owns their 32px pixel icons and
vanilla crafting/recipe-book advancement outputs. The existing
`gen_forging_assets.py` includes these outputs. `--materials-preview` renders the
actual generated PNGs without rebuilding the ding previews.

- `xuantie_ingot` / 玄铁锭: reinforced iron, consumed by tough.
- `chitong_ingot` / 赤铜锭: infused copper, consumed by channeling (sword only).
- `edge_tempering_sand` / 淬锋砂: quartz/flint abrasive, consumed by sharp.

These are ordinary stackable items with localized tooltips (`ForgingMaterialItem`),
not new ores or a separate production machine. Workbench acquisition uses existing
vanilla materials plus low-grade spirit stones. Finding the primary raw ingredient
unlocks the corresponding recipe. `forging/rules.json` remains the sole source for
which additive and count a trait consumes. Base weapon materials, quality weights,
trait effects and repair remain unchanged. Old weapons require no migration.

`--preset forging-materials` checks generator outputs, forging/protocol Java tests
and jar packaging, then runs mapped vanilla crafting of all three materials and
uses those actual outputs to forge three trait-bearing swords. A raw-quartz
rejection case checks that the old additive cannot bypass processing. Raw input
items are administrator fixtures; natural mining, owner balance/appearance and
new broad multiplayer/lifecycle checks are not claimed by this bounded run.
