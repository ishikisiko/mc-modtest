## ADDED Requirements

### Requirement: Cultivation archetype variants are deliberately distinct forms

Each small/medium cultivation town archetype (`cultivation_house`, `cultivation_shop`, `cultivation_market`, `cultivation_inn`) SHALL define its variant tiers (`_v1`/`_v2`/`_v3`) as distinct massing templates. Any two variants of the same archetype SHALL differ on **at least two** of the following massing axes: footprint (长宽), total height including platform and wall (高低), aspect ratio or roof-ridge axis (胖瘦), volume count, roof type, and presence of a walled rear courtyard (后院) or side wing (厢房). A variant SHALL NOT be produced by re-rolling a single shared footprint pool with no other deliberate difference.

#### Scenario: A cultivation house's three variants are distinct forms

- **WHEN** `cultivation_house` is generated at `_v1`, `_v2`, and `_v3`
- **THEN** each pair of variants SHALL differ on at least two massing axes among footprint, total height, aspect/ridge axis, volume count, roof type, and rear-courtyard/side-wing presence
- **AND** no two of the three variants SHALL share an identical main-volume footprint, wall height, and roof type simultaneously.

#### Scenario: Shop, market, and inn variants are also distinct forms

- **WHEN** `cultivation_shop`, `cultivation_market`, or `cultivation_inn` is generated across its variant tiers
- **THEN** each archetype's variants SHALL likewise differ on at least two massing axes
- **AND** the differentiation SHALL be expressed through the cultivation massing grammar (platform height, wall height, ridge axis, side wing, rear courtyard), not through Western domestic features.

### Requirement: Variant form is selected deterministically per variant index

The massing template for a cultivation variant SHALL be selected deterministically from its variant index (the `_vN` suffix), not by unconstrained RNG that can cause adjacent variants to collide on the same form. The RNG MAY jitter within-template details (decoration placement, material variation) but SHALL NOT be the sole determinant of which template a variant uses. Regeneration with the same seed SHALL be reproducible.

#### Scenario: Variant index drives the template, not a colliding roll

- **WHEN** `cultivation_house_v1` and `cultivation_house_v2` are generated
- **THEN** they SHALL resolve to different massing templates as a function of their variant index
- **AND** they SHALL NOT be able to resolve to the same footprint, height, and roof simultaneously by chance.

#### Scenario: Same seed reproduces the same structure

- **WHEN** a cultivation variant is generated twice with the same seed
- **THEN** both generations SHALL produce an identical structure.

### Requirement: A cultivation archetype's variant set meets a measurable distinctness gate

Within each small/medium cultivation town archetype, the `silhouette_score` spread (max minus min) across its shipped variants SHALL be at least 30, and no two shipped variant structures SHALL be byte-identical. Library validation SHALL fail the cultivation town library when either condition is violated.

#### Scenario: The library report shows sufficient silhouette spread

- **WHEN** the cultivation town building library is generated and reported
- **THEN** for each of `cultivation_house`, `cultivation_shop`, `cultivation_market`, and `cultivation_inn`, the difference between the highest and lowest variant `silhouette_score` SHALL be at least 30.

#### Scenario: Byte-identical variants fail validation

- **WHEN** two variant structures of the same cultivation archetype have identical bytes
- **THEN** the cultivation town library validation SHALL fail and SHALL name the colliding variants.
