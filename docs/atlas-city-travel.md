# 舆图城标确认传送

本次新增原生城标确认界面和服务器有界安全传送，网络协议由 54 升到 55。
读取当前主世界冻结 `WorldPlan.cities()`；V8、V9 共用接口，不改变世界规则、旧存档或城市资源。
没有城标的旧舆图仍可缩放、平移、定位，不增加虚构目的地。

## 玩家流程

1. 普通生存玩家在主世界按 H → 天下 → 舆图。
2. 点击城市方形城标，显示「是否传送至某某城？」、城名、州名和冻结入口坐标。
3. 「确认传送」只发送城市 ID 与舆图指纹，随后收卷。聊天栏显示准备、抵达或拒绝原因。
4. 「取消」或 Esc 返回同一个舆图对象，保留 `AtlasViewport` 的缩放、平移和定位结果；取消不发传送包，地图底部显示取消提示。

城标绘制、悬停和点击共用经过 GUI 缩放取整的坐标。只有同一城标按下/释放才打开确认；
超出拖动阈值、拖出又拖回、空白处开始拖动、按住时滚轮缩放或窗口重置均不能误触。
地图上的计划入口是提示坐标，实际落点由服务器在该入口附近检查后选择。
其他维度始终拒绝跨维传送，中文提示个人秘境从 H → 行迹返回、其他维度先回主世界。

## 权威与落点安全

- `AtlasCityTravelPayload` 只含 `cityId`（至多 56 字符）和 64 位小写十六进制指纹；解码及构造均验证，完全没有客户端 XYZ、目标玩家、费用或权限字段。
- 城市必须精确命中当前冻结计划；不能把州 ID、猜测 ID 或客户端入口当作目标。指纹过期直接拒绝。无费用、发现前置、任务前置或 OP 要求。
- 接收、入队判定、ticket 获取/移除、每 tick 检查和最终传送都在服务器主线程执行。没有后台 level 修改、阻塞 `getChunkFuture`、`join` 或同步整城预生成。
- 检查在线对象身份、存活/移除、当前主世界、睡眠、骑乘/载客、滑翔、观战/非本人镜头、换维度、定身、打坐、结构开关和世界边界。个人秘境仍在 `PLACING` 时暂缓；不更改秘境归属、章节、返回点或入口生命周期。
- 每玩家只能有一个在途请求；服务器容量满立即拒绝，无待处理队列。重复包不申请新 ticket，也不替换原在途请求；原请求继续按完成、超时或生命周期退出路径释放。
- 按入口加有限安全边距计算区块集合，票距为 0（FULL 可用，不主动开启方块/实体 tick）。正常服务器 tick 调度加载，服务仅用 `getChunkNow` 轮询。配置最多 9 个显式 ticket/请求；原生区块生成依赖仍由 Minecraft 调度。
- 搜索围绕入口的 first-air Y，按水平距离及有限上下偏差枚举；实际脚下必须是完整实心碰撞方块，站立玩家的身体及头部净空，周边不能有液体、伤害方块或传送门。进一步检查实体碰撞和实际世界边界；不把蹲伏/游泳的小碰撞箱当作净空标准。
- 不写世界方块、不造城门平台、不退回任意出生点。搜索耗尽就拒绝。成功后清除旧动量/坠落距离，保持原版传送后的短暂 ticket，由原版拥有其生命周期。
- 成功、异常、加载超时、离线、死亡、Clone、换维度和停服均移除本服务 ticket 与在途槽位。停服清空节流状态；普通结束保留短期冷却。过期节流项按 tick 清理，离线直接清除。移票异常保留错误日志并继续处理其余票据，明确记录 `release_incomplete`，不冒充清理完成；票据自身还按同一配置的请求寿命到期，提供有限回收兜底。

## 唯一调参来源

`src/main/resources/data/myvillage/worldplan/atlas_city_travel.json` 是本功能唯一调参文件。
服务器在 `ServerStartedEvent` 从当前 resource manager 读取；缺失或非法时关闭传送并记录原始错误，不静默使用另一组默认值。
服务器数据包覆盖在下次启动生效，不在在途请求中切换规则。客户端点击阈值读取 jar 内同一文件，不从客户端决定服务器预算。

| 字段 | 用途 |
| --- | --- |
| `enabled` | 服务器功能开关 |
| `search_radius` / `vertical_radius` | 入口水平/上下安全搜索范围 |
| `hazard_padding` / `hazard_blocks` / `hazard_tags` | 实际方块危险检查 |
| `max_chunks_per_request` / `max_concurrent_requests` | 显式加载与并发上限 |
| `load_timeout_ticks` | 包含加载和安全搜索的最长请求寿命 |
| `request_cooldown_ticks` / `feedback_interval_ticks` | 请求和拒绝反馈节流 |
| `candidate_checks_per_tick` | 全服共享的每 tick 落点检查预算 |
| `click_slop_pixels` / `marker_hit_radius_pixels` | GUI 像素的拖动与命中阈值 |

Java 只保留协议格式和资源/计算的硬安全上界，不维护第二套运行默认值。

## 本次文件

生产源：

- `src/main/java/com/example/myvillage/client/atlas/WorldAtlasScreen.java`
- `src/main/java/com/example/myvillage/client/atlas/AtlasCityTravelScreen.java`
- `src/main/java/com/example/myvillage/worldplan/atlas/AtlasCityInteraction.java`
- `src/main/java/com/example/myvillage/network/AtlasCityTravelPayload.java`
- `src/main/java/com/example/myvillage/network/ModPayloads.java`
- `src/main/java/com/example/myvillage/worldplan/travel/AtlasCityTravelRules.java`
- `src/main/java/com/example/myvillage/worldplan/travel/AtlasCityTravelTarget.java`
- `src/main/java/com/example/myvillage/worldplan/travel/AtlasCityLanding.java`
- `src/main/java/com/example/myvillage/worldplan/travel/AtlasCityTravelSafety.java`
- `src/main/java/com/example/myvillage/worldplan/travel/AtlasCityTravelLimiter.java`
- `src/main/java/com/example/myvillage/worldplan/travel/AtlasCityTravelService.java`
- `src/main/java/com/example/myvillage/worldplan/travel/AtlasCityTravelLifecycle.java`
- `src/main/resources/assets/myvillage/lang/zh_cn.json`（仅自有 `*.atlas.travel.*` 键）
- `src/main/resources/data/myvillage/worldplan/atlas_city_travel.json`

测试源：

- `src/test/java/com/example/myvillage/network/AtlasCityTravelPayloadTest.java`
- `src/test/java/com/example/myvillage/worldplan/atlas/AtlasCityInteractionTest.java`
- `src/test/java/com/example/myvillage/worldplan/travel/AtlasCityTravelTargetTest.java`
- `src/test/java/com/example/myvillage/worldplan/travel/AtlasCityLandingTest.java`
- `src/test/java/com/example/myvillage/worldplan/travel/AtlasCityTravelSafetyTest.java`
- `src/test/java/com/example/myvillage/worldplan/travel/AtlasCityTravelLimiterTest.java`
- `src/test/java/com/example/myvillage/worldplan/travel/AtlasCityTravelRulesTest.java`

本说明：`docs/atlas-city-travel.md`。未改 WorldPlanRules、WorldPlanRuntime、codec、构建脚本、城市/style 或生成器。
没有本域派生资源生成命令；配置和中文键是直接 authored 输入。

## 检查与原生采集交接

本实施者仅做自有文件的 JSON/差异/接口静态核对；未运行 Gradle、Minecraft、云端或交易场景。
轻量核对通过：22 个自有文件的换行/空白与 Java 括号、JSON 解析/重复键、25 个自有中文新增键及旧键保留、协议 55 注册、公共代码 client-only 导入边界和无直接世界方块写入；这不是 Java 编译证据。
上述 7 个 focused 测试类为待跑源码，不能据此声称测试通过。
父代理安排 Terra 在 Colab 固定完整共享源运行必要编译/JAR 和这些定向测试；
目标选择测试明确需要正常生成的 `frozen_v8.json`、`frozen_v9.json` 与配套完整资源，缺失时失败，不跳过断言。

原生基础场景保留为待执行：

1. 主世界普通生存玩家实际 H/鼠标点击城标并确认；观察中文弹窗、聊天反馈、抵达坐标及实际脚下/头部。
2. 缩放并平移后实际拖动释放不弹窗，再点击同一标记并取消/Esc；视口和玩家坐标保持。
3. 个人秘境/其他维度真实交互拒绝，确认个人返回点及章节未被地图改写；关闭结构或缩小边界应拒绝。

无安全落点、加载超时、重复请求、离线与停服的日志入口亦已准备，供需要时定位失败和核对在途/ticket 清理；不据此扩大交易或完整玩法验收。

客户端日志：`ATLAS_OPEN`、`ATLAS_INPUT action=pan|wheel|travel_cancel`、
`ATLAS_TRAVEL_INPUT action=select|confirm|cancel`。选择日志带 city/plan/viewport，可对照取消后的 viewport。
服务器日志：`ATLAS_TRAVEL state=ready|loading|denied|arrived|released|failed`，包含城市、计划指纹、落点/票据数和原因；
`ticket_release_failed` 保留原始异常，`release_incomplete` 与成功清理分开。收集实际映射输入及录像/截图后才能报告原生通过；命令直接调用、日志准备和姿态 probe 均不是映射输入。

编译、原生确认/取消、旧档实机、多人、玩家手感、FPS 目前均为 `not_verified`。
七城最终全景/街景由父代理和对应城市作者完成并在父会话展示，本域不生成替代城市画面。
