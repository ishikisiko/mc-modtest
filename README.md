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
> **CRAFT orchestration:** start at [CRAFT.md](CRAFT.md) for the Commander,
> GenOps pipelines, project Codex subagents, evidence, and review gates.

## GenOps Orchestration

Generator work is routed through a Commander Agent conversation. The project
owner describes intent in natural language; the Commander chooses the pipeline,
runs the local manager tools, and reports `goal_status`,
`scope_or_direction`, `validation_state`, `risk_or_blocker`,
`human_decision_needed`, and `next_decision`. Run ids, pipelines, task ids,
worker ownership, artifacts, gates, raw logs, and manifest paths are available
for audit, but they are not the normal user interface.

CRAFT-required work now has to enter through GenOps before protected artifacts
are edited. That includes explicit CRAFT/GenOps requests, OpenSpec proposal or
apply work, visual/aesthetic structure changes, subagent or parallel work,
release/version/build handoff, and acceptance handoff. Trivial read-only checks
remain direct.

Example owner messages:

```text
用 GenOps 规划一下宗门远景剪影怎么改，先别动代码。
继续上次工作，把已确认的实现方向做完。
跑完整回归并准备人工视觉验收。
```

The Commander uses `tools/genops/run_pipeline.py` when useful; no distributed
service is added. Runs still write task contracts, prompts, patch-guard reports,
gate evidence, structured aesthetic reviews when applicable, an embedded
front-door check result, and a final manifest under `reports/agent_runs/<run_id>/`. See
[`docs/ai-kb/19_genops.md`](docs/ai-kb/19_genops.md) and
[`openspec/specs/genops/spec.md`](openspec/specs/genops/spec.md).

OpenSpec proposal/design/spec/task authoring uses
`genops/pipelines/openspec-change.full.yaml`. Protected-path provenance can be
checked with `tools/genops/check_frontdoor.py`; `run_pipeline.py` runs the same
check for run-owned protected artifacts before reporting a green final status.
Protected categories distinguish Java runtime, client resources, data resources,
generated NBT, release metadata, generator code, GenOps, docs, and OpenSpec
paths instead of using a broad `src/main/**` bucket. Those backend details are
Commander-owned unless audit detail is requested or a backend failure blocks a
decision.

Pipeline YAML governance is checked by `tools/genops/validate_pipelines.py`.
That validator turns role/scope/review/gate/release-output mistakes into a
non-zero compile step instead of soft convention drift.

The Commander backend is stateful: `tools/genops/commander.py` supports
`classify`, `start-run`, `continue-current`, `status`, `next-decision`,
`record-verdict`, `closeout`, and `summary`, backed by the rebuildable
`.genops/state.sqlite` index. Stop conditions are evaluated in code before the
state machine advances. Rebuild derives verdict state from mirrored decision
artifacts, and `closeout-ready` requires closeout evidence, front-door pass,
validation pass, and an OK verdict.

Mod item creation is also CRAFT-routed. The repo-local
`.codex/skills/mod-item-creation` skill creates the Item Contract and routes
work through `genops/pipelines/mod-item.full.yaml`, separating Java registration,
resources, visual review, validation, docs, and regression evidence.

GenOps worker roles are also registered as project-scoped Codex custom
subagents under `.codex/agents/` (for example
`genops-generator-engineer`, `genops-validator-engineer`, and
`genops-visual-reviewer`). They are spawned only when the owner explicitly asks
for subagents or parallel agent work.

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
python3 tools/generate_compound_library.py --count 6 --profile vanilla
python3 tools/validate_compound_library.py --count 6 --profile vanilla
python3 tools/generate_compound_library.py --group chinese_huipai_mansion --count 2 --base-seed 20260619
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/generate_compound_library.py --group ganlan_stilted_house --count 2 --base-seed 20260708
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
```

The six `chinese_courtyard_NNN` templates are rebuilt 一进 compounds: an outer
yard with 影壁, one 垂花门 into the raised main yard, returning 抄手游廊, and a
正房 fronted by 月台. Their filenames and `/myvillage place` commands are
unchanged, but v0.15.0 regenerates their footprints, silhouettes, and interiors.
v0.15.0-fix1 rebuilds the courtyard **ground + path layer** (`courtyard-ground-layer`
/ `courtyard-path-network` specs): the yard floor is now solid (露天 grass /
屋檐下 stone_bricks) and a multi-source-BFS path connects every door, water
feature, planting bed, and the moon platform, with a single stairs block at the
plinth edge — the courtyards are walkable end-to-end.
v0.16.0 ships the **3-进 江南大宅 (`chinese_mansion`)** family: six new NBTs
`chinese_mansion_001..006.nbt`, a `garden_rockery` / `garden_pond` / 亭 garden
layer, the `open_hall` and `tower_house` archetypes, the `myvillage:rockery_block`
self-namespace block, and the voxel-walkability 3D BFS validator for all compound
families. Also regenerates `chinese_courtyard_001..006.nbt` with 照壁侧立
off-axis screen wall and ≥3-cell 垂花门 passage.
v0.16.2 re-sculpts the mansion garden's named hero 假山: a layered, 收分 太湖石
(stone-dominant with moss accents, per `docs/mt.png`) procedurally authored by
`tools/buildgen/gen_hero_rockery_sculpt.py`, with a spring that issues from a
grotto inside the rock and cascades down the terraces into a pool embedded in the
foot. It also fixes the v0.16.1 flood: `waterlogged` rock (a spreading water
source) is dropped entirely — visible water is a contained pool + non-fluid
`rockery_cascade` only. Review it directly with `/myvillage place hero_rockery`;
preview the 48³ sculpt offline with `tools/buildgen/preview_voxel_field.py`.
v0.16.2-fix1 keeps the summit tree at the same 1/16 scale as the rockery: it is
now a baked leaning bonsai with visible branches and layered foliage instead of
ordinary full blocks. The spring's micro-water is also baked from one connected
grotto-to-pool path that follows the terraced rock face; the fixed exterior
`rockery_cascade` column is no longer placed. Real water remains sealed in the
山脚 pool.
v0.17.0 rebuilds the six `chinese_mansion_*` templates with the enclosure
planning skeleton: the south entrance is now a real `gate_house`
through-building instead of a wall hole, mansion buildings use form-rule
door-wall facings so their doors face their yards, and the gravel path is routed
from the gate-house inner opening to every door-front. Command names are
unchanged: `/myvillage place chinese_mansion_001` ... `_006`.
v0.18.0 **surface-zones** the courtyard/mansion ground + path layer so the path
reads as **three routes with six surfaces** instead of one flat gravel stripe:
the formal axis (青石 `PATH_FORMAL`), the winding garden tour (苔石 `PATH_TOUR`,
a waypoint polyline 假山→水岸→亭), and the waterside stairs + slab bridge
(`PATH_WATERSIDE`); the ground splits into 天井 心 (`GROUND_YARD_HEART`), 廊下
(`PATH_GALLERY`), and 夹道 (`PATH_ALLEY`) around the open grass. The mansion
garden gains the 月洞门 穿墙通道 (the formal↔tour material boundary), a dry-bank
reference-style 水亭 that directly touches the pond edge with a raised stone
base, dark wooden deck, heavy timber posts, lanterns, broad double eaves, and
stone roof ornaments, and a 仆役房 along the 倒座 夹道;
the cross-pond 汀步 spike-row is replaced by a flat slab bridge, with sparse
lily pads kept out of the bridge clear-water lane. The separate pond-side
水边廊/shed was removed after reference-image review; the mansion 主院 抄手游廊
remains a real 3D gallery with floor, columns, balustrade, and roof;
the 绣楼 sits in its own 后院 instead of the 花园, and the 主院 heart remains
grass rather than a full-width stone platform. `/myvillage place` ids are
unchanged — only the surface materials, gallery realization, and garden routing
change.
See [`docs/ai-kb/16_path_surface_zoning.md`](docs/ai-kb/16_path_surface_zoning.md).
Run the final full-profile generation after a vanilla-profile proof to restore
the shipped artifact profile.
v0.19.0 adds the first external-reference-driven original output:
`chinese_huipai_mansion_001..002`, derived from the `candidate_003` Hui-style
breakdown as generator grammar only. It keeps the source as `local_research`
provenance and implements the narrow recognizable slice: closed white street
facade, dark roof, stepped 马头墙 cue, and 门堂 → 天井一 → 享堂 → 天井二 → 寝堂
sequence with paired 厢房/side-wing enclosure around the sky-wells, an expanded
`47x76` / `43x72` review-lot footprint, clear spacing between the three-in
sequence elements, larger and taller hall / side-wing massing, and no 江南 garden
parcel. Visual acceptance
still requires reviewer verdict after preview.
v0.19.1 adds the second external-reference-driven original output:
`ganlan_stilted_house_001..002`, derived from the `candidate_005` Ganlan /
干栏式 breakdown as generator grammar only. It keeps the source as
`local_research` provenance and implements the narrow recognizable slice:
humid fully elevated bamboo/wood living floor, bay-aligned support posts and
underfloor tie beams down to the ground/water plane, mostly open underside,
framed permeable walls, an offset stair leading across a raised veranda, a
lower rain canopy beneath the main gable, and water passing below part of the
floor. It is a generated
sample family for review, not a copied Ganlan village, jigsaw pool, or runtime
worldgen integration. The owner accepted this narrow visual slice on
2026-07-11 after preview and automated validation; that verdict does not imply
broader Ganlan village or worldgen acceptance.
v0.20.0 rebuilds the existing `pagoda_001..003` landmarks instead of adding a
new building family. The three deterministic profiles now use five, five, and
seven occupied storeys; body footprints from `15x15` to `19x19`; stepped inset
schedules; a projecting bracketed eave at every storey boundary; framed upper
openings; first-storey colonnades; pyramidal crowns; and taller finials. The
compact `19x37x21` resource remains the fixed town-core pagoda, while the broad
`27x46x29` and slender `23x56x25` variants provide larger standalone/sect
landmarks. Python and Java footprint mirrors contain all three. The ids and
commands are unchanged, `candidate_006` is calibration-only provenance, and
final appearance still requires the new owner visual verdict.

Expected compound output:

```text
src/main/resources/data/myvillage/structure/main_hall_review.nbt
src/main/resources/data/myvillage/structure/side_wing_review.nbt
src/main/resources/data/myvillage/structure/front_row_review.nbt
src/main/resources/data/myvillage/structure/chinese_courtyard_001.nbt ... chinese_courtyard_006.nbt
src/main/resources/data/myvillage/structure/chinese_huipai_mansion_001.nbt ... chinese_huipai_mansion_002.nbt
src/main/resources/data/myvillage/structure/ganlan_stilted_house_001.nbt ... ganlan_stilted_house_002.nbt
src/main/resources/data/myvillage/function/gallery/chinese_courtyard.mcfunction
src/main/resources/data/myvillage/function/gallery/chinese_huipai_mansion.mcfunction
src/main/resources/data/myvillage/function/gallery/ganlan_stilted_house.mcfunction
src/main/resources/data/myvillage/function/place/chinese_courtyard_001.mcfunction ... chinese_courtyard_006.mcfunction
src/main/resources/data/myvillage/function/place/chinese_huipai_mansion_001.mcfunction ... chinese_huipai_mansion_002.mcfunction
src/main/resources/data/myvillage/function/place/ganlan_stilted_house_001.mcfunction ... ganlan_stilted_house_002.mcfunction
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
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/buildgen/tests/test_huipai_reference_slice.py
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
python3 tools/buildgen/tests/test_ganlan_stilted_house.py
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
python3 tools/render_structure.py --world run-acceptance/chunky_stage1_world --anchor 0 79 192 --spp 10   # Chunky path-traced survey PNGs from a placed-world target
python3 tools/render_structure.py --world run-acceptance/chunky_stage1_world --anchor 152 179 247 --target 156 181.5 248 --views right left --spp 10   # focused look-at for internal subjects such as a water court
python3 tools/write_visual_acceptance_report.py                             # report representative preview/Chunky visual targets
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
it can be closed, or until the related OpenSpec change is being archived.
`tools/write_visual_acceptance_report.py` writes
`reports/visual_acceptance_report.json` and `.md` after preview and Chunky prep,
listing the representative PNGs and in-game Chunky targets that must be opened
before claiming visual verification. Add new block colors to
`tools/block_colors.json`; unknown blocks render magenta and should be reported
there. `--max-px` (default 2048)
auto-reduces static PNG scale so large compounds stay bounded.

The separate headless Chunky path-tracing renderer is not currently a custom
`myvillage:` block acceptance path. It can render placed worlds for ordinary
blocks, but `myvillage:rockery_block` has been observed to render as Chunky's
unknown-block placeholder even with the MyVillage jar supplied through
`-texture`. Review custom block appearance, including `hero_rockery`, in a
Minecraft client until a dedicated renderer compatibility path exists. For
ordinary layout review, `tools/render_structure.py` defaults to
`--view-plan survey`, which renders four mid-height cardinal views plus four
high diagonal views. Use `--view-plan height-sweep` for low/mid/high passes
from each side, or `--view-plan cardinal` / explicit `--views front right back
left` for the old four-view behavior. Use `--target X Y Z` when the focal
subject is internal to a larger structure (for example the mansion 水亭/池面),
so the camera looks at that feature instead of the scanned bbox center.
Multi-view runs also write
`contact_sheet.png` by default; pass `--no-contact-sheet` only for narrow
diagnostics.

## Build The Mod

Requires JDK 21. The build also runs the Python structure generator
(`tools/generate_all_structures.py`); Gradle calls `python` on Windows and
`python3` elsewhere by default. If your environment maps Python differently,
override it with the `PYTHON` environment variable, e.g.
`PYTHON=python3.11 ./gradlew build` (Linux/macOS) or
`set PYTHON=py && gradlew.bat build` (Windows cmd).

```bash
./gradlew build
```

Confirm the jar contains the structure resources:

```bash
jar tf build/libs/*.jar | grep "data/myvillage/structure"
jar tf build/libs/*.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/*.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/*.jar | grep "assets/myvillage/textures/painting/inscription"
jar tf build/libs/*.jar | grep "assets/myvillage/textures/entity/simple_fox/simple_fox.png"
jar tf build/libs/*.jar | grep "data/myvillage/neoforge/biome_modifier/add_simple_fox_spawns.json"
jar tf build/libs/*.jar | grep "assets/myvillage/models/item/rideable_flying_sword.json"
jar tf build/libs/*.jar | grep "assets/myvillage/textures/item/rideable_flying_sword.png"
jar tf build/libs/*.jar | grep "assets/myvillage/blockstates/spirit_testing_stele.json"
jar tf build/libs/*.jar | grep "assets/myvillage/blockstates/technique_inheritance_stele.json"
```

The expected jar is:

```text
build/libs/myvillage-0.25.0.jar
```

## Versioning And Changelog

Maintain `CHANGELOG.md` whenever a version is prepared or a validated fix is
accepted. The authoritative version increment and synchronized-file rule lives
only in `openspec/config.yaml` under `rules.tasks`; apply that rule rather than
duplicating its mechanics here.

## Manual Acceptance Prep

Before a staged manual acceptance pass, prepare both the mod artifact and the
command documentation:

```bash
python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_custom_entities.py
python3 tools/validate_rideable_flying_sword.py
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 tools/validate_spirit_stone_resources.py
python3 tools/validate_cultivation_lifespan.py
python3 tools/validate_cultivation_meditation.py
python3 tools/validate_cultivation_gain.py
python3 tools/validate_cultivation_advancement.py
python3 tools/validate_mod_block_fallbacks.py
python3 tools/validate_plaque_bindings.py
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_compound_library.py --group cultivation_town --count 6
python3 tools/validate_compound_library.py --group cultivation_sect --count 2
python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2
python3 tools/validate_compound_library.py --group ganlan_stilted_house --count 2
python3 tools/buildgen/tests/test_huipai_reference_slice.py
python3 tools/buildgen/tests/test_ganlan_stilted_house.py
python3 tools/buildgen/tests/test_pagoda_landmark.py
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
python3 tools/write_visual_acceptance_report.py
python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview
./gradlew build
jar tf build/libs/myvillage-0.25.0.jar | grep "data/myvillage/structure"
jar tf build/libs/myvillage-0.25.0.jar | grep "data/myvillage/mod_block_fallbacks.json"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/textures/block/plaque"
jar tf build/libs/myvillage-0.25.0.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/textures/painting/inscription"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/textures/entity/simple_fox/simple_fox.png"
jar tf build/libs/myvillage-0.25.0.jar | grep "data/myvillage/neoforge/biome_modifier/add_simple_fox_spawns.json"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/models/item/rideable_flying_sword.json"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/textures/item/rideable_flying_sword.png"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/blockstates/spirit_testing_stele.json"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/blockstates/technique_inheritance_stele.json"
jar tf build/libs/myvillage-0.25.0.jar | grep "assets/myvillage/textures/item/low_grade_spirit_stone.png"
jar tf build/libs/myvillage-0.25.0.jar | grep "data/myvillage/worldgen/configured_feature/spirit_stone_ore.json"
jar tf build/libs/myvillage-0.25.0.jar | grep "data/myvillage/myvillage/realm/qi_refining.json"
```

Use the command list below as the acceptance script. Update this README,
`AGENTS.md`, and the relevant OpenSpec specs whenever commands or acceptance
prep steps change.

## Run Client

```bash
./gradlew runClient
```

Create or open a flat test world with commands enabled.

## Simple Fox Entity Smoke Test

`myvillage:simple_fox` is the first complete custom-entity slice. It reuses the
vanilla fox model, animation, AI, synchronized state, NBT, and sounds while
keeping an independent entity id, orange texture, spawn egg, loot table, and
low-weight taiga natural spawn entry.

```mcfunction
/summon myvillage:simple_fox ~ ~ ~
/give @s myvillage:simple_fox_spawn_egg
/data get entity @e[type=myvillage:simple_fox,limit=1,sort=nearest]
```

For save/reload, note the summoned fox's UUID from `/data get`, save and exit,
reopen the world, and query the same nearby entity again. Natural-spawn review
must use a recorded seed and taiga-family biome, then note observation time and
group size; a successful codec/server boot does not prove frequency. Inspect
front, both sides, back, three-quarter, idle, walk, sit, sleep, crouch, pounce,
hurt, and death before recording the human visual verdict.

## Rideable Flying Sword Smoke Test

Give the functional item to the current player:

```mcfunction
/give @s myvillage:rideable_flying_sword
```

Hold it and right-click once to create the sword below the player and mount it.
Right-click again to recall and remove the owned sword; that second use does not
create a replacement. Each player can own one loaded sword and the sword accepts
only its bound owner as its single passenger. Summoning needs enough block-clear
space for the sword's hitbox; a confined placement fails without leaving a sword.

Controls while mounted:

```text
W / S       forward / backward
A / D       left / right
Space       ascend
Shift       descend
```

The client sends one bounded six-key bitset. It does not send coordinates,
rotation, velocity, speed, owner identity, or an entity id. The server derives
the sword from the sender's current vehicle, validates the owner/passenger
relationship, expires stale input, and computes yaw, acceleration, drag, speed
limits, and collision-aware movement.

The entity stores the owner UUID, while the player's persistent data indexes the
current sword UUID. Explicit recall clears that index and discards every loaded
owner match; ordinary removal clears the index only when it still names that
sword. The sword is registered with `noSave()` and is discarded on owner death,
logout, dimension change, or separation beyond 64 blocks; it is not retained
across chunk unload, world reload, or server restart.

Automated preparation:

```bash
python3 tools/validate_rideable_flying_sword.py
./gradlew test
./gradlew build
./gradlew runAcceptanceServer
```

Dedicated-server startup checks payload/entity registration and common/client
separation, not riding quality. In a real client, manually verify all six
controls, Shift descent without dismount, neutral hover and gradual slowdown,
solid-block collision, no fall damage when the mounted sword descends onto the
ground, smooth riding without repeated position/yaw snaps, the blade tip pointing
along the player's horizontal view direction, fall-distance reset,
recall/singleton behavior, every cleanup condition, multiplayer authority, and
item-model scale/readability before recording acceptance.

## Cultivation Playable Loop

**当前已实现“测灵觉醒 -> 基础吐纳诀传承 -> 普通/灵石打坐 -> 修为结算 -> 确定性冲关”的第一段可玩循环；炼气四层是本版终点，不代表炼气后续层级与筑基已经开放。**

The foundation provides a server-authoritative immutable v3 player profile,
codec-backed Data Attachment persistence, synced definition registries,
owning-client snapshots, and operator commands. The initiation slice remains
two separate server-side actions:

```text
mortal_unawakened
  -> use myvillage:spirit_testing_stele
  -> deterministic root + mortal_qi_sensed
  -> use myvillage:technique_inheritance_stele
  -> myvillage:basic_breathing at mastery 0
```

Awakening never teaches the technique automatically. Inheritance learns
`myvillage:basic_breathing` at mastery `0`; only an active, eligible meditation
session executes that technique. Neither initiation action directly grants
progress, spiritual power, stability, mastery growth, attributes, effects, or
advancement.

Acquire the two facilities from `myvillage:main` creative inventory or with:

```mcfunction
/give @s myvillage:spirit_testing_stele
/give @s myvillage:technique_inheritance_stele
```

These are the only current stele acquisition paths. Neither stele has a recipe,
natural generation, sect/worldgen placement, BlockEntity, menu, or block-local
player data.

The first spirit-stone slice can be inspected directly with:

```mcfunction
/give @s myvillage:low_grade_spirit_stone 64
/give @s myvillage:spirit_stone_ore
/give @s myvillage:deepslate_spirit_stone_ore
```

Both ores require an iron-tier-or-better pickaxe. Silk Touch drops the matching
ore block; ordinary mining starts from one low-grade spirit stone, and Fortune
uses the vanilla `ore_drops` bonus formula. The Overworld biome modifier adds
upper/middle/deep layers with counts `30/3/3`, vein sizes `6/6/3`, and height
bands `80..384`, `-24..56`, and world-bottom through `0`. Existing generated
chunks are not retrofitted: natural ore must be checked in newly generated
chunks. There is no raw ore, smelting chain, recipe, higher grade, storage block,
fragment, refining machine, or currency behavior in this slice.

Press `H` in game to open the non-pausing cultivation panel. The binding is
configurable as `Open Cultivation Profile` under the MyVillage key category.
Its Profile tab renders the latest server-synchronized realm, stage,
current/capped progress, stability, spiritual affinity, power, root,
techniques, calendar, and lifespan. Its Meditation tab shows the current rates,
stage-owned spirit-stone cost and runtime state, with normal, spirit, stop, and
advance buttons. Profile values remain read-only: each button sends only the
same bounded action intent as V/B/G/N, and the server revalidates every result.
`H` or Escape closes the panel without stopping cultivation.

The current profile is schema version `3`: all v2 fields plus non-negative
`spiritualAffinity`, whose new/reset/migrated default is `10`. The
version-dispatched codec preserves explicit v1 and v2 decode paths, migrates
both into v3 without losing unknown ids, progress, lifespan, or reserve, and
writes only v3 afterward. `meditationQiReserve` remains stored for save
compatibility but is inert: v3 cultivation does not credit, spend, convert, or
display it. The attachment uses `copyOnDeath` as its only copy mechanism; there
is no duplicate cultivation `PlayerEvent.Clone` handler. Every profile
replacement still goes through `CultivationService`.

The synced datapack registries and shipped resource roots are:

| Registry key | Resource root |
|---|---|
| `myvillage:realm` | `src/main/resources/data/myvillage/myvillage/realm/` |
| `myvillage:spiritual_element` | `src/main/resources/data/myvillage/myvillage/spiritual_element/` |
| `myvillage:technique` | `src/main/resources/data/myvillage/myvillage/technique/` |

Spiritual-root generation uses only the Overworld seed, player UUID, algorithm
version `1`, fixed salt `0x4D5956494C4C4147`, and the current positive-weight
element id/`awakening_weight` set sorted by full id. Omitted weights default to
`1`; weight `0` excludes an element. The count distribution is `10/25/35/20/10`
for one through five distinct elements, and integer largest-remainder allocation
produces positive affinities totaling exactly `10000`.

The same root is reproduced after `reset` only when seed, UUID, eligible ids,
weights, and algorithm version are unchanged. Existing saved roots are never
recalculated. Datapack id/weight changes affect future awakening and may change a
post-reset result; seed plus UUID alone is not a permanence promise. Reusing the
testing stele without reset never rerolls an existing root.

`myvillage:basic_breathing` has no generic/data-driven executor field. The fixed
first-release settlement service executes only this technique and requires
current definition eligibility at minimum `myvillage:mortal` /
`myvillage:mortal_qi_sensed`, with no element-affinity restriction. Repeat
inheritance never resets existing mastery.

### Meditation, Time, And Settlement

The client sends only a bounded action intent. Identity, position, eligibility,
timing, profile values, inventory use, settlement, and advancement outcome are
derived on the logical server.

| Configurable key | Intent |
|---|---|
| `V` | Start normal meditation |
| `B` | Start spirit-stone meditation |
| `G` | Stop meditation or advancement |
| `N` | Start the current definition-owned advancement |
| `H` | Open/close the Profile and Meditation panel |

Normal and spirit meditation share one transient state machine. Both start with
`40` eligible preparation ticks. Starting requires an awakened root, learned
Basic Breathing, survival/adventure mode, a living non-exhausted player on stable
ground, no mount/swimming/flight/sleep/item use/conflicting session, and no
positive damage during the previous `100` server ticks. Moving more than `0.01`
block on any axis, jumping, damage, attack/swing, mining, block/entity/item use,
mounting, swimming/flying/sleeping, incompatible game mode, dimension change,
death, logout, or `G` interrupts the session. Camera yaw and pitch, opening H,
and switching between its tabs are allowed.

The time defaults under server config section `cultivation_time` are
`ticks_per_day = 24000` and `days_per_year = 6`: one cultivation year is
`144000` effective ticks. The Overworld `SavedData` calendar advances once per
server tick while at least one survival/adventure player is online. A player's
lifespan advances only while that player is online, alive, and in
survival/adventure. Sleep, `/time set`, daylight-cycle rules, dimension, and
offline wall time do not drive either clock. Realm definitions currently grant
maximum lifespans of `80` mortal, `120` Qi Refining, and `240` Foundation years;
warnings are derived at `10`, `5`, and `1` remaining years.

Both counters store raw effective ticks. Changing either time-scale setting does
not rescale old data; it immediately reinterprets all prior calendar and lifespan
ticks and can move the displayed date, warnings, or exhaustion in either
direction. The server logs an operator warning on load/reload. Treat scale
changes as world-rule migrations, back up the world first, and do not assume the
old displayed ages are preserved.

Active Basic Breathing makes one progress settlement every `10` continuously
eligible ticks. Normal meditation adds the current server-profile
`spiritualAffinity`; its default result is therefore `10` progress per batch.
Spirit meditation adds a fixed total `50` progress per funded batch, independent
of affinity, and atomically removes the current source stage's complete batch
from ordinary inventory:

| Current source stage | Low-grade stones / 10 ticks | Progress / 10 ticks |
|---|---:|---:|
| Mortal, qi sensed | 1 | 50 |
| Qi Refining I | 1 | 50 |
| Qi Refining II | 2 | 50 |
| Qi Refining III | 3 | 50 |

The final nonempty batch pays the full item cost even when fewer than `50`
points remain; its output is clamped to the cap. At the cap, no stone is scanned
or removed. Insufficient inventory removes nothing, applies that due batch at
the normal affinity rate, and downgrades the same session to normal without a
new preparation period. Failed pre-install profile commits restore the complete
multi-slot item removal. Stability does not grow while stage-local progress is
below its cap, including the batch that first fills progress. Starting with the
next ten-tick batch, either mode adds current `spiritualAffinity` to stability,
without scanning or consuming a stone, until the stage stability cap is reached.
Basic Breathing mastery alone remains at `10` per configured cultivation year
in both modes and across both phases.

The shipped stage-local caps are:

| Stage | Progress cap | Stability cap |
|---|---:|---:|
| Mortal, qi sensed | 1000 | 500 |
| Qi Refining I | 1100 | 550 |
| Qi Refining II | 1200 | 600 |
| Qi Refining III | 1300 | 650 |

Unawakened mortal, Qi Refining IV-IX, and Foundation Early have no cultivation
cap in this release and cannot gain progress. `cultivationProgress` is always
the current stage's progress; successful advancement resets it to zero and never
transfers overflow.

### Deterministic Advancement

Press `N` only after the current cap and stability requirement are met. The
server owns the target and rule; there is no random failure and no client-chosen
stage. Advancement is mutually exclusive with meditation and reuses its
interruption set.

| Transition | Required progress | Duration | Required stability | Stability after success | Interrupt loss |
|---|---:|---:|---:|---:|---:|
| Qi-sensed mortal -> Qi Refining I | 1000 | 100 ticks | 500 | 250 | 0 |
| Qi Refining I -> II | 1100 | 100 ticks | 550 | 275 | 0 |
| Qi Refining II -> III | 1200 | 120 ticks | 600 | 300 | 0 |
| Qi Refining III -> IV bottleneck | 1300 | 200 ticks | 650 | 325 | 5 |

The result column assumes advancement starts at the ordinary stage cap. Runtime
always retains integer-floor half of actual current stability; it does not
subtract a fixed absolute cost.

Ordinary advancement loses no stability when interrupted. A player/world
interruption during the Qi III bottleneck loses exactly `5` stability, clamped
at zero; clean server shutdown or registry-reload teardown has no penalty. One
completed process advances exactly one stage. Qi Refining IV is the release
ceiling: Qi IV-IX cultivation, Foundation breakthrough, pills, facilities,
environment rules, tribulation, and reincarnation remain deferred.

Lifespan exhaustion is a derived state, not a death loop. At or beyond the
current realm maximum the player cannot start meditation or advancement and sees
an explicit status, but the profile is not cleared and the system does not kill
the player. Lifespan continues to be counted monotonically while otherwise
eligible.

All cultivation commands inherit the existing `/myvillage` permission-level-2
requirement. The existing administrator surface remains available:

```mcfunction
/myvillage cultivation info [target]
/myvillage cultivation reset <target>
/myvillage cultivation setrealm <target> <realm_id> <stage_id>
/myvillage cultivation setprogress <target> <amount>
/myvillage cultivation setstability <target> <amount>
/myvillage cultivation setpower <target> <amount>
/myvillage cultivation setroot <target> <metal> <wood> <water> <fire> <earth>
/myvillage cultivation clearroot <target>
/myvillage cultivation learn <target> <technique_id>
/myvillage cultivation forget <target> <technique_id>
/myvillage cultivation setmastery <target> <technique_id> <amount>
```

`setstability` accepts a non-negative integer. The profile schema has no fixed
`100` ceiling; ordinary gameplay derives the current stage cap from half of its
progress cap.

The normal-rules initiation actions expose all eight English/pinyin routes:

```mcfunction
/myvillage cultivation awaken [target]
/myvillage cultivation juexing [target]
/myvillage xiulian awaken [target]
/myvillage xiulian juexing [target]
/myvillage cultivation initiate [target]
/myvillage cultivation rumen [target]
/myvillage xiulian initiate [target]
/myvillage xiulian rumen [target]
```

Omitting `target` uses the executing player. Awakening routes accept no seed,
element, affinity, count, reroll, force, or bypass argument. Inheritance routes
always target `myvillage:basic_breathing` and accept no technique id or bypass.
Both command roots continue to accept either literal in every pair:

| English | Pinyin |
|---|---|
| `info` | `chakan` |
| `reset` | `chongzhi` |
| `setrealm` | `shezhijingjie` |
| `setprogress` | `shezhixiuwei` |
| `setstability` | `shezhiwendingdu` |
| `setpower` | `shezhilingli` |
| `setroot` | `shezhilinggen` |
| `clearroot` | `qingchulinggen` |
| `learn` | `xuexi` |
| `forget` | `yiwang` |
| `setmastery` | `shezhishuliandu` |
| `awaken` | `juexing` |
| `initiate` | `rumen` |

Run the complete playable-loop and affinity/UI revision handoff gates:

```bash
openspec validate --specs --strict
for change in add-spirit-stone-resources add-cultivation-lifespan-calendar add-cultivation-meditation add-basic-breathing-cultivation-gain add-qi-refining-advancement; do openspec validate "$change" --type change --strict; done
openspec validate revise-cultivation-affinity-meditation-ui --type change --strict
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 tools/validate_spirit_stone_resources.py
python3 tools/validate_cultivation_lifespan.py
python3 tools/validate_cultivation_meditation.py
python3 tools/validate_cultivation_gain.py
python3 tools/validate_cultivation_advancement.py
python3 tools/validate_mod_items.py
python3 -m unittest discover -s tools/tests -p 'test_validate_*.py'
./gradlew test
./gradlew build
python3 tools/run_chunky_acceptance.py --stage 1
```

Stage 1 proves bounded dedicated-server startup, registration, datapack loading,
payload direction, and side safety only. It does not prove ore readability,
natural distribution, stele interaction, controls, exact interruption feel,
inventory consumption, H-screen layout, multiplayer clocks, persistence, or
advancement. Use a real client and record every unobserved item as
`not_verified`, never as an inferred pass.

### In-Game Acceptance

Use a disposable world copy for time-scale and exhaustion checks. Build and
install the same jar on client and server, keep the default scale for the main
pass, and record the exact game/version/config used.

1. Use the three spirit-resource `/give` commands above. Confirm inventory icons,
   names, hand scale, both placed block textures, and distinction from ordinary
   stone/deepslate. Confirm all three entries appear in `myvillage:main`.
2. In survival, break both ores with an under-tier/wrong tool, an iron pickaxe,
   a Silk Touch iron pickaxe, and a Fortune pickaxe. The wrong tool yields no
   resource, iron yields low-grade stones, Silk Touch yields the matching block,
   and repeated Fortune trials produce bonuses without changing the drop item.
3. Explore newly generated Overworld chunks at the upper, middle, and deep bands.
   Confirm both stone targets occur and that old generated chunks are unchanged;
   do not infer distribution from `/give` or placed blocks.
4. Run `/myvillage cultivation reset @s`, then test the testing stele and the
   inheritance stele as separate actions. Confirm awakening does not teach the
   technique, inheritance does not reroll the root, and repeat use is idempotent.
5. Open `H`. Confirm the Profile and Meditation tabs remain within the panel at
   normal and constrained GUI scales. Profile must show schema 3, affinity 10,
   calendar, lifespan, realm/stage, progress/cap, stability/current-stage cap,
   power, root, and mastery without displaying legacy reserve. Meditation must
   show status, normal and spirit results, source-stage cost, inventory count,
   locked/active/capped stability state, and four stable buttons without treating
   displayed values as authority.
6. On stable ground use both the Normal button and `V`; remain still through the
   40-tick preparation and confirm one bounded start each. Repeat parity checks
   for Spirit/B, Stop/G, and Advance/N. Rotate the camera, switch H tabs, and
   close H without interruption or an implicit stop, then separately verify
   movement, jump, positive damage, attack/swing, mining, item/block/entity use,
   mount, swim/flight/sleep, mode change, dimension change, death, logout, and
   `G` each end a session once. Verify recent damage blocks restart for 100 ticks.
7. Confirm default-affinity normal meditation adds exactly 10 progress per ten
   active ticks. Prepare sensed, Qi-I, Qi-II, and Qi-III profiles and confirm one
   funded spirit batch adds 50 while removing exactly `1/1/2/3` stones across
   ordinary inventory slots. Confirm a final partial-cap batch pays the full
   cost and clamps output, an already capped batch costs nothing, and an
   underfunded batch removes nothing, applies the normal affinity result, and
   downgrades once. Before progress is full, confirm stability never changes,
   including the batch that fills progress. On the next normal and spirit
   batches, confirm stability gains current affinity, consumes no stone, and
   clamps at `500/550/600/650`; mastery must remain at 10 per configured year.
   Legacy reserve must remain unchanged and inert.
8. Use the administrator setters to prepare each row of the advancement table.
   Press `N`, remain eligible for its exact duration, and confirm one transition,
   zero progress, integer-floor half of prior stability, and preservation of root, power,
   techniques, mastery, lifespan, affinity, and inert reserve. Interrupt an ordinary attempt and
   confirm zero loss; interrupt Qi III -> IV and confirm exactly five stability
   loss. At Qi IV, `N` and cultivation gain must report the release limit.
9. With one survival/adventure player, observe shared calendar and personal age
   advance independently of sleep and `/time set`. Switch the only player to
   creative/spectator and confirm both pause. With a second survival/adventure
   player online, confirm the shared calendar advances while the excluded or
   offline first player's personal age does not. Separately verify reconnect,
   dimension change, death/respawn, ordinary save/restart, and clean-stop flushes
   without double age. Also wait through one 600-tick interval to verify the
   periodic batch path separately.
10. In a backed-up disposable copy, change `cultivation_time.ticks_per_day` or
    `days_per_year` and reload/restart. Confirm the operator warning and immediate
    reinterpretation of raw history. A `1/1` scale can reach the mortal limit
    quickly: exhaustion must block `V`, `B`, and `N` with a clear status, must not
    kill the player or clear the profile, and restoring the old scale may make the
    same raw counter non-exhausted again.

Owner real-client verdict recorded on 2026-07-13: `pass`.

| Manual acceptance surface | Result |
|---|---|
| Three item/block assets and creative-tab exposure | `pass` |
| Iron-tier, Silk Touch, Fortune, and wrong-tool loot | `pass` |
| Upper/middle/deep generation in new Overworld chunks | `pass` |
| Separate testing/inheritance stele flow and repeat behavior | `pass` |
| H Profile/Meditation tabs, text fit, values, buttons, and status feedback | `pass` |
| V/B/G/N and button parity, preparation, camera movement, and interruptions | `pass` |
| Affinity progress, `1/1/2/3` direct costs, rollback, cap, and downgrade | `pass` |
| Pre-cap stability lock, post-cap affinity gain, no stone cost, and `500/550/600/650` caps | `pass` |
| `1000/1100/1200/1300` advancement rules, stability halving, interruption, Qi-IV ceiling | `pass` |
| Shared calendar, personal online lifespan, lifecycle persistence, multiplayer | `pass` |
| Config reinterpretation warning and non-lethal exhaustion | `pass` |

Use only `pass`, `fail`, or `not_verified`. A `fail` records the observed mismatch
and reproduction steps; `not_verified` means the surface was not directly
observed and does not block truthful reporting of automated results.

Profile, time, and session status travel only server-to-client. The sole
client-to-server cultivation payload is the bounded meditation/advancement
intent enum used by both keys and H buttons; it carries no identity, coordinate,
velocity, affinity, resource count, rate, profile value, target stage, or
result. Client caches and button state are presentation-only and clear on
disconnect.

The serial dependency is intentional: spirit resources -> profile v3/time ->
meditation state -> affinity/direct-stone Basic Breathing settlement ->
advancement. Later work must
treat Qi IV+, Foundation breakthrough, generic technique execution, spiritual-
power recovery, pills/facilities, combat/exploration rewards, and reincarnation
as separate boundaries.

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
/myvillage townat 20260618 512 80 0
```

The optional seed makes generation deterministic for the same seed and site.
`townat` is the RCON/console-safe coordinate form; it uses the same planner and
realizer as `/myvillage town <seed>` but anchors at the explicit block position
instead of requiring a player.
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
/myvillage sectat 20260618 -512 80 0
```

Force-generate a worldgen-style sect (with its derived mountain) at the player,
for review/testing, optionally selecting the detached-spire variant:

```mcfunction
/myvillage sect worldgen
/myvillage sect worldgen 20260618
/myvillage sect worldgen 20260618 pavilion_short_straight_east
/myvillage sectat worldgen 20260618 none -512 80 512
/myvillage sectat worldgen 20260618 pavilion_short_straight_east -512 80 512
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
`sectat` and `sectat worldgen` are the coordinate-addressable RCON/console forms
and do not require a `ServerPlayer`.

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
/myvillage placeat test_house_03 0 80 0
```

Place a generated building directly:

```mcfunction
/myvillage place small_house_001
/myvillage placeat small_house_001 0 80 0
/myvillage place medium_house_001
/myvillage place blacksmith_001
/myvillage place medium_shop_001
/myvillage place big_house_001
/myvillage place chinese_courtyard_001
/myvillage place chinese_mansion_001
/myvillage place chinese_mansion_002
/myvillage place chinese_mansion_003
/myvillage place chinese_mansion_004
/myvillage place chinese_mansion_005
/myvillage place chinese_mansion_006
/myvillage place chinese_huipai_mansion_001
/myvillage place chinese_huipai_mansion_002
/myvillage place ganlan_stilted_house_001
/myvillage place ganlan_stilted_house_002
/myvillage place hero_rockery
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
/place template myvillage:chinese_mansion_001 ~ ~-1 ~
/place template myvillage:chinese_huipai_mansion_001 ~ ~-1 ~
/place template myvillage:ganlan_stilted_house_001 ~ ~-1 ~
/place template myvillage:tavern_001 ~ ~-1 ~
/place template myvillage:lord_manor_001 ~ ~-1 ~
/place template myvillage:cultivation_town_001 ~ ~-1 ~
/place template myvillage:cultivation_sect_001 ~ ~-1 ~
```

When `/myvillage place` substitutes optional-mod palette entries because a decor
mod is not loaded, the success line includes `fallback_substitutions=<count>`.
The coordinate form `/myvillage placeat <structure_id> <x> <y> <z>` applies the
same one-block Y offset for generated non-test structures and uses the same
runtime fallback resolver.

Place all loaded `myvillage` blueprints in a grouped gallery with 128-block
spacing:

```mcfunction
/myvillage gallery
/myvillage galleryat all 0 80 0
```

Place only the original non-cultivation structures, or only the cultivation
structures, in the same grouped gallery layout:

```mcfunction
/myvillage gallery original
/myvillage gallery cultivation
/myvillage galleryat original 0 80 0
/myvillage galleryat cultivation 0 80 0
```

The full gallery is arranged as columns by broad type: houses, shops,
blacksmiths, Chinese courtyard compounds, civic structures, cultivation town,
cultivation sect, Chinese review sub-buildings, tests, and other templates.
The `original` gallery keeps the non-cultivation columns, while the
`cultivation` gallery keeps the cultivation town and cultivation sect columns.
It is intended for side-by-side visual comparison across sizes and archetypes.
The `galleryat` variants keep the same grouping, filtering, ordering, spacing,
and fallback behavior but can be driven from RCON or the server console.

Staged Chunky/RCON automation:

```bash
python3 tools/run_chunky_acceptance.py --stage 1   # Chunky/RCON/server lifecycle
python3 tools/run_chunky_acceptance.py --stage 2   # plus myvillage ...at command smoke
python3 tools/run_chunky_acceptance.py --stage 3   # plus full optional-mod preflight + cases
python3 tools/run_chunky_acceptance.py --stage 4   # plus locate myvillage:sect + bounded Chunky
python3 tools/write_visual_acceptance_report.py    # visual handoff checklist from previews + Chunky report
```

Stage 2 includes coordinate placement for both `small_house_001` and
`chinese_mansion_001`, then the cultivation gallery/town/sect smoke cases.
Stage 3 extracts `exmod/mod_jars.zip` into the isolated profile and verifies
both the expected optional mod ids and mandatory jar dependencies before the
server starts. The local staged zip must include all dependency jars required by
those mods, for example `architectury` for Fetzi's Displays.
Stage 4 runs only after Stage 3 passes; it locates a natural `myvillage:sect`
and runs a small bounded Chunky task around that site.
The visual report does not judge aesthetics automatically; it records the
representative preview PNGs and generated-world coordinates that the agent and
reviewer must inspect before visual acceptance is claimed.
Do not count headless Chunky renderer images as custom `myvillage:` block visual
acceptance evidence; custom blocks currently require client-side inspection.
When using the headless renderer for ordinary placed-world review, keep the
default multi-camera `--view-plan survey` unless a narrower diagnostic view is
intentional; use `--view-plan height-sweep` when angle/height could affect the
layout judgment. The renderer writes a manifest-linked `contact_sheet.png` by
default for quick multi-angle comparison.

Generated datapack functions are also available after resource generation:

```mcfunction
/function myvillage:gallery/medieval_village
/function myvillage:gallery/chinese_courtyard
/function myvillage:gallery/chinese_mansion
/function myvillage:gallery/chinese_huipai_mansion
/function myvillage:gallery/ganlan_stilted_house
/function myvillage:gallery/civic
/function myvillage:gallery/cultivation_town
/function myvillage:gallery/cultivation_sect
/function myvillage:place/chinese_courtyard_001
/function myvillage:place/chinese_mansion_001
/function myvillage:place/chinese_huipai_mansion_001
/function myvillage:place/ganlan_stilted_house_001
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
  vocabulary and placed flanking the shrine. Pagodas use deterministic
  five/five/seven-storey profiles with stepped taper, one bracketed eave per
  occupied level, a pyramidal crown, and profile-specific finial height; the
  compact profile stays in the fixed civic-core parcel while larger profiles
  are available to roomy parcels and sect placements. The footprint is
  force-loaded via chunk tickets so the whole town generates in one command.
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
- /myvillage placeat <structure_id> <x> <y> <z>
- /myvillage list
- /myvillage town [seed]
- /myvillage townat <seed> <x> <y> <z>
- /myvillage sect [seed]
- /myvillage sect worldgen [seed] [variant]
- /myvillage sectat <seed> <x> <y> <z>
- /myvillage sectat worldgen <seed> <variant|none> <x> <y> <z>
- /myvillage gallery
- /myvillage gallery original
- /myvillage gallery cultivation
- /myvillage galleryat <all|original|cultivation> <x> <y> <z>
- a custom `myvillage:sect` worldgen Structure: sects are sited during world generation, biome-gated by `tags/worldgen/biome/has_sect`, spaced by `worldgen/structure_set/sect`, and `/locate`-able, resting on a mountain derived from the terrace profile (反推山形)
- generated optional-mod runtime fallback map and fallback coverage validation
- `myvillage:simple_fox`: vanilla-model custom entity, spawn egg, empty first-pass loot table, and low-weight taiga natural spawning
- `myvillage:rideable_flying_sword`: transient, one-player, server-authoritative flying-sword vehicle and creative-tab item
- NBT integrity validation for roof/top-layer/function-block/signature checks
- deterministic town-plan and sect-plan/sect-generation validation with top-down previews
```

Not included:

```text
- passive/natural *town* worldgen (towns remain command-built via `/myvillage town`; only sects are sited during world generation)
- jigsaw / template-pool generation (the sect structure is a hand-written `Structure`, not a jigsaw template pool)
- reward-bearing entity loot or complex authored block-entity NBT
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
`/myvillage place pagoda_001`, `/myvillage place pagoda_002`,
`/myvillage place pagoda_003`, `/myvillage place pavilion_001`,
`/myvillage place bell_drum_tower_001`,
`/myvillage place sect_gate_001`,
`/myvillage place scripture_pavilion_001`,
`/myvillage place cultivation_sect_001`, `/myvillage gallery`,
`/myvillage gallery original`, and `/myvillage gallery cultivation`. The
simple-fox slice completed its summon, spawn-egg, save/reload,
natural-frequency, multiplayer, and multi-view Minecraft acceptance on
2026-07-12.
