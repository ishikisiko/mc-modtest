# 秘籍物品与研读机制任务书 — 2026-10-06

统筹：Fable 会话。实施：Opus 5.5 子代理，F1（物品与数据）、F2（研读机制）并行，F3（面板显示、文档、版本）收尾。接在 `docs/technique-system-brief.md` 第一阶段（0.37.0）之后；本稿把第三阶段的"秘籍物品"提前。

## 0. owner 的决定（原话）

| 日期 | 决定 |
|---|---|
| 2026-10-06 | "有点想功法这些弄实际的MC注册的物品，但是不需要每一种一个，主要是按照稀有度和流派分类，一个类别X稀有度，做一个物品然后像附魔书那样单物品做差分" |
| 2026-10-06 | "go，但是右键读 = 开始学，得走一个小机制" |

统筹给的默认值（owner 对问题 1 点头，问题 2 改成"小机制"）：16 个物品，凡阶不做；研读是一个会被打断、进度存在书上、带关卡的打坐式会话（第 3 节）。

## 1. 物品矩阵

- 4 类 × 4 阶 = 16 个物品，id `manual_<category>_<grade>`，category ∈ core/active/movement/body，grade ∈ huang/xuan/di/tian。类 `TechniqueManualItem`（`item/`），最大堆叠 1。
- 原版 `Rarity`：黄 COMMON、玄 UNCOMMON、地 RARE、天 EPIC；天阶 `isFoil` 为真（附魔光）。
- 创造模式物品栏 `myvillage:main`：先 16 个空白本体，再像附魔书一样列出每门功法的秘籍（注册表里 grade ≥ 1 的每一门一个，按类别、品阶、id 排序），用 `BuildCreativeModeTabContentsEvent` 和参数里的 `HolderLookup.Provider` 读注册表。
- 名字：有功法组件时显示 `《功法名》`（键 `item.myvillage.manual.named` = `《%1$s》`）；没有组件时显示 `空白秘籍 · 绝技 · 玄阶`；组件和物品对不上（功法不存在、类别或品阶不符）显示红色 `残损秘籍`，不能读。
- tooltip：类别·品阶、流派、五行、传承位置（太白剑脉 2/4）、修习条件（照功法页的写法）、参悟进度 `参悟 37%`（有进度时）、残损原因。
- 贴图：4 张 16×16 基础图（`manual_core` 线装书、`manual_active` 卷轴、`manual_movement` 折页、`manual_body` 玉简），`tools/gen_manual_textures.py` 用纯标准库写 PNG（照 `tools/gen_meridian_figure.py` 的做法），`--check` 进发布闸门，不得手改。模型：每类一个共享模型，layer0 = 不染色的线稿细节，layer1 = 染色遮罩（`tintindex` 1）；16 个物品模型各自 parent 到类模型；品阶颜色在 `MyVillageClient` 用 `RegisterColorHandlersEvent.Item` 注册（黄 `0xC9A227`、玄 `0x3F6FB5`、地 `0x8B5A2B`、天 `0xE8D9A0`；数字放在一个 Java 常量表里即可，这是颜色不是平衡数）。
- 命令：`/myvillage cultivation manual <target> <technique_id>`，拼音别名 `miji`，给对应类别品阶的秘籍（凡阶功法拒绝）；原版 `/give @s myvillage:manual_active_xuan[myvillage:technique="myvillage:gengjin_jianjue"]` 也要能用。

## 2. 组件（已落地为接缝，`item/ModDataComponents.java`，冻结）

| 组件 | 类型 | 含义 |
|---|---|---|
| `myvillage:technique` | `ResourceLocation` | 这本秘籍教哪门功法 |
| `myvillage:comprehension` | `int ≥ 0` | 已参悟的研读点数；缺省 0 |

两者持久化并同步到客户端。统筹已注册到 mod 总线。

## 3. 研读机制（F2）

研读是打坐系统的第三种模式 `MeditationMode.STUDY`：玩家盘坐，书留在背包里，进度写在书上，随时可被打断，回来接着读。

### 3.1 开始

右键秘籍（服务端 `TechniqueManualItem.use` → `cultivation/study/ManualStudy.use(player, hand)`，接缝已落地，F2 填实现）。依次检查，任何一条不满足就用聊天消息说明并不开始：

1. 书有效：组件存在、功法已注册、类别与品阶和物品一致。
2. 功法尚未学会。
3. 已觉醒灵根；`TechniqueRequirementEvaluator` 的境界、阶段、五行亲和要求通过。
4. 传承前置：功法有 `lineage.previous` 时，前一门必须已学（熟练度档位尚未实现，先只要求已学）。
5. 没有正在进行的打坐或研读；和普通打坐一样的地面、非骑乘、非游泳、非飞行、非睡眠、近期未受伤等条件（复用 `MeditationManager.requestStart` 的检查链）。

通过后开始 STUDY 会话（和打坐一样有 `PREPARATION_TICKS` 的准备期和锚点），会话记住背包槽位和功法 id。

### 3.2 结算（每 10 tick，和打坐同一个结算节拍）

- 槽位里的物品必须仍是同一门功法的有效秘籍，否则以 `MANUAL_LOST` 停止。
- 增加点数：`affinity × (10000 + 五行契合加成bp) / 10000`，`affinity` 是档案的 `spiritualAffinity`（普通打坐就是按它涨），五行契合加成和心法因子一样取 `rules.json` `techniques.element_match_bonus`（灵根在该功法任一五行亲和 ≥ `roots.element_threshold_bp` 时），**不乘品阶倍率**。整数基点，向下取整。
- 写回书上的 `comprehension`。
- 关卡：功法的 `study.gates` 个关卡均匀落在 `points × k / (gates + 1)`。结算要跨过一道关卡时：稳定度 ≥ `study.gate_stability_cost` 则扣掉并继续；否则点数停在关卡上，以 `STUDY_GATE` 停止，消息说明"参悟至此，神思不济"和还差多少稳定度。关卡状态不用额外存：点数 ≥ 关卡即视为已过，扣费只在跨越那一次发生。没有任何随机。
- 完成：点数 ≥ `study.points` → 学会功法（走 `CultivationService` 现有的学习入口，第一门心法自动运转的规则照旧），秘籍消耗 1 个，以 `STUDY_COMPLETE` 停止并提示。

### 3.3 打断

和打坐完全一样：移动、受伤、停止意图（X 键 / 面板按钮 / 命令）、下线、死亡、管理员取消。进度留在书上。`MeditationStopReason` 新增 `MANUAL_LOST`、`STUDY_GATE`、`STUDY_COMPLETE`、`STUDY_REQUIREMENTS`（按需要再加，少加）。

### 3.4 状态同步

`MeditationStatus` 新增 `Optional<StudyProgress>`（功法 id、点数、总点数、下一道关卡点数或 -1、关卡稳定度消耗），只在 STUDY 状态出现；`MeditationStatusPayload` 编解码扩展；`ModPayloads.PROTOCOL_VERSION` 10 → 11。客户端输入不变：仍只有四个打坐意图，开始靠右键物品，停止靠现有的 STOP。

## 4. 数据（F1 的生成器改动）

每门生成的功法 JSON 新增 `study` 块，由目录生成器按品阶从 `tools/technique_catalogue/rules.json` 新节 `study_by_grade` 写出；`basic_breathing` 不带（它不是秘籍）。编解码里 `study` 可选，缺省 `{points 4000, gates 0, gate_stability_cost 0}`。

| 品阶 | points | gates | gate_stability_cost |
|---|---|---|---|
| 黄 | 4000 | 0 | 0 |
| 玄 | 12000 | 1 | 50 |
| 地 | 36000 | 2 | 100 |
| 天 | 96000 | 3 | 150 |

灵性 10 时 points 4000 = 4000 tick ≈ 3.3 分钟实时；玄 10 分钟、地 30 分钟、天 80 分钟，五行契合再打八五折。都是数据，owner 嫌长就改表。

## 5. 显示（F3）

- 修炼页：会话是 STUDY 时在左栏经脉图下方（或右栏顶部，看布局）加一张"研读"卡：标题 `研读《功法名》`、进度条 点数/总点数、`下一关 n 点 · 耗稳定度 m`、提示 `按 X 停止`。`sessionText` 显示 `研读中`。只读 `PanelContext`，不加新输入。
- 功法页不变。tooltip 在 F1。
- 没有 HUD。

## 6. 工作包

分支 `feat/world-sim`。只 `git add` 自己的路径；重活走 `flock -w 3600 /home/ubuntu/code/mc/.mc-heavy.lock`；`/usr/bin/python3` 跑校验器；提交信息末尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；不推送、不碰版本号、不写 OpenSpec。同一工作树并行：随时保持可编译，别人的包里有编译错误就等。

### 接缝（统筹已提交，双方都不改签名）

- `item/ModDataComponents.java`：`TECHNIQUE`、`COMPREHENSION`。
- `cultivation/study/ManualStudy.java`：`public static InteractionResultHolder<ItemStack> use(ServerPlayer player, InteractionHand hand)`，F2 实现，F1 调用。

### F1：物品与数据

拥有：`item/TechniqueManualItem.java`（新）、`item/ModItems.java`（16 个注册 + 创造栏变体）、`client/MyVillageClient.java`（只加物品 tint 注册）、`assets/myvillage/models/item/manual_*.json`、`assets/myvillage/textures/item/manual_*.png`、`tools/gen_manual_textures.py`（新）+ 测试、`tools/release_gate.py`（加一步 `gen-manual-textures-check`）、`tools/technique_catalogue/rules.json`（`study_by_grade`）与 `tools/technique_catalogue/*.py`（写出 `study` 块）+ 重新生成 `data/myvillage/myvillage/technique/*.json`、`cultivation/data/TechniqueDefinition.java` 只加可选 `study` 记录（`TechniqueStudy(points, gates, gateStabilityCost)`，校验 points > 0、gates ≥ 0、cost ≥ 0）、`cultivation/CultivationCommands.java` 的 `manual|miji`、语言文件里 `item.myvillage.*`、`tooltip.myvillage.*`、命令文案键，`tools/validate_mod_items.py` 若需登记新物品，`genops/contracts/items/` 若该流程要求契约。测试：物品名/残损判定/创造栏变体数（Gradle），生成器幂等与 `--check`（Python）。
验收：`tools/validate_mod_items.py`、`gen_technique_catalogue.py --check`、`gen_manual_textures.py --check`、`validate_cultivation_core.py`、`validate_world_sim.py`、`./gradlew test --tests 'com.example.myvillage.item.*' 'com.example.myvillage.cultivation.data.*'`，最后全量 `./gradlew test`。

### F2：研读机制

拥有：`cultivation/study/**`（`ManualStudy` 实现、纯逻辑 `StudyLedger`/`StudyGates` 等）、`cultivation/meditation/**`（STUDY 模式、会话槽位、结算、停止原因、状态）、`cultivation/network/MeditationStatusPayload.java` 与接收端、`network/ModPayloads.java` 只改协议号 10 → 11、`cultivation/CultivationService.java` 只在需要时加学习入口、`cultivation/CultivationEvents.java` 若需要、相应测试、`tools/validate_cultivation_meditation.py` / `validate_cultivation_gain.py` 中因新模式必须更新的扫描（保持 `input=one-of-4-actions`、`random=none`、结算常量名不变）、语言文件里 `message.myvillage.cultivation.study.*`。读取组件用 `ModDataComponents`；物品类型判断用 `stack.getItem() instanceof TechniqueManualItem`，F1 未落地前用 `ModDataComponents.TECHNIQUE` 是否存在代替并在报告里写明。测试：开始检查链每条一个用例、结算点数与契合、关卡扣费与不足停止、完成学会并消耗、槽位换物停止、打断后进度保留、状态编解码往返。
验收：六个 cultivation 校验器、`validate_cultivation*` 的 Python 测试、`./gradlew test --tests 'com.example.myvillage.cultivation.*'`，最后全量 `./gradlew test`。

### F3：面板、文档、版本（F1、F2 合入后）

拥有：`client/cultivation/panel/MeditationPage.java`（研读卡）、`PanelContext`/`PanelReadouts` 的只读辅助、`screen.myvillage.cultivation.*` 语言键、面板测试；`docs/ai-kb/41_technique_system.md`、`28`、`30`、`37` 的相应段落、README（命令、秘籍小节、0.38.0 台账全 `not_verified`）、CHANGELOG、AGENTS.md 一条、`python3 tools/bump_version.py 0.38.0`。

### 顺序

统筹：接缝提交 → F1 ∥ F2 → F3 → 统筹跑 `tools/release_gate.py` → 部署到 owner 电脑：给一本黄阶秘籍，右键开始研读，截修炼页研读卡与 tooltip；用 `/tick rate` 加速到完成，确认学会并消耗。

## 7. 留给 owner 的

1. 研读时长表（第 4 节）是否合适。
2. 关卡消耗稳定度这个"神思"设定是否认可；备选是消耗灵石。
3. 是否要让研读期间的书有可视化（手持举书、面前浮现书页）；本稿不做。
