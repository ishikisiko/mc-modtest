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
