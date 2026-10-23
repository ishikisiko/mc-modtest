# 阡镇故事地点蓝图

本文记录最初独立蓝图及建筑验收。后续第一章人物、对话、任务与生产场景
接入见 [KB64](64_qianzhen_story.md)；下面的“尚未接入”描述保留该批蓝图的
原始范围，不把历史建筑证据升级为任务验收。

入口：[工具与证据边界](../contracts/tooling.md)、[建筑约束](../contracts/buildings.md)、
[工作流程](../WORKFLOW.md)、[authored 输入与命令](../../blueprints/qianzhen/README.md)。

阡镇是《灯尽之前》的西漠绿洲小镇候选。云山仍位于中州；本批没有迁移
云山、改写冻结 worldplan、注册新 settlement family 或修改已有存档。
第一章的地点安排是阡陌驿接取、长明药庐问药、青泉渠圃取样、沙埋旧驿
寻找周禾、护泉古阵查验和处置，最后回药庐复命；问灵小院承接可选入门。

## 所有权与数据

`blueprints/qianzhen/` 的六份 JSON 是默认 authored 模式的手工几何输入，沿用
`tools/json_to_nbt.py` 的 set / fill / line DSL。`manifest.json` 管理来源、
最大尺寸、概念地块原点与剧情连线；每栋的 `presentation` 管理中文说明和
审阅视角。尺寸与方块统计读取生成报告，不在文档维护第二份常量。

`walk_targets` 是可站立的脚部位置。`story_anchors` 用稳定 ID 绑定这些
位置，提供未来任务、角色和触发器的接入意图；它们尚未成为任务运行时。
现有测灵、传承、炼丹设施只引用已有 block ID。蓝图内的床、门、招牌和
空容器是实际方块；人物和任务图标没有用装饰伪造。

`tools/qianzhen/build.py` 负责校验并导出原版结构 NBT、Sponge v2 SCHEM、
展开 JSON、位置表和报告。NBT 使用仓库的 canonical gzip 写入，招牌文本
经 NBT / SCHEM 双路写入和回读。`viewer.html` 从真实方块生成离线预览，
沿用仓库 Three.js；支持正面、背面、入口、平面、下层和上层剖切，古阵
另有地下侧剖。`capture.py` 在云端启动临时回环 HTTP 服务并拍摄各视角，
退出时关闭浏览器与该临时服务，不操作已有 8765 服务。

## 必要检查与交付

- 六处边界、方块 ID、模组 state variant、入口至全部剧情目标的保守步行图。
- 门床配对、招牌和灯的基本支撑、池渠侧面及底部不直接邻接空气。
- 已声明的完整屋顶截面和墙顶接触；旧驿有意坍塌处单独保留设计说明。
- NBT 与 SCHEM 每格及招牌回读、相对地块不重叠、导出 SHA-256。
- 六个 focused tests 覆盖水面伪地面、堵路、孤床、漏水及有效对照。
- 浏览器异常、图片加载、390px 横向溢出检查，并人工查看不同朝向和剖面。

生成物在忽略目录 `out/preview/qianzhen-blueprints/`，源快照与运行日志在
`reports/qianzhen/`。面向 T3 的入口使用
`https://assets.ishiki333hhh.win/qianzhen-blueprints/index.html`。
总包包含六处 NBT / SCHEM、authored 输入、剧情位置、预览页面、画面和报告。

这是独立建筑蓝图交付，不进入 mod jar，因此蓝图本身不升版本、不要求构建 jar。
离线预览使用示意色及简化家具，不能证明原版状态属性、原生放置与碰撞、
NPC 与任务交互、声音、FPS 或玩家外观验收。这些项目保持 `not_verified`。
整镇道路、自然生成、出生点、主线引擎和任务 HUD 属于后续接入范围。

## 原生 MC 验收入口

用户明确要求 MC 内验收时，使用 Modal `qianzhen-buildings`：定向 Python
检查、同源蓝图导出、用于启动的当前 mod jar，以及原生客户端/服务器采集。
这不是全量 release，也不启动本机 Minecraft。

```bash
.venv-modal/bin/python -m tools.modal_runner run --preset qianzhen-buildings --ref <固定提交> --timeout 2400
```

`tools/qianzhen/game_evidence.py` 使用独立平坦测试世界，将六处地块分开放置。
原闭门结构先由实际结构方块界面的 SAVE 保存；随后逐扇用右键打开门，再
从每栋入口外用 W/A/S/D 连续访问全部 `walk_targets` 并返回入口。
`game_routes.py` 只从与离线检查共享的步行图规划折点，不直接移动玩家。
路线中不传送、不跳跃；RCON 只准备各栋入口起点并读取实际服务器坐标。

采集器另检查已有炼丹炉、测灵碑、传功碑的真实右键，保留日间内外画面、
代表性夜景、连续行走录像、逐格回读的游戏 NBT/SCHEM 与独立测试世界 ZIP。
原生图形使用 llvmpipe 软件渲染，不能代替玩家的审美、声音、FPS 或多人验收。
实际通过项以该次 `evidence.json` 的 `selected/completed/unrun/checks` 及
云端 `termination=confirmed` 为准；工具存在或启动成功不代表已通过。

`--cases canal_gardens,old_relay,spring_array,initiation_court` 可只重验这些
地点的连续步行，放置、保存、设施和画面仍覆盖六处。局部运行明确记录
`mapped_selected_walk_targets` 与 `walk_locations`；复用之前已通过的地点时，
必须匹配 authored 文件 SHA-256，保留原始报告、录像与对应固定源。

设施菜单补验使用 `qianzhen-facilities`，只选择放置与既有设施阶段。鼠标解除
抓取可能早于软件渲染绘制菜单，故采集器等待后保留两帧及右键录像，并要求实际
图片审阅；不把该局部运行写成重新通过全部路线或结构保存。

`tools/qianzhen/package_game.py` 可组合当前运行与之前已经完整完成的地点，
拒绝 authored SHA、生产输入或移动方法变化；每处必须覆盖全部目标、回程和
非空录像。设施补验还需带图片哈希的审阅记录。原始 `evidence.json` 不改写，
组合结论保存在 `acceptance.json`，来源与终止确认保存在 `run-evidence/`。

本轮原生结果、渠圃入口修复和下载入口见
[2026-10-23 MC 内验收](../testing/2026-10-23-qianzhen-mc.md)。

## 六地点 seeded 离线生成

`tools/qianzhen/storygen/` 已包含六个地点插件，公共层由 authored 场景提取
固定尺寸/原点、剧情角色、目标完整 state、脚部/approach 和视线保护格。
`generate_sites` 动态加载六插件，`compose_scene` 混合结果并复用现有道路、
整场景连通与 NPC 占位校验；props 在契约阶段物化，组合/导出不重复套用。
古阵保留下层 authored 构造并重建亭廊与支室，其余插件按各自语义空间生成。

```bash
python3 -m tools.qianzhen.storygen --seed 20261024 --out out/qianzhen-seeded/20261024
python3 tools/qianzhen/build.py --source out/qianzhen-seeded/20261024/source --out out/preview/qianzhen-seeded-20261024 --source-id seeded-20261024
```

前者输出 `resources/` 下六 NBT 和场景 JSON、`source/` 下物化快照及
`generation-report.json`；后者复用 NBT / SCHEM 逐格/招牌回读和 viewer，
输出仍适用于现有 `capture.py`。快照哈希不匹配时拒绝沿用生成来源报告。
浏览器采集后打包须保留相同 `--source`。详细命令与报告字段见
[storygen README](../../tools/qianzhen/storygen/README.md)。

报告区分 root seed、版本、六插件及公共工具源码身份、原 authored 输入、
实际展开方块与最终资源哈希；同输入/源码/Python 环境的同 seed 可复现。
跨 seed 比较实际方块哈希，有限语法允许相同布局；元数据差异不证明几何变化。
所有结果在忽略目录；默认 authored 输入和无 seed 生产资源保持原字节，
候选不会自动打包、自然生成、修改冻结 V4 或迁移存档。

此入口只完成离线生成与导出。原有 authored 原生验收不能覆盖这些候选；
生成候选仍须独立 Colab 原生放置、碰撞、点击、映射行走和视觉验收。
原生采集器已提供显式 `--candidate / --request / --provider Colab` 适配，
固定请求、启动前资源安装及串行执行见 [Colab 交接](../testing/qianzhen-seeded-colab-handoff.md)。
适配工具及离线测试不构成候选原生通过证据。
