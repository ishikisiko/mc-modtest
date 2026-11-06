# Realm Statistics and Wilderness

当前修炼属性已改为[逐阶段加值与肉身防御](../features/additive-cultivation-stats.md)。
`data/myvillage/stats/progression.json` 是凡人至金丹的生命、攻击、肉身防御、灵力和品质奖励的唯一数表。
玩家保存筑基来源与金丹品质，profile v5 / protocol59；装备贡献不会再被境界整体放大。

## 成长和权威

`CultivationService` 仍是玩家档案的唯一替换入口。数值根据阶段、筑基来源、金丹品质重新计算，使用稳定 ADD_VALUE 修饰替换；
刷新、补基、重登不重复奖励，扩容不自动补满生命或灵力。copyOnDeath 不额外叠加 Clone 复制。
已设计境界中的物理 NPC、妖兽和原版怪物同样叠加默认下品的境界贡献，保留各自原生属性、出生记录与地区身份。
未设计的 NPC 元婴数据仍沿既有配置；当前玩家链止于金丹后期。离线命簿斗法不在本轮范围内。

## 伤害

独立 `body_defense` 和 `defense_penetration` 在同一伤害容器里只结算一次。低防线性、高防平滑趋近保底，参数来自同一数表。
自定义伤害保存攻击和招式收据，原版近战读取攻击属性，将攻防对抗置于招式/暴击倍率之前；投射物使用自身伤害基准。
以已减免主击为基数的火灵衍生伤害不递归扣防。环境伤害与管理性绕过伤害保持各自边界。

原版护甲、韧性、附魔、药水与吸收仍执行一次。`CombatRulesMixin` 保持只访问第一次 clamp 的参数零，
不再以防守方境界系数降低护甲压力；既有物理穿甲仍在自己的护甲步骤生效。
六种分类抗性及其独立抗性穿透在原版减免后、吸收前结算。
`stats info` 展示基础数表、有效属性、两项品质及修饰来源；`stats trace` 区分攻防、护甲、抗性和实际失血。

## 验证边界

H → 内视已接入天道筑基和三档金丹正式雷劫；承受指定次数有效命中并存活后结算。详细条件和命令见[突破雷劫](../features/tribulation.md)。
原生 fixture 不构成玩家映射输入、手感、多人或 FPS 验收；正式入口的映射输入和实际结果需单独记录。

## Regions and wildlife

### 普通怪物的生成权重与境界下限（0.112.2）

`VanillaMonsterSpawnModifier` 在 NeoForge 的 `MODIFY` 阶段修改群系敌对生成表。
`data/myvillage/neoforge/biome_modifier/scale_vanilla_monsters.json` 中的 `multiplier`
是原版怪物的权重系数，只作用于 `minecraft` 命名空间的 `MONSTER` 条目。
整数权重四舍五入，正值至少保留 1，原有 0 继续为 0。新增怪物、动物、群体数量、
生成成本和敌对生物容量不跟随改变；刷怪笼、袭击等独立生成入口不走群系权重。
该系数改变刷怪池中的相对概率，不承诺实测数量或占比同比下降。

`wilderness/vanilla_monsters.json` 的 `minimum_realm` 单独控制原版普通怪物的下限。
`ExplorationRules.vanillaEncounters` 先合并地区和结构、维度的原有范围，再使用该下限，
保留合并后的上限。目前允许无境界，仍按最终离散范围均匀抽取；建议境界、奖励和
特殊首领配置分别沿用原规则。没有适用范围时仍不虚构境界。
新增妖兽、六种野怪和地域亡灵继续使用 `WildernessRules` 的地区 `beast` 范围，
最低为炼气一层或当地更高下限。已保存的出生境界不重抽、不随移动改变。

`data/myvillage/wilderness/rules.json` maps region IDs to inclusive beast and
rogue realm-stage ranges. Region IDs, generated region tiers and realm indices
are separate concepts. The existing region runtime is read-only; wilderness
consumes its position queries. Outside its supported area or outside the
overworld, no region is invented.

Natural wolves have biome spawn resources and a registered placement rule.
Their realm is sampled once at birth and saved. Moving across a region boundary
or reloading never rerolls it. Legacy command/egg wolves retain their baseline;
diagnostic assigned wolves are explicitly distinguished from natural spawns.
The existing empty loot table and combat presentation remain unchanged.

### Ordinary vanilla monsters (0.63.1)

**0.65.0:** [KB60](60_vanilla_exploration.md) extends new births with structure/scenario floors and explicit Nether ranges. The paragraphs below describe the original regional integration. Spawn eligibility, saved-birth compatibility and the shared damage path remain in force; the older other-dimension baseline statement no longer describes configured Nether births. End mobs and the four excluded special bosses remain unchanged in V1.

`VanillaMonsterRealms` consumes the same region `beast` ranges. Its separate
`wilderness/vanilla_monsters.json` declares eligible spawn reasons, excluded
special monsters and non-attribute damage types. Only `minecraft` entities in
the `MONSTER` category are eligible. Natural/chunk births, spawners (including
trial spawners), structures, patrols, events, reinforcements and other declared
gameplay births are covered; command/egg births and previously saved unassigned
entities retain baseline. Wither, dragon, warden and elder guardian are excluded.
No region assignment is invented outside the supported overworld area.

The finalize-spawn event records a pending birth before vanilla initialization;
the entity-join event applies permanent health/attack modifiers after native
size and attribute initialization, then fills health once. The saved
`myvillage:vanilla_monster_birth` records realm/stage, profile, actual region,
birth coordinates and reason. Later joins do not heal or reroll. Native slime
splits and hostile conversions inherit that record. Outer worldplan births
retain their actual outer region ID while consuming their profile's range;
actual region `tier` does not change that range.

Native bases such as ravager health can exceed the authored player/beast base
envelope. `RealmStats.vanillaDerived` preserves those bases and caps derived
values at the configured effective limits; it does not loosen authored-data
validation. The realm assignment still supplies the common growth factor for
armor pressure and non-attribute damage.

Melee and slime contact already consume the attack attribute. Declared arrow,
trident, projectile, magic, explosion and thorns sources from assigned vanilla
monsters instead apply one growth factor at `LivingIncomingDamageEvent` before
defense. This also covers explicitly assigned ruin monsters. Environmental
damage and unassigned attackers are unchanged. Potion duration, ongoing poison
without an attributable owner, explosion radius and block destruction are not
scaled. Existing armor/typed-resistance stages still execute once.

Bounded validation: `.venv-modal/bin/python -m tools.modal_runner run --preset vanilla-monster-realms --ref <commit>`.
The preset runs stats/wilderness unit tests, builds the deliverable jar, and
boots an isolated dedicated server twice for native spawn/damage/convert/split
fixtures and real save/restart. `src/realmEvidence/java` is opt-in through
`-PvanillaRealmEvidence`; packaging with that flag is forbidden. These are
developer fixtures, not stochastic spawn-density or mapped-player evidence.
See [the measured 0.63.1 report](../testing/2026-10-18-vanilla-monster-realms.md).

Rogue encounters select existing living, eligible unsected ledger people in
the local region. `WorldSimAvatars` is still the sole avatar spawner, and its
global identity map prevents a person appearing twice for two players. Wild
projections share global caps with sect projections and use bounded attempts
in already loaded chunks. If a region has no eligible person, no fictional
person is added to the ledger. Changed eligibility or distance withdraws the
projection. These avatars keep the existing invulnerability and disposition;
this change does not make them combatants.

Both numerical and wilderness rules are bundled startup data. Rebuild/restart
after changing them; `/reload` is not a numerical hot-reload promise.

## Validation

Run `tools/validate_realm_stats.py`, its mutation tests and the Java `stats.*`
and `wilderness.*` tests, alongside the gameplay/entity/cultivation/world-sim
gates required by `AGENTS.md`. The custom-entity validator checks declared
natural spawning resources instead of blanket-forbidding natural wolves.
The jar must contain both rule files and the wolf biome resources.
`wilderness info` reports the actual biome spawn table, difficulty, lighting and
a fixed-seed placement probe only for an already loaded FULL chunk; queries
neither load chunks nor consume the world's RNG.

`tools/realm_stats_evidence.py` collects bounded developer evidence using the
isolated physical-client capture session, server diagnostics and mapped input.
Retain failure attempts and distinguish setup, server, client, test and restart
times. The README command manual and acceptance ledger are the user-facing
entry point. Automated evidence does not establish owner combat feel, encounter
density or multiplayer acceptance.
See [the measured verification report](../testing/2026-10-14-realm-stats.md).


0.61.0 adds `myvillage:fire_spirit`, tagged as fire damage, for attributed fate burns
and bursts. It uses the existing fire-resistance channel without changing physical
hits or granting the other elemental attack routes. Derivative amounts enter the
ordinary mitigation path once, with explicit no-proc attribution; see [48](48_innate_fates.md).

## 玩家生命显示（0.80.1）

`client/hud/HealthBarLayer` 替换原版玩家爱心图层，读取已同步的 `getHealth`、`getMaxHealth`、`getAbsorptionAmount`，不改属性、治疗或伤害。朱砂条以真实生命上限为分母，吸收生命独立金条和 `+数值`。0.80.1-fix1 去掉生命、吸收和状态名称文字，保留血量数值、中毒／凋零／冰冻配色及低血量边框。`HealthBarLayout` 固定宽度和占用高度，不随生命值增加行数。

护甲／饥饿继续由原版绘制在共同基线，随后更新 NeoForge 的左右 HUD 高度预算，气泡、坐骑生命、物品名与动作提示避开生命条。创造、旁观及隐藏 GUI 沿用原版可见条件。定向检查为 `HealthBarLayoutTest` 与 Colab `tools/health_bar_evidence.py` 的画面；全套战斗与玩家手感不由此推定通过。

[0.80.1 生命条实机与构建记录](../testing/2026-10-22-player-health-bar.md)。
