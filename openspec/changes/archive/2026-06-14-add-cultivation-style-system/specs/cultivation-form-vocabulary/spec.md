## ADDED Requirements

### Requirement: Tiered eave roof form
The generator SHALL provide a `tiered_eave_roof` roof type that produces a multi-eave (重檐) silhouette with more than one stacked eave tier, distinct from the single-eave `gable_roof`. It SHALL be registered in the roof registry and resolve all blocks through style material slots.

#### Scenario: A sect hall builds a tiered eave roof
- **WHEN** a volume names `tiered_eave_roof` and its footprint is large enough
- **THEN** the generated roof SHALL have at least two stacked eave tiers
- **AND** all roof blocks SHALL resolve through the style's `ROOF_DARK` slot.

#### Scenario: The footprint is too small for tiers
- **WHEN** a `tiered_eave_roof` is requested on a footprint too small for multiple tiers
- **THEN** the generator SHALL fall back to a single-eave roof rather than fail the build.

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
- **THEN** it SHALL lay a ground glyph pattern using spirit-material slots (e.g. crystal / dark formation blocks)
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
- **THEN** no cultivation form (`tiered_eave_roof`, `moon_gate`, `spirit_array`, `incense_altar`, `cloud_rail`) SHALL be invoked.
