## ADDED Requirements

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
