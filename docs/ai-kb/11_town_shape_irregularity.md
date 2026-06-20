# Town Shape Irregularity (Design Note)

Status: **superseded by the realized 0.13.0 follow-up**
`town-shape-vocabulary`; see
[Town Shape Vocabulary](12_town_shape_vocabulary.md). The original 0.12.0
change `town-shape-irregularity` implemented only Lever A's small
(perimeter variants `square` / `chamfer` / `indent`, inward-only, with a `moat`
negative space) and a safe subset of Lever B (`TownDistrict.cells` authoritative
+ cell-set-aware subdivision; the `chamfer` shape chamfers the fringe) are live.
The follow-up adds the full silhouette vocabulary, seed-derived grid, and
clip-to-shape outer districts while keeping `civic_core` rectangular. This note
is the original exploration; the
normative contract is the `town-plan` / `town-districts` specs.

The runtime cultivation town (`/myvillage town`) used to read as a flat square
box: its wall was four straight edges, every district was an axis-aligned
rectangle, and the Java realizer mirrored that geometry exactly. This was
visually monotone and unlike real Chinese walled cities, which commonly pair an
orthogonal street grid with an irregular city wall. The spec never mandated a
rectangle — it only requires a closed perimeter, gates on the perimeter, and a
partition into districts — so the squareness was an implementation accident.
This note explored making the shape intentionally irregular while preserving
every existing invariant. It covers two independent, composable levers:
**A** deform the perimeter wall, **B** de-grid the district boundaries. Both
keep the internal street network orthogonal.

See-also specs: [town-plan](../../openspec/specs/town-plan/spec.md),
[town-districts](../../openspec/specs/town-districts/spec.md).

## Why the town is square today

Four layered sources, each must be addressed for any shape change:

1. **`TownSite`** is a `width × depth` rectangle (default ~160×160) — `town.py:115`.
2. **`_boundary(site)`** draws four straight edges as the wall perimeter — `town.py:305`.
3. **`_layout`** emits every district as an axis-aligned rectangle
   `b(kind, x0, z0, x1, z1)` — `town.py:407`.
4. **Java `TownGenerator.plan`** mirrors every bound above deterministically
   (no shared RNG) — `TownGenerator.java:182`.

### Hard coupling: Python ⇄ Java parity

`parity_constants()` (`town.py:562`) exports the planner's geometry constants
and the runtime validator compares them against Java-hardcoded values to catch
drift. **Any shape change requires moving Python, the Java mirror, and this
constant set together**, or parity check fails. Shape geometry MUST stay a pure
deterministic function of the site (or seed-derived with an algorithm both ends
reproduce identically).

### What the spec actually requires

`town-plan` does NOT mandate a rectangular perimeter. It requires: a closed
perimeter, gates on the perimeter, ritual axis inside `civic_core`, shrine as
sole dominant landmark, and the planner fits the site. So irregularity is
spec-compatible as long as these invariants hold.

## Lever A — Deform the perimeter wall (recommended first step)

Keep district rectangles and the orthogonal street grid untouched. Only the
wall stops being a square. The gap between the new wall and the rectangular
district edges becomes moat / green / negative space. This is the classic
"orthogonal street net + irregular city wall" form of Ming–Qing cities.

### Shape options (deterministic, derived from site size + seed)

```
A1 chamfer (octagon)        A2 bastion (mid-edge bulge)   A3 moon-fort (indent)
 ┌──────┐                    ┌─┐ ┌─┐                     ┌──────┐
 │      │                    │ │ │ │                     │      │
 │      │                     \|/  |                      │      |
 │      │                    ──┴───┴──                    │  ┌─┐ │
 └──────┘                    ┌───────┐                     │  │ │ │
  cut 4 corners              │       │                    ──┴─┴─┴─┘
                             └───────┘                    indent one edge
                             bulge each side              (forms a barbican)
```

Composable: A1 + A2 on different edges, per-seed variant selection.

### Change points

| Where | Change |
|-------|--------|
| `town.py:_boundary` | Replace 4-edge rect with a polygonal/partial-cell boundary builder parameterized by site + seed-derived variant. |
| `town.py:generate_town_plan` | `gate_cells` must track the new south edge; constrain deformation so the south-gate segment stays straight and on the wall. |
| `town.py:validate_town_plan` | The `perimeter == _boundary(site)` assertion **auto-follows** (calls same fn). Gate-on-perimeter check auto-follows. Just re-check no plan cell exits the site. |
| `town.py:parity_constants` | Add perimeter descriptor (variant id + chamfer/bulge params) to the exported set. |
| `TownGenerator.java` | Mirror the new `_boundary` derivation and the gate placement; update hardcoded parity values. |
| Negative space | Decide the wall↔district gap: leave open (moat/green) or extend `fringe` cells inward. Leaving open is lowest-risk. |

### Invariants preserved automatically

Closed perimeter, gate-on-perimeter, spine↔gate connectivity, ritual axis inside
core, shrine as sole top tier. None of these depend on wall squareness.

## Lever B — De-grid the district boundaries

Break the "chessboard" read by making inter-district boundaries stepped or
kinked, and/or by dropping the strict west↔east mirror symmetry.

### Shape options

```
B1 stepped boundary        B2 kinked boundary          B3 asymmetric rosters
 ┌────┬─────┐              ┌────┬─────┐                ┌────┬─────┐
 │mkt │ res │               \   │ res │                │mkt │diff │
 │    ├─────┘                \  ├─────┘                │   │rost │
 │    │      step the        \ │      kink the        ├────┤er   │
 ├────┘      mkt/res line     └┘      boundary         │res │     │
                                  west≠east no mirror  └────┴─────┘
```

### Change points — bigger than A, because districts feed parcel subdivision

| Where | Change |
|-------|--------|
| `TownDistrict` schema | Today `cells = _rect(*bounds)` (`town.py:149`). Either add `extra_cells`/`excluded_cells` masks, or store an explicit cell set and keep `bounds` as an AABB for sorting/spatial queries. |
| `town.py:_layout` | District construction `b(...)` must emit the chosen non-rect cell set deterministically. |
| `town.py:_subdivide_district` | **Strong dependency**: line 829 unpacks `x0,z0,x1,z1 = district.bounds` and slices the rectangle. Must switch to operating on `district.cells` (slab over a cell set, not over an AABB). All `_edge_touches_street` / row/col slicing callsites follow. |
| `validate_town_plan` | `district_overlap` / `parcel_outside_district` already key off `district.cells` — **auto-correct** once cells reflect the real shape. |
| `TownGenerator.java` | `District` record + subdivision mirror must support non-rect cells. This is the most invasive Java change. |
| `parity_constants` | District cell counts / boundary descriptors added. |

### Why B is more expensive than A

Parcel subdivision, frontage alignment, and alley emission all currently assume
a rectangular district. De-gridding forces subdivision to reason over an
arbitrary cell set. A avoids this entirely by leaving districts rectangular.

## Impact summary

| Lever | Python | Java | Spec | Validator | Preview | Risk |
|------:|:------:|:----:|:----:|:---------:|:-------:|:----:|
| A wall | `_boundary` + parity | mirror perimeter + gate | none (already allows) | auto-follows | regen | low |
| B districts | `_layout` + `_subdivide_district` + `TownDistrict` | mirror non-rect districts | consider a note | auto-follows | regen | medium |

## Recommended path

1. **Start with A alone.** Lowest risk, immediate visual payoff, spec-clean.
   Ship one or two deterministic wall variants behind per-seed selection.
2. **Validate** with the full `09_validation_checklist` acceptance chain
   (Python parity, Java mirror, preview regen, README unchanged because no
   command surface changes).
3. **Only if A feels insufficient**, add B. B's cost lives mostly in
   `_subdivide_district` and the Java `District` mirror; budget for a parity
   constant expansion and a dedicated preview review.

## Acceptance signals (when implemented)

- `tools/buildgen/town.py` parity check vs `TownGenerator.java` passes.
- `validate_town_plan` / `validate_realized_town` green on multiple seeds.
- Town no longer reads as a square box in `out/preview/`, while the ritual
  axis, shrine, spine↔gate link, and district hierarchy remain intact.
- No plan cell leaves the site bounds; wall stays a single closed loop.

## Open questions

- Should wall deformation be pure-site-derived (every town same shape) or
  seed-derived (per-town variant)? Seed-derived is more varied but expands the
  Java mirror surface.
- For B, is asymmetry (west ≠ east roster) wanted, or only boundary kinks that
  preserve mirror symmetry? Asymmetry is cheaper visually-per-dollar but
  complicates the skyline-relief rule in the civic core.
