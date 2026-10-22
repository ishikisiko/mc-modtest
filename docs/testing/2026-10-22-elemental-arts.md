# 五行法修：0.81.0 Colab 定向交付

用户指定复用已有 `sword-arts-highmem` CPU High-RAM 会话。只在该 Colab 运行 Gradle、服务器和原生客户端；本地主机做源码、生成与报告工作。既有实例保留，测试 Minecraft 进程在交付前停止并确认。

## 固定源与最小检查

初始实现 `884f9634`，补充定向测试 `3e6223d9`，生存配方修正 `7d3ccea8`。每次上传保留全跟踪文件 SHA-256 清单，Colab 核对后运行。未复制用户存档；使用工具自己的独立平坦测试世界。

本地 48 项：元素作者数据、配方/生成、旧功法目录、原六招生成器。原 H 图标生成器 `--check` 通过，未改图标资源。首次默认 Python 缺 Pillow，改用此机已有依赖的 `/usr/bin/python3` 完成图标检查，没有安装或改动运行环境。

`3e6223d9` Colab 编译、jar 与 86 个 suite / 475 项 Java 检查通过。覆盖 combat、stats、cultivation.data/technique、client.combat/panel；仅排除下文已由基线复现的三项旧手臂测试。配方更正只改资源与作者数据，另跑 ElementalArtsTest、TechniqueStudyDefinitionTest 与 jar；最终结果见交付清单。

## 保留的失败与范围

首轮广筛有五项失败。在开发前固定源码 `50164e4b68a21348a3c1c08b0123fc083b64220f` 逐文件核对后，五项均复现：

- `FirstPersonArmIkTest.handAndForearmMoveWithoutPopping`：旧青锋剑第二式 4.625 tick 转腕 14.611194°。
- `FirstPersonArmIkTest.wristStaysAnatomicalAndBonesKeepTheirLength`：旧第二式左腕 4.75 tick 偏转 -45.620415°。
- `FirstPersonOffHandTest.outOfReachTheHandSlidesAlongTheShaftInsteadOfLeavingIt`：旧第二式 0.25 tick 副手沿杆跳变。
- `CombatDataLoaderTest.onlyArtDefinitionsAndTheClientWorldTrailReferenceTrails`：旧流派适配器已透传表现轨迹，但检查白名单未更新。本次补入真实透传者，仍禁止命中解析器消费轨迹。
- `TechniqueStudyDefinitionTest.everyShippedManualTechniqueCarriesTheStudyBlockOfItsGrade`：旧崩山劲等六招的独立研读成本已经与通用品阶默认值不同。本次改为按六招与五系各自作者源核对例外，其余功法仍核对通用品阶值。

三项手臂问题未在此任务改动，不能称全仓测试全绿。初始测试结果、基线复现、修正后的结果分别保留。

第一次实机启动在资源预检发现错误的灵石物品 ID 后主动停止，未计作任何玩法通过；配方改为现有 `c:spirit_stones/low_grade` 标签后重新构建与启动。失败/中止目录保留。

## 实机证据

`tools/elemental_arts_evidence.py` 以中文原生客户端映射鼠标/键盘研读、H 分配、按键施法和身法；命令只用于场景、境界、后续战斗用已学夹具、伤害边界探针及读回。普通获取中的自然材料采集不由管理员书本夹具证明。实际完成/未运行列表、失败重试、截图与源身份以 `reports/elemental-arts` 交付清单为准。

开发者截图、自动判定和 CPU 软件渲染均不代替玩家外观、战斗手感、数值平衡、音效、FPS 或广泛多人验收，这些保持 `not_verified`。金丹专属大招、第二专精及全五行反应表尚未实现。

## 最终结果

- 构建源码：`dc128bceaee8823f3fd710f16a5857fe42835bba`；最终战斗实机脚本源码：`05ec4e15e5b2d8f4d1610dedfc0c5cde6d4aa469`，两者只有验收脚本差异。
- 最终修复后 56 个 suite / 283 项相关 Java 检查通过；另补前置学习与研读结算 3 个 suite / 27 项通过。此前 475 项广筛结果保留，不重复累加为新的独立测试数量。
- `selected=completed=[learning, five_kits, weapon_and_resistance, refusals, two_players, restart, old_combat]`，`unrun=[]`。学习场景复用 `runtime-2` 已完成的五本普通研读和二十配方注册；其余六类来自 `runtime-5`，534 条记录含时间推进等基础设施断言，不等于 534 个独立玩法场景。
- 二十招均由实际按键触发，五系积蓄、爆发、护体与多段场技完成；金系穿透第二目标、木火归属持续伤害/收割、水土位移均观察。空手/剑/枪/拳套四种手持下本系伤害一致，各系抗性与穿透独立结算。
- 零灵力、境界不足、已忘技能、墙体、友军、共享护体冷却、换手持中断与冷却保留、施法者身法取消、目标运动保持完成。
- 两个原生客户端验证施法/观察、目标身法免伤、不能收割别人的灼痕、本人仍可收割、两人的护体冷却互相独立；访客进程清理已确认。
- 同一测试存档重启后保留学习及键位，临时流水障速度修饰清除，空手重新施法成功；原青莲剑歌、庚金剑诀与右键剑气回归通过。
- 游戏 `termination=confirmed`，原 Colab VM 按用户本次复用意图保留。

额外保留的诊断：水系近距潮涌最初使用了范围外的目标；位移木桩最初使用 NoAI，改为 AI 开启且主动移动速度/索敌范围为零后，实际位移通过。截图发现元素快捷栏误查专属 32px 小图，修复为复用 PanelAssets 图标/品阶底色并加五行字标。命中复查修复了元素攻击透传近战零冲量时清空目标速度的问题，最终实机确认保留目标运动。这些未通过或中止尝试均留在对应原始目录。

部分 H 绑定截图曾被后续同名采集覆盖，交付使用同一构建的四招 H 截图及运行中已下载的原始图；采集器已补防覆盖命名。公开图像与完整原始尝试分别保存，未以概念图替代实机图。

## 产物

- `reports/elemental-arts/artifacts/myvillage-0.81.0.jar`，21,629,854 bytes。
- SHA-256：`191257cc877707e374055718d5627b9b8de603067e723e1a36218aaf364b4e7c`。
- jar 内 1,408 个 class 与运行后的编译输出逐字节一致；71 项五行生成输出、五个流派资源及沿用的图标纹理与源码一致，指南页面已入 jar；5,318 个跟踪源文件哈希核对通过。
- `reports/elemental-arts/artifacts/delivery.json` 为汇总；`reports/elemental-arts/delivery.tgz` 保留原始检查、失败、日志和截图。
- [交互成长表、实机图与 jar](http://43.156.135.198:8765/elemental-arts/)。

最终集成只再修改非打包说明与截图防覆盖的工具逻辑，未改上述已验证的运行时、作者数据或 jar 资源。
