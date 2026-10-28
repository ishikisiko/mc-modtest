# 八州主城基础模型与交换接口

本批实现独立的数据基础和有界规划端口，未激活任何生产生成器。目录里的八座主城是身份与设计目标，不是八座模型、八个已选城址或已完成的 `worldplan_v8`。用户明确本轮不测试；所有编译、测试、validator、lint/check、Gradle、Minecraft 和云端验证均为 `not_run`，后续只交 Terra xhigh。玩家外观、步行体验、音效、FPS、多人和实际地形适配均为 `not_verified`。

不可变协调契约：`/tmp/city-plan-research-20261028/coordination-contract.json`。工作区为 `/tmp/mc-default-cultivation-20261028`，本代理没有创建或切换 checkout，不提交、不合并、不推送，不操作另外两位代理的归属路径。

## 实际交付

- [八州 authored 目录](../blueprints/province_capitals/catalog.json)：每州唯一主城、独立外观和布局身份、地貌目标、服务职责、旧档边界及未运行状态。
- [Java 独立包](../src/main/java/com/example/myvillage/worldplan/capital/)：核心州域枚举、几何包络与变换、预览元数据读取、目录读取、候选与城址回执、完整八城集合、有界规划端口、命簿绑定键和旧单城只读适配。
- [Python exchange 定义与读取辅助](../tools/province_capitals/schema.py)：`MANIFEST_SCHEMA` / `manifest_schema()`、`load_catalog()`、`capital_for_region()`、`new_manifest()`、`read_manifest()`、`load_manifest()` 和显式数据路径解析。无 CLI，无 import 时文件读取或检查。

以上代码是实现，尚无本批运行或编译证据。本代理不提供预览 HTML 或三维 exchange manifest；两者由对应归属代理交父代理汇总。自己的目录 JSON 绝对路径为 `/tmp/mc-default-cultivation-20261028/blueprints/province_capitals/catalog.json`，它不是预览 manifest。

实际新增文件共 16 个，均在归属范围内：

```text
src/main/java/com/example/myvillage/worldplan/capital/CapitalRegion.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalGeometry.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalJson.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalManifest.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalCatalog.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalLayoutIdentity.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalCandidate.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalTerrain.java
src/main/java/com/example/myvillage/worldplan/capital/PlannedCapital.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalCities.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalPlanner.java
src/main/java/com/example/myvillage/worldplan/capital/CapitalBindings.java
src/main/java/com/example/myvillage/worldplan/capital/LegacyCapitalAdapter.java
tools/province_capitals/schema.py
blueprints/province_capitals/catalog.json
docs/province-capitals-foundation.md
```

## 稳定身份与尺度

| region_id | 唯一 city_id | appearance_id | 地貌接口目标 | 目标尺度 |
|---|---|---|---|---|
| `zhongzhou` | `capital_zhongzhou` | `zhongzhou_full_yunshan` | 完整云山山水 | 完整原作 |
| `donghai` | `capital_donghai` | `donghai_harbour` | 海岸港湾 | D1 街区量级 |
| `ximo` | `capital_ximo` | `ximo_oasis` | 绿洲盆地 | D1 街区量级 |
| `nanjiang` | `capital_nanjiang` | `nanjiang_stream_valley` | 林间溪谷 | D1 街区量级 |
| `beiyuan` | `capital_beiyuan` | `beiyuan_sheltered_snowplain` | 背风雪原 | D1 街区量级 |
| `donghuang` | `capital_donghuang` | `donghuang_valley_gate` | 狭长谷口 | D1 街区量级 |
| `lingyue` | `capital_lingyue` | `lingyue_supported_ridge` | 有支撑的山脊台地 | D1 街区量级 |
| `moyu` | `capital_moyu` | `moyu_connected_rift` | 有通路的裂谷两岸 | D1 街区量级 |

region id 沿用现有区域资源身份。city id 是新城市的持久身份；`appearance_id`、`terrain_profile` 与布局族 `layout_id` 由 authored 目录确定，不能从随机 level、tier、抽样序号或资源名字前缀推断。`RegionContext.assignedLevel` 和 `PlannedCapital.regionLevel` 单独存值，不参与挑选目录外观。外域重复使用某州地貌不等于增加核心州，不生成第二座同身份主城。魔域的封闭/关隘规则不豁免主城。

七州的“D1 量级”是现有生活街区规模参考，不是将同一平城换材质。旧打包 D1 为 224×82×272、70 民居加四栋功能建筑；中州目标为完整原作 587 民居与 28 商铺的山水城市。首批中州只交原作实际 plan 派生的地形道路白模与建筑占位，不能称完整建筑交付。目录的这些数字是参考标注，不是新算法的建筑数参数。

## Exchange schema 1

协调契约必需字段完整保留：

```text
schema, city_id, region_id, stage, units, bounds, source, terrain,
districts, roads, buildings, entries, services
```

`schema=1`、`stage=preview`、`units=block`。两端读取器都提供所需结构：城区为 `id/role/bounds`，道路为 `id/kind/width/points`，建筑为 `id/role/template/position/rotation_quarters`，入口为 `id/position/facing`，服务为 `id/role/building_id/position`。各列表内部 id 不重复，服务引用同 manifest 内真实存在的建筑 id；白模占位建筑仍需有稳定 id。`terrain.data` 可以是作者所属相对路径、内联对象或数组，读取器不会隐式打开它。

扩展字段允许且保留。Java 用 `toJson()` 返回防御性深复制；Python 返回深复制字典，不丢弃来源、地形或建筑内部扩展。Java 的列表、坐标和地形数据访问也不暴露内部可变 JSON。`source.ref` 只表达来源，不被解释为可写目标或模板身份。

Python 的公共 `MANIFEST_SCHEMA` 是 JSON Schema 定义；`manifest_schema()` 可取得独立副本。读取辅助还会拒绝反转 bounds、重复 id、非有限数值、非整数旋转及悬空建筑引用。JSON Schema 本身不表达跨字段 min/max 比较、列表 id 去重或 building_id 引用关系，这些语义由读取器负责。没有在本批执行这些函数或 schema。

两个预览生成器可按以下接口消费目录，示例仅供后续集成，没有执行：

```python
from tools.province_capitals.schema import load_catalog, new_manifest, read_manifest

catalog = load_catalog()
manifest = new_manifest(
    region_id="donghai",
    bounds=authored_bounds,
    source={"kind": "authored", "ref": authored_source_ref},
    terrain={"kind": "heightfield", "data": authored_heightfield},
    catalog=catalog,
)
# 在生成器内填入 districts/roads/buildings/entries/services 后再交付。
# read_manifest(manifest, catalog=catalog) 可供未来明确授权的读取/检查调用。
```

`new_manifest()` 提供全部必需键和目录身份，空数组只表示待填的预览数据。普通 `load_manifest()` 不自动强制目录中的新 city id；显式传入 `catalog` 才核对 city/region 配对。Java `CapitalManifest` 同样负责交换结构，目录与身份配对由 `CapitalPlanner` / `CapitalCities` 核对。这允许明确报告其它作者尚未对齐的 ID，不能把不匹配静默重命名。

## 原契约的歧义与本层消费约定

没有删改不可变协调 JSON，没有增加 exchange 必需键。以下细节在原契约中未定义，本层给出消费约定，父代理仍需与另外两份 manifest 对齐；不是已核验一致的结论。

1. **包络端点。** `bounds` 按闭几何包络处理：端点可以落在边界，min/max 可有负值，允许预览零体积占位；不能拿宽度直接做旧算法的体素数量。正式候选必须有三维体积。推荐扩展 `bounds_convention=closed_geometric_envelope`。它不是体素索引最大值，未来区块切片须显式定义 block footprint。
2. **整体旋转。** 正向四分之一圈将 `+X` 转到 `+Z`；绕作者原点旋转后再平移，不自动把 min 归零。入口朝向跟随整体旋转，建筑 `rotation_quarters` 仍是局部值；未来放置时需叠加整体旋转。预览 metre/block 坐标可含小数，转换为 Minecraft 整数网格需未来显式规则，本批不截断。
3. **数据路径。** `terrain.data` 路径基准不明。Python `resolve_data_path()` 默认相对 manifest 目录，也可显式传 `base_directory` 和 `allowed_root` 读取 repo-relative 约定；仅解析不读文件，拒绝 URL、绝对路径及越出允许根的引用。不应自动搜索其它代理目录来猜路径。
4. **高度变体。** 中州原高度与主世界适配高度应在 manifest 扩展中保留不同 profile/source 与完整变换。不能用同一个冻结布局哈希代指不同高度数据。本层不替作者选择默认变体或伪造高度压缩。
5. **布局哈希。** 目录 `layout_id` 表达布局族；交换数据用 `layout_family_id` 引用该族，`layout_id` 与 `layout_sha256` 表达具体高度方案的独立版本。`CapitalLayoutIdentity` 要求 64 位小写十六进制摘要，但只约束表示形式；资源集合的哈希定义、计算与核对由后续拥有正式生成器的实现负责。本批目录没有占位哈希，也没有计算或认证真实资源摘要。

## 有界规划与八城不变量

`CapitalCatalog` 强制八个核心州域与八个唯一 city id 一一对应；`CapitalPlanner.Request` 也必须接收八个不重复的 `RegionContext`。因此五至七州的旧计划不能作为新八城请求进入这条路径。

`CapitalPlanner` 只实现有限尝试、元数据完整性、搜索包络、空间避让与结果聚合。候选位置和真实地形算法由调用方提供；它没有随机位置算法、默认平地回退或世界写入。

- `CandidateSource.propose()` 接收 world seed、当前定义、地区上下文和有限 attempt。外观身份已在目录确定，不按 level 重抽。
- `Budget` 显式提供每城候选上限、每候选地形采样上限、预留边距；无内置调参默认值。未来所有值须来自相应版本冻结配置。
- `CapitalTerrain.assess()` 显式接收候选、地区、**含边距的实际预留包络**和采样上限。适配器必须使用未来算法的真实规划地形与核心域归属来源，检查预留完全落在宿主州、建筑地基支撑、入口连接区域通路和有界地形过渡。只有这些义务均满足且采样数大于零时才可发出 `FIT` 回执。不存在实测/规划来源时返回 `UNAVAILABLE`，不能以 preview heightfield 冒充已选址。
- 编排器记录地形耗用并拒绝超预算回执。预留之间按水平包络避让，不能以垂直错开把两座城叠在一起。只检查矩形不足以证明宿主州归属；不规则区域覆盖义务仍在地形端口。
- 空候选和 `REJECTED` / `UNAVAILABLE` 都消耗当前有限尝试；耗尽后记录 city/region/attempts/最后原因。各州都有限尝试后，任一缺城返回 `Failure`，不暴露可供发布的部分城市集合，不允许调用方跳过该州继续保存。
- 只有八州各有一城才构造 `Success(CapitalCities)`。`CapitalCities` 再约束目录身份、服务/城区/道路/入口完整性、唯一州域和预留互斥；不能把两份样板或中州白模凑成八城成功。

地形端口的实现必须自身遵守采样预算，不能仅事后填写较小 `samplesUsed`。候选端口和地形端口都应只读纯规划数据；外部副作用、自动取区块票、实际支撑/通路覆盖证明和存档事务均未实现。未来如要整体重试，应由新冻结规则显式提供有界总重试预算；本层没有无限回溯或降低主城要求的逻辑。

## 从单城到 cities 集合的接入边界

未来新算法的只读门面应使用：

- `cities()`：完整主城记录列表；
- `cityById(cityId)`：明确身份定位，未知返回空；
- `capitalOf(regionId)`：核心州域唯一主城，不选择列表第一城兜底；
- `intersectingReservations(bounds)`：所有相交主城预留，供区块切片与其它规划族共享避让。

新算法应冻结城市原点/旋转、真实资源集合布局身份、城区/入口/服务元数据、地形配置身份及其来源，区块、舆图、传送提示和生活适配共同消费同一快照。`CapitalManifest` 的来源快照可保留高度扩展，但本批没有新世界 codec、保存格式或资源冻结实现；不能将 `PlannedCapital` 构造成功解释为已持久化或已生成。

旧 `WorldPlan.city()`、`WorldPlanRules.city`、`PlannedCity` 区块逻辑和 `NpcLifeSites` 都未改。`LegacyCapitalAdapter.read(existingPlan)` 仅复制已经存在的旧计划字段：算法、旧规则 id、宿主、整数位置与尺寸、旧布局哈希和 life layout。它不构造新 `WorldPlan`，不读取或重算旧存档，不改旧 ID，也不将单城伪装成 `CapitalCities`。旧命簿 `yunshan` 绑定及其仓库继续原样，适配器不会声称这个场所已经存在或迁移成功。

## cityId 绑定命簿、岗位和仓库

`CapitalBindings` 是只读交接数据，未调用 `WorldSim`，没有注册场所、造人、安排岗位或初始化库存。

- 新城 `LifeSite.id`、生活 supplies 的 site key 与 `CommerceState.markets` 的 site key 都用持久 `cityId`。同一城市的交易和仓库可沿用已有 siteId 存储边界，不建立另一份账本或现金账户。
- 岗位身份是二元组 `SlotKey(cityId, serviceId)`，建筑 id 和服务 id 只在该城内有意义。不能单独以 `inn`、`smith` 或服务角色作为全世界唯一岗位。服务角色可重复，服务 id 不重复。
- `serviceSlots()` 统一变换服务锚点；`entries()` 统一变换入口位置与朝向。预览锚点不含完整 fixture、容量或真实设施可用性证据，不能直接变成可营业岗位。
- 后续注册、人员分配、供料和仓库写入只经 `WorldSim` facade。以 cityId 幂等登记，不重复造人，不刷新已存货物、死者岗位或一次性初始化收据；观察范围仍限已加载真实设施。
- 多城初始化状态需要按 world/cityId 隔离。现有 `NpcLifeSites.automaticAttempted` 和 `YUNSHAN_SITE` 仍未迁移，不能把一个 static 布尔量用作所有城市的登记结果。

## 待 Terra xhigh 执行的具体断言

以下全部为 `not_run`，不是本批检查结果，也没有新增可执行测试文件：

1. Java 编译及 API/Gson 兼容性；Python 导入、JSON Schema 合法性和两端同 fixture 读取 parity。
2. 目录恰含现有八个 region id，city id 唯一、魔域有城；随机等级改变后 city/appearance/layout/terrain profile 不变，外域同地貌不新增主城。
3. 必需字段缺失、错误 schema/stage/units、布尔或小数充当整数、NaN/Infinity、反转 bounds、重复局部 id 和悬空服务引用被拒绝；扩展字段防御复制且往返保留。
4. 负局部坐标及四种旋转下包络、入口位置/朝向、服务锚点一致；明确闭几何包络到 block/chunk footprint 的边界和整数化策略，不能照搬旧尺寸公式。
5. 请求少州/重复州拒绝；每个核心州恰有一城；候选永空、单州耗尽或地形不可用时返回整份失败，不能泄漏部分成功集合。耗用总量不超过显式预算，超预算回执拒绝。
6. 不规则州域边界、魔域关隘、地基支撑、桥台、入口道路衔接、已存在预留和两城相交均由真实规划地形 fixture 覆盖；不能用矩形包含或假 FIT 当真实地形证据。
7. 直接构造 `CapitalCities` 也拒绝不齐的服务/入口/城区/道路元数据；预览白模不因有 placeholder buildings 就被认定为真实完成。
8. 同名局部岗位在两座城形成不同 SlotKey，两城仓库键不同，同城重绑不产生新身份；后续 WorldSim 集成须覆盖重启、人员死亡、已有库存和观察边界。本批未实现这些实际状态变更。
9. 旧适配器前后旧计划 manifest/fingerprint 不变；V1–V7 旧资源、算法、保存格式及旧场所键保持原样。不把本层读取成功解释为旧档实机兼容通过。
10. 两个区域样板与中州白模实际 manifest 对齐本目录 cityId、坐标与路径约定；中州高度变体身份不混用。本代理未读取、运行或认证另外两位代理的产物。

未完成范围：八州真实多候选选址与保底算法、真实 terrain 适配器、中州完整建筑资源化、另外五州三维城市、新生产版本注册/codec/保存/区块切片/保护、共享预留与舆图接入、cityId 多城生活初始化、仓库/岗位持久化迁移、正式拥有者生成器接入、所有测试与玩家验收。正式接入仍须经既有 groups.py/modset/catalogue/roof registries 和资源生成器所有权，本批不建立隐式第二条生产链。

## 父代理接口整合补充

两套预览均从同一 authored catalog 读取布局族和外观身份。候选与 `CapitalCities` 经同一入口同时核对城市、州域、布局族、具体布局 ID 与具体摘要；原高度和适配高度不可混用。仍保留无布局族扩展时的旧基础记录精确 ID 比对。此次改动仅经源码阅读完成，未运行编译或测试。

中州交付 `manifest.json` 是 profile 索引，`manifest-original/adapted.json` 是文件目录；真正符合 exchange 的入口是同目录 `layout-original.json`、`layout-adapted.json`。派生交换清单只保留在忽略的 out/preview 目录，资源路径相对各自清单目录；blueprints 仅保留 authored 输入。需先由拥有的导出器生成，文件存在不代表真实世界适配。
