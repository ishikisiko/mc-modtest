## ADDED Requirements

### Requirement: The perimeter is a deterministic, seed-derived variant
The town wall perimeter SHALL be a single closed boundary that MAY be a non-rectilinear polygon (chamfered, bastioned, or indented) rather than the site's bounding rectangle. The perimeter shape SHALL be selected from a fixed variant set by a shape id derived deterministically from the seed and site, and the south-gate segment SHALL remain a straight run on the perimeter so the gate stays wall-aligned. The perimeter SHALL stay within the site bounds, every gate cell SHALL lie on it, and it SHALL form exactly one closed loop.

#### Scenario: The same seed yields the same perimeter shape
- **WHEN** a town plan is produced twice for the same seed and site
- **THEN** the two perimeters SHALL be identical cell-for-cell
- **AND** both SHALL carry the same shape id.

#### Scenario: An irregular perimeter still closes and stays in bounds
- **WHEN** a town plan is produced with a non-rectangular perimeter variant
- **THEN** the perimeter SHALL form exactly one closed loop
- **AND** every perimeter cell SHALL lie within the site bounds
- **AND** every gate cell SHALL lie on the perimeter.

#### Scenario: The wall-to-district gap is reserved negative space
- **WHEN** a non-rectangular perimeter leaves cells between the wall and the orthogonal district grid
- **THEN** those gap cells SHALL be reserved as named negative space (moat / green / spirit field)
- **AND** they SHALL be disjoint from parcels and street cells.
