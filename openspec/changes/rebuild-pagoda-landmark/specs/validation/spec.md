## ADDED Requirements

### Requirement: Pagoda quality validation checks scale and storey grammar
Pagoda quality validation SHALL report storey count, inset schedule,
intermediate eave levels, eave-cell count, lifted-corner count, resource height,
maximum horizontal span, and height-to-width ratio. It SHALL fail a pagoda with
fewer than five storeys, missing eaves at any occupied-storey boundary, fewer
than two inset reductions, no pyramidal crown, no finial, or a broken protected
stair opening or landing.

#### Scenario: Missing intermediate eave fails
- **WHEN** a five-storey pagoda reports fewer than four intermediate eave levels
- **THEN** quality validation SHALL fail with a pagoda eave-level error.

#### Scenario: Large tapered pagoda passes focused metrics
- **WHEN** a pagoda has at least five storeys, two inset reductions, a complete
  intermediate eave set, a pyramidal crown, a finial, and aligned stair openings
- **THEN** pagoda-specific quality validation SHALL pass
- **AND** its geometry metrics SHALL be written to the building report.

### Requirement: Pagoda library validation enforces variant distinctness
The cultivation-town building-library gate SHALL require three unique pagoda
profile signatures and three non-identical NBT hashes. The exported height
spread SHALL be at least eight blocks, and at least one profile SHALL report a
height-to-width ratio of at least `2.0`.

#### Scenario: Re-rolled pagodas fail distinctness
- **WHEN** two pagoda variants have the same profile signature or identical NBT
  bytes
- **THEN** the pagoda distinctness gate SHALL fail and name the duplicate pair.

#### Scenario: Large pagoda family passes distinctness
- **WHEN** all three profiles are unique, all NBT hashes differ, their height
  spread is at least eight, and one ratio is at least `2.0`
- **THEN** the pagoda distinctness gate SHALL report `passed: true`.
