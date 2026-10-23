# 天命基础能力与乙木灵体交付（0.85.0）

运行时代码及 jar 固定源：`0ee7e0272958ddd597ff10e55e15a7445e938465`。
后续提交只修改采集工具、测试与说明，未改变打包输入。

## 范围

天命主动能力入口、控制／净化、治疗修正／吸血、多来源护盾、持续领域，以及乙木灵体的实际主伤害吸血和「乙木缠根」。其他占位天命不因此获得效果。玩家入口、配置所有权与技术边界见[功能说明](../features/fate-foundations.md)。

按用户要求由多个 GPT-6.1 Sol、high 子代理并行实现独立模块，主代理整合并复用既有 `wilderness-roster-highmem` Colab。没有在用户电脑执行 Gradle 或启动 Minecraft。

## 构建与资源

远端上传固定源及文件 SHA-256 清单，逐文件核对后执行聚焦 Java 测试和 jar 构建。选中范围为 combat.foundation、cultivation.fate、SkillBinding、CombatRuntime／Discipline、combat.session、CombatHotbar 和 cultivation 客户端相关测试。

- 353 项 JUnit：零失败、零错误、零跳过。
- `jar -x generateAllStructures` 与 `validate_innate_fates.py --jar`：通过。
- `selected=completed=[focused_java,jar,fate_validator]`，`unrun=[]`；成功构建约 25.92 秒。
- 1,515 个编译类、3,485 个处理后资源与 jar 逐字节一致，另核对七个本次关键资源。
- jar：`myvillage-0.85.0.jar`。
- SHA-256：`ff75ba8cbbec028ced2b12fd5e2b478d428dfe1cff619b032184da1c64873b9c`。

本地验收工具 46 项 focused tests 通过，覆盖解析、真实输入调度、失败拒绝、窗口边界、会话身份和有界清理；这些离线检查不替代实机结果。

首次编译暴露 LivingEntity 移动字段 API 使用错误，第二次暴露测试未初始化原版注册表；修复后以上检查通过。失败日志、XML 和重试源均保留。

## 真实输入证据

玩家选择天命、H 绑定、施放、攻击、移动、跳跃和视角操作使用真实映射输入。管理员命令只准备夹具和读回；基础服务中尚无玩家消费入口的能力经管理员授予，再观察真实受击、治疗事件与服务器 tick。它们不等同于已开放给玩家的全部新技能。

| 用例 | 完整通过证据 | 观察范围 |
|---|---|---|
| selection_binding | runtime-01 | 测灵碑确认、H 绑定、失去资格后拒绝 |
| wood_root | runtime-02 | 成本、冷却、敌方定身、墙体与友方过滤、到期和灵力不足 |
| foundations | runtime-03 | 同源覆盖、异源护盾、真实受击与到期、净化、霸体、治疗修正、领域脉冲、蓄力受击后释放 |
| lifesteal | runtime-04 | 实际伤害比例、固定窗口上限、同窗口第二击不增、新窗口恢复、持续伤害不重复吸血 |
| lifecycle | runtime-06 | 同服退出重连、服务器重启、精确剩余冷却、绑定保持、护盾／控制／领域／治疗修饰清理 |
| two_players | runtime-05 | PvP 开关、友军过滤、根缠下真实鼠标与 W／跳跃、逐玩家状态隔离、图腾主伤害吸血 |

六组跨轮次聚合 `selected=completed=[selection_binding,wood_root,lifesteal,foundations,lifecycle,two_players]`，聚合 `unrun=[]`；不是单次全量执行。工具源分别为 `0ee7e027`、`c9685aa5`、`22c1fa5a`、`ca9bc2a2`、`5094061f`、`94c92b47`，打包输入一致。各轮保存独立 `selected/completed/unrun`、case 结果、日志、截图与源码身份。失败不覆盖：早期轮次定位到测碑残留遮挡、高境界攻击倍率超出靶子可准备生命、站位和新实体点击时序；修正夹具后只复验受影响场景。runtime-03 重启早期时钟读回失败，后续双客户端未正常执行，不能算已验证。runtime-05 的两次独立早期时钟查询一致，重启冷却、绑定、暂态清理均通过；其生命周期末段因自然回灵从190升到191触发严格相等断言，改为在拒绝检查前填满灵力后，runtime-06 全例通过，未改变冷却／成本逻辑或放宽断言。

双客户端读回：根缠期间 yaw 从 -180° 变为 -168°，跳跃时 Y 上升而 X/Z 保持；图腾前1HP的自定义近战使图腾消耗且目标存活，攻击者只回复0.08HP。服务器 `pvp=false` 的独立重启场景拒绝控制玩家。

## 证据边界

这批是聚焦开发者验证，不是全量 release。本轮未进行死亡／跨维度的客户端实测。玩家外观、操作手感、音效、真实硬件 FPS、长期平衡、自然获取密度及未观察的多人行为仍为 `not_verified`。领域使用现有粒子与 UI；没有独立视觉改造。流体和活塞的水平外力不在本轮支持验收范围。

## 交付与清理

本次测试游戏进程全部结束，重型锁释放；原 `combat_capture_world` 目录恢复，测试世界另存为 `combat_capture_world.fate-foundations-0850`。既有 Colab VM 按用户先前保留约定继续保留，不标记为已销毁。没有安装或启动用户电脑上的游戏。

交付 jar：[下载](https://assets.ishiki333hhh.win/fate-foundations-0850/myvillage-0.85.0.jar)。

原始证据、失败重试与截图：[证据包](https://assets.ishiki333hhh.win/fate-foundations-0850/evidence.tgz)；[逐例交付清单](https://assets.ishiki333hhh.win/fate-foundations-0850/delivery.json)。
