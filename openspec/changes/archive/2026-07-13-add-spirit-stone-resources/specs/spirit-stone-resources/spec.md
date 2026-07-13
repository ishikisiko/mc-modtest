## ADDED Requirements

### Requirement: The low-grade spirit-stone resource set is complete
The mod SHALL register item `myvillage:low_grade_spirit_stone` and blocks plus
BlockItems `myvillage:spirit_stone_ore` and
`myvillage:deepslate_spirit_stone_ore`. All three SHALL appear in
`myvillage:main`, have English and Chinese names, complete item/block models and
textures, and be packaged in the practical jar.

#### Scenario: A player obtains the resource set
- **WHEN** a player opens `myvillage:main` or uses `/give` for each declared id
- **THEN** all three entries SHALL resolve with localized names and non-missing models and textures

#### Scenario: The built jar is inspected
- **WHEN** packaging validation examines the practical mod jar
- **THEN** it SHALL contain every registration-backed model, texture, blockstate, loot, tag, language, and worldgen resource declared by this capability

### Requirement: Spirit-stone ores require an iron-tier pickaxe
Both ore blocks SHALL require the correct tool for drops, SHALL belong to
`minecraft:mineable/pickaxe`, and SHALL belong to
`minecraft:needs_iron_tool`. A tool below iron tier or a non-pickaxe SHALL not
produce the ore or spirit-stone drop.

#### Scenario: A valid pickaxe mines an ore
- **WHEN** a survival player mines either ore with an iron-tier-or-better pickaxe
- **THEN** the matching loot-table path SHALL run

#### Scenario: The wrong tool mines an ore
- **WHEN** either ore is broken without a qualifying pickaxe
- **THEN** it SHALL drop neither its block nor a low-grade spirit stone

### Requirement: Ore loot supports Silk Touch and Fortune without an intermediate item
A Silk Touch qualifying pickaxe SHALL drop the exact ore block that was mined.
Ordinary qualifying mining SHALL start from one
`myvillage:low_grade_spirit_stone`, SHALL apply the vanilla `ore_drops` Fortune
formula, and SHALL apply explosion decay. The loot path SHALL NOT produce a raw
ore, smeltable intermediate, higher-grade stone, or XP reward.

#### Scenario: Silk Touch mines each target
- **WHEN** a qualifying Silk Touch pickaxe mines the stone or deepslate ore
- **THEN** exactly the matching stone or deepslate ore block SHALL be the loot-table item

#### Scenario: Fortune mines an ore
- **WHEN** a qualifying Fortune pickaxe mines either ore without Silk Touch
- **THEN** the result SHALL be low-grade spirit stones using the vanilla ore-drop bonus formula

#### Scenario: Ordinary mining has no processing chain
- **WHEN** a qualifying unenchanted pickaxe mines either ore
- **THEN** the base result SHALL be one low-grade spirit stone
- **AND** no raw-spirit-stone or smelting recipe SHALL be required

### Requirement: Spirit-stone ore uses three fixed Overworld layers
The mod SHALL define a size-`6` configured ore feature and a size-`3` small
configured ore feature, each targeting
`minecraft:stone_ore_replaceables` with `myvillage:spirit_stone_ore` and
`minecraft:deepslate_ore_replaceables` with
`myvillage:deepslate_spirit_stone_ore`. Placed features SHALL use counts
`30/3/3` for upper/middle/deep, sizes `6/6/3`, `in_square`, and `biome`.

#### Scenario: Upper ore placement is resolved
- **WHEN** `myvillage:spirit_stone_ore_upper` is loaded
- **THEN** it SHALL use count `30`, size `6`, and a trapezoid from absolute Y `80` through `384`

#### Scenario: Middle ore placement is resolved
- **WHEN** `myvillage:spirit_stone_ore_middle` is loaded
- **THEN** it SHALL use count `3`, size `6`, and a trapezoid from absolute Y `-24` through `56`

#### Scenario: Deep ore placement is resolved
- **WHEN** `myvillage:spirit_stone_ore_deep` is loaded
- **THEN** it SHALL use count `3`, size `3`, and a uniform range from the Overworld bottom through absolute Y `0`

### Requirement: A NeoForge biome modifier injects the ore only into the Overworld
One `neoforge:add_features` biome modifier SHALL add the three placed features
to Overworld biomes at generation step `underground_ores`. The implementation
SHALL NOT add these features to Nether or End biomes and SHALL NOT alter vanilla
iron features.

#### Scenario: Overworld biome features are assembled
- **WHEN** a normal Overworld biome is loaded
- **THEN** all three spirit-stone placed features SHALL be present at `underground_ores`

#### Scenario: A non-Overworld biome is loaded
- **WHEN** a Nether or End biome is assembled
- **THEN** the spirit-stone biome modifier SHALL add none of the three features

### Requirement: The first resource slice remains cultivation-neutral
This change SHALL add no item-use action, meditation conversion, current
spiritual-power effect, recipe, raw item, storage block, fragment, refining
machine, currency behavior, or middle/high-grade spirit stone. A later explicit
cultivation capability MAY consume the otherwise plain low-grade item.

#### Scenario: A low-grade spirit stone is used before cultivation integration
- **WHEN** a player uses the item in this resource-only slice
- **THEN** it SHALL perform no cultivation mutation and SHALL not consume itself

### Requirement: Mechanical evidence does not replace visual acceptance
Automated validation SHALL verify file integrity and packaging, while item and
placed-block appearance SHALL remain pending until directly observed in a real
client.

#### Scenario: Assets and build pass without client inspection
- **WHEN** validators and the practical jar build pass but no client evidence exists
- **THEN** mechanical status SHALL be green and visual status SHALL remain `human_review_pending`
