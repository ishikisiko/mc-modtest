# myvillage - NeoForge Town Structure Validation Mod

## 玄枢炼器鼎（0.83.0）

新增独立多方块炼器鼎，保留原炼器鼎和锻器台。创造栏“玄枢炼器鼎”提供五类可合成部件；先放阵枢，右键按分层蓝图搭建 5×5 底座和上方鼎体，红框标出首个不符位置，悬停格子可查看世界坐标。

放入尚未炼灵的成品兵器或工具，选择锋锐／坚韧／导灵，点击“引火炼灵”。沿用原炼灵配方和炼器资质品质上限，材料从当前操作者背包或灵石袋精确转入鼎内，完成一件后停止；不额外要求器谱、历练或锻打次数。已有名字、损伤、附魔和淬炼记录保留。

炉次与材料随世界保存，缺件或区块未加载时暂停，修复后续炼，不补算离线时间。开炉者可撤炉退料；拆掉阵枢只掉落原器物、现有产物和暂存材料。共享炉内一次只炼一件，输入在活动炉次中锁定，未开放漏斗自动化。

详情见 [多方块炼器鼎](docs/features/multiblock-forging.md)；[实机预览与下载](https://assets.ishiki333hhh.win/forging_array/)、[云端验证记录](docs/testing/2026-10-23-forging-array.md)。

## H 水墨修行面板（0.79.0）

按 **H** 打开内视、修炼、功法、技能、天下、先天与技艺。七页共用宣纸主题与更清晰的正文，修炼保留分境界水墨内景。

- **功法**：按心法／绝技／身法／炼体筛选，点击图标选中，悬停查看完整效果、条件与传承；心法可点击“设为主修”。六个高级绝技有独立图案，其余功法共用大类图标，边框、角标与名称保留品阶颜色。
- **技能**：仅列可主动施放的已学功法；选中技能和 Z／C／I／J／K／M 槽位，再点击“分配到”。功法页的“分配按键”可以直接带着所选技能进入此页。支持清空、随武器、默认和按键设置，保存结果由服务器确认。
- **先天／技艺**：只读展示已确认天命、炼丹／炼器资质及品质上限；天命的完整数值放在悬停详情中。窄窗口只滚动正文，分类和底部操作保留固定位置。

[面板实现与资源归属](docs/ai-kb/37_cultivation_panel.md) · [固定源与实机验证](docs/testing/2026-10-22-ink-hub.md) · [实机截图与下载](http://43.156.135.198:8765/ink-hub/)。

凝元果树（0.76.0）：[种植、采果、完整木材件与青玉装饰](docs/features/ningyuan-tree.md)。

## 三档聚灵阵（0.74.2）

保留原四方聚灵阵，中品为四组青玉莲台，上品为四座紫金悬晶阵门与四角镇阵灯台。阵心嵌入地基中央，四座**同品级**阵枢放在东南西北地面上；中央生效区上方留两格空间。右键阵心放入灵石并“启阵”，按 V／B 修炼，X 收功。

| 阵法 | 地基 / 中央生效区 | 阵枢距中心 | 下品灵石 | 中品灵石 | 上品灵石 |
|---|---|---|---|---|---|
| 四方聚灵阵 | 7×7 / 3×3 | 3 格 | +25% | 不接受 | 不接受 |
| 中品聚灵阵 | 9×9 / 5×5 | 4 格 | +30% | +40% | 不接受 |
| 上品聚灵阵 | 11×11 / 7×7 | 5 格 | +35% | +50% | +60% |

每枚下品／中品／上品灵石分别供 **20／200／2,000 次有效加成**，不按货币百倍比例折算。正常 20 TPS、单人持续受益时约为 10 秒／1 分 40 秒／16 分 40 秒，多人逐人扣费。额外修为每批向下取整，截断到境界上限。

菜单同时显示当前余能来源及加成、下一枚燃料及加成。换入高品灵石不会提升旧余能，换入低品也不会降低已买到的余能；耗尽后才消费下一枚。停止、卸载和重启保留余能与品级。空闲、固本、研读和已满修为不耗阵能；重叠阵仍按距离、坐标选择一座，不叠加。

中品阵心／阵枢由对应的旧组件、中品灵石及石英、铜加工；上品组件由对应的中品组件、上品灵石及金、黑曜石类材料加工。取得前一级组件后解锁对应配方。创造栏“聚灵阵”提供六个核心组件及七种建筑构件。中品还需青玉阵阶 12、阵翼 8；上品还需紫金阵座 4、阵柱 32、阵梁 24、灯台基 16、镇阵灵灯 4。右键阵心，点击蓝图下方按钮逐层查看；悬停格子查看构件、朝向和相对坐标，悬停切层按钮查看整阵材料。每层以阵心上方第一格为第 1 层。旧中、上品阵补齐新构件后恢复，已有燃料与余能保留。

拆阵枢暂停，修复后恢复；拆阵心退回未转化的灵石，已转化余能散失。灵石袋先取出灵石再装阵；B 模式个人费用仍使用背包和袋中下品余额。[完整说明与验证边界](docs/ai-kb/62_spirit_gathering_array.md)。

0.74.2 为莲台玉石、悬晶增加局部柔光，镇阵灵灯照明提高至 11 级。光影包可读取附带的 LabPBR 自发光材质；实际泛光取决于其支持与设置。旧灯如未刷新周边照明，拆下重放即可。

[柔光日夜对照与新版 jar](http://43.156.135.198:8765/gathering_lighting/) · [0.74.2 验证记录](docs/testing/2026-10-22-gathering-lighting.md)。

[改造前后、jar 与独立展示世界](http://43.156.135.198:8765/gathering_architecture/) · [0.74.1 验证记录](docs/testing/2026-10-22-gathering-architecture.md)。


This repository builds a NeoForge 1.21.1 mod jar containing generated
`myvillage` structure templates. The current validation target is the Mod
resource layer plus an on-demand runtime town command: structures must be packed
in the jar and placeable in game via debug commands, and `/myvillage town
[seed]` must build a terrain-aware living town in loaded chunks.

The long-term project goal is broader than a simple village generator. The
current structure library is an early resource-validation layer for a future
town-generation system with multiple settlement categories, varied housing
types, functional buildings, roads and town pieces, and possible NPC-related
systems when the data and runtime pipeline are ready.

> **Knowledge base:** start at [docs/ai-kb/INDEX.md](docs/ai-kb/INDEX.md) — it maps the
> technical notes and [technical contracts](docs/contracts/README.md).
> **Workflow:** [docs/WORKFLOW.md](docs/WORKFLOW.md); [CRAFT](CRAFT.md) is optional.

游戏内山海舆图：按 **H → 天下 → 舆图**，查看当前修仙大陆存档的州界、州名、海陆和自身位置；支持拖动、滚轮缩放、归位和收卷。普通世界会提示没有修仙大陆规划。地图包含未探索地区的宏观划分，不显示地形细节或玩家改建。

## 青铜丹炉阵（0.72.0）

首台 3×3×3 多方块机器，使用 21 块青铜炉壁、炉心控制器、进药口、出丹口、灵石口、排气盖各一。创造栏“青铜丹炉阵”提供所有组件，生存可合成；逐块搭建，中央留空。先放正面中层炉心，右键打开“搭建蓝图”，查看三层布局、缺件坐标和端口朝向。

学会丹方并觉醒后，选择丹方补齐首批材料，再点击“启动连续炼制”。站在炉前面向炉体时，进药口在右侧中层，出丹口在左侧底层；进药与灵石口的外侧可接漏斗，出丹口下方接漏斗取成品。四个成品槽可存放不同品质；缺料等待、堵口暂停、拆坏停机，修复继续原批次，停止按钮只阻止后续批次。原单方块丹炉保持原有操作。

[结构、研究依据与技术边界](docs/features/multiblock-alchemy.md) · [游戏内搭建指南源页](guidebook/getting_started/alchemy_array.md)。
管理员只读诊断：`/myvillage array inspect <x> <y> <z>`；端口模拟：`/myvillage array port <x> <y> <z>`。
`/myvillage array stale <端口坐标>` 仅供空炉测试，会临时拆装并恢复测试炉，不能用于普通生产验收替代真实交互。

## 雷角山君（0.69.0）

灰白黑纹、额生雷角的大妖兽，固定筑基初期遭遇。四类招式有蓄势与收招，受重创后进入第二阶段；诱导扑击撞上巢穴石柱，再攻击低下的头部可破角并压制雷法。身体与头部共享生命，断角、阶段和伤势会随正常保存恢复。

- 生存入口：主世界山地与草甸的新生成区块中的石谷巢穴；玩家走入深处会唤醒该巢穴唯一的山君。已击杀的巢穴不会重新刷新。
- 玩家提示：`/exploration guide tiger`；城镇向导也提供“山君传闻”入口。
- 管理员定位：`/locate structure myvillage:thunder_tiger_lair`；独立诊断可用 `/summon myvillage:thunder_tiger` 或创造栏雷角山君刷怪蛋。
- 本轮奖励暂不配置。匹配资源、生命周期与实际验证边界见 [KB61](docs/ai-kb/61_thunder_tiger.md)。

## 月洞水院建筑样板（0.68.1-fix1）

新增“月洞水院 · 建筑构件”创造栏，包含粉墙、木构、灰瓦、青石和灯饰等细体素构件。
在独立测试世界的空地上，管理员可执行 `/place template myvillage:reference_courtyard ~ ~ ~` 放置完整样板；
范围 48 × 28 × 40 格，以命令坐标为最小角，模板包含空气，会覆盖该范围内的原有方块。
创造栏和 JEI 各只有 10 类入口，手持时 Shift＋右键选择共 41 个款式（含薄木格门楣和整体弧顶，瓦面支持分页）。窗为完整 3×3 物品，月洞门为 5×5 物品；放置整件消耗一个，拆除任意部分整体移除并只掉落一个。模型用 1/16 格细节表达曲线，水池使用真实水。

[构件来源、导出与验证边界](docs/features/reference-courtyard-minecraft.md)。默认不在世界中自然生成，不替换已有城镇。

## 原版探索与城镇向导（0.66.0）

原版探索接入修炼：凡人安家，炼气探索主世界，筑基深入下界，金丹挑战末地。第二版补齐末地主岛／外岛／末地城的独立普通怪物范围，以及远古守卫者、末影龙、凋灵、监守者的独立首领配置。保留原版 AI、龙的部位和阶段、凋灵护盾与监守者音爆机制；这些是初始调平，玩家手感和长期平衡仍待试玩。

指定箱子和普通／不祥宝库保留原版战利品并追加有限修炼物资，末地城只在原版宝箱首次展开时追加末地奖励；旧打开过的箱子不补奖。烈焰粉、通用铜锭和下品灵石可合成一块赤铜锭，原灌灵路线也可用。

云山客栈接待区会从当地现有命簿人物中安排一位城镇向导，右键可询问个人下一步和 tips。白天接待、夜间回客房；实际设施未加载、没有空闲人物时岗位会空缺。`/exploration guide where` 可查询已就位向导的位置。旧已绑定且有对应设施的城镇也可安排。

普通玩家可用 `/exploration` 查看当前建议，`/exploration guide` 查看下一步，`/exploration guide tips` 再听一条，`/exploration catalogue` 查看全部基础建议。路线延伸到击败龙、末地折跃门、末地城和鞘翅，通过 `/exploration guide side` 查看三个可选特殊首领的准备路线，它们不阻塞主线；修行入门录有双语探索页。可选传送门限制默认关闭，默认仍可提前进入原版地点；服主可在实例目录 `config/myvillage-exploration-server.toml` 的 `[portal_gates]` 设置 `enabled = true`，无需重打 jar。若 `<世界目录>/serverconfig/` 中已存在同名文件，该世界优先读取它；可手动创建以按世界覆盖。`defaultconfigs/` 下的同名文件仅用于基础配置缺失时初始化，不会为每个新世界自动创建覆盖文件；最低境界仍由探索 JSON 配置维护。命簿格式 7 兼容旧版本读取。

[配置、实现与边界](docs/ai-kb/60_vanilla_exploration.md) · [第二版验证记录](docs/testing/2026-10-18-vanilla-exploration-v2.md)。

## 云山活城与完整主城 V4（0.64.0）

新建“修仙大陆（种子蓝图）”使用 V4，完整云山 D1 主城按新区块自然生成，保留 70 所民居、四栋功能建筑、街道、湖面与原台地高差。旧 V1/V2/V3 世界继续使用原冻结内容，不自动迁移或还原玩家改建。

通过 `/myvillage worldplan city` 找到当前种子的主城。接近并加载真实住处/岗位后，新 V4 会从当地现有散修安排店主；近距右键当值药师可买丹药/卖药材，铁匠可买卖有限金属商品，客栈可付费恢复生命与灵力。潜行右键保留旧交谈。库存、公库与冷却由服务器和原命簿保存，没有足够人物或建筑未加载时不会凭空补人。

管理员可用 `/myvillage life status site yunshan` 查看，或 `/myvillage life commerce yunshan` 幂等启用并重试未分配岗位。旧世界经济需显式启用；绑定场所不等于放置建筑。客栈本版是受条件和冷却约束的一次恢复，不是租房或跳过夜晚。

[完整操作、兼容与范围](docs/ai-kb/58_yunshan_living_city.md) · [本次检查与待实测项](docs/testing/2026-10-18-yunshan-living-v4.md)。新客户端和服务器需一起更新，协议 33、命簿 payload 6。

## 持剑 NPC（0.63.0）

修仙者现可用玩家同一套基础剑连招进行自卫，负伤后撤退；宗门、野外散修和城镇居民的损耗及死亡跟随命簿人物保存。战斗期间居民停止生活收益，脱战后重新安排活动。当前范围和实际验证见 [NPC 战斗说明](docs/ai-kb/57_npc_sword_combat.md) 与 [云端验证记录](docs/testing/2026-10-18-npc-sword-combat.md)。

在创造测试场景可用 `/summon myvillage:cultivator ~ ~ ~` 或修仙者刷怪蛋生成中立持剑样板；切换生存模式和修炼战斗模式后，持青锋剑攻击即可观察自卫。召唤样板使用实体存档；真实命簿人物由既有宗门、野外和居民入口投射。杀死真实命簿人物会永久记录死亡与社会后果。


## NPC 半自主生活首期（0.60.0-fix1）

命簿现在为每位登录玩家保存独立社会身份，并能把现有人物分配到真实城镇的住处与岗位。散修可在生活需求、盘缠和性格影响下选择歇息、当值、采药、交货、吐纳和小聚；右键生活化身会显示当前安排，短暂停步，并记住与玩家的交谈。

支持规划大陆中的云山城和现有大型云山展示街区。先登记场所，再显式分配人物，示例会选择四位当地已有散修。命令需要管理员权限，`<...>` 参数用实际值替换：

```text
/myvillage life yunshan
/myvillage life status site yunshan
/myvillage life place yunshan herb_field gather <x> <y> <z>
/myvillage life demo yunshan
/myvillage life status <personId>
```

采集点选可站立、可步行往返的已加载位置。旧世界或已有展示街区可用 `/myvillage life bind <site> yunshan_showcase_v2 <原点x> <原点y> <原点z>` 登记；该命令不创建建筑。示例缺人时会说明原因，也可用 `assign` 手动安排当地存活修士，`unassign` 解除岗位。

近处需要实际走到目标格，远处沿用相同活动记录；工作进度跨化身撤离和存档保存。0.60.0 首期的物资为有限命簿账户；0.63.0 已接真实战斗和死亡，0.64.0 已接玩家交易，仍未接真实灵植自动收割或完整 NPC 实体背包。本期的休息与工作使用到点停留表现。

实现说明见 [KB56](docs/ai-kb/56_npc_semi_autonomy.md)，**云端验证结果、构建入口与尚未验证的项目见 [NPC 生活验证交接](docs/testing/2026-10-17-npc-life.md)**。新版本写入命簿 v4，旧存档迁移与回退检查请在存档副本进行。

## 修仙大陆与局部生态过渡（0.59.1-fix1）

0.59.1-fix1 的新建世界使用 V3 局部过渡（0.64.0 的新世界已切到保留该生态规则的 V4）：保留原雪林、竹林等主体群系，仅在不相容交界 32 格内调整植被。已有 V1/V2 世界保持各自冻结规则，不自动重刷。`/myvillage worldplan info` 可查看算法和规划哈希。城市审计区分正常遮阴草方块退化与实际损坏，同时保留原始差异。

创建方式、旧档兼容与验收见 [修仙大陆说明](docs/worldplan/acceptance.md)。局部云端构建使用 Modal `--preset worldplan-build`；玩家画面与手感仍单独验收。

## 剑气蓄势动作（0.56.0-fix3）

按住右键即将剑收到左侧蓄势；保持蓄力时维持该姿态，松开或满蓄力时直接接横斩。
取消蓄力会收回。第一、第三人称均有蓄势动作，快点按与自动释放保留各自正确的出射时机。

## 横斩薄刃剑气（0.56.0-fix2）

剑气改为横斩同平面出射，刃口扫过前方时释放；窄白青前缘、渐隐薄月牙和分离短尾，
替换原先鼓起的半透明楔体。保留 fix1 的扩大范围，首帧从出射点连续衔接。

## 剑气范围修正（0.56.0-fix1）

剑气诀的视觉与命中范围同步扩大：点按宽 4 格、厚 0.9 格，满蓄力宽 6 格、厚 1.35 格。
沿用月牙形剑气；侧缘碰墙仍会阻挡整道剑气。技术与验证见 [KB45](docs/ai-kb/45_spiritual_power_sword_qi.md)。

## 引魔珠（0.56.0）

手持 `myvillage:demon_lure_pearl` 右击地面，消耗一颗召出引魔试炼核心。
需要 13×13 格平整实心地面、上方 3 格净空，并远离其他试炼/遗迹宝箱至少 32 格。
成功召唤才扣除；和平难度、障碍、场地未加载等失败不扣珠。建议炼气一至三层。

三波依次为近战魔卒、妖狼与弓手混编、精英妖狼与近战护卫。全部击败后核心转青，
右击领取共享的 12 下品灵石、2 回灵丹和 2 养元丹。取空后核心消失，可以再用新珠开启下一场。
死亡、离开和正常重启保留进度；敌人卸载不算击败。创造模式不耗珠，但战斗需生存玩家在场。

工作台配方：上、下各一枚下品灵石，中心末影珍珠，左、右各一根骨头。创造栏也可取得。
管理员试玩：`/give @s myvillage:demon_lure_pearl`。

## 战斗遗迹（0.55.0）

- **断戈营垒**：建议炼气一至三层，从残门向主台推进，依次击败三处守军，主台宝箱解封。
- **伏妖残殿**：建议炼气四至六层，沿台阶进入下沉大殿，右击封印宝箱启动三波妖兽挑战。
- 通关宝箱为整座遗迹共享的一份奖励：营垒含剑气诀秘籍，残殿含流云步秘籍，另有灵石与丹药。
- 可撤退、死亡后返回或保存退出继续；普通重启不刷新奖励。和平难度暂停挑战，移除的守卫不计为击败。
- 箱体不可挖掘或搬运，不支持漏斗。箱体封印由红转青后，右击打开并手动取出奖励。
- 新结构仅在新生成区块出现；旧区块不回填。

```mcfunction
/locate structure myvillage:duange_ruin
/locate structure myvillage:fuyao_ruin
```

管理员可在空旷测试区用 `/place template myvillage:duange_ruin ~ ~ ~` 或
`/place template myvillage:fuyao_ruin ~ ~ ~` 放置同一模板；模板原点位于基础底部，
自然生成会自动扣除各自基础深度。纯创造模式靠近不会自动触发营垒，试玩需切换生存。
技术入口：[战斗遗迹 KB53](docs/ai-kb/53_combat_ruins.md)。


## 丹霞旧观 · 遗迹探索（0.54.0）

在新生成的森林、桦木林、草甸、平原或樱花林中寻找破损道观。沿铜渠下到地下丹室，
空手右击丹霞残阵预览药园收益；潜行并空手右击预览内库收益。再次执行同一动作确认。
每座遗迹仅有一次残泉，所有玩家共享选择；离开或正常重启不会恢复。

- 药园：复苏成熟的六种灵植和灵土，可采收后带回种植。
- 内库：在地下木桶中复原《青木长春功》秘籍、凝神丹方与灵石。
- 外围有少量补给，两只固定境界的妖狼分别栖于地表和内库。后殿塌洞提供第二条路线。
- 方块可挖掘；残阵破坏后不掉落。阵位被修改时交互会拒绝，不消耗残泉。
- 旧区块不会回填。管理员定位：`/locate structure myvillage:danxia_ruin`。
  模板预览：`/place template myvillage:danxia_ruin ~ ~-9 ~`，当前站位对应地表。

构建与游戏检查在 Modal：`--preset danxia-build` / `--preset danxia-runtime`。
技术说明见 [KB52](docs/ai-kb/52_danxia_ruins.md)；外观、平衡、音效/FPS 和多人接受度另行验收。

## 云山巨城 · Browser Voxel Artwork

`web/yunshan-city/` is a standalone browser project (independent of the mod
jar): a three.js voxel scene of a terraced Chinese mountain city above a sea of
clouds, built entirely from 0.2 m voxels, with a guided road tour and a
continuous 24-hour day/night cycle. Open `web/yunshan-city/dist/yunshan-city.html`
directly, or run `npm start` inside `web/yunshan-city/` and visit
<http://localhost:8080/>. Controls and architecture are documented in
[web/yunshan-city/README.md](web/yunshan-city/README.md).

## Development Workflow

Follow [docs/WORKFLOW.md](docs/WORKFLOW.md): scope the change, implement it,
run the necessary checks, then commit and report. Expand checks only when a
specific failure or affected boundary justifies them. Existing full checklists
below document complete acceptance options and historical runs; they are not
the default for every edit. Tooling and non-packaged documentation changes do
not require a mod version bump, jar build, or game launch.

[GenOps](genops/README.md) is optional and used only on explicit request.
Its pipelines, scoped roles, patch guards and run evidence remain available;
ordinary work needs no proposal, CRAFT run, role approvals or archive ceremony.

## Resource Path

Use the singular Minecraft/NeoForge structure resource directory:

```text
src/main/resources/data/myvillage/structure/
```

Do not use `src/main/resources/data/myvillage/structures/`.

## Generate One JSON DSL Structure

`test_house_03` is the smoke-test JSON DSL structure.

```bash
python3 tools/validate_structure_json.py examples/test_house_03.json
python3 tools/json_to_nbt.py examples/test_house_03.json src/main/resources/data/myvillage/structure/test_house_03.nbt --mc-version 1.21.1
```

If your shell maps `python` to Python 3, the same commands also work with
`python`.

## Generate All Mod Structures

The canonical batch command generates `test_house_03.nbt`, the
`medieval_village` building library, the default Chinese courtyard compound
library, the civic library, the cultivation town standalone/block libraries,
and the cultivation sect standalone/compound libraries:

```bash
python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure
```

Expected structure output:

```text
src/main/resources/data/myvillage/structure/test_house_03.nbt
src/main/resources/data/myvillage/structure/small_house_001.nbt ... small_house_010.nbt
src/main/resources/data/myvillage/structure/medium_house_001.nbt ... medium_house_010.nbt
src/main/resources/data/myvillage/structure/blacksmith_001.nbt ... blacksmith_010.nbt
src/main/resources/data/myvillage/structure/small_shop_001.nbt ... small_shop_005.nbt
src/main/resources/data/myvillage/structure/medium_shop_001.nbt ... medium_shop_005.nbt
src/main/resources/data/myvillage/structure/big_house_001.nbt ... big_house_005.nbt
src/main/resources/data/myvillage/structure/main_hall_review.nbt
src/main/resources/data/myvillage/structure/side_wing_review.nbt
src/main/resources/data/myvillage/structure/front_row_review.nbt
src/main/resources/data/myvillage/structure/chinese_courtyard_001.nbt ... chinese_courtyard_006.nbt
src/main/resources/data/myvillage/structure/tavern_001.nbt ... tavern_005.nbt
src/main/resources/data/myvillage/structure/lord_manor_001.nbt ... lord_manor_003.nbt
src/main/resources/data/myvillage/structure/cultivation_house_001.nbt ... town_shrine_003.nbt
src/main/resources/data/myvillage/structure/cultivation_town_001.nbt ... cultivation_town_006.nbt
src/main/resources/data/myvillage/structure/sect_gate_001.nbt ... sect_gate_002.nbt
src/main/resources/data/myvillage/structure/sect_main_hall_001.nbt ... sect_main_hall_002.nbt
src/main/resources/data/myvillage/structure/scripture_pavilion_001.nbt ... scripture_pavilion_002.nbt
src/main/resources/data/myvillage/structure/alchemy_room_001.nbt ... alchemy_room_002.nbt
src/main/resources/data/myvillage/structure/disciple_quarters_001.nbt ... disciple_quarters_002.nbt
src/main/resources/data/myvillage/structure/cultivation_sect_001.nbt ... cultivation_sect_002.nbt
src/main/resources/data/myvillage/settlement_meta/cultivation_sect_001.json ... cultivation_sect_002.json
```

The Gradle build also runs this batch generator before packing resources, so
v0.8 jars are expected to contain individual buildings, compound structures,
civic/cultivation structures, plaque blocks, and inscription assets used by the
runtime town command.

The current generator data is populated for the full external-mod profile.
When those staged mods are installed, generated market stalls, sect-gate decor,
ritual anchors, lighting, furniture, and canopy/eave details may use confirmed
ids from Ars Nouveau, Farmer's Delight, Fetzi's Displays, Macaw's Furniture,
Macaw's Windows, and Supplementaries. The Python style loader still supports a
vanilla namespace profile for regression checks; every populated slot keeps a
trailing `minecraft:` fallback.
The batch generator also exports
`src/main/resources/data/myvillage/mod_block_fallbacks.json` from those slot
lists. Runtime `/myvillage place` and `/myvillage town` template placement use
that map so worlds without the optional decor mods place vanilla fallbacks
rather than air holes.

## Generate Chinese Courtyard Compounds

Generate the Chinese courtyard review sub-buildings plus the default compound
library:

```bash
python3 tools/generate_compound_library.py --count 6
python3 tools/validate_compound_library.py --count 6
python3 tools/generate_compound_library.py --count 6 --profile vanilla
python3 tools/validate_compound_library.py --count 6 --profile vanilla
python3 tools/generate_compound_library.py --group chinese_huipai_mansion --count 2 --base-seed 20260619
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/generate_compound_library.py --group ganlan_stilted_house --count 2 --base-seed 20260708
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
```

The six `chinese_courtyard_NNN` templates are rebuilt 一进 compounds: an outer
yard with 影壁, one 垂花门 into the raised main yard, returning 抄手游廊, and a
正房 fronted by 月台. Their filenames and `/myvillage place` commands are
unchanged, but v0.15.0 regenerates their footprints, silhouettes, and interiors.
v0.15.0-fix1 rebuilds the courtyard **ground + path layer** (`courtyard-ground-layer`
/ `courtyard-path-network` specs): the yard floor is now solid (露天 grass /
屋檐下 stone_bricks) and a multi-source-BFS path connects every door, water
feature, planting bed, and the moon platform, with a single stairs block at the
plinth edge — the courtyards are walkable end-to-end.
v0.16.0 ships the **3-进 江南大宅 (`chinese_mansion`)** family: six new NBTs
`chinese_mansion_001..006.nbt`, a `garden_rockery` / `garden_pond` / 亭 garden
layer, the `open_hall` and `tower_house` archetypes, the `myvillage:rockery_block`
self-namespace block, and the voxel-walkability 3D BFS validator for all compound
families. Also regenerates `chinese_courtyard_001..006.nbt` with 照壁侧立
off-axis screen wall and ≥3-cell 垂花门 passage.
v0.16.2 re-sculpts the mansion garden's named hero 假山: a layered, 收分 太湖石
(stone-dominant with moss accents, per `docs/mt.png`) procedurally authored by
`tools/buildgen/gen_hero_rockery_sculpt.py`, with a spring that issues from a
grotto inside the rock and cascades down the terraces into a pool embedded in the
foot. It also fixes the v0.16.1 flood: `waterlogged` rock (a spreading water
source) is dropped entirely — visible water is a contained pool + non-fluid
`rockery_cascade` only. Review it directly with `/myvillage place hero_rockery`;
preview the 48³ sculpt offline with `tools/buildgen/preview_voxel_field.py`.
v0.16.2-fix1 keeps the summit tree at the same 1/16 scale as the rockery: it is
now a baked leaning bonsai with visible branches and layered foliage instead of
ordinary full blocks. The spring's micro-water is also baked from one connected
grotto-to-pool path that follows the terraced rock face; the fixed exterior
`rockery_cascade` column is no longer placed. Real water remains sealed in the
山脚 pool.
v0.17.0 rebuilds the six `chinese_mansion_*` templates with the enclosure
planning skeleton: the south entrance is now a real `gate_house`
through-building instead of a wall hole, mansion buildings use form-rule
door-wall facings so their doors face their yards, and the gravel path is routed
from the gate-house inner opening to every door-front. Command names are
unchanged: `/myvillage place chinese_mansion_001` ... `_006`.
v0.18.0 **surface-zones** the courtyard/mansion ground + path layer so the path
reads as **three routes with six surfaces** instead of one flat gravel stripe:
the formal axis (青石 `PATH_FORMAL`), the winding garden tour (苔石 `PATH_TOUR`,
a waypoint polyline 假山→水岸→亭), and the waterside stairs + slab bridge
(`PATH_WATERSIDE`); the ground splits into 天井 心 (`GROUND_YARD_HEART`), 廊下
(`PATH_GALLERY`), and 夹道 (`PATH_ALLEY`) around the open grass. The mansion
garden gains the 月洞门 穿墙通道 (the formal↔tour material boundary), a dry-bank
reference-style 水亭 that directly touches the pond edge with a raised stone
base, dark wooden deck, heavy timber posts, lanterns, broad double eaves, and
stone roof ornaments, and a 仆役房 along the 倒座 夹道;
the cross-pond 汀步 spike-row is replaced by a flat slab bridge, with sparse
lily pads kept out of the bridge clear-water lane. The separate pond-side
水边廊/shed was removed after reference-image review; the mansion 主院 抄手游廊
remains a real 3D gallery with floor, columns, balustrade, and roof;
the 绣楼 sits in its own 后院 instead of the 花园, and the 主院 heart remains
grass rather than a full-width stone platform. `/myvillage place` ids are
unchanged — only the surface materials, gallery realization, and garden routing
change.
See [`docs/ai-kb/16_path_surface_zoning.md`](docs/ai-kb/16_path_surface_zoning.md).
Run the final full-profile generation after a vanilla-profile proof to restore
the shipped artifact profile.
v0.19.0 adds the first external-reference-driven original output:
`chinese_huipai_mansion_001..002`, derived from the `candidate_003` Hui-style
breakdown as generator grammar only. It keeps the source as `local_research`
provenance and implements the narrow recognizable slice: closed white street
facade, dark roof, stepped 马头墙 cue, and 门堂 → 天井一 → 享堂 → 天井二 → 寝堂
sequence with paired 厢房/side-wing enclosure around the sky-wells, an expanded
`47x76` / `43x72` review-lot footprint, clear spacing between the three-in
sequence elements, larger and taller hall / side-wing massing, and no 江南 garden
parcel. Visual acceptance
still requires reviewer verdict after preview.
v0.19.1 adds the second external-reference-driven original output:
`ganlan_stilted_house_001..002`, derived from the `candidate_005` Ganlan /
干栏式 breakdown as generator grammar only. It keeps the source as
`local_research` provenance and implements the narrow recognizable slice:
humid fully elevated bamboo/wood living floor, bay-aligned support posts and
underfloor tie beams down to the ground/water plane, mostly open underside,
framed permeable walls, an offset stair leading across a raised veranda, a
lower rain canopy beneath the main gable, and water passing below part of the
floor. It is a generated
sample family for review, not a copied Ganlan village, jigsaw pool, or runtime
worldgen integration. The owner accepted this narrow visual slice on
2026-07-11 after preview and automated validation; that verdict does not imply
broader Ganlan village or worldgen acceptance.
v0.20.0 rebuilds the existing `pagoda_001..003` landmarks instead of adding a
new building family. The three deterministic profiles now use five, five, and
seven occupied storeys; body footprints from `15x15` to `19x19`; stepped inset
schedules; a projecting bracketed eave at every storey boundary; framed upper
openings; first-storey colonnades; pyramidal crowns; and taller finials. The
compact `19x37x21` resource remains the fixed town-core pagoda, while the broad
`27x46x29` and slender `23x56x25` variants provide larger standalone/sect
landmarks. Python and Java footprint mirrors contain all three. The ids and
commands are unchanged, `candidate_006` is calibration-only provenance, and
final appearance still requires the new owner visual verdict.

Expected compound output:

```text
src/main/resources/data/myvillage/structure/main_hall_review.nbt
src/main/resources/data/myvillage/structure/side_wing_review.nbt
src/main/resources/data/myvillage/structure/front_row_review.nbt
src/main/resources/data/myvillage/structure/chinese_courtyard_001.nbt ... chinese_courtyard_006.nbt
src/main/resources/data/myvillage/structure/chinese_huipai_mansion_001.nbt ... chinese_huipai_mansion_002.nbt
src/main/resources/data/myvillage/structure/ganlan_stilted_house_001.nbt ... ganlan_stilted_house_002.nbt
src/main/resources/data/myvillage/function/gallery/chinese_courtyard.mcfunction
src/main/resources/data/myvillage/function/gallery/chinese_huipai_mansion.mcfunction
src/main/resources/data/myvillage/function/gallery/ganlan_stilted_house.mcfunction
src/main/resources/data/myvillage/function/place/chinese_courtyard_001.mcfunction ... chinese_courtyard_006.mcfunction
src/main/resources/data/myvillage/function/place/chinese_huipai_mansion_001.mcfunction ... chinese_huipai_mansion_002.mcfunction
src/main/resources/data/myvillage/function/place/ganlan_stilted_house_001.mcfunction ... ganlan_stilted_house_002.mcfunction
```

The compound exporter currently uses single structure NBT files. The one-court
Chinese compounds stay compact; cultivation town blocks and mountain sect
compounds are larger review structures and are spaced by the generated gallery
functions and `/myvillage gallery` command.

## Generate Cultivation Libraries

Generate the mortal town building/block group and immortal sect group directly:

```bash
python3 tools/generate_building_library.py --group cultivation_town --count 3 --base-seed 20260613
python3 tools/generate_compound_library.py --group cultivation_town --count 6 --base-seed 20260617
python3 tools/generate_building_library.py --group cultivation_sect --count 2
python3 tools/generate_compound_library.py --group cultivation_sect --count 2 --base-seed 20260616
```

These commands write group-specific reports under `reports/`:

```text
reports/cultivation_town_building_library_report.json
reports/cultivation_town_compound_library_report.json
reports/cultivation_sect_building_library_report.json
reports/cultivation_sect_compound_library_report.json
```

Sect compound generation also writes placement metadata sidecars under
`src/main/resources/data/myvillage/settlement_meta/`, including siting context,
relative terrace levels, hierarchy, and gallery/bridge link endpoints.

## Validate Generated NBT

Run the NBT-level integrity checks after generation:

```bash
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_mod_block_fallbacks.py
python3 tools/validate_plaque_bindings.py
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_compound_library.py --group cultivation_town --count 6
python3 tools/validate_compound_library.py --group cultivation_sect --count 2
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/buildgen/tests/test_huipai_reference_slice.py
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
python3 tools/buildgen/tests/test_ganlan_stilted_house.py
python3 tools/validate_civic_library.py
python3 tools/validate_town_generation.py
python3 tools/validate_runtime_town_plan.py
python3 tools/check_style_policy.py
python3 tools/check_cultivation_forms.py
python3 tools/validate_region_topology.py
```

The validator checks that files exist, palettes and blocks are non-empty, roof
blocks exist, the top layers are not empty, key stairs/slabs/logs/planks are
present, gable closure is heuristically checked, and building interiors contain
the expected function blocks. Blacksmiths must contain forge-equivalent blocks;
houses must contain crafting/furnace/barrel-style utility blocks; civic
structures must contain tavern or lord-manor signature role blocks; cultivation
town and sect structures must contain their expected town/sect signatures.
`myvillage:` plaque block ids are accepted as shipped self-namespace resources
under both `full` and `vanilla` validation profiles, while unrelated external
mod ids remain profile-gated. Plaque-bearing archetypes additionally require
plaque blocks whose block textures have the bound inscription baked in, and
`validate_plaque_bindings.py` checks that each binding points at an existing
frame preset and inscription asset. Generated structures must not contain
`myvillage:inscription/...` painting entities; inscription paintings are not
used at runtime because they can fail vanilla hanging-entity survival checks
and drop as painting items.

## Region Topology (Offline Layer)

The region (洲/域) layer is the macro geography the mod was missing — a
per-seed region graph of 5–7 洲 with a single 中州 `anchor` at the center,
rule-governed 连 (passable) / 隔 (separated) relations, a tier gradient, and a
sealed 魔域-style `walled` region. It is the top of the OTG stack
(WorldConfig region rules + a constrained-random FromImage-like geography),
delivered **offline-first**: data drives generation and validation before any
runtime chunk-gen. The authored catalog and ruleset ship as JSON under
`src/main/resources/data/myvillage/worldgen/`; a canonical example graph ships
at `worldgen/region_topology_example.json`. See
[`docs/ai-kb/13_region_topology.md`](docs/ai-kb/13_region_topology.md).

```bash
# Emit the region graph for a seed as JSON (constructive, seed-deterministic):
python3 tools/generate_region_topology.py 20260620
python3 tools/generate_region_topology.py 20260620 --out reports/rt.json
python3 tools/generate_region_topology.py 20260620 --check-determinism

# Validate structural invariants + determinism + deliberate breaks, and write
# the multi-seed survey to reports/region_topology_validation.json:
python3 tools/validate_region_topology.py

# Render per-seed SVG + ASCII previews under out/preview/region_topology_s*/:
python3 tools/generate_region_topology_preview.py --count 6
```

The graph lists regions (tier/role/position) and a typed edge list: `连` edges
are passable; `隔` edges carry a separator (`特殊山脉` or `特殊海洋`); a
`walled` region's single retained `连` edge is marked `关隘`. The offline
generator remains the single source of truth and writes no world blocks. As of
`add-region-runtime-binding`, a **runtime companion** places the per-seed graph
into the world (中州 at the world origin, all 洲 within a ~4000-block radius),
binds world spawn deterministically to the lowest-tier eligible region, and
exposes a `region_at` / `current_rung` / `next_rung_regions` query API for
downstream consumers (compass / map / alignment / mobility — all still
deferred). The runtime is passive: it reads the world seed and answers queries;
it overrides no biome, hooks no chunk-gen, and writes nothing beyond the
one-time `setDefaultSpawnPos`. See `/myvillage spawn info|recompute` below and
[`docs/ai-kb/13_region_topology.md`](docs/ai-kb/13_region_topology.md). Turning
the typed edges into actual relief (山脉/海洋 ranges) and placing subjects into
regions remains the deferred next change.

## Preview Structures Offline

Render structures to offline PNG and HTML previews without launching the game,
to eyeball layout, massing, roof form, and fenestration before doing an in-game
`/place template` pass. This is primarily a coarse voxel-color preview; plaque
block textures are resolved from their shipped models so baked inscriptions can
be checked before an in-game pass. Other blockstate detail such as door facing
or trapdoor open/close still needs an in-game check.

```bash
python3 tools/preview_structure.py src/main/resources/data/myvillage/structure/small_house_001.nbt
python3 tools/preview_structure.py examples/buildings/small_house_01.json   # DSL source form
python3 tools/preview_structure.py --all                                    # every .nbt
python3 tools/generate_town_plan_preview.py --count 6                       # town plan PNG/HTML previews (default covers all 6 wall families)
python3 tools/preview_structure.py --viewer-only src/main/resources/data/myvillage/structure/cultivation_sect_001.nbt
python3 tools/preview_structure.py --no-viewer --all                        # PNGs only
python3 tools/render_structure.py --world run-acceptance/chunky_stage1_world --anchor 0 79 192 --spp 10   # Chunky path-traced survey PNGs from a placed-world target
python3 tools/render_structure.py --world run-acceptance/chunky_stage1_world --anchor 152 179 247 --target 156 181.5 248 --views right left --spp 10   # focused look-at for internal subjects such as a water court
python3 tools/write_visual_acceptance_report.py                             # report representative preview/Chunky visual targets
python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview           # serve public previews for review
```

Outputs land in `out/preview/<stem>/`: `isometric.png` (shaded 3D overview),
`slices_contact.png` plus per-Y `slice_yNN.png` (top-down floor plans), and
`legend.png` / `legend.txt` mapping swatch indices to block ids. The generated
`viewer.html` opens directly from disk and supports orbit/zoom/pan, X/Y/Z
cross-section cuts, Y-layer range sliders, and block-base checkboxes. When a
run emits more than one `viewer.html`, the tool also writes
`out/preview/index.html` as the reviewer entry point, with browser assets copied
under `out/preview/_assets/` so the directory is self-contained for HTTP review.
For acceptance handoff, serve `out/preview/` with a public HTTP server bound to
`0.0.0.0:8765` and report `http://43.156.135.198:8765/index.html` while this
host keeps that public IP, so review starts from an opened preview surface
instead of a file list. Keep the preview server running until the reviewer says
it can be closed, or until the relevant work is being closed.
`tools/write_visual_acceptance_report.py` writes
`reports/visual_acceptance_report.json` and `.md` after preview and Chunky prep,
listing the representative PNGs and in-game Chunky targets that must be opened
before claiming visual verification. Add new block colors to
`tools/block_colors.json`; unknown blocks render magenta and should be reported
there. `--max-px` (default 2048)
auto-reduces static PNG scale so large compounds stay bounded.

The separate headless Chunky path-tracing renderer is not currently a custom
`myvillage:` block acceptance path. It can render placed worlds for ordinary
blocks, but `myvillage:rockery_block` has been observed to render as Chunky's
unknown-block placeholder even with the MyVillage jar supplied through
`-texture`. Review custom block appearance, including `hero_rockery`, in a
Minecraft client until a dedicated renderer compatibility path exists. For
ordinary layout review, `tools/render_structure.py` defaults to
`--view-plan survey`, which renders four mid-height cardinal views plus four
high diagonal views. Use `--view-plan height-sweep` for low/mid/high passes
from each side, or `--view-plan cardinal` / explicit `--views front right back
left` for the old four-view behavior. Use `--target X Y Z` when the focal
subject is internal to a larger structure (for example the mansion 水亭/池面),
so the camera looks at that feature instead of the scanned bbox center.
Multi-view runs also write
`contact_sheet.png` by default; pass `--no-contact-sheet` only for narrow
diagnostics.

## Build The Mod

Requires JDK 21. The build also runs the Python structure generator
(`tools/generate_all_structures.py`); Gradle calls `python` on Windows and
`python3` elsewhere by default. If your environment maps Python differently,
override it with the `PYTHON` environment variable, e.g.
`PYTHON=python3.11 ./gradlew build` (Linux/macOS) or
`set PYTHON=py && gradlew.bat build` (Windows cmd).

```bash
./gradlew build
```

Confirm the jar contains the structure resources (name the versioned jar:
`build/libs/` keeps the jars of earlier versions):

```bash
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/structure"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/painting/inscription"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/entity/simple_fox/simple_fox.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/neoforge/biome_modifier/add_simple_fox_spawns.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/beast/demon_wolf.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/beast/demon_wolf_model.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/beast/demon_wolf_animations.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/entity/demon_wolf/demon_wolf_eyes.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/npc/cultivator_model.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/npc/cultivator_animations.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/entity/cultivator/cultivator.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/rideable_flying_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/rideable_flying_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/qingfeng_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/qingfeng_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/qingfeng_sword_3d.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/qingfeng_sword_model.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/qingfeng_sword_geometry.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/qingfeng_first_person.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/lingxiao_spear.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/lingxiao_spear_3d.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/lingxiao_spear.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/lingxiao_spear_model.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/lingxiao_spear_geometry.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/lingxiao_spear_first_person.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/xuantie_gauntlet.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/xuantie_gauntlet_3d.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet_model.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/xuantie_gauntlet_geometry.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/xuantie_gauntlet_first_person.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/xuanyue_zhenshan_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/xuanyue_zhenshan_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/chilian_lihuo_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/chilian_lihuo_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/qingxiao_liuyun_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/qingxiao_liuyun_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/player_animations/sword_combat.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/player_animations/spear_combat.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/player_animations/fist_combat.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/recipe/qingfeng_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/minecraft/tags/item/swords.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/blockstates/spirit_testing_stele.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/blockstates/technique_inheritance_stele.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/guideme_guides/cultivation.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/guides/myvillage/cultivation/index.md"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/cultivation_handbook.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/manual_core_huang.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/manual_core_tint.png"
```

The expected jar is:

```text
build/libs/myvillage-0.84.3.jar
```

## On-Demand Modal Test Runner (default heavy-test workflow)

Full release/build, acceptance-server, client and two-client tests default to
**Modal** to isolate memory pressure from this host. Local work is editing,
planning and focused lightweight checks. Heavy commands elsewhere in this README
are remote-workspace or advanced references; they are not instructions to start
local Gradle/Minecraft jobs by default. Cloud failure does not automatically
fall back to local heavy execution.

The runner uploads a fixed Git commit, runs a bounded test preset in a Modal
Sandbox, downloads evidence, and terminates the Sandbox. `plan` is local
and allocates no cloud compute; real execution requires your Modal account's
authentication. Credentials stay outside the source archive and test logs.

```bash
python3 -m venv .venv-modal
.venv-modal/bin/python -m pip install -r tools/modal_runner/requirements.txt
# First authentication, or renewal after the token expires:
.venv-modal/bin/modal token new --profile myvillage --expires-in 7d
.venv-modal/bin/python -m tools.modal_runner doctor
# Commit intended source first; final delivery uses one Sandbox:
test_commit=$(git rev-parse HEAD)
.venv-modal/bin/python -m tools.modal_runner plan --preset release-alchemy --ref "$test_commit" --timeout 3600
.venv-modal/bin/python -m tools.modal_runner run --preset release-alchemy --ref "$test_commit" --timeout 3600
# Focused diagnostic retry; not full delivery evidence:
.venv-modal/bin/python -m tools.modal_runner run --preset alchemy-quality --cases medicine --ref "$test_commit" --timeout 1800
```

`release-alchemy` runs the full release gate once, clean stage1, then all alchemy
cases in one immutable Sandbox workspace. No feature-specific preliminary Java
selection is added to generic `release`. `alchemy-quality --cases` accepts `all`
(default) or unique comma-separated `brewing,multiplayer,medicine,persistence,ui`.
Each case prepares its own prerequisites. Unknown/empty/duplicate selections fail
before cloud allocation; the combined preset rejects `--cases` so final coverage
cannot silently shrink. Reports list selected, completed and unrun cases, with
`coverage=partial` and `full_acceptance=false` for subsets. A failed assertion
stops the run; use a new run directory for a targeted retry and retain the failure.
UI and persistence share one restart when both are selected. Heavy work stays on
Modal; clean stage1 is retained. See [current workflow evidence](docs/testing/2026-10-16-acceptance-workflow.md).

`HEAD` means the last commit: uncommitted code is not tested. The exact root PAL
jar and `exmod/mod_block_catalog.json` are the only extra local dependencies;
each frozen input and the independent runner bootstrap have separate hashes.
This does not upload the rest of `exmod` or any other mod jars. Generic untracked files, local worlds
and credentials are excluded. The optional SDK is pinned to 1.6.1.

Presets are `smoke` (environment), `python` (tool tests), `build` (Gradle tests
and jar), `release` (the complete `tools/release_gate.py`), `server` (stage1),
`client` (sword-qi capture), and `alchemy-quality` (clean stage1 followed by
`tools/alchemy_quality_evidence.py`: mapped brewing/use, restart, bilingual
layouts and a second isolated client for attribution). Historical verified scope
is listed below; client capture
uses no DevBridge. The timeout defaults to 1800 seconds,
with an allowed range of 180–7200. This bounds Sandbox lifetime after creation;
image preparation before creation is outside that timeout, so it is not a
whole-run billing cap. Automatic retries are zero. The SDK resource
request is CPU `(4, 4)` and memory `(16384, 16384)` MiB (16 GiB request/hard limit);
observed resources belong in the result, not in assumptions about performance.

Only the dedicated Gradle dependency cache persists. Source, worlds and test
state remain ephemeral. One local lock serializes launches; every completion
or failure attempts termination, with the Sandbox timeout covering a crashed
local controller. Recover an uncertain remote state using its recorded run:

```bash
.venv-modal/bin/python -m tools.modal_runner status --run reports/modal-runs/<run-id>
.venv-modal/bin/python -m tools.modal_runner terminate --run reports/modal-runs/<run-id>
```

A terminated Sandbox can report entrypoint return code `137` because cleanup
kills its `sleep` process; use the recorded remote test exit code and confirmed
termination state to assess the run. These are distinct results.

`reports/modal-runs/<run-id>/` records source/executor identity, input hashes,
phase durations, logs, termination status and downloaded artifacts. Local
contract tests and offline planning do not establish cloud startup or cleanup.

The following results are **historical 0.46.1 evidence**, not checks of the
current source. Current `release` and `alchemy-quality` results remain pending
until their fetched artifacts, source commit and confirmed cleanup are verified.

| Modal runner acceptance surface | Historical result |
|---|---|
| Local archive/lifecycle tests and offline plan | `pass` (15 local tests; latest timed command 0.380 s) |
| Authenticated cloud smoke and confirmed termination | `pass` (second attempt 17.626 s; Java/Python dependencies/software GL, downloaded evidence and confirmed cleanup; initial hash-mismatch failure retained) |
| Remote Gradle/build preset | `pass` (913 tests, 0 failures/errors/skips; fresh 0.46.1 jar fetched; 190.077 s total, 165.328 s Gradle command) |
| Remote Python preset | `pass` (1053 tests; command 137.520 s, runner total 156.217 s; downloaded evidence and confirmed termination) |
| Remote bounded stage-1 server | `pass` (commands/save/stop report; 116.757 s command, 143.870 s runner total; confirmed termination) |
| Remote client capture and artifact collection | `pass` for mapped-input `tap_hit` / `charged_hit` (129.723 s command, 150.015 s total; videos/logs/manifest fetched; termination confirmed). Other client scenarios remain `not_verified`. |
| Owner physical-client look, sound, feel and FPS | Separate from this runner and from its cloud checks |

Details: [Modal test runner](docs/ai-kb/46_modal_test_runner.md).
Measured attempts: [runner verification record](docs/testing/2026-10-14-modal-test-runner.md).

## Versioning And Changelog

Maintain `CHANGELOG.md` whenever a version is prepared or a validated fix is
accepted. The authoritative version increment and synchronized-file rule lives
in [docs/WORKFLOW.md](docs/WORKFLOW.md); apply that rule rather than
duplicating its mechanics here.

`tools/bump_version.py` applies the rule. It rewrites `mod_version` in
`gradle.properties`, the `[[mods]]` version in `neoforge.mods.toml`, and every
README jar name of the current version, and inserts an empty `## <version>`
heading above the newest CHANGELOG entry. It refuses a malformed or
non-increasing version and a tree whose four places already disagree. Other
mentions of the old version (prose, older CHANGELOG entries) are left alone.

```bash
python3 tools/bump_version.py 0.30.0 --dry-run   # print the plan, write nothing
python3 tools/bump_version.py 0.30.0
```

For an actual mod release, write the CHANGELOG entry by hand. Use the full
release gate only when complete release validation is requested:

```bash
python3 tools/release_gate.py                 # every step, with a fresh ./gradlew build
python3 tools/release_gate.py --list          # step names, in order
python3 tools/release_gate.py --skip-build    # no Gradle build and no jar steps
python3 tools/release_gate.py --only 'validate-cultivation-*'
```

It prints one line per step and a summary, and exits non-zero if a step
failed. The steps: the four version places agree and the newest CHANGELOG
entry has a body; the generator `--check`s (including `python3 -m tools.beastgen build demon_wolf
--check` and `python3 -m tools.npcgen build cultivator --check`); the structure generator, then the validators
from [Manual Acceptance Prep](#manual-acceptance-prep); `tools/tests`; the
offline preview's numpy tests (solver parity, `sweep`/`diff` rendering) under
`$MC_PREVIEW_PYTHON` or `.venv-preview` (skipped when neither exists); and `./gradlew build` under the shared heavy-work lock (`$MC_HEAVY_LOCK`, else
`.mc-heavy.lock` beside the main checkout). Python steps use `/usr/bin/python3`
because it has PyYAML. The gate deletes the current version's jar before
building, so the combat, spirit-stone, and GuideME jar checks and the README
jar listing (each `jar tf ... | grep` pattern above must match an entry) read a
jar written by that run. It fails if regenerating changed a file under
`src/main/resources`. It starts no Minecraft client or server:
`runAcceptanceServer`, Chunky stages, previews, and real-client checks stay
manual. Per-step output is in `reports/release_gate/`.

## Manual Acceptance Prep

Before a staged manual acceptance pass, prepare both the mod artifact and the
command documentation. `python3 tools/release_gate.py` (see
[Versioning And Changelog](#versioning-and-changelog)) runs the generator,
validators, build, and jar listing below in one command; previews, the visual
report, and the preview server are not part of it.

```bash
python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_custom_entities.py
python3 -m tools.beastgen build demon_wolf --check
python3 -m tools.npcgen build cultivator --check
python3 tools/validate_rideable_flying_sword.py
python3 tools/validate_sword_combat_foundation.py
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 tools/validate_spirit_stone_resources.py
python3 tools/validate_cultivation_lifespan.py
python3 tools/validate_cultivation_meditation.py
python3 tools/validate_cultivation_gain.py
python3 tools/validate_cultivation_advancement.py
python3 tools/validate_guideme_cultivation_guide.py    # needs PyYAML in this python3
python3 tools/validate_mod_block_fallbacks.py
python3 tools/validate_plaque_bindings.py
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_compound_library.py --group cultivation_town --count 6
python3 tools/validate_compound_library.py --group cultivation_sect --count 2
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
python3 tools/buildgen/tests/test_huipai_reference_slice.py
python3 tools/buildgen/tests/test_ganlan_stilted_house.py
python3 tools/buildgen/tests/test_pagoda_landmark.py
python3 tools/validate_civic_library.py
python3 tools/validate_town_generation.py
python3 tools/validate_runtime_town_plan.py
python3 tools/check_style_policy.py
python3 tools/check_cultivation_forms.py
python3 tools/validate_region_topology.py
python3 tools/validate_world_sim.py
python3 tools/preview_structure.py --all
python3 tools/generate_town_plan_preview.py --count 6    # default covers all 6 wall families
python3 tools/generate_sect_plan_preview.py --count 6    # default covers all 3 detached-spire variants + absent
python3 tools/generate_region_topology_preview.py --count 6   # offline 洲/域 graph previews
python3 tools/write_visual_acceptance_report.py
python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview
./gradlew build
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/structure"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/mod_block_fallbacks.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/block/plaque"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/painting/inscription"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/entity/simple_fox/simple_fox.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/neoforge/biome_modifier/add_simple_fox_spawns.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/beast/demon_wolf.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/beast/demon_wolf_model.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/beast/demon_wolf_animations.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/entity/demon_wolf/demon_wolf_eyes.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/npc/cultivator_model.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/npc/cultivator_animations.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/entity/cultivator/cultivator.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/rideable_flying_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/rideable_flying_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/qingfeng_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/qingfeng_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/qingfeng_sword_3d.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/qingfeng_sword_model.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/qingfeng_sword_geometry.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/qingfeng_first_person.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/lingxiao_spear.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/lingxiao_spear_3d.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/lingxiao_spear.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/lingxiao_spear_model.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/lingxiao_spear_geometry.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/lingxiao_spear_first_person.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/xuantie_gauntlet.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/xuantie_gauntlet_3d.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet_model.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/xuantie_gauntlet_geometry.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/combat/xuantie_gauntlet_first_person.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/combat/"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/xuanyue_zhenshan_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/xuanyue_zhenshan_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/chilian_lihuo_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/chilian_lihuo_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/qingxiao_liuyun_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/qingxiao_liuyun_sword.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/player_animations/sword_combat.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/player_animations/spear_combat.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/player_animations/fist_combat.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/recipe/qingfeng_sword.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/minecraft/tags/item/swords.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/blockstates/spirit_testing_stele.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/blockstates/technique_inheritance_stele.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/textures/item/low_grade_spirit_stone.png"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/worldgen/configured_feature/spirit_stone_ore.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "data/myvillage/myvillage/realm/qi_refining.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/guideme_guides/cultivation.json"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/guides/myvillage/cultivation"
jar tf build/libs/myvillage-0.84.3.jar | grep "assets/myvillage/models/item/cultivation_handbook.json"
```

Use the command list below as the acceptance script. Update this README,
`AGENTS.md`, and the affected technical contracts whenever commands or acceptance
prep steps change.

## Run Client

```bash
./gradlew runClient
```

Create or open a flat test world with commands enabled.

## Simple Fox Entity Smoke Test

`myvillage:simple_fox` is the first complete custom-entity slice. It reuses the
vanilla fox model, animation, AI, synchronized state, NBT, and sounds while
keeping an independent entity id, orange texture, spawn egg, loot table, and
low-weight taiga natural spawn entry.

```mcfunction
/summon myvillage:simple_fox ~ ~ ~
/give @s myvillage:simple_fox_spawn_egg
/data get entity @e[type=myvillage:simple_fox,limit=1,sort=nearest]
```

For save/reload, note the summoned fox's UUID from `/data get`, save and exit,
reopen the world, and query the same nearby entity again. Natural-spawn review
must use a recorded seed and taiga-family biome, then note observation time and
group size; a successful codec/server boot does not prove frequency. Inspect
front, both sides, back, three-quarter, idle, walk, sit, sleep, crouch, pounce,
hurt, and death before recording the human visual verdict.

## Realm Statistics and Wilderness (境界数值与野外)

Since 0.47.0, player and beast physical combat share a numerical policy.
Maximum health and attack grow by
`entry_multiplier × major_ratio^major_index × (1 + minor_slope × minor_index)`:
exponential between cultivated major realms, linear within each realm. Mortal
is a separate baseline. The authoritative coefficients, explicit stage aliases
and bounds are in `data/myvillage/stats/rules.json`; inspect the complete table
with the growth command instead of maintaining a second table in this manual.
Existing player advancement gates are unchanged. Speed, armor, percentage
resistance and spiritual-power caps do not receive this blanket multiplier.

Outgoing damage exposes base attack, flat addition, move multiplier, summed
additive bonuses and total multiplier. Vanilla armor/effects/enchantments apply
once, followed by one typed-resistance step before absorption. Equipment is
already included in the attack attribute. Physical and five-element resistance
channels are available; merely declaring an elemental resistance does not give
a weapon elemental damage. Unknown damage sources retain their existing path.
Since 0.48.2, each element has its own resistance penetration value. Values
subtract percentage points from the matching resistance, with a zero floor;
they do not affect armor or other elements. Each new value defaults to zero,
with bounds from `stats/rules.json`. The old `myvillage:resistance_penetration`
ID is now **physical-only**, retaining saved physical bases/modifiers; it no
longer penetrates fire and is not copied into elemental values. Physical and
fire damage sources are currently connected; the other four elemental attack
routes are still unimplemented.

| Channel | Attribute ID |
|---|---|
| Physical | `myvillage:resistance_penetration` (legacy ID, physical-only) |
| Metal | `myvillage:metal_resistance_penetration` |
| Wood | `myvillage:wood_resistance_penetration` |
| Water | `myvillage:water_resistance_penetration` |
| Fire | `myvillage:fire_resistance_penetration` |
| Earth | `myvillage:earth_resistance_penetration` |

Since 0.48.1, armor pressure uses `D_atArmor / defender_realm_factor` while
final armor damage still multiplies the full `D_atArmor`. Armor and toughness
values remain equipment/native attributes; the base armor reduction is capped
at 80%, and other layers can reduce damage further. Missing or invalid defender
assignment uses vanilla scale one. Armor-bypassing sources still bypass; all
other sources reaching vanilla armor use this pressure rule, independently of
typed-resistance classification. Shields, invulnerability-frame differences,
armor wear and the weapon's armor-effectiveness enchantments retain native
ordering. No innate armor or new equipment set is granted.

Attribute refresh does not heal or stack old realm modifiers. Saved modifiers
preserve high current health through a save/restart.

Since 0.63.1, newly spawned ordinary vanilla monsters also use the region's
beast realm range. Natural births, spawners, patrols and other configured
gameplay spawns receive a permanent birth assignment. Crossing regions or
restarting does not reroll it; slime children and hostile conversions inherit
it. Existing unassigned mobs, command/egg spawns, unsupported areas/dimensions
and the excluded dragon/wither/warden/elder guardian retain baseline behavior.
Melee uses the attack attribute; configured projectile, magic and explosion
damage receives its realm multiplier once before normal mitigation. Explosion
radius and potion duration are unchanged. Scope is configured in
`data/myvillage/wilderness/vanilla_monsters.json`; ranges remain in the existing
`wilderness/rules.json`. Region tier does not dynamically change these ranges.
Inspect a nearby zombie with
`/myvillage stats info @e[type=minecraft:zombie,sort=nearest,limit=1]`.

`data/myvillage/wilderness/rules.json` gives each region an inclusive realm and
stage range for beasts and rogue cultivators. Natural wolves keep their birth
realm when moving or reloading; command/egg wolves retain their old baseline.
Rogues are projections of eligible living people already in the local ledger,
within shared avatar caps and loaded chunks. They keep the existing
invulnerability and do not attack. If no local person fits the range, no rogue
is fabricated. Region queries are overworld-only; no encounter is invented
outside the region graph. Both rule files are bundled startup data, requiring
a rebuild/restart after tuning.

Operator diagnostics (permission level 2; stages here are zero-based):

```mcfunction
/myvillage stats growth
/myvillage stats growth myvillage:qi_refining 0
/myvillage stats info @s
/myvillage stats trace @e[type=myvillage:demon_wolf,sort=nearest,limit=1]
/myvillage stats damage preview 10 0 1 0 1 10 physical 0.5 0.1
/attribute @s myvillage:physical_resistance get
/attribute @s myvillage:fire_resistance_penetration get
/attribute @s myvillage:fire_resistance_penetration base set 0.2
/myvillage wilderness info
/myvillage wilderness info 0 80 0
/myvillage wilderness sample zhongzhou 12345 16
/myvillage wilderness birth @e[type=myvillage:demon_wolf,sort=nearest,limit=1]
/myvillage wilderness spawn_test ~ ~ ~ 12345
```

`sample` is read-only and repeatable. `spawn_test` is an administrator fixture
using the regional assignment policy, explicitly distinguished from a natural
spawn; it does not prove encounter frequency. `stats info` shows base/effective
attributes and modifier identities, amounts and operations. `stats damage preview`
accepts base, flat, move, additive bonus, total multiplier, post-vanilla damage,
channel, resistance and penetration; it changes no entity. `stats trace`
separates requested damage, actual armor input/defender scale/pressure, armor-stage
output, vanilla mitigation, resistance, absorption and health loss. A skipped armor
stage has no invented armor input. Generic
damage bonus and resistance attributes can also be inspected with vanilla
`/attribute`; these commands are debugging tools, not player progression.
For a loaded chunk, `wilderness info` also reports the actual biome monster
table, difficulty, light levels and a fixed-seed placement-rule probe without
loading chunks or consuming the world's random source.

Validation and developer evidence:

```bash
/usr/bin/python3 tools/validate_realm_stats.py
/usr/bin/python3 -m unittest tools.tests.test_validate_realm_stats
./gradlew test --tests 'com.example.myvillage.stats.*' --tests 'com.example.myvillage.wilderness.*'
python3 tools/run_chunky_acceptance.py --stage 1
python3 tools/realm_stats_evidence.py
# Focused armor verification (separate capture session):
python3 tools/realm_armor_evidence.py
# Independent elemental penetration and save/restart:
python3 tools/elemental_penetration_evidence.py
/usr/bin/python3 tools/validate_realm_stats.py --jar build/libs/myvillage-0.84.3.jar
```

Run clean stage 1 before the evidence script: it clears `run-acceptance`.
The evidence script owns its capture sessions and reuses its isolated world
only for save/restart verification; do not run another Gradle/Minecraft job
concurrently. It explicitly enables natural monsters for the bounded birth
trial (ordinary capture sessions keep them disabled; manual sessions can opt
in with `session start --natural-monsters`). `out/preview/realm_stats/evidence.json` retains commands, checks,
failures and separate phase timings. Full gameplay/entity/cultivation/world-sim
regressions and the jar build remain required by `AGENTS.md`.
The natural-spawn trial explicitly sets normal difficulty and midnight;
administrator fixtures and ordinary-world encounter frequency are separate.

`realm_armor_evidence.py` records its isolated session under
`out/preview/realm_armor/`. Its explicit 30 armor / 12 toughness fixture combines
vanilla equipment with an administrator attribute offset; it is not a new set.
The checks cover same/cross-realm pressure, heavy hits, invalid assignments,
Breach, Protection, resistance, absorption, durability, shields, invulnerability
frames, mapped melee and mapped sword qi. See the
[0.48.1 verification report](docs/testing/2026-10-15-realm-armor.md).

| 0.48.1 developer verification | Result |
|---|---|
| Pure/runtime tests, validator mutations and packaged hook | Pass; 984 Java tests, 40 focused validator cases, 56 release checks and jar inspection |
| Clean acceptance server and mapped-client armor checks | Pass; 27/27 checks in the first capture attempt |
| Owner combat feel and multiplayer compatibility | `not_verified` |

`elemental_penetration_evidence.py` uses explicit attribute fixtures and mapped
melee/sword-qi input, then restarts its isolated world to read back all six
independent values and modifiers. It leaves a command/trace/timing record under
`out/preview/elemental_penetration/`. Run clean stage 1 before starting it; do
not clean the world between its two sessions. The five new attributes do not
by themselves make elemental equipment or attacks available.

| 0.48.2 developer verification | Result |
|---|---|
| Channel mapping/isolation tests, language and jar checks | Pass; 990 Java tests, 54 focused validator cases, 56 release checks and jar inspection |
| Physical/fire runtime isolation and independent save/restart | Pass; 27/27 checks, including mapped melee/sword qi and direct restart |
| Metal/wood/water/earth attacks | Not implemented |
| Owner combat feel, UI and multiplayer | `not_verified` |

See the [0.48.2 verification report](docs/testing/2026-10-15-elemental-penetration.md).

| Owner acceptance surface | Result |
|---|---|
| Major-realm gap and minor-stage combat feel | `not_verified` |
| Natural regional encounter frequency and difficulty | `not_verified` |
| Rogue placement and readability in ordinary terrain | `not_verified` |
| Two-player shared rogue projections and combat | `not_verified` |

See [the numerical and wilderness contract](docs/ai-kb/47_realm_stats_wilderness.md)
and [the measured verification report](docs/testing/2026-10-14-realm-stats.md).

## Demon Wolf (妖狼)

`myvillage:demon_wolf` is the first hostile beast (0.33.0): a wolf about 1.3
blocks at the shoulder with its own model, keyframe clips, and AI. It hunts
the nearest player it can see and fights with two moves: a close-range bite
(撕咬) with a short telegraph and a long recovery, and a mid-range pounce (扑击)
with a deep crouch, an aim that locks on where the target stood, a leap, and a
skidding landing. The moves are built so that stepping sideways avoids the
bite and stepping away after the pounce locks makes it miss (the ledger below
records what has been observed). Since 0.47.0 wolves can spawn naturally in
eligible overworld regions with a saved birth realm. There is still no drop.
Summon it or use the spawn egg for the unscaled baseline (in `myvillage:main`
after the simple fox egg).
It needs a difficulty above Peaceful, where vanilla removes every monster.

```mcfunction
/summon myvillage:demon_wolf ~ ~ ~
/give @s myvillage:demon_wolf_spawn_egg
```

During part of each move (its immune window) a combat hit still damages the
wolf and freezes it for the hit-stop but neither stuns nor pushes it; outside
that window it flinches like any mob, its move is cancelled, and that move
comes back after a short cooldown. The bite resists from its first tick
through its last active tick, so its recovery is the punish window; the pounce
can be interrupted early in its wind-up and resists from mid wind-up through
its last active tick. While a move shows, the client draws the wolf on the
server's tick-by-tick path (each position update applied in one step, the
move clip two ticks behind the newest synced move tick); that keeps the
pounce's landing on the ground, and uneven packet arrival can show as a
slight per-tick judder during a move. Every move
number (timing, ranges, cooldowns, weights, damage, hit boxes, lunges,
knockback, immune windows) and the attributes live only in
`src/main/resources/data/myvillage/beast/demon_wolf.json`; the server runs from
it and the animation generator reads it, so a retune is a data edit plus a
rebuild. The model, clips, texture, and glow layer are generated by
`tools/beastgen`; never edit them by hand. How the framework works and how to
add a second beast: `docs/ai-kb/38_hostile_beasts.md`.

Operator debug commands (permission 2) for checks and stills:

```mcfunction
/myvillage beast move @e[type=myvillage:demon_wolf,limit=1,sort=nearest] myvillage:demon_wolf_pounce
/myvillage beast status @e[type=myvillage:demon_wolf]
/myvillage beast debug on
/myvillage beast debug off
```

`move` starts the named move on the next AI step, ignoring range, cooldown,
and gap (aimed at the current target, or straight ahead without one);
`status` prints each selected beast's move, move tick, phase, whether it
resists stagger, its aim, cooldowns, and target distance; `debug on` writes
`BEAST_DEBUG` lines for move starts, ends, staggers, and hits to the server
log. With `/tick freeze` and `/tick step` the client shows the pose of the
server's exact move tick.

Generator, offline previews, and headless in-game evidence:

```bash
python3 -m tools.beastgen build demon_wolf            # rewrite the four generated files
python3 -m tools.beastgen build demon_wolf --check    # fail when they differ (release gate)
python3 -m tools.beastgen preview demon_wolf          # out/preview/demon_wolf/ (numpy/Pillow via .venv-preview)
python3 -m tools.combat_capture beast                 # out/preview/demon_wolf/ingame/
python3 -m tools.combat_capture beast --parts fight,dodge   # re-run some parts
```

`preview` (sheets, GIFs, and for the pounce `clip_pounce_arc.png` and
`clip_pounce_landing.png`, with the beast moved along the server's path) is
described in `tools/beastgen/README.md`. The capture has six parts: `idle`
(four sides and a scale still), `moves` (exact-tick stills of every move, plus
landing stills for a move that leaves the ground), `locomotion` (walk and run
videos), `fight` (60 s against a survival player with the Qingfeng sword, no
armour or effects, natural regeneration off, with a per-beast table of moves,
hits, damage, and staggers inside and outside immune windows), `dodge` (slowed
trials: standing still against strafing for the bite, one early swing against
every move), and `slowmo` (every move at a slowed tick rate, server ticks
polled against video time). `tools/combat_capture/README.md` has the details.
It uses the same host programs, session, and heavy-work lock as the weapon
captures; its output is developer evidence and records no owner verdict.

Automated checks:

```bash
python3 tools/validate_custom_entities.py
/usr/bin/python3 -m unittest tools.tests.test_validate_custom_entities tools.tests.test_beastgen tools.tests.test_beast_capture
./gradlew test
./gradlew build
```

Developer evidence from the headless capture on the build host (not owner
acceptance): offline previews in `out/preview/demon_wolf/`, in-game stills,
videos, `fight_log.txt`, and `manifest.json` in
`out/preview/demon_wolf/ingame/`. In the 60 s fight, 35 player hits landed
inside an immune window and staggered nothing; of 36 outside, 20 staggered the
wolf and 17 of those cancelled a move. A player who stood still and traded
took the pounce and two bites per wolf before killing it, about 14 of 20
health on Normal, with no deaths. Strafing during the bite's wind-up, from its
start or from the turn lock, avoided the bite. Stepping away from a pounce
after its lock was not measured. The pounce's landing hung about 0.6 blocks in
the air before the one-step position update
(`out/preview/demon_wolf/ingame_lerp_before/`).

Nothing below has been observed by the owner on a physical client.

| Demon Wolf (0.33.0) real-client acceptance surface | Result |
|---|---|
| `/summon` and the spawn egg create the wolf; egg place in the creative tab; English and Chinese names | `not_verified` |
| Size beside the player, model, texture, and the glowing eyes, mane edges, and tail tip by day and at night | `not_verified` |
| Idle, walk, and run clips; feet do not slide while walking or running | `not_verified` |
| Bite and pounce telegraphs read early enough to react; sideways movement avoids the bite; stepping away after the lock makes the pounce miss | `not_verified` |
| How the fight feels with the Qingfeng sword and the Lingxiao spear: immune windows, stagger flinch, recovery as the punish window | `not_verified` |
| Bite and pounce damage, armour and shield, and knockback on the player | `not_verified` |
| Sounds: ambient and wind-up growls, hurt, death, and steps at the lowered pitch (the build host has no audio device) | `not_verified` |
| Death and no drop | `not_verified` |
| Save and reload keep the wolf; it does not despawn by distance | `not_verified` |
| `F3+T` reload picks up regenerated model, animation, and texture files | `not_verified` |
| Multiplayer: a second client sees the same moves, clips, and staggers in sync | `not_verified` |
| Moves on a real network: the pounce lands on the ground, and the per-tick judder from uneven packets is acceptable | `not_verified` |
| `/myvillage beast move`, `status`, and `debug` from a real client | `not_verified` |
| Frame rate with several wolves on a real GPU | `not_verified` |
| Owner verdict on the wolf's look and its fight | `not_verified` |

## Cultivator NPC (修仙者)

`myvillage:cultivator` is the first humanoid NPC (0.34.0): a sect disciple
about 1.9 blocks tall in a pale robe under a long sleeveless indigo vest, hair
tied in a bun under a small crown. It has no disposition yet: it stands,
strolls, and looks at nearby players, and never attacks; friend or foe is a
later decision. It appears in any difficulty, does not despawn by distance,
and cannot be leashed. There is no natural spawning and no drop: summon it or
use the spawn egg (in `myvillage:main` after the demon wolf egg). Since 0.35.0 the
world simulation also projects ledger members as cultivator avatars on a
built sect compound; since 0.47.0 it also projects eligible local rogues in
the wilderness (see [World Simulation](#world-simulation-世界模拟--命簿) and
[Realm Statistics and Wilderness](#realm-statistics-and-wilderness-境界数值与野外)).

```mcfunction
/summon myvillage:cultivator ~ ~ ~
/give @s myvillage:cultivator_spawn_egg
```

It does not use the player model or a skin. Its model is authored at half the
vanilla unit (the model file's `scale` is 0.5, so 32 texels per block) and is
built from layers that really stand apart: a crossed collar on the chest, a
vest open down the front with border bands and sloped shoulder caps, a belt
with a buckle, sash ends and a jade pendant, sleeves that deepen to an open
cuff with a hanging drape, a two-tier skirt under the vest's panels, and a
cut-out hair shell with a bun, crown, pin, ribbons, and loose strands. The
head is one cube from chin to crown (since 0.44.1; the stepped jaw of
0.34.1 is gone), and the face is drawn the way anime-style Minecraft skins
draw theirs (0.44.1, after a study of 119 of them): the fringe covers the
top half of the face in strands and the side hair runs down to the chin,
the eyes sit in the lower half (an iris two columns wide shading dark to
light downward, a white column outside it, a lash line on top), no brow, no
nose, a blush texel, a small pink mouth, flat pale skin.
The texture is painted per texel from its place on the model (rounded form light,
shadows cast by the layer above, folds, dye toward cuffs and hem, woven bands,
trim). The model, clips, and texture are generated by `tools/npcgen`; never
edit them by hand. How the framework works, how to add a second NPC or a
recolour, and what a friend or foe design would build on:
`docs/ai-kb/39_humanoid_npcs.md`.

Generator, offline previews, and headless in-game evidence:

```bash
python3 -m tools.npcgen build cultivator            # rewrite the three generated files
python3 -m tools.npcgen build cultivator --check    # fail when they differ (release gate)
python3 -m tools.npcgen preview cultivator          # out/preview/cultivator/ (numpy/Pillow via .venv-preview)
python3 -m tools.npcgen preview cultivator --only face   # face.png only: the head from six angles, large and at in-game size
python3 -m tools.combat_capture npc                 # out/preview/cultivator/ingame/
python3 -m tools.combat_capture npc --parts idle    # stills only
```

`preview` writes a six-view turnaround, close-ups, a face sheet, a scale
image beside the vanilla player, the atlas, walk and idle sheets, and GIFs
(`tools/npcgen/README.md`). It draws without back-face culling, as the game
does for NPCs, so the inside of the hair shell shows through its cut-outs. The capture has two parts: `idle` (stills from four
sides with the world frozen, close-ups of the face, collar, belt, sleeve, hem,
and back panel, and a scale still beside the player) and `walk` (two NPCs
strolling in a barrier pen, filmed from the front and from the end). It uses
the same host programs, session, and heavy-work lock as the other captures;
its output is developer evidence and records no owner verdict.

Automated checks:

```bash
python3 tools/validate_custom_entities.py
/usr/bin/python3 -m unittest tools.tests.test_validate_custom_entities tools.tests.test_npcgen tools.tests.test_npc_capture
./gradlew test
./gradlew build
```

Developer evidence from the build host (not owner acceptance): offline
previews in `out/preview/cultivator/` and in-game stills and walk videos in
`out/preview/cultivator/ingame/`. In the headless capture the cultivator drew
at the intended size beside the player, the vest opening and the hair
cut-outs showed the layers under them as in the offline preview, and the NPCs
strolled with the walk clip.

The owner observed 0.34.0 on their own PC on 2026-10-06:
"刚刚的修仙者验证90%通过了。服饰、背身、背后看那些姿势、走路的动作都没有问题。唯一的问题是那个脸有点丑，他给我的感觉有点像大佛的脸，没有修仙者那种俊朗英气。"
The rows below record that verdict. 0.34.1 changes only
the head (face geometry, hair cut-outs round the face, face paint); its face
has not been observed, and every surface the owner did not speak to stays
`not_verified`.

| Cultivator NPC real-client acceptance surface (0.34.0 owner verdict; 0.34.1 face; 0.44.1 二次元 face) | Result |
|---|---|
| `/summon` and the spawn egg create the cultivator; egg place in the creative tab; English and Chinese names | `not_verified` |
| Clothes (服饰) and the back view (背身) on 0.34.0 | `pass` (owner, 2026-10-06, own PC: "服饰、背身……都没有问题") |
| Poses seen from behind (背后看那些姿势) on 0.34.0 | `pass` (owner, 2026-10-06, own PC: "背后看那些姿势……都没有问题") |
| Size beside the player; the model reads as layered from the front, sides, and three-quarter views | `not_verified` |
| 0.34.0 face reads as a handsome, spirited cultivator | `fail` (owner, 2026-10-06, own PC: "那个脸有点丑……有点像大佛的脸，没有修仙者那种俊朗英气") |
| 0.34.1 face (tapering jaw, open forehead, brow on the eye, no mark) up close and at ten blocks | `superseded` (owner, 2026-10-07, own PC, on 0.44.0: "感觉修仙者建模还是不好看，可能脸部偏二次元的会好看一些"; redrawn in 0.44.1) |
| 0.44.1 face after the anime skins (one head cube framed by fringe and side hair, gradient eyes low on the face with the white outside, no brow, no nose, blush, small mouth) up close and at ten blocks | `not_verified` (a first 二次元 draft with a brow, highlight and dot nose was rejected by the owner from the offline previews on 2026-10-07: "不行还是不好看，你去照着仿照一些皮肤看看"; the skin-style face on the stepped jaw was rejected from the PC stills the same day: "脸型有点诡异了") |
| Hair and head ornaments up close and at ten blocks | `not_verified` |
| Texture by day, at night, and indoors: shading, folds, dye, woven bands, and trim | `not_verified` |
| No flicker where layers overlap (collar, vest opening, belt, panels) up close and at a distance | `not_verified` |
| Idle: breathing, hair, ribbons, sash, and pendant | `not_verified` |
| Walk motion (走路的动作) on 0.34.0; 0.34.1 does not change the clips | `pass` (owner, 2026-10-06, own PC: "走路的动作都没有问题") |
| Walk details: feet do not slide, the skirt does not come through the vest panels, hair and ribbons trail | `not_verified` |
| Head follows a nearby player | `not_verified` |
| It never attacks or retaliates; hurt and death look right; no drop | `not_verified` |
| Save and reload keep it; it does not despawn by distance; it stays in Peaceful | `not_verified` |
| `F3+T` reload picks up regenerated model, animation, and texture files | `not_verified` |
| Multiplayer: a second client sees the same NPC and motion | `not_verified` |
| Frame rate with several cultivators on a real GPU | `not_verified` |
| Owner verdict on the 0.34.1 cultivator's look as a whole | `not_verified` |

### Looks (0.41.0)

The one entity type `myvillage:cultivator` now wears one of three looks
(`docs/female-cultivator-brief.md`), each its own model, clips, and texture
on the same body bones and hitbox:

| Look | Who | Design |
|---|---|---|
| `default` | the male disciple described above | clothes unchanged; since 0.44.1 the skin-style face (fringe in strands, blue gradient eyes) |
| `f_novice` | 女·刚入门, a woman just through the door | low ponytail tied with a ribbon, a plain waist-high ru skirt (襦裙) under a half-sleeved jacket (半臂); since 0.44.1 the skin-style face (fringe in strands, warm amber gradient eyes, a blush) |
| `f_adept` | 女·小有所成, a woman who has made her name | high bun with a 步摇 hairpin, a wide-sleeved coat, a 披帛 drape, a two-layer skirt; palette A (crimson-purple coat, moon-white skirt, gold); since 0.44.1 the skin-style face (fringe parted in the middle, violet gradient eyes, rouge) |

```mcfunction
/summon myvillage:cultivator ~ ~ ~ {Look:"f_novice"}
/summon myvillage:cultivator ~ ~ ~ {Look:"f_adept"}
```

The look is synced to clients and saved as `Look` only when it is not
`default`; a name the type does not list is ignored. A spawn egg picks one
of the three at random. A world-ledger avatar wears the look of its person:
a woman at or below Qi Refining `f_novice`, above it `f_adept`, a man
`default`.

Each look is one `tools/npcgen` definition (`defs/cultivator.py`,
`defs/cultivator_f_novice.py`, `defs/cultivator_f_adept.py`) writing
`npc/cultivator[_<look>]_model.json`, `..._animations.json`, and
`textures/entity/cultivator/cultivator[_<look>].png`; the contract's
`looks:` list names them and `tools/validate_custom_entities.py` checks each.

```bash
python3 -m tools.npcgen build cultivator_f_novice [--check]
python3 -m tools.npcgen build cultivator_f_adept [--check]
python3 -m tools.npcgen preview cultivator_f_novice [--only face]   # out/preview/cultivator_f_novice/
python3 -m tools.npcgen preview cultivator_f_adept [--only face]    # out/preview/cultivator_f_adept/
python3 -m tools.combat_capture npc --look f_novice                 # out/preview/cultivator/ingame_f_novice/
python3 -m tools.combat_capture npc --look f_adept                  # out/preview/cultivator/ingame_f_adept/
python3 tools/npc_looks_page.py                                     # out/preview/cultivator/looks/index.html
```

The comparison page sets the three looks side by side: offline turnaround,
face sheet, close-ups and walk GIFs, then the headless stills from four
sides, close-ups, and walk videos (a missing file shows as 未采集).

| Cultivator looks (0.41.0) real-client acceptance surface | Result |
|---|---|
| `f_novice` look: reads as a woman and a novice, plain and old-style, from the front, sides, and three-quarter views | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_novice` face up close and at ten blocks (0.44.1 二次元 face) | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_novice` walk and idle (hip sway, folded hands, ponytail and skirt ties trailing) | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_novice` name tag height on an avatar | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` look: reads as a woman of standing, richer than the novice, from the front, sides, and three-quarter views | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` face up close and at ten blocks (0.44.1 二次元 face) | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` walk and idle (drape, hairpin pendant, back hair trailing) | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` name tag height on an avatar | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| Spawn egg random look; `/summon` with `Look`; look kept across save and reload | `not_verified` |
| Avatars wear the look of their person's gender and realm | `not_verified` |
| Owner verdict on palette A, the low ponytail, and both faces | `not_verified` (the 0.41.0 faces were redrawn 二次元 in 0.44.1 after the owner's 2026-10-07 remark; see the cultivator ledger above) |

## World Simulation (世界模拟 / 命簿)

Since 0.35.0 every world keeps a ledger (命簿) of sects and named cultivators
who cultivate, break through or fail, travel the regions, find fortunes
(奇遇), make friends and enemies, take revenge, wage sect wars, found and lose
sects, and die, one sim day per cultivation-calendar day, whether or not a
player is near. The ledger is the only authority; avatars (化身) and sect
compounds (山门) are its projections. A sect's gate coordinate is fixed inside
its region when it is founded, whether or not a compound has been built there.

The ledger is created once per world, at the first server start with this
version (old worlds included), after the region runtime: genesis with the
world seed and the configured tier, then a prehistory of
`time.prehistory_years` (in `rules.json`), so the world already has lineages,
grudges, and old sects. Dates are 启元 N 年 (启元前 N
年 for the prehistory); a fresh world shows the same year as the cultivation
calendar. The ledger advances whenever the calendar does, which is on every
tick with at least one player online in any game mode, so it also runs while
testing in creative. If the region runtime or the world-sim data fails to
load, or the save cannot be read, the ledger is inactive for the session, the
commands say why, and the save is left untouched.

Commands (permission 2):

```mcfunction
/myvillage world                 # same as `world info`
/myvillage world info            # era date, sim and calendar day, pending days, paused?, population, realms, sects, foremost people
/myvillage world sects [all]     # active sects (all: with the destroyed): region, master, members, top realm, prestige, gate
/myvillage world sect <id|name>  # one sect: founding, master, resources, prestige, signature technique, heritage (传承), gate, relations, members at the sect
/myvillage world sect <id> build        # build the sect's compound (山门) at its ledger gate
/myvillage world sect <id> build here   # move the sect's gate to you first, then build at your feet
/myvillage world sect <id> join <player>    # 0.41.0: admin join, skipping the admission rules
/myvillage world sect <id> leave <player>   # 0.41.0: the player leaves that sect (penalty and cooldown)
/myvillage world player <player>            # 0.41.0: a player's sect record and standings
/myvillage world gates [retry]              # 0.41.0: framed gate builder state; retry gates given up
/myvillage world sect <id> rank <player> <outer|inner|elder>   # 0.42.0: set a member's rank
/myvillage world sect <id> shelves [place]   # 0.42.0: scripture shelves of a built compound; place them again
/myvillage world sect <a> war <b>             # 0.44.0: admin: sect a declares war on sect b now
/myvillage world sect <id> destroy            # 0.44.0: admin: the sect is destroyed now
/myvillage world person <name>   # up to five people whose name or Daoist title contains the text, living first
/myvillage world chronicle [1-50]   # the latest notable and major events (default 10)
/myvillage world here            # your region: tier, qi, danger, sects seated there with gate distance, people present, recent events
/myvillage world pause           # stop settlement (saved); the calendar keeps running
/myvillage world resume          # restart settlement; the paused days are not caught up
/myvillage world advance <1-3650>   # settle that many days now, even while paused
```

Major events (importance 3) also reach every online player as chat rumors
(江湖传闻), players in the event's region first; notable ones (importance 2)
reach only the players in that region. Each player gets at most
`rumors_per_minute`; the rest wait in a short queue.

In game, every player (no permission needed) can read the ledger on the H
panel's 天下 page (0.36.0; see [Cultivation Playable Loop](#cultivation-playable-loop)):
the overview, the sect list and each sect, a person search and each person,
the chronicle, and the region you stand in, with rows that open the sect or
person they name. It shows what `info`, `sects`, `sect`, `person`,
`chronicle`, and `here` show, without the admin actions. The client asks the
server one bounded, read-only query at a time (the same query at most every
2.5 s; the server answers a player at most once every 4 ticks) and gets back a
capped snapshot with names already resolved; the payload protocol (`10` since
0.37.0) must match, so the client and server need the same jar. Lists are capped (96 sects, 24 members,
10 search matches, 10 people present, 40 chronicle lines, 10 recent events).

Server config `config/myvillage-world_sim-server.toml` (a copy in a world's
`serverconfig/` overrides it):

| Key | Default | Meaning |
|---|---|---|
| `world_sim.tier` | `small` | `small` (about 80 people), `medium` (about 300), or `large` (about 1000); read only at genesis, then fixed in the save |
| `world_sim.catch_up_cap_days` | `30` | Most sim days that may be pending at once (one is settled per tick); `rules.json` `scheduler.max_pending_days` is a ceiling this can only lower |
| `rumors.rumors_enabled` | `true` | Rumors in chat on or off |
| `rumors.rumors_per_minute` | `2` | Most rumors per player per real minute |
| `rumors.rumor_queue_cap` | `6` | Most rumors waiting per player; the oldest is dropped |
| `avatars.avatars_enabled` | `true` | Avatars on or off |
| `avatars.avatar_spawn_radius` | `64` | Avatars appear while a player is within this many blocks (8–128) of a built compound's site, and are withdrawn beyond it plus 32 |
| `avatars.max_avatars_per_sect` | `12` | Most avatars per compound (0–64): the master first, then the elders, then by realm |
| `avatars.max_avatars` | `40` | Most avatars in the world (0–256); the compound nearest a player is served first |
| `avatars.auto_realize_gates` | `true` | 0.44.0: build an unbuilt sect gate automatically, in chunk clips, when a player comes near (see [Player sect entry](#player-sect-entry-0410)); an area where players have lived or built is left alone either way; `world sect <id> build` works regardless |

Every balance number, name, technique, artifact, site, beast, and fortune is
data in `src/main/resources/data/myvillage/world_sim/` (`rules.json`,
`realms.json`, `encounters.json`, `names.json`, `techniques.json`,
`lore.json`, and since 0.37.0 `heritages.json`; `techniques.json` and
`heritages.json` are generated by `tools/gen_technique_catalogue.py`), and every sentence is a language key in both language files.
How it works: `docs/ai-kb/40_world_sim.md`.

Offline chronicle (no Gradle, no Minecraft; the pure core is compiled with
`javac`) and the report pages:

```bash
/usr/bin/python3 tools/world_sim_cli.py run --seed 1 --tier small --years 300 \
    --out out/preview/world_sim/seed1.json --text out/preview/world_sim/seed1.txt
/usr/bin/python3 tools/world_sim_report.py --runs small:1,2,3 medium:1,2,3 --years 300
```

The report writes `out/preview/world_sim/<tier>_<seed>.html` and
`index.html`: population and realm charts, a sect timeline, biographies, the
chronicle, and the roll of the dead. Public preview:
`http://43.156.135.198:8766/world_sim/index.html`.

Checks and headless evidence:

```bash
/usr/bin/python3 tools/validate_world_sim.py
/usr/bin/python3 -m unittest tools.tests.test_validate_world_sim tools.tests.test_world_sim_report
./gradlew test --tests 'com.example.myvillage.sim.*'
python3 tools/world_sim_evidence.py --part all    # out/preview/world_sim/evidence/ (holds the heavy-work lock itself)
```

The evidence script runs a headless server and client in a fresh superflat
world with a sped-up calendar (creative mode, every command over RCON, pause
and resume, rumors and their rate limit) and then restarts a kept world
(ledger kept, genesis once, a later tier change ignored). Its 2026-10-06 run
passed all 17 checks (`summary.md`); that is developer evidence, not owner
acceptance.

### Sect compounds and avatars (化身)

A ledger sect has no compound in the world until one is built for it (worldgen
does not place compounds at ledger gates yet; the randomly scattered
`myvillage:sect` compounds are unrelated to the ledger and stay empty).
`/myvillage world sect <id> build` loads the gate's chunk and builds the
worldgen-style compound (derived mountain, terraces, buildings) on the surface
at the ledger's gate coordinate; `build here` first moves the sect's gate (and
its seat) to your position, which must be inside a region, and builds at your
feet. The compound's seed and spire variant come from the world seed and the
sect id, so a rebuild at the same place is identical. The build runs on the
server thread and the server does not respond until it is done (the reply
warns; it can take a minute). The gate is then marked realized, and the build
is recorded in `data/myvillage_world_sim_gates.dat`.

While a player is within `avatar_spawn_radius` of a built compound, the
sect's members who are at the sect in the ledger stand on its open courtyard
ground as cultivators named 姓名 · 境界 · 宗门 (the realm in the client's
language): the master first, then the elders, then by realm, up to the caps,
one entity per person, at least two blocks apart. They look around and at
players but do not walk, cannot be hurt (only damage that bypasses
invulnerability, such as `/kill` or the void, reaches them), do not burn, cannot be pushed, and do nothing when used (since 0.41.0 the gate
steward and the elders talk; see [Player sect entry](#player-sect-entry-0410)). They are
never saved: when every player has moved away they are withdrawn, and on the
way back new ones are projected from the ledger. After each settled day the
set follows the ledger (people who left, died, or went travelling disappear;
arrivals appear). A cultivator from `/summon` or the spawn egg is unchanged.

```bash
python3 tools/world_sim_avatar_evidence.py    # out/preview/world_sim/avatars/ (holds the heavy-work lock itself)
```

It builds one sect's compound with `build here` in a fresh superflat world
and checks the avatars on the courtyard, their withdrawal 300 blocks away
(with the chunks force-loaded), their return, `advance 60`, and damage on an
avatar and on a summoned cultivator. Its 2026-10-06 run passed all 19 checks
(`index.html`, screenshots); developer evidence, not owner acceptance.

| World simulation (0.35.0) acceptance surface | Result |
|---|---|
| Pure-core tests (determinism, save/load, scheduler, purity, lifespan agreement, text keys, health bands, performance) | `pass` (Gradle) |
| Every `/myvillage world` command on a headless server | `pass` |
| Calendar and ledger both advance in creative mode | `pass` |
| Pause holds the ledger while the calendar runs; resume does not catch up | `pass` |
| Restart keeps the ledger; genesis once; later config tier change ignored | `pass` |
| `advance` bounds | `pass` |
| Rumors reach an online player, rate-limited, own region first | `pass` (headless; own-region priority unit-tested only) |
| Chinese chronicle/rumor text on a zh_cn client | `not_verified` |
| Sim inactive when the region runtime failed to load | `not_verified` |
| Newer-format save left untouched | `not_verified` in game (unit-tested) |
| Unreadable save left untouched | `not_verified` |
| Genesis on an existing old world | `not_verified` in game |
| How rumors look in chat on a physical client | `not_verified` |
| Named avatars with realm stand on the courtyard ground of a built compound | `pass` (headless) |
| Avatar count near the compound, zero far away, back on return | `pass` |
| Avatars follow `advance` (left and arrived members) | `pass` |
| No duplicate avatars | `pass` |
| Avatars invulnerable; a `/summon` cultivator unchanged | `pass` |
| Avatar look, motion, and name-tag legibility on a physical client | `not_verified` |
| Chinese avatar name tags on a zh_cn client | `not_verified` |
| Avatars in multiplayer | `not_verified` |
| `sect <id> build` without `here` on real terrain far away (only `build here` ran; a long synchronous build may trip a production server's max-tick-time watchdog) | `not_verified` |
| The gate record surviving a restart in game (save round trip unit-tested only) | `not_verified` |

The 天下 page was checked on the owner's PC on 2026-10-06 (real GPU, Chinese
client, singleplayer world `agent-test`, GUI 534x300 at scale 3, then 480x270,
427x240, and 320x240 by resizing the window; stills in
`out/preview/world_sim_panel/pc/`). That is developer evidence, not the
owner's verdict.

| 天下 page (0.36.0) surface | Result |
|---|---|
| Query/snapshot codec, snapshot builder per kind and caps, server throttle, page readouts | `pass` (Gradle) |
| 天下 tab opens with ledger data in Chinese: 总览, 宗门, 人物, 纪事, 此地 | `pass` (owner's PC) |
| 人物 search updates as you type | `pass` (owner's PC) |
| 纪事 newest first with cause lines | `pass` (owner's PC) |
| Drilling into a sect and a person, and back | `pass` (owner's PC) |
| Hover highlight on clickable rows | `pass` (owner's PC) |
| One-column cards and the two-row dock below 300 wide (GUI 320x240); layout at 534x300, 480x270, 427x240 | `pass` (owner's PC, Chinese) |
| English text at narrow widths | `not_verified` |
| The ledger-inactive card on a real client | `not_verified` (unit-tested) |
| The outside-every-region card on 此地 | `not_verified` |
| `H` typed into the search field does not close the panel | `not_verified` |
| Multiplayer: several players querying, per-player throttle | `not_verified` (throttle unit-tested) |
| Hover, click, and wheel feel on a physical mouse | `not_verified` |
| The owner's verdict on the page's look and amount of information | `not_verified` |

### Player sect entry (0.41.0)

Slice 1 of 拜入宗门 (`docs/player-sect-entry-brief.md`): the player gets a
record in the ledger and can join a sect at its gate, leave it, and rise by
the yearly review. The player is never a ledger person: they do not count
as a sect's people, resources, or prestige, and the engine never moves them.
The scripture hall (slice 2) followed in 0.42.0 ([Scripture hall](#scripture-hall-0420)),
sect tasks and masters (slice 3) in 0.43.0
([Sect tasks and apprenticeship](#sect-tasks-and-apprenticeship-0430)), and
the world's response (slice 4) in 0.44.0 ([World response](#world-response-0440)).

A walk through it:

1. On the H panel's 天下 page, 此地 lists the sects seated in your region
   with the distance to each gate and its bearing (北, 东北, ...).
2. Walk toward a gate. When you come within `gates.realize_radius` blocks
   (planar) of an active sect whose gate is not built, the compound is built
   for you, a few chunks per tick, one compound at a time (nearest first),
   while avatars are enabled and `avatars.auto_realize_gates` is on. Its
   chunks load in the background and it rests on the ground the chunk
   generator made (so a retry after a broken-off attempt does not stack on
   the leftovers); chunks not loaded within 1200 ticks fail the build.
   Players within that radius read "…的山门正在显形……" and then
   "…的山门已然落成。" The build goes on if you walk away. Since 0.44.0 it
   never levels what players made: if any chunk of the area has had a player
   nearby for more than `gates.inhabited_ticks_max` ticks, holds a block
   entity of another mod, or more than `gates.player_block_entities_max`
   vanilla block entities that worldgen does not place itself, the gate is
   skipped ("…的山门未在此显形：此地已有人居住营造。") until `world gates
   retry` or a restart. `world sect <id> build` still works and is refused
   while the same gate is being built this way.
3. Within `avatar_spawn_radius` the avatars appear as before. The gate
   steward (守山执事) is the member at the sect of the lowest rank (outer,
   inner, elder, master) and then the lowest id; it is always shown first,
   stands on the lowest terrace on the free cell nearest the axis and the
   gate opening, and its name tag has a fourth part: 姓名 · 境界 · 宗门 · 守山执事.
4. Right-click the steward (within `steward.interact_range`). The dialogue
   screen shows a greeting, the sect's prestige, people at the gate and
   master, and either an invitation (拜入 / 告辞) or the reason you are
   turned away. Elders and the master also talk (a greeting; to a
   non-member, a pointer to the steward) but take no requests. Other
   avatars still do nothing when used.
5. 拜入: you become an outer disciple (外门弟子), with a chat line, a
   chronicle line ("…拜入…，为外门弟子。"), and a 我的宗门 card on the 天下
   overview (sect, rank, joining date, master, contribution, standing). Your
   own sect's page shows your rank, joining date, and standing.
6. At the start of each sim year your rank rises one step (inner, then
   elder; never master) if your realm meets the `promotion` bar, with a chat
   and chronicle line. Negative standings recover toward 0. If your sect is
   destroyed you become a rogue cultivator without a penalty.
7. To leave, talk to the steward again and pick 退宗. Your standing with
   that sect drops by `leave.standing_penalty`, and you cannot rejoin it for
   `leave.rejoin_years` nor while that standing is below
   `leave.rejoin_standing_at_least`. Other sects are open to you.

Admission is checked in this order, and the dialogue states the first
refusal: `sect_inactive`, `already_member` / `member_elsewhere`,
`rejoin_cooldown`, `standing_too_low`, `not_awakened`, `realm_too_low`,
`selective`. The server decides everything: the client sends only the chosen
option (JOIN, LEAVE, FAREWELL) with the avatar's entity id and the sect id,
and the server checks the level, the distance, the avatar's role and its
sect again before changing the ledger. Payload protocol is `13` (`14` since
0.42.0, `15` since 0.43.0), so client and server need the same jar. Ledger saves gain the optional
`player_members` (payload version 3); older saves load with no players.

The numbers are data, the `player` section of
`src/main/resources/data/myvillage/world_sim/rules.json`:

| Key | Meaning |
|---|---|
| `admission.require_awakened_root` | A spiritual root must be awakened to join |
| `admission.min_realm`, `admission.min_stage` | Lowest realm (player registry path; `mortal` is below every ledger realm) and 0-based stage to join |
| `admission.join_standing` | Standing (交情) gained with the sect on joining |
| `admission.selective.prestige_at_least` | Sects at or above this prestige are selective |
| `admission.selective.root_peak_bp_at_least`, `admission.selective.or_min_realm` | What a selective sect wants: a highest single-element root affinity of at least this many basis points, or at least this realm |
| `leave.standing_penalty` | Standing change on leaving |
| `leave.rejoin_years` | Years before the same sect takes you back |
| `leave.rejoin_standing_at_least` | Least standing a sect needs to take you |
| `leave.standing_recovery_per_year` | Yearly recovery of each negative standing toward 0 |
| `promotion.inner`, `promotion.elder` | Realm, 0-based stage, and contribution for the yearly rise to inner disciple and to elder |
| `scripture_hall.borrow_cost_by_grade` | Read, not used until the scripture hall (slice 2) |
| `steward.interact_range` | Blocks within which a steward or elder will talk |
| `gates.realize_radius` | Planar blocks from a player at which an unbuilt gate is built |
| `gates.clips_per_tick` | Chunk clips built per server tick |
| `gates.inhabited_ticks_max` | A build-area chunk with players nearby for longer than this (ticks) keeps the automatic build away |
| `gates.player_block_entities_max` | A build-area chunk with more vanilla block entities than this (worldgen's own not counted), or any other mod's, keeps the automatic build away |

Commands (permission 2):

| Command | Does |
|---|---|
| `/myvillage world sect <id> join <player>` | Join the player to the sect without the admission rules (the sect must be active and not already theirs; a member elsewhere leaves that sect first, without penalty or event) |
| `/myvillage world sect <id> leave <player>` | The player leaves that sect, exactly as through the steward (penalty and cooldown) |
| `/myvillage world player <player>` | The player's record: sect and rank, joining date, master, contribution, standing with each sect, the sect they last left |
| `/myvillage world gates` | The framed builder's state: `idle`, `building sect <id> clip <i>/<n>`, and the gates given up this session |
| `/myvillage world gates retry` | Let the gates given up this session be tried again |

Log lines (INFO; the evidence script reads them):

```text
GATE_REALIZE sect=<id> state=queued gate=<x> <z> distance=<n> chunks=<n>
GATE_REALIZE sect=<id> state=started anchor=<x> <y> <z> clips=<n> seed=<n> variant=<v> load_ticks=<n> preloaded=<a>/<b>
GATE_REALIZE sect=<id> state=clip <i>/<n>
GATE_REALIZE sect=<id> state=done seconds=<s> clips=<n> blocks~=<n> written=<n>
GATE_REALIZE sect=<id> state=cancelled reason=<server_stopping|ledger_changed|sect_changed|already_realized>
GATE_REALIZE sect=<id> state=skipped reason=<inhabited|player_blocks> chunk=<cx> <cz>
GATE_REALIZE sect=<id> state=failed reason=<chunks_not_loaded|exception class>
SECT_DIALOGUE option=<JOIN|LEAVE|FAREWELL> x=<px> y=<px> w=<px> h=<px>
SECT_ENTRY player=<name> intent=<JOIN|LEAVE> sect=<id> result=<ok|reason>
```

`SECT_DIALOGUE` is the client's, one line per button in screen pixels each
time the dialogue lays out; the other two are the server's. A failed or
skipped build releases its chunks, leaves the gate unbuilt (blocks already
placed stay; only `world sect <id> build` replaces a half-built site), and
is not retried until `world gates retry` or a restart. Details:
`docs/ai-kb/43_player_sect_entry.md`.

Headless evidence (fresh superflat world, creative, settlement paused; about
15 to 25 minutes; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_entry_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600] [--skip-promotion]
```

It walks the player 140 blocks from an unbuilt gate and waits for
`GATE_REALIZE ... state=done`, finds the steward, right-clicks it and clicks
JOIN, reads `world player` and the chronicle, photographs the 我的宗门 card,
advances a year below and at the promotion bar, leaves and tries to rejoin,
and runs the admin join and leave. Output in `out/preview/world_sim/entry/`
(`index.html`, `evidence.json`, `commands.txt`, `server_log.txt`,
`client_log.txt`, screenshots). Developer evidence, not owner acceptance.

Its 2026-10-07 run (capture script of `225c5b3`) passed all 20 checks: the
gate 140 blocks away built itself in 3.9 s (135 chunk clips) while `tick
query` read 7.1 ms per tick; the steward stood with its 守山执事 tag; JOIN
through the dialogue made the player an outer disciple of 玄黄阁 with a
chronicle line and the 我的宗门 card; a mortal was not promoted after a year,
and at 炼气五层 the next yearly review made them an inner disciple; LEAVE
succeeded and, within the cooldown, the steward offered no JOIN button; the
admin join went past the cooldown and the admin leave worked. Not captured:
bearings, `world gates`, promotion to elder, a destroyed sect.

| Player sect entry (0.41.0) acceptance surface | Result | Notes |
|---|---|---|
| Player-build guard: the automatic build skips an area where players have lived or built (0.44.0) | `not_verified` | headless: not captured (the captures built only on untouched superflat ground; unit-tested in `GateRealizePlanTest`) |
| Auto gate realization near a player (framed, no server stall) | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `gate_realized_automatically`: player 140 blocks from the gate, `GATE_REALIZE` started→done 3.9 s, 135 chunk clips; `server_responsive_while_building`: `tick query` 7.1 ms/tick during the build; `gate_realized_in_ledger`: `world sect` shows (built); `gate_far.png`. Stutter on a real client not judged |
| Steward name tag (four parts, 守山执事) and its place by the gate | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `steward_present` (name tag with the 守山执事 part), `player_faces_steward`; `steward_nameplate.png` |
| Dialogue screen: lines, buttons, refresh in place, closing | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `dialogue_opens_with_join` (options JOIN, FAREWELL), `dialogue_offers_leave` for a member; `dialogue_open.png`, `dialogue_member.png`; the welcome and farewell pages after JOIN and LEAVE (`dialogue_welcome.png`, `dialogue_left.png`). Look and readability on a physical client not judged |
| Join through the steward (outer disciple, chat and chronicle line) | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `join_ok` (`SECT_ENTRY ... intent=JOIN ... result=ok`), `player_record_outer` (`world player`: outer disciple of 玄黄阁, standing +20), `chronicle_has_join`; `dialogue_welcome.png` |
| Leave through the steward (standing penalty) | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `leave_ok` (`intent=LEAVE result=ok`); `dialogue_left.png` |
| Rejoin cooldown refusal | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `rejoin_refused_cooldown`: within the cooldown the dialogue offers only FAREWELL (no JOIN button, so no `result=rejoin_cooldown` line; the refusal line is in `dialogue_rejoin_refused.png`) |
| Admin commands `world sect <id> join/leave <player>`, `world player`, `world gates` | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `admin_join_forced` (join past the cooldown, `result=ok`), `admin_join_record`, `admin_leave`; `world player` read throughout. `world gates [retry]` not exercised |
| 我的宗门 card and the own-sect rank row on the 天下 page | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `panel_after_join.png` shows the 我的宗门 card with 玄黄阁 / 外门弟子 (screenshot only, no log check); the own-sect row not captured |
| Gate bearings on 此地 | `not_verified` | headless: not captured (unit-tested only, `BearingTest`) |
| Yearly promotion to inner disciple and elder | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `no_promotion_below_threshold` (a mortal stays outer after a year), `promoted_inner_at_threshold` (realm set to 炼气五层, one day advanced to refresh the snapshot, then a year: `player.promote.inner`, `world player` shows inner; that world has 6 days a year). Elder not captured |
| Sect destroyed → the player becomes a rogue | `not_verified` | headless: not captured (unit-tested only, `PlayerAffairsTest`) |

### Scripture hall (0.42.0)

Slice 2 of 拜入宗门: a sect's members borrow its manuals (秘籍) at the
scripture hall (藏经阁), by rank, one copy of each, free for now.

A walk through it:

1. Every ledger compound built from 0.42.0 on, by `world sect <id> build` or
   automatically near a player, gets a scripture shelf (经架,
   `myvillage:scripture_shelf`) on the hall floor of each of its two
   scripture pavilions. Since 0.46.2 it is a two-block-high custom reading
   stand; either half opens the same hall and only its lower root owns the
   sect. Compounds built earlier get them, or repair their old one-cell
   shelves when the upper space is clear, with `world sect <id> shelves place`.
   Occupied sites are skipped. Random worldgen compounds have none.
2. Join the sect at its gate (see [Player sect entry](#player-sect-entry-0410)),
   walk into a pavilion, and use the shelf with an empty hand (within
   `steward.interact_range`).
3. The hall lists what your rank may borrow: a sect with a heritage (传承)
   lends its chain, the first technique to an outer disciple, the first two
   to an inner disciple, the whole chain to an elder; a sect without one
   lends its basic technique, and to inner disciples and elders also its
   signature technique. Mortal-grade breathing has no manual; the hall says
   to take it from the inheritance stele.
4. 借阅 gives you the manual (dropped at your feet if your inventory is
   full) and a chat line, records the borrow in the ledger (a chronicle
   line "…自…藏经阁借得《…》。"), and greys the row out as 已借. Each
   technique can be borrowed once; a lost manual can be replaced only by
   the admin `manual` command. Read it as any manual (盘坐研读).
5. A rise in rank (the yearly review, or `world sect <id> rank`) opens more
   of the chain. Anyone who is not of the sect sees only the refusal; an
   unowned shelf (placed by hand from the creative tab) says "此架无主".

The client sends only the shelf position and the technique id; the server
checks the level, the distance, the shelf's owner, the sect, the membership,
the list, and the manual before it records the borrow. Payload protocol is
`14` (`15` since 0.43.0), so client and server need the same jar. The cost per grade is
`player.scripture_hall.borrow_cost_by_grade` in
`src/main/resources/data/myvillage/world_sim/rules.json`; it is shown when
above 0 but nothing is charged yet (contribution, earned by sect tasks since 0.43.0, is not spent).

Find, repair and inspect the 0.46.2 stand:

```mcfunction
/myvillage world sects
/myvillage world sect <id> shelves
/myvillage world sect <id> shelves place
/give @s myvillage:scripture_shelf
```

`shelves` reports the lower-root coordinates. `shelves place` requires a built
ledger compound and upgrades old one-cell roots when both cells are available.
Hand placement makes an unowned stand for model checks; it cannot lend manuals.
Either half can be used. Breaking either half in survival removes the pair
and drops one item; creative drops none.

Generate and verify its authored resources (never edit the outputs):

```bash
python3 tools/gen_scripture_shelf_model.py
python3 tools/gen_scripture_shelf_model.py --check
/usr/bin/python3 tools/validate_scripture_shelf.py
.venv-preview/bin/python tools/gen_scripture_shelf_model.py --preview
```

The normal generation/check path uses Python's standard library; optional
offline PNG previews use `.venv-preview/`. The generator owns the blockstates,
lower/upper block models, item model and custom atlas. Release gate enrollment
is `gen-scripture-shelf-model-check`; `validate-scripture-shelf` verifies the
eight half/facing variants, two-cell geometry, custom assets and lower-only
single-item loot. Offline rendering checks shape and assets;
physical-client appearance and reload remain separate owner checks.

Commands (permission 2):

| Command | Does |
|---|---|
| `/myvillage world sect <id> rank <player> <outer\|inner\|elder>` | Set a member's rank; a rise records a promotion and tells the player |
| `/myvillage world sect <id> shelves` | The shelf sites of the sect's built compound and the shelf found at each, with its sect id |
| `/myvillage world sect <id> shelves place` | Place (or re-own) the shelves of a compound built before 0.42.0 |

Log lines (INFO):

```text
SCRIPTURE_SHELF sect=<id> placed=<n>/<m> at=<x y z;...>
SCRIPTURE_HALL player=<name> intent=OPEN sect=<id> member=<true|false> entries=<n>
SCRIPTURE_HALL player=<name> intent=BORROW sect=<id> technique=<id> result=<ok|reason>
SCRIPTURE_HALL_UI technique=<id> borrowed=<true|false> x=<px> y=<px> w=<px> h=<px>
```

The first three are the server's; `SCRIPTURE_HALL_UI` is the client's, one
line per borrow button in screen pixels each time the hall lays out. Borrow
refusals: `not_member`, `member_elsewhere`, `not_borrowable`,
`already_borrowed`, `no_manual`, `inactive`, `too_far`, `unowned`. Details:
`docs/ai-kb/43_player_sect_entry.md` ("Scripture hall").

Headless evidence (fresh superflat world; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_scripture_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600] [--shelf-distance 3.0]
```

It builds a heritage sect's gate by walking near it, checks that both
shelves were placed, joins by admin command, opens the hall as an outer
disciple, borrows the first manual and finds it in the inventory, opens the
hall again (the button disabled), ranks the player inner (a second
technique), and leaves (refused as a non-member). Output in
`out/preview/world_sim/scripture/` (`index.html`, `evidence.json`,
`commands.txt`, `server_log.txt`, `client_log.txt`, screenshots). Developer
evidence, not owner acceptance.

Its 2026-10-07 run (capture script of `23d8ecb`, on the 0.42.0 tree) passed
all 19 checks with 明心宗 (heritage 太白剑脉): both shelves placed, the outer
disciple saw and borrowed `gengjin_yinqi_fa` and held its manual, the
reopened hall showed it borrowed with one copy in the inventory, `rank inner`
added `gengjin_jianjue` (borrowed too), and after `leave` the hall answered
`member=false`. On the same code `world_sim_entry_evidence.py` passed 20/20
and `world_sim_avatar_evidence.py` 19/19.

| Scripture hall (0.42.0) acceptance surface | Result | Notes |
|---|---|---|
| A shelf on the hall floor of both scripture pavilions of a built compound | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `shelves_placed` 2/2 at (553, -35, -805) and (581, -35, -805) after the gate of 明心宗 built itself (started→done 3.3 s, 135 clips, 9.7 ms/tick); `shelves_listed_for_sect` (2 of 2). How the shelf looks in the pavilion not judged |
| Outer disciple's list (first technique of the chain, or the basic technique) | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `hall_opens_for_outer`: one entry, `gengjin_yinqi_fa` (first of 太白剑脉); `hall_outer.png` |
| Borrowing gives the manual into the inventory, chat and chronicle line | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `borrow_ok` (`intent=BORROW ... result=ok`), `manual_in_inventory` (`manual_core_huang` with that technique). Chat and chronicle line not machine-checked |
| A borrowed technique cannot be borrowed again (button disabled, `already_borrowed`) | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `borrowed_button_disabled` (`borrowed=true` on reopening), `no_second_copy` (one manual); `hall_borrowed.png`. The button is not clicked, so no `already_borrowed` line (unit-tested) |
| Inner disciple sees the second technique | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `rank_inner`, `hall_inner_sees_two` (`gengjin_yinqi_fa`, `gengjin_jianjue`), `borrow_second_ok`, `second_manual_in_inventory` (`manual_active_xuan`); `hall_inner.png` |
| A non-member is refused | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): after `admin_leave_ok`, `hall_refuses_non_member` (`member=false entries=0`); `hall_refused.png` |
| Admin commands `world sect <id> rank` and `shelves [place]` | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `rank_inner` and `shelves` pass; `shelves place` not exercised |

### Sect tasks and apprenticeship (0.43.0)

Slice 3 of 拜入宗门: the gate steward hands each member one sect task (宗门事务)
a year for contribution, and an inner disciple can take an elder as master
(拜师), whose guidance speeds meditation.

A walk through it:

1. As a member, talk to your sect's steward. Below the greeting it names
   this year's task and its brief, with a 领事务 button. Which task you get
   is fixed for the whole year (drawn from your player id and the year).
2. Do it. 巡山除兽 (patrol): kill beasts while standing in your sect's home
   region; each kill counts (a chat line with the progress). 供奉灵石
   (tribute): have the low-grade spirit stones in your inventory. 传信他宗
   (courier): walk onto the compound site of the sect the brief names (the
   check runs every second; that gate must be built to see anything there).
3. Back at the steward the line says the task is ready, with 交事务. Turning
   it in adds its contribution (a chat line and a chronicle line); a
   tribute's stones are taken then. While a task is not done the steward
   shows the progress instead. After turning in, the steward says there is
   nothing more this year; a new year brings a new offer. An unfinished
   task does not expire at the new year.
4. Once you are an inner disciple or above and have no master, an elder or
   the sect master standing at the gate offers 拜师. The ledger records the
   master (`world player`, the 我的宗门 card, a chronicle line).
5. While your master lives and stays in your sect, meditation progress is
   multiplied by `1 + cultivation.master_guidance` (`rules.json`). If the
   master dies or leaves, you are told that day and the bond ends.
6. The 天下 page's 我的宗门 card shows the open task and its progress.

Tasks are data in `src/main/resources/data/myvillage/world_sim/sect_tasks.json`:

| Field | Meaning |
|---|---|
| `id` | Task id; its name and brief are `world_sim.task.<id>.name` / `.brief` in both language files |
| `kind` | `patrol` (beast kills in the sect's region), `tribute` (low-grade spirit stones handed in), or `courier` (reach another active sect's compound) |
| `count` | Kills, stones, or 1 for a courier |
| `contribution` | Contribution gained on turning it in |

Dialogue options added: 领事务 (`TASK_ACCEPT`) and 交事务 (`TASK_TURN_IN`) at
the steward, 拜师 (`APPRENTICE`) at an elder; as before the client sends only
the option, the avatar, and the sect, and the server judges it. Refusals:
`task_active`, `task_done_this_year`, `no_task`, `not_ready`,
`tribute_short` (stones missing) for tasks; `rank_too_low`, `has_master`,
`master_not_here` for apprenticeship. Payload protocol is `15`, so client
and server need the same jar.

Log lines (server, INFO):

```text
SECT_TASK player=<name> kind=<patrol|courier> progress=<p>/<n>
SECT_ENTRY player=<name> intent=<TASK_ACCEPT|TASK_TURN_IN|APPRENTICE> sect=<id> result=<ok|reason>
SECT_DIALOGUE option=<...|APPRENTICE|TASK_ACCEPT|TASK_TURN_IN> x=<px> y=<px> w=<px> h=<px>   # client
```

Details: `docs/ai-kb/43_player_sect_entry.md` ("Sect tasks and
apprenticeship").

Headless evidence (fresh superflat world; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_tasks_evidence.py [--sect ID] [--courier-tries 6] [--skip-meditation] [--meditation-seconds 10]
```

It joins a sect by admin command and ranks the player inner, takes the
year's task at the steward and does it whichever kind the draw gives
(summoned wolves killed by the player, stones given, or a walk to another
sect's gate), turns it in, checks that no second task is offered, takes an
elder as master, samples meditation without and with the master, and
photographs the panel. Output in `out/preview/world_sim/tasks/`
(`index.html`, `evidence.json`, `commands.txt`, `server_log.txt`,
`client_log.txt`, screenshots). Developer evidence, not owner acceptance.

Its 2026-10-07 run (script `6806fe7`) passed all 21 checks with 明心宗: the
gate built itself in 3.8 s (135 clips, 6.3 ms per tick); after the admin join
and `rank inner` the steward offered the year's task, which the draw made
the tribute (供奉灵石); with 5 stones given the steward offered 交事务, the
turn-in took the 5 stones and raised contribution from 0 to 10, and the
steward then offered no second task; the elder 侯南枝 offered 拜师, which
recorded the master and a chronicle line. Not captured: the patrol and
courier tasks (not drawn), a master's loss, and the meditation factor (both
samples gained no progress). `world_sim_entry_evidence.py` passed 20/20 on
the same code.

| Sect tasks and apprenticeship (0.43.0) acceptance surface | Result | Notes |
|---|---|---|
| Taking the year's task at the steward (领事务) | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `dialogue_offers_task` (options TASK_ACCEPT, LEAVE, FAREWELL; `task_offer.png`), `task_accepted` (`intent=TASK_ACCEPT result=ok`; chronicle "takes a sect task from 明心宗: Spirit Stone Tribute") |
| Patrol progress from beast kills in the sect's region | `not_verified` | headless: not captured (the year's draw was the tribute; unit-tested only) |
| Tribute: stones taken at turn-in | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `tribute_stones_given` (0 → 5), `dialogue_offers_turn_in` (`task_ready.png`), `tribute_taken` (5 → 0) |
| Courier: arrival at the other sect's compound | `not_verified` | headless: not captured (the year's draw was the tribute; unit-tested only) |
| Turning in adds the contribution (交事务) | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `task_turned_in` (`intent=TASK_TURN_IN result=ok`), `contribution_awarded` (0 → 10); `task_done.png` |
| No second task this year | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `no_second_task_this_year` (options LEAVE, FAREWELL only); `task_again.png` |
| Apprenticeship at an elder (拜师) | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `dialogue_offers_apprentice` (elder 侯南枝; `apprentice_offer.png`), `apprenticed` (`intent=APPRENTICE result=ok`), `master_recorded` (`world player`: Master 侯南枝), `chronicle_has_apprentice` |
| Losing a master who died or left | `not_verified` | headless: not captured (unit-tested only, `PlayerAffairsTest`, `WorldSimTasksTest`) |
| Meditation gain with a master (about ×1.15) | `not_verified` | headless: not captured (both meditation samples gained 0 progress, so they show nothing; unit-tested only) |
| The task row on the 我的宗门 card | `not_verified` | headless: `panel_task.png` shows the card with contribution, master and task rows (screenshot only, no log check) |

### World response (0.44.0)

Slice 4 of 拜入宗门, the last: what happens to your sect reaches you, and
sects you are at odds with turn you away.

- **Sect news.** After every settled day each online player is told, in
  chat and wherever they stand, every notable or major event (importance 2
  or more) about their sect: wars, a new master, a split, a lost heritage,
  the sect's destruction. A line reads "【宗门】" followed by the event's own
  chronicle line. After leaving a sect (or losing it) you still hear the
  sect events of the one you last left, so the day it is destroyed you are
  told. Your own lines (joining, promotion, tasks, a master) still come as
  before. Players who are offline when it happens are not told later.
- **Hostile gates.** A sect at war with yours turns you away at its gate:
  its steward and elders say so and offer only 告辞. Without a war, a
  standing (交情) with that sect below
  `player.admission.hostile_standing_below` (in
  `src/main/resources/data/myvillage/world_sim/rules.json`) does the same.
  A rogue cultivator is never refused for a war, and your own sect never
  turns you away. A JOIN sent to a hostile steward is refused by the server.
- **Admin acts** (permission 2), for testing and evidence; both go through
  the same ledger paths as the yearly politics and tell online players at
  once:

| Command | Does |
|---|---|
| `/myvillage world sect <a> war <b>` | Sect a declares war on sect b now (the chronicle's war line; both at war). Refused: `no_sect`, `same_sect`, `sect_inactive`, `already_at_war` |
| `/myvillage world sect <id> destroy` | The sect is destroyed now (the ruin line), its people become rogues, its players are released ("…覆灭，…自此成了散修。"), a held heritage is lost; its avatars withdraw on the next pass. Refused: `no_sect`, `sect_inactive` |

Log line (server, INFO), one per news line sent:

```text
SECT_NEWS player=<name> event=<id> type=<type> sects=<[ids]>
```

Details, and an assessment of real P4 (compounds placed by worldgen at
ledger gates, not built): `docs/ai-kb/43_player_sect_entry.md` ("World
response", "Real P4 (worldgen placement) assessment").

Headless evidence (fresh superflat world; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_news_evidence.py [--sect-a ID] [--sect-b ID] [--news-timeout 10] [--withdraw-timeout 10]
```

It joins sect A by admin command, stands at sect B's gate far from A, has A
declare war on B and waits for the news line in the client's chat (prefix
【宗门】, `[Sect]` in English), right-clicks B's steward and checks that only
告辞 is offered, then destroys A and checks that `world player` shows a
rogue, the chronicle and the chat line, and A's avatars withdrawing (about
15 to 30 minutes; two gates are built). Output in
`out/preview/world_sim/news/`. Developer evidence, not owner acceptance.

Its 2026-10-07 run (script `c1d8af3`) passed all 15 checks with A = 玄黄阁
and B = 明心宗: both gates built themselves (4.3 s and 3.4 s); the war news
reached the player 4534 blocks from A's gate at once; 明心宗's steward
offered only 告辞 with the at-war refusal; the destruction made the player a
rogue, wrote the ruin line, sent the two chat lines at once, and A's 12
avatars were gone within 6 s. Not captured: an elder's refusal and the
standing bar. `world_sim_tasks_evidence.py` passed 21/21 on the same code.

| World response (0.44.0) acceptance surface | Result | Notes |
|---|---|---|
| News of the player's own sect reaches them anywhere (【宗门】) | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `sect_news_delivered`: right after `war`, with the player 4534 blocks from 玄黄阁's gate (at 明心宗's), the line "[Sect] 金萝 (sect master 玄黄阁) declares war on 明心宗." (en_us client); `SECT_NEWS` lines for `war`, a following `battle`, and `sect_destroyed`; `news_chat.png`, `news_chat_open.png` |
| A hostile (at war) sect's steward turns the player away | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `b_steward_present`, `hostile_steward_refuses` (明心宗's steward 郑湘灵: client options FAREWELL only, no JOIN), `hostile_reason_at_war` (`steward.refuse.at_war`); `hostile_refused.png`. A standing below the hostile bar is not reachable with the shipped numbers and was not captured |
| A hostile sect's elder turns the player away | `not_verified` | headless: not captured (the script talks only to the steward; unit-tested in `SectDialogueScenesTest`) |
| Admin war `world sect <a> war <b>` | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `war_declared` (玄黄阁 declared war on 明心宗) |
| Admin destruction `world sect <id> destroy` | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `sect_destroyed` (玄黄阁), `chronicle_sect_gone` ("玄黄阁 in 灵岳, last held by 金萝, falls apart.") |
| The player becomes a rogue with a chat line when the sect is destroyed | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `player_now_rogue` (`world player`: rogue cultivator, left 玄黄阁), `destroy_news_delivered` (two lines at once: "[Sect] 玄黄阁 … falls apart." and "玄黄阁 is no more; CaptureDev goes on as a rogue cultivator."); `destroy_chat_open.png` |
| A destroyed sect's avatars withdraw | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `avatars_withdrawn_after_destroy` (12 → 0 at 玄黄阁's site within 5.9 s); `destroyed_court.png` |

### Portraits (命簿人物像素头像, 0.45.0)

Every person of the ledger has a 64×64 anime-style pixel bust, composed on
the client from hand-drawn part maps and shown in the sect dialogue (the
speaker at the left, name and role under it) and on 天下 → 人物 (the bust at
the top-left of the person card, a 16×16 thumbnail on every row). The same
record always gives the same face; the face follows the record: robe by realm
tier (炼气 plain / 筑基 dyed / 金丹 brocade with a jade pendant / 元婴 玄色 with
gold), headwear by rank (宗主 玉冠 or 凤钗, 长老 抹额 and 簪 or 玉簪, 内门 发带),
greying from 55 % and white hair with age marks from 80 % of the realm's
lifespan, eye colour by the dominant root element (金 grey-silver, 木 green,
水 blue, 火 amber, 土 brown-gold, 元婴 violet), brows and mouth by the
strongest trait (好斗, 野心, 谨慎, 好游, 忠诚; otherwise calm), a bandage from
injury 20 and a scar from 50, grey when dead, and the sect's accent on
ribbons and bands. The rest (face shape, skin, hair colour and cut, eye
shape) comes from a splitmix64 hash of the person id.

The drawing lives in `tools/portraitgen` (one module per layer, hand-placed
pixels in the style of the owner-approved sample `tools/portraitgen/old/sample.png`);
`python3 -m tools.portraitgen.export` writes the part maps to
`assets/myvillage/portrait/` and the goldens; the Java `PortraitComposer`
composes them exactly as `tools/portraitgen/replay.py` does (pixel-exact
tests both ways). Preview: `out/preview/cultivator/portraits/index.html`
(48 real ledger people, part sheets, a life strip); details in
`docs/ai-kb/44_portraits.md` and `docs/portrait-java-contract.md`.

| Portraits (0.45.0) acceptance surface | Result | Notes |
|---|---|---|
| The speaker's bust in the sect dialogue, name and role under it, the text beside it | `not_verified` | agent stills on the owner's PC 2026-10-07 (`out/preview/cultivator/portraits/pc/dialogue_steward.png`, GUI scale 3): 秦玄览 of 天池楼 with his bust at the left |
| The bust on the 人物 card and the thumbnails on the person rows, at GUI scale 2 and 3 | `not_verified` | stills at scale 3: `world_person.png` (card), `world_search.png` (two rows with thumbnails), `world_sect_detail.png` (在山门者 rows); scale 2 not captured |
| The style itself (二次元, after the approved hand-drawn sample) on real people of the owner's ledger | `not_verified` | the offline sheet and the Artifact were approved as a direction ("大致方向对了"); the stills above show three ledger people |
| Robe, headwear, greying, eye colour and expression match the person's record | `not_verified` | the client log reported `Portrait part maps loaded: 172` and no portrait errors |

### Avatar colours (化身发色瞳色, 0.45.1)

The 3D cultivator avatar of a ledger person now wears the same hair colour and
eye colour as that person's portrait (the owner, 2026-10-07: "我想让建模的发色和
瞳色与像素画的对应"). Hair style, headwear, robe and skin stay as baked into the
look. `tools/npcgen` writes a role map next to each look (`assets/myvillage/npc/<name>_roles.png`:
which texels are hair, at which tone, and which are the iris rows); the server
syncs the person's hair and eye colours on the entity (`NpcEntity.colours()`,
NBT `Colours`; a summoned cultivator has none and keeps the baked colours), and
the client recolours the baked texture once per (look, colours) into a
`DynamicTexture` (`NpcSkins`, `NpcSkinComposer`; goldens pinned from Python,
pixel-exact). A resource reload (`F3+T`) drops the recoloured textures and the
portrait caches. For stills: `/summon myvillage:cultivator ~ ~ ~ {Colours:68}`
(`hair << 4 | eye`; hair 0 ink_violet, 1 ink_blue, 2 dark_brown, 3 chestnut,
4 silver, 5–8 the greying forms; eye 0 metal, 1 wood, 2 water, 3 fire, 4 earth,
5 spirit). Details: `docs/portrait-java-contract.md` (3D avatar colours).

| Avatar colours (0.45.1) acceptance surface | Result | Notes |
|---|---|---|
| An avatar's hair and eyes in the world match its portrait in the dialogue and on the 人物 card | `not_verified` | agent stills on the owner's PC 2026-10-07 (`out/preview/cultivator/skins/pc/`): 秦玄览 (ink_violet/metal, packed 0), 陈薇 (dark_brown/metal, 32), 韩澄江 (silver/water, 66), 刘耕烟 (grey_ink_blue/earth, 100), each beside their dialogue bust; the packed values read back with `/data get entity … Colours` |
| Silver and greying hair and the pale hair colours read right under entity lighting (the portrait ramp is used without darkening) | `not_verified` | stills `row_f_adept_left.png`, `row_f_novice_right.png`, `row_default.png` (summoned rows of all nine hair colours); the darkest colours are about 1.5× the baked near-black hair's luminance |
| Summoned cultivators keep the baked colours; `{Colours:n}` on `/summon` recolours them | `not_verified` | the rows above were summoned with `{Colours:n,Look:"…"}`; the baked-colour case was not photographed |

## Rideable Flying Sword Smoke Test

Give the functional item to the current player:

```mcfunction
/give @s myvillage:rideable_flying_sword
```

Hold it and right-click once to create the sword below the player and mount it.
Right-click again to recall and remove the owned sword; that second use does not
create a replacement. Each player can own one loaded sword and the sword accepts
only its bound owner as its single passenger. Summoning needs enough block-clear
space for the sword's hitbox; a confined placement fails without leaving a sword.

Controls while mounted:

```text
W / S       forward / backward
A / D       left / right
Space       ascend
Shift       descend
```

The client sends one bounded six-key bitset. It does not send coordinates,
rotation, velocity, speed, owner identity, or an entity id. The server derives
the sword from the sender's current vehicle, validates the owner/passenger
relationship, expires stale input, and computes yaw, acceleration, drag, speed
limits, and collision-aware movement.

The entity stores the owner UUID, while the player's persistent data indexes the
current sword UUID. Explicit recall clears that index and discards every loaded
owner match; ordinary removal clears the index only when it still names that
sword. The sword is registered with `noSave()` and is discarded on owner death,
logout, dimension change, or separation beyond 64 blocks; it is not retained
across chunk unload, world reload, or server restart.

Automated preparation:

```bash
python3 tools/validate_rideable_flying_sword.py
./gradlew test
./gradlew build
./gradlew runAcceptanceServer
```

Dedicated-server startup checks payload/entity registration and common/client
separation, not riding quality. In a real client, manually verify all six
controls, Shift descent without dismount, neutral hover and gradual slowdown,
solid-block collision, no fall damage when the mounted sword descends onto the
ground, smooth riding without repeated position/yaw snaps, the blade tip pointing
along the player's horizontal view direction, fall-distance reset,
recall/singleton behavior, every cleanup condition, multiplayer authority, and
item-model scale/readability before recording acceptance.

## Additional Sword Items

Three reference-derived swords are available from `myvillage:main` or through
the following commands:

```mcfunction
/give @s myvillage:xuanyue_zhenshan_sword
/give @s myvillage:chilian_lihuo_sword
/give @s myvillage:qingxiao_liuyun_sword
```

All three currently follow ordinary diamond-sword attack, mining, enchantment,
repair, and durability behavior. They have no recipes, right-click abilities,
elemental effects, custom payloads, or Qingfeng/PAL five-move integration.

<!-- SWORD_COMBAT_FOUNDATION -->
## Qingfeng Sword Combat

This feature requires the exact `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar`
on both client and server. Place it at the repository root for development and
beside MyVillage in the instance `mods/` directory for play. PAL is not bundled
in the MyVillage jar, and the supplied root jar remains untracked.

Obtain the independent functional sword and switch modes with the configurable
`Switch Combat Mode` control (default `R`). Since 0.40.0 `Movement Dodge`
(default Left Alt) dashes with a learned 身法 technique in cultivation mode
(see "Movement Dodge (0.40.0)" below):

```mcfunction
/give @s myvillage:qingfeng_sword
```

In vanilla mode, the Qingfeng Sword follows ordinary diamond-sword attack,
mining, enchantment, repair, and durability behavior. In cultivation mode,
mapped attack input is intercepted only while a registered combat weapon is in
the main hand: this sword, or since 0.29.0 the two-handed Lingxiao Spear (see
below). The client sends an empty attack intent; the server owns the move, timing,
facing, hit shape, targets, damage, durability, steps, and target reaction. Pickaxes,
empty hands, other weapons, open screens, and vanilla mode keep their existing
input paths. An eligible cultivation click also starts one local-only
first-person swing for the predicted move; an authoritative start
corrects its elapsed time, and rejection or stop blends back to the neutral
hold.

Since 0.28.0 the move set is data. Each style file under
`src/main/resources/data/myvillage/combat/style/` holds a combo's timing,
damage, hit shapes, steps, reactions, sounds, hit-stop, and camera cues. Each
weapon file under `.../combat/weapon/` binds an item to a style, a first-person
rig, and a geometry contract, and `.../combat/index.json` lists both. The files
ship in the jar and load at startup on both sides; a datapack cannot override
them and `/reload` does not re-read them. The field reference and the steps to
add a weapon or a move are in `docs/ai-kb/34_combat_data_and_capture.md`.

The first-person swings are data in
`src/main/resources/assets/myvillage/combat/qingfeng_first_person.json`. The
sword swings around one camera-space shoulder pivot: `plane` tilts the swing on
screen, `sweep` turns the arm in that plane, `reach` is pivot-to-grip distance,
and `lead`/`lift`/`twist` aim and turn the blade at the handle (`lift 0` points
the blade up, `-90` points it forward, `twist 90` shows the flat during a cut).
Keys are in server ticks with an `ease` per segment (`linear`, `in`, `out`,
`in_out`, `in_cubic`, `out_cubic`, `in_out_cubic`, `out_back`). They start and
end at the neutral hold. Each move's `strike` window must cover the server
active window within three ticks so the blade crosses the target while the hit
resolves, and an optional `contact` tick anchors the hit-stop. Edit the file and
reload resources (`F3+T`) to see changes; an invalid file is logged and Qingfeng
falls back to the vanilla hold.

A skin and sleeve arm (`FirstPersonArmRenderer`) holds the sword on the
same pivot rig: upper arm, forearm, a bending wrist, and a fist. The handle
crosses the fist, with the guard showing on the thumb side and the pommel below
the little finger. The wrist bend stays within anatomical limits, and the arm
lags a cut slightly and follows through when it stops (presentation only). The
rig's `rig.weapon_scale` and `rig.arm` settings and each key's `grip_roll` (hand
turn about the handle) and `elbow` (elbow swivel) tune it. The arm is drawn
beside the item pass and never cancels it.

The held Qingfeng Sword is a 3D jian model. The inventory icon stays the 2D
sprite (`neoforge:separate_transforms`). The 3D model, its texture, the wrapper
model, and the geometry contract `combat/qingfeng_sword_geometry.json` (format 2:
grip centre, `collar` for the guard, `butt` for the pommel, `head_base` and
`head_tip` for the blade) are all generated. Edit
`tools/gen_qingfeng_sword_model.py` and rerun it instead of editing them:

```bash
python3 tools/gen_qingfeng_sword_model.py            # write the outputs
python3 tools/gen_qingfeng_sword_model.py --check    # fail on drift
python3 tools/gen_qingfeng_sword_model.py --report   # print the derived display and grip fit
```

The first-person grip, the first-person trail, and the world-trail size
read the contract, and the grip undoes the model's own first-person display
transform. A regenerated sword therefore needs no Java change; reload resources
(`F3+T`) to see it.

The third-person full-body poses in `player_animations/sword_combat.json` are
generated. Edit `tools/gen_sword_pal_anims.py` and rerun it instead of editing
the JSON by hand. It keeps the grip centre in the fist while PAL rotates the
held item.

Feedback follows server outcomes only. Each move plays a swing or thrust sound
about one tick before its active start, with slight pitch variation. The
attacker hears it on the local timeline and everyone else hears it from the
server. Only targets that took damage produce feedback:
- the hit sound, plus a heavy impact layer on moves four and five;
- a `myvillage:blade_cut` slash particle at the contact point and a few crit
  sparks (the vanilla sweep particle is gone);
- a per-move hit-stop (`1.5/2/2/3/4` ticks) for the attacker, after which the
  swing catches up so the move still ends on the server total;
- attacker-local camera shake and kicks, scaled by the Screen Effect Scale and
  FOV Effect Scale accessibility options.

Nearby clients also briefly freeze and jitter the struck target from the
`CombatImpactPayload` broadcast. A thin translucent 剑光 ribbon follows the blade
through each strike in first person. Other players, and your own third-person
camera, see a ribbon along the move's server hitbox samples. The five
`myvillage:combat.sword.*` sounds currently alias vanilla sounds in
`assets/myvillage/sounds.json` and have subtitles. None of these visuals or
sounds sends a packet or changes timing, targets, damage, or movement.

The server also decides how the target reacts. Mobs freeze for the hit-stop,
slide back along the attack facing, then stagger for `9-16` ticks (no movement
or melee damage). Repeated stuns fall off, and bosses are exempt. Player targets
are never frozen; they slide and are slowed by 60% for the hitstun. Every move
takes a small forward step, and move five a lunge of up to `1.40` blocks. The
server decides each step's distance, stopping short of a target in front, and
sends it as a motion impulse that the attacker's client moves through with
normal collision, like knockback.

One legal attack input advances each connected move. A click from a move's
active start onward is held in one buffer slot. The held click cancels the rest
of the recovery and starts the next move at its chain tick (`7/8/10/13` for
moves one to four). Without a held click the move plays to its end. Misses may
continue the sequence, while timeout or move five resets it:

```text
1  basic_sword_01_thrust          一式：青锋问路
2  basic_sword_02_horizontal_cut  二式：流云横渡
3  basic_sword_03_rising_cut      三式：燕返撩月
4  basic_sword_04_diagonal_cut    四式：回风落雁
5  basic_sword_05_lunge_thrust    五式：一线穿云
```

### Lingxiao Spear (0.29.0)

`myvillage:lingxiao_spear` (凌霄枪) is a two-handed spear and the second combat
weapon. It is in `myvillage:main` after the four swords and has no recipe:

```mcfunction
/give @s myvillage:lingxiao_spear
```

In vanilla mode it is a diamond-tier sword item with 8 attack damage and 1.2
attack speed. In cultivation mode it runs the `myvillage:basic_spear` style
(`data/myvillage/combat/style/basic_spear.json`), with longer reach and slightly
slower moves than the sword. Moves one to four chain at `8/10/10/14`; the
fifth cannot chain:

```text
1  basic_spear_01_mid_thrust      中平扎    thrust, range 4.2, one target
2  basic_spear_02_sweep           横扫      level sweep left to right, up to four targets
3  basic_spear_03_rising_flick    上挑      right-low to left-high, lifts the target
4  basic_spear_04_overhead_smash  劈枪      left-high to right-low, heavy
5  basic_spear_05_dragon_lunge    游龙突刺  long thrust with a step of up to 1.6 blocks, heavy
```

Both hands hold the shaft in the guard. In third person the leading hand stays
on the shaft for the mid thrust and lets go for the sweep, flick, smash, and
lunge, regripping in the recovery (the third-person arms cannot bend). In first
person a second arm holds the shaft through the thrust, sweep, and flick and
lets go during the smash and lunge; it is drawn only while the off hand is
empty. The model, its textures, the 2D icon, and the geometry contract
`combat/lingxiao_spear_geometry.json` come from
`tools/gen_lingxiao_spear_model.py`; the third-person poses in
`player_animations/spear_combat.json` come from `tools/gen_sword_pal_anims.py`.
Edit the generators, never the outputs:

```bash
python3 tools/gen_lingxiao_spear_model.py            # write the outputs
python3 tools/gen_lingxiao_spear_model.py --check    # fail on drift
python3 tools/gen_lingxiao_spear_model.py --report   # print the derived display and grip fit
```

The first-person rig `combat/lingxiao_spear_first_person.json` is hand-authored
and reloads with `F3+T`. Its `rig.off_hand` block and the per-key
`off_hand_slide`, `off_hand_roll`, `off_hand_elbow`, and `off_hand_hold` fields
are described in `docs/ai-kb/35_lingxiao_spear.md`, with what the spear showed
about the 0.28.0 data infrastructure. A rig, contract, or first-person solver
change also rewrites the first-person parity golden (see the offline preview
commands below).

Geometry contracts are format 2 and use weapon-neutral names (`butt`, `handle`,
`collar`, `head`, `head_base`, `head_tip`, `axes.length`); the rig's scale is
`rig.weapon_scale`. A format 1 contract or a rig that still has `sword_scale`
fails to load with a message naming the new field, and the validator reports it.
The rename table is in `docs/ai-kb/34_combat_data_and_capture.md`. A move may
also give its third-person world trail its own path (`"trail": {"samples": ...}`
in the style file, presentation only; without it the trail follows the hit
samples), and `rig.off_hand` may give the off arm its own `upper_arm`,
`forearm`, and released rest (`rest_direction`, `rest_reach`). No shipped style
or rig uses these yet.

Operators can inspect the server-computed active samples without changing hit
authority:

```mcfunction
/myvillage combat debug on
/myvillage combat debug status
/myvillage combat debug off
/myvillage combat dodge status [player]   # 0.40.0: chosen 身法, cooldown and window left
```

For client-side pose review only, a developer client can play each full-body
PAL curve, or hold one first-person or third-person frame at a server tick,
without generating an attack intent. The move number is one-based within the
style of the weapon in the main hand; with no registered weapon the command
fails:

```text
/myvillage_pal_smoke move 1
/myvillage_pal_smoke first_person 2 5.0
/myvillage_pal_smoke first_person release
/myvillage_pal_smoke third_person 2 5.0
/myvillage_pal_smoke third_person release
```

These local smoke commands prove rendering only. They cannot replace
mapped-click, server authority, damage, timing, or multiplayer acceptance.

Run the focused automated gates before client review:

```bash
python3 tools/validate_sword_combat_foundation.py
python3 -m unittest tools.tests.test_validate_sword_combat_foundation tools.tests.test_combat_data tools.tests.test_combat_style_baseline
python3 tools/gen_sword_pal_anims.py --check
python3 tools/gen_blade_cut_sprite.py --check
python3 tools/gen_qingfeng_sword_model.py --check
python3 tools/gen_lingxiao_spear_model.py --check
python3 tools/gen_xuantie_gauntlet_model.py --check
python3 -m unittest tools.tests.test_gen_sword_pal_anims tools.tests.test_gen_blade_cut_sprite tools.tests.test_gen_qingfeng_sword_model tools.tests.test_gen_lingxiao_spear_model tools.tests.test_gen_xuantie_gauntlet_model
python3 -m unittest tools.tests.test_combat_capture tools.tests.test_combat_preview tools.tests.test_combat_preview_tuning
.venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity tools.tests.test_combat_preview_sweep
python3 tools/validate_mod_items.py
python3 -m unittest tools.tests.test_validate_mod_items
./gradlew test
./gradlew build
./gradlew runAcceptanceServer
```

The validator checks the combat data files and their consistency with the
animations, rigs, translations, and sounds; it holds no per-move numbers.
`tools/tests/test_combat_style_baseline.py` pins the accepted Qingfeng values,
so a deliberate retune updates that test.

To collect presentation evidence on a headless host (stills of every move at
its key ticks in first and third person, a mapped-click combo with video and
server-side target health, and a before/after page), use the capture tool. It
needs `Xvfb`, `xdotool`, `ffmpeg`, and ImageMagick, takes about 6 to 8 minutes
per pass, and writes to `out/preview/combat_capture/<label>/`:

```bash
python3 -m tools.combat_capture run --label <label>
python3 -m tools.combat_capture run --label <label> --weapon myvillage:lingxiao_spear
python3 -m tools.combat_capture combo --label <label> --weapon myvillage:lingxiao_spear --layout sweep --camera back   # in a running session
python3 -m tools.combat_capture compare out/preview/combat_capture/<a> out/preview/combat_capture/<b> --label <a-vs-b>
```

Without `--views`, a weapon shorter than 1.8 blocks is captured in first person
and F5 back and front, and a longer one (the spear) in first person and two
quarter views whose sheets are cropped around the player. `combo --layout
default|sweep|line` places the three targets (one in reach; an arc 2.5 blocks
out; a line ahead), `--tick-rate 5` runs at quarter speed, and the manifest
attributes every health loss to a move (`hits_by_move`).

`tools/combat_capture/README.md` has the session, hot-reload, and tuning-loop
commands. Capture output is developer evidence and records no owner verdict.

For quick looks without Minecraft, the offline previews render first-person rig
frames (item model, both arms, trail) at any tick, third-person PAL poses, and
item models from `src/main/resources`, in seconds and without the heavy-work
lock. They need numpy and Pillow; without them the command re-executes under
`$MC_PREVIEW_PYTHON` or `.venv-preview/bin/python`, or prints the setup
command. The default vanilla resources jar comes from a Gradle build
(`build/moddev/artifacts/`).

```bash
python3 -m venv .venv-preview && .venv-preview/bin/pip install -r tools/combat_preview/requirements.txt   # once
python3 -m tools.combat_preview fp --weapon myvillage:lingxiao_spear --move all --key-ticks --out out/preview/combat_preview/spear_fp.png
python3 -m tools.combat_preview pose --animations src/main/resources/assets/myvillage/player_animations/spear_combat.json \
    --animation basic_spear_02_sweep --all-keys --item myvillage:item/lingxiao_spear --out out/preview/combat_preview/spear_sweep.png
python3 -m tools.combat_preview model myvillage:item/qingfeng_sword --out out/preview/combat_preview/sword_model.png
# candidate rig values side by side; before/after stills from two capture sets
python3 -m tools.combat_preview sweep --weapon myvillage:lingxiao_spear --set rig.off_hand.thickness=0.42,0.5,0.56,0.62 \
    --out out/preview/combat_preview/sweep_offarm_thickness
python3 -m tools.combat_preview diff out/preview/combat_capture/<before> out/preview/combat_capture/<after> --out out/preview/combat_preview/<before-vs-after>
```

`sweep` renders `fp` frames for each candidate value of a rig field (a dotted
JSON path) and writes close-ups where they differ, changed pixels against the
shipped value, and a rig file per candidate. `diff` writes a before/after sheet
and changed-pixel counts with their bounding boxes. Both are covered in
`tools/combat_preview/README.md` ("Tuning a rig value").

The `fp` port of the first-person solver is pinned to the Java by
`src/test/resources/first_person_preview_parity.json`. Both tests check it;
after a deliberate change to the solver, a rig, or a geometry contract, rewrite
it from the Java and bring `tools/combat_preview/fp_rig.py` in step until the
Python test passes:

```bash
./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest                        # check
./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest -PupdatePreviewParity  # rewrite the golden
.venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity                                   # Python port
```

`tools/combat_preview/README.md` has the options, the `--compare-capture`
check against capture stills, and what the golden does not cover.

For a separate final client profile, connect to the bounded server without
editing launcher state:

```bash
./gradlew runClient -Pcombat_smoke_server=127.0.0.1:25565 \
  -Pcombat_smoke_game_dir=run-combat-smoke -Pcombat_smoke_username=CombatDev
```

`combat_smoke_server` also bounds the client window to `960x540`; set FOV to
`70` for the reference first-person capture. Use a unique game directory
and username for a second physical client. Stop every client and the acceptance
server cleanly after collecting the evidence.

Gate A directly observed PAL controller registration, play, transition, stop,
normal-pose restoration, and dedicated-server side safety. A later real-client
`THIRD_PERSON_MODEL` probe failed because the ready sword floated near screen
center and the attack arm/sword clipped at excessive scale, so custom PAL
first-person arms/camera stay disabled with `FirstPersonMode.DISABLED`.

The 0.26.1 owner review judged the first-person swings "not like swinging a
sword". Capture confirmed why: the strike keys sat after the server hit window,
every key eased to a stop, the blade was edge-on at screen center, and there was
no hit, sound, or trail feedback. Revision 0.26.2 replaces that layer with the
pivot rig and feedback described above and hides the segmented arm. A developer
capture at `960x540`, FOV 70 with mapped clicks showed all five swings crossing
the target during the hit, the trails, sweep/crit particles, and hit-stop. That
capture is implementation evidence only.

Owner verdict on 0.26.2 (2026-09-30): after watching it (lab station A) next to
Epic Fight, the owner said A "现在不太行看上去" and asked for an optimized A:
"我要的是那种战斗真实动作游戏的感觉". The 0.26.2 swings were not accepted.
Revision 0.27.0 is the response. It adds chain windows, server-decided steps and
lunge, target reaction, per-move hit-stop, camera effects, blade-cut particles,
thin translucent trails, the complete first-person arm, and generated
third-person poses.

Owner feedback on 0.27.0 (2026-09-30): "自研的动作好一些了现在，但是握持这部分完全不行现在就像插入肉里的，非常僵硬。剑的建模也不太行可以优化一下。"
Revision 0.27.1 is the response: the fist-and-wrist arm with wrist lag, the 3D
jian model, the geometry contract, and the third-person grip compensation.

Owner words on 0.27.1 (2026-10-01, after viewing the 0.27.0 vs 0.27.1
comparison page, not real-client play): "感觉271看上去可以"; then "暂时就这样".

The lab station E capture on 2026-09-30
(`/home/ubuntu/code/mc/combat-lab/out/E`) exercised 0.27.0 in a physical client.
Rows marked "lab capture E" below were observed there. The lab station F
capture on 2026-09-30 (`/home/ubuntu/code/mc/combat-lab/out/F`) exercised the
0.27.1 grip and model with a development jar, and rows marked "lab capture F"
were observed there. Both captures are implementation evidence, not owner
acceptance. A local copy of the F evidence (0.27.0 vs 0.27.1 comparison
sheets, reactive-dummy clips, model previews, the lab capture tools and their
usage) lives in `out/preview/qingfeng_grip_0271/`. A local copy of the whole
lab (reports, station configs, harness, and the A to F captures) lives in
`out/preview/combat_lab/`; its handoff chapter is
`report/combat_lab_report.md` section 0, and the tracked summary is
[docs/ai-kb/33_combat_framework_comparison.md](docs/ai-kb/33_combat_framework_comparison.md).
Record only directly observed results below.

| Qingfeng real-client acceptance surface | Result |
|---|---|
| Inventory and held-item Qingfeng model/texture resolve and remain recognizable | `pass` |
| Chinese name, recipe-book result, and creative-tab order | `not_verified` |
| Owner verdict on texture, model scale, seven-animation style, and combat feel | `not_verified` |
| Vanilla-mode entity attack, block mining, cooldown, enchantments, repair, and durability | `not_verified` |
| Default `R` toggle and server-owned cultivation mode across reconnect, death, and dimension change | `pass` |
| Mode persistence across a clean server restart | `pass` |
| Rebound mode key plus both English and Chinese action-bar text | `not_verified` |
| Cultivation main-hand Qingfeng interception without a vanilla attack packet or duplicate damage path | `pass` |
| Remapped attack, sword-on-block mining suppression, unsupported item, empty hand, GUI, and vanilla-mode input regression | `not_verified` |
| Moves one through five in exact order, distinct full-body poses, visible fifth lunge, timeout reset, and fifth-to-first reset | `pass` |
| 0.26.x ordinary late-buffer chain and miss continuation under click timing | `pass` (0.26.x timing) |
| 0.27.0 buffer from active start and chain at `7/8/10/13` under real click timing | `not_verified` |
| Second buffered-intent rejection while the one slot is full | `not_verified` |
| Move-one center-thrust side/rear range boundaries | `not_verified` |
| Move-two/three/four visible arc and diagonal distinction | `pass` |
| Move-two/three/four exact target caps, range boundaries, and light knockback | `not_verified` |
| Move-five server-owned forward step measured at `0.8` blocks | `pass` (0.26.x server move; superseded) |
| Move-five wall suppression plus no wall-through target damage | `pass` (0.26.x server move) |
| 0.27.0 move-five impulse lunge produces visible forward displacement | `pass` (lab capture E; distance not measured) |
| 0.27.0 impulse step distances, magnetism stop, wall/cliff suppression, and planned-origin hit sweep | `not_verified` |
| Move-five player collision and cliff suppression | `not_verified` |
| Solid-wall target blocking | `pass` |
| PvP/team rules, invulnerability, deterministic target order, and per-action hit deduplication | `not_verified` |
| Real server damage and exactly one durability loss for each of five successful actions | `pass` |
| Armor/protection, Sharpness/Smite/Bane, Knockback/Fire Aspect, and NeoForge event-listener compatibility | `not_verified` |
| No duplicate vanilla damage, sweep, cultivation critical, or sprint bonus | `not_verified` |
| First-person mapped click produces immediate packet-free predicted Qingfeng feedback | `pass` |
| 0.26.1 normalized first-person curves read as a sword swing | `fail` |
| 0.26.2 pivot-rig swings read as a real action game (owner, 2026-09-30: "现在不太行看上去") | `fail` |
| Owner verdict on the 0.27.0 action-feel revision | `not_verified` |
| 0.27.0 first-person arm holds the sword on the pivot rig | `pass` (lab capture E) |
| 0.27.0 thin translucent 剑光 trails | `pass` (lab capture E) |
| 0.27.0 `blade_cut` particles at the contact point | `pass` (lab capture E) |
| 0.27.0 target slide on hit | `pass` (lab capture E) |
| 0.27.0 camera roll on heavy hits | `pass` (lab capture E) |
| 0.27.0 third-person full-body poses | `pass` (lab capture E) |
| 0.27.0 per-move hit-stop, target freeze/hitstun, player-target slow, and boss exemption | `not_verified` |
| 0.27.0 swing whoosh lead, pitch variation, heavy impact layer, and subtitles | `not_verified` |
| 0.27.0 no slowness FOV zoom during swings; accessibility scaling of camera effects | `not_verified` |
| 0.27.0 chained-move prediction and remote impact freeze/jitter in multiplayer | `not_verified` |
| Owner verdict on the 0.27.1 grip and 3D sword model | `not_verified` |
| 0.27.1 inventory shows the 2D icon, hand shows the 3D jian, no missing texture | `pass` (lab capture F) |
| 0.27.1 item frame and dropped item show the 3D jian | `pass` (lab capture F) |
| 0.27.1 third-person handle through the fist, back and front, staying in the fist during moves | `pass` (lab capture F) |
| 0.27.1 first-person fist on the handle with the guard visible | `pass` (lab capture F) |
| 0.27.1 first-person wrist bend and wrist lag/follow-through read naturally | `not_verified` |
| 0.27.1 first-person grip with a slim-arm skin and a left main hand | `not_verified` |
| 0.27.1 first-person wrist top edge: an occasional 1 px dark-green line in the rising-cut follow-through, visible at 6x zoom (lab F `grip_v5/compare_v4_v5_wrist_6x.png`). Suspected skin-texture bleed at the face boundary (the body shirt column next to the arm strip), not geometry; needs a recapture with other skins to confirm, and would be fixed by insetting the arm box UVs | `not_verified` (open) |
| 0.27.1-fix1 third-person legs walk with the sword drawn (ready idle), arms keep the guard, no foot sinking, smooth return to the stance on stopping | `pass` (headless capture 2026-10-02, local player, front F5 view, W held 2.5 s) |
| 0.27.1-fix1 walking legs seen on a remote player and under real keyboard play | `not_verified` |
| 0.28.0 data-driven styles: five moves and server damage unchanged under mapped clicks (target `80.0` to `44.18` before and after) | `pass` (headless capture 2026-10-02, `out/preview/combat_capture/before-vs-after-data-infra`) |
| 0.28.0 first- and third-person poses unchanged: 75 probe stills (5 moves, 5 key ticks, 3 views) match before and after | `pass` (same capture; developer comparison at 2% colour tolerance, not an owner verdict) |
| 0.28.0 packaged jar loads the combat data on a standalone dedicated server (protocol `7`) | `pass` |
| 0.28.0 two-client observation after the protocol change: remote start/stop, impact freeze/jitter, world-trail length | `not_verified` |
| 0.28.0 weapon change during an action in a real client (unregistered item stops it) | `not_verified` |
| 0.28.0 real keyboard play and sound | `not_verified` |
| Remote/third-person world trail follows the hitbox arc | `not_verified` |
| PAL third-person-model first-person arms/camera animation | `fail` |
| Two-client nearby five-move start/stop animation and real target damage synchronization | `pass` |
| Live interruption on mode, item, mount, dimension, death, and meditation start | `pass` |
| Logout/reconnect cleanup plus persisted ready state | `pass` |
| Unfinished-action recovery-lock timing | `not_verified` |
| Meditation start suppresses attack/ready pose until meditation stops | `pass` |
| Advancement mutual interruption and double-penalty exclusion | `not_verified` |
| Existing rideable sword, cultivation H/keys, GuideME, and ordinary combat regression | `not_verified` |

0.29.0 adds the Lingxiao Spear and two first-person fixes that change how the
Qingfeng Sword looks in play. Rows marked "headless capture" were observed in a
developer client on the capture host (`tools/combat_capture`, 960x540, software
rendering); they are implementation evidence, not owner acceptance. Evidence
lives under `out/preview/lingxiao_spear/` and `out/preview/combat_capture/`.

| Lingxiao Spear and 0.29.0 acceptance surface | Result |
|---|---|
| `/give`, tooltip (8 attack damage, 1.2 attack speed), and creative-tab place after the four swords | `pass` (headless capture 2026-10-02, `lingxiao_spear/ingame_model`) |
| Held 3D spear in first-person vanilla hold, dropped item, and item frame match the offline model previews; spearhead and inlays stay lit at night | `pass` (same capture) |
| Inventory icon readable at GUI scale 2 (icon redrawn on a 32x32 grid after the first look found the earlier one breaking up) | `not_verified` |
| Off-hand vanilla hold drawn as the mirror of the main hand (left-hand display fixed after the first look; the pennant side is still not mirrored) | `not_verified` |
| Chinese and English item and move names in game | `not_verified` |
| All five spear moves from mapped clicks with server damage, default layout: centre target `80.0` to `38.28` (per move `7.085/7.478/7.872/9.053/10.234`) in every run, normal and quarter speed, first person and F5 back | `pass` (headless capture 2026-10-02, `combat_capture/spear-final`, `-tpcombo`, `-slow-fp`, `-slow-back`) |
| Spear target spread: sweep hits three targets on an arc 2.5 blocks out; on a line 2.5/3.5/4.5 ahead the lunge hits three, sweep, flick, and smash two each, thrust one | `pass` (headless capture 2026-10-02, `combat_capture/spear-final-sweep`, `-line`) |
| Spear stills: first person with both hands on the shaft, quarter views of the two-handed guard, releases, and regrips, five moves at five key ticks | `pass` (headless capture 2026-10-02, `combat_capture/spear-final`; developer evidence, not an owner verdict) |
| Quarter-view motion of the five moves, mode entry, and ready idle | `pass` (client playback only, `combat_capture/spear-final-motion`; no hits, steps, or world trail) |
| One first-person hit-stop per move; a three-target sweep starts one stop and ignores two confirmations | `pass` (headless capture 2026-10-02, `fp_log` of `spear-final-sweep`) |
| Swing keeps its pace through a forced +20 game-clock reset (`/tick sprint 20`); `/tick freeze` and `/tick step` hold and resume it; a Nether teleport and a death mid-move stop it cleanly | `pass` (headless capture 2026-10-02, `lingxiao_spear/fp_runtime/forced`) |
| Game-clock reset from server lag, a camera kick running through a reset, a clean track of a +1/+2 reset mid-swing | `not_verified` |
| Spear world trail seen by another player; one stop per action seen by another player | `not_verified` |
| Flick and smash world trails seen from the side | `not_verified` |
| Spear first-person off arm hidden while an item is in the off hand | `not_verified` |
| Spear step distances, magnetism stop, lunge wall/cliff suppression, and foot sliding | `not_verified` |
| Owner verdict on the spear model, icon, five moves, and two-handed presentation | `not_verified` |
| First-person hit-stop no longer steps back on a late hit confirmation (Qingfeng and spear, normal and quarter tick rate) | `pass` (headless capture 2026-10-02, `lingxiao_spear/fp_runtime`) |
| First-person weapon no longer dips out of view after a landed hit costs durability (Qingfeng and spear) | `pass` (same capture; an unbreakable control shows the same no-dip trace) |
| Rename, enchantment, or another item still plays the equip animation; switching between two copies of one weapon does not | `not_verified` |
| Qingfeng regression: `80.0` to `44.18` under mapped clicks; third-person stills identical and first-person stills within 2 colour levels of `sword-v2`, apart from the experience bar row | `pass` (headless capture 2026-10-02, `combat_capture/sword-final`; developer comparison, not an owner verdict) |
| Owner verdict on the changed Qingfeng first-person hit-stop, post-hit hold, and clock behaviour | `not_verified` |
| Combat-mode and debug-command messages weapon-neutral in both languages | `not_verified` |
| Real keyboard and mouse play, sound, frame rates on a real GPU, other skins, armour, and capes | `not_verified` |

### Adding Another Combat Weapon (0.30.0)

The procedure is `docs/ai-kb/36_new_combat_weapon.md` (22 numbered steps).
`tools/new_combat_weapon.py` starts a weapon from an existing one and reports
how far it has come, in the same step ids:

```bash
python3 tools/new_combat_weapon.py scaffold --from myvillage:qingfeng_sword --id myvillage:jade_sword --dry-run
python3 tools/new_combat_weapon.py scaffold --from myvillage:lingxiao_spear --id myvillage:iron_halberd \
    --style myvillage:basic_halberd --moves thrust,sweep,hook,chop,lunge
python3 tools/new_combat_weapon.py progress myvillage:iron_halberd [--fast] [--json]
```

`scaffold` writes only the renamed copies (weapon file, index entries,
first-person rig, with `--style` the style, and `TODO(new_combat_weapon)`
translations) and prints every other step with its file; it never writes Java
or a generator-owned file, and refuses, writing nothing, when an id or file is
taken. `progress` sorts the existing validators' and tests' findings into the
steps (`DONE`, `PLACEHOLDER`, `MISSING`, `BLOCKED`, `N/A`) and exits 1 while a
checked step is open.

### Xuantie Gauntlet (0.39.0)

`myvillage:xuantie_gauntlet` (玄铁拳套) is the 拳掌 (fist) school's weapon: a
pair of dark-iron gauntlets in one item (the weapon file sets `paired`), the
third combat weapon. It is
in `myvillage:main` after the Lingxiao Spear and has no recipe:

```mcfunction
/give @s myvillage:xuantie_gauntlet
```

In vanilla mode it is a diamond-tier sword item with 5 attack damage and 2.2
attack speed (lighter and faster than the sword). In cultivation mode it runs
the `myvillage:basic_fist` style (`data/myvillage/combat/style/basic_fist.json`):
short reach, fast moves with two-tick hit windows, small slides and short
hit-stops; moves one to four chain at `6/7/7/8`, the fifth cannot chain, and
the combo resets after 10 idle ticks:

```text
1  basic_fist_01_straight_punch      冲拳      9 ticks, hit 3-4, range 1.9, one target
2  basic_fist_02_horizontal_palm     横掌     10 ticks, hit 4-5, right to left at chest height, two targets
3  basic_fist_03_uppercut            上勾     10 ticks, hit 4-5, rising through the centre, lifts the target
4  basic_fist_04_chop                劈掌     11 ticks, hit 5-6, from high right down through the centre, two targets
5  basic_fist_05_step_double_strike  踏步双撞  11 ticks, hit 6-7, a 0.9-block step then both fists, two targets, heavy
```

The gauntlet sits on the fist and never turns in the hand. Both hands are free:
in third person the stance leads with the left hand out at chest height and
the gauntlet at the dantian; the left hand pulls back to the waist on the punch
and the chop, stays up on the palm and the uppercut, and strikes with the right
on the step-in. The mode entry is a 抱拳礼. In first person the left guard
hand is drawn while the off-hand slot is empty and moves with the strikes.

0.39.1 (after the owner's first look): one item is a pair. While the off-hand
slot is empty the same model is drawn mirrored on the left hand, in third
person by `PairedWeaponLayer` (it follows the PAL left arm) and in first
person over the guard fist; anything held in the off hand replaces it. The
model is a slimmer articulated fist (12.5 x 11.0 x 10.25 model px, was
16.5 x 12.8 x 11.9; still 2 model px per player px) with separate finger
plates, knuckle caps, overlapping back lames, the thumb across the curled
fingers and a plain palm, and the inventory icon is an angled three-quarter
fist. Candidates and before/after sheets: `out/preview/xuantie_gauntlet/v2/index.html`. The
model, textures, icon, and the contract `combat/xuantie_gauntlet_geometry.json`
come from `tools/gen_xuantie_gauntlet_model.py`; the poses in
`player_animations/fist_combat.json` come from `tools/gen_sword_pal_anims.py`
(`BASIC_FIST`); the first-person rig `combat/xuantie_gauntlet_first_person.json`
is hand-authored and reloads with `F3+T`. Edit the generators, never their
outputs:

```bash
python3 tools/gen_xuantie_gauntlet_model.py            # write the outputs
python3 tools/gen_xuantie_gauntlet_model.py --check    # fail on drift or a failed self-check
python3 tools/gen_xuantie_gauntlet_model.py --report   # the derived display and the fist fit
python3 tools/gen_sword_pal_anims.py --report          # per key, with each fist move's off-hand role and arm clearance
python3 -m tools.combat_preview fp --weapon myvillage:xuantie_gauntlet --move all --key-ticks --out out/preview/xuantie_gauntlet/fp.png
python3 -m tools.combat_capture run --label <label> --weapon myvillage:xuantie_gauntlet
```

What the gauntlet added to the shared combat data (details in
`docs/ai-kb/34_combat_data_and_capture.md`, `35_lingxiao_spear.md` and
`36_new_combat_weapon.md`): a weapon file's optional `family` (`sword`,
`spear`, `fist`), which the validator checks against the schools'
`weapon_family`; the optional boolean `paired` (0.39.1, validator
`COMBAT_WEAPON_PAIRED`: needs a free off hand and no `off_hand_grip_center`);
a free first-person off hand (`rig.off_hand.free`) with a
keyed rest per key (`off_hand_rest`, `off_hand_reach`); `rig.arm.grip_diagonal`
up to 90 for a weapon worn along the hand; and the pose generator's `worn` and
`free_off_hand` tables. `tools/tests/test_combat_style_baseline.py` and
`BasicFistStyleTest` pin the accepted fist values.

Rows marked "headless capture" were observed in a developer client on the
capture host (`tools/combat_capture`, 960x540, software rendering) on
2026-10-07; they are implementation evidence, not owner acceptance. Offline
sheets and candidates for the owner: `out/preview/xuantie_gauntlet/index.html`.

| Xuantie Gauntlet and 0.39.0 acceptance surface | Result |
|---|---|
| All five fist moves from mapped clicks with server damage: centre target `80.0` to `56.38` (per move `3.936/4.182/4.428/4.92/6.15`) | `pass` (headless capture, `combat_capture/gauntlet-final`) |
| One first-person hit-stop started per move, the finisher's included | `pass` (same capture, `fp_log`; the two earlier captures showed the finisher's stop dropped, fixed in this version) |
| Gauntlet drawn on the fist in third person, front and back, through the five moves; bare left guard hand drawn in first person with an empty off-hand slot | `pass` (same capture, stills; developer evidence) |
| Owner verdict on the gauntlet model, texture, and icon | `not_verified` (0.39.0 rejected by the owner; 0.39.1 rework not yet seen) |
| 0.39.1 pair: the mirrored left gauntlet in third person through the stance and the five moves, and on the first-person guard hand | `pass` (headless capture `combat_capture/gauntlet-v2-pair`, 2026-10-07: left gauntlet mirrored on the left fist in tp_front/tp_back and on the first-person guard hand in all five moves; server damage unchanged, `80.0` to `56.38`; developer evidence) |
| 0.39.1 pair: owner verdict on the left gauntlet (mirror, fit on the left fist, guard and chamber read with two gauntlets) | `not_verified` |
| 0.39.1 size: owner verdict on the slimmer model (no longer a ski glove) in third and first person | `not_verified` |
| 0.39.1 icon: owner verdict on the angled icon at 1x in the hotbar, inventory and creative tab | `not_verified` |
| Owner verdict on the stance, mode entry, and the five third-person strikes (weight transfer, off-hand counter-moves) | `not_verified` |
| Owner verdict on the first-person rig: gauntlet size, punch read from behind, guard hand size and place | `not_verified` |
| Fist trails (short, from the knuckles) in first person and as world trails seen by another player | `not_verified` |
| Sound (reused sword sounds at higher pitch) and the heavy finisher's feel | `not_verified` |
| Real keyboard and mouse play, chain timing at `6/7/7/8`, combo timeout 10, minimum intent interval 1 | `not_verified` |
| Step-in distance, magnetism stop, wall/cliff suppression, two-target spread of the palm, chop, and double strike | `not_verified` |
| Off-hand item held: guard hand and left gauntlet hidden, vanilla off-hand draw | `not_verified` |
| Vanilla first-person hold, dropped item, item frame, inventory icon at GUI scale 2, tooltip (5 damage, 2.2 speed), creative-tab place, Chinese and English names | `not_verified` |
| Slim-arm and other skins in the gauntlet; left main hand (the pair then sits on the right off hand unmirrored); armour and capes | `not_verified` |
| A second client (remote poses, impact freeze, one stop per action) | `not_verified` |
| Frame rates on a real GPU | `not_verified` |
| Qingfeng and Lingxiao regression after the shared first-person changes (keyed rest, free off hand, grip diagonal range) | `not_verified` in game (parity golden and Java tests unchanged for both) |

### Movement Dodge (0.40.0)

A learned 身法 (movement) technique gives a short dash with an invulnerable
window. Press `Movement Dodge` (身法闪避, `key.myvillage.dodge`, default Left
Alt, rebindable under the MyVillage key category) in cultivation combat mode.
The movement keys held at that moment pick one of eight directions relative to
the view; with none held the dodge is a back step. A sneaking player's held key
still counts as a direction (dead zone 0.2, below vanilla's 0.3 sneak input
scale). No weapon has to be in hand.

The client sends only the key press and that one-byte direction
(`CombatDodgeIntentPayload`); the server decides everything else, in this
order, and refuses at the first failed check:

1. State: alive, not spectating, sleeping, using an item, riding, or
   meditating (`STATE`); cultivation combat mode (`MODE`); on the ground,
   by the client's flag or a block within 0.25 below the feet
   (`AIRBORNE`).
2. Technique: the highest-grade learned movement technique with an
   `effects.movement` block; equal grades go to the smaller id
   (`NO_TECHNIQUE` without one). Its data gives the distance, the
   invulnerable ticks, and the cooldown.
3. Cooldown since the last dodge (`COOLDOWN`).
4. A running attack can only be dodged out of its recovery (action tick past
   the move's last active tick); during the wind-up or the strike the press
   is refused (`TIMING`) and not queued.
5. The dash distance is shortened to a collision- and footing-safe one, as for
   an action step (ground within one block below); none left is `BLOCKED`.

Then a recovery in progress stops with the new stop reason `DODGED` (combo
back to the first move, no recovery lock), the server applies the dash
impulse (`setDeltaMovement` + `hurtMarked`, with a small downward part,
-0.1, so the body stays on the ground and keeps ground friction; sprint off),
and opens the
invulnerable window: for that many ticks every incoming damage is cancelled
except sources tagged `bypasses_invulnerability` (`/kill`, the void), and
attack intents are refused, so an attack after a dodge starts when the window
ends. Each dodge adds one mastery point to the technique. Everyone nearby gets
a low thrust whoosh and a puff of cloud at the feet; `CombatDodgeStartPayload`
tells the dodging player and everyone tracking it to draw cloud afterimages at
the heels for at least six ticks, and gives the dodging player a 6° FOV surge
(plus a 1.2° lean on a sideways dodge). The motion itself is the vanilla
motion packet; the client predicts no movement. There is no dedicated dash
pose. `qi_cost` is in the data but no qi is spent. Payload protocol is `12`,
so client and server need the same jar.

Two water movement techniques ship (the catalogue now has 131):

| Technique | Grade | `previous` | Distance | Invulnerable | `qi_cost` (not spent) | Cooldown |
|---|---|---|---:|---:|---:|---:|
| `myvillage:liuyun_bu` 流云步 | 黄 1 | none | 3.5 blocks | 5 ticks | 6 | 30 ticks |
| `myvillage:taxue_wuhen` 踏雪无痕 | 玄 2 | 流云步 | 4.5 blocks | 7 ticks | 10 | 24 ticks |

Learn one by studying its manual (`manual_movement_huang` / `_xuan`; the
usual realm, affinity, and `previous` requirements apply) or, for testing,
with the debug command, which checks only that the technique is registered:

```mcfunction
/myvillage cultivation manual @s myvillage:liuyun_bu
/myvillage cultivation learn @s myvillage:taxue_wuhen
/myvillage combat dodge status [player]
```

`combat dodge status` prints the technique a dodge would use (grade,
distance, invulnerable ticks, cooldown), the cooldown and window ticks left,
and whether the player is dodging. The server logs every press at INFO:

```text
DODGE_DEBUG player=<name> t=<tick> result=started technique=<id> dir=<DIR> yaw=<f> distance=<f> invuln=<n> cooldown=<n>
DODGE_DEBUG player=<name> t=<tick> result=rejected reason=<STATE|MODE|AIRBORNE|NO_TECHNIQUE|COOLDOWN|TIMING|BLOCKED>
DODGE_DEBUG player=<name> t=<tick> cancelled_damage=<amount> source=<damage type id>
```

Movement data comes from the catalogue: a row's optional `effects` object in
`tools/technique_catalogue/catalogue.json` overrides the category default and
is checked against the Java shape; names containing 步, 身法, 无痕, or 遁 are
classified as movement. Regenerate with `tools/gen_technique_catalogue.py`.
Both techniques are also in the world ledger's pool, so sects, rogue
cultivators, and fortunes can now draw a 身法. Details:
`docs/ai-kb/42_movement_dodge.md`; brief: `docs/movement-dodge-brief.md`.

Headless evidence against the demon wolf (bite and pounce at several move
ticks, cooldown, recovery cancel; health, distance, server lines, F5-back
videos) into `out/preview/movement_dodge/`:

```bash
python3 -m tools.combat_capture dodge [--beast ID] [--technique ID] [--out DIR]
```

<!-- DODGE_CAPTURE_RESULTS --> Capture results (headless capture on the
build host, commit `ef4187f`, `/tick rate 5`, 踏雪无痕: 4.5 blocks, 7
invulnerable ticks, cooldown 24; output `out/preview/movement_dodge/` with
`index.html`, `manifest.json`, `dodge_log.txt`, and two F5-back videos,
`video/dodge_demon_wolf_bite.mp4` 88.8 s and `video/dodge_demon_wolf_pounce.mp4`
115.2 s). Developer evidence, not the owner's verdict:

- Bite: without a dodge health `20` to `16` (hit at move tick 11, 4.0).
  Back steps pressed at move ticks 4, 7, 9, 10 and a left dodge at 9 all
  `started` and health stayed `20`; back steps moved 4.49 blocks, the left
  dodge 4.92 (with the A key's own walk). After the press at tick 10 the
  server logged `cancelled_damage=4.00 source=minecraft:mob_attack` on the
  next tick and the wolf's hit line read `accepted=false` (the window took
  the hit); the other four left the bite's reach and drew no hit line.
- Pounce: without a dodge health `20` to `13.6` (hit at move tick 27, 6.4).
  Back steps at 14, 17, 18, 19 and a left dodge at 18 all `started`, health
  stayed `20`, 4.49 blocks (left 5.14). All five escaped by distance; no
  pounce hit was cancelled by the window (that needs a later press or a
  shorter dash).
- Cooldown: two presses 5 ticks apart, `started` then
  `rejected reason=COOLDOWN`.
- Recovery cancel: after the first Qingfeng move, a press at action tick 3
  was `rejected reason=TIMING`, one at action tick 6 `started` (4.22
  blocks).
- Every landing matched the planned distance; no `AIRBORNE` refusal in the
  whole log.

The first capture run found that a flat impulse left the client reporting
`onGround=false` on the next tick: air friction carried the 4.5-block back
step to 5.39 blocks, and a press at action tick 3 was refused `AIRBORNE`
instead of `TIMING`. `ef4187f` adds the downward part and the 0.25-block
support probe; the numbers above are from the second run.

| Movement Dodge (0.40.0) real-client acceptance surface | Result |
|---|---|
| A dodge cancels a demon wolf bite and a pounce inside the window (health unchanged, `cancelled_damage` or no accepted hit) | `pass` (headless capture `movement_dodge`, `ef4187f`: health `20` kept in all 10 dodge trials; bite at tick 10 cancelled by the window, `cancelled_damage=4.00`, `accepted=false`; the other bite trials and all pounce trials escaped by distance, so window cancellation of a pounce hit is not shown; developer evidence) |
| `COOLDOWN` on a second press inside the cooldown; `TIMING` during an attack's strike and `started` in its recovery | `pass` (same capture: second press 5 ticks later `COOLDOWN`; action tick 3 `TIMING`, action tick 6 `started`, 4.22 blocks; no `AIRBORNE` in the log; developer evidence) |
| Feel: dash distance (3.5 / 4.5 blocks), invulnerable window (5 / 7 ticks), cooldown (30 / 24 ticks) | `not_verified` |
| Key: Left Alt by default, rebinding, eight directions and the back step from real WASD input, sneaking | `not_verified` |
| Cloud afterimages and the puff at the feet, seen by the dodger and by a second client | `not_verified` |
| Camera: FOV surge and sideways lean on the dodging player | `not_verified` |
| Animation after `DODGED`: stop of the recovery, combo back to the first move, ready idle re-entered, no dash pose | `not_verified` |
| Attack right after the window; a click inside the window is not predicted locally | `not_verified` |
| Sound (the thrust whoosh at volume 0.6, pitch 0.75) | `not_verified` |
| Learning through a manual and the 功法 page's 身法 card; mastery rising per dodge | `not_verified` |
| Edges and walls (`BLOCKED`), slopes, water, riding, meditation, mid-air presses | `not_verified` |
| Frame rates on a real GPU | `not_verified` |

### Spiritual Power and Sword Qi (0.46.0)

Spiritual power now has a realm-stage capacity and server-owned recovery.
0.80.0：庚金剑诀（锁定破甲）、天罡剑典（七段区域剑罡）、太白剑经（延迟追斩）已可通过 **H → 技能** 分配到六键槽。身法无敌窗口逐段全额豁免，余势部分化解；锁定受墙体和距离限制。原有右键剑气与 Z/C 默认武技保留。详见[太白剑脉说明](guidebook/getting_started/locked_sword_arts.md)。

The public grade-one 剑气诀 (`myvillage:jianqi_jue`) is the first usable active
technique. Melee and movement dodge remain free.

Normal play: reach Qi Refining I, join a sect, borrow 剑气诀 from its scripture
shelf, then use the manual and complete its study. Equip a sword-family weapon,
enter cultivation combat mode, aim, and hold then release use (default right
mouse) to launch sword qi. Full charge releases automatically. More charge
increases the wave's width, damage and power cost. The advancing wave stops at
walls; each launch has a bounded target count and cannot hit a target twice.

The upper-left jade-seal HUD shows current/capacity, a power bar and a gold
charge indicator (0.46.2). The wave has a thicker crescent body and brighter
front edge, inside its server-authoritative bounds; damage and hit range are
unchanged. Power
recovers after the post-spend delay; actual meditation accelerates recovery,
while studying a manual does not. Insufficient power refuses the launch.
Interrupted charges spend nothing. Opening a GUI or losing focus cancels only
an unreleased charge; release the use key before starting again. Logout/relogin and respawn do not refill
power; the persistent balance stays within the current stage cap.

Administrator fixtures and equivalent aliases:

```mcfunction
/myvillage cultivation qi @s
/myvillage cultivation qi fill @s
/myvillage cultivation qi set @s 0
/myvillage xiulian lingli buman @s
/myvillage xiulian lingli shezhi @s 0
/myvillage cultivation manual @s myvillage:jianqi_jue
/myvillage cultivation learn @s myvillage:jianqi_jue
```

`qi`/`lingli` work under both `cultivation` and `xiulian`; `set`/`shezhi` and
`fill`/`buman` are equivalent. Set rejects amounts above the stage cap. The
manual and learn commands prepare test fixtures; they do not prove that normal
borrowing and study work.

No-DevBridge developer capture:

```bash
python3 -m tools.combat_capture sword-qi --journey --language en_us --out out/preview/sword_qi_journey
python3 -m tools.combat_capture sword-qi --hud --language en_us --out out/preview/sword_qi
python3 -m tools.combat_capture sword-qi --hud --reuse-world --language zh_cn --out out/preview/sword_qi_zh
```

`--journey` exercises real steward join, shelf borrowing, manual study and the
earned skill from an explicitly prepared eligible prospect; it grants no join,
manual or learned technique by command. Run it before boundary fixtures.
`--hud` captures idle/charging at three window sizes. Sessions default to
`en_us`; `--language zh_cn` explicitly checks Chinese. `--reuse-world` preserves
only the isolated capture world for restart checks; it is not a general world
backup command. Language applies when a new session starts. Reuse fails immediately if that
world is missing. Capture shares `run-acceptance` with stage-1 preparation: run
the clean stage-1 smoke before saving a capture world for restart checks. Its
default `--clean` removes the whole directory, including the capture world;
`--no-clean` preserves it but is not a clean acceptance baseline.

The tool uses actual mouse press/release input in a Minecraft client, RCON
fixtures and health readback, `SWORD_QI` server logs, and recordings. It produces
an HTML page and a manifest with separately labelled admin fixtures and
unobserved natural-play checks. A successful startup or pose probe does not
establish successful casting.

For this change, measured commands are recorded in
`reports/qi-sword-wave-tests/timings.jsonl`, with UTC start/end, elapsed time,
exit status, command, cache state, CPU time and peak RSS where measurable.
The host snapshot and readiness/trial timings accompany the ledger. Failed
attempts and reruns remain recorded; report them separately from successful
runs when comparing future test environments. Heavy Gradle/Minecraft work uses
the shared lock, and a warm build is not presented as a clean-cache benchmark.

| Spiritual power / sword qi acceptance surface | Result |
|---|---|
| Focused rules/data tests, affected regression and fresh build | `pass` (913 Java tests, 384 affected Python tests; final Gradle test/build 114.295 s, developer evidence) |
| Final jar inspection | `pass` (fresh 0.46.0 jar: skill, technique, power rules, stage caps, both languages and cancel/HUD classes; 0.071 s) |
| Bounded isolated stage-1 server startup | `pass` (77.707 s; startup evidence only) |
| Saved balance and learned skill after restart | `pass` (separate admin lifecycle fixture: same world, offline power 17; after login 19/30 as recovery resumes; learned techniques and sect membership retained) |
| Real mapped-input charge/release, cost, target damage and wall blocking | `pass` (11 boundary trials; mapped input plus server logs/health, developer evidence) |
| Charge cancellation, insufficient power and recovery | `pass` (11 boundary trials, later tap/full/menu-cancel 3/3; acquired-skill ordinary recovery observed, developer evidence) |
| Melee and dodge at exactly zero power | `pass` (before/after balance 0, target health 93.07 → 86.77, dodge displacement 4.4803 blocks with server start) |
| English/Chinese HUD at GUI 480×270, 427×240, 320×240 | `pass` (12 real-client idle/charge PNGs; representative small layouts inspected, developer evidence) |
| F3+T completion | `pass` (automated reload readiness 5.572 s; final owner appearance remains unverified) |
| Sect shelf → manual study → learned technique → cast without granting the technique by command | `pass` (eligible-prospect fixture; real JOIN, BORROW, inheritance, study, cast and recovery; not a from-new-character run, and separate from the later restart fixture) |
| Second-client release/wave observation | `pass` (two real clients online; observer swing and travelling wave inspected, full-charge cost 12 and target HP 100 → 90.55; developer evidence) |
| Observer charge-particle clarity | `not_verified` (target cleanup death particles contaminated the charge still) |
| Same-world restart and reconnect | `pass` (74.027 s restart phase; world existed, client rejoined; separate from original acquisition world) |
| Owner first/third-person look, sound, feel and physical-client FPS | `not_verified` |

Persistent environment/timing report: [2026-10-14-spiritual-power.md](docs/testing/2026-10-14-spiritual-power.md).

Developer evidence: [sword-qi capture index](http://43.156.135.198:8765/sword_qi/index.html).

Implementation and data ownership: [45_spiritual_power_sword_qi.md](docs/ai-kb/45_spiritual_power_sword_qi.md).

### Cultivation polish (0.46.2): owner review

This build adds the custom two-cell reading stand, an upper-left jade power HUD,
approximately 10-second grade-one manual study at default affinity, and a thicker
brighter sword-qi crescent. The version and changelog workflow follows
`docs/WORKFLOW.md` the version section. The 0.46.0 developer results above do not
constitute acceptance of these revised visuals. Preparing the jar does not
launch the game; enter it later to review the following checks.

1. Run `world sect <id> shelves place` for your built sect and inspect the
   stand's open book, scrolls, jade slips and open wooden feet. Use its lower
   and upper portions to open the same borrowing screen. Test a hand-placed
   stand's four orientations separately; it must remain unowned. In a test
   area, break either half in survival and check one dropped item and no orphan.
2. Use H to confirm eligibility, then right-click a fresh grade-one manual in
   survival/adventure with Basic Breathing learned and an awakened root.
   At affinity 10, no element match and 20 TPS, preparation plus reading takes
   about 10 seconds. Interrupt once and resume; its comprehension must remain.
   An older book above the new threshold should finish at its next settlement.
3. Enter cultivation combat mode with `R`; inspect the upper-left HUD at full,
   empty and charging power, in English and Chinese at GUI 480×270, 427×240 and
   320×240. Its label, seal, value and charge line should remain readable.
4. Equip a sword-family weapon, aim and hold/release right mouse. Compare tap
   and full charge in first and third person, including an oblique view. The
   brighter crescent should be easy to see, should stop at a wall, and should
   retain the existing charge-scaled cost and damage. Menu/weapon cancellation
   should still spend nothing.
5. Use `F3+T`, reconnect and, when available, a second client. Check model and
   HUD reload, saved book progress and power, observer wave visibility, sound,
   feel and frame rate. Record only the checks you actually observe.

For a cast-only fixture, `/myvillage cultivation learn @s myvillage:jianqi_jue`,
`/give @s myvillage:qingfeng_sword` and
`/myvillage cultivation qi fill @s` bypass acquisition. The player still needs
a nonzero stage cap; these commands do not verify borrowing or study. For study,
use `/myvillage cultivation manual @s myvillage:jianqi_jue` on an eligible
character that has not already learned it.

| 0.46.2 owner acceptance surface | Result |
|---|---|
| Stand model, four facings, both-half interaction, one drop and legacy repair | `not_verified` |
| Upper-left power/charge HUD at three GUI sizes in both languages | `not_verified` |
| Grade-one timed learning, interruption/resume and old-book completion | `not_verified` |
| First-/third-person crescent visibility, wall blocking and unchanged cost | `not_verified` |
| Sound, feel, physical-client FPS, F3+T, reconnect and multiplayer | `not_verified` |

Implementation sources: [Scripture hall](docs/ai-kb/43_player_sect_entry.md),
[Study numbers](docs/ai-kb/41_technique_system.md#study-numbers),
[Spiritual power and sword qi](docs/ai-kb/45_spiritual_power_sword_qi.md).

## GuideME Cultivation Guide

MyVillage 0.27.1 requires a compatible GuideME installation on both client and
server (`[21.1.17,22)`). Gradle resolves GuideME 21.1.17 from Maven Central for
development; GuideME is not bundled in the MyVillage jar. The untracked
root-level `guideme-21.1.17.jar` is inspection material, not a build input.

The first data-driven `myvillage:cultivation` slice has one Chinese default
source, a complete `_en_us` pair, and three task pages: overview, initiation,
and the combined cultivation loop. Give and use the MyVillage entry item with:

```mcfunction
/give @s myvillage:cultivation_handbook
```

GuideME's own diagnostic entry points are:

```mcfunction
/guidemec myvillage:cultivation open
/guideme open @s myvillage:cultivation
/guideme give @s myvillage:cultivation
/give @s guideme:guide[guideme:guide_id="myvillage:cultivation"]
```

For authoring, edit only the root `guidebook/` tree and launch:

```bash
./gradlew runGuide
```

That run watches the same source copied into the jar and validates/opens the
guide at startup. Do not add a second checked-in Markdown mirror below
`src/main/resources`. GuideME's item-tooltip hotkey applies in inventory-style
screens, not to a placed block under the crosshair. Owner review of 0.25.1 found
that sharing `G` with MyVillage's stop-meditation binding made GuideME's hotkey
unavailable. The current fix leaves `G` to GuideME and changes MyVillage's
default stop key to `X`; it adds no GuideME-specific interception, remapping, or
automatic migration. Existing installations that saved the old `G` binding
must reset or rebind `Stop Meditation` once in Controls.

Run the focused automated gate before client review:

```bash
python3 tools/validate_guideme_cultivation_guide.py
python3 -m unittest tools.tests.test_validate_guideme_cultivation_guide
./gradlew test
./gradlew build
./gradlew runGuide
./gradlew runAcceptanceServer
```

The GuideME validator and its tests need a `python3` that has PyYAML; without
it they fail with "PyYAML is required to validate GuideME frontmatter".

Client startup and dedicated-server startup prove dependency, page parsing,
packaging, and side safety only. They do not prove rendering or interaction.

Owner real-client review on 2026-07-14 confirmed the guide UI, Chinese default
surface, three-page navigation, and representative component rendering. The
0.25.1 `G` item-index hotkey failed globally; after releasing `G` in the current
fix, the post-fix hotkey behavior remains `not_verified` until retested.

| GuideME real-client acceptance surface | Result |
|---|---|
| Guide discovery and Chinese-default fallback | `pass` |
| Complete English switching | `not_verified` |
| Three-page navigation | `pass` |
| Search for 打坐, 灵石, and 冲关 | `not_verified` |
| Both stele item-index jumps | `not_verified` |
| Spirit-stone and ore item-index jumps | `not_verified` |
| `ItemLink` and `BlockImage` rendering | `pass` |
| Live configured key displays after remapping | `not_verified` |
| Root-source live reload | `not_verified` |
| Handbook first open and remembered-page reopen | `not_verified` |
| GuideME `G` item-index hotkey after MyVillage stop moved to `X` | `not_verified` |
| Existing H-screen and cultivation gameplay regression | `not_verified` |

## Cultivation Playable Loop

**0.48.0 的正常修炼链已开放到金丹后期：测灵与传承 -> 炼气一至九层 -> 筑基初中后期 -> 金丹初中后期。沿用确定性单阶段突破；金丹后期为当前终点，元婴玩家突破尚未开放。**

The foundation provides a server-authoritative immutable player profile (v4 since 0.37.0),
codec-backed Data Attachment persistence, synced definition registries,
owning-client snapshots, and operator commands. The initiation slice remains
two separate server-side actions:

```text
mortal_unawakened
  -> use myvillage:spirit_testing_stele
  -> deterministic root + mortal_qi_sensed
  -> use myvillage:technique_inheritance_stele
  -> myvillage:basic_breathing at mastery 0
```

Awakening never teaches the technique automatically. Inheritance learns
`myvillage:basic_breathing` at mastery `0`; only an active, eligible meditation
session executes that technique. Neither initiation action directly grants
progress, spiritual power, stability, mastery growth, attributes, effects, or
advancement.

Acquire the two facilities from `myvillage:main` creative inventory or with:

```mcfunction
/give @s myvillage:spirit_testing_stele
/give @s myvillage:technique_inheritance_stele
```

These are the only current stele acquisition paths. Neither stele has a recipe,
natural generation, sect/worldgen placement, BlockEntity, menu, or block-local
player data.

The first spirit-stone slice can be inspected directly with:

```mcfunction
/give @s myvillage:low_grade_spirit_stone 64
/give @s myvillage:spirit_stone_ore
/give @s myvillage:deepslate_spirit_stone_ore
```

Both ores require an iron-tier-or-better pickaxe. Silk Touch drops the matching
ore block; ordinary mining starts from one low-grade spirit stone, and Fortune
uses the vanilla `ore_drops` bonus formula. The Overworld biome modifier adds
upper/middle/deep layers with counts `30/3/3`, vein sizes `6/6/3`, and height
bands `80..384`, `-24..56`, and world-bottom through `0`. Existing generated
chunks are not retrofitted: natural ore must be checked in newly generated
chunks. There is no raw ore, smelting chain, recipe, higher grade, storage block,
fragment, refining machine, or currency behavior in this slice.

Press `H` in game to open the non-pausing cultivation panel. The binding is
configurable as `Open Cultivation Profile` under the MyVillage key category.
The panel has a page rail on the left, a header (name, realm and stage,
calendar, remaining lifespan) and a footer (session state) that stay visible on
every page, and a body that scrolls when a small window cannot hold a page:

| Page (zh / en) | Shows |
|---|---|
| 内视 / Profile | The current realm's stage ladder, progress and stability bars, power, spiritual affinity, calendar and lifespan, the spiritual root's shares, and the next advancement's target and conditions. |
| 修炼 / Meditation | 分境界水墨内景、服务器驱动的灵气流动、修为／稳定／灵力、研读与突破条件；四个原有按钮显示实际绑定键，收益与消耗可悬停查看。 |
| 功法 / Techniques | 四大类筛选与已学图标格，独立品阶色；心法可切换主修，主动条目可进入按键分配，完整条件、传承、熟练度与效果保留在悬停层。 |
| 技能 / Skills | 选中可施放技能与六个固定槽位后分配，显示服务器确认的绑定与冲突；清空、随武器、默认和原版键位设置均保留。 |
| 天下 / World | The world ledger (命簿, see [World Simulation](#world-simulation-世界模拟--命簿)), read-only, in five sub-views chosen from the buttons under the body (0.36.0): 总览 (era date, settlement state, population, realms, sects, the five foremost people), 宗门 (every sect, active first), 人物 (search by name or Daoist title as you type), 纪事 (the latest notable and major events, newest first, with their causes), and 此地 (your region, the sects seated there with the distance to their gate, the strongest people present, recent events). Click a sect or person row, or a name inside a detail, to open it; 「← 返回」 goes back. |

Profile values remain read-only: each Meditation button sends only the same bounded action
intent as V/B/X/N, the 运转此心法 button sends only the technique id, and the server revalidates every result. `H` or Escape closes
the panel without stopping cultivation, and reopening it returns to the page it
was left on. While the 人物 search field has focus, `H` types into it instead
of closing the panel (Escape still closes). The 天下 page refreshes the open
view at most every 2.5 seconds and is open to every player; pause, resume, and
advance stay `/myvillage world` commands. Structure and how to add a page:
`docs/ai-kb/37_cultivation_panel.md`.

The current profile is schema version `4` (0.37.0): the v3 fields (all v2
fields plus non-negative `spiritualAffinity`, whose new/reset/migrated default
is `10`) plus the optional running core technique `active_core_technique`. The
version-dispatched codec preserves explicit v1, v2, and v3 decode paths,
migrates v1 and v2 into v3 and v3 into v4 (running core technique = Basic
Breathing when it is learned, otherwise none) without losing unknown ids,
progress, lifespan, or reserve, and writes only v4 afterward. `meditationQiReserve` remains stored for save
compatibility but is inert: v3 cultivation does not credit, spend, convert, or
display it. The attachment uses `copyOnDeath` as its only copy mechanism; there
is no duplicate cultivation `PlayerEvent.Clone` handler. Every profile
replacement still goes through `CultivationService`.

The synced datapack registries and shipped resource roots are:

| Registry key | Resource root |
|---|---|
| `myvillage:realm` | `src/main/resources/data/myvillage/myvillage/realm/` |
| `myvillage:spiritual_element` | `src/main/resources/data/myvillage/myvillage/spiritual_element/` |
| `myvillage:technique` | `src/main/resources/data/myvillage/myvillage/technique/` |
| `myvillage:school` (0.37.0) | `src/main/resources/data/myvillage/myvillage/school/` |
| `myvillage:heritage` (0.37.0) | `src/main/resources/data/myvillage/myvillage/heritage/` |

Spiritual-root generation uses only the Overworld seed, player UUID, algorithm
version `1`, fixed salt `0x4D5956494C4C4147`, and the current positive-weight
element id/`awakening_weight` set sorted by full id. Omitted weights default to
`1`; weight `0` excludes an element. The count distribution is `10/25/35/20/10`
for one through five distinct elements, and integer largest-remainder allocation
produces positive affinities totaling exactly `10000`.

The same root is reproduced after `reset` only when seed, UUID, eligible ids,
weights, and algorithm version are unchanged. Existing saved roots are never
recalculated. Datapack id/weight changes affect future awakening and may change a
post-reset result; seed plus UUID alone is not a permanence promise. Reusing the
testing stele without reset never rerolls an existing root.

`myvillage:basic_breathing` has no generic/data-driven executor field. The fixed
first-release settlement service executes only this technique and requires
current definition eligibility at minimum `myvillage:mortal` /
`myvillage:mortal_qi_sensed`, with no element-affinity restriction. Repeat
inheritance never resets existing mastery.

### Meditation, Time, And Settlement

The client sends only a bounded action intent. Identity, position, eligibility,
timing, profile values, inventory use, settlement, and advancement outcome are
derived on the logical server.

| Configurable key | Intent |
|---|---|
| `V` | Start normal meditation |
| `B` | Start spirit-stone meditation |
| `X` | Stop meditation or advancement |
| `N` | Start the current definition-owned advancement |
| `H` | Open/close the Profile and Meditation panel |

Normal and spirit meditation share one transient state machine. Both start with
`40` eligible preparation ticks. Starting requires an awakened root, learned
Basic Breathing, survival/adventure mode, a living non-exhausted player on stable
ground, no mount/swimming/flight/sleep/item use/conflicting session, and no
positive damage during the previous `100` server ticks. Moving more than `0.01`
block on any axis, jumping, damage, attack/swing, mining, block/entity/item use,
mounting, swimming/flying/sleeping, incompatible game mode, dimension change,
death, logout, or `X` interrupts the session. Camera yaw and pitch, opening H,
and switching between its tabs are allowed.

The time defaults under server config section `cultivation_time` are
`ticks_per_day = 24000`, `days_per_year = 24` and `days_per_week = 6`: one
cultivation day is one Minecraft day-night length, a week is six days, and a
year is four weeks, `576000` effective ticks. The week is only a display unit
(the H header, the 内视 calendar card and the 天下 date row show year, week and
day); weeks restart with each year, and a `days_per_year` that is not a multiple
of `days_per_week` is allowed with a load/reload warning and a short last week.
At 20 TPS with someone online the defaults convert as:

| Unit | Effective ticks | Real time online |
|---|---:|---:|
| 1 day | 24000 | 20 minutes |
| 1 week (6 days) | 144000 | 2 hours |
| 1 year (24 days) | 576000 | 8 hours |
| Mortal maximum (80 years) | 46080000 | 640 hours |

The Overworld `SavedData` calendar advances once per
server tick while at least one player is online, in any game mode. A player's
lifespan advances only while that player is online, alive, and in
survival/adventure. Sleep, `/time set`, daylight-cycle rules, dimension, and
offline wall time do not drive either clock. Realm definitions currently grant
maximum lifespans of `80` mortal, `120` Qi Refining, `240` Foundation and `500` Golden Core years;
warnings are derived at `10`, `5`, and `1` remaining years.

Both counters store raw effective ticks. Changing any time-scale setting does
not rescale old data; it immediately reinterprets all prior calendar and lifespan
ticks and can move the displayed date, warnings, or exhaustion in either
direction. The server logs an operator warning on load/reload. Treat scale
changes as world-rule migrations, back up the world first, and do not assume the
old displayed ages are preserved.

Active Basic Breathing makes one progress settlement every `10` continuously
eligible ticks. Normal meditation adds the current server-profile
`spiritualAffinity`; its default result is therefore `10` progress per batch.
Spirit meditation adds a fixed total `50` progress per funded batch, independent
of affinity, and atomically removes the current source stage's complete batch
from ordinary inventory:

| Current source stage | Low-grade stones / 10 ticks | Progress / 10 ticks |
|---|---:|---:|
| Qi-Sensing Mortal | 1 | 50 |
| Qi Refining I | 1 | 50 |
| Qi Refining II | 2 | 50 |
| Qi Refining III | 3 | 50 |
| Qi Refining IV | 4 | 50 |
| Qi Refining V | 5 | 50 |
| Qi Refining VI | 6 | 50 |
| Qi Refining VII | 7 | 50 |
| Qi Refining VIII | 8 | 50 |
| Qi Refining IX | 9 | 50 |
| Early Foundation | 20 | 50 |
| Middle Foundation | 24 | 50 |
| Late Foundation | 28 | 50 |
| Early Golden Core | 40 | 50 |
| Middle Golden Core | 48 | 50 |

The final nonempty batch pays the full item cost even when fewer than `50`
points remain; its output is clamped to the cap. At the cap, no stone is scanned
or removed. Insufficient inventory removes nothing, applies that due batch at
the normal affinity rate, and downgrades the same session to normal without a
new preparation period. Failed pre-install profile commits restore the complete
multi-slot item removal. Stability does not grow while stage-local progress is
below its cap, including the batch that first fills progress. Starting with the
next ten-tick batch, either mode adds current `spiritualAffinity` to stability,
without scanning or consuming a stone, until the stage stability cap is reached.
Basic Breathing mastery alone remains at `10` per configured cultivation year
in both modes and across both phases.

The shipped stage-local caps are:

| Stage | Progress cap | Stability cap |
|---|---:|---:|
| Qi-Sensing Mortal | 1000 | 500 |
| Qi Refining I | 1100 | 550 |
| Qi Refining II | 1200 | 600 |
| Qi Refining III | 1300 | 650 |
| Qi Refining IV | 1400 | 700 |
| Qi Refining V | 1500 | 750 |
| Qi Refining VI | 1600 | 800 |
| Qi Refining VII | 1700 | 850 |
| Qi Refining VIII | 1800 | 900 |
| Qi Refining IX | 1900 | 950 |
| Early Foundation | 3000 | 1500 |
| Middle Foundation | 3600 | 1800 |
| Late Foundation | 4200 | 2100 |
| Early Golden Core | 6000 | 3000 |
| Middle Golden Core | 7200 | 3600 |

Unawakened mortal and terminal Late Golden Core have no cultivation
cap and cannot gain progress. All fifteen source stages above are playable. `cultivationProgress` is always
the current stage's progress; successful advancement resets it to zero and never
transfers overflow.

### Deterministic Advancement

Press `N` only after the current cap and stability requirement are met. The
server owns the target and rule; there is no random failure and no client-chosen
stage. Advancement is mutually exclusive with meditation and reuses its
interruption set.

| Transition | Required progress | Duration | Required stability | Stability after success | Interrupt loss |
|---|---:|---:|---:|---:|---:|
| Qi-Sensing Mortal -> Qi Refining I (ordinary) | 1000 | 100 ticks | 500 | 250 | 0 |
| Qi Refining I -> Qi Refining II (ordinary) | 1100 | 100 ticks | 550 | 275 | 0 |
| Qi Refining II -> Qi Refining III (ordinary) | 1200 | 120 ticks | 600 | 300 | 0 |
| Qi Refining III -> Qi Refining IV (bottleneck) | 1300 | 200 ticks | 650 | 325 | 5 |
| Qi Refining IV -> Qi Refining V (ordinary) | 1400 | 140 ticks | 700 | 350 | 0 |
| Qi Refining V -> Qi Refining VI (ordinary) | 1500 | 160 ticks | 750 | 375 | 0 |
| Qi Refining VI -> Qi Refining VII (bottleneck) | 1600 | 240 ticks | 800 | 400 | 5 |
| Qi Refining VII -> Qi Refining VIII (ordinary) | 1700 | 180 ticks | 850 | 425 | 0 |
| Qi Refining VIII -> Qi Refining IX (ordinary) | 1800 | 200 ticks | 900 | 450 | 0 |
| Qi Refining IX -> Early Foundation (bottleneck) | 1900 | 400 ticks | 950 | 475 | 10 |
| Early Foundation -> Middle Foundation (ordinary) | 3000 | 200 ticks | 1500 | 750 | 0 |
| Middle Foundation -> Late Foundation (ordinary) | 3600 | 240 ticks | 1800 | 900 | 0 |
| Late Foundation -> Early Golden Core (bottleneck) | 4200 | 600 ticks | 2100 | 1050 | 20 |
| Early Golden Core -> Middle Golden Core (ordinary) | 6000 | 300 ticks | 3000 | 1500 | 0 |
| Middle Golden Core -> Late Golden Core (ordinary) | 7200 | 360 ticks | 3600 | 1800 | 0 |

The result column assumes advancement starts at the ordinary stage cap. Runtime
always retains integer-floor half of actual current stability; it does not
subtract a fixed absolute cost.

Ordinary advancement loses no stability when interrupted. A player/world
interruption applies only the authored bottleneck loss in the table, clamped
at zero; clean server shutdown or registry-reload teardown has no penalty. One
completed process advances exactly one stage. Late Golden Core is the release
ceiling. Nascent Soul player advancement, pills, facilities, environment rules,
tribulation, and reincarnation remain deferred.

Foundation stages have spiritual-power capacities `200/250/300`; Golden Core
stages have `400/500/600`. Increased maximum health, power and lifespan are
resolved from the new stage/realm without healing wounds, refilling power or
resetting consumed age. Existing saves keep their original IDs, counters and
profile v4; no payload shape changes. Numeric defaults are tunable in the
authored realm JSON and do not imply owner approval of progression pacing.

Lifespan exhaustion is a derived state, not a death loop. At or beyond the
current realm maximum the player cannot start meditation or advancement and sees
an explicit status, but the profile is not cleared and the system does not kill
the player. Lifespan continues to be counted monotonically while otherwise
eligible.

All cultivation commands inherit the existing `/myvillage` permission-level-2
requirement. The existing administrator surface remains available:

```mcfunction
/myvillage cultivation info [target]
/myvillage cultivation reset <target>
/myvillage cultivation setrealm <target> <realm_id> <stage_id>
/myvillage cultivation setprogress <target> <amount>
/myvillage cultivation setstability <target> <amount>
/myvillage cultivation setpower <target> <amount>
/myvillage cultivation setroot <target> <metal> <wood> <water> <fire> <earth>
/myvillage cultivation clearroot <target>
/myvillage cultivation learn <target> <technique_id>
/myvillage cultivation forget <target> <technique_id>
/myvillage cultivation setmastery <target> <technique_id> <amount>
/myvillage cultivation core <target> <technique_id>     # run another learned core technique (心法); 0.37.0
/myvillage cultivation manual <target> <technique_id>   # give that technique's manual (秘籍); 0.38.0
```

`setstability` accepts a non-negative integer. The profile schema has no fixed
`100` ceiling; ordinary gameplay derives the current stage cap from half of its
progress cap.

The normal-rules initiation actions expose all eight English/pinyin routes:

```mcfunction
/myvillage cultivation awaken [target]
/myvillage cultivation juexing [target]
/myvillage xiulian awaken [target]
/myvillage xiulian juexing [target]
/myvillage cultivation initiate [target]
/myvillage cultivation rumen [target]
/myvillage xiulian initiate [target]
/myvillage xiulian rumen [target]
```

Omitting `target` uses the executing player. Awakening routes accept no seed,
element, affinity, count, reroll, force, or bypass argument. Inheritance routes
always target `myvillage:basic_breathing` and accept no technique id or bypass.
Both command roots continue to accept either literal in every pair:

| English | Pinyin |
|---|---|
| `info` | `chakan` |
| `reset` | `chongzhi` |
| `setrealm` | `shezhijingjie` |
| `setprogress` | `shezhixiuwei` |
| `setstability` | `shezhiwendingdu` |
| `setpower` | `shezhilingli` |
| `setroot` | `shezhilinggen` |
| `clearroot` | `qingchulinggen` |
| `learn` | `xuexi` |
| `forget` | `yiwang` |
| `setmastery` | `shezhishuliandu` |
| `core` | `xinfa` |
| `manual` | `miji` |
| `awaken` | `juexing` |
| `initiate` | `rumen` |

Run the complete playable-loop and affinity/UI revision handoff gates:

```bash
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 tools/validate_spirit_stone_resources.py
python3 tools/validate_cultivation_lifespan.py
python3 tools/validate_cultivation_meditation.py
python3 tools/validate_cultivation_gain.py
python3 tools/validate_cultivation_advancement.py
python3 tools/validate_guideme_cultivation_guide.py
python3 tools/validate_mod_items.py
python3 -m unittest discover -s tools/tests -p 'test_validate_*.py'
./gradlew test
./gradlew build
python3 tools/run_chunky_acceptance.py --stage 1
```

Stage 1 proves bounded dedicated-server startup, registration, datapack loading,
payload direction, and side safety only. It does not prove ore readability,
natural distribution, stele interaction, controls, exact interruption feel,
inventory consumption, H-screen layout, multiplayer clocks, persistence, or
advancement. Use a real client and record every unobserved item as
`not_verified`, never as an inferred pass.

The five original playable-loop changes and the affinity/UI revision are now
archived and synchronized into baseline specs. Validate those capabilities as
specs; do not run their old change names as though they were still active.

### Techniques And Heritages (0.37.0)

One technique catalogue feeds both the player's technique registry and the
world ledger: 129 techniques (心法 core, 绝技 active, 炼体 body; grades 黄 玄
地 天; 131 since 0.40.0 added two 身法 movement techniques) plus the hand-written 凡阶 Basic Breathing, four schools (流派: sword,
spear, fist, flying sword), and three heritages (传承: 太白剑脉, 青帝木脉,
万劫金身脉), each a chain of four techniques. Edit the source tables in
`tools/technique_catalogue/` and regenerate; the datapack files under
`data/myvillage/myvillage/{technique,school,heritage}/`, the ledger's
`techniques.json` and `heritages.json`, and the technique, school, and heritage
names in both language files are its output:

```bash
/usr/bin/python3 tools/gen_technique_catalogue.py           # write what differs
/usr/bin/python3 tools/gen_technique_catalogue.py --check   # also a release-gate step
```

One core technique (心法) runs at a time; initiation leaves Basic Breathing
running. The running one scales meditation progress (not stability, mastery,
or stone cost) by its grade's cultivation multiplier, plus the element bonus
when the spiritual root's affinity in one of its elements reaches the
threshold, all from `data/myvillage/world_sim/rules.json`; Basic Breathing
stays at ×1.0, so the numbers above are unchanged. A 玄阶 technique with a
matching root turns a normal batch of 10 into 14 and a spirit batch of 50 into
72. Switching to another learned core technique, from the 功法 page's
运转此心法 button or with

```mcfunction
/myvillage cultivation core <target> <technique_id>
/myvillage xiulian xinfa <target> <technique_id>
```

costs `techniques.switch_progress_loss` (0.3) of the current progress (散功),
except between two techniques of one heritage chain; switching to the running
one changes nothing. `info` shows the running core technique. Active and
body techniques can be learned and are listed, but have no effect yet; since
0.40.0 movement techniques drive the dodge key (see "Movement Dodge (0.40.0)").
Payload protocol is `10` (`11` since 0.38.0, `12` since 0.40.0), so client and
server need the same jar.

In the world ledger, some sects hold a heritage from genesis, `world sect`
and the 天下 sect detail show it as 传承, a sect that ends loses it, a later
founder who practises one of its techniques can revive it, and its manuals
turn up in ruins while it is lost. Details:
`docs/ai-kb/41_technique_system.md`; design: `docs/technique-system-brief.md`.

### Technique Manuals (0.38.0)

A technique is learned by reading its manual (秘籍). There are 16 manual
items, one per category and grade, and the technique a manual teaches is a
data component on the stack, like the enchantment on an enchanted book:

| Item | Category | Grades |
|---|---|---|
| `myvillage:manual_core_<grade>` | 心法 core, thread-bound book | `huang` 黄, `xuan` 玄, `di` 地, `tian` 天 |
| `myvillage:manual_active_<grade>` | 绝技 active, scroll | same |
| `myvillage:manual_movement_<grade>` | 身法 movement, folded book | same |
| `myvillage:manual_body_<grade>` | 炼体 body, jade slip | same |

The icon of a category is tinted by grade (yellow, blue, brown, pale gold);
rarity runs common, uncommon, rare, epic, and 天阶 manuals glint. A manual
with a technique is named `《功法名》` (`Manual: …` in English) and its tooltip
lists category and grade, school, elements, heritage position (such as
`太白剑脉 2/4`), requirement, `参悟 n%` once you have read some of it, and
`右键盘坐研读`. Without a technique it is a blank manual (`空白秘籍 · 绝技 ·
玄阶`); a technique that does not exist or does not match the item's category
or grade makes a red `残损秘籍` that cannot be read. The `myvillage:main`
creative tab lists the 16 blank manuals and then one manual per technique
(129; 131 since 0.40.0). Other ways to get one:

```mcfunction
/myvillage cultivation manual @s myvillage:gengjin_jianjue
/myvillage xiulian miji @s myvillage:gengjin_jianjue
/give @s myvillage:manual_active_xuan[myvillage:technique="myvillage:gengjin_jianjue"]
```

The command picks the right item for the technique (Basic Breathing, 凡阶, has
no manual). With `/give`, the item and the technique must agree or the manual
is damaged; add `myvillage:comprehension=<points>` to start part-read. The
icons come from `python3 tools/gen_manual_textures.py` (`--check` is a
release-gate step); never edit the PNGs.

Studying (研读) is a third kind of meditation. In survival or adventure, with
an awakened root and Basic Breathing learned, right-click a manual of a
technique you have not learned and whose requirements you meet (realm, stage,
element affinity; for a chain technique, the previous one learned). Each
refusal is a chat line. You sit down: 40 ticks of preparation, then every 10
ticks the manual gains `spiritual affinity × (1 + element bonus)` points,
rounded down; the bonus is 0.15 when your root has at least 15 % in one of
the technique's elements, and the grade does not multiply study. Keep the
manual in the slot you used it from; moving it ends the study. Higher grades
have gates: crossing one costs stability once, and with too little stability
the study stops on the gate (`参悟至此，神思不济`) until you have more. At the
total the technique is learned (a core technique starts running if none was), the
manual is used up, and the study ends. Anything that interrupts meditation
(moving, damage, the stop key `X`, the stop button, logging out) interrupts
study, and the points read so far stay on the manual; right-click again to go
on. Right-clicking a manual during another session first ends that session.

| Grade | Points | Gates (at points) | Stability per gate | Time at affinity 10 | With an element match |
|---|---:|---|---:|---:|---:|
| 黄 | 160 | none | 0 | 8 s | 7.5 s |
| 玄 | 12000 | 6000 | 50 | 10 min | about 9 min |
| 地 | 36000 | 12000, 24000 | 100 | 30 min | about 27 min |
| 天 | 96000 | 24000, 48000, 72000 | 150 | 80 min | about 73 min |

Times are real time at 20 TPS without the preparation or gate stops. Add the
2-second preparation: grade-one manuals now take about **10 seconds** at
default affinity 10 without a match (9.5 seconds with a match). Higher grades
keep their existing requirements. Affinity zero still gains no study points;
this changes manual study, not ordinary cultivation gain. Old manuals keep
their comprehension, and one already at or above 160 points finishes on its
next eligible settlement instead of being reset. The
numbers are data (`study_by_grade` in `tools/technique_catalogue/rules.json`,
written into each technique file). 地 and 天 techniques need Foundation early,
which play cannot reach in this release. While you study, the 修炼 page shows
a 研读《功法名》 card and the session reads 研读中. The meditation status
carries the study progress, so the payload protocol is `11`. Details:
`docs/ai-kb/41_technique_system.md` ("Manuals and study"); brief:
`docs/technique-manual-brief.md`.

### In-Game Acceptance

Use a disposable world copy for time-scale and exhaustion checks. Build and
install the same jar on client and server, keep the default scale for the main
pass, and record the exact game/version/config used.

1. Use the three spirit-resource `/give` commands above. Confirm inventory icons,
   names, hand scale, both placed block textures, and distinction from ordinary
   stone/deepslate. Confirm all three entries appear in `myvillage:main`.
2. In survival, break both ores with an under-tier/wrong tool, an iron pickaxe,
   a Silk Touch iron pickaxe, and a Fortune pickaxe. The wrong tool yields no
   resource, iron yields low-grade stones, Silk Touch yields the matching block,
   and repeated Fortune trials produce bonuses without changing the drop item.
3. Explore newly generated Overworld chunks at the upper, middle, and deep bands.
   Confirm both stone targets occur and that old generated chunks are unchanged;
   do not infer distribution from `/give` or placed blocks.
4. Run `/myvillage cultivation reset @s`, then test the testing stele and the
   inheritance stele as separate actions. Confirm awakening does not teach the
   technique, inheritance does not reroll the root, and repeat use is idempotent.
5. Open `H`. Confirm the Profile, Meditation, and Techniques pages remain within
   the panel at normal and constrained GUI scales (below 427x240 GUI pixels a page
   stacks its cards and the body scrolls). Profile must show schema 4, affinity 10,
   calendar, lifespan, realm/stage, progress/cap, stability/current-stage cap,
   power, root, and mastery without displaying legacy reserve. Meditation must
   show status, normal and spirit results, source-stage cost, inventory count,
   locked/active/capped stability state, and four stable buttons without treating
   displayed values as authority.
6. On stable ground use both the Normal button and `V`; remain still through the
   40-tick preparation and confirm one bounded start each. Repeat parity checks
   for Spirit/B, Stop/G, and Advance/N. Rotate the camera, switch H tabs, and
   close H without interruption or an implicit stop, then separately verify
   movement, jump, positive damage, attack/swing, mining, item/block/entity use,
   mount, swim/flight/sleep, mode change, dimension change, death, logout, and
   `G` each end a session once. Verify recent damage blocks restart for 100 ticks.
7. Confirm default-affinity normal meditation adds exactly 10 progress per ten
   active ticks. Prepare sensed, Qi-I, Qi-II, and Qi-III profiles and confirm one
   funded spirit batch adds 50 while removing exactly `1/1/2/3` stones across
   ordinary inventory slots. Confirm a final partial-cap batch pays the full
   cost and clamps output, an already capped batch costs nothing, and an
   underfunded batch removes nothing, applies the normal affinity result, and
   downgrades once. Before progress is full, confirm stability never changes,
   including the batch that fills progress. On the next normal and spirit
   batches, confirm stability gains current affinity, consumes no stone, and
   clamps at the current data-defined stability cap; mastery must remain at 10 per configured year.
   Legacy reserve must remain unchanged and inert.
8. Use the administrator setters to prepare each row of the advancement table.
   Press `N`, remain eligible for its exact duration, and confirm one transition,
   zero progress, integer-floor half of prior stability, and preservation of root, power,
   techniques, mastery, lifespan, affinity, and inert reserve. Interrupt an ordinary attempt and
   confirm zero loss; interrupt Qi III -> IV and confirm exactly five stability
   loss. Repeat at the Qi VI, Qi IX and Foundation-late bottlenecks using their
   authored losses. At Late Golden Core, `N` and cultivation gain must report
   the release limit. Across major realms confirm new health/power/lifespan
   maxima without refilling or resetting the existing counters.
9. With one survival/adventure player, observe shared calendar and personal age
   advance independently of sleep and `/time set`. Switch the only player to
   creative/spectator and confirm the shared calendar keeps advancing while
   personal age pauses; disconnect the only player and confirm both pause. With
   a second survival/adventure player online, confirm the shared calendar
   advances while the creative/spectator or offline first player's personal age
   does not. Separately verify reconnect,
   dimension change, death/respawn, ordinary save/restart, and clean-stop flushes
   without double age. Also wait through one 600-tick interval to verify the
   periodic batch path separately.
10. In a backed-up disposable copy, change `cultivation_time.ticks_per_day`,
    `days_per_year` or `days_per_week` and reload/restart. Confirm the operator warning and immediate
    reinterpretation of raw history. A `1/1` scale can reach the mortal limit
    quickly: exhaustion must block `V`, `B`, and `N` with a clear status, must not
    kill the player or clear the profile, and restoring the old scale may make the
    same raw counter non-exhausted again.

Historical owner real-client verdict recorded on 2026-07-13: `pass` for the
original Qi-IV slice only. These passes do not cover the new 0.48.0 stages.

| Manual acceptance surface | Result |
|---|---|
| Three item/block assets and creative-tab exposure | `pass` |
| Iron-tier, Silk Touch, Fortune, and wrong-tool loot | `pass` |
| Upper/middle/deep generation in new Overworld chunks | `pass` |
| Separate testing/inheritance stele flow and repeat behavior | `pass` |
| H Profile/Meditation tabs, text fit, values, buttons, and status feedback (the two-tab screen through 0.30.0) | `pass` |
| V/B/N and H-button parity, preparation, camera movement, and interruptions | `pass` |
| Default X stop key after releasing GuideME G | `not_verified` |
| Affinity progress, `1/1/2/3` direct costs, rollback, cap, and downgrade | `pass` |
| Pre-cap stability lock, post-cap affinity gain, no stone cost, and `500/550/600/650` caps | `pass` |
| `1000/1100/1200/1300` advancement rules, stability halving, interruption, Qi-IV ceiling | `pass` |
| Shared calendar, personal online lifespan, lifecycle persistence, multiplayer | `not_verified` (the 2026-07-13 `pass` covered the old rule; on 2026-10-06 the shared calendar began advancing in every game mode, so step 9 must be redone in game) |
| Config reinterpretation warning and non-lethal exhaustion | `pass` |

The 0.48.0 progression extension has its own ledger:

| Surface | Developer evidence | Owner verdict |
|---|---|---|
| Complete fifteen-transition chain and cross-realm state preservation | `pass` (mapped N, 15 successive stages; admin near-cap fixtures) | `not_verified` |
| Golden-Core-late terminal and direct-world save/restart | `pass` (V/B/N refused; stage, stability, wounds and age preserved) | `not_verified` |
| Higher-stage spirit costs and interruption losses | `pass` (V/B at every source, exact inventory costs; all four bottleneck losses) | `not_verified` |
| Higher-stage H values, bilingual text and guide rendering | English 480x270 terminal panel and English guide index observed; remaining surfaces `not_verified` | `not_verified` |
| New-stage death/End/dimension and multiplayer behavior | `not_verified` | `not_verified` |
| Progression pacing and combat feel | `not_verified` | `not_verified` |

For bounded developer reproduction, run clean stage 1 first, then
`python3 tools/golden_core_evidence.py`; the script uses mapped client controls
with administrator-prepared near-cap fixtures, and keeps two sessions for a
direct save/restart. It does not prove natural acquisition or the full time
needed to cultivate from zero. See [phase timings and evidence](docs/testing/2026-10-14-golden-core.md).

The 0.31.0 panel replaced the screen that verdict covered. Its own surfaces:

| 0.31.0 panel surface | Result |
|---|---|
| Three pages, header, footer, and rail: layout, text fit, and look | `not_verified` |
| Four action buttons on the Meditation page and their status feedback | `not_verified` |
| Hover, focus, wheel scrolling, and reopening on the last page | `not_verified` |
| 0.32.0 meridian diagram on the Meditation page: figure, channels, qi flow per state, and look | `not_verified` |
| 0.36.1 year/week/day dates in the header, the 内视 calendar card, and the 天下 总览 row: values and text fit at 320x240 | `not_verified` |

Nothing of the technique system has been looked at in a real client yet; the
automated checks are the catalogue `--check`, the world-sim and five
cultivation validators, and the Gradle tests.

| Technique system (0.37.0) surface | Result |
|---|---|
| Catalogue generator `--check`, datapack/ledger cross-checks, definition decoding, switch rules, progress factor, payload codec, page grouping, meridian route, heritage genesis/loss/rekindling, save round trips | `pass` (automated) |
| 功法 page: 心法/绝技/身法/炼体 cards, chips, and text fit at GUI 480x270, 427x240, and 320x240 in Chinese and English | `not_verified` |
| 运转此心法 button switches the running core technique; 运转中 moves; hover and click | `not_verified` |
| 散功 on switch: progress drops by 30 % outside a chain, not at all inside one | `not_verified` |
| `/myvillage cultivation core` / `xiulian xinfa` and the `info` line on a server | `not_verified` |
| Meditation gain with a graded running core technique (for example 14 per normal batch at 玄阶 with a matching root) | `not_verified` |
| Meridian diagram unchanged with Basic Breathing and with a catalogue core technique running | `not_verified` |
| 天下 sect detail 传承 row | `not_verified` |
| `/myvillage world sect <id>` 传承 line | `not_verified` |
| Old save migration: profile v3 → v4 (Basic Breathing running) and world-sim payload version 1 → 2 | `not_verified` in game (unit-tested) |
| Heritage chronicle lines (genesis, lost, rekindled, ruin find) in a real client and as rumors | `not_verified` |
| The owner's verdict on the page and the switch rules | `not_verified` |

Nothing of the technique manuals has been looked at in a real client yet; the
automated checks are the catalogue and manual-texture `--check`s, the
cultivation validators, and the Gradle tests (item rules, study start checks,
settlement and gates, status codec, panel readouts).

| Technique manuals (0.38.0) surface | Result |
|---|---|
| Manual validity, names, creative variants, study numbers, start checks, settlement, gates, completion, status codec, command, panel readouts | `pass` (automated) |
| The four icons and their grade tints, rarity colours, and the 天阶 glint in the inventory and in hand | `not_verified` |
| Tooltip lines (category · grade, school, elements, heritage, requirement, `参悟 n%`, use hint) and the blank and damaged names | `not_verified` |
| Creative tab: 16 blank manuals, then the 129 technique manuals | `not_verified` |
| Right-click starts a study (preparation, then 研读中) without a hand swing; each refusal message | `not_verified` |
| Interruption (move, damage, `X`, logout) keeps the points on the manual; right-click continues | `not_verified` |
| Moving the manual out of its slot stops with the manual-lost message | `not_verified` |
| Gate: stability paid once when enough; stop on the gate with the cost and shortfall message when not | `not_verified` |
| Completion learns the technique, consumes the manual, and a core technique starts running if none was | `not_verified` |
| 修炼 page study card (title, bar, percent, gate line, stop hint) at GUI 480x270, 427x240, and 320x240 in Chinese and English | `not_verified` |
| `X` and the stop button end a study; the start buttons are disabled while studying | `not_verified` |
| `/myvillage cultivation manual` / `xiulian miji` and the `/give` component form | `not_verified` |
| The owner's verdict on the study durations, the gate (神思) rule, and the art | `not_verified` |

Use only `pass`, `fail`, or `not_verified`. A `fail` records the observed mismatch
and reproduction steps; `not_verified` means the surface was not directly
observed and does not block truthful reporting of automated results.

Profile, time, and session status travel only server-to-client. The only two
client-to-server cultivation payloads are the bounded meditation/advancement
intent enum used by both keys and H buttons, which carries no identity,
coordinate, velocity, affinity, resource count, rate, profile value, target
stage, or result, and (0.37.0) the core-technique switch, which carries only a
technique id; the server decides whether the switch is allowed and what it
costs. A study (0.38.0) starts by using a manual, which is ordinary item use,
and stops with the existing stop intent. Client caches and button state are presentation-only and clear on
disconnect. The 天下 page's world query (0.36.0) is a separate read-only
payload of the world simulation, not a cultivation payload.

The serial dependency is intentional: spirit resources -> profile v3/time ->
meditation state -> affinity/direct-stone Basic Breathing settlement ->
advancement. Later work must
treat Nascent Soul player advancement, additional technique execution,
pills/facilities, combat/exploration rewards, and reincarnation as separate
boundaries. Spiritual-power recovery is already implemented (note 45).

## Available Commands

The v0.11 mod registers debug commands for structure validation, the
on-demand living-town generator, and the terraced sect-compound generator, plus
a custom worldgen `myvillage:sect` structure that sites cultivation sects into
the world on their own (rare, biome-gated to high-relief biomes, world-seed
reproducible) and is locatable via `/locate structure myvillage:sect`. Town
worldgen is still not registered (a later change).

List loaded templates:

```mcfunction
/myvillage list
```

Generate a living cultivation town around the player:

```mcfunction
/myvillage town
/myvillage town 20260618
/myvillage townat 20260618 512 80 0
```

The optional seed makes generation deterministic for the same seed and site.
`townat` is the RCON/console-safe coordinate form; it uses the same planner and
realizer as `/myvillage town <seed>` but anchors at the explicit block position
instead of requiring a player.
It selects both the perimeter silhouette (square, 天圆 circle, oval, 半月
D-shape, octagon, or trapezoid, optionally with a barbican/bastion) and a
bounded orthogonal internal grid. Family review seeds include `4` (square), `5`
(circle), `2` (oval), `1` (D-shape), `11` (octagon), and `13` (trapezoid).
The town is a districted ~160×160 修仙坊市 (gate / market / residential / civic
core / fringe districts), force-loaded via chunk tickets so the whole footprint
generates in one command; regions that cannot be force-loaded are reported
rather than silently skipped. The civic core carries the ritual axis (plaza /
paifang gate / lantern approach) and a skyline of vertical landmarks
(`pagoda`, `pavilion`, `bell_drum_tower`) flanking the dominant `town_shrine`.
Market and residential parcels form continuous street-frontage row shops with
party walls and narrow alleys. Cultivation street life (幌子 banners,
药圃/灵田 spirit-field beds, 炼丹炉 alchemy furnaces, 法器摊 artifact stalls,
阵纹 formation floors) and villager/spirit-fox inhabitants are placed across
districts. Parcels above the slope limit are skipped and reported in the
completion message. If the optional decor mods are absent, authored mod blocks
in template palettes and runtime decor fixtures are substituted with generated
vanilla fallbacks.

Generate a terraced cultivation sect compound ascending away from the player:

```mcfunction
/myvillage sect
/myvillage sect 20260618
/myvillage sectat 20260618 -512 80 0
```

Force-generate a worldgen-style sect (with its derived mountain) at the player,
for review/testing, optionally selecting the detached-spire variant:

```mcfunction
/myvillage sect worldgen
/myvillage sect worldgen 20260618
/myvillage sect worldgen 20260618 pavilion_short_straight_east
/myvillage sectat worldgen 20260618 none -512 80 512
/myvillage sectat worldgen 20260618 pavilion_short_straight_east -512 80 512
```

`worldgen` builds the same compound resting on a mountain **derived from the
terrace profile** (反推山形): the terraces are fixed first, then the slopes
beneath/between them are noise-filled, an outer blend skirt grades the man-made
relief into the surrounding terrain (no cut-off edge), a sheer cliff face backs
the summit, a translucent cloud-sea (云海面) sheet floats between the gate and
disciple terraces, and — when the feature is present — a solitary peak (孤峰)
rises under the detached volume reachable only across the flying bridge. The
optional third argument forces one of the three detached-spire variants
(`pavilion_short_straight_east`, `pagoda_long_arched_west`,
`disciple_medium_angled_north`) or `none`; omitting it falls back to the
per-seed selection. In natural worldgen the same structure is sited
automatically and bakes into chunks (no force-load, no build pop-in); find one
with `/locate structure myvillage:sect`.
`sectat` and `sectat worldgen` are the coordinate-addressable RCON/console forms
and do not require a `ServerPlayer`.

The sect is a terraced axial 宗门 compound (gate / disciple / assembly /
scripture / summit terraces, count parametric 4–6) ascending a single fall-line
ritual axis from the mountain gate (山门) to the cliff-backed principal hall
(主殿). Slot importance grades with terrace level — the principal hall and
scripture pagoda hold the top tiers; flanking volumes (disciple-quarter rows,
paired pagodas, flanking bell/drum towers) mirror about the axis and are joined
by covered galleries (廊); each terrace meets the next through a retaining face
and an on-axis stair flight. The optional detached-spire flying-bridge (飞桥)
feature is selected per seed (one of three deterministic variants, or absent):
a detached volume sits on its outcrop reachable only by the flying bridge. The
footprint is force-loaded via chunk tickets; terraces are carved and retained
against the terrain so platforms step the slope with no floating or buried
slabs, and palette ids route through the mod-fallback resolver. The same seed
rebuilds the same compound. The exported terrace profile (elevations/bounds,
rise/depth/taper, axis-stair width, cliff-back height) is the contract the sect
worldgen consumes to derive the mountain (反推山形); the on-the-spot
`/myvillage sect [seed]` build is unchanged (it rests on the live surface, no
derived mountain).

Place the smoke-test structure at the player position:

```mcfunction
/myvillage place test_house_03
/myvillage placeat test_house_03 0 80 0
```

Place a generated building directly:

```mcfunction
/myvillage place small_house_001
/myvillage placeat small_house_001 0 80 0
/myvillage place medium_house_001
/myvillage place blacksmith_001
/myvillage place medium_shop_001
/myvillage place big_house_001
/myvillage place chinese_courtyard_001
/myvillage place chinese_mansion_001
/myvillage place chinese_mansion_002
/myvillage place chinese_mansion_003
/myvillage place chinese_mansion_004
/myvillage place chinese_mansion_005
/myvillage place chinese_mansion_006
/myvillage place chinese_huipai_mansion_001
/myvillage place chinese_huipai_mansion_002
/myvillage place ganlan_stilted_house_001
/myvillage place ganlan_stilted_house_002
/myvillage place hero_rockery
/myvillage place tavern_001
/myvillage place lord_manor_001
/myvillage place cultivation_town_001   # courtyard district-fill fragment (not the canonical town — use /myvillage town)
/myvillage place cultivation_inn_001
/myvillage place pagoda_001
/myvillage place pavilion_001
/myvillage place bell_drum_tower_001
/myvillage place sect_gate_001
/myvillage place scripture_pavilion_001
/myvillage place cultivation_sect_001
```

Plaque-bearing generated structures place shipped `myvillage` plaque blocks with
the bound inscription baked directly into the block textures. Notable review targets include
`tavern_001`, `lord_manor_001`, `cultivation_inn_001`, `pagoda_001`, `pavilion_001`,
`bell_drum_tower_001`, `sect_gate_001`, and `scripture_pavilion_001`.

For generated structures other than `test_*`, `/myvillage place` applies a
one-block downward Y offset before placement. This lets terrain-replacement
cells such as courtyard water, gravel paths, and entry hardscape replace the
ground block instead of sitting one block above it. If using vanilla commands
directly, place generated structures with the same offset:

```mcfunction
/place template myvillage:small_house_001 ~ ~-1 ~
/place template myvillage:chinese_courtyard_001 ~ ~-1 ~
/place template myvillage:chinese_mansion_001 ~ ~-1 ~
/place template myvillage:chinese_huipai_mansion_001 ~ ~-1 ~
/place template myvillage:ganlan_stilted_house_001 ~ ~-1 ~
/place template myvillage:tavern_001 ~ ~-1 ~
/place template myvillage:lord_manor_001 ~ ~-1 ~
/place template myvillage:cultivation_town_001 ~ ~-1 ~
/place template myvillage:cultivation_sect_001 ~ ~-1 ~
```

When `/myvillage place` substitutes optional-mod palette entries because a decor
mod is not loaded, the success line includes `fallback_substitutions=<count>`.
The coordinate form `/myvillage placeat <structure_id> <x> <y> <z>` applies the
same one-block Y offset for generated non-test structures and uses the same
runtime fallback resolver.

Place all loaded `myvillage` blueprints in a grouped gallery with 128-block
spacing:

```mcfunction
/myvillage gallery
/myvillage galleryat all 0 80 0
```

Place only the original non-cultivation structures, or only the cultivation
structures, in the same grouped gallery layout:

```mcfunction
/myvillage gallery original
/myvillage gallery cultivation
/myvillage galleryat original 0 80 0
/myvillage galleryat cultivation 0 80 0
```

The full gallery is arranged as columns by broad type: houses, shops,
blacksmiths, Chinese courtyard compounds, civic structures, cultivation town,
cultivation sect, Chinese review sub-buildings, tests, and other templates.
The `original` gallery keeps the non-cultivation columns, while the
`cultivation` gallery keeps the cultivation town and cultivation sect columns.
It is intended for side-by-side visual comparison across sizes and archetypes.
The `galleryat` variants keep the same grouping, filtering, ordering, spacing,
and fallback behavior but can be driven from RCON or the server console.

Staged Chunky/RCON automation:

Stage 1 waits for the current world task's new completion log entry before saving
and stopping; an initial idle response is not completion.

```bash
python3 tools/run_chunky_acceptance.py --stage 1   # Chunky/RCON/server lifecycle
python3 tools/run_chunky_acceptance.py --stage 2   # plus myvillage ...at command smoke
python3 tools/run_chunky_acceptance.py --stage 3   # plus full optional-mod preflight + cases
python3 tools/run_chunky_acceptance.py --stage 4   # plus locate myvillage:sect + bounded Chunky
python3 tools/write_visual_acceptance_report.py    # visual handoff checklist from previews + Chunky report
```

Stage 2 includes coordinate placement for both `small_house_001` and
`chinese_mansion_001`, then the cultivation gallery/town/sect smoke cases.
Stage 3 extracts `exmod/mod_jars.zip` into the isolated profile and verifies
both the expected optional mod ids and mandatory jar dependencies before the
server starts. The local staged zip must include all dependency jars required by
those mods, for example `architectury` for Fetzi's Displays.
Stage 4 runs only after Stage 3 passes; it locates a natural `myvillage:sect`
and runs a small bounded Chunky task around that site.
The visual report does not judge aesthetics automatically; it records the
representative preview PNGs and generated-world coordinates that the agent and
reviewer must inspect before visual acceptance is claimed.
Do not count headless Chunky renderer images as custom `myvillage:` block visual
acceptance evidence; custom blocks currently require client-side inspection.
When using the headless renderer for ordinary placed-world review, keep the
default multi-camera `--view-plan survey` unless a narrower diagnostic view is
intentional; use `--view-plan height-sweep` when angle/height could affect the
layout judgment. The renderer writes a manifest-linked `contact_sheet.png` by
default for quick multi-angle comparison.

Generated datapack functions are also available after resource generation:

```mcfunction
/function myvillage:gallery/medieval_village
/function myvillage:gallery/chinese_courtyard
/function myvillage:gallery/chinese_mansion
/function myvillage:gallery/chinese_huipai_mansion
/function myvillage:gallery/ganlan_stilted_house
/function myvillage:gallery/civic
/function myvillage:gallery/cultivation_town
/function myvillage:gallery/cultivation_sect
/function myvillage:place/chinese_courtyard_001
/function myvillage:place/chinese_mansion_001
/function myvillage:place/chinese_huipai_mansion_001
/function myvillage:place/ganlan_stilted_house_001
/function myvillage:place/tavern_001
/function myvillage:place/lord_manor_001
/function myvillage:place/cultivation_town_001
/function myvillage:place/cultivation_sect_001
```

Query the region runtime (passive — reads the per-seed region graph, overrides
no biome, writes nothing beyond the one-time world spawn):

```mcfunction
/myvillage spawn info
/myvillage spawn recompute
```

`/myvillage spawn info` (player-only) prints the computed spawn region and
bound spawn block, plus the caller's current region / tier rung / next-rung
region set. `/myvillage spawn recompute` (admin, permission 2) forces a spawn
recompute for the current world and calls `setDefaultSpawnPos`, **overriding
any existing spawn** — the documented admin-override path. The automatic
world-load binding otherwise runs once per world and preserves any existing
custom (admin-set) spawn rather than clobbering it.

## Generation Architecture

```text
Settlement Group     tools/buildgen/groups.py       style profile + archetype roster + layout strategy
Style Profile        tools/buildgen/styles/*.json   medieval_village / chinese_courtyard / cultivation_town / cultivation_sect
Building Archetype   tools/buildgen/archetypes.py   small_house / medium_house / blacksmith / shops / civic / cultivation town / cultivation sect
Massing Graph        tools/buildgen/massing.py      main + great hall + tower + wing + porch + chimney + shed nodes
Facade Grammar       tools/buildgen/facade.py       bay split, posts, jittered windows
Build Ops            tools/buildgen/ops.py          wall ops + registered roof/motif handlers
Pass + Protection    tools/buildgen/passes.py       ordered passes, mezzanine_floor_pass, tag/priority grid, PROTECTED cells
Quality Check        tools/buildgen/quality.py      entrance, windows, interior, mezzanine, belfry, gables, forms, forbidden blocks
Resource Export      tools/buildgen/export.py       structure NBT + gallery/place mcfunctions
Compound Graph       tools/buildgen/compound.py     Chinese one-courtyard, cultivation town block, and cultivation sect parcel layouts
Civic Generator      tools/generate_civic_library.py tavern_001..005 + lord_manor_001..003
Town Planner         tools/buildgen/town.py         deterministic enclosure/spine/parcels/negative-space model + validation
Town Realizer        src/main/java/.../town/        /myvillage town runtime site-fit, frontage, street-room, and tissue placement
```

Important properties:

- Materials resolve through abstract slots such as `BASE_STONE`, `WALL_MAIN`,
  `FRAME_WOOD`, `ROOF_DARK`, `DETAIL_WOOD`, `INTERIOR_WORK`,
  `INTERIOR_STORAGE`, `INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, and
  `HERALDRY`. External-mod decor uses semantic slots such as `ROOF_TILE`,
  `PAPER_LANTERN`, `RITUAL_ANCHOR`, and `MARKET_FITTINGS`; cultivation form
  geometry uses `COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, and `BALUSTRADE`.
  Each populated list keeps a final `minecraft:` fallback. Cultivation sect
  styles may also define `SPIRIT_CRYSTAL` and `RITUAL_METAL`; mortal styles may
  omit them.
- Families with non-vanilla blockstate grammar are oriented through the
  buildgen orientation adapter. Vanilla stairs/slabs and Supplementaries
  awnings are registered families; unregistered families fail loudly.
- Supported roof handlers are registered in `tools/buildgen/ops.py`. Current
  names include `gable_roof`, `cross_gable_roof`, `lean_to_roof`, Chinese
  roof-grade aliases, `sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, and
  `tiered_eave_roof`.
- Cultivation eave curvature is a real flying-eave (飞檐翘角) silhouette built
  from stair/slab geometry: the eave line droops at mid-span and swoops up
  toward each gable end, each eave side runs through a flat eave band (举折)
  before climbing to a level ridge, and the corners carry an upturned finial
  with an outward wing. A slot-resolved dougong/额枋 bracket course (`DETAIL_WOOD`
  `_fence`) sits under the deep overhangs. It does not require an Asian-decor
  curved-roof mod; optional mod blocks only skin the slot-resolved materials.
- Decoration motifs are also registered in `tools/buildgen/ops.py`; cultivation
  forms include `moon_gate`, `spirit_array`, `incense_altar`, `cloud_rail`, and
  `sect_gate_paifang`. Market styles may also enable `market_stall`.
- Add new settlement families through `tools/buildgen/groups.py`, and add new
  roof or motif forms by registering a handler before listing the form in a
  style's `allowed_roof_types` or `allowed_motifs`.
- The default medieval building-library archetypes are `small_house`,
  `medium_house`, `blacksmith`, `small_shop`, `medium_shop`, and `big_house`.
  Civic archetypes `tavern` and `lord_manor` are generated by the separate
  civic library loop.
- Chinese compound generation uses `tools/buildgen/styles/chinese_courtyard.json`
  and the `main_hall`, `side_wing`, `front_row`, and `gate_house` sub-building
  builders. These are composed by `CompoundGraph`, not emitted by the default
  medieval building-library generator.
- Cultivation town generation uses `cultivation_town.json` with the runtime
  town-generation layout. The runtime `/myvillage town` realizer produces a
  districted ~160×160 修仙坊市: named districts (坊门区 gate / 市肆区 market /
  民居坊 residential / 礼制核心 civic core / 边缘区 fringe), each carrying its
  own density, storey band, and material register from the group's `district_brief`.
  The ritual axis (plaza / paifang gate / lantern approach) is expressed inside
  the civic core, with the `town_shrine` as the sole dominant landmark. Market
  and residential parcels carry street frontage (party-wall row shops, shared
  gables, intentional narrow alleys) instead of centered-in-lot plinths. A
  skyline rule guarantees the civic core rises above the surrounding roofline
  through vertical-landmark archetypes — `pagoda` (塔), `pavilion` (楼阁), and
  `bell_drum_tower` (钟鼓楼) — built from the existing terrace + flying-eave
  vocabulary and placed flanking the shrine. Pagodas use deterministic
  five/five/seven-storey profiles with stepped taper, one bracketed eave per
  occupied level, a pyramidal crown, and profile-specific finial height; the
  compact profile stays in the fixed civic-core parcel while larger profiles
  are available to roomy parcels and sect placements. The footprint is
  force-loaded via chunk tickets so the whole town generates in one command.
- The static `cultivation_town_NNN` compound library is **district fill tissue**,
  not a standalone town: it supplies courtyard street-block material the
  residential and market districts draw from. `/myvillage place cultivation_town_001`
  remains available for placing a single courtyard fragment for review, but it is
  no longer the canonical cultivation town — `/myvillage town` is. Standalone
  cultivation-town buildings (`cultivation_house`, shops, inn, market,
  `town_shrine`, and the `pagoda`/`pavilion`/`bell_drum_tower` landmarks) are also
  generated because the runtime town places those templates directly. Cultivation
  town and sect buildings use cultivation massing grammar directly: raised
  platforms, entry colonnades with dougong brackets, sweeping/hip/pyramidal/tiered
  roofs, pagoda finial spires, belfry bells, pavilion balconies, a built three-bay
  mountain gate, and a furnace feature for alchemy rooms. Cultivation sect
  generation uses `cultivation_sect.json` with standalone sect archetypes plus a
  terraced axial mountain-compound layout: four stacked terrace courtyards,
  monumental stairways, summit hall/pagoda hierarchy, a water/cliff/cloud siting
  context, and structural covered-gallery/flying-bridge link nodes.
- Town building graphs expose frontage metadata (`side`, `facing`, and opening
  cells) and optional importance-tier hints used by the town planner/realizer.
- Chinese courtyard water and gravel/path cells are authored as
  terrain-replacement cells one layer below the structure origin. Planting stays
  on the plant layer, and bamboo is sampled around water with supporting dirt
  where needed.
- Multi-story buildings use aligned floor openings and stairwell metadata.
  `mezzanine_floor_pass`, `floor_slab_pass`, and `stair_pass` run after
  `structure_pass`.
- Generated building entry hardscape is lowered to the stair's lower layer so
  random path blocks do not sit flush with the doorway stair. Porch posts extend
  down to the lowered hardscape layer.
- `clear_inside` runs before roof generation and only carves the interior wall
  volume. Roofs and gable cells are generated later and protected by the pass
  pipeline.
- Current `myvillage` and `medieval_village` names are implementation labels,
  not the final scope of the project. Future work is expected to expand from
  the current building library toward richer town generation.

## Current Scope

Included:

```text
- JSON DSL validation and JSON -> vanilla structure NBT conversion
- Batch generation into NeoForge Mod resources
- 45 medieval_village building-library structures
- 6 generated Chinese courtyard compound structures
- 8 generated civic structures (`tavern_001..005`, `lord_manor_001..003`)
- 6 generated cultivation town block structures (district fill tissue)
- 24 generated standalone cultivation town structures (incl. `pagoda`/`pavilion`/`bell_drum_tower` landmarks)
- 10 generated standalone cultivation sect structures
- 2 generated cultivation sect compound structures
- 105 generated NBT structures in the default batch, including `test_house_03.nbt`
- sect compound placement metadata under `data/myvillage/settlement_meta/`
- test_house_03.nbt Mod resource smoke test
- /myvillage place <structure_id>
- /myvillage placeat <structure_id> <x> <y> <z>
- /myvillage list
- /myvillage town [seed]
- /myvillage townat <seed> <x> <y> <z>
- /myvillage sect [seed]
- /myvillage sect worldgen [seed] [variant]
- /myvillage sectat <seed> <x> <y> <z>
- /myvillage sectat worldgen <seed> <variant|none> <x> <y> <z>
- /myvillage gallery
- /myvillage gallery original
- /myvillage gallery cultivation
- /myvillage galleryat <all|original|cultivation> <x> <y> <z>
- a custom `myvillage:sect` worldgen Structure: sects are sited during world generation, biome-gated by `tags/worldgen/biome/has_sect`, spaced by `worldgen/structure_set/sect`, and `/locate`-able, resting on a mountain derived from the terrace profile (反推山形)
- generated optional-mod runtime fallback map and fallback coverage validation
- `myvillage:simple_fox`: vanilla-model custom entity, spawn egg, empty first-pass loot table, and low-weight taiga natural spawning
- `myvillage:demon_wolf` (妖狼): hostile beast with its own generated model and clips and two data-driven moves; summon and spawn egg only, no drop
- /myvillage beast move <targets> <move_id> | status <targets> | debug on|off
- the world ledger (命簿): sects and named cultivators simulated by the day, saved per world, with chat rumors
- /myvillage world [info] | sects [all] | sect <id|name> | sect <id> build [here] | sect <id> join|leave <player> | sect <id> rank <player> <rank> | sect <id> shelves [place] | sect <a> war <b> | sect <id> destroy | player <player> | gates [retry] | person <name> | chronicle [1-50] | here | pause | resume | advance <1-3650>
- player sect entry (0.41.0): a player's ledger record, joining and leaving at the gate steward (守山执事), yearly promotion, the 我的宗门 card, framed gate building near a player; the scripture hall (0.42.0): scripture shelves (经架) in a ledger compound's pavilions lending sect manuals by rank; sect tasks and apprenticeship (0.43.0): one task a year from the steward for contribution, a master whose guidance speeds meditation; world response (0.44.0): news of the player's sect in chat anywhere, hostile sects refuse visitors
- the H panel's 天下 page: the world ledger read-only for every player (overview, sects, people search, chronicle, here), with drill-down into sects and people
- world-sim avatars: ledger members shown as never-saved, invulnerable cultivators on a built compound's courtyard while a player is near
- `myvillage:rideable_flying_sword`: transient, one-player, server-authoritative flying-sword vehicle and creative-tab item
- NBT integrity validation for roof/top-layer/function-block/signature checks
- deterministic town-plan and sect-plan/sect-generation validation with top-down previews
```

Not included:

```text
- passive/natural *town* worldgen (towns remain command-built via `/myvillage town`; only sects are sited during world generation)
- jigsaw / template-pool generation (the sect structure is a hand-written `Structure`, not a jigsaw template pool)
- reward-bearing entity loot or complex authored block-entity NBT
```

Future direction:

```text
- multiple town/settlement categories rather than one simple village type
- more house types across sizes, styles, and roles
- functional buildings such as shops, workshops, storage, markets, services, and more civic pieces
- roads, props, districts, and layout rules for coherent town generation
- possible NPC/villager-related behavior once runtime and data support exist
```

## Known Issues And Visual Review Notes

The `mc-modtest-codex` candidate branch previously produced visible empty-roof
or roof-hole results. During integration, do not blindly reuse that branch's
roof generation logic. Pay special attention to:

```text
- whether roof layers contain non-air blocks
- whether gable ends are visually sealed
- whether clear_inside accidentally removes roof or gable material
- whether high layers in each structure are empty
- whether stairs and slabs face the correct direction
- whether gallery dimensions make roof defects easier to compare
```

The automated validators check the mechanical parts of this list, but final
acceptance still needs a v0.9 mod jar plus in-game visual inspection with
`/myvillage list`, `/myvillage town 20260618`,
`/myvillage place chinese_courtyard_001`,
`/myvillage place tavern_001`, `/myvillage place lord_manor_001`,
`/myvillage place cultivation_town_001` (courtyard fragment),
`/myvillage place cultivation_inn_001`,
`/myvillage place pagoda_001`, `/myvillage place pagoda_002`,
`/myvillage place pagoda_003`, `/myvillage place pavilion_001`,
`/myvillage place bell_drum_tower_001`,
`/myvillage place sect_gate_001`,
`/myvillage place scripture_pavilion_001`,
`/myvillage place cultivation_sect_001`, `/myvillage gallery`,
`/myvillage gallery original`, and `/myvillage gallery cultivation`. The
simple-fox slice completed its summon, spawn-egg, save/reload,
natural-frequency, multiplayer, and multi-view Minecraft acceptance on
2026-07-12.


### Innate Fates / 先天气运

Focused cloud build: `.venv-modal/bin/python -m tools.modal_runner run --preset fates --ref HEAD --timeout 1800`. This runs fate tests and builds the jar, without a game or full release suite.

Right-click **Spirit Testing Stele / 测灵碑** to open the independent awakening
screen. It shows your spiritual root first; continue in the same screen to choose
three of six fate candidates, then click Confirm and Finalize. The first visit
creates candidates automatically. Closing and right-clicking again resumes the
same draft and lock without rerolling. Existing awakened saves may choose once;
confirmed characters see their stored result. **H → Fates / 先天** only shows the
result or directs you back to the stele. The inheritance stele remains a separate
learning step. Existing roots are preserved; explicit rerolls only change fate
candidates. The pool now contains fifteen working traits and 11 purple/gold/red placeholders.
Placeholders are explicitly labeled, can be selected and saved, and grant no effects;
they temporarily have no element/purity gate. Ten lower-tier designs remain deferred.
All six tier weights are equal (within each available tier, candidates are uniform).
Two ordinary candidates remain guaranteed, so the final six cards are not uniformly
distributed across tiers. Existing drafts/confirmed choices are preserved: restart
the game/server and explicitly reroll an unconfirmed draft to use the new pool.
The [36-trait catalogue](docs/innate-fates-design.json) distinguishes these states.

| Trait | Runtime ID | Tier / points |
|---|---|---|
| 身强体健 / Robust | `myvillage:robust` | white / 1 |
| 脚步轻快 / Lightfoot | `myvillage:lightfoot` | white / 1 |
| 天资聪颖 / Bright Aptitude | `myvillage:bright` | white / 1 |
| 亲火 / Fire Affinity | `myvillage:fire_affinity` | green / 2 |
| 丹师之后 / Alchemist Descendant | `myvillage:alchemist_descendant` | green / 2 |
| 猎妖老手 / Beast Hunter | `myvillage:beast_hunter` | blue / 3 |
| 不屈 / Unyielding | `myvillage:unyielding` | blue / 3 |
| 燃命凶星 / Burning Star | `myvillage:burning_star` | purple / 4 |
| 经脉通达 / Open Meridians | `myvillage:open_meridians` | purple / 4 |
| 吞丹化势 / Pill Aegis | `myvillage:pill_aegis` | purple / 4 |
| 百炼战骨 / Tempered Bones | `myvillage:tempered_bones` | purple / 4 |
| 剑痴 / Sword Obsession | `myvillage:sword_obsession` | purple / 4 |
| 踏风步 / Wind Step | `myvillage:wind_step` | blue / 3 |
| 先天剑心 / Innate Sword Heart | `myvillage:innate_sword_heart` | gold / 5 |
| 火灵之体 / Fire Spirit Body | `myvillage:fire_spirit_body` | gold / 5 |

0.60.1 activates three previously selectable placeholders: Open Meridians raises qi capacity and refunds one successful paid cast; Pill Aegis enhances Vitality Pills and grants an independent temporary shield; Tempered Bones converts hostile health loss into one enhanced melee hit and knockback. Existing selected IDs work after upgrading without rerolling. See [purple-fate validation](docs/testing/2026-10-17-purple-fates.md) for actual cloud coverage and limits.

0.60.2 activates Sword Obsession: select it at the testing stele, equip the Qingfeng Sword, enter cultivation combat mode and land the ordinary combo. Each move earns at most one momentum stack; a full-stack finishing hit adds one independent sword wave. Missing for the configured timeout or changing weapons clears momentum. Active/derived sword qi does not earn stacks. See [sword-fate verification](docs/testing/2026-10-17-sword-obsession.md).

0.61.0 adds three combat fates. **Innate Sword Heart** rewards a real perfect dodge with one critical sword hit and a piercing wave. **Wind Step** allows one air dodge per landing and briefly boosts speed after dodging: learn a movement technique, use the existing dodge key (Left Alt), and pay that technique's qi cost only in the air; ground dodges remain free. It excludes Lightfoot. **Fire Spirit Body** adds attributed burning to direct health damage and triggers a target-only burst on repeated hits. Saved cooldowns survive death/reconnect, and derivatives do not recursively trigger offensive fates. See [advanced-fate verification](docs/testing/2026-10-17-advanced-fates.md).

The awakening screen and H result page display current effect values from synchronized data. Locking preserves
one candidate but does not force selection. Confirmed choices cannot be rerolled
by ordinary players, including after death or reconnect. Budget is server-owned:
set `[innate_fates] point_budget = 8` in `config/myvillage-server.toml`
(3–18); a matching file in the world's `serverconfig/` overrides it. The default
is 8. Since 0.49.2 high-tier choices have no separate count cap: select exactly
three within the budget and respect group exclusions. Two 4-point fates plus
one 1-point fate require a budget of at least 9.
A changed budget does not revoke confirmed builds. A datapack-disabled/missing
confirmed definition stops granting its effect without silently replacing it.

Ordinary roll/lock/confirm commands require your live testing-stele interaction,
including when you are an operator. Stay alive, in the same dimension and within
vanilla block interaction range of the same loaded stele. Closing the screen,
teleporting, logout, death, dimension change or reset ends that session; removing
the stele or leaving its range also invalidates it. Right-click again to resume.
`awaken`/`juexing` commands awaken the root only; they do not replace stele entry.
Both `cultivation` and `xiulian` accept both
`fate` and `mingge`; each subcommand has the following structurally identical alias:

| English | Pinyin | Use |
|---|---|---|
| `status [player]` | `chakan [player]` | Inspect own state; targeting another player requires operator permission. |
| `roll` / `roll <player>` | `chongzhi` / `chongzhi <player>` | Self form requires a live stele session. Explicit target form is an operator diagnostic fixture. |
| `lock <slot>` | `suoding <slot>` | Live stele session required. Slots 1–6; 0 unlocks. |
| `confirm <mask>` | `queren <mask>` | Live stele session required. Three bits from slots 0–5; prefer the awakening screen. |
| `confirm <player> <mask>` | `queren <player> <mask>` | Operator diagnostic fixture without the session requirement; normal fate eligibility still applies. |
| `reset <player>` | `qingchu <player>` | Operator-only removal/reselection; invalidates the session and preserves cooldown deadlines. |

```mcfunction
/myvillage cultivation fate status
/myvillage xiulian mingge chongzhi
/myvillage cultivation fate lock 1
/myvillage cultivation fate lock 0
/myvillage cultivation fate confirm 7
/myvillage cultivation fate reset @s
```

Mask 7 selects the first three slots and is rejected if the actual combination is
invalid. The explicit targeted `roll` and `confirm` forms check permission level 2
on the executing command source; they are administrator fixtures, not normal
player entry. Each action is server-throttled. There is no client-provided seed, cost,
effect number, health, mana or cooldown. See [KB 48](docs/ai-kb/48_innate_fates.md).

```bash
/usr/bin/python3 tools/validate_innate_fates.py
/usr/bin/python3 -m unittest tools.tests.test_validate_innate_fates
./gradlew test --tests 'com.example.myvillage.cultivation.*' --tests 'com.example.myvillage.client.cultivation.*'
/usr/bin/python3 tools/release_gate.py
/usr/bin/python3 tools/validate_innate_fates.py --jar build/libs/myvillage-0.84.3.jar
python3 tools/run_chunky_acceptance.py --stage 1
python3 tools/innate_fates_evidence.py --ui-only --out out/preview/stele_awakening
```

The recommended `--ui-only` run covers actual fresh testing-stele input,
root-first presentation, draft resumption, confirmation and read-only H results.
It captures 18 images across English/Chinese and the three supported GUI sizes,
checks session closure, leaving range, removing the stele and death, then saves
and restarts to verify the root and confirmed draft remain unchanged while the
ephemeral session returns to zero. It skips the existing numerical effects
suite. Running `python3 tools/innate_fates_evidence.py` without `--ui-only`
runs the full stele UI plus existing effects and restart suite.

Run clean stage 1 before capture; it clears the shared acceptance directory.
The evidence script owns the heavy-work lock and uses mapped client input with
RCON fixtures/readback, not DevBridge. Do not run another Gradle/Minecraft job
while it is active. A numerical or scripted pass is not owner combat-feel acceptance.

The following 0.49.0 results are historical evidence for the original H-based
selection flow; they do not verify the revised awakening screen.

| Historical 0.49.0 acceptance surface | State |
|---|---|
| Data/rules/payload and persistence tests | pass: focused rules/codecs and validator mutations; full Java 999 |
| Clean acceptance server / fresh jar | pass: clean stage 1, final 57-step release gate, all 817 tested class files match the jar |
| Mapped H-page confirmation and six bilingual layouts | pass: actual mouse input; 12 draft/confirmed PNGs across six language/size combinations |
| Actual effects and same-world restart | pass: 23 checks; see [measured evidence](docs/testing/2026-10-15-innate-fates.md) for scope and retained retries |
| Owner balance, usability, effects feel | not_verified |
| Two-player multiplayer | not_verified |
| Other 28 planned traits and root rerolls | not_implemented |


Testing-stele awakening evidence (0.49.1 layout and interaction baseline):

| Acceptance surface | State |
|---|---|
| Stele opening/session authority and read-only H tests | pass: 263 focused Java, final 1008 full Java; 1205 Python run with 13 skips covered by preview tests, then 46 focused pass |
| 0.49.1 jar and clean acceptance server | pass: final build/jar checks 5/5, 831 classes and resources match; clean stage 1 before the client-only tooltip correction |
| Mapped root-first entry, draft resume, confirmation and H results | pass: final 29/29 capture checks; actual stele and screen input |
| Awakening screen at three sizes in English and Chinese | pass as developer layout evidence: 18 state PNGs plus six hovered tooltips, representative images inspected |
| Root/fate persistence and invalid-session rejection | pass: close/reopen retains draft/root; close, range, block removal and death reject self-command mutations; confirmed root/draft persist on restart and session becomes zero |
| Separate inheritance-stele real-click regression | not_verified |
| Owner appearance, usability, balance and combat feel | not_verified |
| Two-player multiplayer | not_verified |

See [phase timings, retained failures and evidence boundaries](docs/testing/2026-10-15-stele-awakening.md).
Current preview: [testing-stele awakening](http://43.156.135.198:8765/stele_awakening/index.html).

The 0.49.2 removal of the high-tier count cap passed 1009 Java tests, 46 focused
Python tests, 14 selected release checks and clean stage-1 acceptance. See
[rule-change evidence and phase timings](docs/testing/2026-10-15-fate-tier-cap.md).
The preview screenshots remain 0.49.1 layout evidence; their old high-tier-limit
caption has been removed in the current jar.


## Alchemy (炼丹, 0.75.0)

新增三品筑基丹、两品补基丹与两品凝元果；炼丹资质和灵果决定固定成丹品质。
使用、丹炉配方及获取方式见[筑基丹与补基丹](docs/features/foundation-pills.md)。
以下英文段落保留原三种基础丹药的说明。

One **青铜丹炉** (`alchemy_furnace`), six herb items, three learnable formula
sheets and three pill items form the first survival preparation loop. The furnace
is a modeled, placeable block with a saved inventory; it is not a spawned creature.
Recipes, time, output and medicine strengths are authored in
`src/main/resources/data/myvillage/alchemy/rules.json` (rebuild/restart after tuning).

### Survival acquisition and use

Craft a furnace with this pattern: `CIC / CFC / C C`, where `C` is a copper
ingot, `F` a vanilla furnace and `I` an iron ingot. Each herb below is made by
shapeless crafting its vanilla plant with one low-grade spirit stone. These **spirit-stone infusion** recipes provide starter planting material.
All six herbs can now be planted on Spirit Soil; wild herb generation is not added.

| Herb item | Chinese | Vanilla infusion input |
|---|---|---|
| `spirit_leaf` | 聚灵叶 | Fern (蕨) |
| `dew_blossom` | 凝露花 | Blue orchid (兰花) |
| `sunroot` | 赤阳根 | Carrot (胡萝卜) |
| `blood_berry` | 血朱果 | Sweet berries (甜浆果) |
| `cloud_fungus` | 云芝 | Brown mushroom (棕色蘑菇) |
| `calm_lotus` | 清心莲 | Lily pad (睡莲) |

Craft each formula sheet with paper, its two herbs and one low-grade spirit
stone (shapeless). Since **0.52.3**, hold the sheet and right-click in the air to
learn it permanently. Learning consumes one sheet in survival; already-known
formulas consume nothing. Knowledge belongs to the player and survives death,
logout and restart independently of the cultivation profile.

Place the furnace and right-click it. The **已学丹方 / Learned recipes** list shows
only your knowledge. Click a recipe to select it and automatically fill one batch
from your main inventory/hotbar: the two herbs and low-grade spirit stones. Old
incompatible furnace inputs return to your inventory first; if there is no room,
the entire switch is refused without moving anything. Missing ingredients remain
empty for you to supply; clicking the same recipe again tops up only the deficit.

Empty the output and press **开始炼制 / Start brewing**. One of each herb and one
spirit stone yields two pills in 20 seconds at 20 TPS. No formula item is required
inside the furnace. The initiating player must know that recipe and be alive,
non-spectator and awakened with initialized alchemy aptitude. The server freezes
the brewer's aptitude/quality ceiling. Active inputs and recipe selection are
locked; each batch requires a new manual start. Other viewers see their own
learned list and cannot start an unlearned recipe or replace an active batch.

Old furnaces keep their saved items and valid active batches. A physical sheet
left in the old formula slot appears as a return-only **旧丹方 / Old copy** slot;
take it back and use it to learn. Current client and server require 0.57.2/protocol25.

| Formula / pill IDs | Herbs | Lower-quality baseline effect |
|---|---|---|
| `formula_huiling` / `pill_huiling` (回灵) | 聚灵叶 + 凝露花 | Restores 25% of maximum spiritual power, rounded down with minimum one and capped at capacity |
| `formula_yangyuan` / `pill_yangyuan` (养元) | 赤阳根 + 血朱果 | Restores 2% of maximum health each second for 10 seconds; received damage cancels further healing |
| `formula_ningshen` / `pill_ningshen` (凝神) | 云芝 + 清心莲 | For 60 seconds, multiplies post-cap meditation stability gain by 1.5; no cultivation progress or study bonus |

Hold use for 1.6 seconds to take a pill. All pills share a 30-second medication
interval, and an active sustained medicine must finish before another pill can
be used. Inventory cooldown shading shows the longer remaining interval/benefit.
Full-power/full-health or ineligible use retains the pill. Ningshen requires
an awakened survival player who knows Basic Breathing, has reached the current
stage's cultivation cap, and still needs stability. It does not start meditation;
use `V` or the existing H-page control afterwards. No medicine stacks, advances
a realm, grants permanent attributes or changes breakthrough conditions.

The output must be empty before starting, so it cannot reveal the hidden roll
through stack compatibility. A full/incompatible output during an active batch
pauses without cost or reroll. The server rolls one quality at start and saves
it with the brewer, score and ceiling; only the frozen score/ceiling is shown
before completion. Unload/restart, brewer logout/death and later aptitude changes
do not reroll or change that batch. Saved valid work resumes; unloaded chunks
do not brew. Legacy progress without valid batch data resets to idle and
retains every item, requiring a new manual start. Breaking the furnace drops its
inventory and the furnace, but loses brewing progress. Hopper automation is not exposed.
Medication timers advance only while alive and online. Logout/restart preserves
the remaining time; death clears sustained benefits and preserves the shared
interval.

### Spirit Soil and three-stage herb farming (0.53.0)

Craft **灵土 / Spirit Soil** with one dirt and one low-grade spirit stone
(shapeless). Place it directly: no hoe or water is needed, and it never dries
or turns back into dirt from trampling. Ordinary crops cannot grow on it.

Hold any of the six herbs above and right-click the soil to plant one. The herb
is both planting material and harvest, with no separate seed item. There are
exactly three stages: **幼苗 → 生长 → 成熟** (`age=0..2`). Natural growth needs
sufficient light and loaded chunks; each bone meal use advances one stage.
Break mature plants to obtain 2–4 of the same herb, then replant one. An immature
plant normally returns one herb. 清心莲 also grows on spirit soil without water.

```mcfunction
/give @s myvillage:spirit_soil 16
/give @s myvillage:spirit_leaf 8
/give @s myvillage:dew_blossom 8
/give @s myvillage:sunroot 8
/give @s myvillage:blood_berry 8
/give @s myvillage:cloud_fungus 8
/give @s myvillage:calm_lotus 8
/give @s minecraft:bone_meal 32
```

Developer resource check: `python3 tools/gen_spirit_crops.py --check`.
Actual-model preview: `.venv-preview/bin/python tools/gen_spirit_crops.py --preview`.
Focused cloud test/build: `.venv-modal/bin/python -m tools.modal_runner run --preset crops-build --ref HEAD --timeout 1800`.
For item-display-only fixes, `--preset jar` packages without running gameplay tests.
Spirit Soil uses normal block item transforms (three-face GUI view, quarter-size drops); placed soil remains full size.
See [implementation](docs/ai-kb/51_spirit_farming.md) and
[three-stage preview](http://43.156.135.198:8765/spirit_crops/index.html).

### Pill quality and strength

Since 0.58.3, pill names, item-icon frames and tooltip borders share the weapon
quality palette: Lower green, Middle blue, Upper purple, Perfect gold. This covers
all three pills in inventories, containers and the hotbar. Old pills still display
as Lower; pills have no Mundane tier. Stack counts remain above the thin frame.

The same three pill IDs carry 下品 / Lower, 中品 / Middle, 上品 / Upper or
极品 / Perfect quality. The current values below come from `alchemy/rules.json`;
probability and balance have not received owner acceptance.

| Frozen quality ceiling | Lower | Middle | Upper | Perfect |
|---|---:|---:|---:|---:|
| Lower | 100% | 0% | 0% | 0% |
| Middle | 70% | 30% | 0% | 0% |
| Upper | 55% | 30% | 15% | 0% |
| Perfect | 45% | 30% | 20% | 5% |

| Quality | Strength factor | Huiling: maximum power restored | Yangyuan: maximum health each second | Ningshen: post-cap stability factor |
|---|---:|---:|---:|---:|
| Lower | 1 | 25% | 2% | 1.5 |
| Middle | 1.25 | 31.25% | 2.5% | 1.625 |
| Upper | 1.5 | 37.5% | 3% | 1.75 |
| Perfect | 2 | 50% | 4% | 2 |

Recovery retains rounding/cap rules. Yangyuan lasts 10 seconds; Ningshen lasts 60
seconds, and scales only the stability **bonus**:
`1 + (base multiplier - 1) × strength factor`. Quality does not shorten the
shared interval or change eligibility. Names/tooltips show quality and actual
strength. Sustained-effect quality and remaining online time survive restart.

`myvillage:pill_quality` is a saved/networked integer component 1..4. Old pills
without it retain lower-quality effects; newly brewed lower pills also omit it
and stack with old pills. Other qualities use vanilla component-sensitive
stacking. Explicit component 1 has lower effects but a distinct component map.

### Administrator setup and developer verification

All thirteen items appear in the MyVillage creative tab. Example fixtures:

```mcfunction
/give @s myvillage:alchemy_furnace
/give @s myvillage:spirit_leaf 8
/give @s myvillage:dew_blossom 8
/give @s myvillage:sunroot 8
/give @s myvillage:blood_berry 8
/give @s myvillage:cloud_fungus 8
/give @s myvillage:calm_lotus 8
/give @s myvillage:formula_huiling
/give @s myvillage:formula_yangyuan
/give @s myvillage:formula_ningshen
/give @s myvillage:pill_huiling 2
/give @s myvillage:pill_yangyuan 2
/give @s myvillage:pill_ningshen 2
# Quality fixtures: 2=Middle, 3=Upper, 4=Perfect; omitted component=Lower
/give @s myvillage:pill_huiling[myvillage:pill_quality=2] 2
/give @s myvillage:pill_yangyuan[myvillage:pill_quality=3] 2
/give @s myvillage:pill_ningshen[myvillage:pill_quality=4] 2
/give @s myvillage:low_grade_spirit_stone 8
```

Lightweight local checks and optional offline art preview:

```bash
python3 tools/gen_alchemy_assets.py --check
.venv-preview/bin/python tools/gen_alchemy_assets.py --preview
python3 tools/validate_alchemy.py
/usr/bin/python3 -m unittest tools.tests.test_validate_alchemy tools.tests.test_gen_alchemy_assets tools.tests.test_alchemy_quality_evidence
```

For focused checks of the learned-formula change:

```bash
.venv-modal/bin/python -m tools.modal_runner run --preset alchemy-build --ref HEAD --timeout 1800
```

This runs the alchemy Java tests and builds a jar; it does not launch a full game
acceptance suite. The old `alchemy_evidence.py` / `alchemy_quality_evidence.py`
physical-formula-slot fixtures and coordinates describe 0.50.0–0.52.2 and require
migration before using `alchemy-quality` or `release-alchemy` for 0.52.3. Their old
reports do not establish learned-formula/automatic-fill acceptance.

Recorded 0.52.0 developer evidence **passes**: 62 release gates, 1062 Java tests,
1297 Python tests, clean server and 42 mapped one-/two-client quality, medicine
and persistence checks on Modal. Six bilingual layouts and six pill tooltips
were captured and inspected. See [timings and retained retries](docs/testing/2026-10-16-alchemy-quality.md).
Owner appearance/feel/balance and wider multiplayer remain `not_verified`.

Preview: [成丹品质运行验证](http://43.156.135.198:8765/alchemy/quality/index.html) ·
[炼丹基础](http://43.156.135.198:8765/alchemy/index.html).
See [implementation boundaries](docs/ai-kb/49_alchemy.md),
[alchemy-quality specification](docs/contracts/cultivation.md#alchemy-quality), and
[historical 0.50.0 developer validation](docs/testing/2026-10-15-alchemy.md).

The following table is **historical 0.50.0 foundation evidence**. It does not
validate the new manual-start/quality behavior.

| Acceptance surface | State |
|---|---|
| Rules, medicine and regression Java tests | pass: 245 focused; 1029 full tests |
| Generated assets and Python tests | pass: full 1242-test run (13 environment skips separately passed with preview interpreter) |
| Fresh jar and dedicated server | pass: 0.50.0 jar/resource checks, generated resources unchanged, clean stage-1 server |
| Mapped furnace/pill input and saved restart | pass: 17 checks with actual client input and server readback |
| Developer model/menu inspection | pass: placed lit model, Chinese 480x270 and English 480x270/427x240/320x240 captures; owner verdict pending |
| Natural survival acquisition journey | not_verified (recipes shipped; runtime fixtures are administrator-prepared) |
| Owner model, texture, interface, pacing and balance | not_verified |
| Two-player multiplayer | not_verified |
| Herb planting/worldgen, furnace failure, optional herbs, trading | not_implemented |


## Crafting Aptitudes (炼丹／炼器资质, 0.52.0)

Press **H → 技艺 / Crafting** to see two independent innate aptitudes and the
highest product quality each allows. These ceilings concern **the quality of an
item made from the same recipe**; they do not restrict 黄/玄/地/天 recipe grade.
Alchemy freezes the initiating player's ceiling for each manually started batch
and rolls only within it (see [alchemy](#alchemy-炼丹-0520)). Forging now uses the same cap in its own production path (see [KB54](docs/ai-kb/54_forging.md)). The H page remains read-only.

| Aptitude | Maximum product quality |
|---|---|
| 0–39 | 下品 / Lower |
| 40–69 | 中品 / Middle |
| 70–89 | 上品 / Upper |
| 90–100 | 极品 / Perfect |

The initial independent rolls and thresholds live in
`src/main/resources/data/myvillage/crafting/rules.json`; changes require a rebuild
and restart. Aptitudes are generated once when the player first awakens a
spiritual root at the testing stele. An already-awakened old save initializes
once at login. A player who has not awakened sees **尚未测定**. Values survive
death, reconnect, restart, dimension changes, clearing/reawakening the root and
resetting the cultivation profile. Ordinary practice cannot exceed the quality
ceiling; no training or natural aptitude-upgrade mechanic is added in this slice.

Current client and server need 0.57.2 (protocol25). The server sends the quality
ceilings with the existing owning cultivation snapshot; H cannot modify them.

Administrator diagnostics (permission2) support both command roots and aliases:

```mcfunction
/myvillage cultivation crafting @s
/myvillage xiulian jiyi @s
/myvillage cultivation crafting set @s alchemy 70
/myvillage xiulian jiyi shezhi @s liandan 70
/myvillage cultivation crafting set @s forging 100
/myvillage xiulian jiyi shezhi @s lianqi 100
```

A setter requires an awakened target with initialized aptitudes, accepts0..100,
and preserves the other profession. These are administrator fixtures, not
survival aptitude upgrades. The status command returns a `CRAFTING` JSON row.

Lightweight local checks:

```bash
python3 tools/validate_crafting_aptitudes.py
/usr/bin/python3 -m unittest tools.tests.test_validate_crafting_aptitudes tools.tests.test_validate_cultivation_initiation
```

Full release/build, clean stage1, client and two-client checks default to Modal,
with no automatic local fallback. Use `release-alchemy` for complete quality delivery, or `alchemy-quality --cases`
for focused diagnostic checks. The historical `crafting_aptitude_evidence.py`
procedure remains an advanced remote-workspace reference: after clean stage1,
it owns mapped stele/H input and two sessions for lifecycle/restart, with RCON
fixtures/readback only. It does not use DevBridge.

Current protocol21 build and alchemy quality integration passed recorded Modal
runs (62 release gates and 42 mapped quality checks). Current H-page layouts
were not recaptured and remain `not_verified`. The following results are
**historical 0.51.0**, not a pass for this version.

Preview: [技艺资质](http://43.156.135.198:8765/crafting_aptitudes/index.html).
See [implementation](docs/ai-kb/50_crafting_aptitudes.md) and
[developer evidence](docs/testing/2026-10-15-crafting-aptitudes.md).

| Acceptance surface | State |
|---|---|
| Focused Java domain/snapshot/command/client tests | pass: 288 tests |
| Focused Python validators and guards | pass: 77 tests |
| Fresh build and clean server | pass: 62 release checks, 1049 Java tests, fresh jar and clean stage1 |
| Mapped awakening, H read-only, lifecycle/restart, six bilingual layouts | pass: 37 checks; isolated retry exited0; first wrapper anomaly retained |
| Owner appearance, aptitude distribution/balance and multiplayer | not_verified |
| Legacy save live migration and dimension-change capture | not_verified (covered by source/codec guards, not this capture) |
| Forging production, training, natural aptitude upgrades | not_implemented |

Current quality validation: [0.52.0 developer evidence](docs/testing/2026-10-16-alchemy-quality.md).


## Forging / 炼器雏形（0.57.2）

### 品质取物器（0.58.4）

在创造栏的 MyVillage 分类取得“品质取物器”，或使用 `/give @s myvillage:quality_picker`。
手持主手或副手右键打开；获取后可在任意游戏模式使用，不需要管理员权限或炼器/炼丹资质。
搜索物品名称或注册 ID，点击物品，选择品质；兵器还可选择其支持的专长。
点击“取一个”或“取一组”放入背包，背包放不下时不生成物品。保持取物器在手中即可连续取物。
丹药支持下、中、上、极品；青锋剑、凌霄枪和精铁工具支持原样凡品及四档炼器品质，专长按各器谱的实际支持范围提供。
没有品质系统的材料、方块等仅支持“原样”。该工具不加入生存合成或掉落。
客户端与服务器使用同版（当前协议 37）。品质取物器最初在 0.58.4 按要求仅打包，其历史验证范围见 KB54。

### 锻器与炼灵（0.70.0）

用三块铁锭、三块原木、三块圆石（自上而下三行）合成「锻器台」。已有炼器鼎也能进入工坊；炼器鼎仍由锻造台与下品灵石无序合成。

1. 左侧选择器谱，悬停“起坯”查看用料。青锋剑和凌霄枪仍先研读对应器谱（书＋石英／铁锭＋下品灵石）；精铁镐、斧、铲、锄入门即可制作，无需觉醒。
2. 起坯后点“入火”，消耗煤炭或木炭；按住“锻打”连续敲击，松开停止。火候不足再入火，没有每锤评分或精准节奏判定。
3. 完成成形后选择“中和／保刃／韧性”，依次淬火（水桶，返还空桶）、回火（燃料）、精磨（燧石）、装柄。每一阶段器坯都可取走保存，之后放回器物槽续作。
4. 装柄后已是可用的凡品兵器或工具。觉醒后将成器带到炼器鼎，可一次性炼灵，消耗原器谱的灵石部分和专长辅材；品质达到当前炼器资质上限。锋锐／导灵／坚韧支持范围由器谱决定，工具目前支持坚韧炼灵。
5. 用器物实际战斗、挖矿、伐木、采集或耕地积累该物品的历练。放回工坊后选择精工、耐用或可用的导灵方向，每一锤消耗历练和铁粒，获得永久提升。界面显示可锻次数；次数上限和每锤门槛以配置为准，不可无限锻打。
6. “修补”保留品质、附魔、命名和全部锻炼记录。“赤铜灌灵”继续使用既有批量灌灵流程。关闭、离开或断线返还当前器物；已完成进度保留，未完成操作取消，热度不带出界面。

工具保留铁级采掘资格，锻炼不会越级开采。人工放置、重复采集位置和活塞搬运不会反复产出历练；当前也不重复奖励同一位置重新长出的树或重复耕地。兵器历练来自有效近战伤害，同一敌人反复回血不能无限提供历练。

旧剑枪和基础合成青锋剑仍可使用并参与养成。凡品白色，首次炼灵后仍沿用下品绿、中品蓝、上品紫、极品金；锻炼度与品质分别保存。器物、材料及中间产物可在创造栏查找。

实现与限制：[KB54](docs/ai-kb/54_forging.md)。工序与养成参数：[workshop.json](src/main/resources/data/myvillage/forging/workshop.json)。

### 炼器材料（0.58.0）

三种辅材已接入各自的生存获取路线，采得或处理后直接放在背包中供炼器消耗：

- **赤铜锭**：右键炼器鼎，点击右上角“赤铜灌灵”，把原版或同标签铜锭放入左槽（可 Shift 点击）。点击“开始 / 继续”，逐次消耗自己的灵力；完成后从右槽取走整批赤铜。界面显示本批剩余耗灵，灵力不足会暂停。暂停、关闭、离开鼎或重启后，已付进度保留在铜锭上；关闭时材料和产物返回本人背包，背包满时按原版规则掉落。每位玩家独立操作，关闭后不会继续离线加工。导灵专长用于青锋剑。
- **玄铁锭**：在主世界深层洞穴寻找黑银纹理的玄铁矿脉，使用铁镐或更好的镐开采，普通挖掘直接得到 1–2 个**粗玄铁**；粗玄铁在熔炉（200 tick）或高炉（100 tick）中熔炼成**玄铁锭**。玄铁锭对应坚韧专长；旧 ID `myvillage:xuantie_ingot` 保留，显示名改为「玄铁锭」。
- **淬锋砂**：沿河滩、浅水和岩岸寻找带浅金晶粒的砂层，用铲直接采得，对应锋锐专长。砂层为有限矿产，不会在原地自动刷新。

玄铁矿与砂层的自然生成只作用于**新生成的主世界区块**；已有区块不会自动补矿。精准采集可取得矿块/砂层本身，玄铁矿支持时运。除玄铁锭的熔炉/高炉加工外，三种辅材没有工作台获取配方；剑枪基础用料与维修规则保持原样。

批量上限、耗灵、脉冲间隔、矿脉范围和采集数量以 [`acquisition.json`](src/main/resources/data/myvillage/forging/acquisition.json) 为准，专长用料以 [`rules.json`](src/main/resources/data/myvillage/forging/rules.json) 为准。


### 材料标签与互通（0.58.1）

- 灌灵原料识别 **`c:ingots/copper`**，其他模组加入这个通用标签的铜锭也可使用。每批仍是一个物品堆叠，不同物品或不同灌灵进度不会强行合并。
- 玄铁：**`myvillage:forging_materials/xuantie`**。
- 赤铜：**`myvillage:forging_materials/chitong`**。
- 淬锋砂：**`myvillage:forging_materials/edge_tempering_sand`**。

所有锭类物品统一使用扁锭轮廓（玄铁锭、赤铜锭、血灵锭及以后新增的锭）；方锭/圆饼/长条只作为形状研究，不进入正式资源。

三种专长辅材通过这些独立标签扣料；物品名称相同不代表自动互通。赤铜没有加入普通铜锭标签，玄铁和淬锋砂也不当作普通铁锭、沙子使用。玄铁矿及其方块物品另加入 `c:ores/xuantie` 和 `c:ores`；砂层方块/物品用 `myvillage:deposits/edge_tempering_sand` 分类。

例如，整合包可以在数据包的 `data/myvillage/tags/item/forging_materials/xuantie.json` 中加入其他模组的等价材料（下例 ID 需要换成目标物品，`required: false` 允许该模组未安装）：

```json
{
  "replace": false,
  "values": [{"id": "othermod:dark_iron", "required": false}]
}
```

标签随数据包重载更新。空标签会拒绝炼制；缺料时不部分扣除。某件物品同时匹配多个用料要求时只会被扣一次，并会寻找有效的完整分配方案。灌灵中的材料若被移出铜锭标签，会暂停且保留进度，重新加入后可继续。

炼器 `rules.json` 的成本键支持原有 `namespace:item` 和新增的 `#namespace:tag/path`；剑枪基础用料、维修默认用料保持原样。

## Advanced weapon arts (development branch)

Sword, spear and fist each gain two learned arts on remappable Z/C keys. The existing right-click sword qi is unchanged. Reach Qi Refining IV, obtain the matching manuals (`/function myvillage:advanced_arts_books` for an administrator), study them, and use the matching weapon in cultivation combat mode. See [the feature guide](docs/features/advanced-weapon-arts.md) for all six arts, choreography/VFX, authoritative rules and explicit unverified build/gameplay checks. No new JAR release is declared by this branch.


### 灵石袋（0.64.5）

用两张皮革、一根线和一枚下品灵石无序合成「灵石袋」，创造栏也可获取。放在背包或副手即可自动收纳各品级灵石，并用于修炼、交易、供奉、炼器和丹炉补料。对空右键切换自动收纳，潜行右键暂停此袋并取出一叠；普通工作台合成先取出原料。物品提示显示袋内余额，暂停收纳不影响支付。袋内数量无固定上限，按精确物品身份用大整数聚合，旧袋自动迁移；现已支持下品、中品和上品分别计数，支付仍使用原先下品，不自动兑换。

饰品来源接口、存档和配置见 [灵石袋说明](docs/ai-kb/59_spirit_stone_bag.md)。

### 中品、上品灵石（0.73.1）

中品为青蓝内流光，上品为紫金灵脉，均可放入灵石袋。**1 中品＝100 下品，1 上品＝100 中品**。普通玩家无需 OP，即可从下品逐级兑换：

```text
/灵石 余额
/灵石 兑换 中品
/灵石 兑换 上品
/灵石 拆分 中品
/灵石 拆分 上品
/灵石 兑换 中品 10
```

升阶每份产出一枚，拆分每份消耗一枚；省略数量为一份。兑换直接读取背包和袋内余额，产物优先入袋（包括已暂停收纳的袋）；余额不足或背包满时整笔失败，不掉物品。改名或带特殊组件的灵石及外部同级物品可保留入袋，但不参与兑换。现有修炼、炼丹、炼器、供奉和交易仍按下品支付，请按需主动拆分。

创造栏可直接取用；管理员物品 ID 为 `myvillage:medium_grade_spirit_stone` 和 `myvillage:high_grade_spirit_stone`。光泽和流光为静态材质表现。[实机截图与下载](http://43.156.135.198:8765/spirit_stone_grades/)；[检查记录](docs/testing/2026-10-22-spirit-stone-grades.md)。


### 实体锻器台（0.70.1）

0.70.2 新增**淬火水槽**（`myvillage:quenching_trough`）：配方上排为“铁锭、空、铁锭”，中排三木板，下排三圆石。将水槽紧贴锻台或炼器鼎、放在同一高度，用水桶右键注水，空桶立即返还。器坯锻打成形且仍热时，在锻台点击“淬火”，器坯会沉入邻槽，伴随蒸汽与淬火声；完成消耗槽中一桶水。空桶可取回未使用的水，淬火中不能取水。关闭界面中断会返还器坯并保留槽水。客户端与服务器均需更新至协议 38。

锻器台可直接放置，台面显示当前器坯，旁人能看到落锤与炉火。右键进入锻器界面操作；同一台一次供一人使用。关闭时器物返还背包，台面清空，不会把器物遗留在工位。配方为上排三铁锭、中排三原木、下排三圆石。

### 五行法修（0.81.0）

金木水火土各有常用、护体、爆发、范围四门战技，可单系或自行搭配。制作并研读秘籍后，H → 技能分配到六键，R 切修炼战斗模式；空手与任意手持物均可施法。沿用现有图标与品阶色。[招式、成长和边界](docs/features/elemental-arts.md)，配方见游戏指南「五行法修」。管理员试用可执行 `/function myvillage:elemental_arts_books` 取得秘籍，不直接学会。

0.81.0 五行法修的七类定向场景已完成；重启与双客户端范围、保留的旧手臂测试问题及未验收项见[交付记录](docs/testing/2026-10-22-elemental-arts.md)。[实机成长表与下载](http://43.156.135.198:8765/elemental-arts/)。

### 野外历练与战利品（0.84.0）

六种普通敌对野怪覆盖人形、妖兽和异植；主材可炼成六种战备丹药，或选取六种互斥器物专长。生存获取、自然分布与技术边界见 [功能说明](docs/features/wilderness-roster.md)，游戏指南新增“野外历练与战利品”。本轮远程验证使用用户指定 high-mem Colab，并按要求保留实例供后续验收。
