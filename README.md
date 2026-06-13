# myvillage - NeoForge Structure Validation Mod

This repository builds a NeoForge 1.21.1 mod jar containing generated
`myvillage` structure templates. The current validation target is the Mod
resource layer: structures must be packed in the jar and placeable in game via
debug commands, without enabling worldgen.

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

The canonical batch command generates `test_house_03.nbt` plus the
`medieval_village` building library:

```bash
python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure
```

Expected structure output:

```text
src/main/resources/data/myvillage/structure/test_house_03.nbt
src/main/resources/data/myvillage/structure/small_house_001.nbt ... small_house_010.nbt
src/main/resources/data/myvillage/structure/medium_house_001.nbt ... medium_house_010.nbt
src/main/resources/data/myvillage/structure/blacksmith_001.nbt ... blacksmith_010.nbt
```

The Gradle build also runs this batch generator before packing resources.

## Validate Generated NBT

Run the NBT-level integrity checks after generation:

```bash
python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure
```

The validator checks that files exist, palettes and blocks are non-empty, roof
blocks exist, the top layers are not empty, key stairs/slabs/logs/planks are
present, gable closure is heuristically checked, and building interiors contain
the expected function blocks. Blacksmiths must contain forge-equivalent blocks;
houses must contain crafting/furnace/barrel-style utility blocks.

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
build/libs/myvillage-0.3.0.jar
```

## Run Client

```bash
./gradlew runClient
```

Create or open a flat test world with commands enabled.

## In-Game Debug Commands

The mod registers debug commands for structure validation only; no worldgen is
registered.

Place the smoke-test structure at the player position:

```mcfunction
/myvillage place test_house_03
```

Place a generated building directly:

```mcfunction
/myvillage place small_house_001
/myvillage place medium_house_001
/myvillage place blacksmith_001
```

Place all loaded `myvillage` blueprints in a horizontal gallery with 20-block
spacing:

```mcfunction
/myvillage gallery
```

The gallery is intended for side-by-side visual comparison across sizes and
archetypes.

## Generation Architecture

```text
Style Profile        tools/buildgen/styles/medieval_village.json
Building Archetype   tools/buildgen/archetypes.py   small_house / medium_house / blacksmith
Massing Graph        tools/buildgen/massing.py      main + wing + porch + chimney + shed nodes
Facade Grammar       tools/buildgen/facade.py       bay split, posts, jittered windows
Build Ops            tools/buildgen/ops.py          hollow_box / wall_frame / gable_roof / doorway / window_kit
Pass + Protection    tools/buildgen/passes.py       ordered passes, tag/priority grid, PROTECTED cells
Quality Check        tools/buildgen/quality.py      entrance, windows, interior, gables, forbidden blocks
Resource Export      tools/buildgen/export.py       structure NBT + gallery/place mcfunctions
```

Important properties:

- Materials resolve through abstract slots such as `BASE_STONE`, `WALL_MAIN`,
  `FRAME_WOOD`, `ROOF_DARK`, `DETAIL_WOOD`, `INTERIOR_WORK`, and
  `INTERIOR_STORAGE`.
- Supported roof types are `gable_roof`, `cross_gable_roof`, and
  `lean_to_roof`.
- Supported archetypes are `small_house`, `medium_house`, and `blacksmith`.
- `clear_inside` runs before roof generation and only carves the interior wall
  volume. Roofs and gable cells are generated later and protected by the pass
  pipeline.

## Current Scope

Included:

```text
- JSON DSL validation and JSON -> vanilla structure NBT conversion
- Batch generation into NeoForge Mod resources
- 30 medieval_village building-library structures
- test_house_03.nbt Mod resource smoke test
- /myvillage place <structure_id>
- /myvillage gallery
- NBT integrity validation for roof/top-layer/function-block checks
```

Not included:

```text
- worldgen
- jigsaw/template pool/structure_set generation
- biome placement
- entities, villagers, loot tables, or complex block entity NBT
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

The automated `validate_generated_structures.py` script checks the mechanical
parts of this list, but final acceptance still needs in-game visual inspection
with `/myvillage gallery`.
