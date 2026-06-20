## Why

The runtime cultivation town (`/myvillage town`) reads as a flat square box:
its wall is four straight edges, every district is an axis-aligned rectangle,
and the Java realizer mirrors that geometry exactly. This is visually monotone
and unlike real Chinese walled cities, which commonly pair an orthogonal street
grid with an irregular city wall and non-mirrored block edges. The spec today
never mandates a rectangle — it only requires a closed perimeter, gates on the
perimeter, and a partition into districts — so the squareness is an
implementation accident, not a contract. This change makes the shape
intentionally irregular while preserving every existing invariant.

## What Changes

- **Perimeter deformation (Lever A).** The wall stops being the site's bounding
  rectangle. `_boundary` becomes a deterministic polygonal/partial-cell boundary
  selected from a small variant set (chamfer / bastion / moon-fort, composable),
  chosen per seed. The south-gate segment stays straight so the gate remains on
  the wall; the gap between the new wall and the untouched district rectangles
  becomes reserved negative space (moat / green / spirit field).
- **District de-gridding (Lever B).** Inter-district boundaries may become
  stepped or kinked instead of straight, and `TownDistrict` gains an explicit
  cell set so its shape is no longer forced to `_rect(bounds)`. West/east mirror
  symmetry is preserved (no roster asymmetry) to keep the civic-core
  skyline-relief rule untouched.
- **Python ⇄ Java parity.** `parity_constants()` exports the new perimeter
  variant id and district cell descriptors; `TownGenerator` mirrors both
  derivations deterministically (no shared RNG — variant id carries the shape).
- **Subdivision generalization.** `_subdivide_district` switches from slicing
  `district.bounds` to operating over `district.cells`, so parcel subdivision,
  frontage alignment, and alley emission work on arbitrary cell sets.
- **No command-surface change.** `/myvillage town [seed]` behaves the same to
  the user; README command list is unchanged. Preview artifacts are regenerated.

## Capabilities

### New Capabilities
<!-- None. This change modifies two existing capabilities rather than introducing one. -->

### Modified Capabilities
- `town-plan`: The perimeter requirement relaxes from "the site's bounding
  rectangle" to "a closed boundary that may be a non-rectilinear polygon
  deterministically derived from site and seed", and gains a requirement that
  the wall variant is reproducible per seed. Gate-on-perimeter, spine↔gate
  connectivity, and the single-dominant-landmark invariants are unchanged.
- `town-districts`: The partition requirement generalizes so a district's shape
  is an explicit cell set (which may have stepped/kinked edges) rather than an
  axis-aligned rectangle derived from `bounds`. Non-overlap, full coverage of
  non-street cells, per-seed determinism, and the core-outranks-fringe
  hierarchy are unchanged.

## Impact

- **`tools/buildgen/town.py`** — `_boundary`, `_layout`, `TownDistrict` schema,
  `_subdivide_district`, `generate_town_plan`, `validate_town_plan`,
  `parity_constants`.
- **`src/main/java/com/example/myvillage/town/TownGenerator.java`** — mirror the
  new perimeter derivation, gate placement, district cell sets, and subdivision;
  update hardcoded parity values.
- **Validators** — `validate_town_plan` / `validate_realized_town` assertions
  that key off `_boundary`/`district.cells` auto-follow; explicit re-checks for
  "no plan cell exits the site" and "wall is a single closed loop".
- **Preview / acceptance** — regenerate `out/preview/` per
  `docs/ai-kb/09_validation_checklist.md`; README command list unchanged.
- **Design note** — `docs/ai-kb/11_town_shape_irregularity.md` already captures
  the exploration this change formalizes.
