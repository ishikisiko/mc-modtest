> 0.102.0：新建世界使用独立 `planned_v9` / `planned_source_v9` / `myvillage:worldplan_v9`。七城改用 v2 authored 总图和建筑族，中州继续引用 v1；V1–V8 codec与资源保留。H → 天下 → 舆图点击城标可确认传送，由服务器验证冻结身份、加载预算和安全落点，取消保留视口。资源边界见[V9接入](../province-capitals-v9-integration.md)，网络与生命周期见[城标传送](../atlas-city-travel.md)。本行描述实现，不表示玩家外观或完整游戏验收通过。

> 0.101.0 八州正式接入：全新 `myvillage:cultivation` 使用独立 `planned_v8`，八州各一主城（含魔域）。中州完整城域保留 absolute originY=0，地基 heightfield 与稀疏 NBT 按当前区块消费；规则、资源摘要、城址、出生、聚落/宗门避让和地图共享冻结计划。固定源 `37985bd5` 已完成正常构建、资源审计、八城登记与代表区块原生检查、受控 V7 旧档加载及舆图开启；完整城体步行、水流、FPS 和玩家外观仍未验收，详见[验证记录](../testing/2026-10-29-province-capitals.md)。[运行时说明](../province-capitals-game-runtime.md)中的 V1–V7 codec、旧 frozen 资源、存档和已有出生不迁移。

> 0.100.2：全新建档默认选择 `myvillage:cultivation`；手动切换世界类型仍有效，“重建”保留来源设置。专用服务器仅在 `level-type` 缺省/空白时采用该默认值。已有 `level.dat`、显式 normal/flat/custom、冻结 V1–V7 世界计划均不迁移。以下 opt-in 描述为历史行为。

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
