# myvillage - NeoForge Town Structure Validation Mod

This repository builds a NeoForge 1.21.1 mod jar containing generated
`myvillage` structure templates. The current validation target is the Mod
resource layer plus an on-demand runtime town command: structures must be packed
in the jar and placeable in game via debug commands, and `/myvillage town
[seed]` must build a terrain-aware living town in loaded chunks.

The long-term project goal is broader than a simple village generator. The
current structure library is an early resource-validation layer for a future
town-generation system with multiple settlement categories, varied housing
types, functional buildings, roads and town pieces, and possible NPC-related
systems when the data and runtime pipeline are ready.

> **Knowledge base:** start at [docs/ai-kb/INDEX.md](docs/ai-kb/INDEX.md) — it maps the
> `docs/ai-kb/` technical notes and the `openspec/specs/` capability index.

## Resource Path

Use the singular Minecraft/NeoForge structure resource directory:

```text
src/main/resources/data/myvillage/structure/
```

Do not use `src/main/resources/data/myvillage/structures/`.

## Generate One JSON DSL Structure

`test_house_03` is the smoke-test JSON DSL structure.

```bash
python3 tools/validate_structure_json.py examples/test_house_03.json
python3 tools/json_to_nbt.py examples/test_house_03.json src/main/resources/data/myvillage/structure/test_house_03.nbt --mc-version 1.21.1
```

If your shell maps `python` to Python 3, the same commands also work with
`python`.

## Generate All Mod Structures

The canonical batch command generates `test_house_03.nbt`, the
`medieval_village` building library, the default Chinese courtyard compound
library, the civic library, the cultivation town standalone/block libraries,
and the cultivation sect standalone/compound libraries:

```bash
python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure
```

Expected structure output:

```text
src/main/resources/data/myvillage/structure/test_house_03.nbt
src/main/resources/data/myvillage/structure/small_house_001.nbt ... small_house_010.nbt
src/main/resources/data/myvillage/structure/medium_house_001.nbt ... medium_house_010.nbt
src/main/resources/data/myvillage/structure/blacksmith_001.nbt ... blacksmith_010.nbt
src/main/resources/data/myvillage/structure/small_shop_001.nbt ... small_shop_005.nbt
src/main/resources/data/myvillage/structure/medium_shop_001.nbt ... medium_shop_005.nbt
src/main/resources/data/myvillage/structure/big_house_001.nbt ... big_house_005.nbt
src/main/resources/data/myvillage/structure/main_hall_review.nbt
src/main/resources/data/myvillage/structure/side_wing_review.nbt
src/main/resources/data/myvillage/structure/front_row_review.nbt
src/main/resources/data/myvillage/structure/chinese_courtyard_001.nbt ... chinese_courtyard_006.nbt
src/main/resources/data/myvillage/structure/tavern_001.nbt ... tavern_005.nbt
src/main/resources/data/myvillage/structure/lord_manor_001.nbt ... lord_manor_003.nbt
src/main/resources/data/myvillage/structure/cultivation_house_001.nbt ... town_shrine_003.nbt
src/main/resources/data/myvillage/structure/cultivation_town_001.nbt ... cultivation_town_006.nbt
src/main/resources/data/myvillage/structure/sect_gate_001.nbt ... sect_gate_002.nbt
src/main/resources/data/myvillage/structure/sect_main_hall_001.nbt ... sect_main_hall_002.nbt
src/main/resources/data/myvillage/structure/scripture_pavilion_001.nbt ... scripture_pavilion_002.nbt
src/main/resources/data/myvillage/structure/alchemy_room_001.nbt ... alchemy_room_002.nbt
src/main/resources/data/myvillage/structure/disciple_quarters_001.nbt ... disciple_quarters_002.nbt
src/main/resources/data/myvillage/structure/cultivation_sect_001.nbt ... cultivation_sect_002.nbt
src/main/resources/data/myvillage/settlement_meta/cultivation_sect_001.json ... cultivation_sect_002.json
```

The Gradle build also runs this batch generator before packing resources, so
v0.8 jars are expected to contain individual buildings, compound structures,
civic/cultivation structures, plaque blocks, and inscription assets used by the
runtime town command.

The current generator data is populated for the full external-mod profile.
When those staged mods are installed, generated market stalls, sect-gate decor,
ritual anchors, lighting, furniture, and canopy/eave details may use confirmed
ids from Ars Nouveau, Farmer's Delight, Fetzi's Displays, Macaw's Furniture,
Macaw's Windows, and Supplementaries. The Python style loader still supports a
vanilla namespace profile for regression checks; every populated slot keeps a
trailing `minecraft:` fallback.
The batch generator also exports
`src/main/resources/data/myvillage/mod_block_fallbacks.json` from those slot
lists. Runtime `/myvillage place` and `/myvillage town` template placement use
that map so worlds without the optional decor mods place vanilla fallbacks
rather than air holes.

## Generate Chinese Courtyard Compounds

Generate the Chinese courtyard review sub-buildings plus the default compound
library:

```bash
python3 tools/generate_compound_library.py --count 6
python3 tools/validate_compound_library.py --count 6
```

Expected compound output:

```text
src/main/resources/data/myvillage/structure/main_hall_review.nbt
src/main/resources/data/myvillage/structure/side_wing_review.nbt
src/main/resources/data/myvillage/structure/front_row_review.nbt
src/main/resources/data/myvillage/structure/chinese_courtyard_001.nbt ... chinese_courtyard_006.nbt
src/main/resources/data/myvillage/function/gallery/chinese_courtyard.mcfunction
src/main/resources/data/myvillage/function/place/chinese_courtyard_001.mcfunction ... chinese_courtyard_006.mcfunction
```

The compound exporter currently uses single structure NBT files. The one-court
Chinese compounds stay compact; cultivation town blocks and mountain sect
compounds are larger review structures and are spaced by the generated gallery
functions and `/myvillage gallery` command.

## Generate Cultivation Libraries

Generate the mortal town building/block group and immortal sect group directly:

```bash
python3 tools/generate_building_library.py --group cultivation_town --count 3 --base-seed 20260613
python3 tools/generate_compound_library.py --group cultivation_town --count 6 --base-seed 20260617
python3 tools/generate_building_library.py --group cultivation_sect --count 2
python3 tools/generate_compound_library.py --group cultivation_sect --count 2 --base-seed 20260616
```

These commands write group-specific reports under `reports/`:

```text
reports/cultivation_town_building_library_report.json
reports/cultivation_town_compound_library_report.json
reports/cultivation_sect_building_library_report.json
reports/cultivation_sect_compound_library_report.json
```

Sect compound generation also writes placement metadata sidecars under
`src/main/resources/data/myvillage/settlement_meta/`, including siting context,
relative terrace levels, hierarchy, and gallery/bridge link endpoints.

## Validate Generated NBT

Run the NBT-level integrity checks after generation:

```bash
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_mod_block_fallbacks.py
python3 tools/validate_plaque_bindings.py
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_compound_library.py --group cultivation_town --count 6
python3 tools/validate_compound_library.py --group cultivation_sect --count 2
python3 tools/validate_civic_library.py
python3 tools/validate_town_generation.py
python3 tools/validate_runtime_town_plan.py
python3 tools/check_style_policy.py
python3 tools/check_cultivation_forms.py
python3 tools/validate_region_topology.py
```

The validator checks that files exist, palettes and blocks are non-empty, roof
blocks exist, the top layers are not empty, key stairs/slabs/logs/planks are
present, gable closure is heuristically checked, and building interiors contain
the expected function blocks. Blacksmiths must contain forge-equivalent blocks;
houses must contain crafting/furnace/barrel-style utility blocks; civic
structures must contain tavern or lord-manor signature role blocks; cultivation
town and sect structures must contain their expected town/sect signatures.
`myvillage:` plaque block ids are accepted as shipped self-namespace resources
under both `full` and `vanilla` validation profiles, while unrelated external
mod ids remain profile-gated. Plaque-bearing archetypes additionally require
plaque blocks whose block textures have the bound inscription baked in, and
`validate_plaque_bindings.py` checks that each binding points at an existing
frame preset and inscription asset. Generated structures must not contain
`myvillage:inscription/...` painting entities; inscription paintings are not
used at runtime because they can fail vanilla hanging-entity survival checks
and drop as painting items.

## Region Topology (Offline Layer)

The region (洲/域) layer is the macro geography the mod was missing — a
per-seed region graph of 5–7 洲 with a single 中州 `anchor` at the center,
rule-governed 连 (passable) / 隔 (separated) relations, a tier gradient, and a
sealed 魔域-style `walled` region. It is the top of the OTG stack
(WorldConfig region rules + a constrained-random FromImage-like geography),
delivered **offline-first**: data drives generation and validation before any
runtime chunk-gen. The authored catalog and ruleset ship as JSON under
`src/main/resources/data/myvillage/worldgen/`; a canonical example graph ships
at `worldgen/region_topology_example.json`. See
[`docs/ai-kb/13_region_topology.md`](docs/ai-kb/13_region_topology.md).

```bash
# Emit the region graph for a seed as JSON (constructive, seed-deterministic):
python3 tools/generate_region_topology.py 20260620
python3 tools/generate_region_topology.py 20260620 --out reports/rt.json
python3 tools/generate_region_topology.py 20260620 --check-determinism

# Validate structural invariants + determinism + deliberate breaks, and write
# the multi-seed survey to reports/region_topology_validation.json:
python3 tools/validate_region_topology.py

# Render per-seed SVG + ASCII previews under out/preview/region_topology_s*/:
python3 tools/generate_region_topology_preview.py --count 6
```

The graph lists regions (tier/role/position) and a typed edge list: `连` edges
are passable; `隔` edges carry a separator (`特殊山脉` or `特殊海洋`); a
`walled` region's single retained `连` edge is marked `关隘`. The offline
generator remains the single source of truth and writes no world blocks. As of
`add-region-runtime-binding`, a **runtime companion** places the per-seed graph
into the world (中州 at the world origin, all 洲 within a ~4000-block radius),
binds world spawn deterministically to the lowest-tier eligible region, and
exposes a `region_at` / `current_rung` / `next_rung_regions` query API for
downstream consumers (compass / map / alignment / mobility — all still
deferred). The runtime is passive: it reads the world seed and answers queries;
it overrides no biome, hooks no chunk-gen, and writes nothing beyond the
one-time `setDefaultSpawnPos`. See `/myvillage spawn info|recompute` below and
[`docs/ai-kb/13_region_topology.md`](docs/ai-kb/13_region_topology.md). Turning
the typed edges into actual relief (山脉/海洋 ranges) and placing subjects into
regions remains the deferred next change.

## Preview Structures Offline

Render structures to offline PNG and HTML previews without launching the game,
to eyeball layout, massing, roof form, and fenestration before doing an in-game
`/place template` pass. This is primarily a coarse voxel-color preview; plaque
block textures are resolved from their shipped models so baked inscriptions can
be checked before an in-game pass. Other blockstate detail such as door facing
or trapdoor open/close still needs an in-game check.

```bash
python3 tools/preview_structure.py src/main/resources/data/myvillage/structure/small_house_001.nbt
python3 tools/preview_structure.py examples/buildings/small_house_01.json   # DSL source form
python3 tools/preview_structure.py --all                                    # every .nbt
python3 tools/generate_town_plan_preview.py --count 6                       # town plan PNG/HTML previews (default covers all 6 wall families)
python3 tools/preview_structure.py --viewer-only src/main/resources/data/myvillage/structure/cultivation_sect_001.nbt
python3 tools/preview_structure.py --no-viewer --all                        # PNGs only
python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview           # serve public previews for review
```

Outputs land in `out/preview/<stem>/`: `isometric.png` (shaded 3D overview),
`slices_contact.png` plus per-Y `slice_yNN.png` (top-down floor plans), and
`legend.png` / `legend.txt` mapping swatch indices to block ids. The generated
`viewer.html` opens directly from disk and supports orbit/zoom/pan, X/Y/Z
cross-section cuts, Y-layer range sliders, and block-base checkboxes. When a
run emits more than one `viewer.html`, the tool also writes
`out/preview/index.html` as the reviewer entry point, with browser assets copied
under `out/preview/_assets/` so the directory is self-contained for HTTP review.
For acceptance handoff, serve `out/preview/` with a public HTTP server bound to
`0.0.0.0:8765` and report `http://43.156.135.198:8765/index.html` while this
host keeps that public IP, so review starts from an opened preview surface
instead of a file list. Keep the preview server running until the reviewer says
it can be closed, or until the related OpenSpec change is being archived. Add
new block colors to `tools/block_colors.json`; unknown blocks render magenta and
should be reported there. `--max-px` (default 2048)
auto-reduces static PNG scale so large compounds stay bounded.

## Build The Mod

Requires JDK 21.

```bash
./gradlew build
```

Confirm the jar contains the structure resources:

```bash
jar tf build/libs/*.jar | grep "data/myvillage/structure"
jar tf build/libs/*.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/*.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/*.jar | grep "assets/myvillage/textures/painting/inscription"
```

The expected jar is:

```text
build/libs/myvillage-0.15.0.jar
```

## Versioning And Changelog

Maintain `CHANGELOG.md` whenever a version is prepared or a validated fix is
accepted. Version updates must be applied consistently in `gradle.properties`,
`src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and
the changelog.

Version numbers use the current `0.x.y` line:

```text
large feature addition: 0.x.y -> 0.(x+1).0
small feature addition: 0.x.y -> 0.x.(y+1)
single validated fix:  0.x.y -> 0.x.y-fix1, then fix2, fix3, ...
```

A `fixN` suffix should only be added after the relevant build or validation
step passes.

## Manual Acceptance Prep

Before a staged manual acceptance pass, prepare both the mod artifact and the
command documentation:

```bash
python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_mod_block_fallbacks.py
python3 tools/validate_plaque_bindings.py
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_compound_library.py --group cultivation_town --count 6
python3 tools/validate_compound_library.py --group cultivation_sect --count 2
python3 tools/validate_civic_library.py
python3 tools/validate_town_generation.py
python3 tools/validate_runtime_town_plan.py
python3 tools/check_style_policy.py
python3 tools/check_cultivation_forms.py
python3 tools/validate_region_topology.py
python3 tools/preview_structure.py --all
python3 tools/generate_town_plan_preview.py --count 6    # default covers all 6 wall families
python3 tools/generate_sect_plan_preview.py --count 6    # default covers all 3 detached-spire variants + absent
python3 tools/generate_region_topology_preview.py --count 6   # offline 洲/域 graph previews
python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview
./gradlew build
jar tf build/libs/myvillage-0.15.0.jar | grep "data/myvillage/structure"
jar tf build/libs/myvillage-0.15.0.jar | grep "data/myvillage/mod_block_fallbacks.json"
jar tf build/libs/myvillage-0.15.0.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/myvillage-0.15.0.jar | grep "assets/myvillage/textures/block/plaque"
jar tf build/libs/myvillage-0.15.0.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/myvillage-0.15.0.jar | grep "assets/myvillage/textures/painting/inscription"
```

Use the command list below as the acceptance script. Update this README,
`AGENTS.md`, and the relevant OpenSpec specs whenever commands or acceptance
prep steps change.

## Run Client

```bash
./gradlew runClient
```

Create or open a flat test world with commands enabled.

## Available Commands

The v0.11 mod registers debug commands for structure validation, the
on-demand living-town generator, and the terraced sect-compound generator, plus
a custom worldgen `myvillage:sect` structure that sites cultivation sects into
the world on their own (rare, biome-gated to high-relief biomes, world-seed
reproducible) and is locatable via `/locate structure myvillage:sect`. Town
worldgen is still not registered (a later change).

List loaded templates:

```mcfunction
/myvillage list
```

Generate a living cultivation town around the player:

```mcfunction
/myvillage town
/myvillage town 20260618
```

The optional seed makes generation deterministic for the same seed and site.
It selects both the perimeter silhouette (square, 天圆 circle, oval, 半月
D-shape, octagon, or trapezoid, optionally with a barbican/bastion) and a
bounded orthogonal internal grid. Family review seeds include `4` (square), `5`
(circle), `2` (oval), `1` (D-shape), `11` (octagon), and `13` (trapezoid).
The town is a districted ~160×160 修仙坊市 (gate / market / residential / civic
core / fringe districts), force-loaded via chunk tickets so the whole footprint
generates in one command; regions that cannot be force-loaded are reported
rather than silently skipped. The civic core carries the ritual axis (plaza /
paifang gate / lantern approach) and a skyline of vertical landmarks
(`pagoda`, `pavilion`, `bell_drum_tower`) flanking the dominant `town_shrine`.
Market and residential parcels form continuous street-frontage row shops with
party walls and narrow alleys. Cultivation street life (幌子 banners,
药圃/灵田 spirit-field beds, 炼丹炉 alchemy furnaces, 法器摊 artifact stalls,
阵纹 formation floors) and villager/spirit-fox inhabitants are placed across
districts. Parcels above the slope limit are skipped and reported in the
completion message. If the optional decor mods are absent, authored mod blocks
in template palettes and runtime decor fixtures are substituted with generated
vanilla fallbacks.

Generate a terraced cultivation sect compound ascending away from the player:

```mcfunction
/myvillage sect
/myvillage sect 20260618
```

Force-generate a worldgen-style sect (with its derived mountain) at the player,
for review/testing, optionally selecting the detached-spire variant:

```mcfunction
/myvillage sect worldgen
/myvillage sect worldgen 20260618
/myvillage sect worldgen 20260618 pavilion_short_straight_east
```

`worldgen` builds the same compound resting on a mountain **derived from the
terrace profile** (反推山形): the terraces are fixed first, then the slopes
beneath/between them are noise-filled, an outer blend skirt grades the man-made
relief into the surrounding terrain (no cut-off edge), a sheer cliff face backs
the summit, a translucent cloud-sea (云海面) sheet floats between the gate and
disciple terraces, and — when the feature is present — a solitary peak (孤峰)
rises under the detached volume reachable only across the flying bridge. The
optional third argument forces one of the three detached-spire variants
(`pavilion_short_straight_east`, `pagoda_long_arched_west`,
`disciple_medium_angled_north`) or `none`; omitting it falls back to the
per-seed selection. In natural worldgen the same structure is sited
automatically and bakes into chunks (no force-load, no build pop-in); find one
with `/locate structure myvillage:sect`.

The sect is a terraced axial 宗门 compound (gate / disciple / assembly /
scripture / summit terraces, count parametric 4–6) ascending a single fall-line
ritual axis from the mountain gate (山门) to the cliff-backed principal hall
(主殿). Slot importance grades with terrace level — the principal hall and
scripture pagoda hold the top tiers; flanking volumes (disciple-quarter rows,
paired pagodas, flanking bell/drum towers) mirror about the axis and are joined
by covered galleries (廊); each terrace meets the next through a retaining face
and an on-axis stair flight. The optional detached-spire flying-bridge (飞桥)
feature is selected per seed (one of three deterministic variants, or absent):
a detached volume sits on its outcrop reachable only by the flying bridge. The
footprint is force-loaded via chunk tickets; terraces are carved and retained
against the terrain so platforms step the slope with no floating or buried
slabs, and palette ids route through the mod-fallback resolver. The same seed
rebuilds the same compound. The exported terrace profile (elevations/bounds,
rise/depth/taper, axis-stair width, cliff-back height) is the contract the sect
worldgen consumes to derive the mountain (反推山形); the on-the-spot
`/myvillage sect [seed]` build is unchanged (it rests on the live surface, no
derived mountain).

Place the smoke-test structure at the player position:

```mcfunction
/myvillage place test_house_03
```

Place a generated building directly:

```mcfunction
/myvillage place small_house_001
/myvillage place medium_house_001
/myvillage place blacksmith_001
/myvillage place medium_shop_001
/myvillage place big_house_001
/myvillage place chinese_courtyard_001
/myvillage place tavern_001
/myvillage place lord_manor_001
/myvillage place cultivation_town_001   # courtyard district-fill fragment (not the canonical town — use /myvillage town)
/myvillage place cultivation_inn_001
/myvillage place pagoda_001
/myvillage place pavilion_001
/myvillage place bell_drum_tower_001
/myvillage place sect_gate_001
/myvillage place scripture_pavilion_001
/myvillage place cultivation_sect_001
```

Plaque-bearing generated structures place shipped `myvillage` plaque blocks with
the bound inscription baked directly into the block textures. Notable review targets include
`tavern_001`, `lord_manor_001`, `cultivation_inn_001`, `pagoda_001`, `pavilion_001`,
`bell_drum_tower_001`, `sect_gate_001`, and `scripture_pavilion_001`.

For generated structures other than `test_*`, `/myvillage place` applies a
one-block downward Y offset before placement. This lets terrain-replacement
cells such as courtyard water, gravel paths, and entry hardscape replace the
ground block instead of sitting one block above it. If using vanilla commands
directly, place generated structures with the same offset:

```mcfunction
/place template myvillage:small_house_001 ~ ~-1 ~
/place template myvillage:chinese_courtyard_001 ~ ~-1 ~
/place template myvillage:tavern_001 ~ ~-1 ~
/place template myvillage:lord_manor_001 ~ ~-1 ~
/place template myvillage:cultivation_town_001 ~ ~-1 ~
/place template myvillage:cultivation_sect_001 ~ ~-1 ~
```

When `/myvillage place` substitutes optional-mod palette entries because a decor
mod is not loaded, the success line includes `fallback_substitutions=<count>`.

Place all loaded `myvillage` blueprints in a grouped gallery with 128-block
spacing:

```mcfunction
/myvillage gallery
```

Place only the original non-cultivation structures, or only the cultivation
structures, in the same grouped gallery layout:

```mcfunction
/myvillage gallery original
/myvillage gallery cultivation
```

The full gallery is arranged as columns by broad type: houses, shops,
blacksmiths, Chinese courtyard compounds, civic structures, cultivation town,
cultivation sect, Chinese review sub-buildings, tests, and other templates.
The `original` gallery keeps the non-cultivation columns, while the
`cultivation` gallery keeps the cultivation town and cultivation sect columns.
It is intended for side-by-side visual comparison across sizes and archetypes.

Generated datapack functions are also available after resource generation:

```mcfunction
/function myvillage:gallery/medieval_village
/function myvillage:gallery/chinese_courtyard
/function myvillage:gallery/civic
/function myvillage:gallery/cultivation_town
/function myvillage:gallery/cultivation_sect
/function myvillage:place/chinese_courtyard_001
/function myvillage:place/tavern_001
/function myvillage:place/lord_manor_001
/function myvillage:place/cultivation_town_001
/function myvillage:place/cultivation_sect_001
```

Query the region runtime (passive — reads the per-seed region graph, overrides
no biome, writes nothing beyond the one-time world spawn):

```mcfunction
/myvillage spawn info
/myvillage spawn recompute
```

`/myvillage spawn info` (player-only) prints the computed spawn region and
bound spawn block, plus the caller's current region / tier rung / next-rung
region set. `/myvillage spawn recompute` (admin, permission 2) forces a spawn
recompute for the current world and calls `setDefaultSpawnPos`, **overriding
any existing spawn** — the documented admin-override path. The automatic
world-load binding otherwise runs once per world and preserves any existing
custom (admin-set) spawn rather than clobbering it.

## Generation Architecture

```text
Settlement Group     tools/buildgen/groups.py       style profile + archetype roster + layout strategy
Style Profile        tools/buildgen/styles/*.json   medieval_village / chinese_courtyard / cultivation_town / cultivation_sect
Building Archetype   tools/buildgen/archetypes.py   small_house / medium_house / blacksmith / shops / civic / cultivation town / cultivation sect
Massing Graph        tools/buildgen/massing.py      main + great hall + tower + wing + porch + chimney + shed nodes
Facade Grammar       tools/buildgen/facade.py       bay split, posts, jittered windows
Build Ops            tools/buildgen/ops.py          wall ops + registered roof/motif handlers
Pass + Protection    tools/buildgen/passes.py       ordered passes, mezzanine_floor_pass, tag/priority grid, PROTECTED cells
Quality Check        tools/buildgen/quality.py      entrance, windows, interior, mezzanine, belfry, gables, forms, forbidden blocks
Resource Export      tools/buildgen/export.py       structure NBT + gallery/place mcfunctions
Compound Graph       tools/buildgen/compound.py     Chinese one-courtyard, cultivation town block, and cultivation sect parcel layouts
Civic Generator      tools/generate_civic_library.py tavern_001..005 + lord_manor_001..003
Town Planner         tools/buildgen/town.py         deterministic enclosure/spine/parcels/negative-space model + validation
Town Realizer        src/main/java/.../town/        /myvillage town runtime site-fit, frontage, street-room, and tissue placement
```

Important properties:

- Materials resolve through abstract slots such as `BASE_STONE`, `WALL_MAIN`,
  `FRAME_WOOD`, `ROOF_DARK`, `DETAIL_WOOD`, `INTERIOR_WORK`,
  `INTERIOR_STORAGE`, `INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, and
  `HERALDRY`. External-mod decor uses semantic slots such as `ROOF_TILE`,
  `PAPER_LANTERN`, `RITUAL_ANCHOR`, and `MARKET_FITTINGS`; cultivation form
  geometry uses `COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, and `BALUSTRADE`.
  Each populated list keeps a final `minecraft:` fallback. Cultivation sect
  styles may also define `SPIRIT_CRYSTAL` and `RITUAL_METAL`; mortal styles may
  omit them.
- Families with non-vanilla blockstate grammar are oriented through the
  buildgen orientation adapter. Vanilla stairs/slabs and Supplementaries
  awnings are registered families; unregistered families fail loudly.
- Supported roof handlers are registered in `tools/buildgen/ops.py`. Current
  names include `gable_roof`, `cross_gable_roof`, `lean_to_roof`, Chinese
  roof-grade aliases, `sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, and
  `tiered_eave_roof`.
- Cultivation eave curvature is a real flying-eave (飞檐翘角) silhouette built
  from stair/slab geometry: the eave line droops at mid-span and swoops up
  toward each gable end, each eave side runs through a flat eave band (举折)
  before climbing to a level ridge, and the corners carry an upturned finial
  with an outward wing. A slot-resolved dougong/额枋 bracket course (`DETAIL_WOOD`
  `_fence`) sits under the deep overhangs. It does not require an Asian-decor
  curved-roof mod; optional mod blocks only skin the slot-resolved materials.
- Decoration motifs are also registered in `tools/buildgen/ops.py`; cultivation
  forms include `moon_gate`, `spirit_array`, `incense_altar`, `cloud_rail`, and
  `sect_gate_paifang`. Market styles may also enable `market_stall`.
- Add new settlement families through `tools/buildgen/groups.py`, and add new
  roof or motif forms by registering a handler before listing the form in a
  style's `allowed_roof_types` or `allowed_motifs`.
- The default medieval building-library archetypes are `small_house`,
  `medium_house`, `blacksmith`, `small_shop`, `medium_shop`, and `big_house`.
  Civic archetypes `tavern` and `lord_manor` are generated by the separate
  civic library loop.
- Chinese compound generation uses `tools/buildgen/styles/chinese_courtyard.json`
  and the `main_hall`, `side_wing`, `front_row`, and `gate_house` sub-building
  builders. These are composed by `CompoundGraph`, not emitted by the default
  medieval building-library generator.
- Cultivation town generation uses `cultivation_town.json` with the runtime
  town-generation layout. The runtime `/myvillage town` realizer produces a
  districted ~160×160 修仙坊市: named districts (坊门区 gate / 市肆区 market /
  民居坊 residential / 礼制核心 civic core / 边缘区 fringe), each carrying its
  own density, storey band, and material register from the group's `district_brief`.
  The ritual axis (plaza / paifang gate / lantern approach) is expressed inside
  the civic core, with the `town_shrine` as the sole dominant landmark. Market
  and residential parcels carry street frontage (party-wall row shops, shared
  gables, intentional narrow alleys) instead of centered-in-lot plinths. A
  skyline rule guarantees the civic core rises above the surrounding roofline
  through vertical-landmark archetypes — `pagoda` (塔), `pavilion` (楼阁), and
  `bell_drum_tower` (钟鼓楼) — built from the existing terrace + flying-eave
  vocabulary and placed flanking the shrine. The footprint is force-loaded via
  chunk tickets so the whole town generates in one command.
- The static `cultivation_town_NNN` compound library is **district fill tissue**,
  not a standalone town: it supplies courtyard street-block material the
  residential and market districts draw from. `/myvillage place cultivation_town_001`
  remains available for placing a single courtyard fragment for review, but it is
  no longer the canonical cultivation town — `/myvillage town` is. Standalone
  cultivation-town buildings (`cultivation_house`, shops, inn, market,
  `town_shrine`, and the `pagoda`/`pavilion`/`bell_drum_tower` landmarks) are also
  generated because the runtime town places those templates directly. Cultivation
  town and sect buildings use cultivation massing grammar directly: raised
  platforms, entry colonnades with dougong brackets, sweeping/hip/pyramidal/tiered
  roofs, pagoda finial spires, belfry bells, pavilion balconies, a built three-bay
  mountain gate, and a furnace feature for alchemy rooms. Cultivation sect
  generation uses `cultivation_sect.json` with standalone sect archetypes plus a
  terraced axial mountain-compound layout: four stacked terrace courtyards,
  monumental stairways, summit hall/pagoda hierarchy, a water/cliff/cloud siting
  context, and structural covered-gallery/flying-bridge link nodes.
- Town building graphs expose frontage metadata (`side`, `facing`, and opening
  cells) and optional importance-tier hints used by the town planner/realizer.
- Chinese courtyard water and gravel/path cells are authored as
  terrain-replacement cells one layer below the structure origin. Planting stays
  on the plant layer, and bamboo is sampled around water with supporting dirt
  where needed.
- Multi-story buildings use aligned floor openings and stairwell metadata.
  `mezzanine_floor_pass`, `floor_slab_pass`, and `stair_pass` run after
  `structure_pass`.
- Generated building entry hardscape is lowered to the stair's lower layer so
  random path blocks do not sit flush with the doorway stair. Porch posts extend
  down to the lowered hardscape layer.
- `clear_inside` runs before roof generation and only carves the interior wall
  volume. Roofs and gable cells are generated later and protected by the pass
  pipeline.
- Current `myvillage` and `medieval_village` names are implementation labels,
  not the final scope of the project. Future work is expected to expand from
  the current building library toward richer town generation.

## Current Scope

Included:

```text
- JSON DSL validation and JSON -> vanilla structure NBT conversion
- Batch generation into NeoForge Mod resources
- 45 medieval_village building-library structures
- 6 generated Chinese courtyard compound structures
- 8 generated civic structures (`tavern_001..005`, `lord_manor_001..003`)
- 6 generated cultivation town block structures (district fill tissue)
- 24 generated standalone cultivation town structures (incl. `pagoda`/`pavilion`/`bell_drum_tower` landmarks)
- 10 generated standalone cultivation sect structures
- 2 generated cultivation sect compound structures
- 105 generated NBT structures in the default batch, including `test_house_03.nbt`
- sect compound placement metadata under `data/myvillage/settlement_meta/`
- test_house_03.nbt Mod resource smoke test
- /myvillage place <structure_id>
- /myvillage list
- /myvillage town [seed]
- /myvillage sect [seed]
- /myvillage sect worldgen [seed] [variant]
- /myvillage gallery
- /myvillage gallery original
- /myvillage gallery cultivation
- a custom `myvillage:sect` worldgen Structure: sects are sited during world generation, biome-gated by `tags/worldgen/biome/has_sect`, spaced by `worldgen/structure_set/sect`, and `/locate`-able, resting on a mountain derived from the terrace profile (反推山形)
- generated optional-mod runtime fallback map and fallback coverage validation
- NBT integrity validation for roof/top-layer/function-block/signature checks
- deterministic town-plan and sect-plan/sect-generation validation with top-down previews
```

Not included:

```text
- passive/natural *town* worldgen (towns remain command-built via `/myvillage town`; only sects are sited during world generation)
- jigsaw / template-pool generation (the sect structure is a hand-written `Structure`, not a jigsaw template pool)
- loot tables or complex authored block-entity NBT
```

Future direction:

```text
- multiple town/settlement categories rather than one simple village type
- more house types across sizes, styles, and roles
- functional buildings such as shops, workshops, storage, markets, services, and more civic pieces
- roads, props, districts, and layout rules for coherent town generation
- possible NPC/villager-related behavior once runtime and data support exist
```

## Known Issues And Visual Review Notes

The `mc-modtest-codex` candidate branch previously produced visible empty-roof
or roof-hole results. During integration, do not blindly reuse that branch's
roof generation logic. Pay special attention to:

```text
- whether roof layers contain non-air blocks
- whether gable ends are visually sealed
- whether clear_inside accidentally removes roof or gable material
- whether high layers in each structure are empty
- whether stairs and slabs face the correct direction
- whether gallery dimensions make roof defects easier to compare
```

The automated validators check the mechanical parts of this list, but final
acceptance still needs a v0.9 mod jar plus in-game visual inspection with
`/myvillage list`, `/myvillage town 20260618`,
`/myvillage place chinese_courtyard_001`,
`/myvillage place tavern_001`, `/myvillage place lord_manor_001`,
`/myvillage place cultivation_town_001` (courtyard fragment),
`/myvillage place cultivation_inn_001`,
`/myvillage place pagoda_001`, `/myvillage place pavilion_001`,
`/myvillage place bell_drum_tower_001`,
`/myvillage place sect_gate_001`,
`/myvillage place scripture_pavilion_001`,
`/myvillage place cultivation_sect_001`, `/myvillage gallery`,
`/myvillage gallery original`, and `/myvillage gallery cultivation`.
