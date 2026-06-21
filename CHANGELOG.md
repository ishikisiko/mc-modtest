# Changelog

All notable project changes should be recorded here when a version is prepared.

## Versioning Rules

The authoritative version-bump rule (increments and the files that must move
together) lives in `openspec/config.yaml` (`rules.tasks`). Follow it there.

## Unreleased

## 0.15.0

### Changed

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
