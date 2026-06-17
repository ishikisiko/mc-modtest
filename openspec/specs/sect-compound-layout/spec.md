# Sect Compound Layout

## Purpose

This spec captures the deterministic plan for a cultivation sect compound: a terraced stack ascending a single ritual axis, importance-by-terrace massing, axis-symmetric flanking volumes joined by covered galleries, terrace retaining and on-axis stairs with a cliff-backed summit, and the optional detached-spire flying-bridge feature.

## Requirements

### Requirement: A sect compound is a terraced stack ascending a single ritual axis

The sect-compound plan SHALL compose an ordered stack of terraces along one fall-line, joined by a single ritual axis that runs from the mountain gate (山门) on the lowest terrace to the principal hall (主殿) on the highest. The plan SHALL be deterministic on its seed: the same seed and site SHALL produce the same terrace count, terrace bounds, axis, and slot assignment. The default skeleton SHALL be five terraces — gate, disciple, assembly, scripture, summit — and the terrace count SHALL be a parameter in the range 4–6.

#### Scenario: The axis ascends from gate to hall

- **WHEN** a sect compound plan is produced
- **THEN** the plan SHALL record an ordered terrace stack with each terrace at a higher elevation than the one below it
- **AND** a single ritual axis SHALL run on the fall-line from the mountain gate on the lowest terrace to the principal hall on the highest terrace
- **AND** the gate, intermediate halls, scripture volume, and principal hall SHALL be ordered along that axis from foot to summit.

#### Scenario: The skeleton is deterministic and parametric

- **WHEN** the same seed and site are planned twice
- **THEN** the two plans SHALL have identical terrace count, terrace bounds, axis cells, and slot-to-terrace assignment
- **AND** the terrace count SHALL be within 4–6 with a default of five.

### Requirement: Slot importance and massing grade with terrace level

Within the compound, a slot's importance tier SHALL be non-decreasing with its terrace level, so a slot on a higher terrace receives an importance tier at least as high as one below it. The principal hall and the scripture pagoda SHALL receive the compound's top tiers, and therefore taller massing and finer roof grade than foot-level slots such as the gate and utilitarian rooms. Building-piece selection per slot SHALL be driven by terrace level, not by an unconstrained random roll.

#### Scenario: Summit volumes outrank foot volumes

- **WHEN** slots are assigned across the terraces
- **THEN** a slot on a higher terrace SHALL have an importance tier greater than or equal to any slot below it
- **AND** the principal hall and scripture pagoda SHALL hold the compound's highest importance tiers
- **AND** their massing height and roof grade SHALL exceed those of the gate-terrace slots.

### Requirement: Flanking volumes are symmetric about the axis and joined by covered galleries

Non-axis volumes SHALL be placed in mirrored pairs about the ritual axis (e.g. disciple-quarter rows, paired pavilions, flanking bell and drum towers). Volumes on the same terrace or on adjacent terraces SHALL be connected by covered galleries (廊) recorded as circulation/structure links with both endpoints, so the connected volumes are reachable through the gallery rather than the gallery being a decoration patch.

#### Scenario: Flanks mirror across the axis

- **WHEN** a sect compound plan places flanking volumes on a terrace
- **THEN** those volumes SHALL appear as pairs mirrored across the ritual axis, except for a single on-axis volume
- **AND** the bell tower and drum tower, when present, SHALL flank the gate terrace symmetrically.

#### Scenario: A gallery links two volumes with recorded endpoints

- **WHEN** the plan connects two flanking volumes
- **THEN** it SHALL record a covered-gallery link with both endpoints
- **AND** the two volumes SHALL be reachable through that gallery.

### Requirement: Terraces are linked by retaining faces and on-axis stairs, with the summit backed by a cliff

Each terrace SHALL meet the terrace above it through a retaining face whose height equals the inter-terrace rise, and the ritual axis SHALL cross between terraces by an on-axis stair flight of the configured axis-stair width, so the compound is traversable from gate to summit. The summit terrace SHALL declare a cliff-back edge, and the principal hall SHALL be placed against that cliff-back edge. The plan SHALL expose the geometry parameters terrace rise, terrace depth, terrace width taper, axis-stair width, and cliff-back height.

#### Scenario: Adjacent terraces are joined and traversable

- **WHEN** two adjacent terraces are planned
- **THEN** a retaining face of height equal to the inter-terrace rise SHALL be recorded between them
- **AND** an on-axis stair flight SHALL connect them so the axis is traversable from the lower terrace to the higher one.

#### Scenario: The principal hall backs the cliff

- **WHEN** the summit terrace is planned
- **THEN** it SHALL declare a cliff-back edge
- **AND** the principal hall SHALL be placed against that cliff-back edge.

#### Scenario: Terraces taper toward the summit

- **WHEN** the terrace widths are derived
- **THEN** the terrace width SHALL be non-increasing from foot to summit
- **AND** the parameters terrace rise, depth, width taper, axis-stair width, and cliff-back height SHALL be recorded in the plan.

### Requirement: The detached-spire flying-bridge feature ships as three deterministic form variants

The plan SHALL define an optional detached-spire feature: one volume placed on a detached outcrop reachable only by a flying bridge (飞桥) that spans a gap and is recorded as a circulation/structure link whose endpoints rest on the compound and the detached volume. The feature SHALL ship as exactly three form variants that differ on at least two of: which volume is detached, the bridge span and shape, and the spire's offset/bearing from the axis. The active variant SHALL be selected deterministically per seed, and the feature MAY be absent on a given seed.

#### Scenario: A present feature is one of three recorded variants

- **WHEN** a sect compound plan includes the detached-spire feature
- **THEN** the feature SHALL match exactly one of the three defined form variants
- **AND** the detached volume SHALL be reachable only by a flying bridge whose endpoints rest on the compound and on the detached volume
- **AND** the chosen variant SHALL be the same for the same seed.

#### Scenario: The feature may be absent

- **WHEN** a seed does not select the feature
- **THEN** the compound SHALL still form a complete terraced axial plan with all required terraces and the ritual axis intact.

#### Scenario: The three variants are distinct

- **WHEN** the three variants are compared
- **THEN** each pair SHALL differ on at least two of: detached volume, bridge span/shape, and spire offset/bearing.
