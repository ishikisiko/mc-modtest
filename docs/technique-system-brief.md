# 功法与流派系统设计稿 — 2026-10-06

统筹：Fable 会话。实施：Opus 5.5 子代理，按工作包分路。本稿是设计稿加第一阶段任务书；第二、三阶段只定方向，动手前另起任务书。
不走 OpenSpec；已有 spec 在行为变化时就地改。

## 0. owner 的决定（原话，验收底线）

| 日期 | 决定 |
|---|---|
| 2026-10-06 | "流派和武器类型更多挂钩，也可以有特殊流派，暂时和宗门不挂钩" |
| 2026-10-06 | "特定的流派和特定的宗门挂钩，但广义上的流派跟广义上的能不挂钩。比如，一些特殊的宗门可以和特殊的功法流、成长路线有关系，但并不是所有宗门都有这种东西" |
| 2026-10-06 | 成长路线的含义："有可能有连续一套功法或者心法，不是每个都有" |
| 2026-10-05 | 玩法按层次做，新系统一律作为 H 面板的一页（`docs/ai-kb/37_cultivation_panel.md`） |
| 2026-09-30 | 战斗走自研，要"真实动作游戏的感觉" |

统筹给的默认值，owner 两次回复未反对，照此执行，写在这里以便推翻：一人同时只运转一门心法，换心法散功；远程法术后置，先做武器绑定的绝技和身法；经脉图暂时只做显示。

## 1. 现状（本稿从哪里出发）

- 玩家侧：注册表 `myvillage:technique` 只有 `basic_breathing`。`TechniqueDefinition` = translation_key + category（core/active/movement/body）+ grade(int) + elements + requirements（最低境界阶段、最低五行亲和）。进度只有 `masteryPoints`。无执行器，只门控打坐（`BasicBreathingEligibility`/`BasicBreathingSettlement`）。功法页 `TechniquesPage` 列已学功法。
- 命簿侧：`world_sim/techniques.json` 130 门，中文名、品阶 黄玄地天、五行或 none。每个 `Person` 恰好一门 `techniqueId`，作用是三个乘数（`rules.json` `techniques` 段：修炼、突破、战力）加五行契合加成 0.15。`Sect` 有 `signatureTechniqueId`（镇派功法）和 `basicTechniqueId`，创世随机挑。奇遇 `broken_manual`/`cave_manual`/`hermit_teaching`/`earth_inheritance`/`heaven_inheritance` 发功法。
- 战斗侧：招式集是数据 `combat/style/{basic_sword,basic_spear}.json`，武器文件绑一个 style。`CombatSession` 按 `nextMoveIndex` 走连段。伤害 = 攻击属性 × `damage_multiplier`。`currentSpiritualPower` 存着没人用。
- 三块互不相通：玩家学不到命簿里的任何一门功法，宗门的镇派功法只是个名字。

**本稿的核心主张：不造第三套，把三块缝起来。功法是修炼、战斗、命簿唯一的交汇点。**

## 2. 概念

### 2.1 流派（school）

流派是"打法层"，回答"这一下打出来是什么样"。

- **广义流派**按武器家族分：剑、枪、拳掌，后续可加刀、棍、法修等。公共的，谁都能学，不引用任何宗门。
- **特殊流派**不走招式集而走自己的运行时钩子：御剑（复用可骑乘飞剑实体做远程操剑）、法修（投射物，后置）、符阵（放置型，后置）。特殊流派可以属于某条传承（见 2.3），也可以是公共的。
- 数据：新注册表 `myvillage:school`，文件 `data/myvillage/myvillage/school/<id>.json`：

```json
{
  "translation_key": "cultivation.school.myvillage.sword",
  "kind": "weapon",            // weapon | special
  "weapon_family": "sword",    // kind=weapon 时必填；武器文件新增同名 family 字段
  "runtime": null,             // kind=special 时必填：flying_sword | spell | talisman
  "element_lean": []           // 可空；非空时该流派绝技的五行模板只取这些
}
```

- 武器 `combat/weapon/*.json` 新增 `family`。绝技要求"手持本流派家族的武器"，与具体武器无关（`combat/**` 里仍不得出现具体物品名）。
- 宗门**没有**流派字段。NPC 化身的武器不按宗门选。

### 2.2 功法（technique）

功法是"秘籍层"。沿用现有四类，每类接到已有的钩子：

| 类别 | 中文 | 接哪里 | 同时持有 |
|---|---|---|---|
| core | 心法 | 打坐收益、突破率；决定经脉图点亮哪条运气路线（只显示） | 运转一门，可学多门 |
| active | 绝技 | 战斗：往本流派招式集插一段连段，耗灵力 | 多门，受技能槽数限制 |
| movement | 身法 | 闪避/踏步，带无敌帧，耗灵力 | 多门，一个身法槽 |
| body | 炼体 | 属性修正：血量、护甲、韧性（`StaggerResistant`）、击退 | 多门，全部生效 |

`TechniqueDefinition` 扩展（全部可选，旧文件不变仍合法）：

```json
{
  "translation_key": "cultivation.technique.myvillage.gengjin_jianjue",
  "category": "active",
  "grade": 2,                       // 0 凡 1 黄 2 玄 3 地 4 天；命簿只认 1..4
  "elements": ["myvillage:metal"],
  "school": "myvillage:sword",      // 心法可无流派
  "requirements": { "minimum_realm": "myvillage:qi_refining", "minimum_stage": "myvillage:qi_refining_3" },
  "lineage": { "previous": "myvillage:gengjin_yinqi_fa" },   // 传承链上的前一门，可无
  "effects": { ... }                // 按类别，见第 3 节；第一阶段只实现 core
}
```

品阶数值（修炼、突破、战力乘数，五行契合加成）**单一来源是 `world_sim/rules.json` 的 `techniques` 段**，玩家侧通过 `SimData` 读同一张表。玩家和 NPC 受同一套公式约束，这是沙盒感的根基。

### 2.3 传承（heritage）

传承 = 一条连续的功法序列（心法链或功法链），2 到 4 门，同流派同五行，后一门以前一门为 `lineage.previous`。

- 只有少数宗门有传承，一个宗门最多一条；大多数宗门只有互不相关的基础功法和镇派功法。
- 数据：`data/myvillage/myvillage/heritage/<id>.json`：`translation_key`、`school`、`techniques`（有序 id 列表）、`exclusive`（true 时只能通过持有它的宗门或其遗迹奇遇获得）。
- 命簿：`Sect` 新增 `heritageId`（空串 = 无）。创世按 `rules.json` `genesis.heritage_chance`（默认让小世界六宗里出一两个）分配，持有传承的宗门其 `signatureTechniqueId` 取该链的最高一门、`basicTechniqueId` 取最低一门。宗门被灭：传承进入遗迹奇遇池（新 encounter effect `heritage`），别人可以捡起来重开。
- 连续性规则（数据可调）：学链上的下一门要求前一门熟练度 ≥ 小成；在同一条链内换心法不散功，前一门熟练度按比例带入。

### 2.4 熟练度

`masteryPoints` 已有。四档 初窥/小成/大成/圆满，阈值在数据里按品阶给。心法靠打坐涨（已有），绝技靠命中涨，身法靠使用涨，炼体靠受击涨。档位解锁：连段延长、灵力消耗下降、第二效果。

### 2.5 130 门怎么做出区别

手写 130 套效果不可能也不必要。效果由（类别 × 品阶 × 五行 × 流派）模板推导，名字是味道：

| 五行 | 状态模板 |
|---|---|
| 金 | 破甲、流血 |
| 木 | 回复、持续 |
| 水 | 减速、冻结 |
| 火 | 灼烧 |
| 土 | 护盾、韧性 |

真正手工打磨的只留"主角功法"：六个创世宗门的镇派功法、各传承链、天阶那十几门。

## 3. 各类效果的定义（分阶段实现）

### 3.1 心法 effects（第一阶段）

```json
"effects": { "core": { "meditation_route": "xiaozhoutian" } }
```

收益公式：现有打坐收益 × 品阶修炼乘数 × (1 + 五行契合加成 若灵根在该心法五行的亲和 ≥ `roots.element_threshold_bp`)。突破率同理用品阶突破乘数。换运转心法：`cultivationProgress` 损失 `techniques.switch_progress_loss`（新增，默认 0.3），同链例外。`meditation_route` 只决定经脉图点亮哪条路线，不是机制。

### 3.2 绝技 effects（第二阶段）

```json
"effects": { "active": { "skill": "myvillage:gengjin_jianjue", "qi_cost": 12, "slots": 1 } }
```

`combat/skill/<id>.json` 用与 style 相同的 move 模式写连段（hitbox、chain、step、reaction 全部复用），外加 `school`、`status`（五行模板）。触发：战斗模式下技能键 1–3；服务端校验 已学、手持本流派家族武器、灵力 ≥ 消耗，然后 `CombatSession` 接受一个外部连段起手，后续 tick 走现有机器。PAL 动作需要新 pose（`tools/gen_sword_pal_anims.py` 的 `POSE_TABLES`），这是真正的美术工作量，第一门剑诀按三招算。

### 3.3 身法 effects（第二阶段）

```json
"effects": { "movement": { "dash_distance": 4.0, "invulnerable_ticks": 6, "qi_cost": 8, "cooldown_ticks": 20 } }
```

服务端决定的冲量（和 step 一样 `setDeltaMovement` + `hurtMarked`），无敌窗口服务端判。没有闪避的战斗只有一半，这是第二阶段最先做的东西。

已落地：0.40.0（见 docs/movement-dodge-brief.md、docs/ai-kb/42_movement_dodge.md）

### 3.4 炼体 effects（任一阶段可塞）

```json
"effects": { "body": { "attributes": { "minecraft:generic.max_health": 4.0, "minecraft:generic.armor": 2.0 }, "stagger_resistance": 1 } }
```

属性修正器按熟练度档位缩放。

## 4. 获取循环（第三阶段的方向）

- **宗门渠道**：藏经阁作为山门里的交互面（组织是数据，建筑是可视化）；入门拿基础功法，内门、长老解锁镇派功法和传承链。依赖"玩家怎么进命簿"，未定。
- **奇遇渠道**：命簿里发功法的事件落成实体：秘籍物品（残卷/全本）出现在洞穴、秘境、遗迹等 lore 站点；现有传功碑保留做交互。NPC 能捡的玩家也能捡，争夺就是现场多一个化身。
- **人与人**：拜师传授、灵石交易、击杀掉落残卷（接进已有的夺宝和复仇链；依赖敌我态度，未定）。
- **学习要花时间**：读一本秘籍是一种新的打坐模式，花几个历日（1 历日 = 1 MC 日）。

## 5. 阶段

1. **缝目录（本任务书）**：一份目录同时喂玩家注册表和命簿；流派、传承注册表；心法接收益公式，可切换运转心法；功法页变成按类别分组的浏览器；命簿宗门带传承。没有新手感，但没有它后面都是空中楼阁。
2. **手感**：身法闪避、第一门剑诀（三招、金系流血）、灵力消耗与灵力条、技能键、熟练度涨法。
3. **循环**：秘籍物品、读书模式、藏经阁、门规品阶门控、奇遇站点落地、NPC 掉落。

## 6. 第一阶段工作包

分支 `feat/world-sim`（命簿尚未合回 main，功法目录要改命簿数据，只能叠在上面）。所有包：只 `git add` 自己的路径，提交信息末尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；重活走 `flock /home/ubuntu/code/mc/.mc-heavy.lock`；`/usr/bin/python3` 跑校验器。

### 包 A：目录与生成器（Python + 资源）

拥有：`tools/technique_catalogue/`（新）、`tools/gen_technique_catalogue.py`（新）、`src/main/resources/data/myvillage/myvillage/technique/**`、`.../school/**`、`.../heritage/**`、`src/main/resources/data/myvillage/world_sim/techniques.json`、两份语言文件中 `cultivation.technique.*`/`cultivation.school.*`/`cultivation.heritage.*` 键、`tools/validate_world_sim.py` 的交叉检查、`tools/tests/test_gen_technique_catalogue.py`（新）。不碰 Java。

1. 源表 `tools/technique_catalogue/catalogue.json`：一行一门，字段 id、zh、en、category、grade(1..4)、element、school、previous（可空）。先把现有 130 门原样导入（类别按名字后缀初分：功/经/心法/诀 → core，剑诀/剑典/剑经 → active 剑，锻体/护体/不灭体/金身 → body，其余 core），再加凡阶 `basic_breathing`（仍手写，生成器不覆盖）。
2. 流派表 `schools.json`：sword、spear、fist（拳掌，招式集暂缺，`style` 留空）、flying_sword（special）。传承表 `heritages.json`：先给 3 条样例链，每条 3 门，用现有名字串起来（如 庚金引气法 → 庚金剑诀 → 庚金不灭体 → 太白剑经）。
3. 生成器输出：每门一个 datapack 文件；`world_sim/techniques.json`（只含 grade 1..4，字段仍是 id/name/grade/element，命簿加载器不改）；`world_sim/heritages.json`（新，schema 1，`heritages: [{id, name, school, techniques:[...]}]`，给包 D 的加载器用）；语言键写入两份语言文件（只动 `cultivation.technique.`/`cultivation.school.`/`cultivation.heritage.` 前缀的键，已有键原地更新，新键成组追加在文件末尾，保持 2 空格缩进；语言文件本来就不排序）。`--check` 作为 `tools/release_gate.py` 的一个 Step（包 A 加那一行，放在 `validate_world_sim` 之前），输出不得手改。
4. `validate_world_sim.py`：命簿里的每个 technique id 必须在 datapack 里存在且品阶五行一致；宗门传承链每门同流派同五行、`previous` 链无环。

### 包 B：心法机制（Java `cultivation/**`）

拥有：`cultivation/data/**`（TechniqueDefinition 扩展、`SchoolDefinition`、`HeritageDefinition`、`ModCultivationRegistries` 两个新注册表 `myvillage:school`、`myvillage:heritage`，同步到客户端）、`cultivation/CultivationProfile.java`（schema v3 → v4：新增 `activeCoreTechnique` Optional，迁移：已学 basic_breathing 则设为它，否则空）、`cultivation/CultivationService.java`（`switchCoreTechnique` 唯一入口，散功在此）、`cultivation/technique/**`（传承连续性判断）、`cultivation/meditation/**`（收益乘以心法因子；因子只读自 `WorldSimRuntime.data()` 的 `Rules.Techniques`，命簿未加载时因子为 1）、`cultivation/network/**`（快照带运转心法；新 serverbound `CoreTechniqueSwitchPayload(ResourceLocation)`，空载荷之外唯一的数据是功法 id，处理器只调 `CultivationService.switchCoreTechnique`）、`cultivation/CultivationCommands.java`（`core <target> <id>` 及拼音别名 `xinfa`）、对应测试、`tools/validate_cultivation_*.py` 中因改名需要更新的扫描。不碰 `client/**`、`sim/**`、文档。语言文件在包 A 完成前归 A；B 的命令文案键在最后一步加，编辑前重读文件。

守住：不可变档案替换只经 `CultivationService`；快照只 clientbound；每条英文命令与拼音别名结构等价；收益规则仍全在服务端。

### 包 C：面板（Java `client/cultivation/panel/**`）

拥有：`TechniquesPage.java`（按类别分组：心法/绝技/身法/炼体；心法卡显示"运转中"标记与"运转此心法"按钮，发唯一的新意图 `SWITCH_CORE`；流派、传承链位置用 chip 显示；绝技/身法/炼体第一阶段只显示）、`MeditationPage` 的经脉图按运转心法的 `meditation_route` 选路线、`WorldPage` 宗门视图加"传承"一行、语言键（只加 `screen.*`）、面板测试。输入只经 `PanelPage` 钩子；布局在 480x270、427x240、320x240 中英文各看一次。

### 包 D：命簿（Java `sim/**` + `world_sim/rules.json`）

拥有：`sim/data/**`（加载 `world_sim/heritages.json` 进 `SimData`/`ContentTables`）、`sim/model/Sect.java`（`heritageId`，`StateCodec` 版本加一，旧档读为空串）、`sim/engine/Genesis.java`（按 `genesis.heritage_chance` 给宗门分传承；镇派/基础功法从链取）、`sim/engine/SectPolitics.java` 或 `Fortunes.java`（宗门覆灭把传承放进遗迹池；新 encounter effect `heritage`）、`sim/data/Rules.java` 与 `rules.json` 新字段、`sim/runtime/WorldSimCommands.java` `world sect` 输出、`WorldSimSnapshot` sect 段加 `heritage`、`TextKeys` 新事件键、测试。纯核心保持纯净（`SimPurityGuardTest`）。

### 顺序与接缝

A 与 B 并行（A 无 Java，B 的测试用自己的夹具，不依赖 A 的文件），D 在 A 之后（依赖 `heritages.json`），C 在 B 之后。同一工作树并行：任何时候保持可编译；编译错误在别人的包里就等一会儿再试，不代改；`.git/index.lock` 存在就等。`TechniqueDefinition` 的 JSON 形状（2.2）和 `SchoolDefinition`/`HeritageDefinition` 字段是 A、B 的契约，改了要双方一起改。`WorldSimSnapshot` 加段的方式照 `docs/world-sim-ui-brief.md`。

### 验收

- `/usr/bin/python3 tools/gen_technique_catalogue.py --check`、`tools/validate_world_sim.py`、五个 cultivation 校验器、`tools/validate_custom_entities.py`。
- `./gradlew test` 全量，`tools/release_gate.py` PASS，版本 0.37.0，CHANGELOG。
- 文档：`docs/ai-kb/41_technique_system.md`（新，进 INDEX）、`28_cultivation_core.md` 注册表表格加两行、`40_world_sim.md` 数据表加传承、README 命令表与 ledger。
- 真机：部署到 owner 的 PC（测试机，默认部署），功法页与 `world sect` 各一张图；手感类全部 `not_verified`。

## 7. 留给 owner 的

1. 第一条特殊流派做御剑（复用飞剑实体），还是先不做特殊流派。
2. 技能键默认绑哪几个键（建议战斗模式下 1/2/3 为绝技，Shift 为身法）。
3. 玩家怎么进命簿（加入宗门），决定第三阶段宗门渠道。
4. NPC 敌我态度，决定击杀掉落。
5. 经脉图是否在第二批变成机制（心法开经脉、穴位对应槽位）。
