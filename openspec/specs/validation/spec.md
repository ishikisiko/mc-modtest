# Validation

## Purpose

This spec captures the current validation baseline. It is temporary and mutable; proposed changes to hard gates, warnings, or acceptance criteria should be discussed with the project owner first.

See also (narrative + acceptance checklist): [docs/ai-kb/09_validation_checklist.md](../../../docs/ai-kb/09_validation_checklist.md).
## Requirements
### Requirement: Structure JSON validation checks schema, bounds, metadata, and modset-aware block ids
The Structure JSON validator SHALL check JSON shape, size and coordinate bounds, palette resolution, supported operations, metadata rules, and block id existence relative to the active modset profile. Under the `vanilla` profile only Minecraft `1.21.1` vanilla block ids and the project-owned `myvillage` self-namespace are accepted. Under the `full` profile a non-`minecraft` block id is accepted when its namespace is `myvillage` or when its namespace is in the catalog's confirmed mod set and the id exists in `exmod/mod_block_catalog.json`; vanilla `minecraft` id existence is still checked against the `1.21.1` registry.

#### Scenario: An external mod block id is referenced under the vanilla profile
- **WHEN** Structure JSON validation runs under the `vanilla` profile and sees a blockstate outside the `minecraft` and `myvillage` namespaces
- **THEN** validation SHALL fail because the `vanilla` profile permits only vanilla Minecraft `1.21.1` blocks plus shipped `myvillage` resources.

#### Scenario: A shipped myvillage block id is referenced under either profile
- **WHEN** Structure JSON validation runs under the `vanilla` or `full` profile and sees a `myvillage:` blockstate
- **THEN** validation SHALL accept the id as a shipped self-namespace resource.

#### Scenario: A confirmed mod block id is referenced under the full profile
- **WHEN** Structure JSON validation runs under the `full` profile and sees a non-`minecraft` blockstate
- **AND** the id's namespace is in the catalog's confirmed mod set and the id exists in the catalog
- **THEN** validation SHALL accept the id.

#### Scenario: An unstaged-namespace block id is referenced under the full profile
- **WHEN** Structure JSON validation runs under the `full` profile and sees a non-`minecraft` blockstate whose namespace is not in the confirmed mod set
- **THEN** validation SHALL fail because that namespace is not part of the active modset.

### Requirement: Build quality check gates export
Generated buildings SHALL pass the build quality check before they are exported into the building library. The quality check SHALL additionally gate on side-wall integrity: it SHALL fail a building whose closed-volume wall plane has an unplanned hole between the foundation top and the roofline (not a door, window, or connection opening), and it SHALL fail a building that places an interior or protected block in a different volume's exterior wall plane. These checks SHALL inspect the actual wall plane, not only the cells a roof op recorded placing.

#### Scenario: A generated building has no door
- **WHEN** quality checking sees no door blockstate
- **THEN** the building SHALL fail with a `no_entrance` error
- **AND** the generator MAY retry with another deterministic seed attempt before giving up.

#### Scenario: A side wall has an unplanned hole
- **WHEN** quality checking finds an air cell in a closed volume's wall plane that is not a planned opening
- **THEN** the building SHALL fail with an `open_side_wall` error reporting the offending coordinate.

#### Scenario: Interior furniture sits on a neighbor's exterior wall
- **WHEN** quality checking finds an `INTERIOR`/`PROTECTED` non-opening block in a different volume's exterior wall plane
- **THEN** the building SHALL fail with a `furniture_on_wall` error.

### Requirement: Quality warnings do not fail export by themselves
Quality warnings and scores SHALL be diagnostic unless they are represented as hard errors.

#### Scenario: A generated building has few exterior decorations
- **WHEN** quality checking records `few_decorations`
- **THEN** the report SHALL include a warning
- **AND** the building MAY still pass if there are no hard errors.

### Requirement: Exported building library validation checks actual mod resources
The building library resource validator SHALL validate exported NBT and mcfunction files from the mod resources tree, not in-memory generation state. Block-id legality SHALL be evaluated against the active modset profile: vanilla `minecraft` ids are checked against the `1.21.1` registry, `myvillage` ids are accepted as shipped self-namespace resources, and external non-`minecraft` ids are accepted only under a profile whose active namespaces include theirs and only when the id exists in the catalog.

#### Scenario: A generated NBT file is missing
- **WHEN** the validator expects `small_house_001.nbt`
- **AND** the file is absent from `src/main/resources/data/myvillage/structure/`
- **THEN** validation SHALL fail with `missing_file`.

#### Scenario: An external mod id appears under the vanilla profile
- **WHEN** the building library validator runs under the `vanilla` profile
- **AND** a structure palette contains a non-`minecraft`, non-`myvillage` block id
- **THEN** validation SHALL fail with `forbidden_mod_blocks` naming the offending id.

#### Scenario: A myvillage plaque id appears under the vanilla profile
- **WHEN** the building library validator runs under the `vanilla` profile
- **AND** a structure palette contains `myvillage:wall_plaque`, `myvillage:wall_plaque_vertical`, `myvillage:hanging_plaque`, or `myvillage:hanging_plaque_vertical`
- **THEN** validation SHALL accept the id because the mod ships the corresponding block registration and assets.

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

### Requirement: Optional-mod fallback validation covers shipped palettes
The runtime fallback-map validator SHALL check every external non-`minecraft` block id appearing in shipped structure palettes has an entry in `src/main/resources/data/myvillage/mod_block_fallbacks.json`, and each mapped value SHALL be a syntactically valid non-air `minecraft:` block state whose block id exists in the Minecraft `1.21.1` reference list. The `myvillage:` self-namespace SHALL NOT require fallback entries.

#### Scenario: A shipped palette contains an optional mod id
- **WHEN** `tools/validate_mod_block_fallbacks.py` scans shipped structure NBT files
- **THEN** every catalog-approved mod block id found in `palette` or `palettes` SHALL have a generated runtime fallback
- **AND** validation SHALL fail if the fallback is missing, non-vanilla, air, or references an unknown vanilla block id.

#### Scenario: A shipped palette contains a myvillage plaque id
- **WHEN** `tools/validate_mod_block_fallbacks.py` scans shipped structure NBT files
- **AND** a palette contains a `myvillage:` plaque block id
- **THEN** validation SHALL accept the id without requiring a fallback entry.

### Requirement: Plaque binding validation covers inscription data
The plaque binding validator SHALL check `src/main/resources/data/myvillage/plaque_bindings.json` against the plaque frame manifest, painting variant JSON files, inscription PNG assets, generated full plaque block textures, and model UV-windowed plaque part outputs.

#### Scenario: Plaque bindings are validated
- **WHEN** `tools/validate_plaque_bindings.py` runs
- **THEN** every binding SHALL reference an existing frame preset
- **AND** each inscription pool entry SHALL reference an existing `painting_variant` JSON and PNG whose bucket is compatible with the frame geometry
- **AND** the generated full plaque texture for each bound frame/mount/orientation SHALL include visible inscription pixels without overfilling the plaque face
- **AND** invalid mounts, unknown frame presets, missing inscriptions, bucket mismatches, invisible baked inscriptions, and overfilled baked inscriptions SHALL fail validation.

### Requirement: Mod item validation checks registration and client resources
The mod item validator SHALL check registered `myvillage:` items have item model
resources, lang entries, referenced `myvillage:` textures, and blockstate
resources when the item is a `BlockItem`.

#### Scenario: A registered item lacks a model
- **WHEN** `tools/validate_mod_items.py` scans `ModItems.java`
- **AND** an item registered through `DeferredRegister.Items` lacks
  `assets/myvillage/models/item/<id>.json`
- **THEN** validation SHALL fail with `missing_item_model`.

#### Scenario: A block item lacks block resources
- **WHEN** `tools/validate_mod_items.py` scans a `BlockItem`
- **THEN** the matching block id SHALL be registered in `ModBlocks.java`
- **AND** `assets/myvillage/blockstates/<id>.json` SHALL exist.

#### Scenario: A baked full plaque texture has invisible or overfilled inscription pixels
- **WHEN** `tools/validate_plaque_bindings.py` compares a generated full plaque texture with the no-inscription full frame texture for a bound frame/mount/orientation
- **AND** the changed inscription pixel coverage is too small or too large
- **THEN** validation SHALL fail with `plaque_inscription_not_visible` or `plaque_inscription_overfilled`.

### Requirement: Generated NBT validation checks wall plaque visual order
The generated-structure NBT validator SHALL inspect horizontal `myvillage:wall_plaque` multipart runs and confirm their `col` values match exterior-view left-to-right order for the plaque's `facing`. The check SHALL account for north/east facades where visual left is the higher tangent coordinate, so wall-mounted inscriptions do not render in reverse order in game.

#### Scenario: A north-facing wall plaque has reversed columns
- **WHEN** generated-structure validation checks a horizontal `myvillage:wall_plaque` run with `facing=north`
- **AND** the viewer-visible left-to-right column sequence is `right, inner_right, center, inner_left, left`
- **THEN** validation SHALL fail with `plaque_visual_order`.

#### Scenario: A wall plaque is in exterior-view order
- **WHEN** generated-structure validation checks a horizontal wall plaque run whose viewer-visible columns are `left, inner_left, center, inner_right, right`
- **THEN** validation SHALL accept the plaque order.

### Requirement: The `chinese_mansion` library is validated as a 6-NBT group with spread ≥ 15
The generation pipeline SHALL produce 6 `chinese_mansion_001..006.nbt` files validated by `validate_mansion`. The compound library check SHALL confirm: (a) 6 distinct variant keys (no two identical), (b) silhouette score spread (raw, uncapped, with `tower_count * 5` roofline bonus) ≥ 15 across the 6 NBTs, (c) every NBT passes the enclosure-model invariants (gate_house present, form-rule facings, every door on path). Mansion validation uses `validate_mansion`, not `validate_compound`.

#### Scenario: chinese_mansion library is generated
- **WHEN** `generate_compound_library.py --group chinese_mansion --count 6` is run
- **THEN** it SHALL produce 6 NBT files, a gallery function, and a report at `reports/chinese_mansion_compound_library_report.json`
- **AND** the report SHALL record `passed: true`, `distinct_variants: 6`, `silhouette_spread >= 15`, and `door_reachable_rate: 1.0` for every NBT
- **AND** every NBT SHALL have a `facing_per_slot` map matching the form rule.

### Requirement: Mansion validation enforces enclosure-model invariants

`validate_mansion` SHALL, for `chinese_mansion` compounds, enforce the enclosure-
model invariants in addition to the grid-only checks it already performs
(perimeter floats, ground-layer holes, voxel-walkability, silhouette). The
enclosure invariants SHALL be:

- A `gate_house` building slot is present and its footprint straddles the south
  perimeter line (the entrance is a through-building, per the gate-house
  requirement of `chinese-mansion-compound`).
- Every `building_slots` entry's facing (recorded in its slot meta) matches its
  role's form-rule facing per `building-orientation-variants` (正房→south,
  倒座→north, 西厢→east, 东厢→west, gate_house→inward, 楼阁→north).
- Every door-cell (`door_info["front"]`) is on a path cell (path-as-input
  guarantee, per `compound-enclosure-planning`).
- The 进 sequence is well-formed: 仪门 borders 前院 and 主院; 二门 borders 主院 and
  后院 — verified by derived-yard adjacency, NOT by z-band tuple comparison.

`validate_mansion` SHALL NOT use z-band tuple comparison (`meta["outer_yard_band"]`
etc.) to assert any enclosure invariant. The band-coupled checks remain in
`validate_compound` for the `chinese_courtyard` family.

#### Scenario: A mansion with a hole-in-the-wall gate fails validation

- **WHEN** `validate_mansion` runs on a compound whose south entrance is a carved
  air hole rather than a `gate_house`
- **THEN** validation SHALL fail with a `gate_house_missing` error.

#### Scenario: A mansion with a south-facing 倒座 fails validation

- **WHEN** `validate_mansion` runs on a compound whose `front_row` slot records
  `facing=south` (door onto the street)
- **THEN** validation SHALL fail with an `enclosure_facing_violation:front_row`
  error.

#### Scenario: A mansion with an unreachable door fails validation

- **WHEN** `validate_mansion` runs on a compound where some `door_info["front"]`
  cell is not on a path cell
- **THEN** validation SHALL fail with a `door_off_path:<slot_id>` error.

#### Scenario: A well-formed mansion passes all enclosure invariants

- **WHEN** `validate_mansion` runs on a realized enclosure-plan mansion
- **THEN** no `gate_house_missing`, `enclosure_facing_violation`,
  `door_off_path`, or `voxel_*` error SHALL fire
- **AND** the report SHALL record a `facing_per_slot` map and a
  `door_reachable_rate` of 1.0.

### Requirement: Voxel-walkability validation gates compound exports
Compound validators (for `chinese_courtyard`, `chinese_mansion`, etc.) SHALL include a voxel-walkability check that performs a 3D BFS from the gate-entry standable column and confirms all door positions and path endpoints are reachable. The following error codes are defined:
- `voxel_unreachable_door:<archetype>`: a building's front door position is not reachable from the gate entry
- `voxel_unreachable_endpoint:<x,z>`: a path endpoint cell (moon platform, inner gate, etc.) is not reachable
- `voxel_step_cliff:<a>-><b>`: two adjacent path cells have an absolute y-difference ≥ 2 that is not bridged by a stair
- `voxel_blocked_by_solid:<x,z>`: a path cell's standable y does not exist (the cell is fully blocked)

A stat entry `voxel_reachability` SHALL be included in the validation report, recording: `visited` (cells reached), `unreachable` (count), `cliff_count`.

#### Scenario: A compound path endpoint is blocked by a colonnade column
- **WHEN** validate_compound or validate_mansion runs voxel_walk_bfs
- **AND** a colonnade column has been placed over a path endpoint cell making its standable_y much higher than its neighbors
- **THEN** the validator SHALL emit `voxel_unreachable_endpoint:<x,z>` for that cell
- **AND** the compound SHALL fail validation until the endpoint is relocated or the column is removed.

#### Scenario: All compound doors and endpoints are reachable
- **WHEN** the voxel BFS completes for a valid compound
- **THEN** no `voxel_unreachable_*` or `voxel_step_cliff_*` errors SHALL be emitted
- **AND** `voxel_reachability.unreachable` SHALL be 0.

### Requirement: Acceptance prep can include staged Chunky automation
The staged acceptance checklist SHALL include an optional Chunky automation path after offline generation, validation, preview generation, and mod build. The Chunky path SHALL be documented as staged: first Chunky/RCON/server lifecycle validation, then coordinate-command integration, then full optional-mod MyVillage cases, then natural sect worldgen coverage.

#### Scenario: Acceptance checklist documents the staged order
- **WHEN** contributors prepare an in-game acceptance pass with Chunky automation
- **THEN** the documented checklist SHALL run the standalone Chunky/RCON/server lifecycle stage before any MyVillage command integration stage
- **AND** it SHALL identify later full optional-mod and natural sect worldgen stages separately.

### Requirement: Chunky acceptance report participates in validation handoff
When the Chunky acceptance workflow is run, `reports/chunky_acceptance_report.json` SHALL be treated as the handoff artifact for in-game automation status. The report SHALL NOT replace offline validator reports or the public preview server; it SHALL supplement them.

#### Scenario: Chunky report exists after an automated acceptance run
- **WHEN** the Chunky acceptance workflow is included in acceptance prep
- **THEN** the handoff SHALL include `reports/chunky_acceptance_report.json`
- **AND** the regular offline preview handoff SHALL still include the aggregate `out/preview/index.html` and running preview HTTP server.

### Requirement: Automated Chunky acceptance does not replace visual review
Passing Chunky acceptance SHALL mean that the configured server, Chunky pre-generation, coordinate commands, optional-mod registry, and targeted worldgen paths completed without detected automation failure. It SHALL NOT by itself mean the generated structures are visually accepted.

#### Scenario: Chunky acceptance passes
- **WHEN** every requested Chunky acceptance stage passes
- **THEN** the generated world SHALL be considered prepared for in-game visual review
- **AND** final appearance-sensitive acceptance SHALL still require reviewer inspection.

### Requirement: Visual review remains outside full automation
Automated validation SHALL NOT be treated as complete visual acceptance. In-game placement via `/myvillage gallery`, `/myvillage gallery original`, `/myvillage gallery cultivation`, or `/place template` remains the review path for visual issues such as roof holes, gable appearance, stair/slab facing, and layout readability.

#### Scenario: Automated validators pass
- **WHEN** all automated validators report success
- **THEN** the generated structures SHALL be considered mechanically valid
- **AND** final visual acceptance SHOULD still include in-game review for appearance-sensitive changes and `/myvillage town` town-scale review.

#### Scenario: Custom myvillage block appearance is reviewed
- **WHEN** a change needs visual acceptance of a custom `myvillage:` block such as `myvillage:rockery_block`
- **THEN** headless Chunky renderer PNGs SHALL NOT be used as the sole visual acceptance evidence
- **AND** the handoff SHALL require Minecraft client inspection or a future explicitly documented custom-block renderer compatibility path.

#### Scenario: Headless renderer images are used for layout review
- **WHEN** `tools/render_structure.py` is used to produce Chunky renderer PNGs for ordinary placed-world layout or framing review
- **THEN** the default renderer run SHALL produce a multi-camera view plan rather than a single-angle image
- **AND** the default view plan SHALL include both cardinal directions and higher diagonal camera elevations
- **AND** the renderer SHALL provide a documented height-sweep option for low, mid, and high camera comparisons
- **AND** multi-view renderer runs SHALL write a manifest-linked contact sheet for quick angle comparison by default.

### Requirement: Visual acceptance has a reportable inspection handoff
Visual acceptance prep SHALL produce a reportable handoff that links the offline preview surface to any latest Chunky acceptance targets. The handoff SHALL verify that representative preview files are present, SHALL list concrete images for agent inspection, and SHALL list in-game command/worldgen coordinates from the Chunky report when available. The handoff SHALL NOT claim to classify visual quality automatically.

#### Scenario: Visual acceptance report is generated after preview prep
- **WHEN** `python3 tools/write_visual_acceptance_report.py` is run after preview generation
- **THEN** it SHALL write `reports/visual_acceptance_report.json`
- **AND** it SHALL write `reports/visual_acceptance_report.md`
- **AND** it SHALL verify that `out/preview/index.html` exists
- **AND** it SHALL verify representative structure preview PNGs and plan preview assets for review.

#### Scenario: Visual acceptance report follows Chunky acceptance
- **WHEN** the visual acceptance report is generated after `reports/chunky_acceptance_report.json` exists
- **THEN** it SHALL include the Chunky stage statuses
- **AND** it SHALL include MyVillage command targets driven by RCON
- **AND** it SHALL include the located natural `myvillage:sect` coordinate and bounded Chunky region when Stage 4 ran.

#### Scenario: Agent claims visual verification
- **WHEN** the agent reports that visual verification was performed
- **THEN** the agent SHALL have opened representative preview PNGs listed in `reports/visual_acceptance_report.md`
- **AND** the response SHALL distinguish file/report inspection from final in-game appearance acceptance.

### Requirement: Manual acceptance has documented preparation
Staged manual acceptance SHALL start from a prepared mod artifact and command list. The acceptance handoff SHALL NOT rely only on generated NBT files or validator reports.

#### Scenario: A reviewer starts a staged acceptance pass
- **WHEN** a reviewer is asked to inspect generated structures in game
- **THEN** a current mod jar build path SHALL be available or documented
- **AND** the README command list SHALL include `/myvillage list`, `/myvillage town [seed]`, `/myvillage place <structure_id>`, `/myvillage gallery`, `/myvillage gallery original`, and `/myvillage gallery cultivation`
- **AND** the acceptance prep SHOULD include `python3 tools/validate_plaque_bindings.py` when plaque-bearing resources are present
- **AND** the acceptance prep SHOULD include `python3 tools/validate_compound_library.py --group chinese_huipai_mansion --count 2` when Hui-style reference slice resources are present
- **AND** the offline preview prep SHOULD include `python3 tools/preview_structure.py --all`, producing static PNG previews and per-structure `viewer.html` files under `out/preview/`
- **AND** town preview prep SHOULD include `python3 tools/generate_town_plan_preview.py --count 6`, producing top-down plan PNG/HTML previews under `out/preview/town_plan_s*` (the default base seed covers all six perimeter wall families in six `+101` increments: `octagon/trapezoid/circle/square/dshape/oval`)
- **AND** when more than one `viewer.html` is produced, the preview prep SHALL produce an aggregate `out/preview/index.html` review entry point
- **AND** the acceptance handoff SHOULD include `python3 tools/write_visual_acceptance_report.py`, producing `reports/visual_acceptance_report.json` and `reports/visual_acceptance_report.md`
- **AND** the acceptance handoff SHALL include a running public HTTP server rooted at `out/preview/`, bound with `python3 -m http.server 8765 --bind 0.0.0.0 --directory out/preview`, and report `http://43.156.135.198:8765/index.html` while this host keeps that public IP
- **AND** the preview HTTP server SHALL remain running for reviewer acceptance until the reviewer explicitly says it can be closed, or until the related OpenSpec change is being archived
- **AND** the changelog SHALL identify the version or fix label under review
- **AND** the reviewer SHOULD first run `/myvillage list` to confirm the expected templates are loaded before placing individual structures.

### Requirement: Town generation validation checks plan and realization invariants
The town-generation validator SHALL check seeded town plans, a sloped-site case, a deliberately broken-town negative case, and the default size/performance budget.

#### Scenario: Town generation is validated
- **WHEN** `tools/validate_town_generation.py` succeeds
- **THEN** several seeded towns SHALL pass plan and realization invariants
- **AND** a deliberately broken plan SHALL fail with the offending invariant
- **AND** the default town SHALL remain within the bounded footprint/block budget while an oversize plan is reported.

#### Scenario: Runtime town layout variants are validated
- **WHEN** `tools/validate_runtime_town_plan.py` succeeds
- **THEN** the runtime shrine-axis plan SHALL keep parcel bounds, negative space, and placed template footprints disjoint from streets
- **AND** every parcel SHALL remain reachable from the street network
- **AND** smoke/light ground-detail candidates and street-room furniture candidates SHALL stay off template footprints
- **AND** shipped runtime templates SHALL have a populated ground layer and sufficient terrain-replacement support.

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

#### Scenario: The cultivation sect compound library is validated
- **WHEN** `tools/validate_compound_library.py --group cultivation_sect --count 2` succeeds
- **THEN** two distinct sect compound structures SHALL be validated
- **AND** generated `place/cultivation_sect_*.mcfunction` and `gallery/cultivation_sect.mcfunction` files SHALL exist
- **AND** matching `settlement_meta/cultivation_sect_*.json` files SHALL expose siting context, terrace levels, and link metadata.

#### Scenario: The Hui-style reference slice library is validated
- **WHEN** `tools/validate_compound_library.py --group chinese_huipai_mansion --count 2` succeeds
- **THEN** two distinct Hui-style reference slice structures SHALL be validated
- **AND** generated `place/chinese_huipai_mansion_*.mcfunction` and `gallery/chinese_huipai_mansion.mcfunction` files SHALL exist
- **AND** the report SHALL preserve `candidate_003` provenance as `local_research`
- **AND** validation SHALL confirm the Tianjing sequence, closed facade, stepped gable cue, no-garden constraint, and original-generated source status.

#### Scenario: A cultivation town block is validated under the vanilla profile
- **WHEN** `tools/validate_compound_library.py --group cultivation_town --profile vanilla` runs against output that contains mod ids
- **THEN** validation SHALL fail with `forbidden_mod_blocks`
- **AND** the same output SHALL validate clean under the `full` profile.

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

### Requirement: Civic structures carry signature role blocks
Generated civic structures SHALL contain archetype-specific signature blocks that distinguish them from housing, commercial, and industrial archetypes. The generated-structure NBT validator SHALL enforce these signatures alongside the existing house utility-block and blacksmith forge-block rules.

#### Scenario: A tavern NBT is validated
- **WHEN** generated-structure validation checks a structure whose filename starts with `tavern_`
- **THEN** the palette SHALL contain `minecraft:brewing_stand` OR at least three `minecraft:barrel` blockstate entries
- **AND** the palette SHALL contain at least one `minecraft:bed` blockstate
- **AND** validation SHALL fail with a civic-signature error if either check fails.

#### Scenario: A lord manor NBT is validated
- **WHEN** generated-structure validation checks a structure whose filename starts with `lord_manor_`
- **THEN** the palette SHALL contain `minecraft:bell` OR `minecraft:lectern`
- **AND** the palette SHALL contain at least one banner blockstate matching `minecraft:.*banner`
- **AND** validation SHALL fail with a civic-signature error if either check fails.

#### Scenario: A non-civic structure is validated
- **WHEN** generated-structure validation checks a structure whose filename does not start with `tavern_` or `lord_manor_`
- **THEN** the civic signature rules SHALL NOT apply
- **AND** the existing house and blacksmith rules SHALL apply unchanged.

### Requirement: Civic library validator checks generated resources
The civic library validator SHALL validate exported civic NBT files and the generated civic place/gallery mcfunction files. It SHALL be invocable separately from the medieval library validator and the compound library validator.

#### Scenario: The civic library is validated
- **WHEN** `tools/validate_civic_library.py` succeeds
- **THEN** five tavern and three lord manor NBT files SHALL be validated
- **AND** each civic NBT SHALL pass the civic signature-block gate
- **AND** generated `place/tavern_*.mcfunction`, `place/lord_manor_*.mcfunction`, and `gallery/civic.mcfunction` SHALL exist.

### Requirement: Cultivation policy and form checks are explicit
Cultivation style policy and form vocabulary regression checks SHALL be invocable separately from broad NBT validation. The policy check SHALL verify that spirit materials pass for sect styles and fail for town styles. The form check SHALL verify that cultivation-only forms are registered and are not invoked by legacy medieval or Chinese generation.

#### Scenario: Cultivation style policy is checked
- **WHEN** `tools/check_style_policy.py` succeeds
- **THEN** a sect-only spirit material SHALL pass the active sect forbidden-block policy
- **AND** the same material SHALL fail the active town forbidden-block policy.

#### Scenario: Cultivation forms are checked
- **WHEN** `tools/check_cultivation_forms.py` succeeds
- **THEN** the registered cultivation roof forms, ridge ornaments, and motifs SHALL be exercisable by cultivation generation
- **AND** legacy medieval and Chinese samples SHALL not invoke cultivation-only forms.

### Requirement: Region topology validation checks structural invariants and determinism

The region-topology validator SHALL check generated region graphs for structural invariants: the region count is within 5–7 inclusive; exactly one anchor region exists and is centered; the 连-subgraph connects every non-walled region to the anchor; every 连 edge respects the tier-step limit N = 5; the anchor holds the highest tier; each 隔 edge carries a separator type from the legal palette {特殊山脉, 特殊海洋}; each `walled` region has at most one 连 (关隘) edge with all others 隔; and the same seed reproduces an identical graph. A multi-seed survey SHALL confirm these hold across seeds and report count distribution, connectivity, tier spread, and walled-region presence.

#### Scenario: A generated region graph is validated

- **WHEN** `tools/validate_region_topology.py` succeeds
- **THEN** several seeded region graphs SHALL satisfy the count, single-centered-anchor, 连-connectivity, tier-step, anchor-top-tier, separator-palette, and walled-region invariants
- **AND** a deliberately broken graph SHALL fail with the offending invariant named.

#### Scenario: Determinism is checked

- **WHEN** the validator generates the same seed twice
- **THEN** the two region graphs SHALL be identical
- **AND** validation SHALL fail with a determinism error if they differ.

#### Scenario: A tier-step violation is rejected

- **WHEN** a 连 edge joins two regions whose tier difference exceeds 5
- **THEN** validation SHALL fail naming the offending edge.

#### Scenario: A disconnected region is rejected

- **WHEN** a non-walled region is not reachable from the anchor through 连 edges
- **THEN** validation SHALL fail with a connectivity error naming the unreachable region.

### Requirement: Cultivation initiation has a dedicated deterministic validator
The repository SHALL provide `tools/validate_cultivation_initiation.py` and `tools/tests/test_validate_cultivation_initiation.py`. The validator SHALL inspect the actual OpenSpec, Java wiring, datapack definitions, client/data resources, translations, documentation, version files, and built-jar surface for the initiation change. It SHALL report specific failures for missing or mismatched evidence and SHALL use Java tests, not source-text assertions, as the authority for deterministic math, affinity totals, atomic mutation, repeated-call state, and mastery preservation.

#### Scenario: The dedicated validator passes
- **WHEN** every initiation contract, integration, resource, authority, scope, documentation, release, and packaging invariant is present
- **THEN** `python3 tools/validate_cultivation_initiation.py` SHALL exit `0`

#### Scenario: A core algorithm is only mentioned in source
- **WHEN** source text contains deterministic-looking constants but the required Java golden/invariant tests are absent or failing
- **THEN** the validation handoff SHALL NOT claim deterministic generation is proven

#### Scenario: A validator negative fixture is exercised
- **WHEN** a fixture removes one required resource, alters a requirement, adds a prohibited payload/direct attachment write, or desynchronizes release documentation
- **THEN** the Python test suite SHALL prove a specific nonzero validator result

### Requirement: Initiation validation checks both functional BlockItems completely
Validation SHALL check that `spirit_testing_stele` and `technique_inheritance_stele` are distinct registered blocks and BlockItems, appear in `myvillage:main`, are covered by `ModBlocks.verifyRegistered`, own no BlockEntity, route to their respective services, and have complete blockstate, block model, item model, loot table, mineable tool tag, bilingual names/messages, and jar entries. It SHALL also confirm no recipe, natural generation, or combined state-dependent stele implementation is introduced.

#### Scenario: One stele lacks a loot table or tool tag
- **WHEN** initiation validation scans the shipped resource tree
- **THEN** it SHALL fail with the affected stele id and missing resource type

#### Scenario: Both BlockItems exist but call one combined block behavior
- **WHEN** runtime wiring does not preserve separate testing and inheritance service boundaries
- **THEN** validation SHALL fail rather than accepting registry ids alone

#### Scenario: Jar packaging is inspected
- **WHEN** `./gradlew build` succeeds
- **THEN** validation SHALL inspect the produced jar for both block classes and all declared resources
- **AND** source-tree existence alone SHALL NOT satisfy the packaging gate

### Requirement: Initiation server smoke checks registry, payload, and side errors
The bounded dedicated/acceptance-server smoke SHALL check logs for registry-freeze, datapack-registry, spiritual-element codec, technique codec, duplicate-payload registration, payload-direction, client-only classloading, missing block/item registry, missing model/loot/translation, cultivation-snapshot, and flying-sword-payload regressions. It SHALL stop the server cleanly and SHALL not be described as visual or interaction acceptance.

#### Scenario: Stage-1 acceptance startup is used
- **WHEN** `python3 tools/run_chunky_acceptance.py --stage 1` is the current repository smoke entry
- **THEN** its result SHALL be recorded as bounded server lifecycle/registration evidence only
- **AND** successful startup SHALL NOT mark the two stele interactions or H-screen appearance as passed

#### Scenario: The log contains an initiation registry or side error
- **WHEN** any listed error is present despite the process reaching a started state
- **THEN** the server-smoke gate SHALL fail

### Requirement: Initiation manual acceptance uses pass fail or not_verified
Manual evidence for stele interactions, messages/effects, exact H-screen phase data, repeated-action invariants, persistence, death/dimension lifecycle, command aliases, mining/drops/creative-tab presence, H-screen sharpness, flying-sword/existing-command regression, and absence of execution/progression SHALL use only `pass`, `fail`, or `not_verified`. No automated validator, build, jar listing, or server startup SHALL substitute for direct observation.

#### Scenario: A gameplay item was not personally observed
- **WHEN** the final handoff has no direct client/server evidence for that item
- **THEN** the item SHALL be recorded `not_verified`
- **AND** it SHALL NOT be inferred as `pass` from another gate

#### Scenario: A repeated inheritance is observed
- **WHEN** the reviewer uses the inheritance stele twice after setting nonzero mastery
- **THEN** the item SHALL pass only if the second use reports already learned and mastery remains unchanged

### Requirement: Focused spirit-stone validation covers resources loot and worldgen
A standard-library validator with negative fixtures SHALL check item/block
registrations, creative-tab and registered-block verification, models, textures,
blockstates, bilingual names, loot alternatives, pickaxe/iron-tier tags,
configured-feature targets and sizes, placed-feature counts/heights, the
Overworld biome modifier, forbidden processing resources, and practical-jar
contents.

#### Scenario: The shipped spirit-stone slice is valid
- **WHEN** focused validation runs against the implemented resource tree and current jar
- **THEN** it SHALL exit successfully only when every declared invariant and reference is satisfied

#### Scenario: A fixed worldgen value drifts
- **WHEN** a fixture changes a count, vein size, height provider, target block, biome set, or generation step
- **THEN** validation SHALL fail with the offending feature and field

#### Scenario: A loot or tool invariant is missing
- **WHEN** a fixture removes Silk Touch, Fortune, the base item, or either required block tag
- **THEN** validation SHALL fail with a specific loot or harvest diagnostic

### Requirement: Spirit-stone validation includes build server and manual evidence
Closeout SHALL run strict change and baseline spec validation, focused validator
tests, mod-item validation, Gradle tests, the practical jar build, jar inspection,
and a bounded dedicated-server smoke. Mining, natural frequency, Fortune output,
Silk Touch output, inventory appearance, and placed-block appearance SHALL use
`pass`, `fail`, or `not_verified` from real-client evidence.

#### Scenario: Automated gates pass without gameplay
- **WHEN** all automated commands pass but no real client mines or observes the ore
- **THEN** registration and packaging MAY pass
- **AND** mining, generation frequency, and visual items SHALL remain `not_verified`

### Requirement: Focused lifespan validation checks migration integration and scope
A focused standard-library validator with negative fixtures SHALL inspect v1/v2
codec ownership, both new fields, migration defaults, realm lifespan values,
Overworld SavedData, eligible-mode filters, 600-tick batching, lifecycle hooks,
configuration defaults/warnings, clientbound-only time synchronization, H-screen
labels, initiation preservation, documentation, and jar inclusion. Algorithmic
and lifecycle behavior SHALL be proven by Java/integration tests rather than
source text alone.

#### Scenario: Shipped lifespan integration is valid
- **WHEN** the focused validator runs after implementation and jar build
- **THEN** it SHALL succeed only when every declared source/resource/docs/package invariant is present

#### Scenario: A fixture silently defaults a malformed v2 value
- **WHEN** migration or current decoding resets an unsupported/negative value instead of failing
- **THEN** validation SHALL fail with a schema-migration diagnostic

### Requirement: Lifespan closeout runs the complete cultivation gate set
Closeout SHALL record strict change and baseline validation, cultivation core and
initiation validators/tests updated for schema v2, focused lifespan validator
tests, Gradle tests/build, jar inspection, and bounded server smoke. Existing
initiation and flying-sword regressions SHALL remain green.

#### Scenario: One required gate fails
- **WHEN** any strict spec, validator, test, build, jar, or server-smoke command exits nonzero
- **THEN** the change SHALL not be reported complete or ready for serial integration

### Requirement: Focused affinity and meditation UI validation covers the revised contract
The repository SHALL add or revise deterministic validators with negative
fixtures for profile v3/migrations, affinity preservation, ten-tick cadence,
normal and spirit arithmetic, direct layer-priced costs, transaction rollback,
inert reserve, four progress/stability caps, pre-cap stability lock, post-cap
affinity consolidation, success halving, Qi-IV ceiling, tabbed H presentation,
bounded button intents, bilingual resources, documentation, version `0.25.0`,
and practical-jar inclusion. Behavioral codec, transaction, and widget routing
claims MUST be proven by Java tests where static source inspection is
insufficient.

#### Scenario: The revised implementation is valid
- **WHEN** all focused validators run against the implemented resources, source, docs, tests, and current jar
- **THEN** they SHALL exit successfully only when every declared invariant is present and internally consistent

#### Scenario: A negative fixture restores items after a successful install
- **WHEN** transaction handling would refund stones after profile installation but snapshot failure
- **THEN** validation SHALL fail with a duplicate-refund diagnostic

#### Scenario: A negative fixture exposes reserve in H
- **WHEN** the tabbed profile or meditation UI still renders the legacy reserve
- **THEN** validation SHALL fail with the stale-reserve presentation path

### Requirement: Revised-loop acceptance separates automation visual review and gameplay
The acceptance handoff SHALL run the strict baseline plus all five playable-loop
change specs, the seven named cultivation/resource validators, validator tests,
Gradle tests/build, jar inspection, bounded stage-1 server smoke, and
CRAFT/front-door checks. Before manual review it SHALL update README/AGENTS/KB
guidance, generate the visual acceptance report, inspect representative H-tab
images, and serve `out/preview/` over HTTP until review ends. Automation and
headless startup SHALL not count as real-client visual or interaction proof.

#### Scenario: Automation passes without a client session
- **WHEN** every automated gate and bounded server smoke passes but no real client opens H
- **THEN** both-tab layout, button interaction, exact inventory consumption, downgrade feedback, and advancement flow SHALL remain `not_verified`

#### Scenario: Visual evidence is prepared
- **WHEN** the worktree is handed to the owner for review
- **THEN** the acceptance report and representative tab images SHALL be inspectable through the served preview
- **AND** the report SHALL state which judgments remain pending

### Requirement: The real-client ledger exercises revised progression without inferred passes
README acceptance SHALL record `pass`, `fail`, or `not_verified` for v1/v2 save
migration, default affinity `10`, Profile/Meditation tabs, all four buttons and
V/B/X/N parity, normal ten-tick gain, each released spirit cost, insufficient
stone downgrade, no progress-cap charge, pre-cap stability lock, post-cap
affinity stability in both modes, all four dynamic stability caps, success
halving, unchanged mastery clock, all four new progress thresholds, Qi-IV
ceiling, interruption, relog/death/dimension preservation, and existing
initiation/flying-sword regressions. Unobserved items SHALL remain `not_verified`
even when validators or server startup pass.

#### Scenario: Exact Qi-II spirit cost is observed
- **WHEN** a real client records inventory and progress before and after one eligible Qi-II spirit settlement
- **THEN** that item SHALL pass only if two stones are removed and progress gains 50 or the exact remaining cap capacity

#### Scenario: H action authority is observed
- **WHEN** each tab button is exercised with valid and invalid authoritative state
- **THEN** the item SHALL pass only when valid actions follow server results and invalid actions are rejected or downgraded without client-authored mutation

#### Scenario: A lifecycle path is not exercised
- **WHEN** save migration, relog, death, or dimension behavior was not directly observed
- **THEN** its ledger entry SHALL remain `not_verified`
