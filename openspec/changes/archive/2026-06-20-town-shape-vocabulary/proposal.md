## Why

The runtime cultivation town (`/myvillage town`) reads as visually near-identical across seeds. The 0.12.0 `town-shape-irregularity` change shipped only three tiny inward-only perimeter variants (`square` / `chamfer` / `indent`) confined to the empty 8-cell east/west margin and corner triangles — `chamfer` bites 144 cells and `indent` 260 cells out of a ~25,600-cell site, i.e. **<1% cell-level variation**. Worse, the internal macro grid is **0% seed-varying**: `_layout` (`town.py:481-536`) hardcodes `cx = w//2`, the lane z-bands `(16,18)/(60,62)/(108,110)`, and every `b(...)` district bound, so every seed of the same site size yields a pixel-identical district skeleton. The `town-shape-irregularity` design.md explicitly deferred the high-impact levers (polygonal site / Lever C, civic-precinct decoupling for full Lever B, asymmetric rosters) as follow-ups.

Real Chinese walled cities span a wide silhouette vocabulary — 天圆地方 (round heaven, square earth), 半月 (D-shape), 八角 (octagon), 瓮城 (barbican) — and the `town-plan` spec never mandated a rectangle (it requires only a closed perimeter, gates on the perimeter, and a partition into districts). This change closes the gap by delivering the deferred macro-variation levers and a target distinctness gate, so two towns of different seeds read as visibly different towns rather than RNG-perturbed copies of one square.

## What Changes

- **Expand the perimeter shape vocabulary** from 3 inward-only nibbles to a family of high-silhouette shapes: circle (天圆), oval, D-shape (半月), large octagon (true 45° sides, not the current `K=8` micro-chamfer), and trapezoid; plus optional barbican / bastion modifiers composable onto any base shape. Seed selects from the vocabulary via a pure deterministic function.
- **Make the internal macro grid seed-driven.** Today `cx`, the three lane z-bands, and district widths are site-derived constants (identical for every seed); they become seed-derived parameters within bounded safe ranges, so every seed gets a visibly different district partition while staying orthogonal and rectangular.
- **Complete Lever B (clip-to-shape districts).** The `TownDistrict.cells_override` mechanism shipped in 0.12.0 but is wired only to fringe in `chamfer` mode. Outer districts (`market`, `residential`, `fringe`) become `rect ∩ perimeter_shape` cell sets so parcels follow the curve; `civic_core` stays rectangular by keeping the precinct derivation decoupled to `core.cells` (the follow-up design.md explicitly named).
- **Introduce a unified deterministic hash module** (`town_hash` Python + Java mirror) so every seed-derived geometry parameter is bit-identical between planner and runtime realizer without a shared RNG stream. All variation parameters route through this module.
- **Add a town-level distinctness gate** to validation, analogous to the building-level `silhouette_score` spread gate in `cultivation-variant-differentiation`: across a fixed set of probe seeds, the planner SHALL produce plans whose cell-level diff and silhouette descriptor meet a measurable distinctness floor, so silent regressions to "one shape for every seed" fail CI.
- **BREAKING (internal parity contract, not user-facing):** `parity_constants()` and the Java `TownGenerator` hardcodes expand to cover the shape vocabulary, grid-parameter set, and per-shape cell counts. Old parity values are obsolete. No `/myvillage` command surface change; saved worlds are untouched (towns generate on demand).

## Capabilities

### New Capabilities
<!-- None: this change extends existing macro-plan capabilities rather than introducing a new one. -->

### Modified Capabilities
- `town-plan`: replace the loose "closed perimeter" allowance with an explicit perimeter-shape vocabulary + seed-driven selection; add a requirement that the internal macro grid (spine position, lane bands, district widths) is seed-derived within bounded ranges; add a town-level distinctness gate requirement across probe seeds.
- `town-districts`: relax the implicit "districts are their AABB" assumption — outer districts SHALL be expressible as `rect ∩ perimeter_shape` cell sets (the existing `cells` field becomes authoritative for all outer districts, not just fringe), while `civic_core` stays rectangular to preserve the civic-precinct contract.

## Impact

- **`tools/buildgen/town.py`** — largest Python change: new shape family in `_bitten_cells` / `_boundary`, seed-driven `_layout` parameters routed through the new hash module, `_subdivide_district` operating over arbitrary `cells` sets for all outer districts (already cell-set-aware from 0.12.0; widen the path), `parity_constants` expansion, validator distinctness gate.
- **`tools/buildgen/`** — new `town_hash.py` (deterministic hash primitives); corresponding Java mirror in `com.example.myvillage.town`. Possibly a `town_curves.py` helper for midpoint-circle / ellipse cell predicates shared by planner and validator.
- **`src/main/java/com/example/myvillage/town/TownGenerator.java`** — mirror every new shape predicate, grid-parameter derivation, and clip-to-shape district cell set; update hardcoded parity values; ensure `placePerimeter` and parcel realization honor non-rect outer district cells.
- **`tools/validate_runtime_town_plan.py`** — extend `validate_parity_constants` for the vocabulary + grid params; add the distinctness-gate probe-seed sweep.
- **`docs/ai-kb/11_town_shape_irregularity.md`** — update status note to point at this change as the realized follow-up; add a new KB note (e.g. `12_town_shape_vocabulary.md`) for the vocabulary + grid-jitter design, listed in `INDEX.md` with see-also to `town-plan` / `town-districts` / `11_town_shape_irregularity.md`.
- **`README.md`** — no `/myvillage` command surface change; the town command already takes `[seed]`. Add a short note that seed now selects both a perimeter silhouette and an internal grid, with examples.
- **`CHANGELOG.md`** — new entry under the next version (large increment per `openspec/config.yaml` `rules.tasks`: this moves planner + Java + parity + validator + docs together).
- **No worldgen change.** `/myvillage town` is on-demand; existing saved worlds keep their already-generated towns.
