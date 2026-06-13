## ADDED Requirements

### Requirement: A Chinese style profile is available
The generator SHALL provide a Chinese style profile loadable by id from `tools/buildgen/styles/<chinese_id>.json`, exposing the same profile structure as existing styles (`material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, `proportions`). Its vocabulary SHALL express sloped roofs, timber-frame walls, and white wall surfaces, and its `allowed_roof_types` SHALL include the courtyard roof grades 硬山, 悬山, and 歇山. Western-only blocks unsuitable for the Chinese style SHALL be listed in `forbidden_blocks`.

#### Scenario: Loading the Chinese style
- **WHEN** the generator requests the Chinese style id
- **THEN** it SHALL load the corresponding `tools/buildgen/styles/<chinese_id>.json`
- **AND** the loaded profile SHALL include `material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, and `proportions`
- **AND** `allowed_roof_types` SHALL include the 硬山, 悬山, and 歇山 roof grades.

#### Scenario: Chinese sub-buildings use the Chinese style
- **WHEN** a `main_hall`, `side_wing`, or `front_row` is generated for a compound
- **THEN** it SHALL resolve its primary materials through the Chinese style profile's slots
- **AND** it SHALL pass the forbidden-block quality gate for that profile.
