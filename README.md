# myvillage - NeoForge Town Structure Validation Mod

This repository builds a NeoForge 1.21.1 mod jar containing generated
`myvillage` structure templates. The current validation target is the Mod
resource layer: structures must be packed in the jar and placeable in game via
debug commands, without enabling worldgen.

The long-term project goal is broader than a simple village generator. The
current structure library is an early resource-validation layer for a future
town-generation system with multiple settlement categories, varied housing
types, functional buildings, roads and town pieces, and possible NPC-related
systems when the data and runtime pipeline are ready.

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
library, the civic library, the cultivation town library, and the cultivation
sect standalone/compound libraries:

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
src/main/resources/data/myvillage/structure/cultivation_house_001.nbt ... cultivation_house_003.nbt
src/main/resources/data/myvillage/structure/cultivation_shop_001.nbt ... cultivation_shop_003.nbt
src/main/resources/data/myvillage/structure/cultivation_inn_001.nbt ... cultivation_inn_003.nbt
src/main/resources/data/myvillage/structure/cultivation_market_001.nbt ... cultivation_market_003.nbt
src/main/resources/data/myvillage/structure/town_shrine_001.nbt ... town_shrine_003.nbt
src/main/resources/data/myvillage/structure/sect_gate_001.nbt ... sect_gate_002.nbt
src/main/resources/data/myvillage/structure/sect_main_hall_001.nbt ... sect_main_hall_002.nbt
src/main/resources/data/myvillage/structure/scripture_pavilion_001.nbt ... scripture_pavilion_002.nbt
src/main/resources/data/myvillage/structure/alchemy_room_001.nbt ... alchemy_room_002.nbt
src/main/resources/data/myvillage/structure/disciple_quarters_001.nbt ... disciple_quarters_002.nbt
src/main/resources/data/myvillage/structure/cultivation_sect_001.nbt ... cultivation_sect_002.nbt
```

The Gradle build also runs this batch generator before packing resources, so
v0.5 jars are expected to contain individual buildings, compound structures,
and civic/cultivation structures.

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

The compound exporter currently uses single structure NBT files. Generated
lots stay within a 48-block footprint on each horizontal axis, so no
multi-template stamping function is needed yet.

## Generate Cultivation Libraries

Generate the mortal town group and immortal sect group directly:

```bash
python3 tools/generate_building_library.py --group cultivation_town --count 3
python3 tools/generate_building_library.py --group cultivation_sect --count 2
python3 tools/generate_compound_library.py --group cultivation_sect --count 2 --base-seed 20260616
```

These commands write group-specific reports under `reports/`:

```text
reports/cultivation_town_building_library_report.json
reports/cultivation_sect_building_library_report.json
reports/cultivation_sect_compound_library_report.json
```

## Validate Generated NBT

Run the NBT-level integrity checks after generation:

```bash
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_civic_library.py
python3 tools/check_style_policy.py
python3 tools/check_cultivation_forms.py
```

The validator checks that files exist, palettes and blocks are non-empty, roof
blocks exist, the top layers are not empty, key stairs/slabs/logs/planks are
present, gable closure is heuristically checked, and building interiors contain
the expected function blocks. Blacksmiths must contain forge-equivalent blocks;
houses must contain crafting/furnace/barrel-style utility blocks; civic
structures must contain tavern or lord-manor signature role blocks; cultivation
town and sect structures must contain their expected town/sect signatures.

## Build The Mod

Requires JDK 21.

```bash
./gradlew build
```

Confirm the jar contains the structure resources:

```bash
jar tf build/libs/*.jar | grep "data/myvillage/structure"
```

The expected jar is:

```text
build/libs/myvillage-0.5.0.jar
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
python3 tools/validate_compound_library.py --count 6
python3 tools/validate_civic_library.py
python3 tools/check_style_policy.py
python3 tools/check_cultivation_forms.py
./gradlew build
jar tf build/libs/myvillage-0.5.0.jar | grep "data/myvillage/structure"
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

The v0.5 mod registers debug commands for structure validation only; no worldgen
is registered.

List loaded templates:

```mcfunction
/myvillage list
```

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
/myvillage place cultivation_house_001
/myvillage place town_shrine_001
/myvillage place sect_gate_001
/myvillage place cultivation_sect_001
```

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
/place template myvillage:cultivation_house_001 ~ ~-1 ~
/place template myvillage:cultivation_sect_001 ~ ~-1 ~
```

Place all loaded `myvillage` blueprints in a grouped gallery with 60-block
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
/function myvillage:place/cultivation_house_001
/function myvillage:place/cultivation_sect_001
```

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
Compound Graph       tools/buildgen/compound.py     Chinese one-courtyard and cultivation sect parcel layouts
Civic Generator      tools/generate_civic_library.py tavern_001..005 + lord_manor_001..003
```

Important properties:

- Materials resolve through abstract slots such as `BASE_STONE`, `WALL_MAIN`,
  `FRAME_WOOD`, `ROOF_DARK`, `DETAIL_WOOD`, `INTERIOR_WORK`,
  `INTERIOR_STORAGE`, `INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, and
  `HERALDRY`. Cultivation sect styles may also define `SPIRIT_CRYSTAL` and
  `RITUAL_METAL`; mortal styles may omit them.
- Supported roof handlers are registered in `tools/buildgen/ops.py`. Current
  names include `gable_roof`, `cross_gable_roof`, `lean_to_roof`, Chinese
  roof-grade aliases, and `tiered_eave_roof`.
- Decoration motifs are also registered in `tools/buildgen/ops.py`; cultivation
  forms include `moon_gate`, `spirit_array`, `incense_altar`, and `cloud_rail`.
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
- Cultivation town generation uses `cultivation_town.json` with standalone
  mortal-town archetypes. Cultivation sect generation uses
  `cultivation_sect.json` with standalone sect archetypes plus a terraced axial
  compound layout.
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
- 15 generated cultivation town structures
- 10 generated standalone cultivation sect structures
- 2 generated cultivation sect compound structures
- 90 generated NBT structures in the default batch, including `test_house_03.nbt`
- test_house_03.nbt Mod resource smoke test
- /myvillage place <structure_id>
- /myvillage list
- /myvillage gallery
- /myvillage gallery original
- /myvillage gallery cultivation
- NBT integrity validation for roof/top-layer/function-block/signature checks
```

Not included:

```text
- worldgen
- jigsaw/template pool/structure_set generation
- biome placement
- entities, villagers, loot tables, or complex block entity NBT
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
acceptance still needs a v0.5 mod jar plus in-game visual inspection with
`/myvillage list`, `/myvillage place chinese_courtyard_001`,
`/myvillage place tavern_001`, `/myvillage place lord_manor_001`, and
`/myvillage place cultivation_house_001`,
`/myvillage place cultivation_sect_001`, `/myvillage gallery`,
`/myvillage gallery original`, and `/myvillage gallery cultivation`.
