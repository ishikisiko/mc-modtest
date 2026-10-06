# 拜入宗门 · 切片 2 藏经阁 拆包说明（0.42.0）

状态（2026-10-07 07:10）：开工。方案见 `docs/player-sect-entry-brief.md` §4.3 与 §5；切片 1 已落地于 0.41.0（`docs/sect-entry-slice1-tasks.md`）。分支 `feat/sect-entry` 继续。本轮 owner 电脑不可用：只做无头采集，README ledger 一律 `not_verified`。

## 0. 统筹已定的默认值（owner 未反对即照此执行）

| 项 | 决定 |
|---|---|
| 经架方块 | `myvillage:scripture_shelf`，`Block implements EntityBlock`（不是 `BaseEntityBlock`，免得默认 `INVISIBLE`），方块实体 `ScriptureShelfBlockEntity` 存 `sectId`（int，-1 = 无主）。有 BlockItem（创造栏，管理员可手放），放下的无主经架右键提示"此架无主" |
| 放置位置 | 命簿落地的山门里，两座藏经阁（`slot_scripture_flank_left_3`/`right_3`，模板 `scripture_pavilion_001/002`）各一个，放在殿内地面中央（槽位中心列从台地标高向上找第一个"实心且上方两格为空气"的位置，上面那格放经架）。`GateBuilder.build` 成功后与 `GateRealizer.finish` 释放 ticket 之前各调一次；随机 worldgen 的匿名山门不放 |
| 可借清单 | 镜像 `People.sectTechnique` 的链规则：外门 = 链[0]（无传承的宗门 = `basicTechniqueId`）；内门 = 链[0..1]（无传承 = basic + signature）；长老 = 整条链（无传承 = basic + signature）。`basic_breathing` 是凡阶（grade 0）无秘籍，清单里不出现，改成一行提示"凡阶心法去传功碑" |
| 借阅 | 首版免费（`rules.player.scripture_hall.borrow_cost_by_grade` 全 0，读出来但为 0 时不扣），一门一本：已借过的在清单里灰掉并标"已借"；取书 = `TechniqueManualItem.manualFor` 给一本秘籍进背包（满了掉在脚下），命簿 `borrowed` 记 technique id（path，不带命名空间） |
| 独占传承 | `HeritageDefinition.exclusive` 的链只从藏经阁来：`CultivationCommands` 的 manual 命令与创造栏不动（管理/开发用）；本切片新增一个纯函数 `ManualSources.playerFacing(techniqueId)` 只在藏经阁与（将来的）遗迹路径里返回 true，并用测试钉住"每个 exclusive 传承的每门功法都在持有宗门长老的可借清单里"。KB 写明这是约束来源而非 StudyStart |
| 事件 | `player_borrow`（重要度 1，玩家名、宗门名、功法名；`TextKeys.PLAYER_BORROW`，1 变体）。不进江湖传闻（重要度 1 无人听） |
| 协议 | 13 → 14 |
| 管理命令 | `world sect <id> rank <player> <outer|inner|elder>`（调 `sim.promotePlayer`），采集靠它看内门清单；`world sect <id> shelves`（列出该宗经架坐标与方块实体 sectId） |

## 1. 契约（包 S-K 先写，编译通过后各包只读不改）

- `WorldSim`：`List<String> borrowable(String playerId)`（成员按品阶可借的功法 path 列表，未入宗返回空）、`boolean hasBorrowed(String playerId, String techniqueId)`、`SimEvent recordBorrow(String playerId, String playerName, String techniqueId)`（非成员/不可借/已借抛 IllegalArgumentException(reason)：`not_member|not_borrowable|already_borrowed`）、`SectView.basicTechniqueId`（新字段，加在 `signatureTechniqueId` 旁）。桩抛 `UnsupportedOperationException("slice 2 package S-A")`。
- `block/ScriptureShelfBlock`（`Block implements EntityBlock`，`CODEC`，`useWithoutItem` 调 `ScriptureHall.open(serverPlayer, level, pos)`）、`block/entity/ScriptureShelfBlockEntity`（`sectId`，`saveAdditional/loadAdditional` 写 `Sect`）、`block/ModBlockEntities`（`DeferredRegister<BlockEntityType<?>>`，在 `MyVillageMod` 构造器里 `ModBlocks.register` 之后注册）、`ModBlocks.SCRIPTURE_SHELF` + `BLOCK_IDS`、`ModItems` 的 BlockItem + 创造栏、资源四件（blockstate、block model：用原版 `bookshelf` 侧面贴图 + `dark_oak_planks` 顶底的立方体即可；item model；loot 掉自身）、两份语言文件 `block.myvillage.scripture_shelf`（经架 / Scripture Shelf）。
- `sim/runtime/player/ScriptureHall`（服务端服务，桩）：`open(ServerPlayer, ServerLevel, BlockPos)`、`handleBorrow(ServerPlayer, ScriptureBorrowPayload)`；`sim/runtime/net/ScriptureHallPayload`（clientbound）、`ScriptureBorrowPayload`（serverbound）记录与编解码（S-K 写完整，S-C 只用）：
  - `ScriptureHallPayload(BlockPos pos, int sectId, String sectName(64), String myRank(16), boolean member, String reason(32), List<Entry> entries(≤16))`，`Entry(String techniqueId(64), Component name, int grade, String category(16), boolean borrowed, int cost)`。
  - `ScriptureBorrowPayload(BlockPos pos, String techniqueId(64))`。
  - `ScriptureHallPayload.installReceiver(Consumer)` 照 `SectDialoguePayload`。
- `sect/SectCourtyard.scriptureShelfSites(long seed, BlockPos anchor, String variant)` → `List<BlockPos>`（两座藏经阁槽位中心列的"台地标高"位置，y = terrace.elevation；殿内真实地面由放置方在世界里向上扫描）。
- `ModPayloads.PROTOCOL_VERSION = "14"`，注册两载荷。

## 2. 工作包（规则同切片 1：只改自己的文件、flock Gradle、只跑 compile 与自己的测试、`/usr/bin/python3`、不 push、提交末尾 Co-Authored-By）

### 包 S-A：命簿核心（纯 `sim/`）
拥有：`sim/engine/PlayerAffairs.java`（借阅部分）、`sim/WorldSim.java` 桩体、`sim/SectView.java` 的 `basicTechniqueId` 填值处、`sim/engine/TextKeys.java`、两份语言文件的 `world_sim.event.player.borrow.1`、`src/test/.../sim/WorldSimScriptureTest.java`。
1. `borrowable`：成员记录 → 宗门 → 传承链 `ctx.data.heritage(sect.heritageId)`：outer `[chain[0]]`，inner `chain[0..1]`，elder 整条；无传承：outer `[basic]`，inner/elder `[basic, signature]`；去掉空串与 `basic_breathing`（凡阶无秘籍）；去重保序。
2. `recordBorrow`：校验顺序 not_member → not_borrowable → already_borrowed；`borrowed.add`；事件 `player_borrow`（重要度 1，params 玩家名、宗门名、功法名 `ctx.data.technique(id).name()`）；立即结清当天（照 join 的做法）。
3. 测试：三种品阶清单、无传承宗门、exclusive 链全在长老清单（用 `SimFixtures` 的创世世界里找有传承的宗门；没有就 `WorldSimHeritageTest` 的做法造一个）、重复借拒绝、codec 往返含 borrowed。
提交：`feat(sim): scripture hall borrow rules and records in the ledger`。

### 包 S-B：方块落点与放置（`sect/SectCourtyard.java`、新 `sim/runtime/avatar/ScriptureShelves.java`、`GateBuilder.build` 成功分支一行、`GateRealizer.finish` 释放 ticket 前一行、`world sect <id> shelves` 命令（放 `GateRealizerCommands` 旁的新类 `ScriptureShelfCommands`，统筹挂）、`src/test/.../sect/SectCourtyardScriptureTest.java`）
1. `scriptureShelfSites`：从 `plan.slots()` 里取 `terraceName == "scripture"` 且 role 以 `flank_` 开头的槽位，中心 `slot.center()`，返回世界坐标 `(base.x + cx, terrace.elevation, base.z + cz)`。
2. `ScriptureShelves.place(ServerLevel level, int sectId, long seed, BlockPos anchor, String variant)`：对每个 site 从 `y = elevation - 1` 向上到 `elevation + 12` 扫描，取第一个"该格实心且上两格为空气"的 y，在其上一格 `setBlock(SCRIPTURE_SHELF)` 并给方块实体写 sectId；日志 `SCRIPTURE_SHELF sect=<id> placed=<n>/<2> at=<x y z;…>`；找不到地面就跳过并 warn。`GateRealizer.finish` 在 `releaseTickets` 之前调；`GateBuilder.build` 在 `GateRealizations.put` 之后调。
3. 测试：用 `SectCompoundRealizationTest.MemorySink` 建一座 seed 7 的山门，断言两个 site 的扫描结果落在藏经阁模板占地内、且那格上方为空气（两个模板变体 `_001/_002` 都验，变体由 seed 决定——找两个 seed）。
提交：`feat(sim): scripture shelves placed in the ledger compounds' scripture pavilions`。

### 包 S-C：服务端服务、客户端界面、命令（`sim/runtime/player/ScriptureHall.java` 桩体、新 `client/sim/ScriptureHallScreen.java`、`client/sim/ClientScriptureHall.java`、`sim/runtime/net/WorldSimPayloads.java` 的 handler 两个、`sim/runtime/WorldSimCommands.java` 的 `rank` 子命令、语言键 `screen.myvillage.scripture_hall.*`、`message.myvillage.world.scripture.*`、`commands.myvillage.world.sect_rank.*`、测试 `ScriptureHallPayloadTest`、`ScriptureHallListTest`）
1. `open`：校验 overworld、方块是经架且 `sectId >= 0`、距离 ≤ `rules.player.steward.interactRange`、命簿活跃、宗门 active；非成员或别宗成员 → 载荷 `member=false, reason=not_member|member_elsewhere`，entries 空；成员 → `sim.borrowable(uuid)` 每个 id 经 `ModCultivationRegistries.technique(registryAccess, myvillage:<id>)` 取 name/grade/category，`borrowed = sim.hasBorrowed`，`cost = rules.scripture_hall.borrow_cost_by_grade[grade]`。日志 `SCRIPTURE_HALL player=<name> intent=OPEN sect=<id> entries=<n>`。
2. `handleBorrow`：再次校验同上 + techniqueId 在 borrowable 里 → `TechniqueManualItem.manualFor` 非空 → `sim.recordBorrow` → 给物品（`player.getInventory().add` 失败则 `player.drop`）→ 标 dirty → 聊天 `message.myvillage.world.scripture.borrowed`（《功法名》）→ 回发刷新后的载荷 → 日志 `SCRIPTURE_HALL player=<name> intent=BORROW sect=<id> technique=<id> result=ok|<reason>`。节流 4 tick。
3. 客户端屏：标题（宗门名 · 藏经阁 · 我的品阶），列表每行：《名》· 品阶（`screen.myvillage.cultivation.grade_name.<grade>`）· 类别 · [借阅] 按钮或"已借"灰字；非成员显示一句 `screen.myvillage.scripture_hall.not_member`；凡阶提示一行 `screen.myvillage.scripture_hall.mortal_hint`；init 时对每个按钮日志 `SCRIPTURE_HALL_UI technique=<id> x= y= w= h=`（屏幕像素，照 SECT_DIALOGUE）；玩家离开 10 格自动关。
4. 命令 `world sect <id> rank <player> <rank>`：`sim.promotePlayer(uuid, name, rank)` → 成功/无变化/失败三种输出；`world sect <id> shelves` 由 S-B 提供。
5. 测试：载荷往返与边界；清单→行的纯函数。
提交：`feat(sim): the scripture hall screen and server-side borrowing`。

### 包 S-E：采集（`tools/world_sim_scripture_evidence.py` 新，复用 entry 脚本的 helper；`tools/tests/test_world_sim_scripture_evidence.py`）
场景：选宗门→自动落地（或 `build`）→ `world sect <id> shelves` 取坐标 → 管理命令拜入 → tp 到经架前 3 格看向它 → 右键 → 等 `SCRIPTURE_HALL_UI` → 截图（外门清单）→ 点第一本 → 等 `intent=BORROW … result=ok` → `data get entity @s Inventory` 含 `myvillage:manual_` 且组件 technique 为该 id → 截图 → 再点同一本应 `already_borrowed` → `world sect <id> rank <p> inner` → 再右键 → 清单多一门（截图）→ 借 → `world player` 显示借阅数 → 非成员：`world sect <id> leave` 后右键 → `not_member` 截图。输出 `out/preview/world_sim/scripture/`。先写不跑；统筹构建后跑。
提交：`feat(capture): scripture hall headless evidence`。

### 包 S-G：文档与版本（统筹最后派）：CHANGELOG 0.42.0、README "Scripture hall (0.42.0)" 节 + ledger、KB 43 新节、KB 41 的 manual sources 一句、AGENTS 一句、`bump_version.py 0.42.0`、brief/任务书状态行。

## 3. 顺序
S-K → S-A ∥ S-B ∥ S-C ∥ S-E（只写）→ 统筹合并、gate、采集 → S-G。

## 4. 验收
gate PASS 0.42.0；采集：外门只见链[0]、借到秘籍进背包、重复借被拒、升内门后见第二门、非成员被拒；`world_sim_entry_evidence.py` 与 `world_sim_avatar_evidence.py` 仍 PASS。

## 5. 留给 owner
1. 借阅代价（现在全免费）。2. 经架的模型（现在是书架贴图的方块）。3. 一门一本是否够（丢了秘籍怎么办：现在只能管理命令补）。
