# 湾口台地集：普通世界与修仙大陆聚落（0.88.0）

在普通主世界开启“生成结构”后，新的合适区块会出现湾口台地集。
使用 `/locate structure myvillage:bay_terraces` 查找；该 ID 是真实注册的原生结构。
世界种子决定候选位置与四向整体旋转，内部布局使用已确认的围场、坡巷与上台民居方案。
这不是任意聚落自动设计器，也不要求切换到修仙大陆世界类型。

## 资源与几何归属

- `blueprints/regiongen/bay_terraces_v1.json` 拥有地形、河湾、建筑位置、道路连接、标高、植被、材料及自然选址参数。
- 新聚落族经 `tools/buildgen/groups.py` 注册，原云山建筑仍由各自生成器拥有。
- `tools/regiongen/bay.py` 将道路交叉点、门前平台与相邻道路边缘作为共同约束求解，直线段安置连续台阶；实际方块再接受完整模板、净空、支撑、楼梯朝向、地基、河床和连通检查。
- `tools/regiongen/bay_assets.py` 生成 `build/generated/regiongen/resources`。地表逐列数据与 16×16 的 NBT 覆盖层来自同一方块结果，覆盖层保留建筑内部空气和告示牌；生成时逐块回读 NBT。
- Gradle 的 `generateBayTerracesAssets` 负责打包前生成，不手改输出。
- `bay_terraces_v1_hashes.json` 固定 V1 全部打包资源的字节身份，生成时拒绝漂移；后续新布局保留旧文件并使用新版本。

## 自然生成与旧档

`BayTerracesStructure` 使用原生 biome predicate、random-spread structure set 和高度采样选址；
候选高度与高差越界则拒绝。位置不合适时不会把任意山峰削平成台地。
`BayTerracesLayout` 是有边界校验的不可变数据，`BayTerracesPiece` 保存原点、旋转、站点种子与布局身份。

包含过渡带的结构占地不超过原生引用范围。每次 `postProcess` 只处理当前区块的列，
地形读取原生生成器的未修改高度，覆盖层旋转后仍归一个区块；不强制加载邻区。
`BayTerracesGuard` 在当前装饰阶段读取已缓存的原生结构引用，保护核心地表、建筑空气和植被，
同时覆盖普通 WorldGenRegion 写入及原版矿石的直接 section 写入。自身切片通过有界 scope 放置；
scope 由 try-with-resources 关闭，不拦截玩家在 ServerLevel 上的修改。
外围逐渐回到原生高度，局部水面在完全拥有的核心内闭合，不声称已实现跨区域水系协商。

已有 FULL 区块不回填、不重建。旧普通世界只在尚未生成的合适区块遇到新聚落；
修仙大陆 V1–V4 的冻结规则和城市资源保持原样。
已有湾口结构引用版本化资源及其布局哈希，不能用修改 V1 资源的方式更换已保存结构。
后续布局版本需要新资源身份。

## 最小检查与证据范围

```bash
python -m unittest tools.tests.test_bay_terraces
python -m tools.regiongen.bay_assets
python -m tools.regiongen.bay_assets --check
# 远程固定源，含资源、regiongen Java 单测、jar，以及普通世界生成/重启检查：
.venv-modal/bin/python -m tools.modal_runner run --preset bay-terraces-native --ref <commit> --timeout 3600
```

原生采集器 `tools/regiongen/bay_native.py` 建独立普通世界，实际执行 locate，
只用有界加载票据触发自然生成；不会 `/place` 或导入聚落方块。
先保存部分生成与管理员测试标记，重启后补齐区域，核对结构身份、覆盖层、核心地表、告示牌及标记保留。
测试票据释放后正常停服，独立交接存档开启创造与作弊。

离线通过不代表原生通过。运行结果见对应 `reports/regiongen/bay-native/`，
以 `selected/completed/unrun` 与 `checks[*].pass` 为准。
首次完整运行保留在源 `6c95bd44` 的报告中：原生定位与重启保留通过，
装饰覆盖造成 337 个方块差异，另有四处告示牌文本比较差异，故整体失败。
原版文本 codec 允许把无样式文字编码成 JSON 字符串，比较器须与等价的 `{"text":"…"}` 形式统一，仍严格比较文字和样式。
后续修复不能将该轮报告改为通过；复验使用新固定源和独立新世界。
本工具不自动执行玩家步行；玩家通行、外观、FPS、NPC 寻路和多种子原生覆盖分别记录，不能由生成检查推断。
新聚落目前没有居民、商业或剧情绑定。

0.87.0 本次实际定位、固定源、完整存档比对和用户游戏交接见
[自然生成验证记录](../testing/2026-10-25-bay-terraces-worldgen.md)。

## 修仙大陆 V5 接入

新修仙大陆的统一世界计划使用 `planned_v5` / `worldplan_v5`。`blueprints/worldplan/rules_v5.json` 拥有选址次数、半径、数量上限和避让距离；布局尺寸、坡度和群系准入从现有聚落生成产物编译进冻结规则。现有湾口 V1 方块资源保持不变。

`WorldPlan` 在城市和出生点确定后，对各地区进行有界、确定性选址。选中的聚落保存 ID、地区、原点、旋转、站点种子和布局哈希；地区过渡带、海面、高差、群系或既有预留不满足条件时跳过，不能强造一个悬空地点。选址仅覆盖有限核心地区，不声称实现无限外域聚落。

聚落完整占地及缓冲进入 `RegionGeometry`，由既有 `GatePlacement` 避让；`PlannedChunkGenerator.createStructures` 同时拒绝碰撞的原生结构。V5 禁止独立随机湾口结构再生成一份。`PlannedSettlement` 在与主城相同的 FEATURES 阶段调用原 `BayTerracesPiece` 实现器，每次只写当前区块；`WorldPlanGuard` 保护核心空间和矿石直接写入路径。地下及外围衔接继续由既有聚落实现器处理，不改变全局地形算法。

`/myvillage worldplan settlements` 输出冻结站点，`worldplan.json` 包含同一清单。它与主城一样是规划切片，不是原生 StructureStart；普通世界继续使用 `/locate structure myvillage:bay_terraces`。舆图的地形采样仍是装饰前地形，不承诺展示聚落局部地表或新增聚落图标。

V1–V4 的 codec、冻结 JSON 和资源继续保留，V4 的 239 个资源另以已交付 jar 的字节哈希锁定。完整云山与生活布局保持原样。旧档不会切换成 V5；本次不自动安装或重开玩家正在游玩的世界，也不新增聚落人物、商业或剧情绑定。

针对性检查为 `WorldPlanSettlementTest`、现有 worldplan/regiongen 测试及资源检查。`python -m tools.worldplan.settlement_native --request <fixed-source-request.json> --out <new-directory>` 只在明确授权的 Colab/Modal 工作区运行；它使用独立新世界，完整比对一个规划聚落并检查重启保留，不能代替人工手感或多种子原生验收。

0.88.0 的固定源、首轮采集器误报、复验与旧版兼容证据见 [主线聚落接入验证记录](../testing/2026-10-25-settlements-mainline.md)。
