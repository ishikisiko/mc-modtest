# 八州主城正式运行时接入

本批工作区 `/tmp/mc-default-cultivation-20261028`，分支 `feature/province-capitals-20261028`，基线 `e5dd97edad5d424cda0d9dc969fd44afb83bd05a`。三个实现者共享父工作区；本文仅说明 runtime 写入域。没有提交、合并、推送、升版本或运行测试。

## 正式资源与冻结身份

入口：

```bash
python3 tools/province_capitals/game/compile.py --out build/generated/province_capitals/resources
```

依次调用 `regional/export_game.py` 与 `central/export_game.py`，只由对应作者写各城 `city.json`、heightfield、NBT；聚合器只写 `capitals/v1/index.json`、`worldplan/frozen_v8.json` 和新建世界 preset。`--aggregate-only` 用于作者已经正常导出后的聚合，不是检查模式。

基准输入为原有 `generateWorldPlanAssets` 任务正常生成的 `build/generated/worldplan/resources/data/myvillage/worldplan/frozen_v7.json`（可显式传 `--base-rules`）。聚合器只读该产物，不调用旧 validator/check/test。Gradle 新增 `generateProvinceCapitalAssets`，依赖原资源任务，新增独立资源源集。打包时仅排除旧输出目录里的新建世界 `cultivation.json`，让 V8 preset 生效；不改写旧 V1–V7 frozen JSON、结构或旧生成器源码。

每城必须为 `capital_game_bundle` schema 1，身份为 `capital_<region>`。`layout_family_id` 必须等于公共 catalog 的布局族；具体 `layout_id` 使用 `life.layout_id`，`layout_sha256` 是聚合后的真实资源内容身份，两者不能混用。缺城、重复州域、尺寸/路径/摘要错误直接失败，不输出七城世界。

`manifest_sha256` 对协调契约中的消费字段（schema/format/city_id/region_id/display_name/layout_family_id/size/source_offset/placement/terrain/tiles/entries/life）做排序紧凑 UTF-8 JSON 摘要；`bundle_sha256` 再冻结该 manifest 摘要及真实 terrain/NBT 字节摘要的排序映射。source、status、报告和时间戳不参与几何身份，原始 `city.json` 摘要另记入 index 供追溯。聚合器不覆写作者清单。嵌套字段也只投影实际消费的 placement/terrain、tile 坐标/模板/摘要、入口和 life 布局/位置字段；fixture 报告、绑定状态、大小统计等诊断字段不会改变冻结几何身份。

V8 的 `level.dat` 只保存这八组引用与摘要、世界规则和 plan fingerprint；不把数百万高度项或整城方块塞入 NBT 字符串。`CapitalGameBundle` 从匹配的 classpath 资源读元数据和短整型地表，先核对冻结身份。实际生成/生活绑定另核对 ResourceManager 中的 manifest/terrain，逐 tile 核对实际 NBT；数据包替换不允许悄悄改变旧城。重载清除资源核对缓存。纯 JDK V8 CLI 的 classpath 需包含相应生成资源目录，V1–V7 不新增该要求。

## 世界计划与区块写入

V8 独立注册 `planned_v8` / `planned_source_v8`；原 codec ID 和旧分支仍保留。V8 `WorldPlan.cities()` 为八城；旧 `city()` 仅用于 V1–V7，在 V8 明确拒绝单城解释。`capitalOf`、`capitalAt`、`capitalTerrainAt`、入口、出生、宗门、聚落、舆图全部读取同一放置真值。

州域与城址共用 `rules_v8.json`：完整八州，扩大核心圈，七州环绕中央锚点，魔域仍为封闭州。城市候选先尝试州中心，再在配置界限内确定性搜索。把完整长方形及融合/结构外缘扩张到 warp 位移上界后，以四角半平面证明其完整归属；不会将中州塞入旧单城 host 半径。候选耗尽中止整份计划。

`size` 是格数。V8 单独允许 2048×320×2048 内的资源，不放宽旧 512/128 等断言。中州保持 absolute `originY=0`；sea_relative 使用海平面减 anchor_y；terrain_relative 使用候选中心自然地表**第一空气格**减 anchor_y（沿用 WorldPlan.surfaceY 语义）；资源 heightfield 自身仍严格为 top-solid，不混用两者。完整局部 Y 范围超出主世界 [-64,320) 则拒绝候选。

heightfield 编码为 gzip little-endian int16，z-major，数值为本地最高实心地面格。`WorldPlan.surfaceY` 仍统一返回第一空气格，即 `originY + topSolid + 1`。城市内部精确使用作者地面，外缘用最近边缘高度向自然地形连续融合；不将山基/空气枚举成全城字典。SURFACE 后按当前 chunk 从原版基岩带之上填实连续地基、恢复作者 top/subsurface/fill。CARVERS、结构起点、装饰直接写入和矿石避让读相同保留区。保留区进入 RegionGraph，V8 宗门选址用新州域半径并避让全城及外缘，旧门址半径规则不变。

FEATURES 每次至多读取当前城市当前 chunk 的一个稀疏 NBT。每 tile 只接受本地 0..15 X/Z、规定 height、有限 palette/blocks、空 entities；压缩输入与 NBT 内存计数均有界。直接写当前 `ChunkAccess`，显式空气、方块实体 NBT 和液体后处理随该 slice 处理，不使用永久缓存积攒整城模板，也不写邻区块。没有 overlay 的 tile 由 heightfield 生成地基即可。已有 FULL 区块没有补建入口。显式关闭结构生成时不放城市 NBT，查询会说明结构已关闭。

V8 初始出生采用最低阶非魔域主城真实入口，不另造平地出生垫覆盖道路；区域运行时的出生查询也使用该位置。CreateSpawnPosition 仅用于第一次建档，已有 spawn-bound 状态不重算。

## 生活与商业

`NpcLifeSites` 只在某城至少一个服务位置所在 chunk 已加载后按 cityId 幂等登记。坐标唯一来源为 bundle.life.places，fixture 只观察已有方块，不放块、不申请 chunk。绑定核对服务器世界边界、真实州域、既有站点位置和布局一致性；失败的城市不会自动开商业或分配向导。旧 `yunshan`、旧布局别名与仓库保持原路径，不迁移到 `capital_zhongzhou`。

七州首轮正式输出每城 159 个生活位置（含住宅），超过旧 life_rules 的 128 站点容量。V8 独立冻结 `planning.life_places_limit=192`，经 `CapitalGameRules.lifeOpener` 仅覆盖本世界的 max_places_per_site，再走原 LifeRules parser、WorldSim facade 和 LifeCodec 的完整验证；创建与恢复使用同一预算。旧 V1–V7 仍使用原 128 配置，结构上限 256 和旧断言不变；没有截断作者的位置表。纯核调用方也需用该 opener 构造对应 V8 SimData。

通过既有 `WorldSim.registerLifeSite`、availability、initializeCommerce、staffCommerce、staffTownGuide facade 写原命簿。每城仓库/库存/岗位均继续由原 siteId 规则隔离；没有另一份常驻 NPC、实体人物或账本。门店岗位只取本州现存、合资格且未被占用的命簿人物。V8 Genesis 将原有散修人口池轮流分配至八州，后续 rogueHaunt 也包含魔域；普通 sect 准入和封闭通行规则不因此放开，旧世界使用原人口分支。小人口档或人物死亡/旅行仍可能留下岗位空缺，不补造替身。

可用性检查仅访问已加载位置与碰撞邻区，要求脚下实心、身体净空、无水和真实 fixture；被占用的床不可用。未加载返回 UNLOADED，玩家拆设施返回 BLOCKED。`info` 中每城生活报告分别列登记、绑定、人物、身体和设施/岗位空缺，不把登记或账本职位称为正在营业。

## 命令与舆图

`/myvillage worldplan city [region]` 默认八城列表；可用州 ID、cityId 或中文州名过滤。每行城名、所属州、入口 XYZ，附可站立/受阻/未加载等只读观察。不申请城市区块。旧版世界仍给单城入口。详细规则、摘要与生活状态留在 info/export。

舆图沿用现有节流查询和 256² raster，新增最多八个主城入口标记，悬停显示中文城名和坐标。公共记录只带有界文字与坐标，不导入 client-only 类型、不传世界 seed/资源/命簿。快照 codec 双向同步，现有协议版本机制升为 54，旧客户端应由协议协商拒绝混连；不承诺 53 与 54 可混用。

`pregen_city` 在 V8 仍仅是明确管理员操作，每次最多 35 个 slice；普通查询不调用它。旧 `audit_city` 只适用全模板旧云山，V8 会给出明确说明，不伪造完整稀疏地基审计结果。

## 本批生成和未验证状态

正常资源导出/聚合的实际结果在本节末补记；没有运行 Gradle、javac、Minecraft、validator/check/lint、单测或云端，没有派子代理。资源解析中的边界/摘要检查是消费者拒绝损坏输入的实现，不是本批测试通过证据。

待 Terra xhigh 验证 Java API/构建与资源打包、八州选址及候选耗尽、负坐标/区块边界、最高建筑与地基/河床语义、数据包漂移拒绝、V1–V7 原始 fingerprint/字节身份和旧存档、加载边界与设施破坏、魔域散修来源与岗位空缺、协议 54/地图边界、真实门街楼梯/NPC 寻路和保存重启。玩家外观、操作感、音效、FPS、多人保持 not_verified。没有用源代码阅读、生成资源或元数据代替这些验收。

## 父代理可整合文件清单

以下均为本实现者写入的路径；相对本工作区。其它作者的 regional/central 源、输出、style、groups.py 不在此清单，不回退、不覆盖。

- `build.gradle`
- `blueprints/worldplan/rules_v8.json`
- `tools/province_capitals/game/compile.py`
- `docs/province-capitals-game-runtime.md`
- `docs/ai-kb/55_seeded_worldplan.md`
- `docs/ai-kb/58_yunshan_living_city.md`
- `docs/ai-kb/INDEX.md`

Java 路径前缀为 `src/main/java/com/example/myvillage/`：

- `worldplan/WorldPlanRules.java`
- `worldplan/WorldPlan.java`
- `worldplan/WorldPlanCli.java`
- `worldplan/WorldPlanExporter.java`
- `worldplan/capital/CapitalGameBundle.java`（新增）
- `worldplan/capital/CapitalGameRules.java`（新增）
- `worldplan/capital/CapitalCity.java`（新增）
- `worldplan/capital/LegacyCapitalAdapter.java`
- `worldplan/runtime/CapitalGamePlacement.java`（新增）
- `worldplan/runtime/PlannedChunkGenerator.java`
- `worldplan/runtime/PlannedBiomeSource.java`
- `worldplan/runtime/PlannedCity.java`
- `worldplan/runtime/WorldPlanRuntime.java`
- `worldplan/runtime/WorldPlanCommands.java`
- `worldplan/atlas/WorldAtlas.java`
- `worldplan/atlas/WorldAtlasSampler.java`
- `region/runtime/RegionRuntimeService.java`
- `sim/engine/Genesis.java`
- `sim/engine/Entrants.java`
- `sim/engine/GatePlacement.java`
- `sim/runtime/WorldSimRuntime.java`
- `sim/runtime/life/NpcLifeSites.java`
- `sim/runtime/net/WorldSimSnapshotCodec.java`
- `network/ModPayloads.java`
- `client/atlas/WorldAtlasScreen.java`

## 接口差异与未完事项

- 正式资源不是 preview manifest。V8 增加 `WorldPlan.cities()` / `capitalOf`，旧 `city()` 明确限于 V1–V7；新增具体 identity 来自 life.layout_id 和聚合 bundle_sha256。
- 正式七州每城 159 个 life.places 与旧 128 容量不兼容，已用 V8 冻结 192 容量的专用 opener 适配同一命簿。LifeRules/LifeCodec 旧断言未改，保存恢复需走同一 V8 SimData 路径。
- terrain_relative 的自然地表沿用 WorldPlan 的第一空气格；资源 heightfield 则严格为 top-solid，并在采样时加 1。中州 absolute originY=0 不应用此转换偏移。
- 网络协议是 54，需要客户端/服务器同时更新。mod 版本与 CHANGELOG 留给父代理。
- 旧 audit_city 的完整密集模板审计不覆盖 V8 稀疏地基；当前会明确拒绝，不输出虚假 complete。V8 独立审计/玩家验收不是本批已完成项。
- 正式八城索引、实际冻结 SHA 和新 preset 的产物，需要八城 owning export 与旧 frozen_v7 正常产物齐备后运行聚合。未生成该输出前，不能宣称新资源已打入 jar 或新世界已实际生成。
- 全部测试/编译/validator/check/lint/Gradle/MC/云端为 not_run；旧档、通行碰撞、服务营业、NPC 路径、资源重载、网络、性能/多人等实际行为仍须后续证据。没有启动 Terra 或其它子代理。

## 本实现者本次实际执行记录

只尝试一次正常聚合（共享未提交工作区）：

```bash
python3 tools/province_capitals/game/compile.py --out build/generated/province_capitals/resources --aggregate-only
```

退出码 **1**，在读取前置旧资源 `build/generated/worldplan/resources/data/myvillage/worldplan/frozen_v7.json` 时出现 FileNotFoundError。原始日志为 `build/province_capitals/runtime/aggregate-attempt.log`；没有重跑旧生成器、旧检查套件或任何测试。本实现者直接执行 owning exporter 次数为 0；本次聚合未输出 V8 index/frozen/preset，不能给出实际冻结八城 SHA。

交接时只读文件清点写在 `build/province_capitals/runtime/resource-inventory.json`。这属于共享作者当时正在正常生成/修订的产物快照，不是固定候选、资源验证或游戏证据：

| city_id | size | life.places | 当前 NBT 文件数 | NBT 字节 |
|---|---|---:|---:|---:|
| capital_beiyuan | 224×96×272 | 159 | 233 | 405600 |
| capital_donghai | 224×96×272 | 159 | 237 | 503645 |
| capital_donghuang | 160×112×416 | 159 | 238 | 388556 |
| capital_lingyue | 224×96×272 | 159 | 156 | 306969 |
| capital_moyu | 224×112×272 | 159 | 202 | 334799 |
| capital_nanjiang | 224×96×272 | 159 | 230 | 459103 |
| capital_ximo | 224×96×272 | 159 | 226 | 299638 |

合计七城、1522 个 NBT、2698310 字节 NBT；该快照的七份 city.json 为 1873912 字节、terrain.bin.gz 为 11585 字节。清点时尚无 capital_zhongzhou/city.json，旧 frozen_v7 与新 frozen_v8 均不存在。作者后续导出会改变本快照，父代理必须在八城和前置产物齐备后重新正常聚合，再冻结实际候选；不能沿用这些文件数冒充最终八城结果。

本批可整合的是上列正式运行时代码与生成入口；未完成的实际产物是完整八城聚合/冻结/打包。编译、测试、实机和玩家接受状态均未改变。

## 父代理后续整合

八城资源齐备后，父代理已完成一次完整正常聚合，输出V8 index/frozen/preset；基准使用固定0.100.2资源缓存，原始失败仍保留。准确冻结身份与当前未运行状态以[总交接](province-capitals-game-integration.md)为准。本实现者上面的首次失败和当时七城快照作为历史记录保留，不能当最终八城统计。
