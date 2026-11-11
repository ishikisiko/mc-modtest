> 0.128.0：中州西坡中段接入灰瓦庄园，撤并已获准的31栋民居，保留外围公共巷道。大门外管家办理100枚上品灵石购房，产权和完整私有范围由服务端管理；`player_estates` 与实际切片共同进入城市冻结摘要。见[庄园购买](../features/estate-purchase.md)。独立蓝图与外观预览仍保留各自证据边界。

> 0.115.0：八城增加 25 处传送亭台。选址权威为 `blueprints/capital_waystones/sites.json`，材料和构件尺寸为同目录 `materials.json`；`tools/capital_waystones/generate.py` 在正式 v4 模板上构造并逐块回读，保留原方块实体 NBT，随后重算 V11 冻结摘要。`CapitalGameBundle.waystones` 与模板共用局部坐标，名称、坐标、朝向和 Waystones 方块类型进入清单身份。`CapitalGamePlacement` 在当前 FEATURES 区块内放置石碑双块及延迟 BE；可选适配仅在装有 Waystones 时加载，原生 BE 首次加载后建立并保存身份，不在区块加载时重放建筑或补块。未安装 Waystones 时只保留亭台建筑。外观由用户进游戏查看，不能把资源检查称为玩家验收。

> 当前开发策略（0.105.0）：仅打包 V11 规则、v4 主城资源及当前运行需要的聚落资源；只注册当前世界生成 codec。历史目录作为现有生成链的中间输入，不随 JAR 交付；不提供旧档兼容、资源别名或历史资源校验。以下早期版本的保留策略仅是历史记录。

0.107.0 官道随区块生成，沿用 A 青石官道与危险路段护栏；正式公共出口和门内只读地坪由当前城市资源导出。布局在独立线程计算并压缩保存，区块完成洞穴雕刻后、装饰前直接写入道路，结构与装饰避让道路实际范围。0.112.3-fix1 在完整路幅高度拟合失败后反馈约束来源并有界绕行，保留必经出口、全通和共享展开预算；新世界宗门选址同时保护双方完整缓冲和出口外转向空间。0.113.0-fix1 将省城公共接坡改为独立规划、同城道路共用的通行面，并统一道路地表与标高区间采样；限制陆地省城的地形锚点不低于配置海平面，避免按海底高度落位后与官道接坡要求冲突；普通路点仍要经过，但其天然高度不再作为硬性标高。完整行为与命令见 [道路说明](../features/intercity-roads.md)。本版游戏效果由用户实看。

[原包两城实验](../../tools/roadweaver_probe/README.md)保留原结论：野外道路已生成，原包匹配铺装未连续接门，CPU 全域精采样请求发生回退；不能用新原型的结果覆盖该实验的失败，也不能把开发者画面或局部映射步行当成全路程玩家验收。

八州整城蓝图统一从 `blueprints/province_capitals/blueprint_plan/catalog.json` 进入。七个区域城采用 `manor_relayout` 后端及 `layouts/<region>.json` 正式 authored 布局：原尺寸七府、地标内收、住宅由 437 减至 386 栋，51 栋原功能建筑保留；私院归属、公共院场用途、城门首层和全路幅 XYZ 均落实到实际施工。`runtime.py` 使用同一规划导出当前默认 V11/v4 资源，`compile_v11.py` 聚合七府与现有中州；入口、地形、舆图与生活岗位共用主城 manifest。新布局组经 `tools/buildgen/groups.py` 注册。府邸使用现有注册构件，保留架空层，独立样板土块不覆盖城内河海沟谷。独立 SVG、三维和试放包仍可单独生成；中州沿用原城主体，西坡庄园的局部替换见本页0.128.0说明。见[接入说明](../features/default-manor-capitals.md)和[工具说明](../../tools/province_capitals/blueprint_plan/README.md)；[最初灵岳试验记录](../testing/2026-10-30-lingyue-blueprint.md)只对应其当时固定源，不能作为后续改动的验收结论。

原生分批放置先落地全部地形和建筑，再放跨分块完整组件与依赖相邻分块支撑的注册墙牌。清单为全部保留方块实体提供 `block_entity_checks`，同时保留导出原始 NBT 摘要和保存世界的字段回读预期；方块审计通过不能替代招牌文字等内容回读。

八城本次已完整导出，中州也已完成整城模板放置。用户最终取消严格审计和行走测试；三处原版楼梯转角状态差异及原失败保留，不能称严格验收通过。来源与交付边界见[八州整城蓝图交付](../testing/2026-10-30-all-capital-blueprints.md)。

后续按用户要求删除中州南门外三块原作农田，恢复自然地形及植被，并同步院场引用。当前生成源、基础资源与蓝图预览已更新，之前的试验包和游戏存档不自动更新；见[农田删除记录](../testing/2026-10-30-zhongzhou-farmland-removal.md)。

> 0.103.0 候选：[V10 八州环境接入](../province-capitals-v10-integration.md)新增八州非房屋地标、七州庭园与城防；八城使用独立 v3 资源，旧 V1–V9 继续冻结。正式 Colab 构建及原生验收待 Terra 统一执行。

0.103.1 新建世界使用 V11，独立 v4 资源仅替换符灯和晒布架；V1–V10 保留，旧城不迁移。实现与获取方式见[符灯与晒布架](../features/street-furniture.md)。本轮游戏内查看由用户完成。

> 0.102.0：新建世界使用独立 `planned_v9` / `planned_source_v9` / `myvillage:worldplan_v9`。七城改用 v2 authored 总图和建筑族，中州继续引用 v1；V1–V8 codec与资源保留。H → 天下 → 舆图点击城标可确认传送，由服务器验证冻结身份、加载预算和安全落点，取消保留视口。资源边界见[V9接入](../province-capitals-v9-integration.md)，网络与生命周期见[城标传送](../atlas-city-travel.md)。[基础验证](../testing/2026-10-29-province-capitals-v9.md)已覆盖正常构建、资源审计、32项Java及地图确认/取消原生场景；玩家外观、手感、FPS和多人仍未验收。

> 0.101.0 八州正式接入：全新 `myvillage:cultivation` 使用独立 `planned_v8`，八州各一主城（含魔域）。中州完整城域保留 absolute originY=0，地基 heightfield 与稀疏 NBT 按当前区块消费；规则、资源摘要、城址、出生、聚落/宗门避让和地图共享冻结计划。固定源 `37985bd5` 已完成正常构建、资源审计、八城登记与代表区块原生检查、受控 V7 旧档加载及舆图开启；完整城体步行、水流、FPS 和玩家外观仍未验收，详见[验证记录](../testing/2026-10-29-province-capitals.md)。[运行时说明](../province-capitals-game-runtime.md)中的 V1–V7 codec、旧 frozen 资源、存档和已有出生不迁移。

> 0.100.2：全新建档默认选择 `myvillage:cultivation`；手动切换世界类型仍有效，“重建”保留来源设置。专用服务器仅在 `level-type` 缺省/空白时采用该默认值。已有 `level.dat`、显式 normal/flat/custom、冻结 V1–V7 世界计划均不迁移。以下 opt-in 描述为历史行为。

> 2026-10-29 设计阶段：[八州独立地标与两城环境样板](../province-capitals-environment-design.md)提供八种非房屋核心物、灵岳/东海庭园街景和城防三维稿。尚未接入正式世界资源、功能或新布局版本，等待玩家确认视觉方向；当前游戏仍使用上述 V9。

# Seeded Cultivation Worldplan

区域与建筑群共同生成的首个离线样板见 [河谷区域工具](../../tools/regiongen/README.md)。
它使用独立的纯 Java 规划 CLI 与既有云山建筑，输出体素、SCHEM 和三维预览；
尚未接入世界类型、区块生成或运行时准入，不改变本文 V1–V4 的冻结算法。
第二份已选布局“湾口台地集”独立接入普通世界原生结构，见 [KB65](65_bay_terraces.md)；
0.88.0 将湾口聚落接入 V5 统一规划；0.89.0 的 V6 另接入第一章六处阡镇建筑，见 [KB66](66_qianzhen_region.md)。旧算法各自保留冻结行为。

> 0.64.0 更新：[完整云山 V4 与商业服务](58_yunshan_living_city.md)。本页保留所述历史版本的范围；该历史版本默认 V4；0.89.0 起新世界默认 V6，第一章建筑接入见 KB66。


0.59.0 adds the opt-in `myvillage:cultivation` world preset. The existing normal world and the legacy region generator in [13](13_region_topology.md) remain available and are not migrated. This note supersedes legacy region-layer deferrals only for the new preset.

The owner-facing rules, creation instructions, CLI, safety model, constraints and acceptance procedure are in [Worldplan acceptance](../worldplan/acceptance.md).

## Ownership and generation

0.59.1-fix1 makes new cultivation worlds use `planned_v3` / `worldplan_v3`. `rules_v3.json` restores the exact V1 biome as the source; `WorldPlanEcotone` consults eight unmodified source samples at a maximum Euclidean radius of 32 blocks. Only authored incompatible group pairs receive a transition biome. A transition never feeds into another sample, so it cannot propagate into the interior or depend on visit order. Outside this local neighbourhood the original biome ID, including `grove`, is retained. The canopy-safe spawn and city audit remain in place.

V2 was too broad: averaging climates changed entire biome interiors and displaced the snowline. The V3 regression measures original snow-type retention in the reproduction area, plus both original endpoints and the maximum transition width. `legacy_v2_hashes.json` and jar-derived V2 sample fixtures preserve already-created V2 saves.

0.59.1 originally made newly created cultivation worlds use `planned_v2` / `worldplan_v2`.
`rules_v2.json` adds a bounded continuous climate field and ordered temperature/moisture biome bands over the unchanged V1 geography. Core regions and outer cells blend climate with the same deterministic spatial ownership; the radial core/outer transition blends both sides. No per-block random biome dithering is used. Climate configuration is frozen into the save.

Existing V1 worlds retain their codec, algorithm, terrain, biomes and city templates. `legacy_v1_hashes.json` pins the released frozen rules and 35 NBT resources; generation fails on drift. The 0.59.0 jar-derived test fixture pins three seeds and 51 sample coordinates. New ecology therefore requires a new world, not a `/reload` or deletion of chunks from an old save.

New V2 rules enable `clear_spawn_canopy`: decoration cannot place a canopy anywhere above the spawn pad, and first-world creation caps `spawnRadius` at the pad radius. Player builds and existing worlds' gamerules remain untouched; older frozen V2 rules without this flag keep their previous behavior.

- `blueprints/worldplan/rules_v1.json`: authored macro rules and terrain profile weights.
- `blueprints/worldplan/yunshan_placeholder_v1.json`: the placement fixture of the four tracked Yunshan buildings. The missing external full D1 city is not recreated by assertion.
- `tools/worldplan/generate_assets.py`: compiles the original Yunshan blueprints, validates paths and services, cuts exactly 35 complete 16×32×16 NBT tiles, and writes frozen rules and the native world preset under `build/generated/worldplan/resources`.
- `worldplan/WorldPlanRules`, `WorldPlan`, `WorldPlanExporter`, `WorldPlanCli`: pure JDK/Gson core. The CLI and runtime use the same planner, not independent approximate implementations.
- `region/runtime/RegionGeometry`: optional frozen shape and site reservations. The old RegionGraph constructor and canonical legacy JSON stay unchanged when geometry is absent.
- `worldplan/runtime/PlannedChunkGenerator`: binds the actual world seed in `createState`; constructs the real settings-backed RandomState rather than using ChunkMap's dummy state for custom generators. It preserves native underground generation, implements the plan's surface in NOISE, delegates surface/carvers/decorations, then emits the one tile owned by the current city chunk.
- `PlannedBiomeSource`: reads holders only after registries bind; retains the native underground cave biomes and chooses surface ecology from the plan.
- `WorldPlanReservationMixin` protects ordinary WorldGenRegion writes; `WorldPlanOreMixin` protects the native OreFeature direct-section path. Decoration scopes are thread-local and closed in try-with-resources. Player edits are not blocked.

## Persistence and scope

Frozen JSON rules and a plan fingerprint are encoded in the generator in `level.dat`; tile resource hashes detect incompatible resource replacement. Do not mutate released V1/V2/V3 algorithms or remove their assets when old saves still use them. Introduce versioned algorithms and assets instead. Full chunks are never rebuilt from the atlas.

The core is a 4000-block radius, with a continuous terrain blend into stateless outer cells. Outer queries inspect only a bounded neighbourhood and return stable coordinate-based region IDs. Negative coordinates use floor. Queries and exports do not force chunks.

RegionRuntime and the finite core world-sim graph use the same planned geometry. GatePlacement avoids the city reservation. Wilderness reads outer ecological profiles but does not invent infinite ledger people. Region tier is not a player realm index, and the existing realm demographic balance has not been rewritten.

The city is emitted in chunk generation rather than as a vanilla StructureStart, so its lookup entry point is `worldplan city`, not a fictional `/locate` structure ID. Macro dashed routes are graph relationships, not generated highways. Maps sample pre-decoration terrain and cannot substitute for real chunk or visual evidence.

## Checks

The [0.59.1-fix1 local-ecotone verification record](../testing/2026-10-17-worldplan-local-ecotones.md) records V3's original-snow retention, seven native biome samples, mapped traversal, spawn/city checks and V1/V2/V3 reopen compatibility.

The [0.59.1 owner-PC and cloud verification record](../testing/2026-10-17-worldplan-ecology.md) records the initial canopy failure, the final scoped checks, the conservative uncovered-dirt limitation and the matching jar hash.

Focused cloud build: `.venv-modal/bin/python -m tools.modal_runner run --preset worldplan-build --ref <commit> --timeout 1800`. This generates/checks owned resources, tests worldplan/region and direct simulation consumers, and builds the jar. The owner-authorized DevHost client can separately verify old V1/V2 worlds, new V3 local ecotones and the audit classifier.

`audit_city` retains `raw_differences` and raw block hashes. It reports `natural_changes` separately only for expected grass becoming plain dirt while the original template's opaque cover is still intact. `differences` and `complete` refer to the remaining changes; `strict_complete` requires raw equality. Missing roads/walls, uncovered dirt edits and altered cover are not ignored. Exact reverse-order hash tests disable random ticks explicitly; gameplay audits keep normal random ticks.

Run `tools/worldplan/ci.py` in the isolated cloud environment configured by the feature workflow, or the separate commands in the acceptance document. Checks cover generated-resource round trips, 510 seed layouts, legacy region parity and sim boundaries, real NOISE columns, city tiling, save/restart, opposite visit order, preservation of a player-placed marker, ordinary-world opt-out and native client images. Results live in `reports/worldplan/`, not in an assumed pass statement in this note.

The normal project workflow and shared [world contracts](../contracts/world.md) continue to apply. This feature used GitHub Actions because Modal credentials were not available in the conversation environment; it did not fall back to the owner's Windows machine.

## In-game ink atlas (0.67.0)

H → 天下 → 舆图 opens a native independent screen in the approved 素纸淡墨 style.
It shows region names/borders, coarse land/sea and the local player's position, with pan,
zoom, overview, locate and return. It displays the complete bounded core including unexplored
areas, not an exploration fog map. The dashed core limit is an administrative boundary, not a coast.

`WorldAtlasSampler` reads only the current save's immutable `WorldPlan`: V1–V4 retain their
frozen rules. The server's one-plan `WorldAtlasCache` samples on a dedicated background thread,
returns LOADING through the existing throttled `WorldSimQuery.ATLAS`, and releases its work at
server stop. Neither sampling nor rendering requests game chunks. The reply contains a 256²
packed grid, at most 16 literal region names/centres, bounds and a plan fingerprint; no seed,
city templates or second ledger. The atlas works even when the world ledger is inactive.

Protocol 35 adds the nullable atlas section to the existing snapshot. Decoding bounds the
region count/text, raster and dimensions and rejects unknown cell owners. Client disconnect
clears snapshots/textures; resource reload releases textures. Java2D paints immutable images
in a background worker, with render-thread uploads; it uses the included OFL typefaces.

Ordinary worlds show an explicit unavailable message. The view is always of the overworld;
other dimensions hide the player marker. Players outside the bounded atlas can still browse it,
with an explicit message. Map samples omit vegetation, terrain details and player edits; coast
loops smaller than 12 sample cells are suppressed for readability. This does not alter generation.

Focused fixed-source Modal presets: `atlas-build` and `atlas-runtime`. The latter uses mapped
H/click/drag input on a real client for the entry, map and ordinary-world message. Actual evidence
and limitations are recorded in `docs/testing/2026-10-19-world-atlas.md`.

## 0.107.0 八城官道生成

八城自动规划、正式关隘、共享路口、生成预留和路网缓存以 [道路说明](../features/intercity-roads.md) 为准。核心实现为 `RoadGeneration`、`RoadLayout`、`RoadChunkPlacement` 与 `PlannedChunkGenerator`；布局按区块记录列，摘要及世界身份不匹配时拒绝加载。门内只读地坪来自正式城市资源，旧世界已生成区块不回填。四个规划聚落尚无专用接入支路。
> 0.124.1：官道路口按实际分岔关系统一生成平台及外围接坡；东荒府前以精确半格宽阶接到真实门洞。规则与检查范围见[道路说明](../features/intercity-roads.md)和[七府接路说明](../features/default-manor-capitals.md#道路与住宅完成面)。
