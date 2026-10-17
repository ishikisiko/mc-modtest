# 云山 D1：功能建筑独立蓝图

## D1 完整可玩街区（规划已确认）

`build_district.py` 读取已确认总图、`district_realization_v2.json` 的材质/边界，
以及 `baseline/d1_columns.json` 和 `baseline_houses.py` 的不可变旧城资料，
重建 70 所完整民居、四馆、地形、湖面、街阶、原门楼/鼓楼体块及树井。
地块填挖以实心台地和挡墙落实，旧城门墙体内显式贯通通道。

```bash
python3 tools/yunshan/build_district.py
python3 -m unittest tools.tests.test_yunshan_district_build tools.tests.test_yunshan_district_evidence
# 只对已经提交的固定源运行重型工作：
.venv-modal/bin/python -m tools.modal_runner run --preset yunshan-district --ref HEAD --timeout 2400
```

`out/preview/yunshan-d1-v2/runtime-build/` 包含完整零 offset SCHEM、189 个
不超过 32³ 的 NBT tiles、独立 datapack、绝对坐标 manifest 和方块检查报告。
SCHEM 是生成源蓝图；真实游戏保存的世界 ZIP 由采集器另行输出，不能混称。

原蓝图坐标保留给历史存档参考。**独立平坦预览世界统一使用配置中的
`preview_translation`**；当前整体下移 122 格，使原 Y62 地基直接接住
默认平坦世界的地表，出生点随之移动。`preview_site.py` 统一转换模板、
入口、路线、探针和摄像机，拒绝重复转换；建筑相对位置及城内高差不变。

采集器实际放置整城，并用映射 W/右键检查北移井巷、药圃后巷、南门通道、
旋转后的四馆入口、客栈楼梯与设施，保存真实客户端画面及完整世界 ZIP。
其 `evidence.json` 的 selected/completed/unrun 和 cleanup 才是运行结论。
旧广场中的鼓楼和陈设占用会单列；本次改动地块/道路的障碍不允许豁免。

玩家端交付使用独立、唯一的 DevBridge flat creative 世界；先完成云端验证。
`deploy_district.py` 默认只导出分阶段命令并逐格重放核对；显式 `--execute`
才向指定新世界写入。它验证世界身份、区块加载、命令结果及专属世界令牌，
最多四个连接，门床成对串行放置，最后写题字。不使用旧的 `d1push.py`。
默认执行不会覆盖既有进度；`--resume` 只允许恢复同一未完成任务，已完成
任务拒绝恢复，防止覆盖玩家后续修改。`prepare_delivery.py` 另可生成带
ZIP 哈希和拒绝覆盖逻辑的 Windows 导入脚本。

本轮不改变 mod 运行时，不升版本。门楼/鼓楼沿用旧 D1 体块；NPC 商人、
委托与收费住宿没有因整城落位而自动增加。玩家外观、手感、音效、FPS
和广泛多人验收仍需另行观察。

## D1 新总图规划（2026-10-18）

`blueprints/yunshan/district_plan_v2.json` 是四栋放大建筑的整街区落位候选。
客栈面向南门广场，百炼铺位于门西工坊区，百草堂及药圃位于御街中段，
问道院位于市集南侧。旧 89 地块中重组 19 个、保留 70 个，新增四栋后共 74 栋。
井巷下段向北移八格，药圃南侧新增三格后巷；四个院场局部填挖 3–4 格，
挡墙及实体台阶构造仍需下一阶段设计。这是规划候选，未写入原世界。

```bash
python3 tools/yunshan/plan_district.py
python3 -m unittest discover -s tools/tests -p 'test_yunshan_district_plan.py'
```

产物为 `out/preview/yunshan-d1-v2/index.html`、高清矢量 `blueprint.svg`、
完整 `layout.json` 与 `report.json`。页面可切换原布局/新布局/拆改叠加，
选择节点并放大，查看门外路线、院场标高和改造代价。已有独栋实机截图若存在，
会内嵌为建筑参考；它们不是新城实机图。复用服务时预览地址为
`http://43.156.135.198:8765/yunshan-d1-v2/`。

旧地块与路网来自 `d1_site_reference.json`；台地、水面和地标来自
`d1_landscape_reference.json` 的历史不可变快照，均保留源哈希。
矩形上界包含末格；建筑旋转从朝北起顺时针计数。基础世界 Y 为
`level + world_offset.y - 1`，不是建筑门槛高度；内部石阶由原建筑输入负责。
建筑尺寸只读原独栋 authored inputs，不修改独栋样板场坐标。

规划检查覆盖占地避让、地块拆改清单、道路净空、水面与地标避让，
以及南门到四处门外和市集的带标高网格连通。不能代替 Minecraft 碰撞验证。
旋转后建筑交互、当前存档地形、挡墙方块实现、整城 SCHEM 导出及玩家外观
验收均为 `not_verified`。本轮不构建 jar、不启动 Minecraft。

## 三格入口放大版与实际游戏导出

本轮已经完成云端实机建造、通行、设施交互和结构方块保存，18 个检查通过。
详见 [2026-10-17 实机记录](../../docs/testing/2026-10-17-yunshan-expanded-buildings.md)；
[游戏截图与蓝图下载](http://43.156.135.198:8765/yunshan-functional-v2/runtime/)。

放大版清单为 `blueprints/yunshan/functional_buildings_v2.json`，使用药铺 / 铁匠铺 /
传功院 v2 和客栈 v4。主开间从约 13 格扩大到 19 格，主入口统一净高 3 格；
床、门、炼丹炉等交互设施维持游戏本来的尺寸。客栈为两格宽楼梯、连续侧廊和
四间客房，各建筑增加对应的工作、储物、照明与生活细节。

这些建筑已经超出原地块，`site.placement_mode=showcase` 明确使用独立样板场。
位置不是旧城安装坐标，旧地块仍需重新规划。所有此前版本保留。

```bash
python3 tools/yunshan/build_functional.py \
  --manifest blueprints/yunshan/functional_buildings_v2.json \
  --out out/preview/yunshan-functional-v2
python3 -m unittest discover -s tools/tests -p 'test_yunshan*.py'
# 固定提交后在 Modal 运行，不能用本机 Minecraft 回退：
.venv-modal/bin/python -m tools.modal_runner run \
  --preset yunshan-buildings --ref HEAD --timeout 3600
```

`game_evidence.py` 只允许在 Modal 的 `/workspace/repo` 执行。它用 RCON 放置模板
和读取服务器状态，使用实际 W 键穿过四个入口并登上客栈楼梯，右键检查已有设施。
随后在游戏的结构方块界面点击保存，轮询实际 NBT 文件，再逐格核对方块和结构状态。
红石 SAVE 只写内存，因此这里明确使用界面 SAVE 按钮写入磁盘。

`game_export.py` 保持游戏保存的 NBT 字节原样，并将其转换成 Sponge v2 SCHEM，
保留所有 NBT 数值 / 数组类型、招牌、容器及其他方块实体。运行产物在
`out/preview/yunshan-functional-v2/runtime/`，包括四栋游戏蓝图包和独立样板世界。
具体成功 / 失败以该轮 `evidence.json` 及 Modal `state.json` 为准，不能把离线导出
或仅存在工具脚本当作实际游戏验证。

样板世界不含商人、委托或收费住宿流程；云端软件渲染不验证玩家电脑 FPS。
它是建筑验证世界，不是已经改造完成的原 `dev1` 街区。

## 保留的小版

本批为建筑候选，未修改玩家 `dev1` 存档、模组 jar 或自然生成。
保留原街区地块，按用途设计不同的外形与室内，不把独栋候选称为已经替换完成的整城。

## 小版来源

当前清单为 `blueprints/yunshan/functional_buildings_v1.json`，每栋的唯一 authored
input 是该清单引用的手工 JSON DSL。尺寸、调色板、屋顶、地块、可达目标和设施均在
各自的 JSON 中定义。没有新增 settlement family、屋顶分派或功能方块 registry ID。

| 建筑 | Authored input | 本轮空间 / 已有设施 |
|---|---|---|
| 百草堂 | `apothecary_v1.json` | 药柜、取药柜台、诊榻、后院；已有炼丹炉 |
| 百炼铺 | `smithy_v1.json` | 铺面、敞开锻打棚、砖烟道；已有炼器鼎、原版铁砧 / 高炉 / 磨石 |
| 问道院 | `initiation_hall_v1.json` | 前庭、石阶、檐廊与正堂；独立测灵石碑、传承石碑 |
| 云栖客栈 | `south_gate_inn_v3.json` | 大堂、柜台、后厨、两间客房；原版床与储物 |

药铺、铁匠铺和传功院的前向默认都是低 z。`site.rotation_quarters` 描述安装到
原地块所需的旋转；原地块定位以 `d1_site_reference.json` 中带来源哈希的快照为准，
不是对玩家当前世界的重新扫描。新建的单层候选保留较高的空气清理体积，
以覆盖原多层民居的屋顶；导出尺寸中的高度不等于新建筑实际高度。

现有功能方块只是按位置放入蓝图，未改其服务器逻辑。容器为空，商人、委托和
收费住宿尚未接入；其游戏内放置和交互本轮 `not_verified`。

## 重建与最小检查

```bash
python3 tools/yunshan/build_functional.py
python3 -m unittest discover -s tools/tests -p 'test_yunshan*.py'
```

构建输出到 `out/preview/yunshan-functional-v1/`，下设 `apothecary/`、`smithy/`、
`initiation_hall/` 和 `inn/`。每栋输出默认方向的 `.blueprint.json`、`.nbt`、`.schem`，
以及按地块旋转的 `_site.nbt`、`_site.schem`、`placement.json` 与离线检查报告。
`index.html` 是四栋总览及原街区定位图；每栋页面可旋转并查看室内剖切。

构建检查：

- 使用原地块的尺寸、标高与朝向，旋转后不越界，不给两栋分配同一地块。
- 两格净空与无跳跃楼梯路线；门按打开处理，家具和薄装饰保守视作完整障碍。
- 半格分辨率的主屋面截面连接，以及显式墙顶 / 瓦檐接触点。
- 已有设施没有被墙或家具覆盖，状态存在于当前 blockstates，接近目标可达。
- 默认与旋转版本的 NBT / SCHEM 均逐格回读对比，保留招牌文字。
- 回归测试覆盖旧屋面漏缝、墙顶漏缝、错误楼梯方向、堵入口、错误地块旋转、
  设施被覆盖和非法状态；二进制导出具有重复生成一致性。

这些检查不能替代 Minecraft 的碰撞、光照、设备交互、实际窗格表现和玩家验收。
屋面检查允许正常阶梯瓦面的边接触，不声称验证了游戏光照。

## 可选离线截图与交付

预览使用仓库 vendored Three.js，功能方块轮廓读取它们原有模型，未修改这些模型。
材质采用色块，普通家具采用简化形状，页面明确标注不是游戏截图。

仅在实际人工预览交付时复用正确的 HTTP 服务。本机既有服务目录为 `out/preview/`，
人工入口为 `http://43.156.135.198:8765/yunshan-functional-v1/`；不要占用或替换同端口的其他接口。
有可用的 Playwright 与 Chromium 时可运行：

```bash
# 可通过 PLAYWRIGHT_CORE 和 CHROMIUM_EXECUTABLE 指向已有安装。
node tools/yunshan/capture.mjs out/preview/yunshan-functional-v1 \
  http://10.3.0.5:8765/yunshan-functional-v1
# 截图产生后再构建一次，将代表画面收进交付 ZIP。
python3 tools/yunshan/build_functional.py
```

交付包是 `yunshan_functional_buildings_v1.zip`，包括逐栋蓝图、定位说明、源 JSON、
报告与已有代表画面，不含游戏存档。`_site` 版本已经旋转，Clipboard Offset 为零，
其最小角对齐 `placement.json` 的 `min_corner`。默认版本不要直接贴到南门。

文件显式包含空气来清除旧屋，安装前应备份当前世界对应区域，先在副本核对
边界、门朝向、旧屋顶清理和实际功能。不能用忽略空气的粘贴完成旧屋替换。
本轮没有执行安装，也不提供未经实机验证的自动覆盖命令。

## 保留的旧版与原资料

客栈 v1、v2 源文件与已交付目录保持原样。v2 把两排下半砖改为上半砖，修正屋面
纵向漏缝；v3 改为双层半砖，补齐檐口下方与墙顶间的半格收口，室内和建筑外轮廓不变。
历史命令 `python3 tools/yunshan/build_inn.py` 仍默认重建独立 v2；当前批次请用上述清单命令。

原资料在 `out/yunshan-blueprint/`、`out/yunshan-d1-build/`、`out/yunshan-scale-probe/`。
第一轮保存的 140 个文件备份为
`out/preview/yunshan-inn-v1/yunshan_d1_before_inn_v1.zip`，逐文件哈希位于同目录
`baseline-sha256.json`。这些忽略目录不是 Git 交付内容。

原整街区 `yunshan_d1_district.schem` 的旧记录指向玩家电脑的 WorldEdit 目录；
本轮未连接读取，备份 ZIP 不含玩家世界或该原始 SCHEM。整街区的下一版应在
实际应用新建筑并验收后重新导出，不能把本批独栋 ZIP 称为已更新的整城蓝图。
