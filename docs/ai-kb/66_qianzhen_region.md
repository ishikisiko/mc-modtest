# 第一章六处建筑接入聚落生成（0.89.0）

本模块的建筑是第一章的**阡陌驿、长明药庐、青泉渠圃、沙埋旧驿、护泉古阵、问灵小院**。
它们与原有连路进入新修仙大陆的统一规划及区块生成。湾口台地集仍是另一种普通聚落，不能用它的验收结果代替本模块。

## 建筑与资源所有权

- 建筑几何、相对位置、道路与原有标志物仍由 `blueprints/qianzhen/` 和 `tools/qianzhen/story_assets.py` 拥有。本次不重画建筑，也不修改原场景。
- `blueprints/regiongen/qianzhen_region_v1.json` 拥有聚落适配、外围地基过渡和选址约束。新族经 `tools/buildgen/groups.py` 注册为 `qianzhen_chapter_sites`。
- `tools/regiongen/qianzhen_assets.py` 实际调用原场景生成器，从六份原始 NBT 与原路编译区块切片；保持每格状态、空气、方块实体及相对坐标，逐片回读。不是改名字后复用云山民居。
- 输出为 `build/generated/qianzhen_region/resources`，与旧剧情场景资源目录分开；`generateQianzhenRegionAssets` 纳入打包并先于世界计划资源生成。派生 NBT 不手改。
- 布局身份覆盖输入、六份原始模板哈希、切片哈希、道路及坐标。冻结世界规则还保存完整区域元数据文件的 SHA-256；已有存档不能静默更换这份资源。

## V6 世界规划

新建“修仙大陆（种子蓝图）”世界默认使用 `planned_v6` / `worldplan_v6`。V6 确保西漠进入有限核心区域，随后按种子在西漠进行有界选址，核对完整占地归属、群系、海拔、高差、城市、出生点和其他聚落预留。不满足条件时明确缺席，不放宽约束或强制削山。

`WorldPlan.Qianzhen` 保存原点、基础标高、旋转、种子及身份；六处入口经相同变换得到实际世界坐标。查询不生成区块：

```mcfunction
/myvillage worldplan qianzhen
```

`PlannedQianzhen` 在与主城相同的 FEATURES 阶段调用原生结构模板 API，每个区块只放自己的切片。原建筑与道路内部标高不改变，外围基础渐变衔接规划地形。`WorldPlanGuard` 保护核心地面和建筑空气，空间预留同步供原生结构与命簿宗门选址使用。已有 FULL 区块与玩家改建不回填。

V1–V5 的 codec、冻结规则及原资源保留。V5 新增字节基线，不能通过替换默认预设迁移旧档；第一章建筑需要新建 V6 世界。普通世界的湾口自然结构入口保持原样。

## 剧情边界

这条路径只生成建筑，不调用 `QianzhenScene.deploy`，不把 `SceneDeploymentData` 置为 COMPLETE，不绑定剧情人物或任务。原 `/qianzhen where` 仍查询显式剧情部署；自然建筑应使用上述 `worldplan qianzhen` 查询。

建筑里的既有测灵、传承、炼丹方块仍是原物件；本次不改它们的玩法，也不声称第一章已能在自然建筑里接取。未来绑定剧情应另行核对身份、旋转后的锚点、加载完成状态与命簿权威。

## 检查

```bash
python -m tools.regiongen.qianzhen_assets
python -m tools.regiongen.qianzhen_assets --check
python -m unittest tools.tests.test_qianzhen_region tools.tests.test_qianzhen_story_assets
```

Java 重点为 `WorldPlanQianzhenTest`、原 worldplan / regiongen / region.runtime 及 `SceneLayoutTest`。重型检查复用用户指定 Colab；独立存档采集器为 `tools/worldplan/qianzhen_native.py`，逐处使用原始六份模板比对保存世界，另核对道路、告示牌、保存重启、加载票据与剧情未绑定状态。

离线切片和位置表不能代替原生生成。玩家步行、NPC 寻路、外观、FPS、多人和剧情玩法分别保留为未验证项，不因建筑检查通过自动升级。
