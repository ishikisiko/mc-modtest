## ADDED Requirements

### Requirement: Style profile schema includes cultivation form slots
The style profile schema SHALL recognize additional optional material slots `COLUMN` (檐柱), `PLATFORM_STONE` (台基), `RIDGE_ORNAMENT` (脊饰), and `BALUSTRADE` (栏杆) alongside the existing slots. Each slot list SHALL end with a `minecraft:` vanilla fallback per the existing fallback convention. A style MAY omit any of these slots, in which case generators referencing the missing slot SHALL skip that element rather than failing style loading.

#### Scenario: The cultivation sect style defines form slots
- **WHEN** the `cultivation_sect` style is loaded
- **THEN** the profile SHALL include `COLUMN`, `PLATFORM_STONE`, `RIDGE_ORNAMENT`, and `BALUSTRADE`
- **AND** each slot's last entry SHALL be a `minecraft:` fallback.

#### Scenario: The vanilla profile resolves form slots to fallbacks
- **WHEN** a cultivation style is loaded with `available_namespaces = {"minecraft"}` and a build resolves a form slot
- **THEN** the slot SHALL resolve to its trailing vanilla fallback
- **AND** no resolution SHALL return air.

### Requirement: Cultivation styles list cultivation forms and exclude Western domestic motifs
The `cultivation_town` and `cultivation_sect` style profiles SHALL list the cultivation roof forms applicable to them (`sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, and `tiered_eave_roof` for the sect) in `allowed_roof_types`, and SHALL NOT list the Western domestic motifs `woodpile`, `barrel_cluster`, `fence_patch`, `side_chimney`, or `small_porch` in `allowed_motifs`.

#### Scenario: The sect style allows sweeping eaves and excludes Western motifs
- **WHEN** the `cultivation_sect` style is loaded
- **THEN** `allowed_roof_types` SHALL include `sweeping_eave_roof`
- **AND** `allowed_motifs` SHALL NOT include `woodpile`, `barrel_cluster`, `fence_patch`, `side_chimney`, or `small_porch`.

### Requirement: Cultivation proportions favor deep eaves and a tall platform
The cultivation style `proportions` SHALL specify a deep roof overhang and a raised platform so that generated halls read with a horizontal, deep-eave silhouette: a roof overhang of at least 2 admissible on hall-class volumes, a platform/foundation height of at least 2 admissible, and a roof-height ratio centred near one-half.

#### Scenario: Sect proportions specify a deep overhang and platform
- **WHEN** the `cultivation_sect` style proportions are read
- **THEN** `roof_overhang` SHALL admit at least 2
- **AND** `foundation_height` (platform) SHALL admit at least 2.
