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

## 云山巨城 · Browser Voxel Artwork

`web/yunshan-city/` is a standalone browser project (independent of the mod
jar): a three.js voxel scene of a terraced Chinese mountain city above a sea of
clouds, built entirely from 0.2 m voxels, with a guided road tour and a
continuous 24-hour day/night cycle. Open `web/yunshan-city/dist/yunshan-city.html`
directly, or run `npm start` inside `web/yunshan-city/` and visit
<http://localhost:8080/>. Controls and architecture are documented in
[web/yunshan-city/README.md](web/yunshan-city/README.md).

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

Confirm the jar contains the structure resources (name the versioned jar:
`build/libs/` keeps the jars of earlier versions):

```bash
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/structure"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/painting/inscription"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/entity/simple_fox/simple_fox.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/neoforge/biome_modifier/add_simple_fox_spawns.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/beast/demon_wolf.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/beast/demon_wolf_model.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/beast/demon_wolf_animations.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/entity/demon_wolf/demon_wolf_eyes.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/npc/cultivator_model.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/npc/cultivator_animations.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/entity/cultivator/cultivator.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/rideable_flying_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/rideable_flying_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/qingfeng_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/qingfeng_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/qingfeng_sword_3d.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/qingfeng_sword_model.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/qingfeng_sword_geometry.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/qingfeng_first_person.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/lingxiao_spear.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/lingxiao_spear_3d.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/lingxiao_spear.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/lingxiao_spear_model.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/lingxiao_spear_geometry.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/lingxiao_spear_first_person.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/xuantie_gauntlet.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/xuantie_gauntlet_3d.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet_model.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/xuantie_gauntlet_geometry.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/xuantie_gauntlet_first_person.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/xuanyue_zhenshan_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/xuanyue_zhenshan_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/chilian_lihuo_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/chilian_lihuo_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/qingxiao_liuyun_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/qingxiao_liuyun_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/player_animations/sword_combat.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/player_animations/spear_combat.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/player_animations/fist_combat.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/recipe/qingfeng_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/minecraft/tags/item/swords.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/blockstates/spirit_testing_stele.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/blockstates/technique_inheritance_stele.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/guideme_guides/cultivation.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/guides/myvillage/cultivation/index.md"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/cultivation_handbook.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/manual_core_huang.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/manual_core_tint.png"
```

The expected jar is:

```text
build/libs/myvillage-0.44.0.jar
```

## Versioning And Changelog

Maintain `CHANGELOG.md` whenever a version is prepared or a validated fix is
accepted. The authoritative version increment and synchronized-file rule lives
only in `openspec/config.yaml` under `rules.tasks`; apply that rule rather than
duplicating its mechanics here.

`tools/bump_version.py` applies the rule. It rewrites `mod_version` in
`gradle.properties`, the `[[mods]]` version in `neoforge.mods.toml`, and every
README jar name of the current version, and inserts an empty `## <version>`
heading above the newest CHANGELOG entry. It refuses a malformed or
non-increasing version and a tree whose four places already disagree. Other
mentions of the old version (prose, older CHANGELOG entries) are left alone.

```bash
python3 tools/bump_version.py 0.30.0 --dry-run   # print the plan, write nothing
python3 tools/bump_version.py 0.30.0
```

Write the CHANGELOG entry by hand, then run the release gate:

```bash
python3 tools/release_gate.py                 # every step, with a fresh ./gradlew build
python3 tools/release_gate.py --list          # step names, in order
python3 tools/release_gate.py --skip-build    # no Gradle build and no jar steps
python3 tools/release_gate.py --only 'validate-cultivation-*'
```

It prints one line per step and a summary, and exits non-zero if a step
failed. The steps: the four version places agree and the newest CHANGELOG
entry has a body; `openspec validate --specs --strict` (skipped without the
CLI); the generator `--check`s (including `python3 -m tools.beastgen build demon_wolf
--check` and `python3 -m tools.npcgen build cultivator --check`); the structure generator, then the validators
from [Manual Acceptance Prep](#manual-acceptance-prep); `tools/tests`; the
offline preview's numpy tests (solver parity, `sweep`/`diff` rendering) under
`$MC_PREVIEW_PYTHON` or `.venv-preview` (skipped when neither exists); and `./gradlew build` under the shared heavy-work lock (`$MC_HEAVY_LOCK`, else
`.mc-heavy.lock` beside the main checkout). Python steps use `/usr/bin/python3`
because it has PyYAML. The gate deletes the current version's jar before
building, so the combat, spirit-stone, and GuideME jar checks and the README
jar listing (each `jar tf ... | grep` pattern above must match an entry) read a
jar written by that run. It fails if regenerating changed a file under
`src/main/resources`. It starts no Minecraft client or server:
`runAcceptanceServer`, Chunky stages, previews, and real-client checks stay
manual. Per-step output is in `reports/release_gate/`.

## Manual Acceptance Prep

Before a staged manual acceptance pass, prepare both the mod artifact and the
command documentation. `python3 tools/release_gate.py` (see
[Versioning And Changelog](#versioning-and-changelog)) runs the generator,
validators, build, and jar listing below in one command; previews, the visual
report, and the preview server are not part of it.

```bash
python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_custom_entities.py
python3 -m tools.beastgen build demon_wolf --check
python3 -m tools.npcgen build cultivator --check
python3 tools/validate_rideable_flying_sword.py
python3 tools/validate_sword_combat_foundation.py
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 tools/validate_spirit_stone_resources.py
python3 tools/validate_cultivation_lifespan.py
python3 tools/validate_cultivation_meditation.py
python3 tools/validate_cultivation_gain.py
python3 tools/validate_cultivation_advancement.py
python3 tools/validate_guideme_cultivation_guide.py    # needs PyYAML in this python3
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
python3 tools/validate_world_sim.py
python3 tools/preview_structure.py --all
python3 tools/generate_town_plan_preview.py --count 6    # default covers all 6 wall families
python3 tools/generate_sect_plan_preview.py --count 6    # default covers all 3 detached-spire variants + absent
python3 tools/generate_region_topology_preview.py --count 6   # offline 洲/域 graph previews
python3 tools/write_visual_acceptance_report.py
python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview
./gradlew build
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/structure"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/mod_block_fallbacks.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/blockstates/wall_plaque.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/block/plaque"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/painting_variant/inscription"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/painting/inscription"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/entity/simple_fox/simple_fox.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/neoforge/biome_modifier/add_simple_fox_spawns.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/beast/demon_wolf.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/beast/demon_wolf_model.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/beast/demon_wolf_animations.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/entity/demon_wolf/demon_wolf_eyes.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/npc/cultivator_model.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/npc/cultivator_animations.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/entity/cultivator/cultivator.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/rideable_flying_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/rideable_flying_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/qingfeng_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/qingfeng_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/qingfeng_sword_3d.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/qingfeng_sword_model.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/qingfeng_sword_geometry.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/qingfeng_first_person.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/lingxiao_spear.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/lingxiao_spear_3d.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/lingxiao_spear.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/lingxiao_spear_model.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/lingxiao_spear_geometry.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/lingxiao_spear_first_person.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/xuantie_gauntlet.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/xuantie_gauntlet_3d.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/xuantie_gauntlet_model.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/xuantie_gauntlet_geometry.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/combat/xuantie_gauntlet_first_person.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/combat/"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/xuanyue_zhenshan_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/xuanyue_zhenshan_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/chilian_lihuo_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/chilian_lihuo_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/qingxiao_liuyun_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/qingxiao_liuyun_sword.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/player_animations/sword_combat.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/player_animations/spear_combat.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/player_animations/fist_combat.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/recipe/qingfeng_sword.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/minecraft/tags/item/swords.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/blockstates/spirit_testing_stele.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/blockstates/technique_inheritance_stele.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/textures/item/low_grade_spirit_stone.png"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/worldgen/configured_feature/spirit_stone_ore.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "data/myvillage/myvillage/realm/qi_refining.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/guideme_guides/cultivation.json"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/guides/myvillage/cultivation"
jar tf build/libs/myvillage-0.44.0.jar | grep "assets/myvillage/models/item/cultivation_handbook.json"
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

## Demon Wolf (妖狼)

`myvillage:demon_wolf` is the first hostile beast (0.33.0): a wolf about 1.3
blocks at the shoulder with its own model, keyframe clips, and AI. It hunts
the nearest player it can see and fights with two moves: a close-range bite
(撕咬) with a short telegraph and a long recovery, and a mid-range pounce (扑击)
with a deep crouch, an aim that locks on where the target stood, a leap, and a
skidding landing. The moves are built so that stepping sideways avoids the
bite and stepping away after the pounce locks makes it miss (the ledger below
records what has been observed). There is no natural spawning and no drop:
summon it or use the spawn egg (in `myvillage:main` after the simple fox egg).
It needs a difficulty above Peaceful, where vanilla removes every monster.

```mcfunction
/summon myvillage:demon_wolf ~ ~ ~
/give @s myvillage:demon_wolf_spawn_egg
```

During part of each move (its immune window) a combat hit still damages the
wolf and freezes it for the hit-stop but neither stuns nor pushes it; outside
that window it flinches like any mob, its move is cancelled, and that move
comes back after a short cooldown. The bite resists from its first tick
through its last active tick, so its recovery is the punish window; the pounce
can be interrupted early in its wind-up and resists from mid wind-up through
its last active tick. While a move shows, the client draws the wolf on the
server's tick-by-tick path (each position update applied in one step, the
move clip two ticks behind the newest synced move tick); that keeps the
pounce's landing on the ground, and uneven packet arrival can show as a
slight per-tick judder during a move. Every move
number (timing, ranges, cooldowns, weights, damage, hit boxes, lunges,
knockback, immune windows) and the attributes live only in
`src/main/resources/data/myvillage/beast/demon_wolf.json`; the server runs from
it and the animation generator reads it, so a retune is a data edit plus a
rebuild. The model, clips, texture, and glow layer are generated by
`tools/beastgen`; never edit them by hand. How the framework works and how to
add a second beast: `docs/ai-kb/38_hostile_beasts.md`.

Operator debug commands (permission 2) for checks and stills:

```mcfunction
/myvillage beast move @e[type=myvillage:demon_wolf,limit=1,sort=nearest] myvillage:demon_wolf_pounce
/myvillage beast status @e[type=myvillage:demon_wolf]
/myvillage beast debug on
/myvillage beast debug off
```

`move` starts the named move on the next AI step, ignoring range, cooldown,
and gap (aimed at the current target, or straight ahead without one);
`status` prints each selected beast's move, move tick, phase, whether it
resists stagger, its aim, cooldowns, and target distance; `debug on` writes
`BEAST_DEBUG` lines for move starts, ends, staggers, and hits to the server
log. With `/tick freeze` and `/tick step` the client shows the pose of the
server's exact move tick.

Generator, offline previews, and headless in-game evidence:

```bash
python3 -m tools.beastgen build demon_wolf            # rewrite the four generated files
python3 -m tools.beastgen build demon_wolf --check    # fail when they differ (release gate)
python3 -m tools.beastgen preview demon_wolf          # out/preview/demon_wolf/ (numpy/Pillow via .venv-preview)
python3 -m tools.combat_capture beast                 # out/preview/demon_wolf/ingame/
python3 -m tools.combat_capture beast --parts fight,dodge   # re-run some parts
```

`preview` (sheets, GIFs, and for the pounce `clip_pounce_arc.png` and
`clip_pounce_landing.png`, with the beast moved along the server's path) is
described in `tools/beastgen/README.md`. The capture has six parts: `idle`
(four sides and a scale still), `moves` (exact-tick stills of every move, plus
landing stills for a move that leaves the ground), `locomotion` (walk and run
videos), `fight` (60 s against a survival player with the Qingfeng sword, no
armour or effects, natural regeneration off, with a per-beast table of moves,
hits, damage, and staggers inside and outside immune windows), `dodge` (slowed
trials: standing still against strafing for the bite, one early swing against
every move), and `slowmo` (every move at a slowed tick rate, server ticks
polled against video time). `tools/combat_capture/README.md` has the details.
It uses the same host programs, session, and heavy-work lock as the weapon
captures; its output is developer evidence and records no owner verdict.

Automated checks:

```bash
python3 tools/validate_custom_entities.py
/usr/bin/python3 -m unittest tools.tests.test_validate_custom_entities tools.tests.test_beastgen tools.tests.test_beast_capture
./gradlew test
./gradlew build
```

Developer evidence from the headless capture on the build host (not owner
acceptance): offline previews in `out/preview/demon_wolf/`, in-game stills,
videos, `fight_log.txt`, and `manifest.json` in
`out/preview/demon_wolf/ingame/`. In the 60 s fight, 35 player hits landed
inside an immune window and staggered nothing; of 36 outside, 20 staggered the
wolf and 17 of those cancelled a move. A player who stood still and traded
took the pounce and two bites per wolf before killing it, about 14 of 20
health on Normal, with no deaths. Strafing during the bite's wind-up, from its
start or from the turn lock, avoided the bite. Stepping away from a pounce
after its lock was not measured. The pounce's landing hung about 0.6 blocks in
the air before the one-step position update
(`out/preview/demon_wolf/ingame_lerp_before/`).

Nothing below has been observed by the owner on a physical client.

| Demon Wolf (0.33.0) real-client acceptance surface | Result |
|---|---|
| `/summon` and the spawn egg create the wolf; egg place in the creative tab; English and Chinese names | `not_verified` |
| Size beside the player, model, texture, and the glowing eyes, mane edges, and tail tip by day and at night | `not_verified` |
| Idle, walk, and run clips; feet do not slide while walking or running | `not_verified` |
| Bite and pounce telegraphs read early enough to react; sideways movement avoids the bite; stepping away after the lock makes the pounce miss | `not_verified` |
| How the fight feels with the Qingfeng sword and the Lingxiao spear: immune windows, stagger flinch, recovery as the punish window | `not_verified` |
| Bite and pounce damage, armour and shield, and knockback on the player | `not_verified` |
| Sounds: ambient and wind-up growls, hurt, death, and steps at the lowered pitch (the build host has no audio device) | `not_verified` |
| Death and no drop | `not_verified` |
| Save and reload keep the wolf; it does not despawn by distance | `not_verified` |
| `F3+T` reload picks up regenerated model, animation, and texture files | `not_verified` |
| Multiplayer: a second client sees the same moves, clips, and staggers in sync | `not_verified` |
| Moves on a real network: the pounce lands on the ground, and the per-tick judder from uneven packets is acceptable | `not_verified` |
| `/myvillage beast move`, `status`, and `debug` from a real client | `not_verified` |
| Frame rate with several wolves on a real GPU | `not_verified` |
| Owner verdict on the wolf's look and its fight | `not_verified` |

## Cultivator NPC (修仙者)

`myvillage:cultivator` is the first humanoid NPC (0.34.0): a sect disciple
about 1.9 blocks tall in a pale robe under a long sleeveless indigo vest, hair
tied in a bun under a small crown. It has no disposition yet: it stands,
strolls, and looks at nearby players, and never attacks; friend or foe is a
later decision. It appears in any difficulty, does not despawn by distance,
and cannot be leashed. There is no natural spawning and no drop: summon it or
use the spawn egg (in `myvillage:main` after the demon wolf egg). Since 0.35.0 the
world simulation also projects ledger members as cultivator avatars on a
built sect compound (see [World Simulation](#world-simulation-世界模拟--命簿)).

```mcfunction
/summon myvillage:cultivator ~ ~ ~
/give @s myvillage:cultivator_spawn_egg
```

It does not use the player model or a skin. Its model is authored at half the
vanilla unit (the model file's `scale` is 0.5, so 32 texels per block) and is
built from layers that really stand apart: a crossed collar on the chest, a
vest open down the front with border bands and sloped shoulder caps, a belt
with a buckle, sash ends and a jade pendant, sleeves that deepen to an open
cuff with a hanging drape, a two-tier skirt under the vest's panels, and a
cut-out hair shell with a bun, crown, pin, ribbons, and loose strands. The
head (rebuilt in 0.34.1) is a cranium over a jaw that narrows in three steps
toward the chin, so the face is taller than wide, under an open forehead; the
brow lies directly on a one-row eye, and there is no mark between the brows.
The texture is painted per texel from its place on the model (rounded form light,
shadows cast by the layer above, folds, dye toward cuffs and hem, woven bands,
trim). The model, clips, and texture are generated by `tools/npcgen`; never
edit them by hand. How the framework works, how to add a second NPC or a
recolour, and what a friend or foe design would build on:
`docs/ai-kb/39_humanoid_npcs.md`.

Generator, offline previews, and headless in-game evidence:

```bash
python3 -m tools.npcgen build cultivator            # rewrite the three generated files
python3 -m tools.npcgen build cultivator --check    # fail when they differ (release gate)
python3 -m tools.npcgen preview cultivator          # out/preview/cultivator/ (numpy/Pillow via .venv-preview)
python3 -m tools.npcgen preview cultivator --only face   # face.png only: the head from six angles, large and at in-game size
python3 -m tools.combat_capture npc                 # out/preview/cultivator/ingame/
python3 -m tools.combat_capture npc --parts idle    # stills only
```

`preview` writes a six-view turnaround, close-ups, a face sheet, a scale
image beside the vanilla player, the atlas, walk and idle sheets, and GIFs
(`tools/npcgen/README.md`). It draws without back-face culling, as the game
does for NPCs, so the inside of the hair shell shows through its cut-outs. The capture has two parts: `idle` (stills from four
sides with the world frozen, close-ups of the face, collar, belt, sleeve, hem,
and back panel, and a scale still beside the player) and `walk` (two NPCs
strolling in a barrier pen, filmed from the front and from the end). It uses
the same host programs, session, and heavy-work lock as the other captures;
its output is developer evidence and records no owner verdict.

Automated checks:

```bash
python3 tools/validate_custom_entities.py
/usr/bin/python3 -m unittest tools.tests.test_validate_custom_entities tools.tests.test_npcgen tools.tests.test_npc_capture
./gradlew test
./gradlew build
```

Developer evidence from the build host (not owner acceptance): offline
previews in `out/preview/cultivator/` and in-game stills and walk videos in
`out/preview/cultivator/ingame/`. In the headless capture the cultivator drew
at the intended size beside the player, the vest opening and the hair
cut-outs showed the layers under them as in the offline preview, and the NPCs
strolled with the walk clip.

The owner observed 0.34.0 on their own PC on 2026-10-06:
"刚刚的修仙者验证90%通过了。服饰、背身、背后看那些姿势、走路的动作都没有问题。唯一的问题是那个脸有点丑，他给我的感觉有点像大佛的脸，没有修仙者那种俊朗英气。"
The rows below record that verdict. 0.34.1 changes only
the head (face geometry, hair cut-outs round the face, face paint); its face
has not been observed, and every surface the owner did not speak to stays
`not_verified`.

| Cultivator NPC real-client acceptance surface (0.34.0 owner verdict; 0.34.1 face) | Result |
|---|---|
| `/summon` and the spawn egg create the cultivator; egg place in the creative tab; English and Chinese names | `not_verified` |
| Clothes (服饰) and the back view (背身) on 0.34.0 | `pass` (owner, 2026-10-06, own PC: "服饰、背身……都没有问题") |
| Poses seen from behind (背后看那些姿势) on 0.34.0 | `pass` (owner, 2026-10-06, own PC: "背后看那些姿势……都没有问题") |
| Size beside the player; the model reads as layered from the front, sides, and three-quarter views | `not_verified` |
| 0.34.0 face reads as a handsome, spirited cultivator | `fail` (owner, 2026-10-06, own PC: "那个脸有点丑……有点像大佛的脸，没有修仙者那种俊朗英气") |
| 0.34.1 face (tapering jaw, open forehead, brow on the eye, no mark) up close and at ten blocks | `not_verified` |
| Hair and head ornaments up close and at ten blocks | `not_verified` |
| Texture by day, at night, and indoors: shading, folds, dye, woven bands, and trim | `not_verified` |
| No flicker where layers overlap (collar, vest opening, belt, panels) up close and at a distance | `not_verified` |
| Idle: breathing, hair, ribbons, sash, and pendant | `not_verified` |
| Walk motion (走路的动作) on 0.34.0; 0.34.1 does not change the clips | `pass` (owner, 2026-10-06, own PC: "走路的动作都没有问题") |
| Walk details: feet do not slide, the skirt does not come through the vest panels, hair and ribbons trail | `not_verified` |
| Head follows a nearby player | `not_verified` |
| It never attacks or retaliates; hurt and death look right; no drop | `not_verified` |
| Save and reload keep it; it does not despawn by distance; it stays in Peaceful | `not_verified` |
| `F3+T` reload picks up regenerated model, animation, and texture files | `not_verified` |
| Multiplayer: a second client sees the same NPC and motion | `not_verified` |
| Frame rate with several cultivators on a real GPU | `not_verified` |
| Owner verdict on the 0.34.1 cultivator's look as a whole | `not_verified` |

### Looks (0.41.0)

The one entity type `myvillage:cultivator` now wears one of three looks
(`docs/female-cultivator-brief.md`), each its own model, clips, and texture
on the same body bones and hitbox:

| Look | Who | Design |
|---|---|---|
| `default` | the male disciple described above | unchanged |
| `f_novice` | 女·刚入门, a woman just through the door | low ponytail tied with a ribbon, a plain waist-high ru skirt (襦裙) under a half-sleeved jacket (半臂) |
| `f_adept` | 女·小有所成, a woman who has made her name | high bun with a 步摇 hairpin, a wide-sleeved coat, a 披帛 drape, a two-layer skirt; palette A (crimson-purple coat, moon-white skirt, gold) |

```mcfunction
/summon myvillage:cultivator ~ ~ ~ {Look:"f_novice"}
/summon myvillage:cultivator ~ ~ ~ {Look:"f_adept"}
```

The look is synced to clients and saved as `Look` only when it is not
`default`; a name the type does not list is ignored. A spawn egg picks one
of the three at random. A world-ledger avatar wears the look of its person:
a woman at or below Qi Refining `f_novice`, above it `f_adept`, a man
`default`.

Each look is one `tools/npcgen` definition (`defs/cultivator.py`,
`defs/cultivator_f_novice.py`, `defs/cultivator_f_adept.py`) writing
`npc/cultivator[_<look>]_model.json`, `..._animations.json`, and
`textures/entity/cultivator/cultivator[_<look>].png`; the contract's
`looks:` list names them and `tools/validate_custom_entities.py` checks each.

```bash
python3 -m tools.npcgen build cultivator_f_novice [--check]
python3 -m tools.npcgen build cultivator_f_adept [--check]
python3 -m tools.npcgen preview cultivator_f_novice [--only face]   # out/preview/cultivator_f_novice/
python3 -m tools.npcgen preview cultivator_f_adept [--only face]    # out/preview/cultivator_f_adept/
python3 -m tools.combat_capture npc --look f_novice                 # out/preview/cultivator/ingame_f_novice/
python3 -m tools.combat_capture npc --look f_adept                  # out/preview/cultivator/ingame_f_adept/
python3 tools/npc_looks_page.py                                     # out/preview/cultivator/looks/index.html
```

The comparison page sets the three looks side by side: offline turnaround,
face sheet, close-ups and walk GIFs, then the headless stills from four
sides, close-ups, and walk videos (a missing file shows as 未采集).

| Cultivator looks (0.41.0) real-client acceptance surface | Result |
|---|---|
| `f_novice` look: reads as a woman and a novice, plain and old-style, from the front, sides, and three-quarter views | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_novice` face up close and at ten blocks | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_novice` walk and idle (hip sway, folded hands, ponytail and skirt ties trailing) | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_novice` name tag height on an avatar | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` look: reads as a woman of standing, richer than the novice, from the front, sides, and three-quarter views | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` face up close and at ten blocks | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` walk and idle (drape, hairpin pendant, back hair trailing) | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| `f_adept` name tag height on an avatar | `not_verified` (headless capture done: `ingame_<look>/`, `looks/index.html`; owner verdict pending) |
| Spawn egg random look; `/summon` with `Look`; look kept across save and reload | `not_verified` |
| Avatars wear the look of their person's gender and realm | `not_verified` |
| Owner verdict on palette A, the low ponytail, and both faces | `not_verified` |

## World Simulation (世界模拟 / 命簿)

Since 0.35.0 every world keeps a ledger (命簿) of sects and named cultivators
who cultivate, break through or fail, travel the regions, find fortunes
(奇遇), make friends and enemies, take revenge, wage sect wars, found and lose
sects, and die, one sim day per cultivation-calendar day, whether or not a
player is near. The ledger is the only authority; avatars (化身) and sect
compounds (山门) are its projections. A sect's gate coordinate is fixed inside
its region when it is founded, whether or not a compound has been built there.

The ledger is created once per world, at the first server start with this
version (old worlds included), after the region runtime: genesis with the
world seed and the configured tier, then a prehistory of
`time.prehistory_years` (in `rules.json`), so the world already has lineages,
grudges, and old sects. Dates are 启元 N 年 (启元前 N
年 for the prehistory); a fresh world shows the same year as the cultivation
calendar. The ledger advances whenever the calendar does, which is on every
tick with at least one player online in any game mode, so it also runs while
testing in creative. If the region runtime or the world-sim data fails to
load, or the save cannot be read, the ledger is inactive for the session, the
commands say why, and the save is left untouched.

Commands (permission 2):

```mcfunction
/myvillage world                 # same as `world info`
/myvillage world info            # era date, sim and calendar day, pending days, paused?, population, realms, sects, foremost people
/myvillage world sects [all]     # active sects (all: with the destroyed): region, master, members, top realm, prestige, gate
/myvillage world sect <id|name>  # one sect: founding, master, resources, prestige, signature technique, heritage (传承), gate, relations, members at the sect
/myvillage world sect <id> build        # build the sect's compound (山门) at its ledger gate
/myvillage world sect <id> build here   # move the sect's gate to you first, then build at your feet
/myvillage world sect <id> join <player>    # 0.41.0: admin join, skipping the admission rules
/myvillage world sect <id> leave <player>   # 0.41.0: the player leaves that sect (penalty and cooldown)
/myvillage world player <player>            # 0.41.0: a player's sect record and standings
/myvillage world gates [retry]              # 0.41.0: framed gate builder state; retry gates given up
/myvillage world sect <id> rank <player> <outer|inner|elder>   # 0.42.0: set a member's rank
/myvillage world sect <id> shelves [place]   # 0.42.0: scripture shelves of a built compound; place them again
/myvillage world sect <a> war <b>             # 0.44.0: admin: sect a declares war on sect b now
/myvillage world sect <id> destroy            # 0.44.0: admin: the sect is destroyed now
/myvillage world person <name>   # up to five people whose name or Daoist title contains the text, living first
/myvillage world chronicle [1-50]   # the latest notable and major events (default 10)
/myvillage world here            # your region: tier, qi, danger, sects seated there with gate distance, people present, recent events
/myvillage world pause           # stop settlement (saved); the calendar keeps running
/myvillage world resume          # restart settlement; the paused days are not caught up
/myvillage world advance <1-3650>   # settle that many days now, even while paused
```

Major events (importance 3) also reach every online player as chat rumors
(江湖传闻), players in the event's region first; notable ones (importance 2)
reach only the players in that region. Each player gets at most
`rumors_per_minute`; the rest wait in a short queue.

In game, every player (no permission needed) can read the ledger on the H
panel's 天下 page (0.36.0; see [Cultivation Playable Loop](#cultivation-playable-loop)):
the overview, the sect list and each sect, a person search and each person,
the chronicle, and the region you stand in, with rows that open the sect or
person they name. It shows what `info`, `sects`, `sect`, `person`,
`chronicle`, and `here` show, without the admin actions. The client asks the
server one bounded, read-only query at a time (the same query at most every
2.5 s; the server answers a player at most once every 4 ticks) and gets back a
capped snapshot with names already resolved; the payload protocol (`10` since
0.37.0) must match, so the client and server need the same jar. Lists are capped (96 sects, 24 members,
10 search matches, 10 people present, 40 chronicle lines, 10 recent events).

Server config `config/myvillage-world_sim-server.toml` (a copy in a world's
`serverconfig/` overrides it):

| Key | Default | Meaning |
|---|---|---|
| `world_sim.tier` | `small` | `small` (about 80 people), `medium` (about 300), or `large` (about 1000); read only at genesis, then fixed in the save |
| `world_sim.catch_up_cap_days` | `30` | Most sim days that may be pending at once (one is settled per tick); `rules.json` `scheduler.max_pending_days` is a ceiling this can only lower |
| `rumors.rumors_enabled` | `true` | Rumors in chat on or off |
| `rumors.rumors_per_minute` | `2` | Most rumors per player per real minute |
| `rumors.rumor_queue_cap` | `6` | Most rumors waiting per player; the oldest is dropped |
| `avatars.avatars_enabled` | `true` | Avatars on or off |
| `avatars.avatar_spawn_radius` | `64` | Avatars appear while a player is within this many blocks (8–128) of a built compound's site, and are withdrawn beyond it plus 32 |
| `avatars.max_avatars_per_sect` | `12` | Most avatars per compound (0–64): the master first, then the elders, then by realm |
| `avatars.max_avatars` | `40` | Most avatars in the world (0–256); the compound nearest a player is served first |

Every balance number, name, technique, artifact, site, beast, and fortune is
data in `src/main/resources/data/myvillage/world_sim/` (`rules.json`,
`realms.json`, `encounters.json`, `names.json`, `techniques.json`,
`lore.json`, and since 0.37.0 `heritages.json`; `techniques.json` and
`heritages.json` are generated by `tools/gen_technique_catalogue.py`), and every sentence is a language key in both language files.
How it works: `docs/ai-kb/40_world_sim.md`.

Offline chronicle (no Gradle, no Minecraft; the pure core is compiled with
`javac`) and the report pages:

```bash
/usr/bin/python3 tools/world_sim_cli.py run --seed 1 --tier small --years 300 \
    --out out/preview/world_sim/seed1.json --text out/preview/world_sim/seed1.txt
/usr/bin/python3 tools/world_sim_report.py --runs small:1,2,3 medium:1,2,3 --years 300
```

The report writes `out/preview/world_sim/<tier>_<seed>.html` and
`index.html`: population and realm charts, a sect timeline, biographies, the
chronicle, and the roll of the dead. Public preview:
`http://43.156.135.198:8766/world_sim/index.html`.

Checks and headless evidence:

```bash
/usr/bin/python3 tools/validate_world_sim.py
/usr/bin/python3 -m unittest tools.tests.test_validate_world_sim tools.tests.test_world_sim_report
./gradlew test --tests 'com.example.myvillage.sim.*'
python3 tools/world_sim_evidence.py --part all    # out/preview/world_sim/evidence/ (holds the heavy-work lock itself)
```

The evidence script runs a headless server and client in a fresh superflat
world with a sped-up calendar (creative mode, every command over RCON, pause
and resume, rumors and their rate limit) and then restarts a kept world
(ledger kept, genesis once, a later tier change ignored). Its 2026-10-06 run
passed all 17 checks (`summary.md`); that is developer evidence, not owner
acceptance.

### Sect compounds and avatars (化身)

A ledger sect has no compound in the world until one is built for it (worldgen
does not place compounds at ledger gates yet; the randomly scattered
`myvillage:sect` compounds are unrelated to the ledger and stay empty).
`/myvillage world sect <id> build` loads the gate's chunk and builds the
worldgen-style compound (derived mountain, terraces, buildings) on the surface
at the ledger's gate coordinate; `build here` first moves the sect's gate (and
its seat) to your position, which must be inside a region, and builds at your
feet. The compound's seed and spire variant come from the world seed and the
sect id, so a rebuild at the same place is identical. The build runs on the
server thread and the server does not respond until it is done (the reply
warns; it can take a minute). The gate is then marked realized, and the build
is recorded in `data/myvillage_world_sim_gates.dat`.

While a player is within `avatar_spawn_radius` of a built compound, the
sect's members who are at the sect in the ledger stand on its open courtyard
ground as cultivators named 姓名 · 境界 · 宗门 (the realm in the client's
language): the master first, then the elders, then by realm, up to the caps,
one entity per person, at least two blocks apart. They look around and at
players but do not walk, cannot be hurt (only damage that bypasses
invulnerability, such as `/kill` or the void, reaches them), do not burn, cannot be pushed, and do nothing when used (since 0.41.0 the gate
steward and the elders talk; see [Player sect entry](#player-sect-entry-0410)). They are
never saved: when every player has moved away they are withdrawn, and on the
way back new ones are projected from the ledger. After each settled day the
set follows the ledger (people who left, died, or went travelling disappear;
arrivals appear). A cultivator from `/summon` or the spawn egg is unchanged.

```bash
python3 tools/world_sim_avatar_evidence.py    # out/preview/world_sim/avatars/ (holds the heavy-work lock itself)
```

It builds one sect's compound with `build here` in a fresh superflat world
and checks the avatars on the courtyard, their withdrawal 300 blocks away
(with the chunks force-loaded), their return, `advance 60`, and damage on an
avatar and on a summoned cultivator. Its 2026-10-06 run passed all 19 checks
(`index.html`, screenshots); developer evidence, not owner acceptance.

| World simulation (0.35.0) acceptance surface | Result |
|---|---|
| Pure-core tests (determinism, save/load, scheduler, purity, lifespan agreement, text keys, health bands, performance) | `pass` (Gradle) |
| Every `/myvillage world` command on a headless server | `pass` |
| Calendar and ledger both advance in creative mode | `pass` |
| Pause holds the ledger while the calendar runs; resume does not catch up | `pass` |
| Restart keeps the ledger; genesis once; later config tier change ignored | `pass` |
| `advance` bounds | `pass` |
| Rumors reach an online player, rate-limited, own region first | `pass` (headless; own-region priority unit-tested only) |
| Chinese chronicle/rumor text on a zh_cn client | `not_verified` |
| Sim inactive when the region runtime failed to load | `not_verified` |
| Newer-format save left untouched | `not_verified` in game (unit-tested) |
| Unreadable save left untouched | `not_verified` |
| Genesis on an existing old world | `not_verified` in game |
| How rumors look in chat on a physical client | `not_verified` |
| Named avatars with realm stand on the courtyard ground of a built compound | `pass` (headless) |
| Avatar count near the compound, zero far away, back on return | `pass` |
| Avatars follow `advance` (left and arrived members) | `pass` |
| No duplicate avatars | `pass` |
| Avatars invulnerable; a `/summon` cultivator unchanged | `pass` |
| Avatar look, motion, and name-tag legibility on a physical client | `not_verified` |
| Chinese avatar name tags on a zh_cn client | `not_verified` |
| Avatars in multiplayer | `not_verified` |
| `sect <id> build` without `here` on real terrain far away (only `build here` ran; a long synchronous build may trip a production server's max-tick-time watchdog) | `not_verified` |
| The gate record surviving a restart in game (save round trip unit-tested only) | `not_verified` |

The 天下 page was checked on the owner's PC on 2026-10-06 (real GPU, Chinese
client, singleplayer world `agent-test`, GUI 534x300 at scale 3, then 480x270,
427x240, and 320x240 by resizing the window; stills in
`out/preview/world_sim_panel/pc/`). That is developer evidence, not the
owner's verdict.

| 天下 page (0.36.0) surface | Result |
|---|---|
| Query/snapshot codec, snapshot builder per kind and caps, server throttle, page readouts | `pass` (Gradle) |
| 天下 tab opens with ledger data in Chinese: 总览, 宗门, 人物, 纪事, 此地 | `pass` (owner's PC) |
| 人物 search updates as you type | `pass` (owner's PC) |
| 纪事 newest first with cause lines | `pass` (owner's PC) |
| Drilling into a sect and a person, and back | `pass` (owner's PC) |
| Hover highlight on clickable rows | `pass` (owner's PC) |
| One-column cards and the two-row dock below 300 wide (GUI 320x240); layout at 534x300, 480x270, 427x240 | `pass` (owner's PC, Chinese) |
| English text at narrow widths | `not_verified` |
| The ledger-inactive card on a real client | `not_verified` (unit-tested) |
| The outside-every-region card on 此地 | `not_verified` |
| `H` typed into the search field does not close the panel | `not_verified` |
| Multiplayer: several players querying, per-player throttle | `not_verified` (throttle unit-tested) |
| Hover, click, and wheel feel on a physical mouse | `not_verified` |
| The owner's verdict on the page's look and amount of information | `not_verified` |

### Player sect entry (0.41.0)

Slice 1 of 拜入宗门 (`docs/player-sect-entry-brief.md`): the player gets a
record in the ledger and can join a sect at its gate, leave it, and rise by
the yearly review. The player is never a ledger person: they do not count
as a sect's people, resources, or prestige, and the engine never moves them.
The scripture hall (slice 2) followed in 0.42.0 ([Scripture hall](#scripture-hall-0420)),
sect tasks and masters (slice 3) in 0.43.0
([Sect tasks and apprenticeship](#sect-tasks-and-apprenticeship-0430)), and
the world's response (slice 4) in 0.44.0 ([World response](#world-response-0440)).

A walk through it:

1. On the H panel's 天下 page, 此地 lists the sects seated in your region
   with the distance to each gate and its bearing (北, 东北, ...).
2. Walk toward a gate. When you come within `gates.realize_radius` blocks
   (planar) of an active sect whose gate is not built, the compound is built
   for you, a few chunks per tick, one compound at a time (nearest first),
   while avatars are enabled. Players within that radius read
   "…的山门正在显形……" and then "…的山门已然落成。" The build goes on if
   you walk away. `world sect <id> build` still works and is refused while
   the same gate is being built this way.
3. Within `avatar_spawn_radius` the avatars appear as before. The gate
   steward (守山执事) is the member at the sect of the lowest rank (outer,
   inner, elder, master) and then the lowest id; it is always shown first,
   stands on the lowest terrace on the free cell nearest the axis and the
   gate opening, and its name tag has a fourth part: 姓名 · 境界 · 宗门 · 守山执事.
4. Right-click the steward (within `steward.interact_range`). The dialogue
   screen shows a greeting, the sect's prestige, people at the gate and
   master, and either an invitation (拜入 / 告辞) or the reason you are
   turned away. Elders and the master also talk (a greeting; to a
   non-member, a pointer to the steward) but take no requests. Other
   avatars still do nothing when used.
5. 拜入: you become an outer disciple (外门弟子), with a chat line, a
   chronicle line ("…拜入…，为外门弟子。"), and a 我的宗门 card on the 天下
   overview (sect, rank, joining date, master, contribution, standing). Your
   own sect's page shows your rank, joining date, and standing.
6. At the start of each sim year your rank rises one step (inner, then
   elder; never master) if your realm meets the `promotion` bar, with a chat
   and chronicle line. Negative standings recover toward 0. If your sect is
   destroyed you become a rogue cultivator without a penalty.
7. To leave, talk to the steward again and pick 退宗. Your standing with
   that sect drops by `leave.standing_penalty`, and you cannot rejoin it for
   `leave.rejoin_years` nor while that standing is below
   `leave.rejoin_standing_at_least`. Other sects are open to you.

Admission is checked in this order, and the dialogue states the first
refusal: `sect_inactive`, `already_member` / `member_elsewhere`,
`rejoin_cooldown`, `standing_too_low`, `not_awakened`, `realm_too_low`,
`selective`. The server decides everything: the client sends only the chosen
option (JOIN, LEAVE, FAREWELL) with the avatar's entity id and the sect id,
and the server checks the level, the distance, the avatar's role and its
sect again before changing the ledger. Payload protocol is `13` (`14` since
0.42.0, `15` since 0.43.0), so client and server need the same jar. Ledger saves gain the optional
`player_members` (payload version 3); older saves load with no players.

The numbers are data, the `player` section of
`src/main/resources/data/myvillage/world_sim/rules.json`:

| Key | Meaning |
|---|---|
| `admission.require_awakened_root` | A spiritual root must be awakened to join |
| `admission.min_realm`, `admission.min_stage` | Lowest realm (player registry path; `mortal` is below every ledger realm) and 0-based stage to join |
| `admission.join_standing` | Standing (交情) gained with the sect on joining |
| `admission.selective.prestige_at_least` | Sects at or above this prestige are selective |
| `admission.selective.root_peak_bp_at_least`, `admission.selective.or_min_realm` | What a selective sect wants: a highest single-element root affinity of at least this many basis points, or at least this realm |
| `leave.standing_penalty` | Standing change on leaving |
| `leave.rejoin_years` | Years before the same sect takes you back |
| `leave.rejoin_standing_at_least` | Least standing a sect needs to take you |
| `leave.standing_recovery_per_year` | Yearly recovery of each negative standing toward 0 |
| `promotion.inner`, `promotion.elder` | Realm, 0-based stage, and contribution for the yearly rise to inner disciple and to elder |
| `scripture_hall.borrow_cost_by_grade` | Read, not used until the scripture hall (slice 2) |
| `steward.interact_range` | Blocks within which a steward or elder will talk |
| `gates.realize_radius` | Planar blocks from a player at which an unbuilt gate is built |
| `gates.clips_per_tick` | Chunk clips built per server tick |

Commands (permission 2):

| Command | Does |
|---|---|
| `/myvillage world sect <id> join <player>` | Join the player to the sect without the admission rules (the sect must be active and not already theirs; a member elsewhere leaves that sect first, without penalty or event) |
| `/myvillage world sect <id> leave <player>` | The player leaves that sect, exactly as through the steward (penalty and cooldown) |
| `/myvillage world player <player>` | The player's record: sect and rank, joining date, master, contribution, standing with each sect, the sect they last left |
| `/myvillage world gates` | The framed builder's state: `idle`, `building sect <id> clip <i>/<n>`, and the gates given up this session |
| `/myvillage world gates retry` | Let the gates given up this session be tried again |

Log lines (INFO; the evidence script reads them):

```text
GATE_REALIZE sect=<id> state=queued gate=<x> <z> distance=<n> chunks=<n>
GATE_REALIZE sect=<id> state=started anchor=<x> <y> <z> clips=<n> seed=<n> variant=<v> load_ticks=<n> preloaded=<a>/<b>
GATE_REALIZE sect=<id> state=clip <i>/<n>
GATE_REALIZE sect=<id> state=done seconds=<s> clips=<n> blocks~=<n> written=<n>
GATE_REALIZE sect=<id> state=cancelled reason=<server_stopping|ledger_changed|sect_changed|already_realized>
GATE_REALIZE sect=<id> state=failed reason=<exception class>
SECT_DIALOGUE option=<JOIN|LEAVE|FAREWELL> x=<px> y=<px> w=<px> h=<px>
SECT_ENTRY player=<name> intent=<JOIN|LEAVE> sect=<id> result=<ok|reason>
```

`SECT_DIALOGUE` is the client's, one line per button in screen pixels each
time the dialogue lays out; the other two are the server's. A failed build
releases its chunks, leaves the gate unbuilt (blocks already placed stay),
and is not retried until `world gates retry` or a restart. Details:
`docs/ai-kb/43_player_sect_entry.md`.

Headless evidence (fresh superflat world, creative, settlement paused; about
15 to 25 minutes; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_entry_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600] [--skip-promotion]
```

It walks the player 140 blocks from an unbuilt gate and waits for
`GATE_REALIZE ... state=done`, finds the steward, right-clicks it and clicks
JOIN, reads `world player` and the chronicle, photographs the 我的宗门 card,
advances a year below and at the promotion bar, leaves and tries to rejoin,
and runs the admin join and leave. Output in `out/preview/world_sim/entry/`
(`index.html`, `evidence.json`, `commands.txt`, `server_log.txt`,
`client_log.txt`, screenshots). Developer evidence, not owner acceptance.

Its 2026-10-07 run (capture script of `225c5b3`) passed all 20 checks: the
gate 140 blocks away built itself in 3.9 s (135 chunk clips) while `tick
query` read 7.1 ms per tick; the steward stood with its 守山执事 tag; JOIN
through the dialogue made the player an outer disciple of 玄黄阁 with a
chronicle line and the 我的宗门 card; a mortal was not promoted after a year,
and at 炼气五层 the next yearly review made them an inner disciple; LEAVE
succeeded and, within the cooldown, the steward offered no JOIN button; the
admin join went past the cooldown and the admin leave worked. Not captured:
bearings, `world gates`, promotion to elder, a destroyed sect.

| Player sect entry (0.41.0) acceptance surface | Result | Notes |
|---|---|---|
| Auto gate realization near a player (framed, no server stall) | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `gate_realized_automatically`: player 140 blocks from the gate, `GATE_REALIZE` started→done 3.9 s, 135 chunk clips; `server_responsive_while_building`: `tick query` 7.1 ms/tick during the build; `gate_realized_in_ledger`: `world sect` shows (built); `gate_far.png`. Stutter on a real client not judged |
| Steward name tag (four parts, 守山执事) and its place by the gate | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `steward_present` (name tag with the 守山执事 part), `player_faces_steward`; `steward_nameplate.png` |
| Dialogue screen: lines, buttons, refresh in place, closing | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `dialogue_opens_with_join` (options JOIN, FAREWELL), `dialogue_offers_leave` for a member; `dialogue_open.png`, `dialogue_member.png`; the welcome and farewell pages after JOIN and LEAVE (`dialogue_welcome.png`, `dialogue_left.png`). Look and readability on a physical client not judged |
| Join through the steward (outer disciple, chat and chronicle line) | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `join_ok` (`SECT_ENTRY ... intent=JOIN ... result=ok`), `player_record_outer` (`world player`: outer disciple of 玄黄阁, standing +20), `chronicle_has_join`; `dialogue_welcome.png` |
| Leave through the steward (standing penalty) | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `leave_ok` (`intent=LEAVE result=ok`); `dialogue_left.png` |
| Rejoin cooldown refusal | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `rejoin_refused_cooldown`: within the cooldown the dialogue offers only FAREWELL (no JOIN button, so no `result=rejoin_cooldown` line; the refusal line is in `dialogue_rejoin_refused.png`) |
| Admin commands `world sect <id> join/leave <player>`, `world player`, `world gates` | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `admin_join_forced` (join past the cooldown, `result=ok`), `admin_join_record`, `admin_leave`; `world player` read throughout. `world gates [retry]` not exercised |
| 我的宗门 card and the own-sect rank row on the 天下 page | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `panel_after_join.png` shows the 我的宗门 card with 玄黄阁 / 外门弟子 (screenshot only, no log check); the own-sect row not captured |
| Gate bearings on 此地 | `not_verified` | headless: not captured (unit-tested only, `BearingTest`) |
| Yearly promotion to inner disciple and elder | `not_verified` | headless (`world_sim_entry_evidence`, 20/20 checks): `no_promotion_below_threshold` (a mortal stays outer after a year), `promoted_inner_at_threshold` (realm set to 炼气五层, one day advanced to refresh the snapshot, then a year: `player.promote.inner`, `world player` shows inner; that world has 6 days a year). Elder not captured |
| Sect destroyed → the player becomes a rogue | `not_verified` | headless: not captured (unit-tested only, `PlayerAffairsTest`) |

### Scripture hall (0.42.0)

Slice 2 of 拜入宗门: a sect's members borrow its manuals (秘籍) at the
scripture hall (藏经阁), by rank, one copy of each, free for now.

A walk through it:

1. Every ledger compound built from 0.42.0 on, by `world sect <id> build` or
   automatically near a player, gets a scripture shelf (经架,
   `myvillage:scripture_shelf`) on the hall floor of each of its two
   scripture pavilions. Compounds built earlier get them with
   `world sect <id> shelves place`. Random worldgen compounds have none.
2. Join the sect at its gate (see [Player sect entry](#player-sect-entry-0410)),
   walk into a pavilion, and use the shelf with an empty hand (within
   `steward.interact_range`).
3. The hall lists what your rank may borrow: a sect with a heritage (传承)
   lends its chain, the first technique to an outer disciple, the first two
   to an inner disciple, the whole chain to an elder; a sect without one
   lends its basic technique, and to inner disciples and elders also its
   signature technique. Mortal-grade breathing has no manual; the hall says
   to take it from the inheritance stele.
4. 借阅 gives you the manual (dropped at your feet if your inventory is
   full) and a chat line, records the borrow in the ledger (a chronicle
   line "…自…藏经阁借得《…》。"), and greys the row out as 已借. Each
   technique can be borrowed once; a lost manual can be replaced only by
   the admin `manual` command. Read it as any manual (盘坐研读).
5. A rise in rank (the yearly review, or `world sect <id> rank`) opens more
   of the chain. Anyone who is not of the sect sees only the refusal; an
   unowned shelf (placed by hand from the creative tab) says "此架无主".

The client sends only the shelf position and the technique id; the server
checks the level, the distance, the shelf's owner, the sect, the membership,
the list, and the manual before it records the borrow. Payload protocol is
`14` (`15` since 0.43.0), so client and server need the same jar. The cost per grade is
`player.scripture_hall.borrow_cost_by_grade` in
`src/main/resources/data/myvillage/world_sim/rules.json`; it is shown when
above 0 but nothing is charged yet (contribution, earned by sect tasks since 0.43.0, is not spent).

Commands (permission 2):

| Command | Does |
|---|---|
| `/myvillage world sect <id> rank <player> <outer\|inner\|elder>` | Set a member's rank; a rise records a promotion and tells the player |
| `/myvillage world sect <id> shelves` | The shelf sites of the sect's built compound and the shelf found at each, with its sect id |
| `/myvillage world sect <id> shelves place` | Place (or re-own) the shelves of a compound built before 0.42.0 |

Log lines (INFO):

```text
SCRIPTURE_SHELF sect=<id> placed=<n>/<m> at=<x y z;...>
SCRIPTURE_HALL player=<name> intent=OPEN sect=<id> member=<true|false> entries=<n>
SCRIPTURE_HALL player=<name> intent=BORROW sect=<id> technique=<id> result=<ok|reason>
SCRIPTURE_HALL_UI technique=<id> borrowed=<true|false> x=<px> y=<px> w=<px> h=<px>
```

The first three are the server's; `SCRIPTURE_HALL_UI` is the client's, one
line per borrow button in screen pixels each time the hall lays out. Borrow
refusals: `not_member`, `member_elsewhere`, `not_borrowable`,
`already_borrowed`, `no_manual`, `inactive`, `too_far`, `unowned`. Details:
`docs/ai-kb/43_player_sect_entry.md` ("Scripture hall").

Headless evidence (fresh superflat world; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_scripture_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600] [--shelf-distance 3.0]
```

It builds a heritage sect's gate by walking near it, checks that both
shelves were placed, joins by admin command, opens the hall as an outer
disciple, borrows the first manual and finds it in the inventory, opens the
hall again (the button disabled), ranks the player inner (a second
technique), and leaves (refused as a non-member). Output in
`out/preview/world_sim/scripture/` (`index.html`, `evidence.json`,
`commands.txt`, `server_log.txt`, `client_log.txt`, screenshots). Developer
evidence, not owner acceptance.

Its 2026-10-07 run (capture script of `23d8ecb`, on the 0.42.0 tree) passed
all 19 checks with 明心宗 (heritage 太白剑脉): both shelves placed, the outer
disciple saw and borrowed `gengjin_yinqi_fa` and held its manual, the
reopened hall showed it borrowed with one copy in the inventory, `rank inner`
added `gengjin_jianjue` (borrowed too), and after `leave` the hall answered
`member=false`. On the same code `world_sim_entry_evidence.py` passed 20/20
and `world_sim_avatar_evidence.py` 19/19.

| Scripture hall (0.42.0) acceptance surface | Result | Notes |
|---|---|---|
| A shelf on the hall floor of both scripture pavilions of a built compound | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `shelves_placed` 2/2 at (553, -35, -805) and (581, -35, -805) after the gate of 明心宗 built itself (started→done 3.3 s, 135 clips, 9.7 ms/tick); `shelves_listed_for_sect` (2 of 2). How the shelf looks in the pavilion not judged |
| Outer disciple's list (first technique of the chain, or the basic technique) | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `hall_opens_for_outer`: one entry, `gengjin_yinqi_fa` (first of 太白剑脉); `hall_outer.png` |
| Borrowing gives the manual into the inventory, chat and chronicle line | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `borrow_ok` (`intent=BORROW ... result=ok`), `manual_in_inventory` (`manual_core_huang` with that technique). Chat and chronicle line not machine-checked |
| A borrowed technique cannot be borrowed again (button disabled, `already_borrowed`) | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `borrowed_button_disabled` (`borrowed=true` on reopening), `no_second_copy` (one manual); `hall_borrowed.png`. The button is not clicked, so no `already_borrowed` line (unit-tested) |
| Inner disciple sees the second technique | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `rank_inner`, `hall_inner_sees_two` (`gengjin_yinqi_fa`, `gengjin_jianjue`), `borrow_second_ok`, `second_manual_in_inventory` (`manual_active_xuan`); `hall_inner.png` |
| A non-member is refused | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): after `admin_leave_ok`, `hall_refuses_non_member` (`member=false entries=0`); `hall_refused.png` |
| Admin commands `world sect <id> rank` and `shelves [place]` | `not_verified` | headless (`world_sim_scripture_evidence`, 19/19 checks): `rank_inner` and `shelves` pass; `shelves place` not exercised |

### Sect tasks and apprenticeship (0.43.0)

Slice 3 of 拜入宗门: the gate steward hands each member one sect task (宗门事务)
a year for contribution, and an inner disciple can take an elder as master
(拜师), whose guidance speeds meditation.

A walk through it:

1. As a member, talk to your sect's steward. Below the greeting it names
   this year's task and its brief, with a 领事务 button. Which task you get
   is fixed for the whole year (drawn from your player id and the year).
2. Do it. 巡山除兽 (patrol): kill beasts while standing in your sect's home
   region; each kill counts (a chat line with the progress). 供奉灵石
   (tribute): have the low-grade spirit stones in your inventory. 传信他宗
   (courier): walk onto the compound site of the sect the brief names (the
   check runs every second; that gate must be built to see anything there).
3. Back at the steward the line says the task is ready, with 交事务. Turning
   it in adds its contribution (a chat line and a chronicle line); a
   tribute's stones are taken then. While a task is not done the steward
   shows the progress instead. After turning in, the steward says there is
   nothing more this year; a new year brings a new offer. An unfinished
   task does not expire at the new year.
4. Once you are an inner disciple or above and have no master, an elder or
   the sect master standing at the gate offers 拜师. The ledger records the
   master (`world player`, the 我的宗门 card, a chronicle line).
5. While your master lives and stays in your sect, meditation progress is
   multiplied by `1 + cultivation.master_guidance` (`rules.json`). If the
   master dies or leaves, you are told that day and the bond ends.
6. The 天下 page's 我的宗门 card shows the open task and its progress.

Tasks are data in `src/main/resources/data/myvillage/world_sim/sect_tasks.json`:

| Field | Meaning |
|---|---|
| `id` | Task id; its name and brief are `world_sim.task.<id>.name` / `.brief` in both language files |
| `kind` | `patrol` (beast kills in the sect's region), `tribute` (low-grade spirit stones handed in), or `courier` (reach another active sect's compound) |
| `count` | Kills, stones, or 1 for a courier |
| `contribution` | Contribution gained on turning it in |

Dialogue options added: 领事务 (`TASK_ACCEPT`) and 交事务 (`TASK_TURN_IN`) at
the steward, 拜师 (`APPRENTICE`) at an elder; as before the client sends only
the option, the avatar, and the sect, and the server judges it. Refusals:
`task_active`, `task_done_this_year`, `no_task`, `not_ready`,
`tribute_short` (stones missing) for tasks; `rank_too_low`, `has_master`,
`master_not_here` for apprenticeship. Payload protocol is `15`, so client
and server need the same jar.

Log lines (server, INFO):

```text
SECT_TASK player=<name> kind=<patrol|courier> progress=<p>/<n>
SECT_ENTRY player=<name> intent=<TASK_ACCEPT|TASK_TURN_IN|APPRENTICE> sect=<id> result=<ok|reason>
SECT_DIALOGUE option=<...|APPRENTICE|TASK_ACCEPT|TASK_TURN_IN> x=<px> y=<px> w=<px> h=<px>   # client
```

Details: `docs/ai-kb/43_player_sect_entry.md` ("Sect tasks and
apprenticeship").

Headless evidence (fresh superflat world; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_tasks_evidence.py [--sect ID] [--courier-tries 6] [--skip-meditation] [--meditation-seconds 10]
```

It joins a sect by admin command and ranks the player inner, takes the
year's task at the steward and does it whichever kind the draw gives
(summoned wolves killed by the player, stones given, or a walk to another
sect's gate), turns it in, checks that no second task is offered, takes an
elder as master, samples meditation without and with the master, and
photographs the panel. Output in `out/preview/world_sim/tasks/`
(`index.html`, `evidence.json`, `commands.txt`, `server_log.txt`,
`client_log.txt`, screenshots). Developer evidence, not owner acceptance.

Its 2026-10-07 run (script `6806fe7`) passed all 21 checks with 明心宗: the
gate built itself in 3.8 s (135 clips, 6.3 ms per tick); after the admin join
and `rank inner` the steward offered the year's task, which the draw made
the tribute (供奉灵石); with 5 stones given the steward offered 交事务, the
turn-in took the 5 stones and raised contribution from 0 to 10, and the
steward then offered no second task; the elder 侯南枝 offered 拜师, which
recorded the master and a chronicle line. Not captured: the patrol and
courier tasks (not drawn), a master's loss, and the meditation factor (both
samples gained no progress). `world_sim_entry_evidence.py` passed 20/20 on
the same code.

| Sect tasks and apprenticeship (0.43.0) acceptance surface | Result | Notes |
|---|---|---|
| Taking the year's task at the steward (领事务) | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `dialogue_offers_task` (options TASK_ACCEPT, LEAVE, FAREWELL; `task_offer.png`), `task_accepted` (`intent=TASK_ACCEPT result=ok`; chronicle "takes a sect task from 明心宗: Spirit Stone Tribute") |
| Patrol progress from beast kills in the sect's region | `not_verified` | headless: not captured (the year's draw was the tribute; unit-tested only) |
| Tribute: stones taken at turn-in | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `tribute_stones_given` (0 → 5), `dialogue_offers_turn_in` (`task_ready.png`), `tribute_taken` (5 → 0) |
| Courier: arrival at the other sect's compound | `not_verified` | headless: not captured (the year's draw was the tribute; unit-tested only) |
| Turning in adds the contribution (交事务) | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `task_turned_in` (`intent=TASK_TURN_IN result=ok`), `contribution_awarded` (0 → 10); `task_done.png` |
| No second task this year | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `no_second_task_this_year` (options LEAVE, FAREWELL only); `task_again.png` |
| Apprenticeship at an elder (拜师) | `not_verified` | headless (`world_sim_tasks_evidence`, 21/21 checks): `dialogue_offers_apprentice` (elder 侯南枝; `apprentice_offer.png`), `apprenticed` (`intent=APPRENTICE result=ok`), `master_recorded` (`world player`: Master 侯南枝), `chronicle_has_apprentice` |
| Losing a master who died or left | `not_verified` | headless: not captured (unit-tested only, `PlayerAffairsTest`, `WorldSimTasksTest`) |
| Meditation gain with a master (about ×1.15) | `not_verified` | headless: not captured (both meditation samples gained 0 progress, so they show nothing; unit-tested only) |
| The task row on the 我的宗门 card | `not_verified` | headless: `panel_task.png` shows the card with contribution, master and task rows (screenshot only, no log check) |

### World response (0.44.0)

Slice 4 of 拜入宗门, the last: what happens to your sect reaches you, and
sects you are at odds with turn you away.

- **Sect news.** After every settled day each online player is told, in
  chat and wherever they stand, every notable or major event (importance 2
  or more) about their sect: wars, a new master, a split, a lost heritage,
  the sect's destruction. A line reads "【宗门】" followed by the event's own
  chronicle line. After leaving a sect (or losing it) you still hear the
  sect events of the one you last left, so the day it is destroyed you are
  told. Your own lines (joining, promotion, tasks, a master) still come as
  before. Players who are offline when it happens are not told later.
- **Hostile gates.** A sect at war with yours turns you away at its gate:
  its steward and elders say so and offer only 告辞. Without a war, a
  standing (交情) with that sect below
  `player.admission.hostile_standing_below` (in
  `src/main/resources/data/myvillage/world_sim/rules.json`) does the same.
  A rogue cultivator is never refused for a war, and your own sect never
  turns you away. A JOIN sent to a hostile steward is refused by the server.
- **Admin acts** (permission 2), for testing and evidence; both go through
  the same ledger paths as the yearly politics and tell online players at
  once:

| Command | Does |
|---|---|
| `/myvillage world sect <a> war <b>` | Sect a declares war on sect b now (the chronicle's war line; both at war). Refused: `no_sect`, `same_sect`, `sect_inactive`, `already_at_war` |
| `/myvillage world sect <id> destroy` | The sect is destroyed now (the ruin line), its people become rogues, its players are released ("…覆灭，…自此成了散修。"), a held heritage is lost; its avatars withdraw on the next pass. Refused: `no_sect`, `sect_inactive` |

Log line (server, INFO), one per news line sent:

```text
SECT_NEWS player=<name> event=<id> type=<type> sects=<[ids]>
```

Details, and an assessment of real P4 (compounds placed by worldgen at
ledger gates, not built): `docs/ai-kb/43_player_sect_entry.md` ("World
response", "Real P4 (worldgen placement) assessment").

Headless evidence (fresh superflat world; holds the heavy-work lock itself):

```bash
python3 tools/world_sim_news_evidence.py [--sect-a ID] [--sect-b ID] [--news-timeout 10] [--withdraw-timeout 10]
```

It joins sect A by admin command, stands at sect B's gate far from A, has A
declare war on B and waits for the news line in the client's chat (prefix
【宗门】, `[Sect]` in English), right-clicks B's steward and checks that only
告辞 is offered, then destroys A and checks that `world player` shows a
rogue, the chronicle and the chat line, and A's avatars withdrawing (about
15 to 30 minutes; two gates are built). Output in
`out/preview/world_sim/news/`. Developer evidence, not owner acceptance.

Its 2026-10-07 run (script `c1d8af3`) passed all 15 checks with A = 玄黄阁
and B = 明心宗: both gates built themselves (4.3 s and 3.4 s); the war news
reached the player 4534 blocks from A's gate at once; 明心宗's steward
offered only 告辞 with the at-war refusal; the destruction made the player a
rogue, wrote the ruin line, sent the two chat lines at once, and A's 12
avatars were gone within 6 s. Not captured: an elder's refusal and the
standing bar. `world_sim_tasks_evidence.py` passed 21/21 on the same code.

| World response (0.44.0) acceptance surface | Result | Notes |
|---|---|---|
| News of the player's own sect reaches them anywhere (【宗门】) | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `sect_news_delivered`: right after `war`, with the player 4534 blocks from 玄黄阁's gate (at 明心宗's), the line "[Sect] 金萝 (sect master 玄黄阁) declares war on 明心宗." (en_us client); `SECT_NEWS` lines for `war`, a following `battle`, and `sect_destroyed`; `news_chat.png`, `news_chat_open.png` |
| A hostile (at war) sect's steward turns the player away | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `b_steward_present`, `hostile_steward_refuses` (明心宗's steward 郑湘灵: client options FAREWELL only, no JOIN), `hostile_reason_at_war` (`steward.refuse.at_war`); `hostile_refused.png`. A standing below the hostile bar is not reachable with the shipped numbers and was not captured |
| A hostile sect's elder turns the player away | `not_verified` | headless: not captured (the script talks only to the steward; unit-tested in `SectDialogueScenesTest`) |
| Admin war `world sect <a> war <b>` | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `war_declared` (玄黄阁 declared war on 明心宗) |
| Admin destruction `world sect <id> destroy` | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `sect_destroyed` (玄黄阁), `chronicle_sect_gone` ("玄黄阁 in 灵岳, last held by 金萝, falls apart.") |
| The player becomes a rogue with a chat line when the sect is destroyed | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `player_now_rogue` (`world player`: rogue cultivator, left 玄黄阁), `destroy_news_delivered` (two lines at once: "[Sect] 玄黄阁 … falls apart." and "玄黄阁 is no more; CaptureDev goes on as a rogue cultivator."); `destroy_chat_open.png` |
| A destroyed sect's avatars withdraw | `not_verified` | headless (`world_sim_news_evidence`, 15/15 checks): `avatars_withdrawn_after_destroy` (12 → 0 at 玄黄阁's site within 5.9 s); `destroyed_court.png` |

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

## Additional Sword Items

Three reference-derived swords are available from `myvillage:main` or through
the following commands:

```mcfunction
/give @s myvillage:xuanyue_zhenshan_sword
/give @s myvillage:chilian_lihuo_sword
/give @s myvillage:qingxiao_liuyun_sword
```

All three currently follow ordinary diamond-sword attack, mining, enchantment,
repair, and durability behavior. They have no recipes, right-click abilities,
elemental effects, custom payloads, or Qingfeng/PAL five-move integration.

<!-- SWORD_COMBAT_FOUNDATION -->
## Qingfeng Sword Combat

This feature requires the exact `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar`
on both client and server. Place it at the repository root for development and
beside MyVillage in the instance `mods/` directory for play. PAL is not bundled
in the MyVillage jar, and the supplied root jar remains untracked.

Obtain the independent functional sword and switch modes with the configurable
`Switch Combat Mode` control (default `R`). Since 0.40.0 `Movement Dodge`
(default Left Alt) dashes with a learned 身法 technique in cultivation mode
(see "Movement Dodge (0.40.0)" below):

```mcfunction
/give @s myvillage:qingfeng_sword
```

In vanilla mode, the Qingfeng Sword follows ordinary diamond-sword attack,
mining, enchantment, repair, and durability behavior. In cultivation mode,
mapped attack input is intercepted only while a registered combat weapon is in
the main hand: this sword, or since 0.29.0 the two-handed Lingxiao Spear (see
below). The client sends an empty attack intent; the server owns the move, timing,
facing, hit shape, targets, damage, durability, steps, and target reaction. Pickaxes,
empty hands, other weapons, open screens, and vanilla mode keep their existing
input paths. An eligible cultivation click also starts one local-only
first-person swing for the predicted move; an authoritative start
corrects its elapsed time, and rejection or stop blends back to the neutral
hold.

Since 0.28.0 the move set is data. Each style file under
`src/main/resources/data/myvillage/combat/style/` holds a combo's timing,
damage, hit shapes, steps, reactions, sounds, hit-stop, and camera cues. Each
weapon file under `.../combat/weapon/` binds an item to a style, a first-person
rig, and a geometry contract, and `.../combat/index.json` lists both. The files
ship in the jar and load at startup on both sides; a datapack cannot override
them and `/reload` does not re-read them. The field reference and the steps to
add a weapon or a move are in `docs/ai-kb/34_combat_data_and_capture.md`.

The first-person swings are data in
`src/main/resources/assets/myvillage/combat/qingfeng_first_person.json`. The
sword swings around one camera-space shoulder pivot: `plane` tilts the swing on
screen, `sweep` turns the arm in that plane, `reach` is pivot-to-grip distance,
and `lead`/`lift`/`twist` aim and turn the blade at the handle (`lift 0` points
the blade up, `-90` points it forward, `twist 90` shows the flat during a cut).
Keys are in server ticks with an `ease` per segment (`linear`, `in`, `out`,
`in_out`, `in_cubic`, `out_cubic`, `in_out_cubic`, `out_back`). They start and
end at the neutral hold. Each move's `strike` window must cover the server
active window within three ticks so the blade crosses the target while the hit
resolves, and an optional `contact` tick anchors the hit-stop. Edit the file and
reload resources (`F3+T`) to see changes; an invalid file is logged and Qingfeng
falls back to the vanilla hold.

A skin and sleeve arm (`FirstPersonArmRenderer`) holds the sword on the
same pivot rig: upper arm, forearm, a bending wrist, and a fist. The handle
crosses the fist, with the guard showing on the thumb side and the pommel below
the little finger. The wrist bend stays within anatomical limits, and the arm
lags a cut slightly and follows through when it stops (presentation only). The
rig's `rig.weapon_scale` and `rig.arm` settings and each key's `grip_roll` (hand
turn about the handle) and `elbow` (elbow swivel) tune it. The arm is drawn
beside the item pass and never cancels it.

The held Qingfeng Sword is a 3D jian model. The inventory icon stays the 2D
sprite (`neoforge:separate_transforms`). The 3D model, its texture, the wrapper
model, and the geometry contract `combat/qingfeng_sword_geometry.json` (format 2:
grip centre, `collar` for the guard, `butt` for the pommel, `head_base` and
`head_tip` for the blade) are all generated. Edit
`tools/gen_qingfeng_sword_model.py` and rerun it instead of editing them:

```bash
python3 tools/gen_qingfeng_sword_model.py            # write the outputs
python3 tools/gen_qingfeng_sword_model.py --check    # fail on drift
python3 tools/gen_qingfeng_sword_model.py --report   # print the derived display and grip fit
```

The first-person grip, the first-person trail, and the world-trail size
read the contract, and the grip undoes the model's own first-person display
transform. A regenerated sword therefore needs no Java change; reload resources
(`F3+T`) to see it.

The third-person full-body poses in `player_animations/sword_combat.json` are
generated. Edit `tools/gen_sword_pal_anims.py` and rerun it instead of editing
the JSON by hand. It keeps the grip centre in the fist while PAL rotates the
held item.

Feedback follows server outcomes only. Each move plays a swing or thrust sound
about one tick before its active start, with slight pitch variation. The
attacker hears it on the local timeline and everyone else hears it from the
server. Only targets that took damage produce feedback:
- the hit sound, plus a heavy impact layer on moves four and five;
- a `myvillage:blade_cut` slash particle at the contact point and a few crit
  sparks (the vanilla sweep particle is gone);
- a per-move hit-stop (`1.5/2/2/3/4` ticks) for the attacker, after which the
  swing catches up so the move still ends on the server total;
- attacker-local camera shake and kicks, scaled by the Screen Effect Scale and
  FOV Effect Scale accessibility options.

Nearby clients also briefly freeze and jitter the struck target from the
`CombatImpactPayload` broadcast. A thin translucent 剑光 ribbon follows the blade
through each strike in first person. Other players, and your own third-person
camera, see a ribbon along the move's server hitbox samples. The five
`myvillage:combat.sword.*` sounds currently alias vanilla sounds in
`assets/myvillage/sounds.json` and have subtitles. None of these visuals or
sounds sends a packet or changes timing, targets, damage, or movement.

The server also decides how the target reacts. Mobs freeze for the hit-stop,
slide back along the attack facing, then stagger for `9-16` ticks (no movement
or melee damage). Repeated stuns fall off, and bosses are exempt. Player targets
are never frozen; they slide and are slowed by 60% for the hitstun. Every move
takes a small forward step, and move five a lunge of up to `1.40` blocks. The
server decides each step's distance, stopping short of a target in front, and
sends it as a motion impulse that the attacker's client moves through with
normal collision, like knockback.

One legal attack input advances each connected move. A click from a move's
active start onward is held in one buffer slot. The held click cancels the rest
of the recovery and starts the next move at its chain tick (`7/8/10/13` for
moves one to four). Without a held click the move plays to its end. Misses may
continue the sequence, while timeout or move five resets it:

```text
1  basic_sword_01_thrust          一式：青锋问路
2  basic_sword_02_horizontal_cut  二式：流云横渡
3  basic_sword_03_rising_cut      三式：燕返撩月
4  basic_sword_04_diagonal_cut    四式：回风落雁
5  basic_sword_05_lunge_thrust    五式：一线穿云
```

### Lingxiao Spear (0.29.0)

`myvillage:lingxiao_spear` (凌霄枪) is a two-handed spear and the second combat
weapon. It is in `myvillage:main` after the four swords and has no recipe:

```mcfunction
/give @s myvillage:lingxiao_spear
```

In vanilla mode it is a diamond-tier sword item with 8 attack damage and 1.2
attack speed. In cultivation mode it runs the `myvillage:basic_spear` style
(`data/myvillage/combat/style/basic_spear.json`), with longer reach and slightly
slower moves than the sword. Moves one to four chain at `8/10/10/14`; the
fifth cannot chain:

```text
1  basic_spear_01_mid_thrust      中平扎    thrust, range 4.2, one target
2  basic_spear_02_sweep           横扫      level sweep left to right, up to four targets
3  basic_spear_03_rising_flick    上挑      right-low to left-high, lifts the target
4  basic_spear_04_overhead_smash  劈枪      left-high to right-low, heavy
5  basic_spear_05_dragon_lunge    游龙突刺  long thrust with a step of up to 1.6 blocks, heavy
```

Both hands hold the shaft in the guard. In third person the leading hand stays
on the shaft for the mid thrust and lets go for the sweep, flick, smash, and
lunge, regripping in the recovery (the third-person arms cannot bend). In first
person a second arm holds the shaft through the thrust, sweep, and flick and
lets go during the smash and lunge; it is drawn only while the off hand is
empty. The model, its textures, the 2D icon, and the geometry contract
`combat/lingxiao_spear_geometry.json` come from
`tools/gen_lingxiao_spear_model.py`; the third-person poses in
`player_animations/spear_combat.json` come from `tools/gen_sword_pal_anims.py`.
Edit the generators, never the outputs:

```bash
python3 tools/gen_lingxiao_spear_model.py            # write the outputs
python3 tools/gen_lingxiao_spear_model.py --check    # fail on drift
python3 tools/gen_lingxiao_spear_model.py --report   # print the derived display and grip fit
```

The first-person rig `combat/lingxiao_spear_first_person.json` is hand-authored
and reloads with `F3+T`. Its `rig.off_hand` block and the per-key
`off_hand_slide`, `off_hand_roll`, `off_hand_elbow`, and `off_hand_hold` fields
are described in `docs/ai-kb/35_lingxiao_spear.md`, with what the spear showed
about the 0.28.0 data infrastructure. A rig, contract, or first-person solver
change also rewrites the first-person parity golden (see the offline preview
commands below).

Geometry contracts are format 2 and use weapon-neutral names (`butt`, `handle`,
`collar`, `head`, `head_base`, `head_tip`, `axes.length`); the rig's scale is
`rig.weapon_scale`. A format 1 contract or a rig that still has `sword_scale`
fails to load with a message naming the new field, and the validator reports it.
The rename table is in `docs/ai-kb/34_combat_data_and_capture.md`. A move may
also give its third-person world trail its own path (`"trail": {"samples": ...}`
in the style file, presentation only; without it the trail follows the hit
samples), and `rig.off_hand` may give the off arm its own `upper_arm`,
`forearm`, and released rest (`rest_direction`, `rest_reach`). No shipped style
or rig uses these yet.

Operators can inspect the server-computed active samples without changing hit
authority:

```mcfunction
/myvillage combat debug on
/myvillage combat debug status
/myvillage combat debug off
/myvillage combat dodge status [player]   # 0.40.0: chosen 身法, cooldown and window left
```

For client-side pose review only, a developer client can play each full-body
PAL curve, or hold one first-person or third-person frame at a server tick,
without generating an attack intent. The move number is one-based within the
style of the weapon in the main hand; with no registered weapon the command
fails:

```text
/myvillage_pal_smoke move 1
/myvillage_pal_smoke first_person 2 5.0
/myvillage_pal_smoke first_person release
/myvillage_pal_smoke third_person 2 5.0
/myvillage_pal_smoke third_person release
```

These local smoke commands prove rendering only. They cannot replace
mapped-click, server authority, damage, timing, or multiplayer acceptance.

Run the focused automated gates before client review:

```bash
python3 tools/validate_sword_combat_foundation.py
python3 -m unittest tools.tests.test_validate_sword_combat_foundation tools.tests.test_combat_data tools.tests.test_combat_style_baseline
python3 tools/gen_sword_pal_anims.py --check
python3 tools/gen_blade_cut_sprite.py --check
python3 tools/gen_qingfeng_sword_model.py --check
python3 tools/gen_lingxiao_spear_model.py --check
python3 tools/gen_xuantie_gauntlet_model.py --check
python3 -m unittest tools.tests.test_gen_sword_pal_anims tools.tests.test_gen_blade_cut_sprite tools.tests.test_gen_qingfeng_sword_model tools.tests.test_gen_lingxiao_spear_model tools.tests.test_gen_xuantie_gauntlet_model
python3 -m unittest tools.tests.test_combat_capture tools.tests.test_combat_preview tools.tests.test_combat_preview_tuning
.venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity tools.tests.test_combat_preview_sweep
python3 tools/validate_mod_items.py
python3 -m unittest tools.tests.test_validate_mod_items
./gradlew test
./gradlew build
./gradlew runAcceptanceServer
```

The validator checks the combat data files and their consistency with the
animations, rigs, translations, and sounds; it holds no per-move numbers.
`tools/tests/test_combat_style_baseline.py` pins the accepted Qingfeng values,
so a deliberate retune updates that test.

To collect presentation evidence on a headless host (stills of every move at
its key ticks in first and third person, a mapped-click combo with video and
server-side target health, and a before/after page), use the capture tool. It
needs `Xvfb`, `xdotool`, `ffmpeg`, and ImageMagick, takes about 6 to 8 minutes
per pass, and writes to `out/preview/combat_capture/<label>/`:

```bash
python3 -m tools.combat_capture run --label <label>
python3 -m tools.combat_capture run --label <label> --weapon myvillage:lingxiao_spear
python3 -m tools.combat_capture combo --label <label> --weapon myvillage:lingxiao_spear --layout sweep --camera back   # in a running session
python3 -m tools.combat_capture compare out/preview/combat_capture/<a> out/preview/combat_capture/<b> --label <a-vs-b>
```

Without `--views`, a weapon shorter than 1.8 blocks is captured in first person
and F5 back and front, and a longer one (the spear) in first person and two
quarter views whose sheets are cropped around the player. `combo --layout
default|sweep|line` places the three targets (one in reach; an arc 2.5 blocks
out; a line ahead), `--tick-rate 5` runs at quarter speed, and the manifest
attributes every health loss to a move (`hits_by_move`).

`tools/combat_capture/README.md` has the session, hot-reload, and tuning-loop
commands. Capture output is developer evidence and records no owner verdict.

For quick looks without Minecraft, the offline previews render first-person rig
frames (item model, both arms, trail) at any tick, third-person PAL poses, and
item models from `src/main/resources`, in seconds and without the heavy-work
lock. They need numpy and Pillow; without them the command re-executes under
`$MC_PREVIEW_PYTHON` or `.venv-preview/bin/python`, or prints the setup
command. The default vanilla resources jar comes from a Gradle build
(`build/moddev/artifacts/`).

```bash
python3 -m venv .venv-preview && .venv-preview/bin/pip install -r tools/combat_preview/requirements.txt   # once
python3 -m tools.combat_preview fp --weapon myvillage:lingxiao_spear --move all --key-ticks --out out/preview/combat_preview/spear_fp.png
python3 -m tools.combat_preview pose --animations src/main/resources/assets/myvillage/player_animations/spear_combat.json \
    --animation basic_spear_02_sweep --all-keys --item myvillage:item/lingxiao_spear --out out/preview/combat_preview/spear_sweep.png
python3 -m tools.combat_preview model myvillage:item/qingfeng_sword --out out/preview/combat_preview/sword_model.png
# candidate rig values side by side; before/after stills from two capture sets
python3 -m tools.combat_preview sweep --weapon myvillage:lingxiao_spear --set rig.off_hand.thickness=0.42,0.5,0.56,0.62 \
    --out out/preview/combat_preview/sweep_offarm_thickness
python3 -m tools.combat_preview diff out/preview/combat_capture/<before> out/preview/combat_capture/<after> --out out/preview/combat_preview/<before-vs-after>
```

`sweep` renders `fp` frames for each candidate value of a rig field (a dotted
JSON path) and writes close-ups where they differ, changed pixels against the
shipped value, and a rig file per candidate. `diff` writes a before/after sheet
and changed-pixel counts with their bounding boxes. Both are covered in
`tools/combat_preview/README.md` ("Tuning a rig value").

The `fp` port of the first-person solver is pinned to the Java by
`src/test/resources/first_person_preview_parity.json`. Both tests check it;
after a deliberate change to the solver, a rig, or a geometry contract, rewrite
it from the Java and bring `tools/combat_preview/fp_rig.py` in step until the
Python test passes:

```bash
./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest                        # check
./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest -PupdatePreviewParity  # rewrite the golden
.venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity                                   # Python port
```

`tools/combat_preview/README.md` has the options, the `--compare-capture`
check against capture stills, and what the golden does not cover.

For a separate final client profile, connect to the bounded server without
editing launcher state:

```bash
./gradlew runClient -Pcombat_smoke_server=127.0.0.1:25565 \
  -Pcombat_smoke_game_dir=run-combat-smoke -Pcombat_smoke_username=CombatDev
```

`combat_smoke_server` also bounds the client window to `960x540`; set FOV to
`70` for the reference first-person capture. Use a unique game directory
and username for a second physical client. Stop every client and the acceptance
server cleanly after collecting the evidence.

Gate A directly observed PAL controller registration, play, transition, stop,
normal-pose restoration, and dedicated-server side safety. A later real-client
`THIRD_PERSON_MODEL` probe failed because the ready sword floated near screen
center and the attack arm/sword clipped at excessive scale, so custom PAL
first-person arms/camera stay disabled with `FirstPersonMode.DISABLED`.

The 0.26.1 owner review judged the first-person swings "not like swinging a
sword". Capture confirmed why: the strike keys sat after the server hit window,
every key eased to a stop, the blade was edge-on at screen center, and there was
no hit, sound, or trail feedback. Revision 0.26.2 replaces that layer with the
pivot rig and feedback described above and hides the segmented arm. A developer
capture at `960x540`, FOV 70 with mapped clicks showed all five swings crossing
the target during the hit, the trails, sweep/crit particles, and hit-stop. That
capture is implementation evidence only.

Owner verdict on 0.26.2 (2026-09-30): after watching it (lab station A) next to
Epic Fight, the owner said A "现在不太行看上去" and asked for an optimized A:
"我要的是那种战斗真实动作游戏的感觉". The 0.26.2 swings were not accepted.
Revision 0.27.0 is the response. It adds chain windows, server-decided steps and
lunge, target reaction, per-move hit-stop, camera effects, blade-cut particles,
thin translucent trails, the complete first-person arm, and generated
third-person poses.

Owner feedback on 0.27.0 (2026-09-30): "自研的动作好一些了现在，但是握持这部分完全不行现在就像插入肉里的，非常僵硬。剑的建模也不太行可以优化一下。"
Revision 0.27.1 is the response: the fist-and-wrist arm with wrist lag, the 3D
jian model, the geometry contract, and the third-person grip compensation.

Owner words on 0.27.1 (2026-10-01, after viewing the 0.27.0 vs 0.27.1
comparison page, not real-client play): "感觉271看上去可以"; then "暂时就这样".

The lab station E capture on 2026-09-30
(`/home/ubuntu/code/mc/combat-lab/out/E`) exercised 0.27.0 in a physical client.
Rows marked "lab capture E" below were observed there. The lab station F
capture on 2026-09-30 (`/home/ubuntu/code/mc/combat-lab/out/F`) exercised the
0.27.1 grip and model with a development jar, and rows marked "lab capture F"
were observed there. Both captures are implementation evidence, not owner
acceptance. A local copy of the F evidence (0.27.0 vs 0.27.1 comparison
sheets, reactive-dummy clips, model previews, the lab capture tools and their
usage) lives in `out/preview/qingfeng_grip_0271/`. A local copy of the whole
lab (reports, station configs, harness, and the A to F captures) lives in
`out/preview/combat_lab/`; its handoff chapter is
`report/combat_lab_report.md` section 0, and the tracked summary is
[docs/ai-kb/33_combat_framework_comparison.md](docs/ai-kb/33_combat_framework_comparison.md).
Record only directly observed results below.

| Qingfeng real-client acceptance surface | Result |
|---|---|
| Inventory and held-item Qingfeng model/texture resolve and remain recognizable | `pass` |
| Chinese name, recipe-book result, and creative-tab order | `not_verified` |
| Owner verdict on texture, model scale, seven-animation style, and combat feel | `not_verified` |
| Vanilla-mode entity attack, block mining, cooldown, enchantments, repair, and durability | `not_verified` |
| Default `R` toggle and server-owned cultivation mode across reconnect, death, and dimension change | `pass` |
| Mode persistence across a clean server restart | `pass` |
| Rebound mode key plus both English and Chinese action-bar text | `not_verified` |
| Cultivation main-hand Qingfeng interception without a vanilla attack packet or duplicate damage path | `pass` |
| Remapped attack, sword-on-block mining suppression, unsupported item, empty hand, GUI, and vanilla-mode input regression | `not_verified` |
| Moves one through five in exact order, distinct full-body poses, visible fifth lunge, timeout reset, and fifth-to-first reset | `pass` |
| 0.26.x ordinary late-buffer chain and miss continuation under click timing | `pass` (0.26.x timing) |
| 0.27.0 buffer from active start and chain at `7/8/10/13` under real click timing | `not_verified` |
| Second buffered-intent rejection while the one slot is full | `not_verified` |
| Move-one center-thrust side/rear range boundaries | `not_verified` |
| Move-two/three/four visible arc and diagonal distinction | `pass` |
| Move-two/three/four exact target caps, range boundaries, and light knockback | `not_verified` |
| Move-five server-owned forward step measured at `0.8` blocks | `pass` (0.26.x server move; superseded) |
| Move-five wall suppression plus no wall-through target damage | `pass` (0.26.x server move) |
| 0.27.0 move-five impulse lunge produces visible forward displacement | `pass` (lab capture E; distance not measured) |
| 0.27.0 impulse step distances, magnetism stop, wall/cliff suppression, and planned-origin hit sweep | `not_verified` |
| Move-five player collision and cliff suppression | `not_verified` |
| Solid-wall target blocking | `pass` |
| PvP/team rules, invulnerability, deterministic target order, and per-action hit deduplication | `not_verified` |
| Real server damage and exactly one durability loss for each of five successful actions | `pass` |
| Armor/protection, Sharpness/Smite/Bane, Knockback/Fire Aspect, and NeoForge event-listener compatibility | `not_verified` |
| No duplicate vanilla damage, sweep, cultivation critical, or sprint bonus | `not_verified` |
| First-person mapped click produces immediate packet-free predicted Qingfeng feedback | `pass` |
| 0.26.1 normalized first-person curves read as a sword swing | `fail` |
| 0.26.2 pivot-rig swings read as a real action game (owner, 2026-09-30: "现在不太行看上去") | `fail` |
| Owner verdict on the 0.27.0 action-feel revision | `not_verified` |
| 0.27.0 first-person arm holds the sword on the pivot rig | `pass` (lab capture E) |
| 0.27.0 thin translucent 剑光 trails | `pass` (lab capture E) |
| 0.27.0 `blade_cut` particles at the contact point | `pass` (lab capture E) |
| 0.27.0 target slide on hit | `pass` (lab capture E) |
| 0.27.0 camera roll on heavy hits | `pass` (lab capture E) |
| 0.27.0 third-person full-body poses | `pass` (lab capture E) |
| 0.27.0 per-move hit-stop, target freeze/hitstun, player-target slow, and boss exemption | `not_verified` |
| 0.27.0 swing whoosh lead, pitch variation, heavy impact layer, and subtitles | `not_verified` |
| 0.27.0 no slowness FOV zoom during swings; accessibility scaling of camera effects | `not_verified` |
| 0.27.0 chained-move prediction and remote impact freeze/jitter in multiplayer | `not_verified` |
| Owner verdict on the 0.27.1 grip and 3D sword model | `not_verified` |
| 0.27.1 inventory shows the 2D icon, hand shows the 3D jian, no missing texture | `pass` (lab capture F) |
| 0.27.1 item frame and dropped item show the 3D jian | `pass` (lab capture F) |
| 0.27.1 third-person handle through the fist, back and front, staying in the fist during moves | `pass` (lab capture F) |
| 0.27.1 first-person fist on the handle with the guard visible | `pass` (lab capture F) |
| 0.27.1 first-person wrist bend and wrist lag/follow-through read naturally | `not_verified` |
| 0.27.1 first-person grip with a slim-arm skin and a left main hand | `not_verified` |
| 0.27.1 first-person wrist top edge: an occasional 1 px dark-green line in the rising-cut follow-through, visible at 6x zoom (lab F `grip_v5/compare_v4_v5_wrist_6x.png`). Suspected skin-texture bleed at the face boundary (the body shirt column next to the arm strip), not geometry; needs a recapture with other skins to confirm, and would be fixed by insetting the arm box UVs | `not_verified` (open) |
| 0.27.1-fix1 third-person legs walk with the sword drawn (ready idle), arms keep the guard, no foot sinking, smooth return to the stance on stopping | `pass` (headless capture 2026-10-02, local player, front F5 view, W held 2.5 s) |
| 0.27.1-fix1 walking legs seen on a remote player and under real keyboard play | `not_verified` |
| 0.28.0 data-driven styles: five moves and server damage unchanged under mapped clicks (target `80.0` to `44.18` before and after) | `pass` (headless capture 2026-10-02, `out/preview/combat_capture/before-vs-after-data-infra`) |
| 0.28.0 first- and third-person poses unchanged: 75 probe stills (5 moves, 5 key ticks, 3 views) match before and after | `pass` (same capture; developer comparison at 2% colour tolerance, not an owner verdict) |
| 0.28.0 packaged jar loads the combat data on a standalone dedicated server (protocol `7`) | `pass` |
| 0.28.0 two-client observation after the protocol change: remote start/stop, impact freeze/jitter, world-trail length | `not_verified` |
| 0.28.0 weapon change during an action in a real client (unregistered item stops it) | `not_verified` |
| 0.28.0 real keyboard play and sound | `not_verified` |
| Remote/third-person world trail follows the hitbox arc | `not_verified` |
| PAL third-person-model first-person arms/camera animation | `fail` |
| Two-client nearby five-move start/stop animation and real target damage synchronization | `pass` |
| Live interruption on mode, item, mount, dimension, death, and meditation start | `pass` |
| Logout/reconnect cleanup plus persisted ready state | `pass` |
| Unfinished-action recovery-lock timing | `not_verified` |
| Meditation start suppresses attack/ready pose until meditation stops | `pass` |
| Advancement mutual interruption and double-penalty exclusion | `not_verified` |
| Existing rideable sword, cultivation H/keys, GuideME, and ordinary combat regression | `not_verified` |

0.29.0 adds the Lingxiao Spear and two first-person fixes that change how the
Qingfeng Sword looks in play. Rows marked "headless capture" were observed in a
developer client on the capture host (`tools/combat_capture`, 960x540, software
rendering); they are implementation evidence, not owner acceptance. Evidence
lives under `out/preview/lingxiao_spear/` and `out/preview/combat_capture/`.

| Lingxiao Spear and 0.29.0 acceptance surface | Result |
|---|---|
| `/give`, tooltip (8 attack damage, 1.2 attack speed), and creative-tab place after the four swords | `pass` (headless capture 2026-10-02, `lingxiao_spear/ingame_model`) |
| Held 3D spear in first-person vanilla hold, dropped item, and item frame match the offline model previews; spearhead and inlays stay lit at night | `pass` (same capture) |
| Inventory icon readable at GUI scale 2 (icon redrawn on a 32x32 grid after the first look found the earlier one breaking up) | `not_verified` |
| Off-hand vanilla hold drawn as the mirror of the main hand (left-hand display fixed after the first look; the pennant side is still not mirrored) | `not_verified` |
| Chinese and English item and move names in game | `not_verified` |
| All five spear moves from mapped clicks with server damage, default layout: centre target `80.0` to `38.28` (per move `7.085/7.478/7.872/9.053/10.234`) in every run, normal and quarter speed, first person and F5 back | `pass` (headless capture 2026-10-02, `combat_capture/spear-final`, `-tpcombo`, `-slow-fp`, `-slow-back`) |
| Spear target spread: sweep hits three targets on an arc 2.5 blocks out; on a line 2.5/3.5/4.5 ahead the lunge hits three, sweep, flick, and smash two each, thrust one | `pass` (headless capture 2026-10-02, `combat_capture/spear-final-sweep`, `-line`) |
| Spear stills: first person with both hands on the shaft, quarter views of the two-handed guard, releases, and regrips, five moves at five key ticks | `pass` (headless capture 2026-10-02, `combat_capture/spear-final`; developer evidence, not an owner verdict) |
| Quarter-view motion of the five moves, mode entry, and ready idle | `pass` (client playback only, `combat_capture/spear-final-motion`; no hits, steps, or world trail) |
| One first-person hit-stop per move; a three-target sweep starts one stop and ignores two confirmations | `pass` (headless capture 2026-10-02, `fp_log` of `spear-final-sweep`) |
| Swing keeps its pace through a forced +20 game-clock reset (`/tick sprint 20`); `/tick freeze` and `/tick step` hold and resume it; a Nether teleport and a death mid-move stop it cleanly | `pass` (headless capture 2026-10-02, `lingxiao_spear/fp_runtime/forced`) |
| Game-clock reset from server lag, a camera kick running through a reset, a clean track of a +1/+2 reset mid-swing | `not_verified` |
| Spear world trail seen by another player; one stop per action seen by another player | `not_verified` |
| Flick and smash world trails seen from the side | `not_verified` |
| Spear first-person off arm hidden while an item is in the off hand | `not_verified` |
| Spear step distances, magnetism stop, lunge wall/cliff suppression, and foot sliding | `not_verified` |
| Owner verdict on the spear model, icon, five moves, and two-handed presentation | `not_verified` |
| First-person hit-stop no longer steps back on a late hit confirmation (Qingfeng and spear, normal and quarter tick rate) | `pass` (headless capture 2026-10-02, `lingxiao_spear/fp_runtime`) |
| First-person weapon no longer dips out of view after a landed hit costs durability (Qingfeng and spear) | `pass` (same capture; an unbreakable control shows the same no-dip trace) |
| Rename, enchantment, or another item still plays the equip animation; switching between two copies of one weapon does not | `not_verified` |
| Qingfeng regression: `80.0` to `44.18` under mapped clicks; third-person stills identical and first-person stills within 2 colour levels of `sword-v2`, apart from the experience bar row | `pass` (headless capture 2026-10-02, `combat_capture/sword-final`; developer comparison, not an owner verdict) |
| Owner verdict on the changed Qingfeng first-person hit-stop, post-hit hold, and clock behaviour | `not_verified` |
| Combat-mode and debug-command messages weapon-neutral in both languages | `not_verified` |
| Real keyboard and mouse play, sound, frame rates on a real GPU, other skins, armour, and capes | `not_verified` |

### Adding Another Combat Weapon (0.30.0)

The procedure is `docs/ai-kb/36_new_combat_weapon.md` (22 numbered steps).
`tools/new_combat_weapon.py` starts a weapon from an existing one and reports
how far it has come, in the same step ids:

```bash
python3 tools/new_combat_weapon.py scaffold --from myvillage:qingfeng_sword --id myvillage:jade_sword --dry-run
python3 tools/new_combat_weapon.py scaffold --from myvillage:lingxiao_spear --id myvillage:iron_halberd \
    --style myvillage:basic_halberd --moves thrust,sweep,hook,chop,lunge
python3 tools/new_combat_weapon.py progress myvillage:iron_halberd [--fast] [--json]
```

`scaffold` writes only the renamed copies (weapon file, index entries,
first-person rig, with `--style` the style, and `TODO(new_combat_weapon)`
translations) and prints every other step with its file; it never writes Java
or a generator-owned file, and refuses, writing nothing, when an id or file is
taken. `progress` sorts the existing validators' and tests' findings into the
steps (`DONE`, `PLACEHOLDER`, `MISSING`, `BLOCKED`, `N/A`) and exits 1 while a
checked step is open.

### Xuantie Gauntlet (0.39.0)

`myvillage:xuantie_gauntlet` (玄铁拳套) is the 拳掌 (fist) school's weapon: a
pair of dark-iron gauntlets in one item (the weapon file sets `paired`), the
third combat weapon. It is
in `myvillage:main` after the Lingxiao Spear and has no recipe:

```mcfunction
/give @s myvillage:xuantie_gauntlet
```

In vanilla mode it is a diamond-tier sword item with 5 attack damage and 2.2
attack speed (lighter and faster than the sword). In cultivation mode it runs
the `myvillage:basic_fist` style (`data/myvillage/combat/style/basic_fist.json`):
short reach, fast moves with two-tick hit windows, small slides and short
hit-stops; moves one to four chain at `6/7/7/8`, the fifth cannot chain, and
the combo resets after 10 idle ticks:

```text
1  basic_fist_01_straight_punch      冲拳      9 ticks, hit 3-4, range 1.9, one target
2  basic_fist_02_horizontal_palm     横掌     10 ticks, hit 4-5, right to left at chest height, two targets
3  basic_fist_03_uppercut            上勾     10 ticks, hit 4-5, rising through the centre, lifts the target
4  basic_fist_04_chop                劈掌     11 ticks, hit 5-6, from high right down through the centre, two targets
5  basic_fist_05_step_double_strike  踏步双撞  11 ticks, hit 6-7, a 0.9-block step then both fists, two targets, heavy
```

The gauntlet sits on the fist and never turns in the hand. Both hands are free:
in third person the stance leads with the left hand out at chest height and
the gauntlet at the dantian; the left hand pulls back to the waist on the punch
and the chop, stays up on the palm and the uppercut, and strikes with the right
on the step-in. The mode entry is a 抱拳礼. In first person the left guard
hand is drawn while the off-hand slot is empty and moves with the strikes.

0.39.1 (after the owner's first look): one item is a pair. While the off-hand
slot is empty the same model is drawn mirrored on the left hand, in third
person by `PairedWeaponLayer` (it follows the PAL left arm) and in first
person over the guard fist; anything held in the off hand replaces it. The
model is a slimmer articulated fist (12.5 x 11.0 x 10.25 model px, was
16.5 x 12.8 x 11.9; still 2 model px per player px) with separate finger
plates, knuckle caps, overlapping back lames, the thumb across the curled
fingers and a plain palm, and the inventory icon is an angled three-quarter
fist. Candidates and before/after sheets: `out/preview/xuantie_gauntlet/v2/index.html`. The
model, textures, icon, and the contract `combat/xuantie_gauntlet_geometry.json`
come from `tools/gen_xuantie_gauntlet_model.py`; the poses in
`player_animations/fist_combat.json` come from `tools/gen_sword_pal_anims.py`
(`BASIC_FIST`); the first-person rig `combat/xuantie_gauntlet_first_person.json`
is hand-authored and reloads with `F3+T`. Edit the generators, never their
outputs:

```bash
python3 tools/gen_xuantie_gauntlet_model.py            # write the outputs
python3 tools/gen_xuantie_gauntlet_model.py --check    # fail on drift or a failed self-check
python3 tools/gen_xuantie_gauntlet_model.py --report   # the derived display and the fist fit
python3 tools/gen_sword_pal_anims.py --report          # per key, with each fist move's off-hand role and arm clearance
python3 -m tools.combat_preview fp --weapon myvillage:xuantie_gauntlet --move all --key-ticks --out out/preview/xuantie_gauntlet/fp.png
python3 -m tools.combat_capture run --label <label> --weapon myvillage:xuantie_gauntlet
```

What the gauntlet added to the shared combat data (details in
`docs/ai-kb/34_combat_data_and_capture.md`, `35_lingxiao_spear.md` and
`36_new_combat_weapon.md`): a weapon file's optional `family` (`sword`,
`spear`, `fist`), which the validator checks against the schools'
`weapon_family`; the optional boolean `paired` (0.39.1, validator
`COMBAT_WEAPON_PAIRED`: needs a free off hand and no `off_hand_grip_center`);
a free first-person off hand (`rig.off_hand.free`) with a
keyed rest per key (`off_hand_rest`, `off_hand_reach`); `rig.arm.grip_diagonal`
up to 90 for a weapon worn along the hand; and the pose generator's `worn` and
`free_off_hand` tables. `tools/tests/test_combat_style_baseline.py` and
`BasicFistStyleTest` pin the accepted fist values.

Rows marked "headless capture" were observed in a developer client on the
capture host (`tools/combat_capture`, 960x540, software rendering) on
2026-10-07; they are implementation evidence, not owner acceptance. Offline
sheets and candidates for the owner: `out/preview/xuantie_gauntlet/index.html`.

| Xuantie Gauntlet and 0.39.0 acceptance surface | Result |
|---|---|
| All five fist moves from mapped clicks with server damage: centre target `80.0` to `56.38` (per move `3.936/4.182/4.428/4.92/6.15`) | `pass` (headless capture, `combat_capture/gauntlet-final`) |
| One first-person hit-stop started per move, the finisher's included | `pass` (same capture, `fp_log`; the two earlier captures showed the finisher's stop dropped, fixed in this version) |
| Gauntlet drawn on the fist in third person, front and back, through the five moves; bare left guard hand drawn in first person with an empty off-hand slot | `pass` (same capture, stills; developer evidence) |
| Owner verdict on the gauntlet model, texture, and icon | `not_verified` (0.39.0 rejected by the owner; 0.39.1 rework not yet seen) |
| 0.39.1 pair: the mirrored left gauntlet in third person through the stance and the five moves, and on the first-person guard hand | `pass` (headless capture `combat_capture/gauntlet-v2-pair`, 2026-10-07: left gauntlet mirrored on the left fist in tp_front/tp_back and on the first-person guard hand in all five moves; server damage unchanged, `80.0` to `56.38`; developer evidence) |
| 0.39.1 pair: owner verdict on the left gauntlet (mirror, fit on the left fist, guard and chamber read with two gauntlets) | `not_verified` |
| 0.39.1 size: owner verdict on the slimmer model (no longer a ski glove) in third and first person | `not_verified` |
| 0.39.1 icon: owner verdict on the angled icon at 1x in the hotbar, inventory and creative tab | `not_verified` |
| Owner verdict on the stance, mode entry, and the five third-person strikes (weight transfer, off-hand counter-moves) | `not_verified` |
| Owner verdict on the first-person rig: gauntlet size, punch read from behind, guard hand size and place | `not_verified` |
| Fist trails (short, from the knuckles) in first person and as world trails seen by another player | `not_verified` |
| Sound (reused sword sounds at higher pitch) and the heavy finisher's feel | `not_verified` |
| Real keyboard and mouse play, chain timing at `6/7/7/8`, combo timeout 10, minimum intent interval 1 | `not_verified` |
| Step-in distance, magnetism stop, wall/cliff suppression, two-target spread of the palm, chop, and double strike | `not_verified` |
| Off-hand item held: guard hand and left gauntlet hidden, vanilla off-hand draw | `not_verified` |
| Vanilla first-person hold, dropped item, item frame, inventory icon at GUI scale 2, tooltip (5 damage, 2.2 speed), creative-tab place, Chinese and English names | `not_verified` |
| Slim-arm and other skins in the gauntlet; left main hand (the pair then sits on the right off hand unmirrored); armour and capes | `not_verified` |
| A second client (remote poses, impact freeze, one stop per action) | `not_verified` |
| Frame rates on a real GPU | `not_verified` |
| Qingfeng and Lingxiao regression after the shared first-person changes (keyed rest, free off hand, grip diagonal range) | `not_verified` in game (parity golden and Java tests unchanged for both) |

### Movement Dodge (0.40.0)

A learned 身法 (movement) technique gives a short dash with an invulnerable
window. Press `Movement Dodge` (身法闪避, `key.myvillage.dodge`, default Left
Alt, rebindable under the MyVillage key category) in cultivation combat mode.
The movement keys held at that moment pick one of eight directions relative to
the view; with none held the dodge is a back step. A sneaking player's held key
still counts as a direction (dead zone 0.2, below vanilla's 0.3 sneak input
scale). No weapon has to be in hand.

The client sends only the key press and that one-byte direction
(`CombatDodgeIntentPayload`); the server decides everything else, in this
order, and refuses at the first failed check:

1. State: alive, not spectating, sleeping, using an item, riding, or
   meditating (`STATE`); cultivation combat mode (`MODE`); on the ground,
   by the client's flag or a block within 0.25 below the feet
   (`AIRBORNE`).
2. Technique: the highest-grade learned movement technique with an
   `effects.movement` block; equal grades go to the smaller id
   (`NO_TECHNIQUE` without one). Its data gives the distance, the
   invulnerable ticks, and the cooldown.
3. Cooldown since the last dodge (`COOLDOWN`).
4. A running attack can only be dodged out of its recovery (action tick past
   the move's last active tick); during the wind-up or the strike the press
   is refused (`TIMING`) and not queued.
5. The dash distance is shortened to a collision- and footing-safe one, as for
   an action step (ground within one block below); none left is `BLOCKED`.

Then a recovery in progress stops with the new stop reason `DODGED` (combo
back to the first move, no recovery lock), the server applies the dash
impulse (`setDeltaMovement` + `hurtMarked`, with a small downward part,
-0.1, so the body stays on the ground and keeps ground friction; sprint off),
and opens the
invulnerable window: for that many ticks every incoming damage is cancelled
except sources tagged `bypasses_invulnerability` (`/kill`, the void), and
attack intents are refused, so an attack after a dodge starts when the window
ends. Each dodge adds one mastery point to the technique. Everyone nearby gets
a low thrust whoosh and a puff of cloud at the feet; `CombatDodgeStartPayload`
tells the dodging player and everyone tracking it to draw cloud afterimages at
the heels for at least six ticks, and gives the dodging player a 6° FOV surge
(plus a 1.2° lean on a sideways dodge). The motion itself is the vanilla
motion packet; the client predicts no movement. There is no dedicated dash
pose. `qi_cost` is in the data but no qi is spent. Payload protocol is `12`,
so client and server need the same jar.

Two water movement techniques ship (the catalogue now has 131):

| Technique | Grade | `previous` | Distance | Invulnerable | `qi_cost` (not spent) | Cooldown |
|---|---|---|---:|---:|---:|---:|
| `myvillage:liuyun_bu` 流云步 | 黄 1 | none | 3.5 blocks | 5 ticks | 6 | 30 ticks |
| `myvillage:taxue_wuhen` 踏雪无痕 | 玄 2 | 流云步 | 4.5 blocks | 7 ticks | 10 | 24 ticks |

Learn one by studying its manual (`manual_movement_huang` / `_xuan`; the
usual realm, affinity, and `previous` requirements apply) or, for testing,
with the debug command, which checks only that the technique is registered:

```mcfunction
/myvillage cultivation manual @s myvillage:liuyun_bu
/myvillage cultivation learn @s myvillage:taxue_wuhen
/myvillage combat dodge status [player]
```

`combat dodge status` prints the technique a dodge would use (grade,
distance, invulnerable ticks, cooldown), the cooldown and window ticks left,
and whether the player is dodging. The server logs every press at INFO:

```text
DODGE_DEBUG player=<name> t=<tick> result=started technique=<id> dir=<DIR> yaw=<f> distance=<f> invuln=<n> cooldown=<n>
DODGE_DEBUG player=<name> t=<tick> result=rejected reason=<STATE|MODE|AIRBORNE|NO_TECHNIQUE|COOLDOWN|TIMING|BLOCKED>
DODGE_DEBUG player=<name> t=<tick> cancelled_damage=<amount> source=<damage type id>
```

Movement data comes from the catalogue: a row's optional `effects` object in
`tools/technique_catalogue/catalogue.json` overrides the category default and
is checked against the Java shape; names containing 步, 身法, 无痕, or 遁 are
classified as movement. Regenerate with `tools/gen_technique_catalogue.py`.
Both techniques are also in the world ledger's pool, so sects, rogue
cultivators, and fortunes can now draw a 身法. Details:
`docs/ai-kb/42_movement_dodge.md`; brief: `docs/movement-dodge-brief.md`.

Headless evidence against the demon wolf (bite and pounce at several move
ticks, cooldown, recovery cancel; health, distance, server lines, F5-back
videos) into `out/preview/movement_dodge/`:

```bash
python3 -m tools.combat_capture dodge [--beast ID] [--technique ID] [--out DIR]
```

<!-- DODGE_CAPTURE_RESULTS --> Capture results (headless capture on the
build host, commit `ef4187f`, `/tick rate 5`, 踏雪无痕: 4.5 blocks, 7
invulnerable ticks, cooldown 24; output `out/preview/movement_dodge/` with
`index.html`, `manifest.json`, `dodge_log.txt`, and two F5-back videos,
`video/dodge_demon_wolf_bite.mp4` 88.8 s and `video/dodge_demon_wolf_pounce.mp4`
115.2 s). Developer evidence, not the owner's verdict:

- Bite: without a dodge health `20` to `16` (hit at move tick 11, 4.0).
  Back steps pressed at move ticks 4, 7, 9, 10 and a left dodge at 9 all
  `started` and health stayed `20`; back steps moved 4.49 blocks, the left
  dodge 4.92 (with the A key's own walk). After the press at tick 10 the
  server logged `cancelled_damage=4.00 source=minecraft:mob_attack` on the
  next tick and the wolf's hit line read `accepted=false` (the window took
  the hit); the other four left the bite's reach and drew no hit line.
- Pounce: without a dodge health `20` to `13.6` (hit at move tick 27, 6.4).
  Back steps at 14, 17, 18, 19 and a left dodge at 18 all `started`, health
  stayed `20`, 4.49 blocks (left 5.14). All five escaped by distance; no
  pounce hit was cancelled by the window (that needs a later press or a
  shorter dash).
- Cooldown: two presses 5 ticks apart, `started` then
  `rejected reason=COOLDOWN`.
- Recovery cancel: after the first Qingfeng move, a press at action tick 3
  was `rejected reason=TIMING`, one at action tick 6 `started` (4.22
  blocks).
- Every landing matched the planned distance; no `AIRBORNE` refusal in the
  whole log.

The first capture run found that a flat impulse left the client reporting
`onGround=false` on the next tick: air friction carried the 4.5-block back
step to 5.39 blocks, and a press at action tick 3 was refused `AIRBORNE`
instead of `TIMING`. `ef4187f` adds the downward part and the 0.25-block
support probe; the numbers above are from the second run.

| Movement Dodge (0.40.0) real-client acceptance surface | Result |
|---|---|
| A dodge cancels a demon wolf bite and a pounce inside the window (health unchanged, `cancelled_damage` or no accepted hit) | `pass` (headless capture `movement_dodge`, `ef4187f`: health `20` kept in all 10 dodge trials; bite at tick 10 cancelled by the window, `cancelled_damage=4.00`, `accepted=false`; the other bite trials and all pounce trials escaped by distance, so window cancellation of a pounce hit is not shown; developer evidence) |
| `COOLDOWN` on a second press inside the cooldown; `TIMING` during an attack's strike and `started` in its recovery | `pass` (same capture: second press 5 ticks later `COOLDOWN`; action tick 3 `TIMING`, action tick 6 `started`, 4.22 blocks; no `AIRBORNE` in the log; developer evidence) |
| Feel: dash distance (3.5 / 4.5 blocks), invulnerable window (5 / 7 ticks), cooldown (30 / 24 ticks) | `not_verified` |
| Key: Left Alt by default, rebinding, eight directions and the back step from real WASD input, sneaking | `not_verified` |
| Cloud afterimages and the puff at the feet, seen by the dodger and by a second client | `not_verified` |
| Camera: FOV surge and sideways lean on the dodging player | `not_verified` |
| Animation after `DODGED`: stop of the recovery, combo back to the first move, ready idle re-entered, no dash pose | `not_verified` |
| Attack right after the window; a click inside the window is not predicted locally | `not_verified` |
| Sound (the thrust whoosh at volume 0.6, pitch 0.75) | `not_verified` |
| Learning through a manual and the 功法 page's 身法 card; mastery rising per dodge | `not_verified` |
| Edges and walls (`BLOCKED`), slopes, water, riding, meditation, mid-air presses | `not_verified` |
| Frame rates on a real GPU | `not_verified` |

## GuideME Cultivation Guide

MyVillage 0.27.1 requires a compatible GuideME installation on both client and
server (`[21.1.17,22)`). Gradle resolves GuideME 21.1.17 from Maven Central for
development; GuideME is not bundled in the MyVillage jar. The untracked
root-level `guideme-21.1.17.jar` is inspection material, not a build input.

The first data-driven `myvillage:cultivation` slice has one Chinese default
source, a complete `_en_us` pair, and three task pages: overview, initiation,
and the combined cultivation loop. Give and use the MyVillage entry item with:

```mcfunction
/give @s myvillage:cultivation_handbook
```

GuideME's own diagnostic entry points are:

```mcfunction
/guidemec myvillage:cultivation open
/guideme open @s myvillage:cultivation
/guideme give @s myvillage:cultivation
/give @s guideme:guide[guideme:guide_id="myvillage:cultivation"]
```

For authoring, edit only the root `guidebook/` tree and launch:

```bash
./gradlew runGuide
```

That run watches the same source copied into the jar and validates/opens the
guide at startup. Do not add a second checked-in Markdown mirror below
`src/main/resources`. GuideME's item-tooltip hotkey applies in inventory-style
screens, not to a placed block under the crosshair. Owner review of 0.25.1 found
that sharing `G` with MyVillage's stop-meditation binding made GuideME's hotkey
unavailable. The current fix leaves `G` to GuideME and changes MyVillage's
default stop key to `X`; it adds no GuideME-specific interception, remapping, or
automatic migration. Existing installations that saved the old `G` binding
must reset or rebind `Stop Meditation` once in Controls.

Run the focused automated gate before client review:

```bash
python3 tools/validate_guideme_cultivation_guide.py
python3 -m unittest tools.tests.test_validate_guideme_cultivation_guide
./gradlew test
./gradlew build
./gradlew runGuide
./gradlew runAcceptanceServer
```

The GuideME validator and its tests need a `python3` that has PyYAML; without
it they fail with "PyYAML is required to validate GuideME frontmatter".

Client startup and dedicated-server startup prove dependency, page parsing,
packaging, and side safety only. They do not prove rendering or interaction.

Owner real-client review on 2026-07-14 confirmed the guide UI, Chinese default
surface, three-page navigation, and representative component rendering. The
0.25.1 `G` item-index hotkey failed globally; after releasing `G` in the current
fix, the post-fix hotkey behavior remains `not_verified` until retested.

| GuideME real-client acceptance surface | Result |
|---|---|
| Guide discovery and Chinese-default fallback | `pass` |
| Complete English switching | `not_verified` |
| Three-page navigation | `pass` |
| Search for 打坐, 灵石, and 冲关 | `not_verified` |
| Both stele item-index jumps | `not_verified` |
| Spirit-stone and ore item-index jumps | `not_verified` |
| `ItemLink` and `BlockImage` rendering | `pass` |
| Live configured key displays after remapping | `not_verified` |
| Root-source live reload | `not_verified` |
| Handbook first open and remembered-page reopen | `not_verified` |
| GuideME `G` item-index hotkey after MyVillage stop moved to `X` | `not_verified` |
| Existing H-screen and cultivation gameplay regression | `not_verified` |

## Cultivation Playable Loop

**当前已实现“测灵觉醒 -> 基础吐纳诀传承 -> 普通/灵石打坐 -> 修为结算 -> 确定性冲关”的第一段可玩循环；炼气四层是本版终点，不代表炼气后续层级与筑基已经开放。**

The foundation provides a server-authoritative immutable player profile (v4 since 0.37.0),
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
The panel has a page rail on the left, a header (name, realm and stage,
calendar, remaining lifespan) and a footer (session state) that stay visible on
every page, and a body that scrolls when a small window cannot hold a page:

| Page (zh / en) | Shows |
|---|---|
| 内视 / Profile | The current realm's stage ladder, progress and stability bars, power, spiritual affinity, calendar and lifespan, the spiritual root's shares, and the next advancement's target and conditions. |
| 修炼 / Meditation | A meridian diagram: a figure seated cross-legged with the small circuit (督脉 up the back, 任脉 down the front), its acupoints, and the lower dantian, lit and animated for the session state (0.32.0). Beside it: what normal and spirit meditation yield and cost, backpack spirit stones, the advancement's target, conditions, duration, stability cost and interruption loss, and the normal, spirit, advance, and stop buttons labelled with their bound keys. While you study a manual (0.38.0) a 研读《功法名》 card with the comprehension bar, the next gate, and the stop key takes the place of the normal and spirit cards (see [Technique Manuals](#technique-manuals-0380)). |
| 功法 / Techniques | Learned techniques grouped into 心法, 绝技, 身法, and 炼体 cards (0.37.0), each with mastery, chips for category, grade (凡阶..天阶), school, heritage and its position (such as `太白剑脉 2/4`), and elements, and its requirement. The running core technique is marked 运转中; every other learned core technique has a 运转此心法 button (see [Techniques And Heritages](#techniques-and-heritages-0370)). |
| 天下 / World | The world ledger (命簿, see [World Simulation](#world-simulation-世界模拟--命簿)), read-only, in five sub-views chosen from the buttons under the body (0.36.0): 总览 (era date, settlement state, population, realms, sects, the five foremost people), 宗门 (every sect, active first), 人物 (search by name or Daoist title as you type), 纪事 (the latest notable and major events, newest first, with their causes), and 此地 (your region, the sects seated there with the distance to their gate, the strongest people present, recent events). Click a sect or person row, or a name inside a detail, to open it; 「← 返回」 goes back. |

Profile values remain read-only: each Meditation button sends only the same bounded action
intent as V/B/X/N, the 运转此心法 button sends only the technique id, and the server revalidates every result. `H` or Escape closes
the panel without stopping cultivation, and reopening it returns to the page it
was left on. While the 人物 search field has focus, `H` types into it instead
of closing the panel (Escape still closes). The 天下 page refreshes the open
view at most every 2.5 seconds and is open to every player; pause, resume, and
advance stay `/myvillage world` commands. Structure and how to add a page:
`docs/ai-kb/37_cultivation_panel.md`.

The current profile is schema version `4` (0.37.0): the v3 fields (all v2
fields plus non-negative `spiritualAffinity`, whose new/reset/migrated default
is `10`) plus the optional running core technique `active_core_technique`. The
version-dispatched codec preserves explicit v1, v2, and v3 decode paths,
migrates v1 and v2 into v3 and v3 into v4 (running core technique = Basic
Breathing when it is learned, otherwise none) without losing unknown ids,
progress, lifespan, or reserve, and writes only v4 afterward. `meditationQiReserve` remains stored for save
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
| `myvillage:school` (0.37.0) | `src/main/resources/data/myvillage/myvillage/school/` |
| `myvillage:heritage` (0.37.0) | `src/main/resources/data/myvillage/myvillage/heritage/` |

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
`ticks_per_day = 24000`, `days_per_year = 24` and `days_per_week = 6`: one
cultivation day is one Minecraft day-night length, a week is six days, and a
year is four weeks, `576000` effective ticks. The week is only a display unit
(the H header, the 内视 calendar card and the 天下 date row show year, week and
day); weeks restart with each year, and a `days_per_year` that is not a multiple
of `days_per_week` is allowed with a load/reload warning and a short last week.
At 20 TPS with someone online the defaults convert as:

| Unit | Effective ticks | Real time online |
|---|---:|---:|
| 1 day | 24000 | 20 minutes |
| 1 week (6 days) | 144000 | 2 hours |
| 1 year (24 days) | 576000 | 8 hours |
| Mortal maximum (80 years) | 46080000 | 640 hours |

The Overworld `SavedData` calendar advances once per
server tick while at least one player is online, in any game mode. A player's
lifespan advances only while that player is online, alive, and in
survival/adventure. Sleep, `/time set`, daylight-cycle rules, dimension, and
offline wall time do not drive either clock. Realm definitions currently grant
maximum lifespans of `80` mortal, `120` Qi Refining, and `240` Foundation years;
warnings are derived at `10`, `5`, and `1` remaining years.

Both counters store raw effective ticks. Changing any time-scale setting does
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
/myvillage cultivation core <target> <technique_id>     # run another learned core technique (心法); 0.37.0
/myvillage cultivation manual <target> <technique_id>   # give that technique's manual (秘籍); 0.38.0
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
| `core` | `xinfa` |
| `manual` | `miji` |
| `awaken` | `juexing` |
| `initiate` | `rumen` |

Run the complete playable-loop and affinity/UI revision handoff gates:

```bash
openspec validate --specs --strict
for spec in spirit-stone-resources cultivation-lifespan-calendar cultivation-meditation cultivation-gain cultivation-advancement; do openspec validate "$spec" --type spec --strict; done
python3 tools/validate_cultivation_core.py
python3 tools/validate_cultivation_initiation.py
python3 tools/validate_spirit_stone_resources.py
python3 tools/validate_cultivation_lifespan.py
python3 tools/validate_cultivation_meditation.py
python3 tools/validate_cultivation_gain.py
python3 tools/validate_cultivation_advancement.py
python3 tools/validate_guideme_cultivation_guide.py
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

The five original playable-loop changes and the affinity/UI revision are now
archived and synchronized into baseline specs. Validate those capabilities as
specs; do not run their old change names as though they were still active.

### Techniques And Heritages (0.37.0)

One technique catalogue feeds both the player's technique registry and the
world ledger: 129 techniques (心法 core, 绝技 active, 炼体 body; grades 黄 玄
地 天; 131 since 0.40.0 added two 身法 movement techniques) plus the hand-written 凡阶 Basic Breathing, four schools (流派: sword,
spear, fist, flying sword), and three heritages (传承: 太白剑脉, 青帝木脉,
万劫金身脉), each a chain of four techniques. Edit the source tables in
`tools/technique_catalogue/` and regenerate; the datapack files under
`data/myvillage/myvillage/{technique,school,heritage}/`, the ledger's
`techniques.json` and `heritages.json`, and the technique, school, and heritage
names in both language files are its output:

```bash
/usr/bin/python3 tools/gen_technique_catalogue.py           # write what differs
/usr/bin/python3 tools/gen_technique_catalogue.py --check   # also a release-gate step
```

One core technique (心法) runs at a time; initiation leaves Basic Breathing
running. The running one scales meditation progress (not stability, mastery,
or stone cost) by its grade's cultivation multiplier, plus the element bonus
when the spiritual root's affinity in one of its elements reaches the
threshold, all from `data/myvillage/world_sim/rules.json`; Basic Breathing
stays at ×1.0, so the numbers above are unchanged. A 玄阶 technique with a
matching root turns a normal batch of 10 into 14 and a spirit batch of 50 into
72. Switching to another learned core technique, from the 功法 page's
运转此心法 button or with

```mcfunction
/myvillage cultivation core <target> <technique_id>
/myvillage xiulian xinfa <target> <technique_id>
```

costs `techniques.switch_progress_loss` (0.3) of the current progress (散功),
except between two techniques of one heritage chain; switching to the running
one changes nothing. `info` shows the running core technique. Active and
body techniques can be learned and are listed, but have no effect yet; since
0.40.0 movement techniques drive the dodge key (see "Movement Dodge (0.40.0)").
Payload protocol is `10` (`11` since 0.38.0, `12` since 0.40.0), so client and
server need the same jar.

In the world ledger, some sects hold a heritage from genesis, `world sect`
and the 天下 sect detail show it as 传承, a sect that ends loses it, a later
founder who practises one of its techniques can revive it, and its manuals
turn up in ruins while it is lost. Details:
`docs/ai-kb/41_technique_system.md`; design: `docs/technique-system-brief.md`.

### Technique Manuals (0.38.0)

A technique is learned by reading its manual (秘籍). There are 16 manual
items, one per category and grade, and the technique a manual teaches is a
data component on the stack, like the enchantment on an enchanted book:

| Item | Category | Grades |
|---|---|---|
| `myvillage:manual_core_<grade>` | 心法 core, thread-bound book | `huang` 黄, `xuan` 玄, `di` 地, `tian` 天 |
| `myvillage:manual_active_<grade>` | 绝技 active, scroll | same |
| `myvillage:manual_movement_<grade>` | 身法 movement, folded book | same |
| `myvillage:manual_body_<grade>` | 炼体 body, jade slip | same |

The icon of a category is tinted by grade (yellow, blue, brown, pale gold);
rarity runs common, uncommon, rare, epic, and 天阶 manuals glint. A manual
with a technique is named `《功法名》` (`Manual: …` in English) and its tooltip
lists category and grade, school, elements, heritage position (such as
`太白剑脉 2/4`), requirement, `参悟 n%` once you have read some of it, and
`右键盘坐研读`. Without a technique it is a blank manual (`空白秘籍 · 绝技 ·
玄阶`); a technique that does not exist or does not match the item's category
or grade makes a red `残损秘籍` that cannot be read. The `myvillage:main`
creative tab lists the 16 blank manuals and then one manual per technique
(129; 131 since 0.40.0). Other ways to get one:

```mcfunction
/myvillage cultivation manual @s myvillage:gengjin_jianjue
/myvillage xiulian miji @s myvillage:gengjin_jianjue
/give @s myvillage:manual_active_xuan[myvillage:technique="myvillage:gengjin_jianjue"]
```

The command picks the right item for the technique (Basic Breathing, 凡阶, has
no manual). With `/give`, the item and the technique must agree or the manual
is damaged; add `myvillage:comprehension=<points>` to start part-read. The
icons come from `python3 tools/gen_manual_textures.py` (`--check` is a
release-gate step); never edit the PNGs.

Studying (研读) is a third kind of meditation. In survival or adventure, with
an awakened root and Basic Breathing learned, right-click a manual of a
technique you have not learned and whose requirements you meet (realm, stage,
element affinity; for a chain technique, the previous one learned). Each
refusal is a chat line. You sit down: 40 ticks of preparation, then every 10
ticks the manual gains `spiritual affinity × (1 + element bonus)` points,
rounded down; the bonus is 0.15 when your root has at least 15 % in one of
the technique's elements, and the grade does not multiply study. Keep the
manual in the slot you used it from; moving it ends the study. Higher grades
have gates: crossing one costs stability once, and with too little stability
the study stops on the gate (`参悟至此，神思不济`) until you have more. At the
total the technique is learned (a core technique starts running if none was), the
manual is used up, and the study ends. Anything that interrupts meditation
(moving, damage, the stop key `X`, the stop button, logging out) interrupts
study, and the points read so far stay on the manual; right-click again to go
on. Right-clicking a manual during another session first ends that session.

| Grade | Points | Gates (at points) | Stability per gate | Time at affinity 10 | With an element match |
|---|---:|---|---:|---:|---:|
| 黄 | 4000 | none | 0 | 3 min 20 s | about 3 min |
| 玄 | 12000 | 6000 | 50 | 10 min | about 9 min |
| 地 | 36000 | 12000, 24000 | 100 | 30 min | about 27 min |
| 天 | 96000 | 24000, 48000, 72000 | 150 | 80 min | about 73 min |

Times are real minutes at 20 TPS without the preparation or gate stops. The
numbers are data (`study_by_grade` in `tools/technique_catalogue/rules.json`,
written into each technique file). 地 and 天 techniques need Foundation early,
which play cannot reach in this release. While you study, the 修炼 page shows
a 研读《功法名》 card and the session reads 研读中. The meditation status
carries the study progress, so the payload protocol is `11`. Details:
`docs/ai-kb/41_technique_system.md` ("Manuals and study"); brief:
`docs/technique-manual-brief.md`.

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
5. Open `H`. Confirm the Profile, Meditation, and Techniques pages remain within
   the panel at normal and constrained GUI scales (below 427x240 GUI pixels a page
   stacks its cards and the body scrolls). Profile must show schema 4, affinity 10,
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
   creative/spectator and confirm the shared calendar keeps advancing while
   personal age pauses; disconnect the only player and confirm both pause. With
   a second survival/adventure player online, confirm the shared calendar
   advances while the creative/spectator or offline first player's personal age
   does not. Separately verify reconnect,
   dimension change, death/respawn, ordinary save/restart, and clean-stop flushes
   without double age. Also wait through one 600-tick interval to verify the
   periodic batch path separately.
10. In a backed-up disposable copy, change `cultivation_time.ticks_per_day`,
    `days_per_year` or `days_per_week` and reload/restart. Confirm the operator warning and immediate
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
| H Profile/Meditation tabs, text fit, values, buttons, and status feedback (the two-tab screen through 0.30.0) | `pass` |
| V/B/N and H-button parity, preparation, camera movement, and interruptions | `pass` |
| Default X stop key after releasing GuideME G | `not_verified` |
| Affinity progress, `1/1/2/3` direct costs, rollback, cap, and downgrade | `pass` |
| Pre-cap stability lock, post-cap affinity gain, no stone cost, and `500/550/600/650` caps | `pass` |
| `1000/1100/1200/1300` advancement rules, stability halving, interruption, Qi-IV ceiling | `pass` |
| Shared calendar, personal online lifespan, lifecycle persistence, multiplayer | `not_verified` (the 2026-07-13 `pass` covered the old rule; on 2026-10-06 the shared calendar began advancing in every game mode, so step 9 must be redone in game) |
| Config reinterpretation warning and non-lethal exhaustion | `pass` |

The 0.31.0 panel replaced the screen that verdict covered. Its own surfaces:

| 0.31.0 panel surface | Result |
|---|---|
| Three pages, header, footer, and rail: layout, text fit, and look | `not_verified` |
| Four action buttons on the Meditation page and their status feedback | `not_verified` |
| Hover, focus, wheel scrolling, and reopening on the last page | `not_verified` |
| 0.32.0 meridian diagram on the Meditation page: figure, channels, qi flow per state, and look | `not_verified` |
| 0.36.1 year/week/day dates in the header, the 内视 calendar card, and the 天下 总览 row: values and text fit at 320x240 | `not_verified` |

Nothing of the technique system has been looked at in a real client yet; the
automated checks are the catalogue `--check`, the world-sim and five
cultivation validators, and the Gradle tests.

| Technique system (0.37.0) surface | Result |
|---|---|
| Catalogue generator `--check`, datapack/ledger cross-checks, definition decoding, switch rules, progress factor, payload codec, page grouping, meridian route, heritage genesis/loss/rekindling, save round trips | `pass` (automated) |
| 功法 page: 心法/绝技/身法/炼体 cards, chips, and text fit at GUI 480x270, 427x240, and 320x240 in Chinese and English | `not_verified` |
| 运转此心法 button switches the running core technique; 运转中 moves; hover and click | `not_verified` |
| 散功 on switch: progress drops by 30 % outside a chain, not at all inside one | `not_verified` |
| `/myvillage cultivation core` / `xiulian xinfa` and the `info` line on a server | `not_verified` |
| Meditation gain with a graded running core technique (for example 14 per normal batch at 玄阶 with a matching root) | `not_verified` |
| Meridian diagram unchanged with Basic Breathing and with a catalogue core technique running | `not_verified` |
| 天下 sect detail 传承 row | `not_verified` |
| `/myvillage world sect <id>` 传承 line | `not_verified` |
| Old save migration: profile v3 → v4 (Basic Breathing running) and world-sim payload version 1 → 2 | `not_verified` in game (unit-tested) |
| Heritage chronicle lines (genesis, lost, rekindled, ruin find) in a real client and as rumors | `not_verified` |
| The owner's verdict on the page and the switch rules | `not_verified` |

Nothing of the technique manuals has been looked at in a real client yet; the
automated checks are the catalogue and manual-texture `--check`s, the
cultivation validators, and the Gradle tests (item rules, study start checks,
settlement and gates, status codec, panel readouts).

| Technique manuals (0.38.0) surface | Result |
|---|---|
| Manual validity, names, creative variants, study numbers, start checks, settlement, gates, completion, status codec, command, panel readouts | `pass` (automated) |
| The four icons and their grade tints, rarity colours, and the 天阶 glint in the inventory and in hand | `not_verified` |
| Tooltip lines (category · grade, school, elements, heritage, requirement, `参悟 n%`, use hint) and the blank and damaged names | `not_verified` |
| Creative tab: 16 blank manuals, then the 129 technique manuals | `not_verified` |
| Right-click starts a study (preparation, then 研读中) without a hand swing; each refusal message | `not_verified` |
| Interruption (move, damage, `X`, logout) keeps the points on the manual; right-click continues | `not_verified` |
| Moving the manual out of its slot stops with the manual-lost message | `not_verified` |
| Gate: stability paid once when enough; stop on the gate with the cost and shortfall message when not | `not_verified` |
| Completion learns the technique, consumes the manual, and a core technique starts running if none was | `not_verified` |
| 修炼 page study card (title, bar, percent, gate line, stop hint) at GUI 480x270, 427x240, and 320x240 in Chinese and English | `not_verified` |
| `X` and the stop button end a study; the start buttons are disabled while studying | `not_verified` |
| `/myvillage cultivation manual` / `xiulian miji` and the `/give` component form | `not_verified` |
| The owner's verdict on the study durations, the gate (神思) rule, and the art | `not_verified` |

Use only `pass`, `fail`, or `not_verified`. A `fail` records the observed mismatch
and reproduction steps; `not_verified` means the surface was not directly
observed and does not block truthful reporting of automated results.

Profile, time, and session status travel only server-to-client. The only two
client-to-server cultivation payloads are the bounded meditation/advancement
intent enum used by both keys and H buttons, which carries no identity,
coordinate, velocity, affinity, resource count, rate, profile value, target
stage, or result, and (0.37.0) the core-technique switch, which carries only a
technique id; the server decides whether the switch is allowed and what it
costs. A study (0.38.0) starts by using a manual, which is ordinary item use,
and stops with the existing stop intent. Client caches and button state are presentation-only and clear on
disconnect. The 天下 page's world query (0.36.0) is a separate read-only
payload of the world simulation, not a cultivation payload.

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
- `myvillage:demon_wolf` (妖狼): hostile beast with its own generated model and clips and two data-driven moves; summon and spawn egg only, no drop
- /myvillage beast move <targets> <move_id> | status <targets> | debug on|off
- the world ledger (命簿): sects and named cultivators simulated by the day, saved per world, with chat rumors
- /myvillage world [info] | sects [all] | sect <id|name> | sect <id> build [here] | sect <id> join|leave <player> | sect <id> rank <player> <rank> | sect <id> shelves [place] | sect <a> war <b> | sect <id> destroy | player <player> | gates [retry] | person <name> | chronicle [1-50] | here | pause | resume | advance <1-3650>
- player sect entry (0.41.0): a player's ledger record, joining and leaving at the gate steward (守山执事), yearly promotion, the 我的宗门 card, framed gate building near a player; the scripture hall (0.42.0): scripture shelves (经架) in a ledger compound's pavilions lending sect manuals by rank; sect tasks and apprenticeship (0.43.0): one task a year from the steward for contribution, a master whose guidance speeds meditation; world response (0.44.0): news of the player's sect in chat anywhere, hostile sects refuse visitors
- the H panel's 天下 page: the world ledger read-only for every player (overview, sects, people search, chronicle, here), with drill-down into sects and people
- world-sim avatars: ledger members shown as never-saved, invulnerable cultivators on a built compound's courtyard while a player is near
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
