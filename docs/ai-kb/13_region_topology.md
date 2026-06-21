# Region Topology (洲/域 Layer)

Implemented in 0.14.0 by change `add-region-topology`. The design reference is
[`docs/otg_worldgen_philosophy.md`](../otg_worldgen_philosophy.md); this note
records how that maps onto the implemented offline-first region layer. See-also
[`07_neoforge_worldgen.md`](07_neoforge_worldgen.md) and the specs
[`region-profile`](../../openspec/specs/region-profile/spec.md),
[`region-topology`](../../openspec/specs/region-topology/spec.md),
[`validation`](../../openspec/specs/validation/spec.md).

## What this layer is

The macro layer the mod was missing. Above the single self-generating subject
(the sect) there was no 洲/域, no tier gradient, no rule for how regions relate.
This adds it as a **region graph (洲际图)** per world seed, delivered
**offline-first** (OTG §19): data drives generation and validation before any
runtime chunk-gen. No blocks are written, no biome is taken over, and nothing
runs during chunk generation in this change.

## The model

Authored content is a **ruleset**, not pairwise relations. Two JSON bodies under
`src/main/resources/data/myvillage/worldgen/`:

- `region_profile/*.json` — the catalog. Each region (洲) declares `id`,
  `display_name`, a nominal `tier`, `qi` / `danger` ranges, a placement `role`
  (`anchor` | `peripheral` | `walled`), and `admitted_subjects` (today `sect`).
- `region_topology.json` — the rules: `region_count ∈ [5,7]`, `tier_range`,
  `tier_step N = 5`, the separator palette `{特殊山脉, 特殊海洋}`, embedding
  radii, and role rules (single centered anchor; walled = all 隔 + ≤1 关隘).

All tunables live in data. Adding regions or recalibrating is a JSON edit, not a
code branch (see `AGENTS.md`).

## Constructive generation (deterministic, no re-roll)

`tools/buildgen/region_topology.py` is the single shared module — the generator
and the validator import it, so they agree on one source. Per seed (one RNG
stream seeded from the world seed; splitmix64/FNV-1a tagged primitives mirroring
`town_hash.py`, parity-ready for a future Java runtime placement-director):

1. Place the `anchor` (中州) at center; choose count 5–7; embed the rest on
   radial sectors (walled on an outer ring).
2. Assign tier outward along a **连 (passable) spanning tree** over non-walled
   regions — `parent.tier − random(0..N)` clamped to range, anchor's direct
   children step down ≥1 — so every tree edge satisfies the tier-step **by
   construction** and the anchor is the sole highest tier.
3. Type remaining geometric-neighbour edges by rule: tier gap > N or incident
   `walled` → 隔 (separated) with a palette separator; otherwise may be 连.
4. Apply the walled rule: a `walled` region (魔域-style) is sealed except for at
   most one 连 edge marked 关隘; its tier is fixed from that neighbour so the
   pass also respects the tier-step.

Because connectivity and the tier-step are *constructed*, generation never
discards a candidate and regenerates. Same seed → identical graph. A
most-constrained-first placement order means a satisfiable ruleset never
dead-ends; an unsatisfiable ruleset is reported explicitly (`UnsatisfiableRuleset`
raises, it does not loop).

## Output

`tools/generate_region_topology.py [seed]` emits the graph as JSON (regions with
tier/role/position + the typed edge list with separator types). A canonical
example ships at `worldgen/region_topology_example.json` (seed `20260620`),
generated from the same ruleset + catalog the tooling reads — no second source
of truth. `tools/generate_region_topology_preview.py` renders per-seed
`layout.svg` + ASCII `layout.txt` + a `viewer.html` under `out/preview/`, wired
into the aggregate preview index. `tools/validate_region_topology.py` checks the
structural invariants, runs deliberate-break cases, confirms determinism, and
writes the multi-seed survey to `reports/region_topology_validation.json`.

## OTG mapping

This is the top of the OTG stack the mod was missing:

- **WorldConfig** (region rules) → `region_topology.json` — world-scale order:
  how many 洲, the tier gradient, how regions may relate, the separator palette.
- **FromImage** (designed geography) → the **constrained-random** generator: not
  a hand-painted PNG (no replayability) and not pure noise (no designed
  geography), but a graph whose *topology is authored as rules* while its
  *geometry is randomized per seed*. Players form geography memory at the rule
  level (魔域 is always sealed and peripheral) rather than the instance level.
- **Resource Queue / CustomObject / CustomStructure** stay where they are
  (biome/feature/structure layers below); this change owns only the region layer.

The typed **edge list** is the convergence point the next change consumes: a
隔=山脉 edge becomes a RIDGE terrain feature, extending the sect's 反推山形 from
a single peak to a range. Runtime placement of subjects into regions, and all
terrain realization, are explicitly deferred — this layer is offline-only today.

## Runtime binding

Landed in change `add-region-runtime-binding`. The runtime side ports the
offline generator to Java so the per-seed region graph is queryable in-game
(中州 at the world origin, spawn bound to the tier gradient, a rung-ladder API
for downstream consumers). Parity between the Python source of truth and the
Java port is enforced by **golden fixtures** the Python side exports and the
Java port reproduces byte-identically. See-also the
[`region-runtime-binding`](../../openspec/specs/region-runtime-binding/spec.md)
spec, and `07_neoforge_worldgen.md` for the wider worldgen picture.

### Coordinate transform

The region graph (unit-circle topology, anchor at the origin) is placed into the
world by a pure linear transform — it writes no blocks, overrides no biomes, and
hooks no chunk generation:

```
world_pos = SCALE * graph_pos
SCALE     = RADIUS_WORLD / RADIUS_GRAPH_OUTER = 4000 / 1.45 ≈ 2759
```

- 中州 (anchor) center → world block `(0, 0)`; `+x` is the stable reference
  direction (no rotation — the graph's deterministic `base_angle=0.0` is
  preserved).
- `RADIUS_WORLD = 4000` is the anchor-centered world radius that bounds every
  region center.
- `RADIUS_GRAPH_OUTER = 1.45` is the **effective** outermost center radius: the
  walled ring (`1.4`) plus the maximum embed jitter (`±0.05`), so the walled 魔域
  — whose deterministic radius jitter can push it to `1.45` — still fits within
  `4000` blocks. Using the nominal walled ring (`1.4`) alone would let walled
  centers overshoot the `4000`-block bound by ~3.5%; the jitter-inclusive
  divisor closes that gap. (The `region-runtime-binding` spec mandates the
  `4000` bound for every region center.)

`region_at(x, z)` is the inverse: translate the world block to graph units and
return the nearest region center (Voronoi). Region extents are not yet modeled,
so nearest-center is the documented v0 approximation — the rung API only needs
the answer at playable positions (inside regions), not in separator bands.

### Spawn binding

On first world load the runtime binds spawn **once**, deterministically per
seed, to the weakest 修为起点:

1. Generate the graph from the world seed.
2. Filter candidates: `role != "walled"` AND `admitted_subjects` non-empty.
3. Sort by `(assigned_tier ASC, distance_from_anchor DESC, qi_midpoint ASC,
   id ASC)` and take the first.
4. Translate the spawn region's center to a world block (transform above).
5. Safe-surface search: spiral outward up to 256 blocks for the first
   standable block (non-liquid surface, clear feet and head); fall back to the
   heightmap surface at the region center if none is found.
6. Call `setDefaultSpawnPos` exactly once per world (gated by a per-world
   `SavedData` flag so the decision is never revisited).

**No-override policy.** On the first encounter, if the world already has a
non-default spawn (an admin-set `/setworldspawn` at a non-origin column), the
runtime preserves it and marks the binding satisfied — it only computes and
sets spawn for worlds still at the default origin sentinel (`x == 0, z == 0`).
`/myvillage spawn recompute` forces an override on demand.

### Rung ladder

The tier-rung ladder for a seed is the sorted ascending list of **distinct
assigned tiers among non-walled regions**; 中州 is the top rung by construction.
It is a tier ladder, not a spanning-tree-parent ladder: the tree is star-shaped
in practice (the anchor hubs every peripheral), so tree-parent would jump
straight to 中州 and skip the tier gradient. The distinct-tier ladder is the
cultivation-meaningful progression axis.

The runtime exposes, per player:

- `current_region(player)` — the region under the player (nearest-center via the
  inverse transform).
- `current_rung(player)` — the tier of the player's region, if it is non-walled
  and on the ladder (魔域 / outside / unresolved → off-ladder).
- `next_rung_regions(player)` — the **set** of non-walled regions at the
  next-higher distinct tier above the player's current rung; the empty set when
  the player is at the top rung (中州).

**Sets, not singletons.** Tier ties are genuine branch points (e.g. 灵岳 + 西漠
both at tier 18), so `next_rung_regions` returns every region at the next tier.
A future 正道/魔道 alignment system resolves which member is "the" next
destination for a given player; until then downstream UI shows all members (or
picks the id-smallest deterministically as a placeholder). Code MUST NOT
collapse the set to a singleton — that would silently pick a branch the
alignment layer is supposed to own. 魔域 (walled) never appears in the ladder or
in any `next_rung_regions` result; reaching it (via 关隘) is a future
alignment-system concern.

### Command surface

`/myvillage spawn info` (query) prints the computed spawn region + bound spawn
block and the caller's current region / rung / next-rung set. `/myvillage spawn
recompute` (admin, permission 2) forces a spawn recompute and calls
`setDefaultSpawnPos`, overriding any existing spawn — the documented
admin-override path; the automatic world-load binding otherwise preserves
existing custom spawns.

### Deferred consumers

This change lands the runtime **data layer only**. The downstream systems that
will consume it are separate future changes:

- **Compass / map / "next region" indicator** — consumes `next_rung_regions`;
  the form (灵脉罗盘 item, map overlay, NPC direction, landmark beacon) is TBD.
- **正道 / 魔道 alignment** — resolves tier-tie branch points (picks one member
  of the `next_rung_regions` set for a given player).
- **Mobility / fast-travel / flight gating** — consumes `current_rung` to unlock
  run / 缩地 / 御剑 / cross-continent teleport by tier; the enforcer, not just
  the data, ships later.
- **Runtime subject placement** — siting sects / towns into their `admitted`
  regions; **terrain realization of 隔 edges** (山脉 / 海洋 ranges as relief);
  and **region extents** (per-洲 area/volume, replacing the nearest-center v0
  approximation). All deferred.

The runtime is **passive**: it reads the world seed, computes and caches the
graph, answers queries, and calls `setDefaultSpawnPos` exactly once per world.
It writes no terrain, overrides no biome, and hooks no chunk-gen — the offline
`region-topology` generator's contract is unchanged.

### Fixture format

`tools/buildgen/tests/generate_region_runtime_fixtures.py` exports one fixture
per fixed seed case under `src/test/resources/region_runtime_fixtures/`. Each
`seed_<n>.json` is canonical JSON (`indent=2`, `sort_keys=True`, UTF-8) with
this shape:

- `case` — the structural category the seed exercises: `min_count` |
  `max_count` | `tier_tie` | `walled_low` | `shipped`.
- `seed` — the world seed (also echoed in the filename).
- `placement` — the pure-math transform constants `{radius_world=4000,
  radius_graph_outer=1.45, scale}`. The scale is `radius_world /
  radius_graph_outer` (the effective outer radius — walled ring `1.4` plus max
  embed jitter `0.05`); world = `scale * graph`, block = rounded int. No
  rotation.
- `spawn` — the spawn binding the runtime derives from the graph:
  - `region_id` — the eligible region with the lowest assigned tier (eligible =
    `role != "walled"` AND `admitted_subjects` non-empty), tie-broken by
    `(assigned_tier ASC, distance_from_anchor DESC, qi_midpoint ASC, id ASC)`.
  - `graph_center` — `[x, z]` of the spawn region's center in graph units (the
    serialized `position` pair).
  - `world_block` — `[x, z]` of the spawn region's center translated by the
    placement transform. The runtime's safe-surface search (spiral for a
    standable block, world-dependent) resolves the `y` later and is **not**
    part of the fixture — it is not deterministic across world terrain.
- `graph` — the canonical `graph.to_dict()` payload (regions with
  tier/role/position/nominal_tier + the typed edge list), exactly what
  `tools/generate_region_topology.py` emits for the same seed.

`index.json` lists the cases informational; the Java parity test enumerates
`seed_*.json` directly so adding a case never requires touching the test.
Regenerate after changing `region_topology.py` or the placement transform:

```
python3 tools/buildgen/tests/generate_region_runtime_fixtures.py
```
