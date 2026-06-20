# Town Shape Vocabulary

Implemented in 0.13.0 by change `town-shape-vocabulary`.

The runtime `/myvillage town [seed]` planner selects a base wall family from
`square`, `circle` (天圆), `oval`, `dshape` (半月), `octagon`, and `trapezoid`.
It independently selects `none`, `barbican`, or `bastion` as a modifier. All
selection and geometry parameters use `town_hash`; Python and Java therefore
reproduce the same integer cell sets without a shared RNG stream.

```text
 square       circle       D-shape      octagon      trapezoid
 ┌────┐        ╭──╮         ╭──╮         /──\          /───\
 │    │       (    )       (    )       /    \        /     \
 └─┄──┘        ╰┄┄╯        └─┄──┘       \    /       └──┄───┘
   gate          gate        gate         \──/           gate
```

Circle and ellipse predicates use integer arithmetic. The ellipse uses
`rz²·dx² + rx²·dz² ≤ (rx·rz)²`; no floating-point tolerance is involved.
Every family supplies a straight south run for the gate.

The internal grid remains orthogonal but is seed-derived: the spine center is
jittered by ±4 cells, three cross-lanes by bounded offsets, and each paired
outer district receives an independent width offset of ±3 cells. Validators
require the spine centerline to cross the south gate and reach the civic core.

```text
 wall curve       clipped outer tissue       fixed civic core
   ╭──────╮          ╭······╮                  ┌────────┐
  / market\        / parcels \────spine───────│ shrine │
 │  ──┼──  │      │ follow   │                │ plaza  │
  \ fringe/        \ curve  /                 └────────┘
   ╰──────╯          ╰──────╯
```

For non-square shapes, outer district cells are the intersection of their AABB
and the perimeter interior. Parcel fitting uses that authoritative cell set and
shifts inward past curve fragments that cannot hold a minimum footprint.
`civic_core` remains its full AABB because precinct framing derives from
`core.bounds`.

Five fixed probe seeds are checked pairwise with perimeter Jaccard distance and
a silhouette descriptor. Calibration lives in
`reports/town_distinctness_calibration.json`; Python/Java geometry parity lives
in `src/test/resources/town_geometry_parity.json`.

See also: [Town Shape Irregularity](11_town_shape_irregularity.md),
[town-plan](../../openspec/specs/town-plan/spec.md), and
[town-districts](../../openspec/specs/town-districts/spec.md).
