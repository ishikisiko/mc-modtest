## Context

The 0.12.0 `town-shape-irregularity` change shipped two levers: A (perimeter variants) confined to the empty 8-cell east/west margin and corner triangles, and B (cell-set-aware subdivision) wired only to fringe in chamfer mode. Its `design.md` explicitly deferred the high-impact follow-ups: polygonal site (Lever C), full Lever B (de-gridding parcel-bearing districts), and asymmetric rosters. Net effect: `chamfer` bites 144 cells and `indent` 260 cells out of ~25,600 (<1%), and `_layout` (`town.py:481-536`) hardcodes `cx`, lane z-bands, and district bounds so the internal skeleton is 0% seed-varying. Two towns of different seeds read as the same square with different parcel fill.

The `town-plan` spec already permits any closed perimeter and any partition into districts — the squareness is an implementation accident. The hard coupling that survived 0.12.0 is Python⇄Java parity: `parity_constants()` (`town.py:562`) and the runtime validator require every geometry constant to be a pure deterministic function of `(site, seed)` that both ends reproduce bit-identically, without a shared RNG stream.

This change delivers the deferred macro-variation in one cohesive cut: a perimeter shape vocabulary (including 天圆 / circle), seed-driven internal grid, clip-to-shape outer districts, and a distinctness gate. See `docs/ai-kb/11_town_shape_irregularity.md` for the original lever analysis this builds on.

## Goals / Non-Goals

**Goals:**
- Perimeter silhouette varies visibly across seeds through a documented vocabulary that includes a circle (天圆), oval, D-shape (半月), large octagon (true 45° sides), trapezoid, and the existing square.
- Internal macro grid (spine position, lane z-bands, district widths) is seed-derived within bounded ranges; every seed produces a visibly different orthogonal grid.
- Outer districts (`market`, `residential`, `fringe`) follow the perimeter curve via the existing `TownDistrict.cells_override` mechanism; `civic_core` stays rectangular.
- A measurable distinctness gate in the validator fails CI when probe-seed towns collapse toward a single silhouette.
- Every existing spec invariant survives: closed single-loop perimeter, gate on perimeter, spine↔gate↔core connectivity, shrine as sole dominant landmark, ritual axis inside `civic_core`, core-outranks-fringe hierarchy, per-seed determinism, Python⇄Java parity.

**Non-Goals:**
- Slanted or curved street grids (B2 in the KB note). Streets stay orthogonal; only their positions jitter. High-risk low-yield, deferred again.
- Asymmetric west/east district rosters (B3). Mirror symmetry preserved to keep the civic-core skyline-relief rule intact.
- Worldgen placement or `/myvillage` command surface changes. Towns still generate on demand from `[seed]`.
- Per-parcel rotation / setback jitter (B4). A separate texture-level change; orthogonal to this one.

## Decisions

### D1. The vocabulary is a composable family + modifier set, not a flat list
A flat `variants[seed % N]` list scales linearly and forces a choice between "few well-designed shapes" and "many near-duplicates." Instead the planner selects a **base family** from `{square, circle, oval, D-shape, octagon, trapezoid}` and zero-or-more **modifiers** from `{barbican, bastion, none}` independently, both via the hash module. This yields `6 × 3 = 18` visual combinations from 6 base predicates + 2 modifier predicates, each of which is independently testable and parity-trackable.
- *Alternatives:* (a) flat list of 8+ hand-tuned shapes — rejected, near-duplicates and high per-shape maintenance; (b) free-form noise-bounded polygon — rejected, parity-hostile and hard to seat gates cleanly.
- Each base family is a pure function `(site, family_id) -> bitten_cells_set`; each modifier is `(site, base_bitten, modifier_id) -> modified_bitten_set`. Both compose by set algebra.

### D2. Curves use integer arithmetic; midpoint circle and integer cross-multiply ellipse
The circle predicate is `(x−cx)² + (z−cz)² ≤ r²` (pure integer). The ellipse predicate avoids float division by rewriting `(x−cx)²/rx² + (z−cz)²/rz² ≤ 1` as the integer cross-multiply `rz²·(x−cx)² + rx²·(z−cz)² ≤ (rx·rz)²`. Both evaluate identically in Python and Java with no float drift, so parity holds without tolerance bands.
- The gate-facing segment of every curvy shape is a chord clipped to a straight run (width ≥ the gate footprint) so `gate_cells` lands cleanly on the perimeter regardless of family.
- *Alternative:* Bezier / superellipse curves — rejected, float-dependent and parity-fragile.

### D3. A single `town_hash` module routes all seed-derived parameters
A new `tools/buildgen/town_hash.py` and its Java mirror `com.example.myvillage.town.TownHash` expose `hash64(seed, tag) -> long` (splitmix64-style finalizer over a tagged hash of the seed bytes) and small helpers (`range64(seed, tag, lo, hi) -> int`, `pick(seed, tag, options) -> T`). Every seed-derived geometry parameter — vocabulary selection, grid jitter, modifier presence — routes through `town_hash`. This centralizes the parity contract: there is exactly one bit-identical hash to mirror, and `parity_constants()` exports every parameter it produces.
- *Alternative:* per-parameter ad-hoc hashing — rejected, drift-prone and untestable as a unit.

### D4. Grid jitter preserves orthogonality and rectangularity; ranges are bounded
The grid stays a strict orthogonal lattice; only lattice parameters move. Bounded ranges: `cx ∈ [w//2 − 4, w//2 + 4]`, each lane z-band shifted by `±2`, district widths jittered by `±3`. These bounds keep every district rectangular, keep the spine intersecting the south gate, and keep the civic_core clear of the precinct wall. All bounds are constants exported through `parity_constants()` so the validator can re-derive them.
- *Alternative:* unbounded jitter — rejected, can orphan the gate or fragment districts; the spec invariant "spine connects a gate to the core" must hold for every seed.

### D5. Clip-to-shape extends `cells_override` to all outer districts; `civic_core` stays rectangular
0.12.0 already made `TownDistrict.cells` authoritative via `cells_override`; this change widens the path so `market`/`residential`/`fringe` emit `rect ∩ perimeter_interior` cell sets whenever the vocabulary entry is not plain `square`. `civic_core` is exempt: the civic-precinct derivation (`_validate_precinct` derives wall/colonnade/spirit-way from `core.bounds`) stays coupled to the AABB, so the precinct contract is undisturbed. The exemption is a documented constraint, not a defect.
- Parcel subdivision already operates on `district.cells` (cell-set-aware from 0.12.0); parcels that would not fit the clipped slab are retried/shifted by the existing path.
- *Alternative:* decouple precinct derivation from `core.bounds` to `core.cells` — explicitly deferred (the 0.12.0 design.md named this as a separate follow-up; it touches the civic-core contract and is out of scope here).

### D6. The distinctness gate measures cell-diff and silhouette descriptor across a fixed probe-seed set
The validator computes, for every pair in a fixed probe-seed set (e.g. the 3 existing seeds `20260618` / `20260719` / `20260820` plus 2 additional), two metrics: (a) the Jaccard distance between the two plans' perimeter cell sets, and (b) a coarse silhouette descriptor (perimeter bounding-box aspect, corner-bit count, curve-cell fraction). The gate fails when any pair falls below either floor. Floors are calibrated to fail the 0.12.0 baseline (which they will, by construction) and pass the post-change output. This mirrors the building-level `silhouette_score` spread gate in `cultivation-variant-differentiation`.
- *Alternative:* a single mean-distinctness number — rejected, hides pairwise collisions; pairwise is what a player actually perceives.

### D7. Staging: ship wall-only (α) first, then clip-to-shape (β) in the same change but behind a sequenced task path
Lever A (vocabulary + grid jitter) is independently shippable and validates before any clip-to-shape code lands. The task list sequences A → validator distinctness → B (clip) so the change can land incrementally and bisect cleanly if a parity drift appears. Both stages are in scope for this change; β is not a follow-up.

### D8. Parity expansion is the canary, not a blocker
`parity_constants()` grows to export: vocabulary family id, modifier set, every grid-parameter value, and per-shape perimeter/interior cell counts. The Java `TownGenerator` hardcodes mirror. Any drift fails `validate_runtime_town_plan.py` immediately. The `town_hash` module is the only parity-critical new primitive; everything else is parameter plumbing.

## Risks / Trade-offs

- **Circle/ellipse parity drift** → Integer-only predicates (D2) eliminate float drift; add explicit Python⇄Java cross-check tests for `(rx, rz)` sweep at multiple radii in the parity validator.
- **Curvy shapes fragment parcels at the boundary** → Cell-set-aware subdivision already retries/shifts; bound the curve's max per-cell bite depth so no parcel is thinner than the minimum footprint. Validator's `parcel_outside_district` check catches regressions.
- **Grid jitter orphans the gate or core** → D4 bounds are tight (±4 / ±2 / ±3); validator's `spine_connects_gate_to_core` and `core_contains_shrine` checks are unchanged and still enforce the invariant.
- **Clip-to-shape breaks the civic-precinct contract** → D5 exempts `civic_core` from clipping; precinct derivation stays on `core.bounds`. Documented constraint.
- **Distinctness gate miscalibrated** → D6 floors are calibrated against the 0.12.0 baseline (must fail) and the post-change probe set (must pass); record both in `reports/` so future regressions are visible.
- **Large change surface** → D7 stages A → validator → B so each stage is independently validatable; each task is independently revertable.
- **Preview visual regressions on curvy shapes** → Regenerate preview across all probe seeds per `09_validation_checklist.md` and eyeball the wall line, district edges, and parcel fit before archiving.

## Migration Plan

No runtime migration: towns generate on demand from `/myvillage town [seed]`, and existing saved worlds keep their already-generated towns. Rollback is a plain revert of the Python + Java + parity + validator changes. The `parity_constants()` set expansion is a breaking change to the internal parity contract but not to any persisted data format — no world chunk, NBT, or structure-file migration.

## Open Questions

- Initial vocabulary size — ship all 6 base families at once, or stage 3 (square + circle + octagon) first and add the rest in a fast-follow within the same change? Lean toward shipping all 6 since D1's composable design keeps per-shape cost low.
- Probe-seed set size — 3 (existing) or 5? Larger set tightens the distinctness gate but lengthens CI. Lean 5.
- Should the circle's thematic weight (天圆 = high-tier 修仙 narrative) be encoded as a non-uniform vocabulary prior, or kept uniform-random across seeds? Lean uniform for now; narrative weighting is a gameplay-design call outside this change.
- Distinctness gate floor values — calibrate empirically against probe output during the first implementation pass; record the chosen floors in `reports/town_distinctness_calibration.json`.
