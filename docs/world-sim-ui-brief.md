# 演算系统（命簿）游戏内 UI 任务书 — 2026-10-06

统筹：Fable 会话。实施：Opus 5.5 子代理，分 A（服务端协议）、B（面板页面）两路并行，C（文档与版本）收尾。
背景：世界模拟 0.35.0 只有 `/myvillage world …` 命令和网页编年史，玩家在游戏里看不到命簿。owner 要求"把演算系统的 UI 做了"。
按 owner 2026-10-05 的方向，新系统一律作为 H 修仙面板的一页出现（`docs/ai-kb/37_cultivation_panel.md`）。

## 1. 目标

H 面板新增一页 **天下**（en: World），玩家在游戏里翻阅命簿：总览、宗门、人物、纪事、此地，五个子视图，可点行下钻（宗门 → 门人 → 人物 → 师父 …）。
只读。停止/恢复/推演仍是管理员命令，不进面板。任何玩家都能看（命簿是世界的公共传说，不是权限数据）。

## 2. 接缝（已落地，commit 9fced42，冻结）

| 文件 | 内容 |
|---|---|
| `sim/runtime/net/WorldSimQuery.java` | 客户端问什么：`Kind`（OVERVIEW/SECTS/SECT/PERSON_SEARCH/PERSON/CHRONICLE/HERE）+ `id` + `text`（≤32 字），构造器已归一化，record 等值可作缓存键 |
| `sim/runtime/net/WorldSimSnapshot.java` | 服务端答什么：按 Kind 填哪些段见类注释表；名字全部已解析为字面量，文案仍是语言键；各段上限常量 `MAX_*` |
| `client/sim/ClientWorldSimState.java` | 客户端缓存：`request(q)`（同一查询 2.5 s 内最多发一次）、`latest(q)`、`awaiting(q)`、`receive(s)`、`installSender(...)`、`clear()`；无 Minecraft 类型 |

**A、B 都不改这三个文件。** 发现缺字段/做不到，写进汇报，由统筹改。

## 3. A 路：服务端协议（Opus）

拥有：`sim/runtime/net/**`（除上面两份）、`sim/runtime/WorldSimCommands.java`、`sim/runtime/WorldSimText.java`、`sim/WorldSim.java`、`network/ModPayloads.java`、`src/test/java/com/example/myvillage/sim/**`。不碰 `client/**`、语言文件、`cultivation/**`、文档。

要做：
1. `WorldSimQueryPayload(WorldSimQuery)` serverbound：kind 用 unsigned byte，越界 id 必须抛 `IllegalArgumentException`（照 `MeditationIntentPayload` 的写法）；id varint；text `writeUtf(32)`。
2. `WorldSimSnapshotPayload(WorldSimSnapshot)` clientbound：手写 `StreamCodec`（可放 `WorldSimSnapshotCodec`）；可空段前置 boolean；列表 `writeCollection`；round-trip 测试覆盖每个 Kind 的填充形态和 `inactive`。
3. `WorldSimSnapshots`：纯构建器 `build(WorldSim sim, int daysPerYear, long calendarDay, boolean paused, int pendingDays, Function<String,String> regionName, Optional<String> hereRegionId, double playerX, double playerZ, WorldSimQuery q)`，无 Minecraft 类型，单元测试用创世世界（看 `WorldSimGenesisTest` 怎么建图）验证：各 Kind 的段按表填、上限生效（`MAX_SECTS`/`MAX_MEMBERS`/`MAX_SEARCH`/`MAX_PRESENT`/`MAX_CHRONICLE`/`MAX_RELATED`）、名字已解析、`causes` 只含 `events` 引用到的、`subjectId` = 第一个 actor。
   - OVERVIEW：`persons` = 当世翘楚 5 人（`Overview.topPersonIds`）。
   - SECTS：全部宗门，存续在前，再按 id；`distance` = -1。
   - SECT：`sect` 详情 + `persons` = 在山门者按强弱（复用/抽出 `WorldSimCommands.strongestFirst` 的排序，放成公开静态助手，命令也改用它）+ `events` = 该宗门相关（`SimEvent.sects` 含 id 或主角属该宗，importance ≥ 2，最近 `MAX_RELATED` 条，旧在前）。
   - PERSON_SEARCH：`findPersons(text, MAX_SEARCH)`；空文本返回空列表。
   - PERSON：`person` 详情（师父、仇杀者、宗门、所在域名字都解析）+ `events` = actors 含此人的最近 `MAX_RELATED` 条（任何重要度，旧在前）。
   - CHRONICLE：`recentEvents(2, MAX_CHRONICLE)`，旧在前；`causes` 用新加的 `WorldSim.event(id)` 取（纪事里可能已被修剪，取不到就不放）。
   - HERE：`region` + `sects` = 坐镇此域的宗门（带到山门的距离）+ `persons` = 在此域最强 `MAX_PRESENT` 人 + `events` = `RegionView.recentEvents` 最近 `MAX_RELATED` 条。
   - 命簿未启：`WorldSimSnapshot.inactive(q, WorldSimRuntime.inactiveReason())`。
4. `WorldSim` 纯核心加 `Optional<SimEvent> event(long id)` 和 `List<SimEvent> recentEvents(int minImportance, int limit, Predicate<SimEvent> filter)`；保持纯净（`SimPurityGuardTest`）。
5. `WorldSimPayloads.register(PayloadRegistrar)`：注册两个载荷；serverbound 处理器在 `enqueueWork` 里按玩家限频（同一玩家两次查询间隔 < 4 tick 直接丢弃，不回话），然后 `WorldSimSnapshots.build(...)` 并 `PacketDistributor.sendToPlayer`；clientbound 处理器 `enqueueWork(() -> ClientWorldSimState.receive(payload.snapshot()))`；注册时 `ClientWorldSimState.installSender(q -> PacketDistributor.sendToServer(new WorldSimQueryPayload(q)))`（该类无客户端类型，专用服务器上也能加载）。HERE 的域用 `RegionRuntimeService.currentRegion(player)`，没有就 `region = null`、其余为空。
6. `ModPayloads`：调用 `WorldSimPayloads.register(registrar)`，`PROTOCOL_VERSION` 7 → 8，注释和日志句子跟着改。
7. `WorldSimText`：加 `public static MutableComponent event(String textKey, List<String> params)`（客户端渲染 `EventLine` 用）。

验收：`/usr/bin/python3 tools/validate_world_sim.py`；`/usr/bin/python3 tools/validate_cultivation_initiation.py`（它扫 `cultivation/**` 的载荷注册，不能被新载荷触发）；`flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew test --tests 'com.example.myvillage.sim.*'`；`flock … ./gradlew compileJava`。
提交：只 `git add` 自己的路径（绝不 `git add -A`），分支 `feat/world-sim`，提交信息末尾加 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`。

## 4. B 路：面板页面（Opus）

拥有：`client/cultivation/panel/WorldPage.java`（可再加 `panel/World*.java` 助手）、`client/cultivation/panel/PanelPage.java`、`client/cultivation/CultivationProfileScreen.java`、`client/cultivation/ClientCultivationEvents.java`、`assets/myvillage/lang/en_us.json` 与 `zh_cn.json`（只加键）、`src/test/java/com/example/myvillage/client/cultivation/panel/World*Test.java`。不碰 `sim/**`、`network/**`、`cultivation/**`、文档。

### 4.1 面板枢纽的两个通用钩子（不是系统逻辑，允许改枢纽）

- `PanelPage` 加：`public void pointer(int mouseX, int mouseY)`（默认空；屏幕坐标，鼠标不在正文视口内时传 -1,-1）、`public boolean mouseClicked(double mouseX, double mouseY, int button)`（默认 false；屏幕坐标）、`public boolean takeScrollToTop()`（默认 false；返回 true 一次则枢纽把该页滚动归零）。
- `CultivationProfileScreen`：每帧 `drawPage` 前调 `pointer`；`mouseClicked` 覆写：落点在正文视口内且当前页消费则返回 true，否则 `super`；`drawPage` 里先问 `takeScrollToTop`。`View` 加 `WORLD("screen.myvillage.cultivation.tab.world")` 排在 TECHNIQUES 之后并注册页面。`setView` 方法体不得出现 `sendToServer`/`IntentSender.send`（校验器扫）。
- `ClientCultivationEvents.onLoggingOut` 里加 `ClientWorldSimState.clear()`（保留原来那行）。

### 4.2 页面结构

页面只读 `ClientWorldSimState`（通过 `request`/`latest`/`awaiting`），不持有任何账本，不直接发包（不得出现 `PacketDistributor`、`sendToServer`、`setData`、`START_NORMAL` 等词；校验器把 `panel/*.java` 和屏幕合为一份源码检查）。

- **底部 dock 一行**（高 18 + 4）：五个 `PanelButton` 子视图按钮 总览/宗门/人物/纪事/此地（当前项 `setSelected`），右侧一个 `EditBox`（只在"人物"显示，`setResponder` 实时搜索，空文本不查）。正文宽度 < 300 时 dock 两行（按钮一行、搜索框一行），`init` 返回相应高度。
- **状态**：静态记住子视图、选中的宗门/人物、返回栈（屏幕尺寸变化会重建页面）。子视图或选中项改变时 `takeScrollToTop` 返回 true 一次。
- **刷新**：`refresh(context, visible)` 可见时每帧对当前子视图的查询调 `request`（缓存已限 2.5 s 一次），所以总览/纪事/此地会自动跟着命簿走。
- **三种空态**：未收到答案 → 一张卡"正在翻阅命簿…"（`…world.loading`）；`active=false` → 一张卡，标题 命簿未启，正文用现有键 `commands.myvillage.world.inactive` 带 reason；列表为空 → 对应的"无"文案。

### 4.3 五个子视图（内容照命令输出，布局照内视页的卡片）

1. **总览**：左卡「命簿」：启元日期（`WorldSimText.date(SimDate.of(day, prehistoryDays, dpy))` + 第几日）、演算状态 chip（运转中=JADE / 已停止=RED，后随"待结算 N 日"）、档位（现有键 `commands.myvillage.world.tier.<id>`）、在世/目标、已故、宗门 存续/覆灭、纪事累计。右卡「境界」：每个境界一行 名字 + 人数 + 相对最大值的 bar。下方整宽卡「当世翘楚」：五行可点（→ 人物）。宽 < 300 时单列。
2. **宗门**：列表卡，每行 `#id 名`（GOLD_BRIGHT）、掌门、门人 N、最高境界、声望、山门 chip（已立 JADE / 未立 MUTED）；已灭行整体 MUTED 并带"已灭"chip；行可点 → 宗门详情。详情：顶部「← 返回」热区；卡「宗门」：名（状态）、坐镇域、开创（日期 + 创派人，创派人可点）、自某宗分出（可点）、覆灭日期（若已灭）、掌门（可点）、门人、最高境界、资财、声望、镇派功法、山门 x,z + 已立/未立；卡「交际」：每个对方宗门一行 名（可点）+ 数值 + 状态 chip（war=RED、feud=AMBER、none=MUTED，键 `commands.myvillage.world.relation.<state>`）；卡「在山门者」：行 = 名 道号 · 境界层次 · 身份，可点 → 人物；卡「近事」：纪事行（见 4.4）。
3. **人物**：搜索结果列表（行 = 名 道号、境界层次、宗门+身份；已故行 MUTED 带"已故"chip）→ 人物详情：「← 返回」；卡「人物」：名 + 道号（+ 已故）、境界层次 + 修为 bar（在世者 `progress`，0..1）、灵根品级 + 五行五条细 bar（`root` 万分比，金木水火土，颜色用 `PanelContext.elementColor` 若能解析到元素 id，否则 FALLBACK_ELEMENT）、年岁（`floorDiv(day - birthDay, dpy)`，已故用 `deathDay`）、师承（可点）、宗门 + 身份（宗门可点）、身在 域 + 状态（键 `commands.myvillage.world.status.<status>`）、功法、伤势（injury > 0 才显示）；已故者改显示 身故日期、死因（`commands.myvillage.world.cause.<cause>`）、死于某人之手（可点）；卡「人缘」：按 kind 分组（弟子/好友/仇家/其他 kind 原样 fallback），名字可点；卡「近事」。
4. **纪事**：最新在前（与命令相反，面板上先看最新）。行 = `[启元N年]`（MUTED）+ 事件文（importance 3：行首金色菱形 + GOLD_BRIGHT；2：TEXT）；有因则下一行 FAINT「因：<cause 文>」；行可点 → `subjectId` 的人物（-1 不可点）。
5. **此地**：卡「此域」：名、阶、灵气 lo-hi、凶险 lo-hi、修士 N 人、可否立宗；卡「坐镇宗门」：行 = 名、掌门、门人、山门 x,z、相距 N 格、已立/未立，可点；无则"此域无宗门坐镇"；卡「在此的人物」：可点；卡「近事」。`region == null` 时整页一张卡"你身在诸域之外"（现有键 `commands.myvillage.world.here.outside`）。

### 4.4 文案与渲染规则

- 事件文：`Component.translatable(textKey, args)`，参数以 `@` 开头的转 `Component.translatable(去掉@)`，其余 `Component.literal`（照 `WorldSimText.params`；A 路会加公开重载，但不要依赖它，自己写三行）。多行事件文用 `font.split(component, width)` 折行。
- 境界层次 `WorldSimText.stage(realmId, stage)`、境界 `WorldSimText.realm`、身份 `WorldSimText.rank`、状态 `WorldSimText.status`、死因 `WorldSimText.cause`（都在 `sim.runtime.WorldSimText`，只依赖 `Component`，客户端可用）。
- 新键一律 `screen.myvillage.cultivation.world.<…>`，两种语言都加（校验器要求面板源码里出现的每个 `screen.myvillage.cultivation.*` 字面量在两个语言文件里非空）；拼接出来的键（以 `.` 结尾的字面量）不受此限。中文为主、英文可读即可。标签页键 `screen.myvillage.cultivation.tab.world` = 天下 / World。
- 可点行：渲染时记下屏幕坐标热区；`pointer` 落在热区上时行底加一层 `withAlpha(GOLD, 0x18)`；`mouseClicked` 左键命中则切换并返回 true。文字一律 `PanelTheme.fit` 截断，不得溢出卡片。
- 纯算术（年岁、日期、分组、五行万分比→宽度）放进一个无 Minecraft 渲染依赖的助手（如 `WorldReadouts`）并写 JUnit 测试，照 `PanelReadoutsTest`。

验收：`flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew compileJava`；`flock … ./gradlew test --tests 'com.example.myvillage.client.*'`；`/usr/bin/python3 tools/validate_cultivation_initiation.py`、`validate_cultivation_meditation.py`、`validate_cultivation_lifespan.py`、`validate_cultivation_gain.py`、`validate_cultivation_advancement.py` 全过。A 路与 B 路并行，B 的编译不依赖 A（只依赖接缝三文件）。
布局自检：480x270、427x240、320x240 三个 GUI 尺寸在中英文下都不溢出——B 只做代码层面的保证（卡片宽度按正文宽算、窄时单列），真机截图由统筹在 owner 电脑上做。
提交：只 `git add` 自己的路径，分支 `feat/world-sim`，`Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`。

## 5. C 路：文档与版本（A、B 合入后）

- `docs/ai-kb/37_cultivation_panel.md`：Pages 表加 天下 行，Code 表加 `WorldPage`/助手与枢纽钩子，Rules 段补"天下页只读缓存、查询限频"。
- `docs/ai-kb/40_world_sim.md`：加"游戏内面板"一节（查询/快照/缓存/限频/各 Kind 上限），Package map 加 `sim.runtime.net`；Known limits 相应调整。
- `README.md`：H 面板一节加天下页；世界模拟 ledger 加 0.36.0 行（真机验证项 `not_verified` 直到 owner 看过）。
- 版本 0.35.1 → 0.36.0：`python3 tools/bump_version.py 0.36.0`，CHANGELOG 写清新增（天下页、协议 8、`WorldSim.event`）。
- `python3 tools/release_gate.py`（它自己拿锁，不要套 flock）。

## 6. 留给 owner 的

- 天下页的观感与信息量（真机）。
- 要不要把 停止/恢复/推演 做成管理员才见的按钮。
- 点人物时要不要能"寻路/传送到山门"（未做）。
