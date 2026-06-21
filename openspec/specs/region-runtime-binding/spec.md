## Purpose

Bind the offline-generated region graph (`region-topology` capability) into the running game as a passive runtime layer: a Java port reproduces the per-seed graph bit-identically, places it into the world by a pure coordinate transform anchored at the world origin, binds world spawn deterministically to the lowest-tier eligible region, and exposes server-side region/rung query APIs — without any terrain, biome, or chunk-gen writes.

## Requirements

### Requirement: A Java runtime port reproduces the offline region graph bit-identically per seed

The region-runtime-binding capability SHALL provide a Java port of the offline region-topology generator (`tools/buildgen/region_topology.py`) that, for any world seed, produces a region graph byte-identical to the offline generator's JSON output (same regions, same assigned tiers, same typed edges, same separators, same positions). The Java port SHALL read the same shipped ruleset (`region_topology.json`) and catalog (`region_profile/*.json`) as the offline generator; no second source of truth SHALL exist. Parity SHALL be enforced by golden-fixture tests covering multiple seeds (including tier-tie cases, walled-region cases, and the min/max region counts).

#### Scenario: Java port matches the offline generator for a fixed seed

- **WHEN** the Java port runs for a world seed that has a golden fixture exported by the offline generator
- **THEN** the Java port's serialized graph SHALL be byte-identical to the golden fixture.

#### Scenario: Parity holds across the ruleset's region-count range

- **WHEN** the Java port runs for seeds chosen to produce the minimum, maximum, and intermediate region counts
- **THEN** each produced graph SHALL match its golden fixture exactly.

#### Scenario: Parity holds in the presence of tier ties

- **WHEN** a seed produces a graph where two non-walled regions share the same assigned tier
- **THEN** the Java port's graph SHALL match the offline generator's graph exactly, including the tie.

### Requirement: The region graph is placed into the world with the anchor at the origin

The runtime SHALL place the per-seed region graph into the world by a pure linear coordinate transform: the anchor (中州) center SHALL be at world block `(0, 0)` (x=0, z=0), and the transform scale SHALL be chosen so that the outermost region center fits within an anchor-centered radius of 4000 blocks. The transform SHALL NOT rotate the graph (the graph's deterministic `base_angle` is preserved as a stable reference direction). The placement SHALL be coordinate math only — it SHALL NOT write world blocks, override biomes, or hook chunk generation.

#### Scenario: The anchor center is at the world origin

- **WHEN** the region graph is placed into the world for any seed
- **THEN** the anchor region's center SHALL map to world block coordinate `(0, 0)`.

#### Scenario: All region centers fit within the world radius

- **WHEN** the region graph is placed into the world for any seed
- **THEN** every region center SHALL lie within an anchor-centered circle of radius 4000 blocks.

#### Scenario: Placement does not modify the world

- **WHEN** the runtime places the region graph into the world
- **THEN** no world blocks SHALL be written, no biome assignments SHALL be overridden, and no chunk-generation hook SHALL be invoked.

### Requirement: World spawn is bound deterministically to the lowest-tier eligible region

The runtime SHALL, on first world load, compute a single world spawn point deterministically from the world seed and call the vanilla `setSpawnPos` once. The spawn region SHALL be the eligible region with the lowest assigned tier, where eligible means `role != "walled"` AND `admitted_subjects` is non-empty. Ties at the lowest tier SHALL be broken deterministically by the sort key `(assigned_tier ASC, distance_from_anchor DESC, qi_midpoint ASC, id ASC)`. The spawn block SHALL be a standable block found by a safe-surface search (spiral outward from the region center's world block, capped at a radius of 256 blocks, accepting the first non-liquid block with a clear head and a standable surface). If no standable block is found within the search cap, the runtime SHALL fall back to the region center with vanilla `setworldspawn` semantics.

#### Scenario: Spawn is the lowest-tier eligible region

- **WHEN** the runtime computes spawn for a world seed
- **THEN** the spawn region SHALL be the eligible region with the lowest assigned tier in the generated graph.

#### Scenario: Walled regions are never spawn candidates

- **WHEN** the runtime computes spawn for a world seed whose graph contains a walled region
- **THEN** the walled region SHALL NOT be selected as the spawn region, even if it has the lowest assigned tier.

#### Scenario: Spawn ties are broken deterministically

- **WHEN** two eligible regions share the lowest assigned tier for a given seed
- **THEN** the spawn region SHALL be the one that sorts first by `(distance_from_anchor DESC, qi_midpoint ASC, id ASC)` after tier, producing the same choice on every recomputation for that seed.

#### Scenario: Spawn lands on a standable block

- **WHEN** the spawn region's center translates to a world block that is non-standable (liquid, no surface, or obstructed headroom)
- **THEN** the runtime SHALL search outward in a spiral up to 256 blocks and select the first standable block found.

#### Scenario: Spawn is computed once per world

- **WHEN** the runtime binds spawn on first world load
- **THEN** it SHALL call `setSpawnPos` exactly once for that world, and subsequent loads SHALL NOT recompute spawn unless an explicit recompute is invoked.

#### Scenario: Existing custom spawns are preserved

- **WHEN** the runtime loads a world that already has a non-default spawn (set by an admin via `/setworldspawn` or otherwise)
- **THEN** the runtime SHALL NOT override the existing spawn on first load.

### Requirement: A coord-to-region query API identifies the region at any world block

The runtime SHALL expose a server-side query `region_at(x, z)` that returns the region whose center is nearest (in world-block distance) to the queried coordinate, computed via the inverse of the placement transform. The query SHALL return a sentinel (no region) for points outside the region-graph's bounded area. Until region extents are introduced by a future change, the nearest-center rule SHALL be documented as a known approximation.

#### Scenario: A point inside a region resolves to that region

- **WHEN** `region_at(x, z)` is called with coordinates close to a region's placed center
- **THEN** the query SHALL return that region.

#### Scenario: A point far outside the region area resolves to no region

- **WHEN** `region_at(x, z)` is called with coordinates well outside the 4000-block anchor-centered radius
- **THEN** the query SHALL return a no-region sentinel.

### Requirement: A rung-ladder API exposes the player's tier-rung progression state

The runtime SHALL derive a tier-rung ladder per seed as the sorted ascending list of distinct assigned tiers among non-walled regions; the anchor (中州) SHALL be the top rung by construction. The runtime SHALL expose `current_region(player)`, `current_rung(player)`, and `next_rung_regions(player)` as server-side queries. `next_rung_regions` SHALL return the **set** of non-walled regions whose assigned tier equals the next-higher rung above the player's current rung (a set, not a singleton, because tier ties are branch points). For a player already at the top rung (中州), `next_rung_regions` SHALL return the empty set.

#### Scenario: The rung ladder is the distinct ascending assigned tiers

- **WHEN** the runtime derives the rung ladder for a seed
- **THEN** the ladder SHALL equal the sorted ascending list of distinct assigned tiers among non-walled regions, with the anchor's tier at the top.

#### Scenario: Current rung reflects the player's region

- **WHEN** a player is standing inside a region with assigned tier T
- **THEN** `current_rung(player)` SHALL return the rung corresponding to T.

#### Scenario: Next-rung returns a set at a tier tie

- **WHEN** a player is at a rung below a rung that contains two or more non-walled regions sharing the same assigned tier
- **THEN** `next_rung_regions(player)` SHALL return the set of all such regions, not a singleton.

#### Scenario: Next-rung is empty at the anchor

- **WHEN** a player is inside the anchor region (中州)
- **THEN** `next_rung_regions(player)` SHALL return the empty set.

#### Scenario: Walled regions are excluded from the rung ladder

- **WHEN** the rung ladder and `next_rung_regions` are computed for any seed
- **THEN** the walled region (魔域) SHALL NOT appear in the ladder nor in any `next_rung_regions` result.

### Requirement: The runtime is passive — no terrain, biome, or chunk-gen writes

The region-runtime-binding capability SHALL be a passive query layer: it SHALL read the world seed, compute and cache the region graph, answer queries, and call `setSpawnPos` exactly once per world. It SHALL NOT write world blocks beyond the spawn-point metadata, SHALL NOT replace or override biome sources, SHALL NOT inject into chunk generation, and SHALL NOT place structures. The offline `region-topology` generator's contract (offline, no world writes) SHALL remain unchanged.

#### Scenario: No blocks are written apart from spawn metadata

- **WHEN** the runtime is active in a world
- **THEN** no world blocks SHALL be modified by the region-runtime-binding capability except for the one-time `setSpawnPos` metadata.

#### Scenario: Chunk generation is unmodified

- **WHEN** chunks generate in a world with the region-runtime-binding capability active
- **THEN** chunk generation SHALL proceed without any injection or override from this capability.

#### Scenario: The offline generator contract is unchanged

- **WHEN** the offline region-topology generator runs after this capability ships
- **THEN** its behavior (no world writes, no biome takeover, no chunk-gen) SHALL remain exactly as specified in the `region-topology` capability.
