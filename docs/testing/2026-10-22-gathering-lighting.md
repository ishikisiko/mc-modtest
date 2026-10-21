# 聚灵阵局部柔光交付（0.74.2）

本次只改变莲座、阵翼玉饰、悬晶与镇阵灵灯的材质亮度和灵灯照明，原结构、燃料、余能与加成逻辑保持原实现。

## 实现与范围

- 源提交：`7c18917e34b3b9001733683597a30e229d7634f3`；基于 `cf49b59e` 的 0.74.1。
- `tools/gathering/visual.json` 同源控制 NeoForge 面亮度、LabPBR emission 和灵灯世界照明。前三种玉石面亮度为 10，灯芯为 12；镇阵灵灯方块光照 9 → 11。
- 玉石仍用原色彩纹理和面朝向明暗，只对指定面应用亮度；独立材质避免普通阵柱玉饰、石座和金框误发光。
- LabPBR `_s` 为 RGBA：emission alpha 为 40／64（254 为最大值，255 不代表发光）；其余通道为低反射非金属。参考 [LabPBR 标准](https://shaderlabs.org/wiki/LabPBR_Material_Standard)。不安装光影包，不把无光影截图称作泛光效果证明。
- 现有灯的存档光照可能需方块更新；拆下重放可重算。新版采集器明确重放四角灵灯后拍摄。

## 检查与证据

- 本地 `python3 tools/gen_gathering_assets.py --check` 通过；`python3 -m unittest tools.tests.test_gathering -q`：8 项通过。首次调用的 Python 无 Pillow；最终复用仓库现有标准库 PNG 解码器，消除了这项新依赖并重新通过。
- 既有 Colab CPU 高内存会话 `alchemy-furnace-0722`：固定源 4,771 文件哈希核对一致；生成检查通过；定向 `gathering.*` Java 28 项全部通过，jar 构建通过，耗时 25.35 秒。
- 0.74.1 与 0.74.2 使用同一独立云端预览世界、同机位与固定时间，分别采集莲台／悬晶／灯台／上品全景的日夜图，各 8 张；最终客户端阶段 145.49 秒。两次采集均完成，结构成型检查通过。原版比较使用未改亮度的模型／代码；新版刷新四角灯照明。
- 人工查看最终莲台日夜、悬晶夜景和灯台夜景及其旧版对照：局部青／紫玉面更清晰，保留面明暗和纹理；灯周围照明适度扩展；金框没有自发光泛白。未添加无光影光晕。
- jar 全部源资源与编译 class 核对一致，无缺失或不同；SHA-256：`4e20af55822dc7b2b8f317648322dacb802b24acfffe0ae114bd8db99bedeb7a`。
- `termination=confirmed`，本次游戏进程全部退出，Colab 会话按此前授权保留。完整证据归档 `reports/gathering-lighting-colab/delivery.tgz`（忽略输出）。
- 最终收尾提交只更新说明与 Python PNG 检查方式；mod 源、资源及 Java 测试与云端固定提交一致，因此复用该 jar 和实机证据。

交付：[日夜前后对照与 jar](http://43.156.135.198:8765/gathering_lighting/)；[云端结果](http://43.156.135.198:8765/gathering_lighting/state.json)、[源身份](http://43.156.135.198:8765/gathering_lighting/source-audit.json)、[jar 核对](http://43.156.135.198:8765/gathering_lighting/jar-audit.json)、[进程退出](http://43.156.135.198:8765/gathering_lighting/cleanup.json)。

## 边界

真实客户端证据只覆盖相同机位、固定午夜／白天的材质表现和结构仍成型；不新增玩法、广泛多人或 FPS 结论。光影实际泛光与彩色照明、玩家最终外观验收均 `not_verified`。使用既有 Colab 会话，原玩家本地世界未启动或覆盖。
