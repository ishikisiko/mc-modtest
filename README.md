# Minecraft Structure JSON -> NBT Pipeline Example

This is a minimal pipeline for generating and validating vanilla Minecraft structure NBT files without compiling a NeoForge mod.

The current target verification loop is:

```text
structure JSON -> validate -> convert to .nbt -> export to test world -> /place template
```

## Files

```text
tools/json_to_nbt.py              Convert the custom JSON DSL into gzipped vanilla structure NBT
tools/validate_structure_json.py  Validate JSON DSL operations and blockstate syntax
tools/export_to_world.py          Copy a generated .nbt into a Minecraft test world's generated structures folder
examples/test_house_01.json       First technical validation house, legacy blocks[] DSL
examples/test_house_02.json       Second house, ops[] DSL with fill/set operations and a sloped roof
out/                              Generated .nbt output files
```

## Supported JSON DSL

The converter remains compatible with the original legacy form:

```json
{
  "blocks": [
    { "pos": [0, 0, 0], "state": "floor" }
  ]
}
```

It also supports the newer operation form:

```json
{
  "ops": [
    { "op": "set", "pos": [4, 1, 1], "state": "door_lower" },
    { "op": "fill", "from": [1, 0, 1], "to": [7, 0, 7], "state": "foundation" }
  ]
}
```

`state` can reference a palette alias or a direct namespaced blockstate such as:

```text
minecraft:cobblestone
minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
```

## Validate test_house_01

```bash
python tools/validate_structure_json.py examples/test_house_01.json
```

## Convert test_house_01

```bash
python tools/json_to_nbt.py examples/test_house_01.json out/test_house_01_1_21_1.nbt --mc-version 1.21.1
```

## Validate test_house_02

```bash
python tools/validate_structure_json.py examples/test_house_02.json
```

## Convert test_house_02

```bash
python tools/json_to_nbt.py examples/test_house_02.json out/test_house_02_1_21_1.nbt --mc-version 1.21.1
```

For Minecraft 1.20.1:

```bash
python tools/json_to_nbt.py examples/test_house_02.json out/test_house_02_1_20_1.nbt --mc-version 1.20.1
```

Default DataVersion values in the script:

```text
1.20.1 -> 3465
1.21.1 -> 3955
```

You can override them with `--data-version`.

## Export to a test world

After creating/opening a Minecraft world named `StructureTest`, run:

```bash
python tools/export_to_world.py out/test_house_02_1_21_1.nbt --world StructureTest --namespace myvillage --name test_house_02
```

The tool copies the NBT file to:

```text
.minecraft/saves/StructureTest/generated/myvillage/structures/test_house_02.nbt
```

On Windows, the default saves root is:

```text
%APPDATA%\.minecraft\saves
```

On Linux:

```text
~/.minecraft/saves
```

On macOS:

```text
~/Library/Application Support/minecraft/saves
```

You can override it:

```bash
python tools/export_to_world.py out/test_house_02_1_21_1.nbt --world StructureTest --namespace myvillage --name test_house_02 --saves-dir "D:/Minecraft/saves"
```

## Test in game

In the test world, run:

```mcfunction
/place template myvillage:test_house_02 ~ ~ ~
```

Or use a structure block with the structure name:

```text
myvillage:test_house_02
```

## Current scope

Implemented:

```text
- JSON DSL validation
- set/fill operation expansion
- palette alias resolution
- basic blockstate string syntax validation
- duplicate/overwritten coordinate reporting
- vanilla gzipped structure NBT writing
- export helper for the generated/<namespace>/structures folder
- test_house_02 with foundation, logs, walls, glass windows, door, lantern, and sloped spruce roof
```

Not implemented yet:

```text
- Full Minecraft registry-backed block/property validation
- Block entity NBT, such as chests, signs, command blocks, spawners
- Entities
- Loot tables
- Schematic or Litematic export
- NeoForge worldgen integration
- Jigsaw/template pool/structure_set generation
```
