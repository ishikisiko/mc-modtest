## ADDED Requirements

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
