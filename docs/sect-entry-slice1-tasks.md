# 拜入宗门 · 切片 1 拆包说明（0.41.0）

状态（2026-10-07 06:00）：开工。方案见 `docs/player-sect-entry-brief.md`；本文是执行用的拆包说明，不是 OpenSpec。分支 `feat/sect-entry`（自 main 41c5b74）。0.41.0 同时含 `docs/female-cultivator-tasks.md` 的女修两套造型（文件不相交，共用一个版本号）。

owner 2026-10-07："有什么你自己决定就行，我回来直接验收结果"。本轮 owner 电脑不可用：所有验收只做无头采集，README ledger 一律 `not_verified`。

## 0. 统筹已定的默认值（写在这里以便推翻）

| 项 | 决定 |
|---|---|
| 版本 | 0.41.0 = 入世切片 1 + 女修两套造型；0.42.0 藏经阁；0.43.0 品阶事务；0.44.0 世界回应 |
| 玩家资质 | 纯核心只认 `PlayerQualification(realmId, stageIndex, awakened, rootPeakBp)`。brief 里的"灵根总亲和 ≥ 3000 bp"解释为**最高单系亲和** ≥ 3000 bp（五系合计恒为 10000，总和无意义）。realmId 是玩家境界注册表的 path（`mortal` / `qi_refining` / `foundation_establishment`），stageIndex 是该境界内 0 起的层数 |
| 资质快照 | 存进命簿成员记录（登录、每个结算日、拜入时由运行时刷新）；年初考核只读快照，纯核心不碰 MC |
| 门槛数值 | `rules.json` 新段 `player`（见 §2），默认：觉醒即可拜入；声望 ≥ 60 的宗门另要最高单系 ≥ 3000 bp 或境界 ≥ 炼气 |
| 交情 | 每宗一个 -100..100；拜入 +20，退宗 -40，负值每年 +10 回向 0；退宗后 3 年内且交情 < 0 不得回头 |
| 执事是谁 | 派生不存：`WorldSim.stewardOf(sectId)` = 在山门成员里按 外门→内门→长老→掌门 取最低 id |
| 执事名牌 | 名牌是一行组件，不能换行：第四段 "守山执事"（`entity.myvillage.cultivator.avatar.steward`）；长老化身名牌不变 |
| 对话文案 | **不建 dialogue.json**：语言键 `world_sim.dialogue.<scene>.<n>` + Java 变体表 `SectDialogueKeys`，测试钉两份语言文件都有 |
| 山门落地 | P4-lite：160 格内自动建，按 chunk 的 `SectSink.Clip` 分帧（worldgen 结构件已经这么切），每 tick 若干 clip，同时只建一座 |
| 事件重要度 | `player_join` / `player_leave` / `player_promotion` 都是 2（永久保留，进江湖传闻） |
| 玩家不计入门人 | `SimContext.members()` 不含玩家；宗门页"在山门者"不列玩家；"我的宗门"卡显示自己的记录 |
| 掌门 | 考核最高到长老；掌门之路留白（owner 已定） |

## 1. 已落的契约（统筹先写、编译通过后各包只读不改；要改先告诉统筹）

纯核心 `sim/`：
- `sim/model/PlayerMember`：`playerId`（UUID 字符串）、`playerName`、`sectId`(-1)、`rank`（outer/inner/elder）、`joinedDay`、`masterId`(-1)、`contribution`、`borrowed`（List<String>）、`standings`（TreeMap<Integer,Integer>）、`leftSectId`(-1)、`leftDay`(-1)、资质快照 `realmId`/`stageIndex`/`awakened`/`rootPeakBp`。
- `WorldState.playerMembers`：`TreeMap<String, PlayerMember>`。
- `sim/PlayerQualification(String realmId, int stageIndex, boolean awakened, int rootPeakBp)`。
- `sim/PlayerMemberView`：只读视图（含 sectName、masterName、standings 复制）。
- `sim/Admission(boolean ok, String reason)`：reason ∈ `ok | sect_inactive | already_member | member_elsewhere | not_awakened | realm_too_low | selective | rejoin_cooldown | standing_too_low`。
- `WorldSim` 新方法（包 A 实现，统筹给桩）：
  - `Optional<PlayerMemberView> playerMember(String playerId)`
  - `List<PlayerMemberView> playerMembers()`
  - `Optional<PersonView> stewardOf(int sectId)`
  - `Admission admission(String playerId, int sectId, PlayerQualification q)`
  - `SimEvent joinSect(String playerId, String playerName, int sectId, PlayerQualification q)`（不可拜则抛 IllegalArgumentException(reason)）
  - `SimEvent leaveSect(String playerId, String playerName)`
  - `Optional<SimEvent> promotePlayer(String playerId, String playerName, String rank)`（管理用；同品阶返回 empty）
  - `void updatePlayerQualification(String playerId, String playerName, PlayerQualification q)`（非成员则无操作）
- `sim/engine/PlayerAffairs.yearly(ctx)`：`Engine.step` 在 `SectAffairs.yearly` 之后调用。
- `sim/data/Rules.Player`（§2 的形状）。

运行时：
- `entity/npc/NpcEntity`：同步字段 `DATA_LOOK`（String，默认 `default`，存档 `Look`）和 `DATA_LEDGER_ROLE`（String：`none|steward|elder`，不存档）；`look()/setLook()`、`ledgerRole()/setLedgerRole()`、`protected List<String> looks()`（子类列出外观名）；`mobInteract` 的对话钩子由包 B 加。
- `sim/runtime/player/WorldSimPlayers`（包 B 实现，统筹给桩）：`register()`、`qualification(ServerPlayer)`、`member(ServerPlayer)`、`join(ServerPlayer, int sectId, boolean force)`、`leave(ServerPlayer)`，返回 `Result(boolean ok, String reason)`；它自己负责 `WorldSimSavedData.setDirty`、给玩家发聊天行、写 `SECT_ENTRY` 日志。
- `sim/runtime/avatar/GateRealizer`（包 D 实现，统筹给桩）：`register()`、`tick(MinecraftServer)`、`status()`。
- `WorldSimRuntime.register()` 已调用 `WorldSimPlayers.register()` 与 `GateRealizer.register()`。
- `genops/contracts/entities/cultivator.yaml` 的 `state.synced` 已列出两个新字段。

## 2. 规则数据 `rules.json` 新段 `player`（统筹已写，包 A 实现解析）

```json
"player": {
  "admission": {"require_awakened_root": true, "min_realm": "mortal", "min_stage": 1, "join_standing": 20,
                "selective": {"prestige_at_least": 60.0, "root_peak_bp_at_least": 3000, "or_min_realm": "qi_refining"}},
  "leave": {"standing_penalty": -40, "rejoin_standing_at_least": 0, "rejoin_years": 3, "standing_recovery_per_year": 10},
  "promotion": {"inner": {"realm": "qi_refining", "stage": 4, "contribution": 0},
                "elder": {"realm": "foundation_establishment", "stage": 0, "contribution": 0}},
  "scripture_hall": {"borrow_cost_by_grade": {"1": 0, "2": 0, "3": 0, "4": 0}},
  "steward": {"interact_range": 6.0},
  "gates": {"realize_radius": 160, "clips_per_tick": 2}
}
```

境界比较：`mortal` 低于命簿境界表的一切；其余按 `RealmTable.indexOf`。`promotion.inner.stage 4` = 炼气五层（0 起）。`Rules.Player` 的 `RealmStage` 允许 realm 为 `mortal`（index -1）。

## 3. 工作包（文件互不相交；只 `git add` 自己的路径；提交信息末尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；Gradle 一律 `flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew <task> -x generateAllStructures`，只跑 `compileJava compileTestJava` 和自己的 `test --tests`；`/usr/bin/python3`；不 push；不改别人的文件，编译错误在别人的包里就等一会儿再试）

### 包 A：命簿核心（纯 `sim/`，不碰 `sim/runtime/`）

拥有：`sim/model/PlayerMember.java`、`sim/model/StateCodec.java`（VERSION 2→3，`player_members` 可选数组；v2 读为空）、`sim/model/WorldState.java`、`sim/WorldSim.java` 的桩方法体、`sim/PlayerMemberView.java`、`sim/Admission.java`、`sim/PlayerQualification.java`、`sim/engine/PlayerAffairs.java`、`sim/engine/TextKeys.java`、`sim/data/Rules.java`（`player` 段）、`sim/cli/ChronicleWriter.java`（若需要）、两份语言文件里的 `world_sim.event.player.*` 键、`tools/validate_world_sim.py`（若它校验 rules 段或键）、`src/test/java/com/example/myvillage/sim/**` 新测试。

1. 事件键（家族，各 1–2 个变体）：`world_sim.event.player.join`（参数：玩家名、宗门名）、`player.leave`（同）、`player.promote.inner` / `player.promote.elder`（玩家名、宗门名）。`TextKeys` 注册参数个数；事件 `type` 为 `player_join` / `player_leave` / `player_promotion`，`sects(sectId)`，`region(sect.homeRegionId)`，actors 空。`TextKeyCoverageTest` 要过（两份语言文件、位置槽齐）。
2. `admission` 按 §0/§2 判定，顺序：sect_inactive → member_elsewhere/already_member → rejoin_cooldown（同宗且 `day - leftDay < rejoin_years*dpy`）→ standing_too_low → not_awakened → realm_too_low → selective。
3. `joinSect`：建/改记录（已有记录则复用 standings 与 borrowed），`standings[sect] += join_standing`（夹在 -100..100），事件 `player_join`，返回事件。`leaveSect`：`sectId=-1, rank=outer, masterId=-1`，`leftSectId/leftDay`，`standings[sect] += standing_penalty`，事件。
4. `PlayerAffairs.yearly`：每个成员按 `promotion` 阈值与快照晋升（outer→inner→elder，一年只升一级），事件 `player_promotion`；所有负交情 `+= standing_recovery_per_year` 回向 0；师父已死或已不在本宗则 `masterId=-1`（本切片没有拜师，留逻辑）。宗门覆灭时（`SectPolitics.dissolve`）：成员自动成散修，`sectId=-1`，事件 `player_leave` 的变体 `.sect_gone`（玩家名、宗门名）——放在 `PlayerAffairs.sectDissolved(ctx, sect)`，由 `SectPolitics.dissolve` 调一行（你可以改 `SectPolitics` 这一行）。
5. `stewardOf`：`membersAt(sectId)` 里按 rank 序 outer(0) inner(1) elder(2) sect_master(3) 再 id 升序取第一个。
6. 测试：codec v3 往返 + v2 载荷读入为空、admission 判定表、join/leave/standing 回复、年初晋升、`WorldSimDeterminismTest`/健康带不变（玩家不进 `members()`）。`tools/validate_world_sim.py` 与 `/usr/bin/python3 -m unittest tools.tests.test_validate_world_sim` 要过。
7. 提交：`feat(sim): player sect membership in the ledger (codec v3, admission, yearly promotion)`。

### 包 B：化身角色与对话（`entity/npc/NpcEntity.mobInteract`、`sim/runtime/player/**`、`sim/runtime/avatar/WorldSimAvatars.java`、`sim/runtime/net/SectDialogue*`、`client/sim/SectDialogueScreen.java`、`network/ModPayloads.java` 协议 12→13）

拥有上述文件 + 语言键 `world_sim.dialogue.*`、`entity.myvillage.cultivator.avatar.steward`、`message.myvillage.world.sect.*`（拜入/退宗的聊天行）+ `genops/contracts/entities/cultivator.yaml` 的 `behavior.interaction.dialogue` 与 `world_sim_avatar.interaction` 两行 + `src/test/java/.../sim/runtime/player/**`、`.../net/SectDialoguePayloadTest`。

1. `WorldSimPlayers`：`qualification(player)` 从 `CultivationService.getProfile(player)` 取 realm path、stage index（在境界定义的 stages 里的下标）、`awakened()`、`spiritualRoot` 最大 bp；`join(player, sectId, force)`：刷新快照 → `force` 时跳过 admission 只要求宗门存续 → `sim.joinSect` → `WorldSimSavedData.get(overworld).setDirty()` → 聊天行 `message.myvillage.world.sect.joined`（宗门名）→ 日志 `SECT_ENTRY player=<name> intent=JOIN sect=<id> result=ok`（拒绝时 `result=<reason>`）。`leave` 同理。`register()`：`PlayerLoggedInEvent` 刷新快照；`WorldSimRuntime.addListener` 每个结算日刷新在线玩家快照，并把当天 `player_*` 事件里 `params[0]` 等于某在线玩家名的事件发成聊天行（`WorldSimText.event`）。
2. `WorldSimAvatars`：`selected()` 把执事放第一位（`AvatarPlanner.select` 加 `stewardId` 参数：有则排最前）；`spawn` 后 `setLedgerRole`：执事 `steward`，长老/掌门 `elder`，其余 `none`；执事的格子固定取 `cells` 里最低台地上离轴线（`SectGenerator.AXIS_X0..AXIS_X1` 中线）最近、z 最小（最靠门洞）的空位（加 `AvatarPlanner.stewardCell(cells, occupied)`）；名牌：执事用 `entity.myvillage.cultivator.avatar.steward`（四段），其余照旧；`reconcile` 时角色变化也要刷新字段与名牌。外观：调用 `CultivatorLooks.forPerson(p, realmOrder)`（包 F1 提供，`entity/npc/CultivatorLooks`，签名 `static String forPerson(PersonView p, List<String> realmOrder)`；它不存在前先写 `"default"` 并留 TODO 一行，统筹合并时替换）。
3. `NpcEntity.mobInteract`：化身且角色非 none：服务端 `SectDialogue.open((ServerPlayer) player, this)` 后返回 `CONSUME`；客户端返回 `CONSUME`；其他化身仍 `PASS`。
4. `sim/runtime/player/SectDialogue`（服务端权威）：`open`：校验同维度、距离 ≤ `rules.player.steward.interact_range`、化身属活跃宗门、角色 steward/elder → 组 `SectDialoguePayload` 发给玩家。`SectDialoguePayload`（clientbound）：`entityId, sectId, sectName, role, avatarName, prestige(int), memberCount, masterName, regionName, myRank(""), myStanding, admissible, reason, List<Component> lines(≤8, ComponentSerialization.TRUSTED_STREAM_CODEC), List<Integer> options`。`SectIntentPayload`（serverbound）：`kind`（byte：0 JOIN 1 LEAVE 2 FAREWELL）、`entityId`、`sectId`；处理：再次校验距离/维度/实体角色/宗门一致 → `WorldSimPlayers.join/leave` → 回发新的 `SectDialoguePayload`（场景变成 welcome / farewell）。两者注册在 `WorldSimPayloads.register` 里（你可以改那一个方法）。`ModPayloads.PROTOCOL_VERSION` → "13"。
5. 文案：`SectDialogueKeys` 列场景与变体数：`steward.greet`（宗门名）、`steward.intro`（宗门名、声望、门人数、掌门名）、`steward.invite`（可拜）、`steward.refuse.<reason>`（每个 reason 一条）、`steward.welcome`（宗门名）、`steward.member`（已是本宗弟子时的问候，品阶）、`steward.leave_ask`、`steward.farewell_left`（退宗后）、`elder.greet`、`elder.member`。两份语言文件都写，中文为主、英文可平实。选项文案 `screen.myvillage.sect_dialogue.option.join|leave|farewell` 与标题键。
6. 客户端 `client/sim/SectDialogueScreen`（vanilla `Screen` + `Button`，可用 `PanelTheme` 配色）：标题（化身名 · 角色 · 宗门），正文按行换行，底部按钮按 options；点 JOIN/LEAVE 发 `SectIntentPayload`，FAREWELL 关闭。收到新载荷时若已开着本屏则就地刷新。打开时写日志一行每个按钮：`SECT_DIALOGUE option=<KIND> x=<> y=<> w=<> h=<>`（INFO，采集脚本靠它点击；坐标是 GUI 缩放后的屏幕像素，`Minecraft.getWindow().getGuiScale()` 乘回去）。
7. 测试：`SectDialoguePayloadTest`（两载荷往返、越界拒绝）、`SectDialogueScenesTest`（纯函数：由 Admission/成员状态决定场景与选项）、`SectDialogueKeysTest`（每键在两份语言文件里存在）。`flock … ./gradlew compileJava compileTestJava` 过了再提交。
8. 提交：`feat(sim): sect steward avatars and the server-authoritative sect dialogue`。

### 包 C：面板、命令、通知（`sim/runtime/net/WorldSimSnapshot*.java`、`sim/runtime/net/WorldSimPayloads.answer`、`client/cultivation/panel/WorldPage.java`、`sim/runtime/WorldSimCommands.java`、语言键 `screen.myvillage.cultivation.world.*`、`commands.myvillage.world.*`、`world_sim.bearing.*`）

1. `WorldSimSnapshot` 加 `MySect mine`（可空；`sectId, sectName, rank, joinedDay, masterName, contribution, standing, borrowedCount`），OVERVIEW 与 SECT（仅当是自己的宗门）填；`SectSummary` 加 `bearing`（八向键尾 `n|ne|e|se|s|sw|w|nw`，HERE 时填，其余 ""）。`WorldSimSnapshots.build` 多一个 `String playerId` 参数；`bearing(dx, dz)` 是纯函数（+x 东、+z 南；0° 北）。编解码 `WorldSimSnapshotCodec` 跟上；`NetFixtures`/`WorldSimSnapshotsTest`/`WorldSimPayloadCodecTest` 更新并加 mine/bearing 用例。
2. `WorldPage`：总览多一张"我的宗门"卡（未入宗显示一句"尚未拜入宗门，去山门找守山执事"）；宗门详情对自己的宗门显示"我的品阶 · 入门日 · 交情"；此地的宗门行加方位：`相距%1$s格（%2$s）`。仍不放任何写入按钮。布局在 480x270 / 427x240 / 320x240 下不溢出（照 AGENTS 的 H 面板规则）。
3. 命令：`world sect <id> join <player>`、`world sect <id> leave <player>`（权限 2，调用 `WorldSimPlayers.join(target, id, true)` / `leave`；`EntityArgument.player()`），`world player <player>`（成员记录：宗门、品阶、入门日期、交情列表、曾退出的宗门）；输出键 `commands.myvillage.world.player.*`。
4. 五个 `tools/validate_cultivation_*.py` 若读 `panel/*.java` 钉东西，要保持通过。
5. 提交：`feat(sim): my-sect card, gate bearings and player membership commands`。

### 包 D：分帧落地 P4-lite（`sim/runtime/avatar/GateRealizer.java`、`sect/SectGenerator.java` 的新入口、`sect/SectSink.java` 若需要、语言键 `message.myvillage.world.gate.*`、`src/test/java/.../sim/runtime/avatar/GateRealizerTest`）

1. `GateRealizer.tick`（每 20 tick 一次，自己订阅 `ServerTickEvent.Post`）：命簿活跃、`avatars_enabled`；对每个活跃、未落地、`sects()` 里有坐标的宗门，取 `GatePlacement` 坐标到最近玩家的平面距离 ≤ `rules.player.gates.realize_radius` → 入队（按距离）。同时只建一座：`Job{sectId, anchor, seed, variant, plan, clips 队列, 已强制加载的区块}`。
2. 建造分帧：开工那 tick：`level.getChunk` 取地表 y 得 anchor（同 `GateBuilder` 的方式），`SectGenerator.plan`，`buildMountain`（纯计算，一次做完），强制加载足迹区块（含 `MOUNTAIN_MARGIN`），聊天广播 `message.myvillage.world.gate.forming`（宗门名）给 160 格内玩家；之后每 tick 做 `clips_per_tick` 个 chunk clip：`writeMountain(sink(clip), …)` 后 `realizeCompound(sink(clip), …)`（照 `SectStructurePiece` 的做法，看它如何用 clip 避免模板重复放置；`templateRandom` 每个 clip 用同一种子派生，与同步 build 一致）。全部 clip 完成 → 取消强制加载 → `GateRealizations.put` + `sim.markGateRealized(true)` + `WorldSimSavedData.setDirty` + `WorldSimAvatars.gateChanged(sectId)` + 聊天 `gate.formed`。异常 → 回滚为未落地（`markGateRealized(false)`、清 `GateRealizations`）、日志 error、取消强制加载。玩家中途离开不取消（建完为止）。
3. 把 `SectGenerator.buildAt` 里能复用的部分抽成包内静态方法（`prepare(level, seed, variant, anchor)` 返回 plan+mountain+base；`sinkFor(level, base, mountain, clip)`），同步的 `generateForcedAt` 行为不变（`SectGeneratorTest` 若有要过）。
4. 日志：`GATE_REALIZE sect=<id> state=queued|started|clip <i>/<n>|done|failed …`（INFO；采集脚本靠它）。`status()` 给 `world info` 用不上就留给 D 自己的命令 `world gates`（可选）。
5. 测试：队列选择（最近先、只建一座、半径）、clip 切分覆盖足迹且不重叠的纯函数测试。
6. 提交：`feat(sim): framed auto-realization of sect gates near a player (P4-lite)`。

### 包 E：采集证据（Python，`tools/world_sim_entry_evidence.py`，`tools/tests/test_world_sim_entry_evidence.py` 纯 helper）

先写代码与纯 helper 测试；真机运行等统筹说"构建好了"。照 `tools/world_sim_avatar_evidence.py` 的骨架（session、rcon、Transcript、check、index.html），输出 `out/preview/world_sim/entry/`（`index.html`、`evidence.json`、`commands.txt`、`server_log.txt`、`client_log.txt`、截图）。

场景（创造模式、暂停结算、正午）：
1. 选一个有"在山门者"的活跃宗门，`world sect <id>` 取 gate 坐标；把玩家 tp 到 gate 水平 140 格外；等服务端日志 `GATE_REALIZE … done`（最长 10 分钟；记录 started→done 秒数与期间 `tps` 可观察的 `/tick` 无卡死：用 `rcon tick query` 或 `Running 20 ticks per second` 行）；截图远景。
2. 找执事：`execute as @e[type=myvillage:cultivator] run data get entity @s CustomName` 里含 `avatar.steward` 键的那只，取 Pos；tp 到它面前 3 格、看向它；截图（名牌）。
3. 右键：`xdotool click 3`（`Game.xdo("click", "3")`），等客户端日志 `SECT_DIALOGUE option=JOIN …`；截图对话；按坐标点击 JOIN（换算 GUI scale：客户端窗口 960x540、`guiScale` 从 options 读或日志坐标已是像素）；等服务端 `SECT_ENTRY … result=ok`；截图欢迎；`world player <name>` 记录；`world chronicle 5` 含"拜入"。
4. 天下页：按 H 打开面板，点"天下"页签（页签坐标照 `out/preview/world_sim_panel` 当时的做法；若找不到就跳过并写 `not_captured`），截图总览"我的宗门"卡；Esc。
5. `world advance 24`（一年）：`world chronicle` 看有无 `player.promote`（炼气五层以下不会晋升，记录"无晋升，符合阈值"即可）；若想看晋升：`cultivation set realm`/`xiulian` 命令把玩家境界设到炼气五层再 `advance 24`，看 `player.promote.inner`。
6. 再右键执事 → LEAVE → `SECT_ENTRY … intent=LEAVE result=ok`；再 JOIN 应 `result=rejoin_cooldown`。
7. 管理命令：`world sect <id> join <player>` 强制拜入成功（绕过冷却）；`world sect <id> leave <player>`。
8. 每步 `check(...)`，index.html 列全部 check 与截图。失败不隐藏。

提交：`feat(capture): player sect entry headless evidence`。

### 包 G：文档与版本（A–E 合入、gate 通过、采集跑完后统筹另派）

README 新节 "Player Sect Entry (0.41.0)"（走一遍、规则数据、命令、ledger 全 `not_verified`）、CHANGELOG 0.41.0（入世 + 女修两节）、`python3 tools/bump_version.py 0.41.0`、`docs/ai-kb/43_player_sect_entry.md` + INDEX 一行 + KB 40 的 facade/数据/命令表更新 + KB 39 加 Looks 节、`AGENTS.md` 世界模拟段加一句玩家成员与对话的规则、两份 brief 的状态行。

## 4. 顺序与接缝

K（契约）→ A ∥ B ∥ C ∥ D ∥ E（只写）∥ F1 ∥ F2 ∥ F3。统筹逐包 review、提交、合并；A 完成前 B/C 的调用编译得过（桩抛 `UnsupportedOperationException`），运行要等 A。B 与 F1 都碰 `WorldSimAvatars.spawn` 的一行外观选择：F1 只提供 `CultivatorLooks`，B 调用。C 改 `WorldSimSnapshot` 记录组件会动 `NetFixtures` 等测试，B 不碰这些文件。协议版本只 B 改。`.git/index.lock` 存在就等。

## 5. 验收

- `tools/release_gate.py` PASS，版本 0.41.0。
- `python3 tools/world_sim_entry_evidence.py`：自动落地有 started/done 与耗时；执事名牌截图；对话截图；JOIN 后 `world player` 显示外门弟子；`world chronicle` 有拜入行；LEAVE 后再 JOIN 被拒 `rejoin_cooldown`；管理命令强制拜入成功。
- `python3 tools/world_sim_avatar_evidence.py` 仍 PASS（老路径不坏）。
- 两份语言文件、`TextKeyCoverageTest`、`SimPurityGuardTest`、`validate_custom_entities.py`、`validate_world_sim.py` 全过。

## 6. 留给 owner

1. 门槛与交情数值（`rules.json player`）。
2. 执事名牌第四段的写法；要不要换成头顶第二行（需要自定义渲染）。
3. 自动落地半径 160 与每 tick 的 clip 数（卡顿感只能真机看）。
4. 对话界面的样式（现在是素的原版按钮）。
