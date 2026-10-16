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


## Dedicated materials (0.57.3)

`tools/forging/materials.json` schema v2 owns the three materials' IDs, names,
traits and descriptions. `materials.py` owns their 32px pixel icons and item
models. It no longer accepts acquisition ingredients or produces recipes.
`gen_forging_assets.py` removes the six explicitly retired recipe/advancement
paths during generation; `--check` rejects lingering retired outputs.
`--materials-preview` renders the generated PNGs without rebuilding ding previews.

- `xuantie_ingot` / 玄铁锭: consumed by tough.
- `chitong_ingot` / 赤铜锭: consumed by channeling (sword only).
- `edge_tempering_sand` / 淬锋砂: consumed by sharp.

These remain ordinary stackable items with localized purpose tooltips
(`ForgingMaterialItem`). Existing stacks and the creative inventory remain usable.
**There is currently no survival acquisition route for these three materials.**
`forging/rules.json` remains the sole source for additive consumption. Base weapon
materials, quality weights, trait effects and repair remain unchanged. Old weapons
require no migration. No new acquisition gameplay is claimed by this removal.

The obsolete `forging-materials` preset and workbench collector were retired.
Use the resource tests and `--preset forging-build` for this change; do not reuse
0.57.2 workbench evidence as current acquisition proof. The historical report is
[2026-10-17 forging materials](../testing/2026-10-17-forging-materials.md).

### 获取方式方向（设计建议，未实现）

目标是让辅材对应不同的出行动机，找到后直接用于炼器，不再增加原矿、粉末、精炼锭等加工中间层。

- **玄铁：找矿。** 山体裂隙/洞穴可见少量玄铁矿脉，地表露头作为线索；每个矿点保底产出，深处矿点更多但伴随环境风险。自然产物宜显示为“玄铁”，沿用既有 registry ID，命名和外观待对应实现时确定。
- **赤铜：拆取。** 从遗迹中明确可见的废弃机关、炉芯拆得，地点本身交代用途；风险来自守卫或机关。普通宝箱只能作为补充，不能成为唯一来源，也不让妖兽掉金属锭。可选择绕开守卫取少量，或清理后取完整器件中的材料。
- **淬锋砂：择时采集。** 风蚀山口/峡谷的特殊砂层被风吹露后可采，玩家观察风势，在安全间隙进入。采集直接得到淬锋砂，不增加筛砂配方，也不采用原版砂砾超低概率掉落。

首批应保证线索可见、成功找到后有确定收益、小量即能试做一把兵器。
矿点消耗、砂层补充和多人共享规则需随实际采集玩法一并落实；不能依赖
有限的一次性遗迹宝箱供应长期消耗品。以上地点、交互和补充机制均未进入当前版本。
