## ADDED Requirements

### Requirement: The offline preview renders baked plaque inscriptions
The offline structure preview tool SHALL render `myvillage:wall_plaque`, `myvillage:wall_plaque_vertical`, `myvillage:hanging_plaque`, and `myvillage:hanging_plaque_vertical` blocks using their blockstate-resolved models, full plaque textures, and model UV windows, including baked inscription pixels. Legacy `minecraft:painting` entities whose variant references `myvillage:inscription/...` MAY be rendered as overlays for backward inspection, but generated structures SHALL NOT require them.

#### Scenario: A structure with a plaque is previewed
- **WHEN** `tools/preview_structure.py` runs on a structure containing a `myvillage:wall_plaque`
- **THEN** the preview SHALL render each plaque block from the blockstate-resolved full plaque texture and that block model's UV window
- **AND** the rendered texture SHALL include the baked inscription pixels.

#### Scenario: A plaque block has no inscription entity
- **WHEN** the preview encounters a `myvillage:wall_plaque` block with no corresponding painting entity
- **THEN** the preview SHALL render the baked plaque block texture
- **AND** the preview SHALL NOT flag `missing_inscription`.

#### Scenario: An inscription entity is referenced but its PNG is missing
- **WHEN** the preview encounters a painting entity with variant `myvillage:inscription/4w/zang_jing_ge`
- **AND** the corresponding PNG does not exist
- **THEN** the preview SHALL render a placeholder quad (e.g. a solid color)
- **AND** the preview's report SHALL flag the structure with `missing_inscription_asset`.
