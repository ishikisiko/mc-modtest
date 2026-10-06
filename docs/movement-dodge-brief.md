# 身法闪避任务书（0.40.0）

状态（2026-10-07）：已落地于 0.40.0，各包提交 a142d7f（A）、0967446（B）、1e0a44d（C）、9c88ab6（D）、6e334a2（死区 0.2 与协议 12 修正）；采集与 PC 部署待定。

owner 2026-10-07："先做做看看"。功法任务书第二阶段"手感"的第一项（`docs/technique-system-brief.md` 3.3）。
本文是执行用的拆包说明，不是 OpenSpec。分支 `feat/movement-dodge`（自 main 3799d5d）。

## 0. 已定的默认值（owner 未反对即照此执行，写在这里以便推翻）

| 项 | 决定 |
|---|---|
| 按键 | 独立键 `key.myvillage.dodge`，默认 Left Alt，可改绑。方向取按下瞬间的 WASD 输入（8 向），无输入 = 后撤 |
| 灵力 | 首版不扣。`qi_cost` 写进数据，运行时不读（当前灵力只是一个数字，没有上限、回复和 HUD 条） |
| 攻击中闪避 | 只在收招段允许（`actionTick > move.activeEndTick()`），打断会话并重置连段，停止原因 `DODGED`；前摇、命中段的闪避请求直接拒绝，不排队 |
| 闪避中攻击 | 无敌窗内拒绝攻击意图；窗口结束后允许（闪避接攻击） |
| 用哪门身法 | 已学身法中品阶最高者，同阶取 id 字典序小的那门。不加档案字段 |
| 前置条件 | 战斗模式 `CULTIVATION`、存活、非旁观、非睡眠、非使用物品、非骑乘、非打坐、在地面（与 step 一致）。不要求手持武器 |
| 无敌窗 | 窗内取消一切 `LivingIncomingDamageEvent`，`BYPASSES_INVULNERABILITY` 标签的伤害源除外（/kill、虚空） |
| 熟练度 | 每次成功闪避 `masteryPoints + 1` |
| 动画 | 首版无专门冲刺姿势：镜头 FOV 冲击 + 残影粒子 + 声音。姿势是可选加分项 |

## 1. 已落的契约（统筹已写，编译通过后各包只读不改；要改字段先告诉统筹）

- `combat/session/CombatStopReason.DODGED`，追加在枚举末尾（线上传 ordinal）。
- `combat/DodgeDirection`：9 向枚举（NONE + 8），`fromInput(forwardImpulse, leftImpulse)` 死区 0.2（低于原版潜行的 0.3 输入缩放），`worldYaw(viewYaw)` 给出世界偏航（左 = yaw − 90，NONE = 后撤）。
- `combat/network/CombatDodgeIntentPayload(DodgeDirection direction)`：serverbound，一个字节的输入，不带任何权威。
- `combat/network/CombatDodgeStartPayload(entityId, startTick, directionYaw, distance, invulnerableTicks, durationTicks, cooldownTicks, techniqueId)`：clientbound，发给本人和追踪者，仅表现用（位移本身由服务端冲量产生的原版运动包送达）。
- `combat/network/CombatDodgeReceiver.install(Consumer<CombatDodgeStartPayload>)`：客户端装回调。
- `CombatPayloads` 已注册两者；serverbound 调 `CombatDodgeService.handleIntent(player, direction)`。
- `combat/runtime/CombatDodgeService`：桩类，签名即契约：`handleIntent`、`tick(MinecraftServer)`、`onIncomingDamage(LivingIncomingDamageEvent)`、`isDodging(ServerPlayer)`、`clear(UUID)`、`clearAll()`。
- 语言键 `key.myvillage.dodge`（zh 身法闪避 / en Movement Dodge）已在两份语言文件里。

## 2. 可复用的现成件

- `CombatStepService`：服务端冲量 = `setDeltaMovement` + `hurtMarked`，地面阻力补偿 `impulseForDistance`、碰撞/落脚检查 `chooseSafeDistance` + `isSafeDestination`（私有，B 抽成共享 helper）。
- `CombatReactionService.onIncomingDamage` 已订阅 `LivingIncomingDamageEvent`，是同类钩子的样板。
- `CombatSessionManager.interrupt(player, reason, preserveRecovery)` + `CombatSession.actionTick/currentMove`。
- `CombatCameraFx.stepSurge(degrees)`、`swingLean`，`CombatSounds.SWORD_THRUST`，`ServerLevel.sendParticles`。
- `ModCultivationRegistries.technique(registryAccess, id)`、`TechniqueDefinition.category()/grade()/effects()`、`TechniqueEffects.Movement(dashDistance, invulnerableTicks, qiCost, cooldownTicks)`。
- `CultivationService.getProfile/updateProfile`，`CultivationProfile.withTechniqueMastery`，`MeditationManager.status(player).state().active()`。
- 妖狼 `demon_wolf.json`：撕咬 windup 10、命中 tick 10–12、use_range ≤ 3.4；扑击 windup 18、命中 19–30、use_range 4–8。`tools/combat_capture/beast.py dodge()` 是慢速试验的样板。

## 3. 工作包（文件互不相交；只 `git add` 自己的路径；提交信息末尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`；重活走 `flock /home/ubuntu/code/mc/.mc-heavy.lock`；`/usr/bin/python3`；不 push）

### 包 A：身法数据（Python + 资源，不碰 Java main）

拥有：`tools/technique_catalogue/{catalogue.json,rules.json,generator.py}`、`tools/tests/test_gen_technique_catalogue.py`、生成产物 `src/main/resources/data/myvillage/myvillage/technique/**`、`src/main/resources/data/myvillage/world_sim/techniques.json`、两份语言文件里生成器管理的 `cultivation.technique.*` 键（只经生成器）、必要时 `tools/validate_world_sim.py`。

1. 生成器支持行级可选字段 `effects`（对象），覆盖 `rules.json` 的类别默认（按 effect 种类浅合并，行优先）。校验 movement 形状与 Java `TechniqueEffects.Movement` 一致：`dash_distance` 有限且 > 0，`invulnerable_ticks`/`qi_cost`/`cooldown_ticks` 为 ≥ 0 的整数，未知键拒绝。`--check` 仍幂等。
2. 加两行（category `movement`，school null，element `water`）：
   - `liuyun_bu` 流云步，grade 1：`dash_distance 3.5, invulnerable_ticks 5, qi_cost 6, cooldown_ticks 30`
   - `taxue_wuhen` 踏雪无痕，grade 2，previous `liuyun_bu`：`dash_distance 4.5, invulnerable_ticks 7, qi_cost 10, cooldown_ticks 24`
3. 分类规则加一条 `name_contains: ["步", "身法", "无痕", "遁"] → movement, school null`，确认不误伤现有 core 名字；`test_catalogue_agrees_with_a_fresh_import` 要过。
4. 跑：`/usr/bin/python3 tools/gen_technique_catalogue.py` 再 `--check`；`/usr/bin/python3 -m unittest tools.tests.test_gen_technique_catalogue`；`tools/validate_world_sim.py`；五个 `tools/validate_cultivation_*.py`。不跑 Gradle（统筹跑 gate）。
5. 提交：`feat(cultivation): two water movement techniques with dash effects`。

### 包 B：服务端闪避（Java `combat/**`，不碰 `client/**`、`cultivation/**`、载荷形状）

拥有：`combat/runtime/CombatDodgeService.java`（填桩）、`combat/runtime/CombatStepService.java`（抽共享 helper）、`combat/session/CombatSessionManager.java`（两个钩子）、`combat/CombatEvents.java`（注册监听）、`combat/runtime/CombatFeedbackService.java`（闪避的声音/粒子）、`combat/CombatCommands.java`（可选 `combat dodge status`）、`src/test/java/com/example/myvillage/combat/**` 新测试。

`handleIntent(player, direction)` 顺序：
1. 前置条件（见 0 表），不满足拒绝，reason `STATE` / `MODE` / `AIRBORNE`。
2. 选身法：遍历 `CultivationService.getProfile(player).learnedTechniques()`，经 `ModCultivationRegistries.technique` 取 category `MOVEMENT` 且 `effects.movement` 存在者，品阶最高、同阶 id 字典序。没有 → `NO_TECHNIQUE`。
3. 冷却：每 UUID `readyTick`，未到 → `COOLDOWN`。
4. 会话：在 `CombatSessionManager` 加 `static boolean tryDodgeCancel(ServerPlayer)`：无动作返回 true；有动作且 `actionTick > move.activeEndTick()` 则 `interrupt(player, DODGED, false)` 返回 true；否则 false → `TIMING`。另在 `handleAttackIntent` 开头：`CombatDodgeService.isDodging(player)` 为真则 `sendRejection` 返回 false。
5. 冲量：`yaw = direction.worldYaw(player.getYRot())`，前向 `(-sin, 0, cos)`；最大距离 `dashDistance`，`supportDepth 1.0`，用 `CombatStepService` 抽出的 `safeDistance(player, forward, maximum, supportDepth)` 取安全距离，≤ 0 → `BLOCKED`；`impulseForDistance(safe)`，`setDeltaMovement(x, 0, z)`，`hurtMarked = true`。
6. 窗口：`invulnerableUntil = now + invulnerableTicks`（开区间），`durationTicks = max(invulnerableTicks, 6)`，`readyTick = now + cooldownTicks`。
7. 熟练度：`CultivationService.updateProfile(player, p -> p.withTechniqueMastery(id, 当前 + 1))`。
8. 表现：`PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, CombatDodgeStartPayload)`；服务端 `sendParticles(ParticleTypes.CLOUD, 脚下, 8 粒, 沿前向散开)`；`CombatSounds.SWORD_THRUST` 音量 0.6 音高 0.75（放进 `CombatFeedbackService.dodge`）。
9. 日志（INFO，采集工具解析，格式照 `BEAST_DEBUG`）：
   `DODGE_DEBUG player=<name> t=<tick> result=started technique=<id> dir=<DIR> yaw=<f> distance=<f> invuln=<n> cooldown=<n>`
   `DODGE_DEBUG player=<name> t=<tick> result=rejected reason=<STATE|MODE|AIRBORNE|NO_TECHNIQUE|COOLDOWN|TIMING|BLOCKED>`
   `DODGE_DEBUG player=<name> t=<tick> cancelled_damage=<amount> source=<damage type id>`

`onIncomingDamage`：实体是 `ServerPlayer`、窗口开着、来源不在 `DamageTypeTags.BYPASSES_INVULNERABILITY` → `setCanceled(true)` + 日志。
`tick`：过期窗口与冷却，掉线玩家清理。`CombatEvents`：`register` 加 `addListener(CombatDodgeService::onIncomingDamage)`，`onServerTick` 加 `CombatDodgeService.tick(server)`，`onPlayerLoggedOut/onLivingDeath(玩家)/onPlayerChangedDimension` 加 `clear`，`onServerStarted/onServerStopping` 加 `clearAll`。
测试：`DodgeDirectionTest`（9 向的 worldYaw 表与 fromInput 死区）、`CombatDodgeServiceTest`（选身法规则、窗口/冷却纯函数）、`CombatPayloadTest` 加两个载荷的往返与边界拒绝、会话收招取消规则。`flock ... ./gradlew compileJava compileTestJava` 过了再提交。
提交：`feat(combat): server-decided movement dodge with an invulnerable window`。

### 包 C：客户端（Java `client/combat/**`）

拥有：`client/combat/ClientCombatKeyMappings.java`（加 `DODGE`，`key.myvillage.dodge`，`GLFW_KEY_LEFT_ALT`，类别 `key.categories.myvillage`）、`client/combat/ClientCombatEvents.java`（按键、收包）、新 `client/combat/CombatDodgeFx.java`（表现）、`client/combat/ClientCombatState.java`（如需记录本地闪避状态）、`src/test/java/com/example/myvillage/client/combat/**` 新测试。语言键已加，不碰语言文件。

1. `onClientTick` 里 `while (DODGE.consumeClick())`：有玩家、存活、无屏幕、`ClientCombatState.mode() == CULTIVATION`、未打坐，且距上次意图 ≥ 4 个 `ClientCombatClock` tick → `DodgeDirection.fromInput(player.input.forwardImpulse, player.input.leftImpulse)` → `PacketDistributor.sendToServer(new CombatDodgeIntentPayload(dir))`。不做位移预测（服务端冲量很快到）。
2. 静态初始化里 `CombatDodgeReceiver.install(ClientCombatEvents::receiveDodgeStart)`。收到后：找实体；本地玩家则 `CombatCameraFx.stepSurge(约 6°)` + 轻微 `swingLean`；所有玩家在 `durationTicks` 内每 tick 在脚后方加 `CLOUD`/`POOF` 残影粒子（`CombatDodgeFx`，用 `ClientCombatClock` 计时）。
3. 服务端 `CombatAttackStopPayload` 带 `DODGED` 到来时：现有 `receiveAttackStop` 对非 COMPLETED 原因已停动画和拖尾，确认本地状态清理路径正确（`resetsServerSession` 不含 DODGED），ready idle 由现有逻辑重新进入。
4. 可选加分（核心稳了再做，否则明说没做）：一段冲刺 PAL 短片。三份 style PAL 文件由 `tools/gen_sword_pal_anims.py` 生成且不许手改，所以要新文件 `assets/myvillage/player_animations/movement_combat.json` + 新小生成器 `tools/gen_movement_pal_anims.py`（`--check` 加进 `tools/release_gate.py` 一行），由 `CombatAnimationController.play` 播放。
5. 规则：`client/combat` 内只读 `ClientCombatClock`，不调 `getGameTime()`；不按物品 id 写死任何东西。
测试：纯函数单测（残影节奏、冷却剩余等）。`flock ... ./gradlew compileJava compileTestJava` 过了再提交。
提交：`feat(client): dodge key, intent and dodge presentation`。

### 包 D：采集（`tools/combat_capture`）

拥有：`tools/combat_capture/dodge.py`（新）、`tools/combat_capture/cli.py`（新子命令 `dodge`）、`tools/combat_capture/README.md`（自己那一行）、`tools/tests/test_combat_capture_dodge.py`（纯 helper）。输出 `out/preview/movement_dodge/`（index.html、manifest.json、video/、dodge_log.txt）。

先写代码和纯 helper 测试；真机运行等统筹说"构建好了"再跑（A、B、C 合入并构建之后）。

场景（照 `beast.py` 的 `dodge()`）：会话复用 `Session`；生存模式玩家、无甲、`naturalRegeneration false`、修仙战斗模式（`scene.ensure_cultivation`）；rcon `myvillage cultivation learn <user> myvillage:taxue_wuhen`（根为 `/myvillage cultivation`，拼音别名 `xuexi`）。按键用 xdotool：`self.g.xdo("key", "Alt_L")`（方向：先 `keydown a` 再按 Alt，再 `keyup a`；无方向 = 后撤）。

试验（`/tick rate 5`，每次新妖狼，`myvillage beast move` 强制出招，状态轮询 `myvillage beast status`）：
- 撕咬（妖狼 2.9 格前）：不闪避；在 move tick 4、7、9、10 后撤；在 tick 9 左闪。
- 扑击（妖狼 6 格前）：不闪避；在 move tick 14、17、18、19 后撤；在 tick 18 左闪。
- 冷却：连按两次相隔 5 tick，第二次应 `rejected reason=COOLDOWN`。
- 收招取消：挥一剑后在 action tick 3 按（应 `TIMING`），再挥一剑在 tick ≥ activeEnd+1 按（应 `started`）。
每次记录：hp 前后、玩家位移、`DODGE_DEBUG` 行（started/rejected/cancelled_damage）、`BEAST_DEBUG ... hit minecraft:player ... accepted=` 行；撕咬和扑击各录一段 F5 背视视频。页面照 `beast.write_page` 的样式。
提交：`feat(capture): player dodge trials against the demon wolf`。

### 包 E：文档与版本（A–D 合入并 gate 通过后，统筹另派）

README 新节 "Movement Dodge (0.40.0)"（按键、规则、数据、命令、ledger：手感类 `not_verified`，采集有证据的写明）、CHANGELOG 0.40.0、`python3 tools/bump_version.py 0.40.0`、`docs/ai-kb/42_movement_dodge.md` + INDEX 一行 + `41_technique_system.md` 的 Not implemented 更新 + `34_combat_data_and_capture.md` 采集表加行、`AGENTS.md` 战斗段把"两个 C2S 载荷为空"改成"攻击 C2S 载荷为空，闪避意图只带一个字节的 8 向输入"、`docs/technique-system-brief.md` 3.3 标注已落地。

## 4. 顺序与接缝

A ∥ B ∥ C ∥ D（D 只写不跑）。统筹先确认桩编译通过再放人。任何时候保持可编译；编译错误在别人的包里就等一会儿再试，不代改；`.git/index.lock` 存在就等。跨包共享的只有第 1 节的契约文件（B 之后拥有 `CombatDodgeService`，C 只读）和可选的 `tools/release_gate.py` 一行（C）。4 核 7 GB，Gradle/Minecraft 同时只跑一个，锁会排队。

## 5. 验收

- `tools/release_gate.py` PASS，版本 0.40.0。
- 采集 `python3 -m tools.combat_capture dodge`：撕咬至少一个起手点 hp 不变且有 `cancelled_damage` 或无 `accepted=true`；扑击同理；冷却拒绝、收招取消各一条证据。
- 部署到 owner 的 PC（测试机，默认部署），出图。

## 6. 留给 owner

1. Left Alt 顺不顺手（可改绑）。
2. 要不要扣灵力（得先做灵力池：上限、回复、HUD 条）。
3. 无敌窗 5/7 tick、冷却 30/24 tick、距离 3.5/4.5 格的手感。
4. 冲刺要不要专门姿势。
