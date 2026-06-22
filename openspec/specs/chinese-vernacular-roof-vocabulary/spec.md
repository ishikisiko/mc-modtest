# Chinese Vernacular Roof Vocabulary

## Purpose

This spec captures the four Chinese vernacular (民居) roof forms registered in the build generator's `ROOF_REGISTRY` for use by styles such as `chinese_courtyard`. They are the vernacular counterparts to the cultivation monumental forms (`sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, `tiered_eave_roof`) captured by the cultivation-form-vocabulary capability.

## Requirements

### Requirement: Four Chinese vernacular roof forms are registered
The build generator SHALL register four Chinese vernacular (民居) roof forms in `ROOF_REGISTRY` as distinct handlers, available to any style that lists them in `allowed_roof_types`: `chinese_flush_gable` (硬山, gable flush with the side wall), `chinese_overhang_gable` (悬山, roof overhangs past the gable wall on both eaves), `chinese_half_hip` (歇山, upper half overhanging the gable, lower half a 45° 抱厦 skirt with 围脊), and `chinese_round_ridge` (卷棚, no main ridge block — a smooth circular ridge bar instead). The four handlers SHALL share eave-curve and ridge-cap geometry helpers but SHALL NOT dispatch among themselves by string-matching.

#### Scenario: A style opts into the vernacular roof vocabulary
- **WHEN** a style profile lists `chinese_flush_gable` in `allowed_roof_types`
- **AND** a sub-building requests roof grade `硬山`
- **THEN** the build pass SHALL dispatch to the `chinese_flush_gable` handler
- **AND** the placed roof SHALL have its gable flush with the side walls (no overhang past the gable end).

#### Scenario: 歇山 has a 45° skirt and a 围脊
- **WHEN** a sub-building requests roof grade `歇山`
- **THEN** the `chinese_half_hip` handler SHALL place an upper gable portion that overhangs past the gable wall
- **AND** a lower 45° 抱厦 skirt SHALL wrap the four sides below the gable
- **AND** a 围脊 (a ridge tile where the skirt meets the gable) SHALL be placed
- **AND** the roof plane SHALL be closed (no roof-hole defect at the gable-to-skirt seam).

#### Scenario: 卷棚 has no ridge block
- **WHEN** a sub-building requests roof grade `卷棚`
- **THEN** the `chinese_round_ridge` handler SHALL NOT place any block on the main ridge axis
- **AND** a smooth circular ridge SHALL be approximated by a curve of partial blocks (stairs/slabs) running along the ridge.

#### Scenario: Roof forms do not collide with cultivation forms
- **WHEN** a style profile lists the four `chinese_*` vernacular forms in `allowed_roof_types`
- **THEN** the cultivation monumental forms (`sweeping_eave_roof`, `hip_roof`, `pyramidal_roof`, `tiered_eave_roof`) SHALL remain registered and unaffected
- **AND** a style that lists only the `chinese_*` forms SHALL NOT be able to dispatch to any cultivation monumental form.

### Requirement: Chinese vernacular forms are distinct from cultivation monumental forms
The four `chinese_*` vernacular roof forms SHALL be registered separately from the cultivation monumental forms even where they share a name root (e.g. `chinese_half_hip` vs `sweeping_eave_roof`). The vernacular forms SHALL use smaller overhang, plain rafter feet (no dramatic curve), and no required platform, matching vernacular (民居) proportions; the monumental forms retain their existing larger proportions.

#### Scenario: Two profiles with the same roof name root
- **WHEN** `cultivation_sect` style lists `sweeping_eave_roof` in `allowed_roof_types`
- **AND** `chinese_courtyard` style lists `chinese_half_hip` in `allowed_roof_types`
- **THEN** the two styles SHALL dispatch to different handlers
- **AND** the cultivation style's roof SHALL have a deeper overhang and more dramatic curve than the vernacular style's roof at the same footprint.
