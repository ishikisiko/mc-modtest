# 拜入宗门 · 切片 3 品阶与事务 拆包说明（0.43.0）

状态（2026-10-07 07:45）：待开工（等切片 2 的采集链跑完再动 Java）。方案见 `docs/player-sect-entry-brief.md` §4.4 与 §5。分支 `feat/sect-entry` 继续。本轮 owner 电脑不可用：只做无头采集，README ledger 一律 `not_verified`。

## 0. 统筹已定的默认值

| 项 | 决定 |
|---|---|
| 年初考核 | 切片 1 已做（阈值在 `rules.player.promotion`，贡献门槛 0 不动） |
| 事务表 | 数据 `world_sim/sect_tasks.json`（schema 1）：`tasks: [{id, kind: patrol|tribute|courier, count, contribution}]`，首版三条：`patrol_beasts`（在本宗所在区域击杀 3 只妖兽，+10）、`tribute_stones`（上交 5 枚下品灵石，+10）、`courier_letter`（走到另一活跃宗门的山门，+15）。文案键 `world_sim.task.<id>.name/brief`（两份语言文件） |
| 派发 | 执事对话多一项"领事务"（TASK_ACCEPT）：每人每年一件（`taskYear` 记年份，年初刷新 = 新一年可再领），由 `SimRng.at(seed, day, hash(playerId), Purpose.PLAYER_TASK=405)` 在三条里选；courier 的目标宗门同一 rng 在其它活跃宗门里选（没有则不派 courier）。已有未完成事务则对话显示进度 |
| 进度 | 命簿 `PlayerMember`：`taskId`（""）、`taskProgress`、`taskTargetSectId`(-1)、`taskYear`(-1)；codec 用 opt 读写，**不升版本**。进度由运行时推：巡山——`LivingDeathEvent` 死者是 `BeastEntity`、击杀者是该玩家、玩家所在区域 == 本宗 `homeRegionId` → `advanceTask(+1)`；传信——`WorldSimPlayers` 每 20 tick 对有 courier 事务的在线玩家检查 `SectCourtyard.footprint(目标宗门 gate).distanceTo == 0` → 直接满额；供奉——不记进度，交付时检查背包有 ≥ count 枚 `low_grade_spirit_stone` 并扣除 |
| 交付 | 执事对话"交事务"（TASK_TURN_IN）：进度达标（供奉是背包达标）→ `completeTask`：贡献 += contribution，事件 `player_task_done`（重要度 2：玩家名、宗门名、事务名），清空事务但保留 `taskYear`（本年不再派） |
| 拜师 | 长老/掌门化身对话多一项"拜师"（APPRENTICE）：条件 内门以上、本宗成员、`masterId < 0`、长老在山门；`apprentice(playerId, name, masterId)`：`masterId` 记入，事件 `player_apprentice`（重要度 2，actors 师父 id：玩家名、宗门名、师父名）。不占 NPC 的 `disciplesPerMaster` 名额，不要求师父境界更高（owner 待定） |
| 师承系数 | 打坐进度乘 `(1 + rules.cultivation.master_guidance)`（0.15）：`WorldSimPlayers.masterGuidanceBasisPoints(player)` → `MeditationManager` 把它乘进 `progressFactorBasisPoints`。只在 `masterId >= 0` 且师父仍在本宗存活时 |
| 师父失效 | `PlayerAffairs.daily(ctx)`（每个结算日）：师父已死或已不在本宗 → `masterId=-1`，事件 `player_master_lost`（重要度 2：玩家名、师父名）；年初的静默清除保留作兜底 |
| 门规 | 向本宗化身出手扣交情：**不做**（化身无敌、无伤害事件可挂；留切片 4） |
| 协议 | 14 → 15（新增选项 id 3/4/5） |
| 面板 | "我的宗门"卡加 贡献（已有）、师父（已有）、事务一行（名、进度/目标）：`MySect` 加 `taskName`、`taskProgress`、`taskCount` |

## 1. 契约（包 S3-K）
- `sim/model/PlayerMember`：四个事务字段；`StateCodec` 写读（`task`、`task_progress`、`task_target`、`task_year`，opt）。
- `sim/data/ContentTables.SectTask(id, kind, count, contribution)` + `parseSectTasks`；`SimDataLoader.SECT_TASKS`；`SimData` 第 8 字段 `sectTasks()` 与 `sectTask(id)`；`src/main/resources/data/myvillage/world_sim/sect_tasks.json`；`tools/validate_world_sim.py` `KNOWN_FILES` 加文件 + `check_sect_tasks`（kind 合法、count/contribution 正整数、id 唯一）；`SimDataLoaderTest` 两个用例。
- `sim/engine/Purpose.PLAYER_TASK = 405`。
- `sim/TaskView(String id, String kind, int count, int contribution, int progress, int targetSectId, String targetSectName, long year, boolean ready)`。
- `WorldSim`（桩抛 `UnsupportedOperationException("slice 3 package S3-A")`）：`Optional<TaskView> task(String playerId)`、`Optional<TaskView> offerTask(String playerId)`（只算不写）、`SimEvent acceptTask(String playerId, String playerName)`（异常 reason：`not_member|task_active|task_done_this_year|no_task`）、`boolean advanceTask(String playerId, String kind, int amount)`（kind 不符返回 false）、`SimEvent completeTask(String playerId, String playerName)`（`no_task|not_ready`）、`SimEvent apprentice(String playerId, String playerName, int masterId)`（`not_member|rank_too_low|has_master|master_not_here`）。
- `sim/runtime/player/SectDialogueScenes.Option` 加 `APPRENTICE(3)`, `TASK_ACCEPT(4)`, `TASK_TURN_IN(5)`；`SectIntentPayload` 最大 kind 5；`SectDialoguePayload.MAX_OPTION_ID = 5`；语言键 `screen.myvillage.sect_dialogue.option.apprentice|task_accept|task_turn_in`；`ModPayloads.PROTOCOL_VERSION="15"` + `CombatPayloadTest` 断言。
- `WorldSimPlayers.masterGuidanceBasisPoints(ServerPlayer)` 桩返回 10000。
- `WorldSimSnapshot.MySect` 加 `taskName/taskProgress/taskCount`（codec 跟上，`WorldSimSnapshots` 先填空/0）。

## 2. 工作包
- **S3-A 命簿核心**：`PlayerAffairs` 的 task/apprentice/daily；`Engine.step` 在人物循环后调 `PlayerAffairs.daily(ctx)`；`TextKeys` + 两份语言文件（`player.task.accept`、`player.task.done`、`player.apprentice`、`player.master_lost`，各 1 变体）；测试 `WorldSimTasksTest`、`PlayerAffairsTest` 的师父用例改为发事件。
- **S3-B 运行时与对话**：`WorldSimPlayers`（巡山 LivingDeathEvent、传信 20 tick 检查、`masterGuidanceBasisPoints` 真实实现、交付时供奉扣物）、`SectDialogueScenes.decide` 的新场景（执事：无事务且本年可领 → TASK_ACCEPT；有事务未达标 → 显示进度（`steward.task.progress` 句）；达标 → TASK_TURN_IN；长老：内门以上无师父 → APPRENTICE）、`SectDialogue.handleIntent` 三个新分发（带 `SECT_ENTRY player= intent=TASK_ACCEPT|TASK_TURN_IN|APPRENTICE sect= result=` 日志）、`MeditationManager` 乘师承系数（跑 `validate_cultivation_gain.py`）、语言键 `world_sim.dialogue.steward.task.*`、`elder.apprentice.*`、聊天行 `message.myvillage.world.sect.task_*`/`apprentice`/`master_lost`。
- **S3-C 面板**：`MySect` 事务行、`WorldSimSnapshots` 填值、`WorldPage` 我的宗门卡多一行。
- **S3-E 采集**：`tools/world_sim_tasks_evidence.py`：拜入 → 对话 TASK_ACCEPT（截图）→ 若是巡山：`summon myvillage:demon_wolf` ×3 在山门旁、`damage @e[type=myvillage:demon_wolf] 100 minecraft:player_attack by <p>` → 对话 TASK_TURN_IN → `world player` 贡献 +10；若是供奉：`give <p> myvillage:low_grade_spirit_stone 5` → TURN_IN；若是传信：tp 到目标宗门 gate → 等 20 tick → TURN_IN；（事务由 rng 决定：脚本按对话里显示的事务名分支处理三种）；拜师：`world sect <id> rank <p> inner` → 找长老化身（名牌 realm 行 + `world sect` 的长老名）→ APPRENTICE → `world player` 显示师父；打坐系数：`myvillage cultivation ...` 打坐 10 秒前后进度对比（有师父 vs 无师父，比例 ≈ 1.15；可选）。
- **S3-G 文档**：CHANGELOG 0.43.0、README 节与 ledger、KB 43、bump。

## 3. 顺序
S3-K → S3-A ∥ S3-B ∥ S3-C ∥ S3-E（只写）→ 合并、采集、gate → S3-G。

## 4. 留给 owner
1. 事务的数量与奖励。2. 每年一件够不够。3. 拜师是否要求师父境界高于自己。4. 贡献晋升门槛（现在 0）。
