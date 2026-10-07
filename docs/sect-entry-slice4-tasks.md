# 拜入宗门 · 切片 4 世界回应 拆包说明（0.44.0）

状态（2026-10-07）：已落地于 0.44.0，证据 15/15（`python3 tools/world_sim_news_evidence.py`，`out/preview/world_sim/news/`；长老拒客与交情拒客未采到），待 owner 验收；真 P4 评估见 `docs/ai-kb/43_player_sect_entry.md`，文档见 README "World response (0.44.0)"。方案见 `docs/player-sect-entry-brief.md` §4.6、§5 切片 4。分支 `feat/sect-entry` 继续。只做无头采集，ledger 一律 `not_verified`。

## 0. 统筹已定的默认值

| 项 | 决定 |
|---|---|
| 本宗事件必达 | 结算日后，凡 `importance >= 2` 且 `event.sects()` 含玩家所在宗门（或 `leftSectId` 当天覆灭）的事件，不论玩家在哪个区域，都发一条聊天行，前缀 `message.myvillage.world.sect.news`（"【宗门】%1$s"）；战争/换掌门/覆灭/分裂/传承失落这些类型都在其中。离线玩家：不补发（首版） |
| 覆灭转散修 | 切片 1 已做（`PlayerAffairs.sectDissolved`，事件 `player_leave.sect_gone`）；本切片只补聊天必达与采集 |
| 敌对宗门的执事拒客 | `SectDialogueScenes`：玩家所在宗门与执事宗门处于 `war` 状态（`SectView.relations` 的 `state == "war"`）→ `steward.refuse.at_war` + [FAREWELL]；玩家对该宗交情 `< rules.player.admission.hostile_standing_below`（新键，默认 -50）→ `steward.refuse.hostile` + [FAREWELL]；散修不受战争影响。长老化身同样拒绝 |
| 交情变化 | 新增两条可判定的交情来源：被本宗覆灭/分裂波及不扣；向本宗化身出手（化身无敌）**不做**（无伤害事件可挂） |
| 真 P4 评估 | 不写代码：KB 43 加 "Real P4 (worldgen placement) assessment" 一节（需要什么：区块生成前拿到命簿坐标、`StructurePlacement` 自定义、与随机 `myvillage:sect` 结构的关系、旧档兼容），给 owner 决策 |
| 协议 | 不变（15） |

## 1. 契约
- `rules.json player.admission.hostile_standing_below`（-50）与 `Rules.PlayerAdmission` 字段。
- `SectDialogueScenes.decide` 加参数 `boolean atWar, int standingWithSect`（或封装为一个 `Visitor` record）。
- 语言键：`world_sim.dialogue.steward.refuse.at_war.1`、`.hostile.1`、`elder.refuse.at_war.1`、`message.myvillage.world.sect.news`。

## 2. 工作包
- **S4-A**（一人包：运行时 + 对话 + 规则 + 测试）：`Rules`/`rules.json` 新键（含 `validate_world_sim.py` 若需要）；`WorldSimPlayers.onDaySettled` 的本宗事件必达；`SectDialogue.open` 计算 `atWar`（`sim.sect(me.sectId).relations()` 里对执事宗门的 state）与 `standingWithSect`（`me.standings().getOrDefault(sectId, 0)`）；`SectDialogueScenes` 两个新拒绝场景；`SectDialogueKeys` + 语言文件；测试（场景用例、必达过滤的纯函数 `SectNews.relevant(event, member)` 单测）。
- **S4-E**（采集，放进 `tools/world_sim_entry_evidence.py` 新步骤或新脚本 `world_sim_news_evidence.py`）：拜入后用管理命令制造战争（若无命令：`advance` 多年直到出现本宗事件；或新增管理命令 `world sect <a> war <b>`——若加命令归 S4-A）→ 检查聊天行（客户端日志里的 `[CHAT]` 行含"【宗门】"）；覆灭：`world sect <id> destroy`（新管理命令，S4-A）→ `world player` 显示散修 + 聊天行 + `player_leave.sect_gone` 事件；敌对拒客：对战争对方的执事右键 → 对话只给告辞 → 截图。
- **S4-G**：CHANGELOG 0.44.0、README 节与 ledger、KB 43（含真 P4 评估节）、bump。

## 3. 留给 owner
1. 必达消息要不要给离线玩家补发。2. 敌对门槛 -50。3. 真 P4 做不做（评估节）。
