# Changelog

All notable project changes should be recorded here when a version is prepared.

## Versioning Rules

The authoritative version-bump rule (increments and the files that must move
together) lives in `openspec/config.yaml` (`rules.tasks`). Follow it there.

## 0.26.1

### Added

- Added the diamond-tier `myvillage:xuanyue_zhenshan_sword`,
  `myvillage:chilian_lihuo_sword`, and `myvillage:qingxiao_liuyun_sword` with
  independent 64x64 transparent textures derived from the supplied reference
  art, handheld models, bilingual names, creative-tab entries, and vanilla
  sword-tag membership.

### Fixed

- Replaced the three reference-derived textures' partially transparent edge
  pixels with neighboring solid sprite colors, leaving only fully transparent
  background pixels and fully opaque sword pixels.

### Notes

- The three swords currently use ordinary diamond-sword behavior. Their names
  and reference art do not imply recipes, elemental abilities, or Qingfeng's
  PAL-backed five-move combat integration.

## 0.26.0

### Added

- Added the independent diamond-tier `myvillage:qingfeng_sword`, bilingual
  resources, recipe, sword tag, creative-tab entry, original pixel texture,
  Item Contract, and focused resource validation.
- Added persistent server-owned vanilla/cultivation combat preference with a
  configurable default-`R` toggle, bounded input-only payloads, and lifecycle
  synchronization.
- Added the complete five-move Basic Sword cultivation style with centralized
  timings, server-tick combo and recovery state, per-move swept hit geometry,
  wall and target legality checks, server-owned fifth-move stepping, damage and
  enchantment hooks, bounded debug particles, and remote-player synchronization.
- Integrated Player Animation Library 1.1.4 from an externally installed local
  jar with seven original full-body animations, ready/enter transitions, local
  prediction correction, and strict client/common isolation.
- Added a separate Qingfeng-only first-person held-item layer with five bounded
  move curves, authoritative elapsed-time correction, and clean pose recovery;
  it does not move the camera or add client gameplay authority.
- Added an independent first-person local skin-arm and sleeve layer that shares
  the corrected five-move item frame and neutral cultivation hold, supports
  wide/slim skins, leaves the ordinary item pass intact, and keeps PAL
  full-body first person disabled.
- Added a MyVillage-owned segmented first-person joint viewmodel with an
  invisible shoulder driver, visible forearm and hand, a screen-edge elbow
  connector, authored shoulder/elbow/wrist tracks, and right/left grip
  correction; it copies no Epic Fight or GeckoLib code or assets.

### Fixed

- Restored visible first-person response for intercepted cultivation attacks
  with predicted five-move held-item playback and a client-only fallback swing;
  neither sends a vanilla attack packet nor changes server authority.
- Revised the first-person five-move curves after owner feedback with exactly
  20 percent more displacement and earlier wind-up/later strike and recovery
  keyframes, preserving every server move duration and hit window; this fixed
  factor is retained as history rather than the current viewport contract.
- Replaced both rejected complete-arm revisions: separately damped rotation had
  split the hand from the handle, while the later pivot-locked cuboid exposed a
  whole arm floating in the middle of the screen. The segmented chain now pins
  the distal hand to the grip through forward kinematics, connects its elbow to
  the screen edge, omits internal caps, and compacts to `0.45` around the grip
  during active motion without changing server authority.
- Superseded fixed-factor-only first-person amplification with per-move viewport
  calibration targeting a temporal sword-and-arm envelope of at least half one
  screen axis, central-band entry, and no lower-right confinement under the
  `960x540`, FOV-70 reference view, without changing server timing or authority.

### Notes

- PAL remains a required separately installed client-and-server dependency and
  is not shaded, unpacked, copied, or distributed in the MyVillage jar.
- PAL 1.1.4's third-person-model first-person path clipped with this animation
  set, so PAL body arms/camera remain disabled; the dedicated held-item layer
  supplies first-person combat animation instead.
- Automated gates and bounded two-client evidence are recorded; the Qingfeng
  texture, animation feel, and complete owner-led gameplay ledger remain pending
  explicit manual acceptance.

## 0.25.1-fix1

### Fixed

- Changed MyVillage's default stop-meditation binding from `G` to `X`, leaving
  GuideME's default `G` item-index hotkey unreserved.
- Kept the integration free of conflict-specific input logic: MyVillage adds no
  GuideME-specific key interception, remapping, or automatic saved-binding
  migration. The pre-existing ordinary screen guard is unchanged. Existing
  clients that saved `G` must reset or rebind Stop Meditation once in Controls.

### Validation

- Recorded the accepted GuideME UI review and the historical 0.25.1 global-G
  failure separately. Post-fix GuideME `G` behavior remains `not_verified`
  until repeated on the fixed client artifact.

## 0.25.1

### Added

- Added the data-driven `myvillage:cultivation` GuideME guide with three Chinese
  default pages, complete path-matched English translations, live configurable
  key displays, and indexes for the initiation steles and spirit-stone resources.
- Added the one-stack `myvillage:cultivation_handbook` item, which opens the guide
  through GuideME's client API without adding a MyVillage network payload.
- Added root-source `runGuide` live preview plus focused dependency, content,
  resource, documentation, release, and practical-jar validation.

### Changed

- GuideME 21.1.17 is now a required client-and-server dependency, resolved from
  Maven Central for development and installed separately from the MyVillage jar.
- The first handbook slice documents the complete released initiation,
  meditation, progress/stability, lifespan, and deterministic advancement loop
  through the Qi Refining IV ceiling.

### Notes

- The handbook reuses GuideME's base book model; custom art and the known-bad
  GuideME 21.1.17 custom-color path remain outside this compatibility slice.
- Language rendering, navigation/search, item-index jumps, component/model
  appearance, live reload, key remapping, handbook reopen behavior, and existing
  gameplay regression remain pending real-client acceptance.

## 0.25.0

### Added

- Upgraded cultivation profiles to schema v3 with server-owned spiritual
  affinity, default value `10`, explicit v1/v2 migration, and lossless
  preservation of the now-inert legacy meditation reserve.
- Added Profile and Meditation tabs to H. The Meditation tab exposes normal,
  spirit, stop, and advancement buttons that reuse the bounded V/B/G/N action
  payload without accepting client-authored rates, costs, targets, or results.

### Changed

- Normal meditation now grants the current spiritual-affinity value every ten
  eligible ticks. Spirit meditation grants a fixed `50` progress per ten ticks
  and directly consumes the current stage's complete low-grade-spirit-stone
  batch: sensed/Qi I `1`, Qi II `2`, and Qi III `3`.
- Revised released target-layer thresholds to `1000`, `1100`, `1200`, and
  `1300` for advancement into Qi I through Qi IV. Stability stays locked until
  progress is full, then gains current affinity per ten ticks in either mode up
  to stage caps `500/550/600/650` without a stone cost. Successful advancement
  retains integer-floor half of current stability; mastery remains `10` per
  configured cultivation year, and Qi IV remains the release ceiling.

### Notes

- H-tab layout, button interaction, exact ten-tick progress and post-cap
  stability gain, multi-slot stone consumption, and advancement halving remain
  pending real-client acceptance.

## 0.24.0

### Added

- Added low-grade spirit stones plus stone/deepslate spirit-stone ores with
  iron-tier harvest rules, Silk Touch/Fortune loot, three-layer Overworld ore
  generation, bilingual assets, and deterministic resource validation.
- Upgraded cultivation profiles to schema v2 with explicit v1 migration,
  persistent lifespan consumption and meditation reserve, realm-owned maximum
  lifespan, a shared active-player calendar, configurable time scale, relative
  lifespan warnings, and non-lethal exhaustion.
- Added one server-authoritative V/B/G/N session manager for normal meditation,
  spirit-stone meditation, stopping, and deterministic advancement. Basic
  Breathing settles in 100-tick batches, spirit stones fund retained reserve,
  progress is stage-local and capped, and Qi-sensed through Qi III can advance
  one stage at a time to the Qi IV release ceiling.
- Extended the read-only H profile with capped progress, reserve, calendar,
  lifespan, session, and advancement status. Added protocol-v2 payloads,
  focused Java/Python tests, strict OpenSpec coverage, and a documented
  real-client acceptance ledger.

### Notes

- Spirit-stone appearance/distribution, real-client controls and interruption
  feel, H-screen layout, multiplayer time behavior, and in-game advancement
  remain pending manual acceptance. Lifespan exhaustion does not kill or reset
  the player, and Qi IV+, Foundation advancement, pills, facilities, and
  reincarnation remain deferred.

## 0.23.0

### Added

- Added deterministic one-time spiritual-root awakening from the Overworld
  seed, player UUID, and the sorted current spiritual-element ids and
  `awakening_weight` values. The fixed generator selects one through five
  distinct elements and produces positive affinities totaling exactly `10000`.
- Added separate `myvillage:spirit_testing_stele` and
  `myvillage:technique_inheritance_stele` blocks and BlockItems. The first
  atomically installs the root plus `mortal_qi_sensed`; the second evaluates the
  current `basic_breathing` definition and learns it at mastery `0` without
  resetting existing mastery.
- Added `awaken`/`juexing` and `initiate`/`rumen` under both cultivation command
  roots, complete bilingual resources, focused Java/Python validation, and
  dedicated-server handoff coverage. The H profile remains read-only and the
  cultivation snapshot remains clientbound-only.
- Kept meditation, technique execution, spiritual-power recovery, cultivation
  gain, mastery growth, qi-refining advancement, root quality/reroll, worldgen,
  and profile schema changes outside this release.

## 0.22.2-fix1

### Fixed

- Fixed the cultivation profile screen calling the vanilla `Screen#render`
  background-blur pass after drawing its panel. The screen now keeps its owned
  translucent backdrop while rendering the player face, text, bars, and
  dividers without post-process blur.

## 0.22.2

### Added

- Added `/myvillage xiulian` and complete pinyin aliases for every cultivation
  inspection and mutation command. Both cultivation roots accept both English
  and pinyin subcommands and share the same arguments, registry suggestions,
  handlers, permission boundary, atomic service mutations, and synchronization.
- Added command-tree equivalence tests and synchronized the alias contract in
  the cultivation command spec, technical notes, README, and agent guidance.

## 0.22.1

### Added

- Added a configurable `H` key binding and non-pausing, read-only personal
  cultivation profile screen for testing synchronized realm, stage, progress,
  stability, spiritual power, spiritual-root affinities, learned techniques,
  grades, categories, mastery, and profile schema version.
- Added synchronized display colors for the five shipped spiritual elements and
  English/Chinese screen translations. The panel reads only the owning-client
  snapshot and sends no cultivation mutation payload.

## 0.22.0-fix2

### Fixed

- Prevented a descending flying-sword vehicle from forwarding its accumulated
  fall distance to the mounted player when it touches a solid block. Collision
  remains active and ordinary player fall damage outside a mounted sword is
  unchanged. The owner confirmed the damage-free mounted touchdown in game on
  2026-07-12.

## 0.22.0-fix1

### Fixed

- Smoothed received server position and yaw snapshots on the client so the
  mounted flying sword no longer snaps directly between tracking packets. The
  server remains authoritative, and the client still sends only key-state input.
- Corrected the vanilla item-render transform so the horizontal placeholder
  sword follows the entity yaw with its blade tip pointing forward instead of
  stacking the parent model's `FIXED` transform. The owner confirmed both the
  riding stability and blade-tip direction in game on 2026-07-12.

## 0.22.0

### Added

- Added `myvillage:rideable_flying_sword` as a stack-size-one functional item
  and transient one-player vehicle. Right-click toggles server-owned summon,
  mounting, and recall; W/S, A/D, Space, and Shift send only a six-bit input
  intent while the server computes bounded collision-aware flight, hover drag,
  yaw, fall safety, singleton ownership, and lifecycle cleanup.
- Added a client-only vanilla item-model renderer, English/Chinese names, a
  `32x32` placeholder texture, focused protocol/resource validation, usage
  documentation, and dedicated-server startup coverage. In-game control,
  multiplayer, collision, cleanup, and appearance review remain pending.

## 0.21.0

### Added

- Added `myvillage:simple_fox` with vanilla fox model/AI inheritance, a spawn
  egg and translations, intentional empty loot, taiga natural spawning, and
  deterministic `48x32` UV provenance. Focused validators, the build, and
  dedicated-server startup pass, followed by accepted in-game client validation
  on 2026-07-12. This path adds no GeckoLib, paid GPT image call, or custom audio.

## 0.20.0

### Changed

- Rebuilt the existing `pagoda_001..003` cultivation landmarks as three
  deterministic large profiles: compact five-storey, broad five-storey, and
  slender seven-storey. Every occupied level now has a projecting two-band
  eave, bracket rhythm, lifted corners, framed openings, stepped taper, a
  pyramidal crown, and a taller finial; ground colonnades stop at the first
  eave instead of rising as uninterrupted posts to the crown.
- Expanded the two larger pagoda resources and synchronized their measured
  footprints across Python town/sect planning and Java runtime mirrors while
  keeping the compact profile inside the fixed civic-core parcel.
- Added pagoda-specific geometry/provenance metrics, a three-profile
  distinctness and NBT-hash gate, focused tests, updated previews/docs, and a
  new owner visual-review handoff. `candidate_006` remains calibration-only;
  no external structure asset is copied.

## 0.19.1

### Added

- Added the second external-reference-driven generated structure slice:
  `ganlan_stilted_house_001..002`, derived from the `candidate_005` Ganlan /
  干栏式 breakdown as original generator output rather than copied source
  assets. The slice adds a dedicated settlement group/style profile, raised
  living floor, support-post grid, open underside, raised veranda/entry stair,
  deep-eave gable roof, wet-ground context, deterministic provenance and
  stilt-cue validation, focused tests, NBT/place/gallery resources, preview
  handoff coverage and KB/README command docs. The owner accepted the narrow
  generated visual slice on 2026-07-11; broader village/worldgen work remains
  out of scope.

## 0.19.0

### Added

- Added the first external-reference-driven generated structure slice:
  `chinese_huipai_mansion_001..002`, derived from the `candidate_003`
  Hui-style breakdown as original generator output rather than copied source
  assets. The slice adds a dedicated settlement group/style profile, closed
  facade + stepped 马头墙 registry vocabulary, deterministic validation for the
  门堂 → 天井一 → 享堂 → 天井二 → 寝堂 sequence plus paired side-wing enclosure,
  an expanded review-lot footprint with clear inter-building gaps and scaled-up
  hall/side-wing/height massing, sample NBT/place/gallery resources, focused
  tests, KB/README command docs, and report provenance that keeps the
  implementation partial until owner visual verdict.

## 0.18.4

### Added

- Added the visual-reference structure pipeline
  (`add-visual-reference-structure-pipeline`): a CRAFT-routed middle layer that
  decomposes a visual reference or `research/source_structures/` candidate into
  a Reference Breakdown Contract with four typed buckets
  (`direct_component`, `atomic_component`, `generative_grammar`,
  `calibration_only`), explicit downstream routes, source-fact preservation,
  and a pending human verdict. Shipped the KB note
  (`docs/ai-kb/20_visual_reference_structure_pipeline.md`), the contract JSON
  schema, the `candidate_003` Hui-style worked-example breakdown card, the
  lightweight validator (`tools/check_reference_breakdown.py`), and the
  dedicated `genops/pipelines/reference-decomposition.full.yaml` pipeline with
  Commander routing cues. Decomposition is planning evidence; it routes
  downstream work and does not implement it.

## 0.18.3

### Added

- Added CRAFT front-door governance for high-impact project work:
  `openspec-change.full` now routes OpenSpec proposal/change authoring through
  GenOps task ownership, and `tools/genops/check_frontdoor.py` checks protected
  changed paths against run evidence.

### Changed

- GenOps run manifests and summaries now expose per-task artifact indexes so
  Commander handoffs can report run id, pipeline, worker/task ownership,
  artifacts, gates, human verdict state, and next decision.
- Documented that the pre-existing `add-visual-reference-structure-pipeline`
  proposal must be re-entered through CRAFT before implementation continues.

## 0.18.2

### Changed

- Prepared the accepted `path-surface-zoning` water-court pass as 0.18.2:
  regenerated the six Jiangnan mansion NBTs with the reference-style 水亭,
  removed the separate pond-side shed, kept the new visual validators/docs
  current, and rebuilt the packaged jar.

## 0.18.1

### Added

- **Chunky acceptance automation** (`add-chunky-acceptance-automation`):
  staged in-game acceptance now covers the isolated server + Chunky + RCON
  lifecycle, coordinate-addressable `/myvillage ...at` command smoke, full
  optional-mod server startup/cases, and natural `myvillage:sect` worldgen via
  `/locate structure` plus bounded Chunky generation.
- Added RCON/console-safe coordinate commands:
  `/myvillage placeat`, `/myvillage galleryat`, `/myvillage townat`,
  `/myvillage sectat`, and `/myvillage sectat worldgen`.

### Changed

- The acceptance script now verifies staged optional-mod jar ids and mandatory
  jar dependencies from `exmod/mod_jars.zip` before full-modset startup. The
  full run writes `reports/chunky_acceptance_report.json`; Chunky remains an
  acceptance-only jar and is not packaged into MyVillage.
- Visual acceptance prep now has `tools/write_visual_acceptance_report.py`,
  which links representative offline preview PNGs with the latest Chunky command
  and worldgen targets in `reports/visual_acceptance_report.json/.md`.
- `validate_generated_structures.py` treats the standalone `hero_rockery` review
  fragment as a non-building landscape specimen for key-building-block checks.
- Replaced the pond-side water pavilion with a reference-image-style heavy
  scenic pavilion: raised stone base, dark wooden deck, heavy timber posts,
  railings, lattice/trapdoor bracket details, hanging lanterns, broad dark-oak
  double eaves, and grey stone roof ornaments; removed the separate right-side
  pond `waterside_gallery` shed.
- Completed the final `path-surface-zoning` mansion review repairs: 水边廊 and
  主院 抄手游廊 now render as 3D galleries with floors, columns, balustrades, and
  roofs; 后院 and 花园 are separate bands, the 绣楼 stays in 后院, and the 主院
  heart remains grass.
- Tightened the waterside mansion garden after low-angle visual review: 水边廊 is
  now one short straight run instead of the whole noisy shoreline, avoids the
  pond/rockery/bridge, and bridge/gallery clear-water lanes are kept free of
  lily-pad clutter. `validate_mansion` now reports
  `waterside_gallery_clutter:*` and `pond_lily_clutter:*` for regressions.
- Fixed the focused 水亭 composition: `garden_pavilion` now chooses a dry
  pond-bank footprint adjacent to pond water instead of the stale west-band
  placement, and `validate_mansion` reports
  `garden_pavilion_detached_from_pond:*` if it drifts away again.
- Added `tools/render_structure.py --target X Y Z` for focused Chunky look-at
  renders when the scanned bbox center is not the visual subject.
- Replaced the water pavilion's raw stair ridge cap with a low two-step slab
  roof so focused low-angle renders no longer show a floating default-stair
  artifact above the 亭.
- Lightened the pond closeup wood forms after visual review: the 水亭 now uses
  fence posts with a thin explicit stair eave and one center cap, and the short
  水边廊 uses compact posts with roof limited to the post line so it stays open
  instead of reading as a 3x2 shed.

## 0.18.0

### Added

- **江南大宅/庭院 路径表面分区** (`path-surface-zoning`): the courtyard/mansion
  ground + path layer is now a two-axis surface model so the path reads as
  **three routes with six surfaces** instead of one flat gravel stripe.
  - Material follows the **zone** (six zones → six style slots): the formal
    axis is 青石 (`PATH_FORMAL` / `smooth_stone`), the 天井/院心 ring is 灰砖
    (`GROUND_YARD_HEART`), 廊下 is 木石 (`PATH_GALLERY` / `oak_planks`), 夹道 is
    砖 (`PATH_ALLEY`), the garden tour is 苔石 (`PATH_TOUR` /
    `mossy_stone_bricks`), and the waterside edge is 石阶 + 木板桥
    (`PATH_WATERSIDE`).
  - Shape follows the **route**: the formal/service backbone keeps the
    single-source shortest-path tree (`PATH_FORMAL`); the garden tour is a
    winding waypoint polyline (假山南 → nearest pond shore → 亭) via
    `_route_tour_path`, each segment a single-source shortest path with an
    obstacle set forcing any segment that would cut through the rockery/pond
    to route around it.
  - Three new path termini make the routes real: the `moon_gate_passage`
    (月洞门 穿墙通道 through the garden screen wall, the formal↔tour material
    boundary), the 水边廊 (shoreside `covered_gallery` variant along the pond
    shore), and the `service_house` archetype (仆役房 along the 倒座 夹道, a
    mandatory path endpoint).
  - The cross-pond 汀步 spike-row (deleted `rockery_block` cells) is replaced
    by a flat slab bridge (`oak_slab`/`spruce_slab`) spanning the pond's
    narrowest crossing to the 亭/island — the spike problem was the block, not
    the crossing.
  - Each family realizes only the zones it has space for: `chinese_mansion`
    gets the full vocabulary; `chinese_courtyard` gets formal + heart + gallery
    + alley; embedded `small_courtyard` (in `cultivation_town`) gets formal +
    heart. Styles that did not adopt the slots (`cultivation_town.json`,
    `cultivation_sect.json`) fall back to the legacy ground/path tiles and
    regenerate byte-identical.
- New `validate_mansion` checks: `surface_zone_material:<zone>:<cell>` (each
  cell's block matches its zone's slot primary), `tour_segment_disconnected`
  (every tour waypoint segment is a connected single-source tree), and
  `waterside_bridge_incomplete` (the slab bridge spans both shores). Added
  `docs/ai-kb/16_path_surface_zoning.md`.

### Changed

- Regenerated `chinese_mansion_001..006.nbt` (Stage 4: the slab-bridge
  `PATH_WATERSIDE` writer + the three new validators). `chinese_courtyard_*`
  and embedded `cultivation_town_*` regenerate with the four-zone ground +
  formal/heart path subset. `cultivation_sect_*` and `medieval_*` stay
  byte-identical (byte-stability guard extended in
  `tools/buildgen/tests/test_chinese_courtyard_regression.py`).

## 0.17.0

### Added

- **江南大宅 enclosure-planning skeleton** (`rebuild-mansion-enclosure-plan`):
  `chinese_mansion_001..006.nbt` now use a building-enclosure model instead of
  fixed z-band placement. The south entrance is a real `gate_house`
  through-building, not a carved wall hole; each mansion role gets a form-rule
  door wall (倒座 north, 厢房 inward, 敞厅 south, 楼阁 north), and every
  door-front is routed into the gravel backbone from the gate-house inner
  opening.
- Added regression coverage for the orientation mechanism, gate-house
  perimeter sealing, derived-yard contiguity, inner-gate adjacency, and
  non-mansion byte stability.

### Changed

- Regenerated the six shipped `chinese_mansion_*` structures under the full
  profile. `chinese_courtyard_*`, embedded small courtyards, cultivation town,
  cultivation sect, and medieval structures remain on their existing planners;
  propagating the enclosure skeleton to those courtyard families is tracked as
  follow-up work.
- `validate_mansion` reports `facing_per_slot` and `door_reachable_rate` and
  enforces gate-house presence, role-facing invariants, door-on-path, and
  derived-yard adjacency in addition to the preserved grid and voxel checks.

## 0.16.2-fix1

### Fixed

- **山顶树恢复微缩比例**：不再用完整草方块、原木和树叶替代源雕塑；`g/t/l`
  微体素现在直接烘焙进峰顶 hero 模型，形成带倾斜树干、横枝和不对称云片树冠的
  半格高盆景。碰撞仍只取岩石，不影响峰顶通行。
- **泉水真正从山里流出**：移除固定在山体外侧的 `rockery_cascade` 方块列，
  改为将源雕塑的 `w` 微体素作为带水色的透明模型几何烘焙。生成器保证泉洞、
  贴岩阶流和山脚内池构成一个六向连通水体；真实流体仅保留在封闭山脚水池，
  不会扩散淹山。
- 重生成独立 `hero_rockery` 与六座 `chinese_mansion_*`，更新字节稳定基线；
  新增微缩植被、连续水路、无整方块树及无外置瀑布的回归断言。

## 0.16.2

### Changed

- **重塑 hero 太湖石假山的形态与水景**：`docs/rockery_compressed.json` 改由
  参数化生成器 `tools/buildgen/gen_hero_rockery_sculpt.py` 重新雕刻，按参考图
  `docs/mt.png` 做成层叠收分的太湖石（石为主、青苔点缀），并配合泉自山体内
  涌出、沿台阶跌落的细瀑与嵌入山脚、与山体相连的水池。新增离线微体素预览工具
  `tools/buildgen/preview_voxel_field.py`（直接渲染 48³ 体素，便于在不进游戏时
  比对参考图）。

### Fixed

- **修复假山被流水淹没的问题**：原 hero 路径把山顶出水口与山脚 cell 设为
  `waterlogged=true`，而含水方块本身是会向相邻空气扩散的水源，导致水流如
  “蓝色帐篷”般盖住整座假山并溢成护城河。改为完全不使用 waterlog；可见水景仅由
  封闭水池（source）+ 非流体 `rockery_cascade` 细瀑构成，泉眼由烘焙进岩体模型的
  石窟表现，确定性且不再泛滥。
- hero cell 数由 19 增至 20；六个 `chinese_mansion_001..006.nbt` 与独立
  `hero_rockery` 片段已重新生成并通过结构 / voxel-walkability / fallback 校验，
  字节稳定基线同步刷新。

## 0.16.1

### Added

- **Hero 太湖石假山 (`add-hero-rockery`)**：将
  `docs/rockery_compressed.json` 的 48³ 微体素雕塑切成 19 个可堆叠的
  `rockery_block` cell，形成一座 3×3×3 实体假山；石/苔材质按微体素分别
  烘焙，并增加真实水池、水浸山脚与峰顶出水口、无碰撞细瀑
  `myvillage:rockery_cascade`、峰顶草木和可站立亭基。
- 新增独立验收结构与命令 `/myvillage place hero_rockery`；六个
  `chinese_mansion_001..006.nbt` 已重新生成并通过 3D voxel-walkability
  校验。

### Fixed

- 修复江南大宅假山由二维 heightfield 每格仅放一个 block，导致整体呈现为
  1–2 格高“尖刺阵列”而非山体的问题。通用 codebook 假山路径保持不变，
  hero 路径改为固定、字节稳定的三维堆叠 cluster。

## 0.16.0-fix2

### Fixed

- **中式宅邸院子地板被沙砾铺满**：`_route_complete_path` 原本把多源 BFS
  的整个可达集合都铺成 `GROUND_PATH`（gravel），覆盖了
  `_place_yard_ground` 写的露天草地 (`GROUND_YARD_OPEN`) 与屋檐下石砖
  (`GROUND_YARD_UNDER_EAVE`)。改为只铺**最短路径骨架**——以街门入口为
  单一源点的 BFS 前驱树，对每个 endpoint（各门/水井/花圃/月台）沿前驱
  回溯到街门，取并集。骨架外的格子保留其地面材质，院子恢复以草地为主、
  gravel 路径为辅的自然形态。
  - 复核：多源 predecessor 树不可用作骨架——所有 endpoint 都是源点，相邻
    endpoint 互相回溯，骨架退化为不相连的零散点，既不穿过台基 (plinth)
    边界也不放台阶，导致主院门 `voxel_unreachable_door`。必须用街门入口
    单源，骨架才会从外院穿过 plinth 到达主院门，台阶生成器
    (`_place_band_transition_stairs`) 才能在边界放 `stone_brick_stairs`。
  - 影响：`chinese_courtyard_*`、`chinese_mansion_*`、`cultivation_town_*`
    （内嵌小庭院）NBT 重新生成；`cultivation_sect_*` 与 `medieval_*` 不受
    影响（字节稳定，回归测试 `test_chinese_courtyard_regression.py` 47 个
    NBT 哈希不变）。
  - spec：`courtyard-path-network/spec.md` 同步——多源 BFS 现仅用于
    `endpoint_unreachable` 可达性校验，骨架改由街门入口单源 BFS 产生。

## 0.16.0-fix1

### Fixed

- **服务器启动崩溃 → 所有指令不可用**：将 `SectStructures` 的 `StructureType` /
  `StructurePieceType` 注册从 `DeferredRegister`（依赖 `RegisterEvent` 时序）改为
  直接 `Registry.register()`（在 mod 构造函数执行期间、注册表解冻后立即写入
  `BuiltInRegistries`），消除 `Unknown registry key: myvillage:sect` 崩溃。

## 0.16.0

### Added

- **江南大宅 (chinese_mansion) compound family — 3-进 大宅** (`rebuild-jiangnan-mansion`).
  Six new NBTs `chinese_mansion_001..006.nbt`, each a full 3-进 compound:
  street gate → 照壁 (照壁侧立 off-axis) → 前院 → 仪门 → 主院 (敞厅 + 正房 on
  台基 plinth) → 二门 → 后院 (绣楼/藏书楼 tower_house ×1 or ×2) → 花园 (假山 +
  水池 + 亭 + 汀步). Variant axes: `courtyard_size ∈ {small, medium, large}`,
  `gate_form ∈ {recessed, flush, paifang}`, `garden_scale ∈ {small, large}`,
  `tower_count ∈ {1, 2}`, `main_bays ∈ {3, 5}`. In-game commands:
  `/myvillage place chinese_mansion_001` … `_006` and
  `/function myvillage:gallery/chinese_mansion`. New style profile
  `chinese_mansion.json` adds slots `FACADE_OPEN`, `GARDEN_PATH`,
  `ROCKERY_STONE`, `GARDEN_PAVEMENT`, `POND_STONE`.
- **假山 (garden_rockery) + 水池 (garden_pond) parcel nodes.** 假山 uses
  `myvillage:rockery_block` (new self-namespace block, registered in
  `ModBlocks.java` / `RockeryBlock.java`) with `variant`, `facing`, and
  `moss_level` blockstate properties and per-variant `VoxelShape` for partial
  climbability. 水池 uses freeform value-noise shoreline with
  `minecraft:water` at y=-1.
- **`open_hall` archetype (敞厅).** Front facade resolves through `FACADE_OPEN`
  slot (columns + open eave, no full-height front wall). Placed on main-yard
  台基 plinth in the mansion compound.
- **`tower_house` archetype (绣楼/藏书楼).** Two-story sub-building via
  `multi-story-massing` (`stories=2`, floor slab, stairwell, per-story facade
  band). Placed off-axis in 后院.
- **`garden_pavilion` archetype (亭).** Four-column standoff with
  `chinese_round_ridge` roof. Placed on or near 假山 peak in the 花园.

### Fixed

- **Voxel-walkability validator added** (`rebuild-jiangnan-mansion`). Both
  `validate_compound` (一进 四合院) and `validate_mansion` (三进 大宅) now run
  a 3D BFS (`_voxel_walk_bfs`) from the gate-entry standable column. All
  building door positions and path endpoints must be reachable. New error codes:
  `voxel_unreachable_door:<archetype>`, `voxel_unreachable_endpoint:<x,z>`,
  `voxel_step_cliff:<a>-><b>`, `voxel_blocked_by_solid:<x,z>`. The
  `plinth_edge_missing_stair` check now fires only when `plinth_h >= 2`
  (Δy=1 is a free Minecraft autostep).
- **照壁侧立 — screen wall moved off-axis in `chinese_courtyard`.** The 影壁 now
  stands at `axis_x ± offset` (照壁侧立 form, `meta.form = "jingbi"`), leaving
  the central path clear. The old on-axis placement blocked the player walking
  from the gate to the 垂花门 without detour. **Breaking (NBT regeneration):**
  `chinese_courtyard_001..006.nbt` regenerate with the off-axis 照壁.
- **垂花门 passage expanded to ≥3 cells.** Both 一进 inner gate (垂花门) and
  mansion inner gates (仪门, 二门) now open at `axis_x-1`, `axis_x`, `axis_x+1`
  minimum.

### Changed (internal, no gameplay change)

- **Courtyard ground + path layer rebuilt** (`fix-courtyard-ground-walkability`).
  The shipped `chinese_courtyard_NNN.nbt` files were structurally correct 一进
  plans but practically unwalkable: the yard floor was AIR outside a 1-cell
  gravel strip (the player fell through), the path stopped at the 正房 leaving
  厢房 / 倒座 / 井 / 鱼缸 / 种植 unreachable, and the plinth edge was a 2-block
  jump. Two new data-driven passes fix all three: `_place_yard_ground` fills
  every non-building cell with 露天 `grass_block` / 屋檐下 `stone_bricks`
  (courtyard-ground-layer spec), `_route_complete_path` runs a multi-source BFS
  from every door + water + planting + moon-platform endpoint to write one
  connected `GROUND_PATH` network (courtyard-path-network spec), and
  `_place_plinth_stairs` drops a single `stone_brick_stairs` at each plinth
  boundary. The same fix applies to the embedded small-courtyards in
  `cultivation_town_NNN.nbt`. **Breaking (NBT regeneration):** the 6
  `chinese_courtyard_*` and 6 `cultivation_town_*` NBTs regenerate with new
  content (same filenames); `cultivation_sect_*` and `medieval_*` stay
  byte-stable. No `/myvillage` command-surface change.
- **Cross-platform build and report determinism.** `build.gradle` now selects
  the Python interpreter per OS (`python` on Windows, `python3` elsewhere),
  falling back to the `PYTHON` environment variable when set, so `gradlew build`
  works out of the box on Windows without the Microsoft Store `python3` stub
  breaking the resource-generation task. Generator/validator/preview tools now
  emit repo-relative paths as POSIX strings (forward slashes) via
  `tools/buildgen/export.py::repo_relpath`, so committed JSON reports and
  human-facing output are byte-identical across Linux and Windows.
- **Library reports slimmed** (`slim-library-reports`, 0.15.0-fix2). The
  `reports/*_library_report.json` files had grown to tens of thousands of lines
  (largest: `cultivation_town_compound_library_report.json` at 164 269 lines /
  3.2 MB) because each generator serialized the full compound/massing graph,
  including per-cell coordinate lists (`parcel_nodes[].cells`,
  `building_slots[].footprint`) that no validator or runtime ever reads. The
  generators now emit a compact `to_summary_dict()` form (cells/footprints
  folded into counts + bounding boxes; non-volume massing nodes and node `meta`
  dropped; `meta.frontage` and `meta.terrace_levels` kept because the
  validators read them). All `*_library_report.json` shrink 59–96% (e.g.
  `cultivation_town_compound` 164 269 → 8 469 lines, `compound` 126 048 → 6 094,
  `cultivation_sect_compound` 72 775 → 2 561). **No `.nbt`, mcfunction, or
  gameplay change** — the in-memory `to_dict()` and generation logic are
  untouched, and regenerated NBTs are byte-identical to the pre-edit state.
- **`reports/` now git-ignored as generated output.** All library/validation
  reports under `reports/` are deterministic generator/validator outputs, so
  `.gitignore` now excludes `reports/*` and the 20 previously-committed report
  files were `git rm --cached`'d (kept on disk; regenerate locally with the
  `tools/` generators). Two files stay tracked via `!`-exceptions because the
  build cannot re-derive them: `reports/town_distinctness_calibration.json`
  (tuning floors read by `validate_runtime_town_plan.py`) and
  `reports/cultivation_style_baseline_hashes.txt` (a pre-migration historical
  hash snapshot). No build behavior changes — the generators and validators
  continue to read/write the files on disk exactly as before.

## 0.15.0

### Changed

- **Chinese courtyard library rebuilt as 一进四合院**
  (`rebuild-chinese-courtyard`). Replaced the shared manor-like shell with real
  硬山/悬山/歇山/卷棚 registry forms, 台基, 檐廊, and 3/5/7-bay hierarchy. The
  parcel plan now separates outer and main yards with 影壁, one 垂花门, two
  returning 抄手游廊, and a 月台. Six deterministic templates vary plan and
  roofline, with full/vanilla validation and legacy-family byte-stability
  guards. **Breaking:** `chinese_courtyard_001..006.nbt` retain their resource
  names but have regenerated footprints, silhouettes, and interiors; existing
  placed blocks are unaffected, while future placements use the rebuilt NBTs.

- **Region runtime binding (洲/域 runtime)** (`add-region-runtime-binding`).
  The macro layer's offline-only era ends: a passive runtime companion now
  consumes the per-seed region graph in-game. The anchor (中州) center is placed
  at the world origin with all 洲 within an anchor-centered ~4000-block radius
  (pure coordinate transform — `SCALE = RADIUS_WORLD(4000) /
  RADIUS_GRAPH_OUTER(1.45 = 1.4 walled ring + 0.05 max embed jitter)`, no
  rotation, no world writes). World spawn is bound **once** per world,
  deterministically from the seed, to the lowest-tier eligible non-walled
  region (sort key `(assigned_tier ASC, distance_from_anchor DESC,
  qi_midpoint ASC, id ASC)`) with a safe-surface spiral search; existing
  custom/admin spawns are preserved on first load (`SavedData`-gated), and
  `/myvillage spawn recompute` (admin) forces an override. A server-side query
  API exposes `region_at(x, z)`, `current_region`, `current_rung`, and
  `next_rung_regions`, where the rung ladder is the ascending distinct tiers
  among non-walled regions and `next_rung_regions` returns a **set** at tier
  ties (灵岳 + 西漠 both at 18) — a branch point the deferred 正道/魔道
  alignment system will resolve. `/myvillage spawn info` (player) prints the
  spawn region/block and the caller's region/rung/next-rung set. The runtime is
  passive: it reads the world seed, caches the graph, answers queries, and calls
  `setDefaultSpawnPos` exactly once — it overrides no biome, hooks no chunk-gen,
  and writes nothing beyond spawn metadata; the offline `region-topology`
  generator's contract is unchanged. The region RNG + generator are ported to
  Java (`com.example.myvillage.region.runtime`) bit-identical to
  `tools/buildgen/region_topology.py`, enforced by golden fixtures under
  `src/test/resources/region_runtime_fixtures/` (regenerate via
  `tools/buildgen/tests/generate_region_runtime_fixtures.py`). Downstream
  consumers (compass/map indicator, alignment tie resolution, mobility gating,
  runtime subject placement, 隔-edge terrain relief, region extents) remain
  deferred. See `docs/ai-kb/13_region_topology.md` and the
  `region-runtime-binding` spec.

## 0.14.0

### Changed

- **Region topology (洲/域) layer — offline-first** (`add-region-topology`).
  Added the macro layer the mod was missing: a per-seed region graph of 5–7 洲
  with a single centered 中州 `anchor`, rule-governed 连 (passable) / 隔
  (separated) relations, a tier gradient under `tier_step N = 5`, and a sealed
  魔域-style `walled` region (all 隔 except ≤1 关隘). Topology is authored as a
  ruleset (count range, tier range/step, separator palette `{特殊山脉, 特殊海洋}`,
  role rules) while geometry is randomized per seed; generation is constructive
  (a 连 spanning tree + outward tier assignment make connectivity and the
  tier-step hold by construction), so it is seed-deterministic and never
  re-rolls. New data under `worldgen/region_profile/` + `worldgen/region_topology.json`
  (+ a shipped `region_topology_example.json`), and new `tools/buildgen/region_topology.py`
  (single shared source), `tools/generate_region_topology.py`,
  `tools/validate_region_topology.py`, and
  `tools/generate_region_topology_preview.py` (SVG + ASCII previews wired into
  the aggregate). Added `docs/ai-kb/13_region_topology.md`. This layer is
  offline-only — **no runtime worldgen, no in-game command** this change; a
  later change consumes the typed edge list for terrain relief.

## 0.13.0

### Changed

- **Town shape vocabulary and seed-driven grid** (`town-shape-vocabulary`).
  `/myvillage town [seed]` now selects independently from square, 天圆 circle,
  oval, 半月 D-shape, true octagon, and trapezoid wall families plus optional
  barbican/bastion modifiers. The spine, three cross-lanes, and outer district
  widths vary within bounded orthogonal ranges. Outer district cell sets clip
  to the perimeter curve while the civic core remains rectangular. Python and
  Java share `town_hash`, a five-seed parity fixture, integer circle/ellipse
  sweeps, and a calibrated pairwise distinctness gate. Added
  `docs/ai-kb/12_town_shape_vocabulary.md` and updated command examples.

## 0.12.0

### Changed

- **Town shape is no longer a hard square** (`town-shape-irregularity`). The
  runtime cultivation town (`/myvillage town`) wall is now a deterministic,
  seed-derived variant (`square` / `chamfer` / `indent`) selected from a fixed
  set, with geometry a pure function of (site, shape id) so the Java realizer
  mirrors it from the id alone (no shared RNG). All deformation is inward-only,
  confined to the empty east/west margin and corner triangles, so the south-gate
  segment, the street grid, and every district are untouched; the bitten cells
  are emitted as a `moat` negative space. `TownDistrict` now carries an
  authoritative `cells` set (with `bounds` kept as the AABB), subdivision is
  cell-set-aware (`_parcel_fits`), and the `chamfer` shape also chamfers the two
  fringe districts' exterior corners (the only safe non-rectangular districts —
  parcel-bearing and civic-core districts stay rectangular because the
  civic-precinct derivation is coupled to `core.bounds`). Python⇄Java parity
  gains perimeter-variant and fringe cell-count descriptors. No command-surface
  change; `/myvillage town [seed]` behaves the same. See
  `docs/ai-kb/11_town_shape_irregularity.md`.

### Fixed

- **Build configuration** (`build.gradle`). The `net.neoforged.moddev` 2.0.141
  plugin removed the `runs { configureEach { modSource ... } }` DSL —
  `RunModel` has no `modSource` in this version, so `./gradlew compileJava`
  failed at evaluation time. Source registration now uses the top-level
  `neoForge { mods { "${mod_id}" { sourceSet sourceSets.main } } }` block.

## 0.11.0-fix2

### Changed

- **Documentation knowledge-base maintenance** (`add-docs-kb-governance`). Added a
  knowledge-base entry map at `docs/ai-kb/INDEX.md` and linked it from `README.md`
  and `AGENTS.md`; cross-linked the worldgen / validation / blueprint-schema doc and
  spec pairs with see-also references; corrected the README "Current Scope" lists so
  shipped sect worldgen is listed as included and no longer excluded; split the two
  oversized `AGENTS.md` conventions (settlement composition into sect/worldgen/town
  sub-items, acceptance command checklist moved into `docs/ai-kb/09_validation_checklist.md`);
  made the version-bump rule single-source in `openspec/config.yaml`, referenced from
  `AGENTS.md` and this changelog. Documentation-only; no code or asset changes.

## 0.11.0-fix1

### Fixed

- **Worldgen sect no longer stalls chunk loading on approach**
  (`fix-sect-worldgen-chunk-stall`). The single `SectStructurePiece` spans
  ~8×15 chunks, and `postProcess` re-ran the *entire* mountain + compound build
  for every overlapping chunk — relying on the sink to merely discard
  out-of-chunk writes — so each of ~120 chunks redundantly performed tens of
  thousands of `getBaseHeight` samples plus re-parsed every slot template's NBT.
  The worldgen thread pool saturated and the feature-stage dependency front
  could no longer advance, freezing chunk loading server-wide as a player
  approached (before the sect was even visible, and through `/tp`). The realizer
  now clips its iteration (not just its writes) to a `SectSink.clip()` — the
  current chunk's column area in worldgen, unbounded for the on-the-spot
  command — so each chunk does work proportional only to its own slice; total
  work drops from O(footprint × overlapping-chunks) to O(footprint). Parsed
  templates are cached in `ModBlockFallback` (cleared on reload) instead of
  re-read per chunk, and per-volume placement RNG is derived from the stable
  sect site + volume origin (not the chunk) so a building straddling a chunk
  seam rolls the same variant/orientation in both halves. Siting, biome gating,
  separation, `/locate`, the `/myvillage sect` command, and the derived-mountain
  geometry are unchanged (Python/Java parity still validated).
- **Worldgen sect terrain no longer floats above the compound.** Exposed once the
  stall fix let the worldgen path complete: the mountain/terrace passes placed via
  `base.offset(x, absoluteY, z)`, double-adding `base.getY()`, so the derived
  terrain (mountain, terraces, stairs, retaining faces, cliff-back, galleries,
  cloud-sea, flying-bridge deck) baked ~a base-height above the buildings, which
  `realizeSlots` placed at the correct absolute Y — the terrain and the sect were
  "not unified." A `SectGenerator.at(base, localX, worldY, localZ)` helper now
  places all terrain passes at the absolute Y directly, so terrain and buildings
  share one elevation frame. The on-the-spot `/myvillage sect` command shares the
  realizer and benefits from the same fix.

## 0.11.0

### Added

- **Sect worldgen with derived mountain** (`add-sect-worldgen`). A custom
  worldgen `myvillage:sect` `Structure` (`SectStructure` + `StructureType` +
  `SectStructurePiece`, registered through `SectStructures`) sites cultivation
  sects during chunk generation — rare, biome-gated to a high-relief biome tag
  (`data/myvillage/tags/worldgen/biome/has_sect.json`), spaced as a regional
  landmark (`worldgen/structure_set/sect.json`), world-seed reproducible, and
  baked into chunks with no force-load and no build pop-in. The structure is
  locatable via `/locate structure myvillage:sect`. The "no worldgen is
  registered" note is removed.
- **反推山形 mountain derivation.** Rather than search for matching natural
  terrain, the generator derives the mountain from the compound's exported
  terrace profile: terrace elevations as the skeleton, seed-driven value noise
  for the inter-terrace and outer slopes, an outer blend skirt grading the
  man-made relief into the natural heightmap (no cut-off seam), a sheer
  cliff-back face behind the summit, a placed translucent cloud-sea (云海面)
  sheet with feathered edges + powder-snow wisps between the gate and disciple
  terraces, and a solitary peak (孤峰) raised under the detached-spire feature.
  Implemented in `SectMountain.java` and mirrored/validated offline by
  `tools/buildgen/sect_mountain.py`.
- **Shared realizer + force-generate command.** The `SectGenerator` realizer is
  refactored onto a `SectSink` so the same plan + geometry serve both the
  on-the-spot `/myvillage sect [seed]` command (unchanged, rests on the live
  surface) and worldgen (rests on the derived mountain, clamped per chunk).
  `/myvillage sect worldgen [seed] [variant]` force-generates a worldgen-style
  sect and can force one of the three detached-spire variants (or `none`).
- **Validation + preview.** `validate_sect_generation.py` adds worldgen checks
  (biome gating, minimum separation, blend-skirt seam, terraces at planned
  elevations, cliff-back, cloud-sea, deterministic spire, mountain parity
  constants, feature presence survey) into `reports/sect_generation_validation.json`;
  `generate_sect_plan_preview.py` adds a top-down derived-mountain heightfield
  preview (`mountain.png` / `mountain.json`) to each sect plan viewer.

## 0.10.0

### Added

- **Terraced cultivation sect compound** (`sect-compound`). A deterministic
  terraced axial sect-compound planner (`tools/buildgen/sect.py`) and a
  structurally-equivalent runtime realizer
  (`src/main/java/com/example/myvillage/sect/SectGenerator.java`) compose an
  ordered terrace stack ascending a single fall-line ritual axis from the
  mountain gate (山门) on the lowest terrace to the cliff-backed principal hall
  (主殿) on the summit. The default skeleton is five terraces
  (gate / disciple / assembly / scripture / summit), parametric on terrace
  count (4–6), rise, depth, width taper, axis-stair width, and cliff-back
  height. Slot importance grades with terrace level — the principal hall and
  scripture pagoda hold the top tiers — and flanking volumes (disciple rows,
  paired pagodas, flanking bell/drum towers) mirror about the axis and are
  joined by covered galleries (廊) recorded as circulation links with both
  endpoints. Each terrace meets the next through a retaining face and an
  on-axis stair flight. The new `/myvillage sect [seed]` command builds the
  compound against terrain, force-loading the footprint, carving and retaining
  each terrace so platforms step the slope with no floating or buried slabs,
  routing palette ids through the mod-fallback resolver, and reporting any
  extent it cannot build.
- **Detached-spire flying-bridge feature** (`sect-flying-bridge`). The sect
  compound ships an optional detached-spire feature as three deterministic
  form variants (differing on detached volume, bridge span/shape, and spire
  offset/bearing), selected per seed (or absent). When present, a detached
  volume sits on its outcrop reachable only by a flying bridge (飞桥) recorded
  with both endpoints on the compound and the detached volume.
- **Sect terrace-profile export (反推山形 contract)**. The planner exports the
  terrace skeleton and geometry parameters (per-terrace elevation/bounds, rise,
  depth, taper, axis-stair width, cliff-back height) as explicit outputs for
  the planned sect worldgen change to derive the man-made mountain from the
  same parameters.
- **Sect validation + preview**. `tools/validate_sect_generation.py` asserts
  the plan invariants (ascending axis, importance grading, gallery/bridge
  endpoint anchoring, variant distinctness, reproducibility), template fit
  against the shipped `.nbt`, and Python/Java planner parity, emitting
  `reports/sect_generation_validation.json`. `tools/generate_sect_plan_preview.py`
  renders top-down sect-plan previews into the preview aggregate.

## 0.9.0

### Added

- **Districted cultivation town plan** (`town-districts`). The runtime
  `/myvillage town` realizer now produces a ~160×160 修仙坊市 partitioned into
  named districts (gate/market/residential/civic_core/fringe), each carrying
  its own density, storey band, material register, and archetype roster from
  the `cultivation_town` group's `district_brief`. The ritual axis (plaza /
  paifang / lantern approach) is expressed inside the civic core rather than
  spanning the whole town. The footprint is force-loaded via chunk tickets so
  the town generates in one command.
- **Street frontage with party-wall rows** (`street-frontage`). Market and
  residential parcels align to the street wall and share gable walls with
  neighbors (沿街连排 / 共墙铺面), producing continuous row frontages and
  intentional narrow alleys (窄巷) instead of centered-lot plinths.
- **Vertical landmark archetypes** (`vertical-landmark`). `pagoda` (塔),
  `pavilion` (楼阁), and `bell_drum_tower` (钟鼓楼) archetypes composed from
  the existing terrace + tiered flying-eave vocabulary and registered as
  roof forms in the form registry. A skyline rule requires the civic core to
  carry at least three above-threshold tall volumes, with at least one being
  a vertical landmark, so the core silhouette rises above the surrounding
  roofline. The `silhouette_score` heuristic now rewards tall rooflines and
  vertical-landmark bonuses.
- **Cultivation street life** (`cultivation-street-life`). The town realizer
  replaces the prior placeholder vanilla furniture (campfire / oak fence /
  white wool / podzol) with a cultivation-themed vocabulary: 幌子 shop
  banners, 药圃/灵田 tending beds and crop rows, 炼丹炉 alchemy furnaces,
  法器摊 artifact stalls (profile-gated `fetzisdisplays` racks), and 阵纹
  formation floor patterns in the civic plaza. Villager inhabitants and
  occasional 灵狐 spirit foxes populate the districts at scale.
- **Profile-gated runtime decor.** Runtime-placed decor fixtures resolve
  through `ModBlockFallback.resolveBlockState()`, so external mod blocks
  (`fetzisdisplays`) are used when loaded and fall back to vanilla barrels
  when absent, mirroring the same modset catalog the Python generators use.

### Changed

- **`cultivation_town` group roster now includes vertical-landmark archetypes**
  (`pagoda`/`pavilion`/`bell_drum_tower`). The `civic_core` district brief
  draws them; the static `cultivation_town_NNN` compound library is reclassified
  as district-fill courtyard tissue — the roster filter in `compound.py`
  restricts the small-block generator to the courtyard-compatible subset.
- **Town footprint raised to 160×160.** `MAX_FOOTPRINT_AXIS` lifted from 96 to
  160 in both Python planner and Java realizer; the `loaded()` hard gate replaced
  with chunk-ticket forced loading released in a `finally` block.
- **`validate_town_plan` / `validate_runtime_town_plan` extended.** District
  partition, core-outranks-fringe hierarchy, skyline relief, and frontage
  sparsity invariants are asserted; the validator now checks every structure
  template fits its parcel with a non-empty ground layer.
- **`quality.py` silhouette score enhanced.** Vertical-landmark roof forms and
  tall roofline heights contribute to the silhouette heuristic so the building
  report reflects the civic core's vertical relief.
- **`check_cultivation_forms.py` extended with vertical-landmark smoke tests.**
  The three new roof forms (pagoda/pavilion/bell_drum_tower) are resolved
  through the form registry and checked for spire finials, upturned corners,
  and belfry bells.

## 0.8.1-fix2

### Fixed

- Closed the corner holes in the upper stories of pagoda buildings
  (`scripture_pavilion`). The stairwell was reserved flush against the volume
  edge, but pagoda story insets step the upper-floor perimeter walls inward
  onto that shaft; the stair's protected void then blocked the inset wall from
  sealing, leaving open corners on the 2nd and 3rd floors. `_reserve_stairwell`
  now offsets the shaft inward by the deepest story inset so it stays inside
  the most-inset footprint and the outer shell closes on every story. Visible
  in `cultivation_sect_001` (summit pagoda) and standalone `scripture_pavilion`.

## 0.8.1-fix1

### Fixed

- Closed side-wall holes on gabled buildings: `gable_roof()` now fills each
  gable-end column from the eave up to the true roof skin directly above it
  (climbing to the real `ridge_y`), and backs any cell carrying only a roof
  stair with a full gable block one step inboard. Apex gaps, edge gaps where
  an overhung slope arrived late, and see-through half-block roofline cells
  are gone.
- Stopped interior furniture mounting on a neighbour volume's exterior wall:
  the blacksmith `smithy` zone is now inset like `forge`/`storage`, and
  `spots_along_walls()` only mounts on a wall cell belonging to the zone's own
  volume. Leaked anvils/barrels/furnaces against the main wall are eliminated.
- Replaced the gable-triangle 60/40 dark-roof-plank mix with a style-declared
  gable infill: stone styles (`cultivation_sect`, `chinese_courtyard`,
  `cultivation_town`) get a solid `WALL_MAIN` gable with no scattered dark
  planks; `medieval_village` opts into a timber-infill look via a new optional
  `GABLE_INFILL` slot. Each gable cell is tagged with the slot it holds.
- Connection openings are now carved only on real (non-open) walls and clear of
  the parent facade's post/window/door columns (re-sealing any crossed post),
  via a new post-facade `connection_carve_pass`. Chimney placement offsets or
  flips around an abutting `side_wing`/shed instead of force-overwriting its
  facade/structure wall cells. Small wings/sheds now always keep a one-row
  stone plinth (`wall_frame` floors `stone_rows` at 1 for `wall_h >= 3`).

### Added

- Two build-quality hard-error checks that gate export: `open_side_wall`
  (every closed gable-family volume's wall plane must be enclosed from the
  foundation top to its roofline, modulo planned openings) and
  `furniture_on_wall` (no `INTERIOR`/`PROTECTED` non-opening block may sit
  against a different volume's exterior wall). These inspect the actual wall
  plane, not just cells a roof op recorded.
- Optional `GABLE_INFILL` material slot (registered in
  `OPTIONAL_MATERIAL_SLOTS`).

### Changed

- `flat_wall` counting now excludes roof-skin (gable-infill) facade cells so
  the new solid gable is not flagged as a flat run.

### Deferred

- Opposite-wall post-layout sharing and side/back speckle clamping were
  evaluated and dropped: both destabilized byte-stable output without offsetting
  payoff, per the change's §6 "drop if it destabilizes" clause.

## 0.8.1

### Added

- Rebuilt the cultivation `sweeping_eave_roof` silhouette as a real flying-eave
  (飞檐翘角) curve instead of a straight gable with corner bumps. The eave line
  now droops at mid-span and swoops up toward each gable end via a per-column
  corner-lift heightfield, and each eave side runs through a flat eave band
  (举折) before climbing to a level ridge. `tiered_eave_roof` inherits the curve
  on every tier. All geometry stays stair/slab-only; no new mod is required.
- Added a slot-resolved dougong / 额枋 bracket course (`DETAIL_WOOD` `_fence`)
  set under the deep eaves of sweeping-eave roofs; styles without the slot skip
  it, so mortal roofs are unchanged.
- Strengthened `tools/check_cultivation_forms.py` to assert the eave actually
  lifts at the corner and that eave brackets are placed, locking the curve
  against regression.

## 0.8.0-fix5

### Fixed

- Fixed horizontal wall-mounted plaque column placement so north/east-facing
  facades read inscriptions in exterior-view order instead of reversing names
  such as `庄园正门` and `藏经阁`.
- Added generated-structure validation for horizontal wall plaque visual
  column order to catch reversed multipart `col` sequences.

## 0.8.0-fix4

### Fixed

- Changed plaque inscription rendering from per-tile baked calligraphy PNGs to
  one full plaque texture per bound frame/mount/orientation, with each
  multipart block model sampling its own UV window from that full texture.
- Preserved HD inscription resolution in generated full plaque textures
  instead of downsampling calligraphy to 16x16 block parts.
- Updated offline preview rendering and plaque binding validation to understand
  full plaque textures plus model UV windows.

## 0.8.0-fix3

### Fixed

- Fixed generated multipart plaque frame textures so `inner_left` and
  `inner_right` tiles no longer render as outer border columns.
- Refit baked inscription artwork into the full plaque interior instead of
  stretching each source texture across the whole multipart target.
- Retinted low-contrast inscription sources against their plaque surface so
  dark calligraphy remains visible on dark wood and lacquer signboards.
- Extended plaque binding validation to check generated block textures for
  visible-but-not-overfilled inscription pixels.

## 0.8.0-fix2

### Fixed

- Reworked plaque inscriptions from runtime `minecraft:painting` entities into
  block-native baked plaque textures, eliminating the vanilla hanging-entity
  survival path that could break inscriptions into dropped painting items.
- Added distinct multipart plaque row/column states for 4w, 5w, and 4h plaques
  so every tile can display the correct slice of the calligraphy texture.
- Updated generated-structure validation to reject `myvillage:inscription/...`
  painting entities in shipped structures.

## 0.8.0-fix1

### Fixed

- Fixed custom calligraphy paintings rendering as missing black-purple textures
  in game by shipping inscription PNGs under the painting atlas path
  `assets/myvillage/textures/painting/inscription/`.
- Fixed even-height plaque painting anchors so 5w_2h and vertical inscriptions
  no longer shift upward, lose support, and drop as vanilla painting items.
- Updated plaque blocks to provide a full hanging support shape without
  colliding with the painting entity in front of the plaque.

## 0.8.0

### Added

- Added four shipped `myvillage` plaque blocks: wall, vertical wall, hanging,
  and vertical hanging plaque variants.
- Added an eight-preset plaque frame catalog with generated blockstates,
  models, frame textures, and artist-facing asset documentation.
- Added image-based inscription plaques through `painting_variant` resources
  and v1 HD calligraphy textures.
- Added data-driven archetype-to-plaque bindings for shops, inns, taverns,
  manors, sect gates, paifang, scripture pavilions, and treasure pavilions.
- Added hanging-plaque chain integration plus preview support for plaque block
  textures and inscription painting overlays.

### Changed

- Extended generated-structure, style-policy, and fallback validators with a
  `myvillage:` self-namespace carve-out while keeping external-mod validation
  strict.
- Extended offline previews and acceptance prep to report missing inscription
  assets and validate plaque binding data.

## 0.7.1

### Added

- Rebuilt cultivation town and sect building form around raised platforms,
  columned entries, balustrades, dougong-style brackets, mountain gates,
  pagoda massing, and alchemy-room furnace features.
- Added sweeping-eave, hip, pyramidal, and revised tiered-eave roof handlers
  for cultivation styles.

### Changed

- Replaced Western retagged cultivation structures with cultivation-specific
  building graphs and validation rules that reject porch, chimney, and other
  Western domestic tells in cultivation families.
- Extended cultivation validation, preview, and acceptance documentation for
  the rebuilt form vocabulary and sect compound checks.

## 0.7.0-fix2

### Fixed

- Restored Supplementaries awning canopies on market stalls and generated
  fence posts plus solid roof beams behind each awning so attached placement
  survives in game.
- Extended generated-structure validation to reject unsupported
  `supplementaries:awning_*` blocks.

## 0.7.0-fix1

### Fixed

- Fixed optional-mod decor motifs that placed attached blocks without valid
  support, causing sect-gate signs/displays or Supplementaries awnings to drop
  or disappear during in-game structure placement.
- Changed free-standing market stall canopies to use stable `ROOF_TILE`
  stairs/slabs instead of wall-attached awnings, while keeping modded market
  fittings visible under the `full` profile.
- Extended generated-structure validation to reject unsupported wall-attached
  sign/banner blocks.

## 0.7.0

### Added

- Added generated runtime fallbacks for optional decor-mod block ids and routed
  `/myvillage place` plus `/myvillage town` template loading through the
  fallback resolver so absent decor mods degrade to vanilla blocks instead of
  air.
- Added optional NeoForge dependency declarations for Ars Nouveau, Farmer's
  Delight, Supplementaries, Fetzi's Displays, Macaw's Furniture, and Macaw's
  Windows.
- Added fallback-map validation for shipped structure palettes.

## 0.6.0-fix3

### Fixed

- Fixed runtime `/myvillage town` site-fit placement so building templates use
  their `y=1` ground layer against the parcel surface and receive a continuous
  footprint support layer, preventing one-block hollow gaps under houses.
- Extended the runtime town-plan validator to check the shipped templates'
  ground-layer convention used by the Java realizer.

## 0.6.0-fix2

### Fixed

- Fixed runtime `/myvillage town` ground-detail placement so campfires,
  lantern posts, and central street-room furniture are anchored to parcel
  ground, free side-yard cells, or actual street cells instead of roof
  heightmap hits after templates are placed.
- Extended the runtime town-plan validator to check smoke/light detail space
  and street-room furniture candidate cells.

## 0.6.0-fix1

### Fixed

- Fixed the runtime `/myvillage town` plan geometry so all seed-selected lane
  offsets keep parcels, negative-space regions, and placed template footprints
  disjoint from streets.
- Added a runtime town-plan regression validator for the Java layout variants.

## 0.6.0

### Added

- Added `/myvillage town [seed]`, an on-demand runtime living-town generator
  with a closed wall, gates, main-street spine, dominant landmark, terrain
  plinths, active frontage, street furniture, smoke/light, wear, and daily-life
  props.
- Added deterministic town-plan data, validation, budget checks, JSON dumps,
  and top-down PNG/HTML plan previews.
- Added frontage metadata and optional importance-tier hooks to generated
  town building graphs.
- Split `/myvillage gallery` into the full gallery plus
  `/myvillage gallery original` and `/myvillage gallery cultivation`.

### Changed

- Rebound `cultivation_town` to the runtime town-generation layout while
  preserving the existing courtyard-street block outputs as reusable parts.
- Extended cultivation town validation to require resolvable frontage metadata
  on embedded town building parts.

### Documentation

- Updated command, preview, validation, and acceptance docs for the living-town
  flow.

## 0.5.1

### Added

- Added the `cultivation_town` courtyard-street block library:
  `cultivation_town_001.nbt` through `cultivation_town_006.nbt`.
- Added small-courtyard and courtyard-street block generation/validation,
  including street, lane, party-wall, gate-orientation, and traversability
  checks.

### Changed

- Changed the `cultivation_town` settlement group from standalone structures to
  the `courtyard_street_block` layout strategy.
- Updated command and acceptance documentation for
  `/myvillage place cultivation_town_001` and the cultivation town gallery.

### Removed

- Removed the old standalone cultivation town NBT/place-function outputs from
  the default generated resource set.

## 0.5.0

### Added

- Generated civic structures, cultivation town structures, standalone
  cultivation sect structures, and cultivation sect compound structures.
- Grouped gallery support for civic, cultivation town, and cultivation sect
  columns.
