## MODIFIED Requirements

### Requirement: Structure JSON validation checks schema, bounds, metadata, and modset-aware block ids
The Structure JSON validator SHALL check JSON shape, size and coordinate bounds, palette resolution, supported operations, metadata rules, and block id existence relative to the active modset profile. Under the `vanilla` profile only Minecraft `1.21.1` vanilla block ids are accepted. Under the `full` profile a non-`minecraft` block id is accepted when its namespace is in the catalog's confirmed mod set and the id exists in `exmod/mod_block_catalog.json`; vanilla `minecraft` id existence is still checked against the `1.21.1` registry.

#### Scenario: A non-vanilla block id is referenced under the vanilla profile
- **WHEN** Structure JSON validation runs under the `vanilla` profile and sees a blockstate outside the `minecraft` namespace
- **THEN** validation SHALL fail because the `vanilla` profile permits only vanilla Minecraft `1.21.1` blocks.

#### Scenario: A confirmed mod block id is referenced under the full profile
- **WHEN** Structure JSON validation runs under the `full` profile and sees a non-`minecraft` blockstate
- **AND** the id's namespace is in the catalog's confirmed mod set and the id exists in the catalog
- **THEN** validation SHALL accept the id.

#### Scenario: An unstaged-namespace block id is referenced under the full profile
- **WHEN** Structure JSON validation runs under the `full` profile and sees a non-`minecraft` blockstate whose namespace is not in the confirmed mod set
- **THEN** validation SHALL fail because that namespace is not part of the active modset.

### Requirement: Exported building library validation checks actual mod resources
The building library resource validator SHALL validate exported NBT and mcfunction files from the mod resources tree, not in-memory generation state. Block-id legality SHALL be evaluated against the active modset profile: vanilla `minecraft` ids are checked against the `1.21.1` registry, while non-`minecraft` ids are accepted only under a profile whose active namespaces include theirs and only when the id exists in the catalog.

#### Scenario: A generated NBT file is missing
- **WHEN** the validator expects `small_house_001.nbt`
- **AND** the file is absent from `src/main/resources/data/myvillage/structure/`
- **THEN** validation SHALL fail with `missing_file`.

#### Scenario: A mod id appears under the vanilla profile
- **WHEN** the building library validator runs under the `vanilla` profile
- **AND** a structure palette contains a non-`minecraft` block id
- **THEN** validation SHALL fail with `forbidden_mod_blocks` naming the offending id.

#### Scenario: A confirmed mod id appears under the full profile
- **WHEN** the building library validator runs under the `full` profile
- **AND** a structure palette contains a non-`minecraft` id from a confirmed namespace that exists in the catalog
- **THEN** validation SHALL accept that id
- **AND** a non-`minecraft` id absent from the catalog SHALL fail with `unknown_mod_blocks`.

### Requirement: Exported NBT validation checks roof and interior heuristics
The generated-structure NBT validator SHALL check parseability, non-empty palettes and blocks, valid size, roof-like blocks in upper layers, non-empty top layers, key building materials, and expected archetype signature markers for houses, blacksmiths, civic structures, and cultivation structures. It SHALL additionally enforce modset-aware block-id legality for non-`minecraft` ids under the active profile.

#### Scenario: A house NBT lacks a furnace marker
- **WHEN** generated-structure validation checks a structure whose filename starts with `small_house`, `medium_house`, or `big_house`
- **AND** no present blockstate contains `furnace`
- **THEN** validation SHALL fail with a house function-block error.

#### Scenario: A cultivation sect NBT lacks sect-form markers
- **WHEN** generated-structure validation checks a cultivation sect standalone or compound structure
- **THEN** the palette or block layout SHALL contain expected sect markers from the cultivation form/material vocabulary
- **AND** validation SHALL fail with a cultivation-signature error if those markers are absent.

#### Scenario: A non-confirmed mod id appears in a generated structure
- **WHEN** generated-structure validation runs under the `full` profile
- **AND** a palette contains a non-`minecraft` id whose namespace is not in the confirmed mod set
- **THEN** validation SHALL fail with `forbidden_mod_blocks`.

### Requirement: Compound validation checks generated resources
The compound library validator SHALL validate generated courtyard and town-block report data and exported mod resources, including NBT files and generated place/gallery functions. It SHALL enforce modset-aware block-id legality for non-`minecraft` ids under the active profile.

#### Scenario: The Chinese courtyard library is validated
- **WHEN** `tools/validate_compound_library.py --count 6` succeeds
- **THEN** six distinct compound structures SHALL be validated
- **AND** the validator SHALL confirm exported NBTs include compound landscape markers such as water and planting
- **AND** generated place/gallery functions SHALL exist for the compound library.

#### Scenario: The cultivation town block library is validated
- **WHEN** `tools/validate_compound_library.py --group cultivation_town --count 6` succeeds
- **THEN** six distinct town-block structures SHALL be validated
- **AND** the validator SHALL confirm exported NBTs include compound landscape markers such as water and planting
- **AND** generated `place/cultivation_town_*.mcfunction` and `gallery/cultivation_town.mcfunction` files SHALL exist.

#### Scenario: A cultivation town block is validated under the vanilla profile
- **WHEN** `tools/validate_compound_library.py --group cultivation_town --profile vanilla` runs against output that contains mod ids
- **THEN** validation SHALL fail with `forbidden_mod_blocks`
- **AND** the same output SHALL validate clean under the `full` profile.

## ADDED Requirements

### Requirement: Generation and validation resolve modset legality from one source
Both the generators and the validators SHALL resolve the set of legal external-mod block ids for a named profile from the same modset resolver, which reads `exmod/mod_block_catalog.json`. The `vanilla` profile SHALL permit only the `minecraft` namespace; the `full` profile SHALL permit `minecraft` plus the catalog's confirmed mod namespaces and only the block ids the catalog lists for them.

#### Scenario: Generators accept a profile
- **WHEN** `generate_building_library.py`, `generate_compound_library.py`, or `generate_civic_library.py` is run with `--profile vanilla`
- **THEN** generation SHALL filter slots to the `minecraft` namespace and emit no non-`minecraft` id.

#### Scenario: Full profile generation is unchanged
- **WHEN** an affected library is generated with `--profile full`
- **THEN** the output SHALL be byte-identical to the pre-change output, because every shipped slot id belongs to a confirmed namespace.

#### Scenario: An unknown profile name is rejected
- **WHEN** a generator or validator is given a profile name other than `vanilla` or `full`
- **THEN** it SHALL fail with an error naming the unknown profile rather than silently defaulting.
