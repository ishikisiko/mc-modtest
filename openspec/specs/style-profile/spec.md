# Style Profile

## Purpose

This spec captures the current style profile implementation baseline. It is temporary and mutable; proposed changes to style vocabulary, slot semantics, or validation should be discussed with the project owner first.

## Requirements

### Requirement: Style profiles define abstract material and rule vocabularies
The building generator SHALL load style profiles from `tools/buildgen/styles/<style_id>.json` and expose abstract material slots, allowed roof types, allowed wall types, allowed opening styles, allowed motifs, forbidden block fragments, variation rates, and proportion rules. Style vocabulary validation SHALL require every listed roof type and motif to exist in the corresponding form registry.

#### Scenario: Loading the medieval village style
- **WHEN** the generator requests style id `medieval_village`
- **THEN** it SHALL load `tools/buildgen/styles/medieval_village.json`
- **AND** the loaded profile SHALL include `material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, and `proportions`.

#### Scenario: Loading the Chinese courtyard style
- **WHEN** the generator requests style id `chinese_courtyard`
- **THEN** it SHALL load `tools/buildgen/styles/chinese_courtyard.json`
- **AND** the loaded profile SHALL include `material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, and `proportions`
- **AND** `allowed_roof_types` SHALL include `chinese_flush_gable`, `chinese_overhang_gable`, `chinese_half_hip`, and `chinese_round_ridge`.

#### Scenario: Loading a style with an unknown form
- **WHEN** a style profile lists an `allowed_roof_types` or `allowed_motifs` entry that is not registered
- **THEN** loading the style SHALL fail with an error identifying the unknown form name.

### Requirement: Build operations resolve primary materials through style slots
Build operations SHALL use style material slots rather than hardcoding concrete block choices for primary building materials.

#### Scenario: A roof block is needed
- **WHEN** a build operation needs roof stairs, roof slabs, or roof planks
- **THEN** it SHOULD resolve them through the `ROOF_DARK` slot
- **AND** it SHOULD preserve the final full Minecraft blockstate string.

### Requirement: Plaque bindings dispatch independently of the SIGNAGE slot
A style profile's plaque placement SHALL be driven by `data/myvillage/plaque_bindings.json`, not by the `SIGNAGE` material slot. When a plaque binding exists for an archetype, the build-gen facade-detail pass SHALL invoke the plaque placement op (`place_wall_plaque` or `place_hanging_plaque` with the binding's `frame`, `orientation`, `mount`, and `inscription`) instead of `ops.wall_hanging` with the `SIGNAGE` slot. When no plaque binding exists for the archetype, the existing `SIGNAGE` slot dispatch SHALL run unchanged.

#### Scenario: A doorway has a plaque binding
- **WHEN** the facade-detail pass runs for an archetype with `entry_signage=true`
- **AND** `plaque_bindings.json` has an entry for that archetype
- **THEN** the pass SHALL invoke the plaque placement op
- **AND** the `SIGNAGE` slot SHALL NOT be consulted for that doorway.

#### Scenario: A doorway has no plaque binding
- **WHEN** the facade-detail pass runs for an archetype with `entry_signage=true`
- **AND** `plaque_bindings.json` has no entry for that archetype
- **THEN** the pass SHALL invoke `ops.wall_hanging` against the `SIGNAGE` slot as before
- **AND** a `wall_sign` (or modded canvas sign under the full profile) SHALL be placed.

#### Scenario: A plaque binding references an unknown frame
- **WHEN** `plaque_bindings.json` references a frame preset that is not in the curated catalog
- **THEN** style-profile validation SHALL fail with `unknown_frame_preset`
- **AND** the offending entry SHALL be named in the report.

### Requirement: Forbidden style blocks are quality-gated
Generated building quality checks SHALL reject generated buildings containing blockstates whose block id matches a fragment listed in the active style profile's `forbidden_blocks`.

#### Scenario: A forbidden block appears in a generated building
- **WHEN** quality validation sees a non-air blockstate whose block id contains a forbidden fragment
- **THEN** the generated building SHALL fail the quality gate
- **AND** the report SHALL include a `forbidden_blocks` error.

#### Scenario: Chinese style excludes Western-only materials
- **WHEN** a Chinese courtyard sub-building or compound is validated
- **THEN** blockstates matching the Chinese style profile's Western-incompatible forbidden fragments SHALL fail validation
- **AND** the generated resource SHALL not be accepted for staged manual review until the forbidden-block gate passes.

### Requirement: Variation preserves protected cells
Material variation SHALL NOT modify cells tagged as `PROTECTED`.

#### Scenario: A protected doorway cell uses a variable slot
- **WHEN** the material variation pass runs
- **THEN** the protected doorway cell SHALL keep its existing blockstate
- **AND** unprotected eligible facade cells MAY be varied according to the style profile.


### Requirement: Style profile schema includes civic and furniture slots
The style profile schema SHALL recognize additional material slots `INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, `HERALDRY`, `SPIRIT_CRYSTAL`, and `RITUAL_METAL` alongside the existing `BASE_STONE`, `WALL_MAIN`, `FRAME_WOOD`, `ROOF_DARK`, `DETAIL_WOOD`, `LIGHTING`, `GROUND_PATH`, `INTERIOR_WORK`, and `INTERIOR_STORAGE` slots. A style profile MAY omit any of these slots, in which case generators referencing the missing slot SHALL skip placement of that slot's blocks.

### Requirement: The `chinese_mansion` style profile introduces garden and open-facade slots
The `chinese_mansion` style profile (`tools/buildgen/styles/chinese_mansion.json`) SHALL define the following additional slots beyond the base Chinese courtyard vocabulary:
- `FACADE_OPEN`: materials for the 敞厅 `open_hall` archetype's open eave columns (columns + open eave, no full-height front wall)
- `GARDEN_PATH`: materials for 花园 path cells (distinct from main-yard `GROUND_PATH` to allow finer gravel/moss textures)
- `ROCKERY_STONE`: materials for 假山 cells (primary: `myvillage:rockery_block`; vanilla fallbacks: `minecraft:stone`, `minecraft:andesite`)
- `GARDEN_PAVEMENT`: materials for 花园 paved area (gravel + stone_bricks mix)
- `POND_STONE`: materials for 水池 shoreline cells (primary: `myvillage:rockery_block`; vanilla fallbacks: `minecraft:mossy_stone_bricks`, `minecraft:stone`)

A style profile that defines `myvillage:rockery_block` in any slot SHALL include a runtime fallback entry in `src/main/resources/data/myvillage/mod_block_fallbacks.json`. The `myvillage:` self-namespace exempts it from mod-catalog confirmation.

#### Scenario: The mansion style profile is loaded
- **WHEN** the generator loads style id `chinese_mansion`
- **THEN** `material_slots` SHALL include `FACADE_OPEN`, `GARDEN_PATH`, `ROCKERY_STONE`, `GARDEN_PAVEMENT`, and `POND_STONE`
- **AND** `allowed_roof_types` SHALL include `chinese_flush_gable`, `chinese_overhang_gable`, `chinese_half_hip`, and `chinese_round_ridge`.

#### Scenario: The medieval village style defines civic slots
- **WHEN** the `medieval_village` style is loaded
- **THEN** the profile SHALL include `INTERIOR_CIVIC` containing at least `brewing_stand`, `lectern`, `bell`, `bookshelf`, and `cauldron`
- **AND** it SHALL include `FURNITURE` containing at least `bed` and `chest`
- **AND** it SHALL include `SIGNAGE` containing at least `standing_sign` and `wall_sign`
- **AND** it SHALL include `HERALDRY` containing at least `standing_banner` and `wall_banner`.

#### Scenario: A civic builder resolves a heraldry block
- **WHEN** a civic builder needs a banner block
- **THEN** it SHALL resolve the blockstate through the `HERALDRY` slot
- **AND** the banner color SHALL be sampled deterministically from a curated medieval heraldic palette keyed by the generation seed.

#### Scenario: The cultivation sect style defines spirit slots
- **WHEN** the `cultivation_sect` style is loaded
- **THEN** the profile SHALL include a `SPIRIT_CRYSTAL` slot containing amethyst-family blocks
- **AND** it SHALL include a `RITUAL_METAL` slot containing oxidized-copper-family blocks.

#### Scenario: A mortal style omits spirit slots
- **WHEN** the `cultivation_town` style is loaded and a generator requests `SPIRIT_CRYSTAL`
- **THEN** placement using that optional slot SHALL be skipped rather than failing style loading.

### Requirement: Cultivation style profiles exist
The generator SHALL provide `cultivation_town` and `cultivation_sect` style profiles loadable through the existing style-loading mechanism. The town style SHALL use a mortal timber/stone/clay-tile palette and forbid spirit materials. The sect style SHALL use the mortal base plus spirit materials and SHALL allow cultivation forms.

#### Scenario: Loading the cultivation town style
- **WHEN** the generator requests style id `cultivation_town`
- **THEN** it SHALL load `tools/buildgen/styles/cultivation_town.json`
- **AND** the loaded profile SHALL include all required style sections.

#### Scenario: Loading the cultivation sect style
- **WHEN** the generator requests style id `cultivation_sect`
- **THEN** it SHALL load `tools/buildgen/styles/cultivation_sect.json`
- **AND** `allowed_roof_types` SHALL include `tiered_eave_roof`
- **AND** `allowed_motifs` SHALL include cultivation motifs such as `moon_gate`, `spirit_array`, `incense_altar`, or `cloud_rail`.

### Requirement: Forbidden blocks are per-style
The `forbidden_blocks` policy SHALL be evaluated against the active style profile. `cultivation_town` SHALL continue to forbid quartz, copper, and gold-block palette materials. `cultivation_sect` SHALL NOT forbid quartz, copper, or gold-block materials needed for spirit palettes.

#### Scenario: A spirit material passes in the sect style
- **WHEN** a quartz, copper, or oxidized-copper blockstate appears in a `cultivation_sect` build
- **THEN** the quality forbidden-block gate SHALL NOT reject it.

#### Scenario: A spirit material fails in the town style
- **WHEN** a quartz or copper blockstate appears in a `cultivation_town` build
- **THEN** the quality forbidden-block gate SHALL reject it
- **AND** the report SHALL include a `forbidden_blocks` error.

### Requirement: Forbidden blocks policy is narrowly scoped
The `forbidden_blocks` list SHALL preserve the medieval palette by excluding modern, nether, metallic treasure, and technical blocks. The list SHALL NOT block placement of furniture or signage blocks that are placement-safe in their default state. Specifically, `bed`, `chest`, `banner`, and `sign` SHALL NOT appear in the `medieval_village` style profile's `forbidden_blocks`.

#### Scenario: The medieval village forbidden list is checked
- **WHEN** the `medieval_village` style profile is loaded
- **THEN** `forbidden_blocks` SHALL NOT contain `bed`, `chest`, `banner`, or `sign`
- **AND** it SHALL still contain palette-breaking fragments such as `quartz`, `concrete`, `terracotta`, `warped_`, `crimson_`, `iron_block`, `copper`, `gold_block`, `netherite`, `spawner`, `command_block`, `shulker`, `jukebox`, and `beacon`.

#### Scenario: A civic interior places a bed
- **WHEN** a tavern inn zone places a bed
- **THEN** the quality gate SHALL NOT reject the bed blockstate
- **AND** the bed SHALL be placed as a two-block foot+head blockstate pair with no NBT.

### Requirement: Chests and brewing stands ship empty
Civic interiors MAY place `chest` and `brewing_stand` blocks. Such blocks SHALL be placed in their default empty state with no inventory NBT, no loot table reference, and no brewed potion contents. The export pipeline SHALL NOT inject loot tables for any civic-generated structure.

#### Scenario: A lord manor places a chest
- **WHEN** a lord manor private quarters zone places a chest
- **THEN** the exported block entity (if any) SHALL have an empty inventory
- **AND** no loot table reference SHALL be present.

#### Scenario: A tavern places a brewing stand
- **WHEN** a tavern hall zone places a brewing stand
- **THEN** the exported block entity (if any) SHALL have no brewing contents
- **AND** no ingredient, potion, or fuel item SHALL be present.

### Requirement: Slot loading is namespace-aware under a modset profile
The generator SHALL support loading a style profile against an active set of namespaces (a modset profile). `load_style(style_id, available_namespaces)` SHALL filter every material slot list to entries whose block id namespace is in `available_namespaces`, at load time, before any downstream resolution. The existing single-argument `load_style(style_id)` behavior SHALL be preserved as the default (all listed entries active). The `primary()`, `alternates()`, `pick()`, `slot_entry()`, and related slot-resolution contracts SHALL behave identically on the filtered profile, so downstream build operations require no changes.

#### Scenario: Loading under the vanilla profile
- **WHEN** a style is loaded with `available_namespaces = {"minecraft"}`
- **THEN** every slot list SHALL retain only its `minecraft:` entries
- **AND** `primary()`, `alternates()`, and `pick()` SHALL operate over the filtered entries with their existing contracts.

#### Scenario: Loading under the full profile
- **WHEN** a style is loaded with `available_namespaces` containing the confirmed external mod namespaces plus `minecraft`
- **THEN** slot lists SHALL retain both mod and vanilla entries in their declared order.

#### Scenario: Default loading is unchanged
- **WHEN** `load_style(style_id)` is called without an `available_namespaces` argument
- **THEN** it SHALL load the profile with all listed slot entries active
- **AND** existing callers SHALL observe no behavior change.

### Requirement: Every material slot ends with a vanilla fallback
Each material slot list in every style profile SHALL end with a guaranteed `minecraft:` (vanilla) block id. After namespace filtering, a slot SHALL therefore always retain at least its vanilla fallback entry, so resolution under any modset profile never yields an empty required slot or places air. Optional slots that a style legitimately omits remain governed by the existing omit-and-skip behavior.

#### Scenario: A slot resolves under an empty mod set
- **WHEN** a style is loaded with `available_namespaces = {"minecraft"}` and a build operation resolves a slot that also lists mod entries
- **THEN** the slot SHALL resolve to its trailing vanilla fallback id
- **AND** no resolution SHALL return air or raise an empty-slot error.

#### Scenario: A style profile omits the trailing vanilla fallback
- **WHEN** a style profile defines a required material slot whose last entry is not a `minecraft:` id
- **THEN** the fallback-convention check SHALL flag that slot
- **AND** the violation SHALL identify the style id and slot name.

### Requirement: Style profile schema recognizes mod-target and cultivation-form slots
The style profile schema SHALL recognize additional optional material slots `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, `MARKET_FITTINGS`, `COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, and `BALUSTRADE` alongside the existing slots. A style profile MAY omit any of these slots, in which case generators referencing the missing slot SHALL skip placement of that slot's optional blocks, consistent with existing optional-slot handling.

#### Scenario: A style declares the new decoration slots with vanilla fallbacks
- **WHEN** a style profile that defines the new slots is loaded
- **THEN** each populated new slot SHALL be present
- **AND** each SHALL contain at least one trailing `minecraft:` fallback entry
- **AND** generation under the `vanilla` profile SHALL place only those vanilla fallbacks.

#### Scenario: The cultivation sect style defines form slots
- **WHEN** the `cultivation_sect` style is loaded
- **THEN** the profile SHALL include `COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, and `BALUSTRADE`
- **AND** each slot's last entry SHALL be a `minecraft:` fallback.

#### Scenario: The vanilla profile resolves form slots to fallbacks
- **WHEN** a cultivation style is loaded with `available_namespaces = {"minecraft"}` and a build resolves a form slot
- **THEN** the slot SHALL resolve to its trailing vanilla fallback
- **AND** no resolution SHALL return air.

#### Scenario: A style omits a new decoration slot
- **WHEN** a style profile does not define `RITUAL_ANCHOR` and a generator requests it
- **THEN** placement using that optional slot SHALL be skipped rather than failing style loading.

### Requirement: Cultivation styles list cultivation forms and exclude Western domestic motifs
The `cultivation_town` and `cultivation_sect` style profiles SHALL list the cultivation roof forms applicable to them (`sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, and `tiered_eave_roof` for the sect) in `allowed_roof_types`, and SHALL NOT list the Western domestic motifs `woodpile`, `barrel_cluster`, `fence_patch`, `side_chimney`, or `small_porch` in `allowed_motifs`.

#### Scenario: The sect style allows sweeping eaves and excludes Western motifs
- **WHEN** the `cultivation_sect` style is loaded
- **THEN** `allowed_roof_types` SHALL include `sweeping_eave_roof`
- **AND** `allowed_motifs` SHALL NOT include `woodpile`, `barrel_cluster`, `fence_patch`, `side_chimney`, or `small_porch`.

### Requirement: Cultivation proportions favor deep eaves and a tall platform
The cultivation style `proportions` SHALL specify a deep roof overhang and a raised platform so that generated halls read with a horizontal, deep-eave silhouette: a roof overhang of at least 2 admissible on hall-class volumes, a platform/foundation height of at least 2 admissible, and a roof-height ratio centered near one-half.

#### Scenario: Sect proportions specify a deep overhang and platform
- **WHEN** the `cultivation_sect` style proportions are read
- **THEN** `roof_overhang` SHALL admit at least 2
- **AND** `foundation_height` (platform) SHALL admit at least 2.

### Requirement: Slots are populated with mod ids per design-intent role
Style profiles SHALL populate material slots with confirmed external-mod block ids drawn from `exmod/mod_block_catalog.json`, placed at the **front** of the matching slot list so they are preferred when active, while the trailing `minecraft:` fallback required by the existing fallback convention is preserved. Mod ids SHALL be assigned to slots according to their design-intent role: `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, `MARKET_FITTINGS`, `COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, and `BALUSTRADE`, plus the existing `FURNITURE`, `LIGHTING`, and wall/window-related slots.

#### Scenario: A populated slot prefers a mod id under the full profile
- **WHEN** a style with a populated slot is loaded under the `full` profile and a build operation resolves that slot's primary entry
- **THEN** it SHALL resolve to a mod id
- **AND** the slot's last entry SHALL still be a `minecraft:` fallback.

#### Scenario: The same slot under the vanilla profile resolves to the fallback
- **WHEN** the same style is loaded with `available_namespaces = {"minecraft"}`
- **THEN** namespace filtering SHALL drop the leading mod ids
- **AND** the slot SHALL resolve to its trailing `minecraft:` fallback exactly as before this change.

### Requirement: Vanilla-profile output is unchanged by slot population
Populating slots with mod ids SHALL NOT change generation output under the `vanilla` profile. After this change, generating any affected library under `available_namespaces = {"minecraft"}` SHALL produce output identical to the pre-change `vanilla` output.

#### Scenario: Vanilla output is byte-stable across the change
- **WHEN** a building library is generated under the `vanilla` profile before and after slot population
- **THEN** the two outputs SHALL be identical
- **AND** no mod id SHALL appear in the `vanilla` output.

### Requirement: Populated mod ids reference only confirmed namespaces
Every mod id inserted into a slot SHALL belong to a namespace listed in the catalog's confirmed mod set. A slot SHALL NOT reference a namespace that is absent from `exmod/mod_block_catalog.json`'s confirmed set (e.g. an Asian-decor namespace that is not staged).

#### Scenario: A slot id uses a confirmed namespace
- **WHEN** a style slot lists a non-`minecraft` block id
- **THEN** that id's namespace SHALL be present in the catalog's confirmed mod set.

#### Scenario: An unstaged namespace is rejected
- **WHEN** a slot would reference a namespace not in the confirmed mod set
- **THEN** that id SHALL NOT be added in this change
- **AND** the role SHALL instead use a present-namespace substitute or its vanilla fallback.

### Requirement: Chinese courtyard style gains vernacular cultivation slots
The `chinese_courtyard` style profile SHALL declare the `PLATFORM_STONE` and `COLUMN` slots (defined by `cultivation-massing-grammar`) with vanilla-only fallback block lists (no external-mod ids). The `chinese_courtyard` style's `allowed_roof_types` SHALL list the four `chinese_*` vernacular roof forms and SHALL NOT list any cultivation monumental form (`sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, `tiered_eave_roof`).

#### Scenario: chinese_courtyard has the vernacular slots
- **WHEN** the `chinese_courtyard` style is loaded
- **THEN** `material_slots["PLATFORM_STONE"]` SHALL exist and contain only `minecraft:` ids
- **AND** `material_slots["COLUMN"]` SHALL exist and contain only `minecraft:` ids.

#### Scenario: chinese_courtyard lists only vernacular roofs
- **WHEN** the `chinese_courtyard` style is loaded
- **THEN** `allowed_roof_types` SHALL include `chinese_flush_gable`, `chinese_overhang_gable`, `chinese_half_hip`, and `chinese_round_ridge`
- **AND** `allowed_roof_types` SHALL NOT include any of `sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, or `tiered_eave_roof`.

### Requirement: Chinese courtyard proportions favor a tall platform under the main yard
The `chinese_courtyard` style profile SHALL declare `proportions` retuned for vernacular courtyard scale: deeper overhang than the medieval family but shallower than the cultivation sect; a tall platform tier when `platform_tier != "none"`; and approximately half the building height devoted to roof.

#### Scenario: chinese_courtyard proportions are retuned
- **WHEN** the `chinese_courtyard` style is loaded
- **THEN** `proportions` SHALL include a roof-ratio setting in the range 0.4–0.55
- **AND** `proportions` SHALL include a platform-height setting matching the `platform_tier` axis values (0, 2, or 3).
