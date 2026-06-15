## ADDED Requirements

### Requirement: Runtime placement resolves palette ids through the fallback resolver
Runtime template placement SHALL route every structure-palette block id through the
runtime mod-fallback resolver (for the `/myvillage town` realizer and `/myvillage place`)
before the block reaches the world, so that a palette id absent from the live registry
is placed as its vanilla fallback rather than the registry's `minecraft:air` default.
For a template whose palette names only registry-present ids, placement output SHALL be
identical to placement without the resolver.

#### Scenario: A town built without the decor mods places fallbacks, not holes
- **WHEN** `/myvillage town` realizes parcels whose templates contain external-mod ids and those mods are not installed
- **THEN** each missing mod block SHALL be placed as its vanilla fallback
- **AND** no one-block air hole SHALL appear where a mod block was authored.

#### Scenario: A town built with the decor mods places the real blocks
- **WHEN** `/myvillage town` realizes the same parcels with the decor mods installed
- **THEN** the authored external-mod blocks SHALL be placed unchanged.

#### Scenario: A vanilla-only template is unaffected
- **WHEN** placement realizes a template whose palette names only registry-present ids
- **THEN** the placed result SHALL be identical to placement without the resolver.
