## MODIFIED Requirements

### Requirement: Structure JSON validation checks schema, bounds, metadata, and modset-aware block ids
The Structure JSON validator SHALL check JSON shape, size and coordinate bounds, palette resolution, supported operations, metadata rules, and block id existence relative to the active modset profile. Under the `vanilla` profile only Minecraft `1.21.1` vanilla block ids are accepted, **with the literal exception of the `myvillage:` namespace, which is the mod's own namespace and is always resolvable because the mod jar ships the assets**. Under the `full` profile a non-`minecraft` block id is accepted when its namespace is in the catalog's confirmed mod set and the id exists in `exmod/mod_block_catalog.json`; vanilla `minecraft` id existence is still checked against the `1.21.1` registry; the `myvillage:` namespace is also accepted.

#### Scenario: A non-vanilla, non-myvillage block id is referenced under the vanilla profile
- **WHEN** Structure JSON validation runs under the `vanilla` profile and sees a blockstate whose namespace is not `minecraft` and not `myvillage`
- **THEN** validation SHALL fail because the `vanilla` profile permits only vanilla Minecraft `1.21.1` blocks (plus the mod's own namespace).

#### Scenario: A myvillage block id is referenced under the vanilla profile
- **WHEN** Structure JSON validation runs under the `vanilla` profile and sees a blockstate in the `myvillage:` namespace (e.g. `myvillage:wall_plaque`)
- **THEN** validation SHALL accept the id
- **AND** validation SHALL NOT emit `forbidden_mod_blocks`.

#### Scenario: A confirmed mod block id is referenced under the full profile
- **WHEN** Structure JSON validation runs under the `full` profile and sees a non-`minecraft` blockstate
- **AND** the id's namespace is in the catalog's confirmed mod set and the id exists in the catalog
- **THEN** validation SHALL accept the id.

#### Scenario: An unstaged-namespace block id is referenced under the full profile
- **WHEN** Structure JSON validation runs under the `full` profile and sees a non-`minecraft` blockstate whose namespace is not in the confirmed mod set and not `myvillage`
- **THEN** validation SHALL fail because that namespace is not part of the active modset.

## ADDED Requirements

### Requirement: Plaque-required archetypes carry plaque signature blocks
Generated structures for plaque-required archetypes (scripture pavilion, treasure pavilion, sect gate / paifang) SHALL contain at least one `myvillage:` plaque block (`wall_plaque`, `wall_plaque_vertical`, `hanging_plaque`, or `hanging_plaque_vertical`) and SHALL NOT contain painting entities referencing `myvillage:inscription/...` variants. The generated-structure NBT validator SHALL enforce these signatures; absence of plaque blocks or presence of inscription painting entities SHALL fail with a `plaque_signature` error.

#### Scenario: A scripture pavilion NBT lacks a plaque block
- **WHEN** generated-structure validation checks a structure whose filename starts with `scripture_pavilion_`
- **AND** the palette contains no `myvillage:.*plaque` block id
- **THEN** validation SHALL fail with a `plaque_signature` error.

#### Scenario: A sect gate NBT contains a legacy inscription entity
- **WHEN** generated-structure validation checks a structure whose filename starts with `sect_gate_` or `paifang_`
- **AND** the entity list contains a `minecraft:painting` with a `myvillage:inscription/...` variant
- **THEN** validation SHALL fail with a `plaque_signature` error.

#### Scenario: A non-plaque structure is validated
- **WHEN** generated-structure validation checks a structure whose archetype is not plaque-required (e.g. `small_house_`, `blacksmith_`)
- **THEN** the plaque signature rules SHALL NOT apply
- **AND** the existing house and blacksmith rules SHALL apply unchanged.

### Requirement: Plaque binding validation covers frame, inscription, and bucket compatibility
A validator SHALL traverse `plaque_bindings.json` and confirm that every entry references (a) a frame preset that exists in the curated eight-preset catalog with the orientation declared by the entry, (b) an inscription id whose `painting_variant` JSON and PNG both exist in the bucket that matches the frame preset's interior, and (c) a `mount` value of `wall` or `hanging`. It SHALL also inspect the generated full plaque texture for each bound frame/mount/orientation and confirm that inscription pixels are visible but not overfilled. Any mismatch SHALL fail with `bucket_mismatch`, `unknown_frame_preset`, `missing_inscription`, `invalid_mount`, `plaque_inscription_not_visible`, or `plaque_inscription_overfilled` as appropriate.

#### Scenario: A bucket-compatible binding is checked
- **WHEN** the validator checks a binding pairing a 4w frame with an inscription in the `4w` bucket
- **THEN** validation SHALL accept the entry.

#### Scenario: A bucket-mismatched binding is caught
- **WHEN** the validator checks a binding pairing a 5w_1h frame with an inscription in the `4w` bucket
- **THEN** validation SHALL fail with `bucket_mismatch` and name the offending entry.

#### Scenario: A binding references an unknown frame
- **WHEN** the validator checks a binding referencing `frame=town_lacquered_ornate_4w` (not in the catalog)
- **THEN** validation SHALL fail with `unknown_frame_preset`.

#### Scenario: A generated baked full plaque has no visible inscription pixels
- **WHEN** the validator checks a generated full plaque texture for a bound frame/mount/orientation
- **AND** the textures differ too little from the no-inscription frame textures
- **THEN** validation SHALL fail with `plaque_inscription_not_visible`.

#### Scenario: A generated baked full plaque is overfilled by inscription pixels
- **WHEN** the validator checks a generated full plaque texture for a bound frame/mount/orientation
- **AND** inscription pixels cover an excessive share of the multipart plaque area
- **THEN** validation SHALL fail with `plaque_inscription_overfilled`.

### Requirement: Generated-structure validation checks wall plaque visual order
The generated-structure NBT validator SHALL inspect horizontal `myvillage:wall_plaque` multipart runs and confirm their `col` values match exterior-view left-to-right order for the plaque's `facing`. This check SHALL account for north/east facades where visual left is the higher tangent coordinate, so inscriptions such as `庄园正门` and `藏经阁` do not render in reverse order.

#### Scenario: A north-facing wall plaque has reversed columns
- **WHEN** generated-structure validation checks a horizontal `myvillage:wall_plaque` run with `facing=north`
- **AND** the viewer-visible left-to-right column sequence is `right, inner_right, center, inner_left, left`
- **THEN** validation SHALL fail with `plaque_visual_order`.

#### Scenario: A wall plaque is in exterior-view order
- **WHEN** generated-structure validation checks a horizontal wall plaque run whose viewer-visible columns are `left, inner_left, center, inner_right, right`
- **THEN** validation SHALL accept the plaque order.
