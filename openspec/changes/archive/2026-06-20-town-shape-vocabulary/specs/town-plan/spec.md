## ADDED Requirements

### Requirement: Perimeter shape is selected from a vocabulary
The town planner SHALL select the perimeter silhouette from a fixed vocabulary of closed-curve shape families. The vocabulary SHALL include at minimum the `square`, `circle` (inscribed in the site, 天圆), `oval` (ellipse), `D-shape` (semicircle joined to a rectangle, 半月), `octagon` (with 45° sides long enough to read as a true octagon, not a micro-chamfer), and `trapezoid`. Selection SHALL be a pure deterministic function of the seed via the shared `town_hash` module. Every selected perimeter SHALL remain a single closed boundary with at least one gate, and the gate-facing segment SHALL be a straight run wide enough to seat the gate cleanly regardless of family.

#### Scenario: Different seeds produce visibly different silhouettes
- **WHEN** town plans are produced for two seeds that resolve to different vocabulary entries
- **THEN** the two plans' perimeter cell sets SHALL differ by more than a small boundary nudge
- **AND** each plan's perimeter SHALL remain a single closed boundary
- **AND** each plan SHALL seat its gate on a straight segment of the perimeter.

#### Scenario: Shape selection is reproducible
- **WHEN** a town plan is produced twice for the same seed and site
- **THEN** both plans SHALL select the same vocabulary entry and produce identical perimeter cell sets.

#### Scenario: Curvy shapes use integer parity-safe predicates
- **WHEN** a plan selects a `circle`, `oval`, or `D-shape` perimeter
- **THEN** the perimeter and interior cell sets SHALL be derived from integer-only arithmetic (e.g. `(x−cx)² + (z−cz)² ≤ r²` for the circle, integer cross-multiply for the ellipse)
- **AND** the Java realizer SHALL reproduce the same cell sets bit-identically without float tolerance.

### Requirement: Composable modifiers extend a base shape
The planner MAY apply zero or more modifiers from a fixed set (at minimum `barbican` and `bastion`) to any selected base vocabulary entry. Modifier selection SHALL be a pure deterministic function of the seed via `town_hash`, independent of base-family selection. Each modifier SHALL compose with the base by set algebra over the bitten-cells set, and the composed perimeter SHALL still be a single closed boundary with the gate seated on a straight run.

#### Scenario: A modifier composes onto a base shape
- **WHEN** a plan selects a base family and a non-empty modifier set
- **THEN** the resulting perimeter SHALL equal the base perimeter modified by the modifier's set algebra
- **AND** the perimeter SHALL remain a single closed boundary
- **AND** the gate SHALL still lie on a straight segment.

#### Scenario: Modifier selection is independent of base selection
- **WHEN** two plans share a seed-derived base family but differ in modifier set
- **THEN** both plans' base perimeter SHALL be identical
- **AND** the modifier's contribution SHALL be reproducible from the seed alone.

### Requirement: The internal macro grid is seed-derived
The planner SHALL derive the main-street spine position, the cross-lane z-bands, and the per-district width/depth offsets from the seed via `town_hash`, within bounded ranges. The bounds SHALL keep every district an axis-aligned rectangle and SHALL keep the spine intersecting a perimeter gate and the civic core. The grid SHALL stay strictly orthogonal; no slanted or curved streets are introduced by this requirement.

#### Scenario: Different seeds produce different orthogonal grids
- **WHEN** town plans are produced for two different seeds with the same site and the same perimeter vocabulary entry
- **THEN** the two plans' spine centerline, cross-lane positions, or district bounds SHALL differ by at least one cell
- **AND** every district SHALL remain an axis-aligned rectangle
- **AND** the spine SHALL still connect a perimeter gate to the civic core.

#### Scenario: Grid derivation is reproducible and parity-tracked
- **WHEN** a town plan is produced twice for the same seed and site
- **THEN** both plans SHALL produce identical spine, lane, and district-bound parameters
- **AND** every parameter value SHALL appear in `parity_constants()` so the runtime validator catches drift.

#### Scenario: Grid bounds preserve the spine-to-gate invariant
- **WHEN** a plan is produced for any seed
- **THEN** the spine centerline SHALL intersect the south gate's straight segment
- **AND** the spine SHALL extend unbroken from the gate to the civic core
- **AND** no district bound SHALL sever the spine.

### Requirement: Town plans meet a distinctness gate across probe seeds
The planner SHALL produce plans whose pairwise variation across a fixed probe-seed set meets a measurable distinctness floor. The validation harness SHALL compute, for each pair of probe seeds, (a) the Jaccard distance between the two plans' perimeter cell sets and (b) a coarse silhouette descriptor (perimeter bounding-box aspect, corner/curve cell fraction). The harness SHALL fail when any pair falls below either floor. Floors SHALL be calibrated to fail the pre-change baseline (every probe seed resolving to a near-identical square) and to pass the post-change probe set.

#### Scenario: Probe-seed towns are visibly distinct
- **WHEN** the validation harness generates plans for the fixed probe-seed set
- **THEN** every pair of probe-seed plans SHALL meet the Jaccard-distance floor on perimeter cell sets
- **AND** every pair SHALL meet the silhouette-descriptor floor
- **AND** any failure SHALL name the colliding pair of seeds and the missed metric.

#### Scenario: Distinctness measurement is reproducible
- **WHEN** the validation harness is run twice against the same planner version
- **THEN** both runs SHALL produce identical distinctness measurements for every probe-seed pair.
