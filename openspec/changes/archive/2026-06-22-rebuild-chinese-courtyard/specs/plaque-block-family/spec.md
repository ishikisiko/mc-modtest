## ADDED Requirements

### Requirement: Main hall central bay carries a plaque
The `plaque_bindings.json` registry SHALL include an entry for the `main_hall` archetype of the `chinese_courtyard` family. When the rebuilt Chinese courtyard main hall is generated, the facade-detail pass SHALL invoke the plaque placement op on the central bay of the main hall's entry face with a Chinese-courtyard-tier frame preset (e.g. `lord_manor_heraldry_5w` or `sect_simple_pine_4w`) and a 名-style inscription (e.g. a 堂 / 斋 name). Other courtyard buildings (`side_wing`, `front_row`, `gate_house`, `inner_gate`) SHALL NOT receive a plaque binding by default.

#### Scenario: A main hall gets a central-bay plaque
- **WHEN** the rebuilt `main_hall` of a `chinese_courtyard` compound is generated
- **THEN** the facade-detail pass SHALL consult `plaque_bindings.json` for the `main_hall` archetype
- **AND** a `myvillage:wall_plaque` SHALL be placed on the central bay of the entry face.

#### Scenario: Non-main-hall courtyard buildings have no plaque binding
- **WHEN** a `side_wing`, `front_row`, `gate_house`, or `inner_gate` building is generated for the `chinese_courtyard` family
- **THEN** `plaque_bindings.json` SHALL have no entry for that archetype
- **AND** the facade-detail pass SHALL fall back to the `SIGNAGE` slot dispatch (or place no signage if the slot is empty).
