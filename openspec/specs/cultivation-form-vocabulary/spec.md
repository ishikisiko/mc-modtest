# Cultivation Form Vocabulary

## Purpose

This spec captures the cultivation-only roof, opening, and motif forms shared by the build engine.

## Requirements

### Requirement: Sweeping, hip, pyramidal, and tiered cultivation roof forms
The generator SHALL provide cultivation roof types `sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, and `tiered_eave_roof`, each registered in the roof registry and resolving blocks through style material slots. `sweeping_eave_roof` SHALL produce deep overhangs with raised upturned corners built from stair/slab geometry. `hip_roof` SHALL slope inward on four sides. `pyramidal_roof` SHALL converge to a finialed crown. `tiered_eave_roof` SHALL stack sweeping eave tiers rather than straight gables.

#### Scenario: A hall builds upturned sweeping eaves
- **WHEN** a cultivation volume names `sweeping_eave_roof`
- **THEN** the eave SHALL overhang the wall plane by the style overhang proportion
- **AND** corner cells SHALL step up and out from the eave line to produce an upturned-corner silhouette
- **AND** roof blocks SHALL resolve through the style's `ROOF_DARK` / `ROOF_TILE` slots.

#### Scenario: Small sweeping-eave footprint falls back gracefully
- **WHEN** `sweeping_eave_roof` is requested on a footprint too small for corner sweeps
- **THEN** it SHALL fall back to a single straight eave rather than fail the build.

#### Scenario: A hip roof slopes on four sides
- **WHEN** a volume names `hip_roof` on an eligible footprint
- **THEN** the roof SHALL slope inward on all four sides
- **AND** all roof blocks SHALL resolve through the style's roof slots.

#### Scenario: A pavilion builds a pyramidal crown
- **WHEN** a volume names `pyramidal_roof`
- **THEN** the roof SHALL converge toward an apex
- **AND** a style with `RIDGE_ORNAMENT` SHALL place a crown ornament at the apex.

#### Scenario: A sect hall builds a tiered eave roof
- **WHEN** a volume names `tiered_eave_roof` and its footprint is large enough
- **THEN** the generated roof SHALL have at least two stacked eave tiers
- **AND** each tier SHALL use sweeping upturned eaves
- **AND** all roof blocks SHALL resolve through style roof slots.

#### Scenario: The footprint is too small for tiers
- **WHEN** a `tiered_eave_roof` is requested on a footprint too small for multiple tiers
- **THEN** the generator SHALL fall back to a single sweeping eave rather than fail the build.

### Requirement: Ridge ornaments and dougong details
Cultivation roof and colonnade details SHALL include optional ridge/crown ornament placement through `RIDGE_ORNAMENT` and dougong bracket rhythms under deep eaves through `COLUMN` / `DETAIL_WOOD` slots. A style that omits the relevant optional slot SHALL skip the detail rather than fail loading.

#### Scenario: A sect hall ridge carries end ornaments
- **WHEN** a hall-class cultivation volume builds a ridged roof with `RIDGE_ORNAMENT` defined
- **THEN** the ridge ends SHALL carry ridge ornament pieces
- **AND** those blocks SHALL resolve through the `RIDGE_ORNAMENT` slot.

#### Scenario: A mortal style omits ridge ornament
- **WHEN** a style without a `RIDGE_ORNAMENT` slot builds a ridged roof
- **THEN** the ridge ornament SHALL be skipped rather than failing the build.

#### Scenario: A hall colonnade carries brackets
- **WHEN** a cultivation hall has a colonnade
- **THEN** repeating dougong bracket sets SHALL be placed under the eave
- **AND** bracket and column blocks SHALL resolve through style slots.

### Requirement: Moon gate opening form
The generator SHALL provide a `moon_gate` opening/motif that produces a round (圆洞) wall opening framed by style detail materials.

#### Scenario: A moon gate is placed in a wall
- **WHEN** a `moon_gate` is placed on an eligible wall span
- **THEN** the opening SHALL be approximately circular
- **AND** the surrounding frame SHALL resolve through the style's detail slots.

### Requirement: Spirit array ground motif
The generator SHALL provide a `spirit_array` (法阵) motif that places a flat ground formation pattern using spirit materials. It SHALL be a registered motif and SHALL only be reachable from styles that allow it.

#### Scenario: A spirit array is placed in a sect courtyard
- **WHEN** the `spirit_array` motif is placed by a sect-group build
- **THEN** it SHALL lay a ground glyph pattern using spirit-material slots such as crystal or dark formation blocks
- **AND** it SHALL NOT raise the walkable ground above the surrounding parcel.

#### Scenario: A mortal town never places a spirit array
- **WHEN** a `cultivation_town` build runs
- **THEN** the `spirit_array` motif SHALL NOT be placed.

### Requirement: Ritual furnishing motifs
The generator SHALL provide `incense_altar` and `cloud_rail` motifs registered as decoration motifs that resolve blocks through style slots.

#### Scenario: An incense altar is placed
- **WHEN** the `incense_altar` motif is placed
- **THEN** it SHALL form a raised altar feature using style detail and ritual-metal slots
- **AND** it SHALL be placed only where the active style allows the motif.

### Requirement: Cultivation forms are engine-level and group-gated
The cultivation form vocabulary SHALL live in the shared build engine and SHALL only be invoked when the active style/group lists the form. Groups that do not list a cultivation form SHALL never invoke it.

#### Scenario: A medieval build ignores cultivation forms
- **WHEN** a `medieval_village` build runs
- **THEN** no cultivation form (`sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, `tiered_eave_roof`, `moon_gate`, `spirit_array`, `incense_altar`, `cloud_rail`) SHALL be invoked.
