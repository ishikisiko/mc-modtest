# 玩家入世方案：拜入宗门（0.41 → 0.44）

状态（2026-10-07）：四个切片全部落地（分支 feat/sect-entry），待 owner 验收：切片 1 入门 0.41.0（无头采集 20/20）；切片 2 藏经阁 0.42.0（19/19）；切片 3 品阶与事务 0.43.0（21/21，供奉路径）；切片 4 世界回应 0.44.0（待采集结论）。真 P4 只做了评估，见 `docs/ai-kb/43_player_sect_entry.md` 的 "Real P4 (worldgen placement) assessment"。

owner 2026-10-07："好，写一个方案看看"。这是设计方案，不是任务书；每个切片开工前另写拆包说明。不建 OpenSpec。

## 1. 为什么是这一步

三条线各自成型但没接上：战斗（三把武器、步法、闪避、妖狼）、修炼（打坐、冲关、功法目录、秘籍读书）、世界演算（命簿、宗门政治、传承、天下页、山门地形）。玩家至今不在命簿里，命簿的宗门只能用管理命令落到地图上，化身不能交互。入世把演算从观赏表变成玩家有立场的世界，前置系统全部现成，主要是设计决定。

原则沿用演算任务书：**组织是数据，建筑是可视化；命簿是权威，世界跟着命簿走。** 玩家进入命簿，但不被命簿操控。

## 2. 玩家走一遍（目标体验）

1. 打开 H 面板的天下页，"此地"一栏列出附近宗门的方位和距离。走过去，靠近到 160 格时山门自动落地（分帧建造，不卡服）。
2. 山门口站着守山执事（该宗门一个在山门的弟子化身，名牌多一行"守山执事"）。右键对话：宗门简介、威望、收徒条件、两三个选项。
3. 选"拜入"。条件不够会被明说原因（灵根未觉醒、境界不足、此宗挑人）。通过则成为外门弟子，命簿记一笔"某某拜入某宗，为外门弟子"，天下页出现"我的宗门"卡。
4. 上藏经阁。殿里有经架，右键看到按品阶开放的功法：外门拿入门功法，内门拿镇派功法的前半条链，长老拿整条传承。取一本就是一本秘籍，走现有的读书流程。
5. 境界到了，年初考核自动晋升内门、长老，命簿记事。可以向长老拜师，打坐收益多一个师承乘数。执事会派简单的门中事务，攒贡献。
6. 宗门卷入战争、换掌门、覆灭，玩家会收到通知。宗门覆灭则玩家自动成散修，传承进入遗迹池，学过的功法都还在。
7. 想走就找执事退宗：和这个宗门的交情掉一截，几年内不能回头；其他宗门照常可拜。

## 3. 需要 owner 拍板的设计点（带默认值，不反对即照此执行）

| # | 问题 | 默认 |
|---|---|---|
| 1 | 入门门槛 | 觉醒灵根即可拜入普通宗门；威望 ≥ 60 的宗门另要求灵根总亲和 ≥ 3000 bp 或境界 ≥ 炼气一层（数据可调） |
| 2 | 一人一宗与退宗代价 | 同时只能在一宗；退宗经执事，和该宗的交情 -40，三年内不得回头；学过的功法保留，藏经阁资格失去。不做"叛门追杀"，NPC 现在不会打 |
| 3 | 玩家算不算门人 | 不计入宗门人数、资源、威望（避免扰动演算平衡），但出现在门人列表里带"玩家"标记，事件进编年史 |
| 4 | 山门怎么落地 | 靠近 160 格自动分帧建造（P4-lite，旧档可用）；真正的 worldgen 落位（新档开荒即在）作为后续升级 |
| 5 | 藏经阁取书代价 | 首版免费，一门一本，记借阅；第三切片引入贡献后按品阶收贡献 |
| 6 | 能否当掌门 | **owner 已定（2026-10-07）：可以当掌门，但不是考核晋升来的；怎么当以后再定。** 本方案的年初考核最高到长老，掌门之路留白 |
| 7 | 执事是谁 | 不造新实体：宗门在山门的弟子里按规则选一个化身当执事（最低 id 的外门，没有则内门、长老、掌门），化身可交互仅限执事与长老 |

## 4. 系统设计

### 4.1 命簿数据：玩家成员记录

- 不把玩家做成 `Person`：Person 每天被引擎结算（修炼、游历、死亡、继位），玩家不能被演算操控。新增 `WorldState.playerMembers`：以玩家 UUID 字符串为键（纯核心不能碰 MC 类型），字段 `sectId, rank(outer|inner|elder), joinedDay, masterId(可空), contribution, borrowed(功法 id 列表), standings(sectId → -100..100 交情), leftSectId/leftDay`。
- `StateCodec.VERSION` 2 → 3，加可选字段，旧档读为空（先例：v2 的 `lost_heritages`）。`WorldSimSavedData.FORMAT` 不动。
- 唯一入口放 `WorldSim` 门面（`joinSect / leaveSect / promotePlayer / borrow / addContribution`），旁边就是现有的 `markGateRealized / moveGate`。
- 事件：新类型 `player_join / player_leave / player_promotion / player_apprentice`，`TextKeys` 注册、两份语言文件加键，玩家名作参数。随机性若需要走新的 `Purpose` 段（4xx），不复用旧码。
- 玩家不进 `SimContext.members()`（决定 3），所以不影响经济、招新、化身选择。
- 年初（`day % dpy == 0`）在 `SectAffairs.yearly` 之后加 `PlayerAffairs.yearly`：按阈值晋升、交情回复、师父仍在否。阈值放 `rules.json` 新段 `player`（见 4.7）。

### 4.2 守山执事与化身对话

- 执事是派生的，不存状态：每个有山门的活跃宗门，`stewardId = 在山门的成员里按 外门→内门→长老→掌门 取最低 id`。`AvatarPlanner.select` 先放执事，再放掌门、长老，其余照旧；执事的格子固定在山门台地靠轴线、门洞一侧（`SectCourtyard.cells` 过滤）。
- `NpcEntity` 增加同步字段 `DATA_LEDGER_ROLE`（none / steward / elder），写进 `cultivator.yaml` 的 `state.synced`，名牌加一行角色。`mobInteract`：执事与长老化身打开对话，其他化身仍 PASS。
- 对话全程服务端权威：右键 → 服务端校验距离 ≤ 6 格、同维度、化身属该宗 → 发 `SectDialoguePayload`（宗门摘要、收徒判定与原因、可选项）→ 客户端 `SectDialogueScreen` → 选项发 `SectIntentPayload(kind, sectId)`（JOIN / LEAVE / APPRENTICE / TASK_ACCEPT / TASK_TURN_IN / FAREWELL）→ 服务端再校验一次才改命簿。载荷放 `sim/runtime/net`，不进 `cultivation/`（那里的载荷白名单由校验器钉死）。`ModPayloads.PROTOCOL_VERSION` 12 → 13。
- 对话文案是数据：`world_sim/dialogue.json`，按角色与场景给几句（迎客、拒收的各种原因、收徒、退宗、拜师），语言键走 `world_sim.dialogue.*`。

### 4.3 藏经阁

- 新方块 `myvillage:scripture_shelf`（经架）。只在命簿落地的山门里出现：`GateBuilder` 建完后置一遍，在两个 `slot_scripture_flank_*` 槽位的殿内地面中央各放一个，方块实体存 `sectId`。随机 worldgen 的匿名山门不放（它们与命簿无关）。
- 右键 → 服务端校验玩家是该宗成员、在山门范围（`SectCourtyard.footprint`）→ 发可借清单 → `ScriptureHallScreen`。清单规则镜像 `People.sectTechnique`：外门 = 宗门 `basicTechniqueId`（加凡阶 `basic_breathing`）；内门 = 传承链第二门（无传承的宗门 = 镇派功法）；长老 = 整条传承链。每门一本，取过记入 `borrowed`。
- 取书 = `TechniqueManualItem.manualFor` 给一本秘籍，读书仍走 `ManualStudy` 的现有门槛（境界、五行、前置）。
- `HeritageDefinition.exclusive` 从此生效：独占传承的秘籍只能来自藏经阁或遗迹池的奇遇，`StudyStart` 不改，改的是来源。

### 4.4 品阶、贡献、师徒、门中事务

- 晋升阈值与 NPC 一致（内门：炼气五层；长老：筑基）加贡献门槛（首版 0）。年初考核自动晋升，发事件和通知。
- 贡献由执事派的事务攒：首版三种，都是已有机制能判定的：巡山（在本宗所在区域击杀 N 只妖兽，接 `LivingDeathEvent`）、供奉（上交 N 枚灵石，接物品）、传信（走到另一宗的山门，接 `GateRealizations` 距离）。事务表是数据 `world_sim/sect_tasks.json`，每人同时一件，年初刷新。
- 拜师：内门以上可向在山门的长老化身拜师，命簿记 `masterId` 与 `player_apprentice` 事件；打坐收益乘以师承系数（复用 `cultivation.master_guidance` 的数值），师父死亡或离宗则失效并通知。
- 门规首版只有两条可判定的：退宗代价；向本宗化身出手（化身无敌，只记录意图）扣交情。NPC 战斗落地后再加同门相残。

### 4.5 山门落地（P4-lite）

- `WorldSimAvatars` 的 20 tick 巡检旁加 `GateRealizer`：活跃宗门、未落地、有玩家进入 `realize_radius`（160）→ 排队建造。建造分帧：先造山与台地（每 tick 若干列），再放模板（每 tick 一个槽位），最后通道与经架；全程强制加载山门所在区块；完成后写 `GateRealizations` 并 `markGateRealized`，和手动 `build` 一致。单服务器同时只建一座。
- 建造中玩家可能看见半成品，用一条聊天提示"某宗山门正在显形"。建造失败回滚为未落地并记日志。
- 真 P4（worldgen 阶段在宗门坐标落位，新档开荒即在）作为后续：需要在区块生成前把命簿坐标暴露给一个自定义 `StructurePlacement`，与现有随机 `myvillage:sect` 结构的关系也要定（建议随机结构改成废弃山门或撤掉）。

### 4.6 面板与指路

- 天下页：概览加"我的宗门"卡（宗门、品阶、入门日、师父、贡献、交情），宗门详情页对自己的宗门显示品阶与可借功法数；"此地"一栏每个宗门加方位（八向）与距离。仍不放管理按钮；入门只能在世界里完成（决定：沉浸优先，面板只读）。
- 通知：本宗事件（战争、换掌门、覆灭、晋升、师父变故）走现有江湖传闻通道并提权为必达，另加一条聊天行。

### 4.7 规则数据（`rules.json` 新段 `player`）

```json
"player": {
  "admission": {"require_awakened_root": true, "min_realm": "mortal", "min_stage": "mortal_qi_sensed",
                "selective": {"prestige_at_least": 60, "root_total_bp_at_least": 3000, "or_min_realm": "qi_refining"}},
  "leave": {"standing_penalty": -40, "rejoin_standing_at_least": 0, "standing_recovery_per_year": 10},
  "promotion": {"inner": {"realm": "qi_refining", "stage_number": 5, "contribution": 0},
                "elder": {"realm": "foundation_establishment", "stage_number": 0, "contribution": 0}},
  "scripture_hall": {"borrow_cost_by_grade": {"1": 0, "2": 0, "3": 0, "4": 0}},
  "steward": {"interact_range": 6.0},
  "gates": {"realize_radius": 160, "realize_columns_per_tick": 64}
}
```

## 4.8 本轮验证口径（owner 2026-10-07）

owner 的电脑这轮不可用，所有验收只在服务器做无头验证：造山门、拜入、藏经阁、`advance` 等都走采集会话和脚本，产物进 `out/preview/`，README ledger 一律 `not_verified`，等 PC 可用再看。

## 5. 切片与版本

| 切片 | 版本 | 内容 | 产出证据 |
|---|---|---|---|
| 1 入门 | 0.41.0 | 成员记录与 codec v3、执事角色与对话、拜入/退宗、编年史事件、天下页"我的宗门"与方位、P4-lite 自动落地、管理命令 `world sect <id> join/leave <player>`、`world player <name>` | 无头：造一座山门，走到执事前拜入，天下页截图，`advance` 一年看事件（本轮无 PC） |
| 2 藏经阁 | 0.42.0 | 经架方块与界面、按品阶的可借清单、借阅记录、独占传承生效 | 无头：外门只见入门功法，管理命令升内门后见第二门；PC 截图 |
| 3 品阶与事务 | 0.43.0 | 年初考核晋升、贡献、三种门中事务、拜师与师承系数 | 无头：巡山任务杀妖狼计数、`advance` 到年初晋升；PC |
| 4 世界回应 | 0.44.0 | 本宗事件通知、覆灭转散修、交情影响对话（敌对宗门的执事拒客）、真 P4 评估 | 无头：强制覆灭看玩家状态与通知 |

切片 1 是最大的一块（命簿、NPC、网络、面板、建造五处都动），估计拆成四个包并行：命簿核心与门面、化身角色与对话、面板与通知、分帧建造。

## 6. 现状与接缝（给拆包时用）

- 命簿：`sim/model/Sect`（无成员表，`Person.sectId` 是真相，`People.join` 唯一入口）、`SimContext.members()`、`SectAffairs.yearly` 晋升阈值在 `rules.json sects`、`Genesis`、`GatePlacement`（每宗一个区块中心坐标）、`StateCodec` v2、`WorldSim` 门面、`SimPurityGuardTest`（核心不碰 MC 类型与随机）、`TextKeys`、`Purpose` 码段。
- 运行时：`WorldSimRuntime`（`DayListener` 可做按日记账）、`WorldSimAvatars`（20 tick 巡检、`AvatarPlanner.select`、每宗 12 全局 40 上限）、`GateBuilder` + `GateRealizations`（唯一的宗门↔山门绑定）、`SectCourtyard.cells/footprint`、`SectGenerator` 的 `slot_scripture_flank_{left,right}_3`。
- NPC：`NpcEntity.mobInteract` 对化身 PASS；同步字段只有 `DATA_LEDGER_PERSON`；`cultivator.yaml` 的 `state.synced` 由 `validate_custom_entities.py` 钉死。
- 网络与面板：`WorldSimQuery` 只读、`WorldSimSnapshot` 已拿到 `ServerPlayer`、`WorldPage` 只读五栏、动作页的写入走有界 serverbound 载荷；`cultivation/` 下的载荷白名单在 `validate_cultivation_initiation.py`。
- 修炼：`CultivationProfile` v4 不动（成员关系放命簿，避免 v5 迁移和校验器钉子）；`TechniqueManualItem.manualFor`、`ManualStudy`、`StudyStart` 门槛复用。
- 词汇：`world_sim.rank.*` 已有掌门/长老/内门弟子/外门弟子；`world_sim.event.recruit` 的句式可借用；藏经阁、守山执事、拜师等键全新。

## 7. 风险

- 同步建造卡服：手动 `build` 本来就阻塞服务端线程，自动落地必须分帧，并限同时一座。
- 命簿重置：演算重新生成则宗门全变，玩家成员记录随之作废（提示并清空），这是接受的。
- 多人：多个玩家同一宗没有冲突；执事对话是每人一份快照。
- 化身无敌且不入存档：执事角色依赖化身在场，玩家离开 96 格化身撤走，对话自然结束；不需要新的实体保存逻辑。
- 独占传承生效后，命簿 NPC 的功法来源不变，只约束玩家。
- 协议升 13、codec 升 3、`cultivator.yaml` 同步字段，三处校验器都要跟着动。

## 8. 并行的美术任务

owner 2026-10-07 同时要求 NPC 与妖兽建模：修仙者增加两套女性造型（刚入门的素雅古风、小有所成的华丽），女性特征要与男性区分。单独成文：`docs/female-cultivator-brief.md`。文件与入世切片不相交，可并行。

## 9. 另两条候选的去向

- 灵力池 + 第一门绝技（剑诀）：排在入世之后；藏经阁给了绝技来路，灵力条是技能键的前提。
- 敌我态度 + NPC 战斗 AI：入世给出阵营（本宗、交情、战争），之后再做战斗状态机才有判据。
