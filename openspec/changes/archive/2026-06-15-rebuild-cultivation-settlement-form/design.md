## Context

The sect compound layer (`sect_terraced_axial_compound`) already establishes the right primitives — a `CompoundGraph`, an axial spine, two terrace platforms, and stair circulation between them, with axial-hierarchy validation (`compound.py:894-966`, `1268-1293`). The gap is depth and connective tissue: two platforms is not a mountain, there are no galleries or bridges linking volumes, and there is no siting context (cliff/water/cloud) to make the complex feel perched rather than placed. This change evolves the existing layer rather than replacing it.

## Goals / Non-Goals

**Goals**
- A sect reads as a mountain complex: a central axis ascending through several terraced courtyards, halls and pagodas rising level by level, linked by 廊桥/飞桥.
- A siting context (mountain/cliff/water/cloud) that the compound is composed against and that export exposes.
- The cultivation town gains a ritual axis anchored by its shrine.

**Non-Goals**
- Any single building's silhouette/massing (→ `rebuild-cultivation-building-form`).
- Binding terraces to live-world terrain heightmaps (emit relative levels + context; world integration tracked separately).

## Decisions

### D1 — Evolve the existing terraced layout, don't replace it
Keep the `CompoundGraph` parcel machinery, axial spine, and hierarchy validation; generalize the two-platform split into N level platforms with a per-level courtyard. This preserves the existing validators and the gate→…→hall axial contract while adding depth.

### D2 — Galleries and bridges are circulation/structure, not decoration
廊桥/飞桥 are link elements with their own footprint and connectivity, validated for reachability between the volumes they join — not decorative patches. This lets the complex feel built and traversable and gives validators something to check.

### D3 — Siting is declared context, consumed at placement
The compound emits a siting context (e.g. `mountain_slope`, `cliff_back`, `water_front`, `cloud_sea`) and relative terrace levels in meta/export. The generator composes against it (rear hall backs the cliff; a pavilion may cantilever over water), and the live mod binds it to terrain later. This keeps the offline pipeline deterministic and defers world-terrain coupling.

### D4 — Altitude seam with the companion change
This change consumes the companion's building forms (山门 gate, colonnaded 重檐 hall, pagoda, 神庙 shrine) and arranges them. `town_shrine`'s *form* lands in the companion; its *role as the town axis anchor* lands here. The two changes can be reviewed independently but are applied companion-first.

## Risks

- **Depends on companion forms** — if applied before `rebuild-cultivation-building-form`, the composed buildings would still be Western-derived. Mitigate by sequencing (companion first) and noting the dependency in both proposals.
- **Validator churn** — multi-level terraces and bridges expand the compound validation surface. Mitigate by extending the existing axial/terrace validators incrementally.
- **Footprint blow-up** — a multi-level mountain complex is large. Mitigate with a bounded level count and per-level scale params.

## Open Questions

- How many terrace levels by default, and is it seed-varied or fixed by sect scale? *Proposal: 3–4, scaled by a `monumental_scale` param.*
- Does the town ritual axis reuse the sect axis machinery or stay a lighter town-plan feature? *Proposal: lighter town-plan feature; share only the axis/anchor concept.*
