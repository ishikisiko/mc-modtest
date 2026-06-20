## Context

`docs/ai-kb/11_town_shape_irregularity.md` is the exploration this design
formalizes; read it for ASCII shape sketches and the full change-point tables.
Background in brief: the town is square because of four stacked layers
(`TownSite` rect → `_boundary` four edges → `_layout` all-rect districts → Java
mirror), and `parity_constants()` (`town.py:562`) enforces Python⇄Java geometry
parity, so any shape change must move all four layers plus the parity constant
set together, deterministically.

## Goals / Non-Goals

**Goals:**
- Wall perimeter reads as non-rectangular (Lever A), varying per seed.
- Inter-district boundaries may be stepped/kinked (Lever B), breaking the
  chessboard read without breaking west/east mirror symmetry.
- Every existing invariant survives: closed single-loop perimeter, gate on
  perimeter, spine↔gate↔core connectivity, shrine as sole dominant landmark,
  ritual axis inside `civic_core`, core-outranks-fringe hierarchy, per-seed
  determinism, Python⇄Java parity.

**Non-Goals:**
- Asymmetric west/east district rosters (deliberately deferred — it complicates
  the civic-core skyline-relief rule; mirror symmetry is preserved).
- Replacing the `TownSite` rectangle itself with a polygon (Lever C from the
  note — out of scope; the site stays a rectangle and the perimeter deforms
  inside it).
- Worldgen placement changes; `/myvillage town` command surface is unchanged.

## Decisions

### D1. Perimeter shape is selected by a seed-derived shape id, geometry is a pure function of (site, shape id)
The seed picks one id from a small fixed variant set (`chamfer`, `bastion`,
`moon-fort`, composable per edge). The actual cell geometry is a pure
deterministic function of the site size and the id — **not** a replayed RNG
stream. The Java mirror therefore only needs the same id→geometry builder, not
a shared RNG sequence.
- *Alternatives considered:* (a) pure site-derivation, same shape for every
  town — rejected as too monotonous; (b) full seed-RNG replay — rejected because
  it forces Java to reproduce the exact RNG call sequence, a fragile coupling.
- The id is exported through `parity_constants()` alongside the perimeter cell
  count so CI catches drift.

### D2. The south-gate segment is a deformation-free straight run
Every variant keeps a straight, wall-aligned segment on the south edge around
`cx` so `south_gate_cells` still lands cleanly on the perimeter. Deformation is
applied to corners and to non-gate edge mid-sections only.
- This keeps the gate-on-perimeter and spine↔gate invariants for free.

### D3. The wall-to-district gap becomes reserved negative space, not extended district
Districts stay rectangular at Lever-A's scope; cells between the deformed wall
and the district grid are emitted as named `NegativeSpace` (moat / green /
spirit field). This avoids coupling Lever A to Lever B.
- *Alternative:* extend `fringe` cells to fill the gap — rejected because it
  changes district shape at Lever A's scope and entangles the two levers.

### D4. `TownDistrict` keeps `bounds` (AABB) and gains an authoritative `cells` set
`bounds` remains the axis-aligned bounding box, used by existing sort/spatial
callsites (`town.py:1407`, `1583`, `1611-1624`). A new explicit `cells` field
holds the real shape; `TownDistrict.cells` returns that set instead of
`_rect(*bounds)`. Stepped/kinked variants set `cells` to a subset (or
super-set-with-cutouts) of the AABB.
- *Alternative:* drop `bounds` entirely — rejected because it touches every
  sorting and adjacency helper for no spec benefit.

### D5. `_subdivide_district` operates over `district.cells`, not the AABB
Today it unpacks `x0,z0,x1,z1 = district.bounds` (`town.py:829`) and slices the
rectangle. It is rewritten to iterate rows/columns of the AABB but mask to
`district.cells`, so slab/frontage/edge-touch logic (`_edge_touches_street`,
alley emission) generalizes to arbitrary cell sets. Step depth on kinked
boundaries is bounded (a few cells) so parcel rows stay usable.
- Parcels that would not fit the masked slab are retried/shifted as today.

### D6. West/east mirror symmetry is preserved under deformation
Stepped/kinked boundaries are applied symmetrically so the west and east
district bands remain mirror images. This keeps the civic-core skyline-relief
rule (pagoda/bell-drum-tower flanking the shrine) undisturbed.
- *Alternative:* break mirror for more variety — deferred (see Non-Goals).

## Risks / Trade-offs

- **Java mirror drift** → The variant id, perimeter cell count, and district
  cell counts join `parity_constants()`; the existing parity test fails loud on
  divergence. Mitigates the biggest coupling risk.
- **Subdivision produces fragmented parcels on kinked districts** → Bound step
  depth to ≤ a few cells; keep the existing retry/shift path; the validator's
  parcel-inside-district and footprint-overlap checks catch regressions.
- **Validator false-positives on the new negative-space gap** → Gap cells are
  emitted as `NegativeSpace` and flow through the existing
  `negative_cells`-disjoint checks; no new assertion category needed.
- **Preview visual regressions** → Regenerate preview across several seeds per
  `09_validation_checklist.md` and eyeball the wall line and district edges
  before archiving.
- **Larger change surface (A+B together)** → Tasks are sequenced A-first so A
  is independently shippable/validatable before B begins; B's tasks are marked
  as depending on A being green.

## Migration Plan

No runtime migration: towns are generated on demand by `/myvillage town`, and
existing saved worlds are untouched (no worldgen structure change). Rollback is
a plain revert of the Python + Java + parity changes. No data-format migration.

## Open Questions

- Initial variant set size — start with 2 (chamfer + bastion) or 3 (add
  moon-fort)? Lean 2 for the first cut, expand later.
- Max step depth for kinked district boundaries — 3 cells or 5? Decide during
  B's first subdivision pass against preview output.

## Scope realized (implementation)

- **Lever A** shipped with three inward-only perimeter variants
  (`square` / `chamfer` / `indent`), all confined to the empty 8-cell east/west
  margin and corner triangles, so the south-gate segment and every district stay
  untouched. The bitten cells are emitted as a `moat` negative space
  (Python/preview; Java leaves them as terrain — a documented cosmetic
  asymmetry, since parity does not inspect negative space).
- **Lever B** shipped the schema (`TownDistrict.cells` authoritative via
  `cells_override`; `bounds` stays the AABB) and a cells-membership guard in
  `_subdivide_frontage` (`_parcel_fits`), so subdivision is cell-set-aware.
  Investigation showed every inter-district boundary is either parcel-dense
  (market/residential/gate) or civic-precinct-coupled (`_validate_precinct`
  derives wall edges from `core.bounds` and checks precinct cells ⊆ core cells),
  so a *visible, safe* de-grid is only possible on the **fringe** (no parcels,
  no precinct coupling). The `chamfer` shape therefore chamfers the two fringe
  districts' exterior corners (mirror-symmetric); `square`/`indent` keep all
  districts rectangular. Java needs no functional change (fringe has no parcels;
  Java dresses the fringe spirit-field by AABB bounds) — Python and Java still
  emit identical parcels.
- **Deferred (follow-up change):** de-gridding parcel-bearing districts and the
  civic core requires decoupling the civic-precinct derivation from
  `core.bounds` (derive wall/colonnade/spirit-way from `core.cells` instead).
  That is intentionally out of scope here to keep the civic-core contract
  intact.
