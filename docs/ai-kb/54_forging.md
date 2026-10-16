# Forging prototype (0.57.0)

The prototype closes learned patterns → material selection → one manually forged
weapon → actual equipment effects → repair. It uses the existing Qingfeng Sword
and Lingxiao Spear IDs, models, animations and combat registry. The table and
patterns intentionally reference vanilla smithing-table/book visuals.

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
  spectator status, block and distance. Both sides require protocol23.
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
