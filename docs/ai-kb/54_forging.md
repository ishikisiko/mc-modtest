# 0.98.0 玄铁甲炼器

表现更新：十类工件从成品主体派生为实体模型；剑枪背包显示复用已有本体，四种工具采用实体铁头和木柄。护甲器坯使用原甲片，装配再加护肩／护臂、饰件和衬垫。`equipment/workshop/` 是 authored 输入，`tools/forging/models.py` 导出物品、台面和水槽共用来源的 OBJ/MTL；旧手绘器坯／工具图标由生成器退役。热处理改变材质，精磨恢复成品主体表面。数值、材料成本、服务器流程与协议 49 不变。

四件玄铁甲接入现有 `ForgingRules` / `WorkshopRules`。数据仍由 `data/myvillage/forging/{rules,workshop}.json` authored 配置持有；`gen_forging_assets.py` 生成器谱、工件图标和台面／水槽模型，不接管装备本体模型。新增四类器型与二十件中间工件；`WorkshopItems.registerArmorPatterns` 从护甲 craft 注册器谱，保留既有显式 registry ID。

工坊器谱每页六项，十个配方不再越界覆盖状态区；协议升至 49，服务端和客户端必须一致。配方最多二十项，与两个菜单各自 recipe/trait 按钮区间互不重叠。护甲由服务器限制为韧性热处理、坚韧炼灵和耐用锻炼；`TemperRecord.strike` 同样拒绝护甲的精工／导灵投入。装柄／精磨显示为铆合衬垫／修整甲片。

`WorkshopProgress.damaged` 只将有效敌对生物伤害产生的历练发给当前武器和匹配装备槽的护甲；沿用目标持久化的 `myvillage_workshop_paid_damage`，额度不超过目标最大生命。背包护甲、空挥、挨打、创造／旁观均不获益，治疗目标不重置额度。护甲不进入采掘提速。`WorkshopEquipment.refresh` 只刷新护甲最大耐久，保留原版防御属性；不新增伤害减免事件。

成品注灵继续共用 `EquipmentInfusion.prepare`，支持旧无 TEMPER 成品；品质、名字、损伤、附魔与历练保留，已有品质不重抽。品质外框和提示涵盖 `EquipmentContent` 独立注册的护甲。使用与材料见 [玄铁甲炼器](../features/xuantie-armor-forging.md)，局部采集入口 `tools/armor_forging_evidence.py`；[固定源功能证据与未接受外观](../testing/2026-10-27-armor-forging.md)。

# 0.83.0 玄枢多方块炼器鼎

新增独立 `forging.array` 控制器设备，接入原成品一次性炼灵。结构/蓝图与时间取 `forging/array.json`；模型/粗碰撞同源 `tools/forging_array/formed_model.py`，资源入口 `gen_forging_array_assets.py`。开炉将精确材料从操作者背包/袋转入暂存，保存原输入、冻结成品、品质、操作者与进度；缺格/未加载/堵输出暂停，完成消耗一次，撤炉或拆枢退还实际材料。旧鼎和锻台不改变共享规则。

共同逻辑 `EquipmentInfusion.prepare` 不新增器谱或 TEMPER 门槛，品质沿用资质上限；`ForgingCosts.reserve` 复用原分配器，额外返回实际消耗项用于暂存。中文菜单 `ForgingArrayMenu/Screen` 使用原版按钮包，renderer 只读同步状态展示真正器物。玩法边界和命令见 [多方块炼器鼎](../features/multiblock-forging.md)，[0.83.0 云端交付证据](../testing/2026-10-23-forging-array.md)。

# 0.70.2 淬火水槽

新增 `myvillage:quenching_trough`，以两铁锭、三木板、三圆石合成。放在锻器台或炼器鼎同一高度的四个正交邻格之一；水桶右键注入一桶水并返空桶，空桶右键可取回未使用的水。`filled` 方块状态保存水量；拆槽仅掉落空槽，不保存水或展示器坯。

锻台的“淬火”现在需要有水且空闲的相邻水槽。热成形坯仍由 `WorkshopMenu` 持有，`QuenchingTroughBlockEntity` 只短期占用和广播器型／开始时间。工序中禁止取水或另一菜单抢占；完成时服务器再次检查原槽及水量，耗水并推进器坯阶段。关闭、死亡、拆台或拆槽不会让展示物掉落另一份物品；中断保留尚未消耗的水。水槽可被相邻两座锻台轮流使用，但不能并行。

`tools/forging/trough.py` 生成模型、材质、合成、掉落；统一资源入口仍为 `gen_forging_assets.py`。槽内只读绘制复用六类器坯模型，砧面在淬火期间隐藏同一器坯。动画时间取 `workshop.json` 的 `operation_ticks.quench`；配置版本 2 移除旧背包水桶扣除与返桶字段，桶交换归实际注水交互。菜单增加相邻水槽可用状态，协议 38。

新增验证场景 `trough` 覆盖映射输入注水／取水、保存重启、缺槽／空槽拒绝、中断、拆槽和完成；`two_players` 录制旁观淬火。

交付记录：[淬火水槽证据](../testing/2026-10-20-quenching-trough.md)。

# 0.70.1 实体锻器台

`myvillage:forging_anvil` 保留原物品、配方与放置方式，新增方块实体负责短期工位占用和台面表现。实体模型与六类、六阶段器物模型由 `tools/forging/anvil.py` 生成，统一入口仍为 `tools/gen_forging_assets.py`。冷／热炉口按菜单实际热量更新。

`WorkshopMenu` 继续持有真实物品与扣料事务；`ForgingAnvilBlockEntity` 只广播器型、阶段、冷热和落锤开始时刻，不持有 ItemStack、不存储展示物。`ForgingAnvilRenderer` 只在客户端根据服务器时间绘制，接触帧取 `workshop.json` 的 `contact_tick`。关闭菜单后器物返还玩家，砧面清空；重启不恢复占用或热量。

实体锻器台一次供一人操作，第二人收到中文占用提示。关闭、死亡或拆台释放，另一个玩家可继续使用。炼器鼎保留原私有菜单规则。原先 0.70.0 的“同台并行私有工位”结论只适用于旧锻器台。

新增验收使用 `workshop-runtime --cases two_players,lifecycle,ui`：双客户端先拒绝抢占，再以真实按键锻造并从旁观客户端录下实体工序，随后验证工位转交；生命周期覆盖阶段物品、重启、死亡和拆台。新增文字与画面以中文为准。下面保留 0.70.0 的实现记录；其旧英文采集不代表后续要求。

本次交付记录：[实体锻器台证据](../testing/2026-10-20-physical-forging-workshop.md)。

# Forging workshop (0.70.0)

## Current player loop

The placed `forging_anvil` (锻器台) and existing `forging_table` open the native
`WorkshopMenu`. The ordinary station is craftable from iron ingots, logs and
cobblestone. Basic pickaxe/axe/shovel/hoe patterns are available without awakening;
Qingfeng/Lingxiao still require their learned pattern. Original weapon IDs,
combat generators, moves and older equipment remain compatible.

1. Prepare a billet using the existing recipe's non-spirit ingredients.
2. Heat with coal/charcoal, then strike. The saved piece changes from rough to
   shaped at the configured milestone. A completed forming pass is finite.
3. Quench with a water bucket (the empty bucket is returned), selecting balanced,
   edge/work or toughness treatment. The treatment is fixed on quenching.
4. Temper with fuel, grind with flint, and fit the handle. Each completed stage
   has its own registered item and generated silhouette. The resulting mundane
   implement is usable before awakening.
5. Carry the finished implement to the existing forging ding and infuse it once, paying the original recipe's spirit portion
   and selected affinity additive. The new workshop deterministically produces
   the initiating player's current aptitude ceiling; there is no per-hit score
   or final quality reroll. Existing `forge_mark` quality never changes in repair
   or tempering. The old direct-forging menu implementation is retained for
   registry compatibility but is no longer opened by either placed station or
   the copper-infusion return button.

`data/myvillage/forging/rules.json` owns recipe/affinity/repair costs and existing
quality effects. `forging/workshop.json` owns forming milestones, timing, heat,
operation costs, treatment effects, per-strike growth and progression bands.
Weapon material totals remain equal to the earlier base-plus-affinity cost;
the new loop partitions that payment between billet preparation and infusion.
Tools use iron-tier vanilla mechanics and retain that harvest tier as they grow.

## Workpieces and authority

- `Workpiece` / `myvillage:workpiece`: recipe, completed stage, forming blows and
  treatment. `WorkpieceItem` identity and component must agree before acceptance.
  All five intermediate stages can be removed, dropped, traded, saved and resumed.
- `TemperRecord` / `myvillage:temper`: bounded item experience plus separately
  accumulated edge, endurance and channeling strikes and the chosen treatment.
  The lifetime count is the sum of these investments; switching direction does
  not reset or reassign earlier work. Experience and growth follow the item.
- Each viewer has two private temporary slots. Closing, disconnecting, dying,
  walking away or removing the station uses ordinary container return/drop
  behavior; it leaves no shared invisible production inventory.
- Heat and unpaid animation belong to the active menu. Closing cancels an unpaid
  action and returns the piece cold, preserving completed work. There is no
  offline manufacture or offline gain.
- The server validates player/menu identity, life, spectator status, distance,
  station, input identity, stage, heat, experience, cap, output and full material
  transaction. Every strike has an authoritative contact tick and minimum cycle.
  Only contact commits the cost and benefit; client hold input submits bounded
  intents and cannot select a timestamp, cost, quality or resulting stack.
- A menu advances at most once per server tick even if inventory notifications
  or repeated button packets call `broadcastChanges` again. Work slot movement
  is locked during a pending operation. Work is revalidated at commitment.
- Protocol 37 includes the new menu and synchronized saved components. Old
  unmarked/marked equipment remains supported, without mass migration.

## Experience and benefits

`WorkshopProgress` awards experience on successful server tool callbacks, not on
mouse clicks or client animation. Pickaxes earn it from eligible ores, axes from
logs, shovels from shovel-mineable blocks, hoes from an actual new farmland
conversion. Mining tier and correct-tool conditions remain vanilla. Creative
and spectator work does not award experience.

`WorkshopHarvestLedger` saves claimed positions per dimension. Relevant player
or entity placement and piston movement mark positions ineligible; a completed
harvest/till claims a position once. There is no eviction or restart reset. This
conservative first version also denies repeat experience at regenerated trees
or replanted ground on the same position; forestry/farm renewal is not a claimed
implemented feature. Pre-install block provenance cannot be reconstructed.

Direct player attacks against hostile monsters award bounded effective-damage
experience to the held supported weapon. A target's persistent paid-damage
counter is limited by its maximum health, so healing that same target does not
mint unbounded experience. Air swings, friendly targets and indirect projectiles
do not award it in this version. Natural encounter pacing remains owner balance
work, not an automated acceptance claim.

Each growth strike consumes its current experience-band cost and the configured
material. Counts stop at the recipe's lifetime maximum. Weapon edge adds a unique
main-hand attack modifier; tool edge scales correct-tool breaking speed;
endurance derives max durability idempotently from the item base, treatment and
existing toughness affinity; channeling reduces eligible weapon Qi costs with a
positive-cost floor. Repair preserves components and only reduces damage.
Experience-only updates do not replay the equip animation.

## Presentation and resources

`tools/forging/workshop.py`, integrated through `gen_forging_assets.py`, owns the
station model, five-stage sprites, four tool icons, GUI sprites, recipes, bilingual
strings, fuel tag and its namespaced sound-event entries. Existing weapon models
remain under their dedicated generators. The UI reuses the bundled brush/book
fonts, with paper, ink, dark iron and restrained cinnabar rather than vanilla
stone buttons. It fits the normal minimum 320×240 GUI surface.

`WorkshopScreen` displays the actual workpiece model, hammer lift/contact/rebound,
heat, saved progress, per-item experience and remaining strikes. Hold the hammer
button for consecutive work; releasing stops future intents. Materials and
unavailable-action explanations are exposed as native hover hints. Sounds are
replaceable aliases to vanilla metal/forge, extinguishing, grinding and amethyst
events with distinct operation cues; no synthesized audio is represented as a
recorded smithy.

## Checks and evidence

Local selection: forging/workshop resource tests, affected Modal command planning,
`gen_forging_assets.py --check`, `validate_mod_items.py`. Cloud `workshop-build`
selects forging Java tests, weapon equip-animation and protocol checks plus a jar
with the required generated worldplan resources. `workshop-runtime` adds native
mapped interaction; `workshop-play --cases ...` supports focused reshoots.

Native scenarios: `stages,training,tools,combat,two_players,lifecycle,ui`.
`myvillage workshop inspect <player>` is operator-only readback; it cannot forge,
award experience or edit a stack. Administrator inventory/terrain/enemy fixtures
are labelled. The collector records real clicks/holds, screenshots, exact item
components, lifecycle readback and a native audio/video sample through an isolated
PulseAudio sink. Each run preserves its selected/completed/unrun cases and failure.

Delivery evidence: [0.70.0 workshop](../testing/2026-10-20-forging-workshop.md).
Owner appearance, tactile feel, sound preference, balance, FPS, discovery pacing
and broad multiplayer remain `not_verified` until observed separately.

---

# Historical prototype and earlier deliveries

The following sections describe their named releases. The staged workshop above
supersedes the immediate production entry and the earlier no-timed-work boundary.

## Forging prototype (0.57.2)

0.65.0 增加下界材料路线：铜锭标签＋烈焰粉＋下品灵石标签可合成一块既有赤铜锭，继续由 `gen_forging_assets.py` 输出；鼎内灌灵仍是替代来源。探索产出与向导提示见 [KB60](60_vanilla_exploration.md)。

0.64.3 起，炼制和维修 rules 的灵石成本使用 `#c:spirit_stones/low_grade`，复用既有完整分配事务；炼器鼎和器谱配方也由生成器输出该 tag 原料。 标签结构与兼容边界见 [资源合同](../contracts/assets.md#灵石公共标签)。


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
All ingot items share the flat-ingot silhouette in `tools/ingot_shape.py`; the bar/cake/block shape studies are not production shapes.

`forging/rules.json`. `xuantie_ingot` now displays as 玄铁锭 / Dark Iron Ingot;
the mining product is `raw_xuantie` (粗玄铁 / Raw Dark Iron). Smelting or blasting
raw dark iron produces the ingot. Existing stacks and old forged weapons need no
migration.
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
  pickaxe drops `raw_xuantie`; Silk Touch yields the block and Fortune applies.
  Smelt or blast raw dark iron into `xuantie_ingot`.
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
