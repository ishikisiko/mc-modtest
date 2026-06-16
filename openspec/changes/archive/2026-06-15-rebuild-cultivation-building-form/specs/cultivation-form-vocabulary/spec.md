## ADDED Requirements

### Requirement: Sweeping eave roof form (飞檐翘角)
The generator SHALL provide a `sweeping_eave_roof` roof type whose eave corners step up-and-out to produce an upturned-corner (翘角) silhouette over a deep overhang, distinct from the straight triangular `gable_roof`. It SHALL be registered in the roof registry and resolve all blocks through style material slots.

#### Scenario: A hall builds upturned eave corners
- **WHEN** a volume names `sweeping_eave_roof` on a footprint large enough
- **THEN** the eave corners SHALL rise above the eave line (翘角)
- **AND** the eave SHALL overhang the wall plane by the style's deep-overhang proportion
- **AND** roof blocks SHALL resolve through the style's `ROOF_DARK` / `ROOF_TILE` slots.

#### Scenario: Small footprint falls back gracefully
- **WHEN** `sweeping_eave_roof` is requested on a footprint too small for corner sweeps
- **THEN** it SHALL fall back to a single straight eave rather than fail the build.

### Requirement: Hip roof form (庑殿)
The generator SHALL provide a `hip_roof` (庑殿) roof type sloping on all four sides to a central ridge, registered in the roof registry and resolving all blocks through style material slots.

#### Scenario: A hip roof slopes on four sides
- **WHEN** a volume names `hip_roof` on an eligible footprint
- **THEN** the roof SHALL slope inward on all four sides
- **AND** all roof blocks SHALL resolve through the style's roof slots.

### Requirement: Pyramidal roof form (攒尖)
The generator SHALL provide a `pyramidal_roof` (攒尖) roof type that converges from a square footprint to a single apex terminating in a finial, for pavilions and pagoda crowns. It SHALL be registered in the roof registry and resolve all blocks through style material slots.

#### Scenario: A pavilion crown converges to a finial
- **WHEN** a volume names `pyramidal_roof` on a roughly square footprint
- **THEN** the roof SHALL converge to a single apex
- **AND** a finial SHALL crown the apex through the `RIDGE_ORNAMENT` slot.

### Requirement: Roof ridge and crown ornament (宝顶/鸱吻)
The generator SHALL provide ridge ornament forms — a crowning finial (宝顶) at a pyramidal apex or main-ridge center, and ridge-end pieces (鸱吻/正脊) at gable, hip, or sweeping-eave ridge ends — registered as roof detail and resolving through the `RIDGE_ORNAMENT` style slot. A style that omits the slot SHALL skip the ornament rather than fail.

#### Scenario: A sect hall ridge carries end ornaments
- **WHEN** a hall-class cultivation volume builds a ridged roof with `RIDGE_ORNAMENT` defined
- **THEN** the ridge ends SHALL carry 鸱吻 ornament pieces
- **AND** those blocks SHALL resolve through the `RIDGE_ORNAMENT` slot.

#### Scenario: A mortal style omits ridge ornament
- **WHEN** a style without a `RIDGE_ORNAMENT` slot builds a ridged roof
- **THEN** the ridge ornament SHALL be skipped rather than failing the build.

### Requirement: Dougong bracket detail (斗拱)
The generator SHALL provide a `dougong` (斗拱) bracket-set detail placed under deep eaves as a repeating bracket rhythm carrying the overhang, replacing the thin single-block fence-under-eave rhythm. It SHALL resolve through the column/detail slots.

#### Scenario: Brackets sit under a deep eave
- **WHEN** a colonnaded hall builds a deep eave with dougong enabled
- **THEN** a repeating bracket-set rhythm SHALL sit between the column heads and the eave
- **AND** the bracket blocks SHALL resolve through the `COLUMN` / `DETAIL_WOOD` slots.

## MODIFIED Requirements

### Requirement: Tiered eave roof form
The generator SHALL provide a `tiered_eave_roof` roof type that produces a multi-eave (重檐) silhouette of two or more stacked eave tiers, where **each tier is a sweeping eave with upturned corners** (飞檐翘角) rather than a stacked straight gable. It SHALL be registered in the roof registry and resolve all blocks through style material slots.

#### Scenario: A sect hall builds a curved double eave
- **WHEN** a volume names `tiered_eave_roof` and its footprint is large enough
- **THEN** the generated roof SHALL have at least two stacked eave tiers
- **AND** each tier's corners SHALL be upturned (翘角)
- **AND** all roof blocks SHALL resolve through the style's roof slots.

#### Scenario: The footprint is too small for tiers
- **WHEN** a `tiered_eave_roof` is requested on a footprint too small for multiple tiers
- **THEN** the generator SHALL fall back to a single sweeping eave rather than fail the build.
