## ADDED Requirements

### Requirement: The cultivation_sect group resolves to a realized terraced compound

The `cultivation_sect` group's terraced/axial layout strategy SHALL resolve to the `sect-compound-layout` plan and be realized by the `/myvillage sect` command, so selecting the group produces a terraced axial compound built against terrain rather than a single exported block or a standalone building.

#### Scenario: Selecting the sect group builds a compound

- **WHEN** the generator selects the `cultivation_sect` group and a sect is built
- **THEN** it SHALL produce a terraced axial compound via the `sect-compound-layout` plan and the `/myvillage sect` realizer
- **AND** it SHALL restrict archetype selection to the sect roster
- **AND** it SHALL NOT emit a single exported block or a standalone building in place of the compound.
