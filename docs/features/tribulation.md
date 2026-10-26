# 正式金丹雷劫 demo

筑基后期 `myvillage:foundation_late` 到金丹初期 `myvillage:golden_early` 改为主动引劫。普通静坐不能跳过；炼气九层筑基丹、其余小境界与已经金丹的存档保留原行为，不补劫。没有演练模式、测试模式 UI、force 晋升或模拟奖励路径。

## 玩家入口

1. 达到当前筑基后期 realm 定义的修为与稳定度要求，学会基础吐纳、已觉醒且寿元有效。默认资源要求修为 4200、稳定度 2100；唯一要求来源仍是 realm JSON，雷劫配置不复制它们。
2. 收功，落地，离开飞剑或其它坐骑；生存／冒险模式，在露天选择足够平整、无液体且区块已加载的活动场地。
3. 按原 **N** 键，或 H 修炼页的 **主动引劫**。接受后用一次文字说明规则和失败代价，顶部持续状态条显示准备倒计时。默认准备 6 秒，收功键可取消初次准备，不扣稳定度。已经起雷后不能用收功取消；重登准备不提供取消已开始雷劫的旁路。
4. 正式雷劫独立于静坐；允许场内移动、服丹、用符、既有防御技能。既有施法／服药本身的成本、冷却和资格不变。

默认三轮六道主雷，另有四次轮间余雷。主雷在正式阶段第 10、30、55、75、105、130 秒落下；第 140 秒最终结算，加初次准备共约 146 秒。全部时序、伤害比例、范围与失败数值以 `tools/tribulation/rules.json` 为唯一作者源。独立 fixture 数据包可以缩短时间，但必须标记其规则哈希，不能声称是自然时长证据。

主雷预警为紫圈与紫白雷弧，锁定渡劫者本身，不能靠单帧闪避全免。`myvillage:tribulation_unavoidable` 只让身法无敌判定放行；原版 `bypasses_cooldown` 仅防止此前受伤的短无敌窗口吃掉整道主雷。没有加入 `bypasses_invulnerability`、绕甲、绕抗性或绕护盾标签。原版护甲／抗性、五行护体、丹盾、共享 `ShieldRuntime` 与黄心仍走一次原伤害管线。主雷伤害基数取起劫时最大生命并冻结，不随重登或中途装备重抽。

轮间余雷使用金圈；落点在起劫时生成并保存，预警与命中复用同一固定落点，离圈可躲。所有命中只检查本尝试的 owner，不扫描或伤害旁人，不绕 PvP 伤人。效果不使用 `LightningBolt`，不点火、不变形村民、不破坏方块、不改变全图天气。视觉与状态 payload 只发本人，局部原版雷声可被附近玩家听见。

## 范围、失败与生命周期

- 场地中心、维度、半径固定。默认水平半径 12 格、上下 8 格；出界有持续 HUD 警告，连续 5 秒后失败，返回圈内归零。不会硬锁玩家坐标。
- 起劫拒绝骑乘、鞘翅与能力飞行。`public static boolean TribulationManager.isActive(ServerPlayer)` 包括初次准备、正式阶段与重登准备，供飞剑起飞互斥使用；运行中出现骑乘／飞行也结束尝试。
- 只有本执行器、同 owner、同 attempt、同一次 DamageSource 的雷击获得非致命失败保护。护甲、抗性和盾处理后，若剩余伤害会致命，在应用生命损失前截到配置保命生命；已有更低生命不会被治疗。黄心按原版后续步骤真实消费。默认保命 2 生命，稳定度减 210，修为与境界保留，已用符丹不返还，调息 10 秒后可重试；没有永久负面或凭空复活。
- 外界真实死亡、换维度、管理员改境界与切模式显式终止，不赠送晋升。管理 stop、初次准备取消与普通 shutdown 不扣失败稳定度；正式出界或飞行失败会扣一次。终态保存幂等标志，重登／停服不会再次扣除。
- 成功仅在所有冻结步骤消费且最终时长到达后执行一次 `CultivationService.replaceProfile`。基于当时完整 profile 做 `withRealmAndStage`、修为归零、实际稳定度向下取整减半，保留其它字段。属性扩容不补生命／灵力。境界变化的服务提交会立即停止外部尚在进行的雷劫；雷劫自身先写成功终态再提交，避免被误认为管理员中断。

## 持久化与资源

每个玩家在 NeoForge persistent data 的 `myvillage:tribulation` 键保存 schema 1 JSON，与 cultivation attachment 一起进入同一个玩家 NBT 文件。含唯一 attempt UUID、owner、源／目标（冻结 rules 中）、起始修为要求与稳定度要求、最大生命伤害基准、完整冻结规则、余雷落点计划、维度／中心、已消费步骤、剩余准备、在线 elapsed、出界／调息时钟和结果。规则或坏档无法读取时保留原始值并拒绝新起劫，不静默重抽。

先持久化雷步骤消费序号，再调用一次 `hurt`；最终结算与失败标志也在同一主线程提交到同一个玩家数据对象。没有第二个独立 SavedData 文件与玩家档案相互竞争。正常存档／重启读取的是整份玩家 NBT；本 demo 不承诺跨外部手工回滚玩家文件的事务。

断线暂停，重登默认 4 秒准备后继续原 elapsed 与原步骤。停服保存暂停；即使读取上次正常存档时没有 logout 标志，登录仍会进入重登准备。场地未知区块不推进雷序，不强加载、不补离线时间；玩家自身仍在线加载时，出界宽限继续计时，不能靠远离到劫场卸载逃过失败。已完成的雷不重放，已消费护盾不退款；共享暂态护盾仍遵循其原有离线／重启清理规则，本执行器不会重建余额或重复授盾。

`tools/gen_tribulation.py` 拥有：

- `data/myvillage/tribulation/golden_core.json`
- 两种 `data/myvillage/damage_type/tribulation_*.json`
- `data/myvillage/tags/damage_type/tribulation*.json`
- `data/minecraft/tags/damage_type/bypasses_cooldown.json`（只追加主雷）

修改 authored JSON 后运行生成器；不要手改输出。资源加载使用服务器数据包，起劫时冻结，`/reload` 不会改变正在进行的同一次雷序。

## Colab 验收入口

所有 Gradle、服务器、客户端均在用户指定的 Colab 执行，实际范围见[交付记录](../testing/2026-10-27-cultivation-demos.md)。以下管理员命令只是 fixture／读回；本轮成功场景使用真实 N 键起劫。

在专用新验收存档准备平整露天平台、启用作弊；目标需已正常觉醒并学会基础吐纳，或用现有管理员 fixture 准备：

```mcfunction
/myvillage cultivation awaken Player
/myvillage cultivation initiate Player
/myvillage cultivation setrealm Player myvillage:foundation_establishment myvillage:foundation_late
/myvillage cultivation setprogress Player 4200
/myvillage cultivation setstability Player 2100
/myvillage cultivation qi fill Player
/gamemode survival Player
/myvillage tribulation status Player
```

`awaken` 对已觉醒目标可能拒绝重复，保留既有灵根即可。实际修为／稳定度 fixture 数字应从最终 realm JSON 读取。平台、装备、符丹与状态可由 fixture 准备；自然起劫必须实际映射 N 键或点击 H 中按钮。起劫后不要切创造飞行摄影，否则会明确终止；可由另一观察客户端摄影，但本人视觉 payload 不发观察者。

```mcfunction
/myvillage tribulation start Player
/myvillage tribulation status Player
/myvillage tribulation stop Player
```

权限 2；start 也要求完整资格、场地、生存／冒险与落地，启动同一正式执行器；stop 是无奖励的管理终止，没有 force。玩家收功只取消初次准备。

`status`、`start`、`stop` 每目标输出一行：

```text
TRIBULATION_STATUS {"schema":1,"attempt_id":"...","owner":"...","phase":"ACTIVE","elapsed":620,"next_strike":2,"main_completed":2,"remaining":2180,"preparation":0,"recovery":0,"source_realm":"myvillage:foundation_establishment","source_stage":"myvillage:foundation_late","target_realm":"myvillage:golden_core","target_stage":"myvillage:golden_early","center":{"x":0.5,"y":65.0,"z":0.5},"dimension":"minecraft:overworld","radius":12.0,"paused":false,"outside_ticks":0,"penalty_applied":false,"result":"渡劫中","rules_sha256":"...","plan_sha256":"...","health_basis":200.0,"cultivation_cap":4200,"required_stability":2100,"player":"...","player_name":"Player","unreadable":false,"command":"status","accepted":true}
```

schema：所有时钟单位为 tick；`next_strike` 是下一待消费步骤的零基索引，包含余雷；`main_completed` 才是已完成主雷数。`remaining` 是正式时长减 elapsed，不含准备与调息。`phase` 为 `PREPARING/ACTIVE/RECOVERING/SUCCEEDED/FAILED/STOPPED/NONE`；暂停不改变 phase，由 `paused` 与 `preparation` 标识。无尝试时 `attempt_id:null,phase:NONE`，不输出不存在的计划字段。两个 SHA-256 对冻结 rules 和冻结落点数组的规范 Gson 序列化计算；重登与重启必须保持它们和 attempt_id 一致。`penalty_applied` 只标识失败扣除，不代表成功奖励。`command/accepted` 是本次管理命令结果。

建议 selected 场景：

1. **真实 N 与准备取消**：完整资格，映射 N；看到准备文字／界面后映射收功取消，验证无损；再次 N 起劫。普通静坐不升级。
2. **主雷、防御、护身符**：主雷预警可读；映射使用真实护身符，读共享护盾余额，等待主雷真实消费；护甲／抗性／黄心与恢复有效。映射身法不能让整道主雷被其无敌窗口吞掉。受伤／移动不收功终止雷劫。
3. **余雷与边界、旁人**：金圈预警期间移动离开；其位置直到命中不变。旁人站圈内不受雷伤。出界显示倒计时，回圈清零；超时只扣一次稳定度。
4. **重登与重启**：已完成至少一雷，记录 JSON、profile 与真实护盾消费；断线／上线读取同 attempt、plan/rules 哈希与 next_strike、elapsed（只允许线上 tick 差）。再正常保存停服、启动、上线，等待重登准备后继续，已结束雷不重放、余额不退款。
5. **失败与成功**：低血 fixture 等正式雷触发非致命失败，生存存活、原境界／修为、稳定度只减一次；重登／停服后再次读回。独立成功场景通过真实护盾／丹药撑过六雷，验证恰一次晋升、修为 0、稳定度减半、生命灵力不补满。管理员生命／护盾 fixture 只能标注 fixture，不可声称自然平衡。
6. **管理生命周期**：start 后通过已有管理员命令改境界、stop、换维度、外界 kill，分别观察明确终止，无奖励；已金丹旧档不补劫；炼气九层仍需原筑基丹。

定向源检查交给父代理：

```bash
python3 tools/gen_tribulation.py --check
python3 -m unittest tools.tests.test_tribulation -v
# 仅父代理在指定 Colab 执行：
./gradlew test --tests 'com.example.myvillage.cultivation.tribulation.*' --tests 'com.example.myvillage.cultivation.GoldenCoreProgressionTest' --tests 'com.example.myvillage.cultivation.meditation.AdvancementProfileTransitionTest' --tests 'com.example.myvillage.cultivation.network.MeditationIntentPayloadTest' build
```

JUnit 覆盖正式过渡／其它阶段、真实配置时序和预警、非致命截断与稳定度一次扣除、断线／重启 codec 与一次晋升、边界／owner／余雷固定落点、payload codec，已在 Colab 执行。本轮实机按默认自然时长完成六道主雷与一次晋升，使用真实护身符/服丹输入，并验证低血非致命失败、稳定度一次扣除、重启保留同一 attempt 和雷序、御剑互斥。没有用加速数据包替代时长；装备/角色/消耗品由管理员 fixture 准备，因此不代表自然生存平衡。边界/旁人/死亡等完整矩阵未穷举，玩家外观、手感、音效、真实电脑 FPS 与更广泛多人验收保持 `not_verified`。
