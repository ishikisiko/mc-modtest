# 远程战斗计划（灵力池 → 剑气 → 法修投射物 → 御剑）

状态（2026-10-08）：计划稿，未动手。owner 2026-10-07 问"三种近战做完了，远程应该如何设计"，统筹在对话里给了方向，owner 说"写个md的计划"。
本文是执行用的分阶段计划，不是 OpenSpec；已有 spec 在行为变化时就地改。阶段 0 和 1 拆到工作包，阶段 2 和 3 只定方向和契约，动手前另起任务书。

起点：main `f061835`（0.45.1）。三件近战武器 青锋剑 / 凌霄枪 / 玄铁拳套 都在 `combat/style` + `combat/weapon` 的数据框架上；身法闪避 0.40.0 带无敌窗；功法目录 131 门，`effects.active {skill, qi_cost, slots}` 已能解码但没有运行时；`currentSpiritualPower` 存着没人用，没有上限、回复和 HUD 条；网络协议版本 `15`。

## 0. 已定的默认值（owner 未反对即照此执行，写在这里以便推翻）

| 项 | 决定 |
|---|---|
| 远程是什么 | 只做修仙味的三种：武器家族的"气"招（剑气 / 枪芒 / 拳罡）、法修的法术投射物、御剑。不做弓箭换皮 |
| 和近战的关系 | 同一套语法：起手 / 判定 / 收招三段，数据在 style 或 skill 文件里，时机和命中由服务端决定，拖尾 / 顿帧 / 镜头反馈照用。远程耗灵力，近战不耗 |
| 瞄准 | 服务端用玩家自己的视角（`getYRot` / `getXRot` + 眼位）在发射 tick 定方向。意图载荷仍为空载荷。轻微辅助瞄准：视线 6° 锥内把初始方向偏向最近合法目标，不追踪 |
| 蓄力 | 按住右键蓄力，松开发射。按下、松开各发一个空载荷，服务端自己计时。蓄力越久气刃越大、灵力花得越多 |
| 按键 | 战斗模式下右键 = 当前武器家族的"气"招（学了对应绝技才有）。1 / 2 / 3 预留给后续绝技槽，Alt 仍是身法 |
| 可被躲开 | 剑气推进有 tick 数，投射物有飞行时间；现有闪避无敌窗能取消其伤害。不做无前摇瞬发远程，不做锁定追踪 |
| 第一门绝技 | 黄阶、公共（不入传承）、五行 none 的 `jianqi_jue` 剑气诀，炼气一层就能学。玄阶 庚金剑诀 在它之上加三招连段和金系流血 |
| 灵力 | 上限按境界阶段写在 realm 数据里；回复按秒，乘运转心法的品阶因子，打坐时加速；花费后短暂不回复。闪避从此扣 `qi_cost` |
| 五行状态 | 金 破甲 / 流血，木 回复，水 减速，火 灼烧，土 护盾。阶段 1 只做金系流血，其余随阶段 2 的投射物一起 |
| 版本 | 每阶段一个 minor：0.46.0 灵力池、0.47.0 剑气与剑诀、0.48.0 投射物与法修、0.49.0 御剑。以 `openspec/config.yaml` 的升版规则为准 |

## 1. 设计原则（为什么这么排）

1. **不造第三套。** 剑气是现有 hitbox 采样的一种新生成器；投射物是一个共享实体，玩家法修、妖兽吐息、敌对修仙者都用它；御剑复用飞剑实体的渲染与平滑。每层只加缺的那一块。
2. **按复用程度排序。** 阶段 1 不加实体就能让三个武器家族各得一招中距离；阶段 2 才引入实体；阶段 3 工作量最大、辨识度也最高，放最后。
3. **权威不动摇。** 现有规则（AGENTS.md 战斗段）：serverbound 战斗载荷要么为空，要么只带 `INPUT_ONLY_COMPONENT_TYPES` 里的输入。本计划的新 serverbound 载荷全部为空，校验器不用放宽。
4. **境界进战斗。** 灵力上限随阶段涨，是境界第一次在打架时有分量。近战免费保证低境界也能打。
5. **证据先于手感判断。** 每阶段有 `tools/combat_capture` 子命令出证据页；手感、声音、真机帧率一律 `not_verified`，等 owner 在游戏里看。

## 2. 阶段 0：灵力池与灵力条（0.46.0）

### 目标

`currentSpiritualPower` 变成有上限、会回复、能花、看得见的资源。闪避开始扣灵力。这是阶段 1 的硬前置（技能键没有灵力条就是盲打）。

### 默认数值（可推翻）

| 阶段 | 灵力上限 |
|---|---:|
| 凡人未感气 | 0 |
| 凡人感气 | 20 |
| 炼气一层 | 30 |
| 炼气每升一层 | +10（九层 110） |
| 筑基初期 | 200 |

| 项 | 值 |
|---|---|
| 基础回复 | 每 20 tick 回 1 点 |
| 心法因子 | 乘 `CoreTechniqueFactor`（与打坐收益同一张表，无运转心法 ×1.0） |
| 打坐时 | ×3 |
| 花费后停回复 | 60 tick |
| 闪避 | 扣技能自己的 `qi_cost`（流云步 6、踏雪无痕 10），不够拒绝，reason `NO_QI` |
| 旧档 | 当前值高于新上限时首次登录截到上限；低于不动 |

### 数据与代码缝

- realm 阶段 JSON（`data/myvillage/myvillage/realm/*.json`）每个 stage 加可选 `spiritual_power_cap`（整数 ≥ 0，缺省 0）。`RealmDefinition` / stage 记录解码；`tools/validate_cultivation_core.py` 查每个有 `cultivation_cap` 的阶段都写了上限。
- 规则数值（回复节奏、打坐倍数、停回复 tick）放 `world_sim/rules.json` 新段 `qi`，经 `WorldSimRuntime.data()` 读，没有命簿数据时用 Java 默认值（和 `StudyRules` 一个做法）。
- `cultivation/qi/QiRules`（纯函数：上限、回复量、能否花）、`CultivationService.spendSpiritualPower(player, amount)` 作为唯一扣费入口（返回 boolean，失败不改档案）、`CultivationService.regenerateSpiritualPower` 由服务端 tick 每 20 tick 调一次。档案不换 schema（字段已有）。快照已带整份 profile，客户端用同步注册表里的 stage 算上限，不加载荷。
- `CombatDodgeService.handleIntent` 在冷却检查后加扣费（第 3.5 步），日志 reason `NO_QI`。
- 命令：`/myvillage cultivation|xiulian qi|lingli <target> set <n>|fill`，`info` 多打一行 `spiritual power: <cur>/<max>`。
- HUD：第一个 GUI 层。`client/cultivation/QiBarLayer` 经 `RegisterGuiLayersEvent` 注册在 `VanillaGuiLayers.HOTBAR` 之上：快捷栏正上方居中一条细条（宽 91 px、高 3 px，和经验条同宽的一半），只在战斗模式 `CULTIVATION`、或灵力值 3 秒内变过时显示；满了淡出；不够用时闪一下。颜色随运转心法五行，无心法青白。纯布局算术进测试。

### 工作包

- **包 Q-A 服务端**（Java `cultivation/**`、`combat/runtime/CombatDodgeService.java`、测试）：上面的规则、扣费入口、回复 tick、闪避扣费、命令。
- **包 Q-B 数据与校验**（realm JSON、`world_sim/rules.json` `qi` 段、`tools/validate_cultivation_core.py`、`tools/validate_world_sim.py`、Python 测试）：不碰 Java。
- **包 Q-C 客户端**（`client/cultivation/QiBarLayer.java`、`ClientCultivationState` 读上限、测试、`screen.*` 语言键）。
- **包 Q-D 文档与版本**：README 节 "Spiritual power (0.46.0)"、CHANGELOG、`docs/ai-kb/28_cultivation_core.md` 和 `42_movement_dodge.md` 的 Not implemented 更新、`bump_version.py 0.46.0`。

Q-A ∥ Q-B ∥ Q-C（Q-C 只读 Q-B 的字段名）。

### 验收

- gate PASS；`CultivationProfileTest` 不变（无 schema 变化）；`QiRulesTest`、`CombatDodgeServiceTest` 加 `NO_QI`。
- 采集 `python3 -m tools.combat_capture qi`：灵力条三种分辨率各一张；连按闪避到灵力耗尽，`DODGE_DEBUG ... reason=NO_QI` 一条；打坐 vs 站立回复速度两条日志。
- 部署到 owner 的 PC。

## 3. 阶段 1：剑气与第一门剑诀（0.47.0）

### 目标

右键蓄力放出一道沿视线推进的剑气，命中与拖尾都在现有机器上跑。`effects.active` 第一次有运行时：绝技 = 一个 `combat/skill/<id>.json`，用 style 同样的 move 模式，由 `CombatSession` 接受外部起手。顺带把枪芒、拳罡的数据位留出来（阶段 1 只做剑；枪、拳的 wave 招式在 1.x 补）。

### `wave` 生成器

`HitboxGenerators.wave(startTick, endTick, startRange, speed, halfWidth, height, radius)`：每个 active tick 一条水平横线（弯月），中心在 `startRange + speed × (tick − startTick)` 处，两端 ±`halfWidth`，离地 `height`；`tools/combat_data.py` 的 `expand_samples` 同步移植（同常数，`test_combat_data` 的"1.0 格到 range 内直立怪都被碰到"规则对 wave 改为"每个 tick 的前沿都在上一个前沿之前方"）。

服务端两处新规则（只对 `kind: wave` 生效）：
- **撞墙即停**：`CombatHitResolver` 在每个 active tick 先从上一 tick 的前沿中点向本 tick 的前沿中点做 `level.clip`，被方块挡住则本 tick 及之后的采样作废（会话记一个 `waveStopped` 标志，START 后的 IMPACT 载荷带 `stopped_at_tick` 让客户端的气刃也停在墙上）。
- **穿透数**：`maximum_targets` 就是穿透数，沿用现有的目标上限与去重。

### 蓄力与发射的会话契约

- style / skill 的 move 新增可选块：`qi_cost`（整数 ≥ 0，缺省 0）、`charge {max_ticks, release_ticks, scale_at_max}`、`launch {aim_cone_degrees}`。有 `charge` 的 move：按下进入 `CHARGING`（不是 action tick 在走，而是会话记 `chargeStartTick`，玩家移速 ×0.6），松开或到 `max_ticks` 时才真正 `start`，START 载荷多带一个字节 `chargeLevel`（0..255，服务端算），active 窗口内的采样按 `1 + (scale_at_max − 1) × level` 放大 `halfWidth` 与 `radius`，伤害与 `qi_cost` 同比例。`release_ticks` 是松手到第一个 active tick 的前摇。
- 新 serverbound 载荷 `CombatChargeIntentPayload`、`CombatReleaseIntentPayload`，都为空记录；校验器现有规则直接放行。协议版本 15 → 16。
- `CombatSessionManager.handleChargeIntent(player)`：前置与 `handleAttackIntent` 相同，再查 (1) 主手武器的 `family`，(2) 已学绝技里 `effects.active.skill` 指向的 skill 文件 `school` 的 `weapon_family` 等于它，(3) `CultivationService.spendSpiritualPower` 成功（在 start 时扣，蓄力中途被打断不扣）。都过了把 skill 的 moves 作为"外部连段"交给 `CombatSession.startExternal(skill, moveIndex)`；之后 tick、hit、stop、chain 全走现有路径，连段结束回到 style 的第一招。
- 蓄力中被打、换武器、打坐、闪避：取消蓄力，不扣灵力，停止原因 `CHARGE_CANCELLED`（追加在枚举末尾）。
- 闪避仍只能在收招段取消；蓄力段按"收招"处理（允许闪避取消）。

### 第一门绝技的数据

目录（`tools/technique_catalogue/catalogue.json`）加一行、改一行：

| id | 名 | 类别 / 品阶 / 五行 / 流派 | `effects.active` | skill 文件 |
|---|---|---|---|---|
| `jianqi_jue` | 剑气诀 | active / 1 黄 / none / sword | `{skill: myvillage:jianqi_jue, qi_cost: 8, slots: 1}` | 一招 `jianqi_jue_01_wave`：total 18、active [6, 9]、speed 2.0、startRange 1.5（到 7.5 格）、halfWidth 1.1、charge max 20 / release 4 / scale 1.5、`maximum_targets` 3、`damage_multiplier` 0.9 |
| `gengjin_jianjue` | 庚金剑诀（已有，玄 / 金 / sword，previous 庚金引气法） | 加 `{skill: myvillage:gengjin_jianjue, qi_cost: 12, slots: 1}` | 三招：两段近身剑击（复用 basic_sword 的 02、04 采样形）+ 一道大剑气（speed 2.5、到 10 格、`maximum_targets` 4、`damage_multiplier` 1.2）；命中附 金系流血 |

金系流血：`combat/status/BleedTracker`（服务端，每 UUID / 实体 id 一条），每 20 tick 掉 1 点、持续 4 次、刷新不叠加；`skill.status {kind: bleed, ticks: 80, interval: 20, amount: 1}` 写在 skill 文件里。玩家目标同样生效。

生成器：`rules.json` 的分类规则给 `active` 行加 `school` 后，`effects.active.skill` 由行级 `effects` 写入，`--check` 查 skill 文件存在且 `school` 的 `weapon_family` 与行的 school 一致。

### 表现

- `MoveKind.WAVE`：世界拖尾画"推进中的弯月 + 一个 tick 长的尾巴"，不像 cut 那样留整条带（`WeaponTrailShape` 加一种形）；撞墙后停在 `stopped_at_tick` 的位置淡出。颜色沿用武器拖尾，玄阶叠一层金色。
- 蓄力：第一人称 rig 加 `charge` key（剑横于身前，刃面朝外），聚气粒子（`ENCHANT` 向剑身汇聚），蓄满一声提示音；第三人称 PAL 加 `jianqi_jue_charge` / `_release` 两段，`tools/gen_sword_pal_anims.py` 要能读 skill 文件的 moves（pose 表与 style 的"招式集合一致"检查扩成 style ∪ skills）。
- 发射瞬间顿帧 0.5 tick、镜头后坐 `hit_pitch_kick 0.8`；命中复用 `CombatImpactFx`。
- 音：`myvillage:combat.sword.qi_release`、`qi_hit` 两个新 sound event，先复用 `cut` / `hit_heavy` 的 ogg。

### 工作包

- **包 W-A 数据、生成器、校验**（`tools/combat_data.py` wave + skill schema、`data/myvillage/combat/skill/*.json` + `index.json` `skills` 列表、`tools/technique_catalogue/**` 两行与 `effects.active` 校验、`tools/validate_sword_combat_foundation.py`（skill 文件进扫描、新载荷为空）、Python 测试）。不碰 Java。
- **包 W-B 服务端**（`combat/definition/**` wave / charge / launch / skill 解码、`combat/session/**` 外部起手与蓄力状态、`combat/runtime/CombatHitResolver` 撞墙、`combat/status/BleedTracker`、`combat/network/**` 两个空载荷 + START 的 `chargeLevel` + IMPACT 的 `stopped_at_tick`、`ModPayloads` 协议 16、Java 测试含一个驱动真实 `CombatSession` 走完剑气诀的 `JianqiJueSkillTest`）。
- **包 W-C 客户端**（`client/combat/ClientCombatEvents` 右键截获：`InteractionKeyMappingTriggered.isUseItem()` 且主手是注册武器且模式 `CULTIVATION` 时取消原版 use 并发 charge 意图，之后每 client tick 轮询 use 键抬起发 release；`ClientCombatState` 蓄力预测只播姿势不预测 start；`CombatWorldTrails` / `WeaponTrailShape` wave 形；三份第一人称 rig 加 charge key（枪、拳先复制剑的再调）；PAL 生成器读 skill；parity golden 重写；测试）。
- **包 W-D 采集**（`tools/combat_capture/wave.py`，子命令 `wave`：目标分别在 2 / 4 / 6 / 8 / 10 格处各一发，记录命中与否；一发对着 3 格外的墙，`stopped_at_tick` 与墙后目标无伤；蓄力 0 / 10 / 20 tick 三发比较伤害与灵力扣减；灵力耗尽后一发被拒；`/tick rate 5` 的 F5 背视视频。输出 `out/preview/ranged_combat/wave/`）。
- **包 W-E 文档与版本**：README 节 "Sword qi and the first skill (0.47.0)"、CHANGELOG、`docs/ai-kb/34_combat_data_and_capture.md`（wave、skill、charge、协议）、`41_technique_system.md`（active 运行时落地）、新 `45_ranged_combat.md` + INDEX、`AGENTS.md` 战斗段（skill 文件、两个新空载荷、wave 规则）、`docs/technique-system-brief.md` 3.2 标注已落地。

W-A ∥ W-B ∥ W-C（契约：第 3 节的字段名与载荷形状，统筹先写桩）。W-D 只写不跑，等构建。

### 验收

- gate PASS，版本 0.47.0；`tools/tests/test_combat_style_baseline.py` 与 `BasicSwordStyleTest` 不变（basic_sword 的五招一个数不动）。
- 采集：8 格命中、10 格不中、墙后无伤、三档蓄力伤害递增且灵力递增、耗尽被拒，各一条证据。
- 部署到 owner 的 PC；`/myvillage cultivation learn @s myvillage:jianqi_jue` 后右键能放。

## 4. 阶段 2：投射物实体与法修（0.48.0，方向与契约）

### 目标

一个共享的数据驱动投射物实体，带飞行时间、重力、撞方块、五行状态；第四个武器家族 法器，连段以施法为主。妖兽与 NPC 之后用同一实体。

### 契约草案

- `combat/projectile/<id>.json`（`CombatDataLoader` 同样严格解析，`index.json` 加 `projectiles`）：

```json
{
  "schema": 1,
  "id": "myvillage:spirit_bolt",
  "speed": 1.4, "gravity": 0.0, "lifetime_ticks": 40,
  "radius": 0.35, "pierce": 1,
  "on_block": "burst",
  "damage_multiplier": 0.8,
  "reaction": {"hitstun_ticks": 6, "slide_distance": 0.3, "lift": 0.0, "lateral_bias": 0.0},
  "status": {"from": "technique_element"},
  "visual": {"shape": "orb", "trail": "sparks", "size": 0.5},
  "feedback": {"launch_sound": "...", "hit_sound": "...", "burst_sound": "...", "hit_stop_ticks": 1.0}
}
```

- move 的 `launch` 块扩成 `{projectile: id, tick, aim_cone_degrees, spread_degrees, count}`；`kind: cast`。
- `entity/projectile/CombatProjectileEntity extends Projectile`：服务端每 tick 用 `ProjectileUtil.getHitResultOnMoveVector` 做实体与方块命中；命中走 `CombatDamageService.apply` 的 `LivingEntity owner` 重载（玩家仍保留原签名；妖兽与 NPC 作为 owner 用新重载，无耐久、无统计）；`noSave`、`clientTrackingRange 12`、`updateInterval 1`。客户端 `client/entity/projectile/CombatProjectileRenderer` 按 `visual.shape` 画（orb 用双面十字片 + 发光层，crescent 复用 wave 的弯月网格）。
- 五行状态落成 `combat/status/StatusTemplates`：金 破甲（护甲 −2，80 tick）+ 流血（阶段 1 已有），木 回复（施法者命中回 1 点灵力），水 减速（移速 ×0.7，60 tick），火 灼烧（`setRemainingFireTicks`），土 护盾（命中后施法者获 2 点吸收，一次）。模板数值在 `world_sim/rules.json` 新段 `status`。
- 法器：school `fashu` 法修（kind weapon，`weapon_family: catalyst`），物品 `yunwen_fuchen` 云纹拂尘，style `basic_catalyst` 五招：01 甩击（cut，1.8 格）、02 点射（cast，`spirit_bolt`）、03 连珠（cast，count 2，spread 6°）、04 横扫（cut，2 目标）、05 聚灵弹（cast，`charge`，`spirit_orb_heavy`，重力 0.02）。按 `docs/ai-kb/36_new_combat_weapon.md` 的 22 步走，`scaffold --from myvillage:qingfeng_sword --style myvillage:basic_catalyst`。基础法器的投射物五行 = 玩家灵根里亲和最高的那一行（`SpiritualRoot` 最大值），没有灵根则 none。
- 妖兽：`beast/<name>.json` 的 move 加可选 `projectile {id, tick, aim: locked|tracking}`，服务端在该 tick 从头部发射；现有 `BeastGeometry` 命中盒不变。第二只妖兽若是远程型，这里就是它的招。

### 留给动手前的任务书

实体同步细节、投射物与现有 `blockedByWall` 的关系、法器的建模与第一人称持法（拂尘的尾丝怎么画）、PAL 施法姿势、`combat_capture projectile` 的试验设计、妖兽远程招的 AI 用距。

## 5. 阶段 3：御剑（0.49.0，方向）

- school `flying_sword` 已存在（kind special，runtime `flying_sword`）。学了 `yujian_shu` 御剑术（active，黄阶，school flying_sword）且主手是 `family: sword` 的武器时，右键长按进入御剑：主手物品"离手"（客户端不画主手模型，画剑指 key），服务端生成 `ControlledSwordEntity`（新实体，复用 `RideableFlyingSwordEntity` 的渲染器与位置平滑，不带乘客逻辑），绕身盘旋。
- 左键 = 出击：剑沿瞄准线直线突进到视线落点（最远 12 格），路径用 wave 同样的胶囊扫掠判定，到点悬停半秒后回手；每次出击扣灵力；剑在外时近战不可用，闪避可用。再按右键或距离超 16 格收剑。
- 剑实体可被打落（有血量，掉落为物品实体，耐久扣一截），御剑术熟练度涨出击速度与盘旋半径。
- 这是最有辨识度、也是美术和同步最重的一层。动手前另起任务书：实体生命周期（照飞剑 27 的清理路径）、剑指的第一人称 rig、出击 / 回手 PAL、与骑乘飞剑的互斥、PvP 下的目标规则。

## 6. 不做的事

- 弓箭、弩、投掷物等凡人远程（原版已有，和修仙感冲突）。
- 锁定目标与追踪弹（破坏瞄准和闪避）。
- 无前摇的瞬发远程。
- 客户端上报方向、蓄力时长、命中（全部服务端算）。
- 远程给命簿 `Combat` 加新乘数（命簿战力公式不变）。

## 7. 顺序、分支与接缝

- 一阶段一分支：`feat/qi-pool` → `feat/sword-qi` → `feat/projectile` → `feat/flying-sword-control`，各自 gate PASS 后 fast-forward 到 main；push 等 owner 说。
- 阶段 0 与 `docs/female-cultivator-brief.md`（美术）文件不相交，可并行。阶段 1 的 W-C 会重写 parity golden，期间不要有别的 rig 改动。
- 所有包：只 `git add` 自己的路径；提交信息末尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；重活走 `flock /home/ubuntu/code/mc/.mc-heavy.lock`；`/usr/bin/python3` 跑校验器；Gradle / Minecraft 同时只跑一个。实施按惯例交 Opus 5.5 子代理，统筹写桩、定契约、跑 gate。
- 协议版本每加一个载荷加一（阶段 0 不加，阶段 1 到 16，阶段 2 至少 17）。
- Gradle 被打断会删掉生成的 structure / function / settlement_meta，用 `git checkout` 恢复，别把 D 行提交进去。

## 8. 风险

| 风险 | 对策 |
|---|---|
| 右键截获和原版用途冲突（盾、放方块、读秘籍、召飞剑） | 只在模式 `CULTIVATION` 且主手是注册武器时截获；秘籍与飞剑本来就不是武器；盾在副手时不截 |
| wave 推进每 tick 2 格，远端玩家看到的气刃与服务端判定错位 | 判定在服务端，表现用 START 的 facing 与本地时钟推算，与现有拖尾一样；撞墙用 IMPACT 的 `stopped_at_tick` 校正 |
| 蓄力中换目标 / 转身 | start 时才取 facing，蓄力期间可自由转身，和近战"每招取当前视角"一致 |
| 灵力数值让低境界打不了远程 | 黄阶剑气 8 点、炼气一层 30 上限 = 三发；可调；近战始终免费 |
| PAL pose 表要读 skill 文件 | 生成器的"集合一致"检查扩成 style ∪ skills，`--check` 仍是 gate 一步 |
| 投射物实体的多人同步与命中延迟 | 阶段 2 另起任务书；先用 `updateInterval 1` 和服务端命中，不做客户端预测 |

## 9. 留给 owner

1. 右键给"气"招，还是留给原版、把"气"招放到 1 / 2 / 3 槽。
2. 第一门绝技做黄阶公共的剑气诀（炼气一层可学），还是直接做玄阶庚金剑诀（要炼气四层 + 金亲和）。
3. 剑气要不要蓄力；射程 7.5 格的上限。
4. 灵力数值：上限表、回复节奏、闪避是否扣。
5. 法修做不做成第四家族；法器选拂尘、玉笛还是法印。
6. 御剑排第几；要不要在阶段 1 之后就做（辨识度最高）。
7. 灵力条的位置与样式（快捷栏上方居中细条 vs 右下角竖条）。
