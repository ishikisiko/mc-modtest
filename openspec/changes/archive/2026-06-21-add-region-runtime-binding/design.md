## Context

The region (洲/域) layer (`add-region-topology`, archived) ships an offline-only per-seed graph generator (`tools/buildgen/region_topology.py`) and its ruleset + catalog under `src/main/resources/data/myvillage/worldgen/`. Its spec mandates that the generator SHALL NOT write world blocks, take over biomes, or run during chunk generation — the layer is data and offline tooling only.

The Python module's docstring anticipated a "future Java runtime placement-director": the RNG primitives (splitmix64 over tagged FNV-1a, mirroring `town_hash.py`) are explicitly parity-ready, and the KB note (`docs/ai-kb/13_region_topology.md`) names "runtime placement of subjects into regions" as a deferred consumer. To date nothing consumes the graph at runtime — in-game the region layer does not exist.

The forcing function is the cultivation-narrative requirement that the player begin in the weakest/qi-poorest peripheral 洲 and journey toward 中州 at the world's spiritual center. That requirement simultaneously needs: (a) a runtime presence of the graph, (b) a fixed world origin at 中州, (c) a deterministic spawn bound to the tier gradient, and (d) a way for downstream systems (compass / map / 正道魔道 alignment / mobility gating) to read the player's current position on the tier ladder.

The tier gradient is already constructed outward from the anchor: 中州 holds the top of `tier_range` (20), tier decreases along the 连 spanning tree, and tier ties can occur (a child may equal its parent's tier when the step draw is 0). The ladder of **distinct assigned tiers** is therefore the natural progression axis, and ties at a tier are naturally a **branch point** (resolvable later by a 道倾向 system).

## Goals / Non-Goals

**Goals:**
- Land the **first runtime exit** of the region layer: the offline graph becomes queryable in-game.
- Place the graph into the world with **中州 center at world origin (0, 0)** and all 洲 within an anchor-centered ~4000-block radius.
- Bind world spawn **deterministically per seed** to the lowest-tier non-walled region with admitted subjects, with a safe-surface search so the spawn block is standable.
- Expose a server-side query API: `region_at(x, z)`, `current_region(player)`, `current_rung(player)`, `next_rung_regions(player)` — where `next_rung_regions` returns a **set** (because tier ties are branch points).
- Establish **bit-identical Java/Python parity** for the region RNG + generator, as the Python module's parity-ready design anticipated.

**Non-Goals (deferred to future changes):**
- The compass / map / landmark form of the "next region" indicator — this change exposes the data, not a UI.
- The 正道 / 魔道 alignment system that resolves tier-tie branch points — this change exposes ties as sets, alignment picks.
- Mobility / fast-travel / flight gating by rung — this change exposes `current_rung`, mobility consumes.
- Runtime placement of subjects (sects / towns) into regions.
- Terrain realization of 隔 edges (山脉 / 海洋 as actual relief).
- Region **extents** (each 洲's own area/volume) — the graph today has only region centers; coord→region uses nearest-center for now, extents are a later generalization.
- Other dimensions (Nether / End) — main world only.

## Decisions

### D1. Java port strategy: golden-fixture parity, not dual-truth

The Python `region_topology.py` remains the **single source of truth** for the algorithm. A new Java mirror reproduces it; correctness is enforced by **golden fixtures**: the Python side exports canonical graphs (and derived spawn points) for a fixed set of seeds, and the Java port's tests assert byte-identical output against those fixtures.

**Alternative considered**: re-implementing in Java as the new source of truth, with Python becoming a preview. **Rejected**: the Python module is already shipped, validated, and offline-useful for preview/audit; rewriting it would discard the existing validator's coverage and break the offline preview pipeline. The Python module's header comment explicitly invites a Java mirror, not a replacement.

**Alternative considered**: a single source in a language-agnostic format (e.g. export the algorithm as data). **Rejected**: the algorithm is non-trivial (constructive spanning-tree + tier assignment + walled rule); expressing it as data would re-invent a language. Code parity via fixtures is the standard approach for cross-language RNG replication.

### D2. Coordinate transform: 中州 at origin, single scale constant

The region graph is on a unit-circle topology (peripherals at radius ~1.0, walled at ~1.4). The world placement is a pure linear transform with no rotation:

```
world_pos = SCALE * graph_pos
SCALE = RADIUS_WORLD / RADIUS_GRAPH_OUTER
      = 4000 / 1.4
      ≈ 2857  (blocks per graph unit)
```

The transform is **pure coordinate math** — it does not write blocks, override biomes, or hook chunk-gen. `region_at(x, z)` is the inverse: `graph_pos = world_pos / SCALE`, then nearest-region-center lookup (Voronoi-style). Region extents are undefined today; nearest-center is the interim rule, documented as a known approximation.

**Rationale for "no rotation"**: a fixed base angle keeps the graph's deterministic `base_angle=0.0` (first peripheral at +x = east) meaningful. Per-seed rotation would add a degree of freedom that breaks the invariant "中州 is at (0,0) and +x is a stable reference direction." If a future narrative needs random orientation, that is a separate change.

**Rationale for single constant over JSON**: the radius-4000 constraint comes from a single gameplay-feel decision; promoting it to JSON implies a tuning workflow that does not yet exist. A named constant in Java (with a docstring) is honest about its status. If multiplayer servers later need per-world tuning, the constant becomes a `gamerule` or `serverconfig` in a follow-up.

### D3. Spawn binding: lowest-tier eligible region, deterministic tie-break, safe-surface search

The spawn region is computed once per world (first load) by:

1. Generate the graph from the world seed (D1 Java port).
2. Filter candidates: `role != "walled"` AND `admitted_subjects` non-empty.
3. Sort by `(assigned_tier ASC, distance_from_anchor DESC, qi_midpoint ASC, id ASC)`. Take the first.
4. Translate the chosen region's graph center to a world block via D2.
5. Run a **safe-surface search**: spiral outward from that block, capped at radius 256, find the first standable block (non-liquid, non-air-below, head-clear). If none found in the cap, fall back to the region center with vanilla `setworldspawn` semantics.
6. Call `ServerLevel.setSpawnPos(x, y, z)` once. Persisted by vanilla; subsequent loads do not recompute.

The sort key is the encoding of "weakest 修为起点": lowest tier (least qi), farthest from anchor (most peripheral), thinnest qi range, deterministic id tiebreak. **Alignment is not in the sort key** because spawn happens before the player has chosen a path.

**Why one-time, not per-respawn**: vanilla world spawn is server-side and persistent. `setSpawnPos` once matches vanilla semantics; per-respawn recomputation would fight vanilla's spawn-chunk model.

**Why safe-surface search radius 256**: the region center is a graph-level abstraction; the actual block can land on water (if the center is near a 特殊海洋 separator) or on a peak (near 特殊山脉). 256 blocks is small relative to the ~5700-block region-to-region spacing, so it cannot accidentally escape the chosen region's neighborhood, but large enough to find standable ground in most biomes.

### D4. Rung ladder: distinct assigned tiers, ascending; next-rung as a SET

The tier rung ladder for a seed is:

```
rungs = sorted({r.tier for r in graph.regions if r.role != "walled"}, ascending)
# e.g. [15, 16, 17, 18, 20]  — 中州 (anchor) is the top rung by construction
```

`current_rung(player)` = the rung containing the player's current region's assigned tier.

`next_rung_regions(player)` = the **set** of non-walled regions whose assigned tier equals the next-higher rung. **Set, not singleton**, because tier ties produce genuine branch points (e.g. 灵岳 + 西漠 both at 18). A future 正道/魔道 alignment system resolves which member of the set is "the" next destination for a given player; until that system exists, downstream UI either shows all members or picks deterministically (id-sorted first) as a placeholder.

`next_rung_regions` for a player already at the top rung (中州) returns the empty set — the journey is complete.

**Walled regions are excluded** by construction: 魔域 does not join the 连 spanning tree, its assigned tier is derived from a neighbor for traceability but it is not on the rung ladder. Reaching 魔域 (via 关隘) is a future alignment-system concern, not this API's.

**Alternative considered**: define "next" as the spanning-tree parent. **Rejected** — the tree is star-shaped in practice (the anchor hubs every peripheral), so tree-parent would jump straight to 中州, skipping the tier gradient. The tier ladder is the cultivation-meaningful axis.

**Alternative considered**: force unique tiers per region by regeneration. **Rejected** — the generator is constructive and re-roll-free; adding a uniqueness constraint could make some rulesets unsatisfiable, and tier ties are narratively useful (they are the branch points the alignment system will resolve).

### D5. API surface: server-side query service, no chunk-gen hooks

The query API is a server-side service (e.g. `RegionRuntimeService`) that:
- Loads the ruleset + catalog from the shipped JSON on world load.
- Runs the Java port of the generator once per world (caches the graph).
- Computes the spawn block once (D3) and calls `setSpawnPos`.
- Answers `region_at(x, z)`, `current_region(player)`, `current_rung(player)`, `next_rung_regions(player)` from the cached graph + D2 inverse transform.

The service is **tickable but passive**: it does not inject into chunk generation, does not replace biome sources, does not write blocks. It only reads the world seed and answers queries. This keeps the region-topology offline contract intact (the offline generator's behavior is unchanged) while adding a runtime reader.

A `/myvillage spawn` (or similar) command may be added for query/debug ("where is spawn, what region am I in, what's my rung"); this is a thin wrapper over the service, not core to the design.

## Risks / Trade-offs

- **[Parity drift]** Python and Java implementations diverge silently, producing different graphs/spawns for the same seed. → Mitigation: golden-fixture tests (D1) covering multiple seeds (including tier-tie cases, walled-region cases, min/max region counts). CI runs both and compares.
- **[Spawn on unstandable block]** Region center translates to a block that is water/lava/cliff/cave. → Mitigation: D3 step 5 safe-surface search; if all else fails, vanilla `setworldspawn` semantics.
- **[Tier-tie at the lowest tier]** Two regions share the lowest assigned tier; which is spawn? → Mitigation: D3 sort key has `distance_from_anchor DESC` then `qi_midpoint ASC` then `id ASC` after tier, so spawn is deterministic without alignment. Documented as "pre-alignment spawn rule."
- **[coord→region ambiguity]** Without region extents, the nearest-center rule can misclassify points near the boundary between two regions, or assign a point "between" regions to one arbitrarily. → Mitigation: accepted as a known approximation for v1; the rung API only needs the answer at playable positions (inside regions), not in separator bands. Documented; extents deferred to the terrain-realization change.
- **[Compass semantics drift]** Once spawn is at the periphery, the vanilla compass (which points to world spawn) no longer points to 中州. → Mitigation: **not a risk, a design opportunity** — explicitly acknowledged in docs; a future "灵脉罗盘" / map / alignment indicator is the natural consumer of `next_rung_regions`. Out of scope here.
- **[Spawn chunks far from 中州]** 中州 (and any future subject placed there) is not in the spawn chunks; the player must travel to discover it. → Mitigation: intended (narrative pacing). Future subject-placement change should provide breadcrumb/路标 guidance, consuming `next_rung_regions`.
- **[Mobility scaling not enforced]** This API exposes `current_rung` but nothing prevents a player from walking/ender-perling to a higher-rung region early. → Mitigation: out of scope — the mobility-gating system (deferred) will enforce soft/hard locks; the region API is a positive (allow-list) source of truth, not a negative (deny-list) enforcer.

## Migration Plan

This is purely additive — no existing behavior changes.

1. Ship the Java port + golden fixtures + spawn binding + query API, all behind the new `region-runtime-binding` capability.
2. On first world load with the new mod version, the spawn binding computes once and calls `setSpawnPos`. **Existing worlds**: the spawn binding detects an already-set custom spawn (admin `/setworldspawn`) and **does not override** — only worlds with the vanilla default spawn (0, 0, 0) get the computed spawn on first load. Documented in `docs/ai-kb/13_region_topology.md` and the README.
3. Rollback: remove the mod or delete the saved spawn metadata — vanilla spawn mechanics resume.

## Open Questions

- **Spawn command surface**: should this change ship a `/myvillage spawn` query command, or defer all command surface to the change that adds the first UI consumer (compass/map)? Lean: ship a minimal `/myvillage spawn info` query to aid testing and multiplayer servers; defer richer commands.
- **Existing-world spawn policy**: confirmed above (don't override a custom spawn), but worth a gameplay pass — should a server admin be able to opt INTO the computed spawn via a flag/command even on an existing world? Likely yes (`/myvillage spawn recompute`), deferred to tasks.
- **Region extents v0**: is the nearest-center rule acceptable for `region_at` until terrain realization lands, or do we need an explicit "between regions / on a separator" sentinel return for boundary points? Lean: accept nearest-center for v0, document the approximation.
