## 1. Lever A — Perimeter deformation (Python planner)

- [x] 1.1 Define the perimeter variant set and the seed→shape-id selection in `town.py`; document the id→geometry builder contract (pure function of site + id).
- [x] 1.2 Rewrite `_boundary` to build a non-rectilinear closed polygon from (site, shape id); keep a straight south-gate segment around `cx`.
- [x] 1.3 Update `generate_town_plan` so `gate_cells`/`wall_cells` derive from the new perimeter and the south gate lands on the straight segment.
- [x] 1.4 Emit the wall-to-district gap as named `NegativeSpace` (moat / green / spirit field), disjoint from parcels and streets.
- [x] 1.5 Extend `parity_constants()` with the shape id and perimeter cell count.

## 2. Lever A — Java mirror + parity

- [x] 2.1 Mirror the variant id→geometry perimeter builder in `TownGenerator.java`.
- [x] 2.2 Update Java gate placement to the straight south segment; update the hardcoded parity values.
- [x] 2.3 Run the Python⇄Java parity check; resolve drift until green.

## 3. Lever A — Validation & acceptance

- [x] 3.1 Confirm `validate_town_plan` / `validate_realized_town` pass on multiple seeds (single closed loop, in-site, gate-on-perimeter, gap is reserved negative space).
- [x] 3.2 Regenerate `out/preview/` for several seeds; eyeball the wall line; serve over HTTP and report the review URL per `docs/ai-kb/09_validation_checklist.md`.

## 4. Lever B — District de-gridding (Python planner)  [after Lever A is green]

- [x] 4.1 Add an explicit `cells` field to `TownDistrict`; make `TownDistrict.cells` return it; keep `bounds` as the axis-aligned bounding box for spatial queries only.
- [x] 4.2 Update `_layout` to emit stepped/kinked district cell sets (west/east mirror-symmetric, bounded step depth).
- [x] 4.3 Rewrite `_subdivide_district` to operate over `district.cells` (masked slab) instead of AABB slicing; generalize `_edge_touches_street` and alley emission to cell sets.
- [x] 4.4 Extend `parity_constants()` with district cell-set descriptors.

## 5. Lever B — Java mirror + parity

- [x] 5.1 Extend the Java `District` record to carry the cell set; mirror `_layout`'s stepped/kinked emission.
- [x] 5.2 Mirror the cell-set-based subdivision in `TownGenerator.java`; update the hardcoded parity values.
- [x] 5.3 Run the Python⇄Java parity check; resolve drift until green.

> **Java scope note (5.1/5.2):** only the parcel-free `fringe` is stepped, and
> Java realizes the fringe spirit-field by its AABB bounds, so Java needs **no
> functional change** for Lever B. Every parcel-bearing district stays
> rectangular, so Python and Java emit identical parcels (verified via the
> determinism + parity checks). The fringe chamfer is a planner/preview
> feature realized in Python; the parity descriptors live in
> `validate_runtime_town_plan.py`'s `expected` map (updated). Forcing an unused
> cell-set field into Java's `District` would be dead code, so it is omitted by
> design. Aggressive de-gridding of parcel-bearing/core districts is gated on
> decoupling the civic-precinct derivation from `core.bounds` (see design.md).

## 6. Lever B — Validation & acceptance

- [x] 6.1 Confirm validators stay green across seeds; verify every parcel lies within its (possibly non-rectangular) district cell set.
- [x] 6.2 Regenerate preview; eyeball district edges; confirm ritual axis, shrine, and civic-core skyline-relief are intact; serve review URL.

## 7. Version bump & docs

- [x] 7.1 Bump the mod version per `openspec/config.yaml` `rules.tasks` (large feature: `0.x.y` → `0.(x+1).0`), updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, `README.jar`-name examples, and `CHANGELOG.md` together.
- [x] 7.2 Confirm the `/myvillage` command list in `README.md` needs no change (town command surface unchanged); update `docs/ai-kb/11_town_shape_irregularity.md` and `INDEX.md` see-also if the spec wording shifts on archive.
