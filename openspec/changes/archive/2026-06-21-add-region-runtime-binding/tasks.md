## 1. Golden fixtures and parity harness (Python side)

- [x] 1.1 Extend `tools/buildgen/region_topology.py` (or add `tools/export_region_fixtures.py`) to dump canonical graph JSON + computed spawn block for a fixed list of seeds: minimum count (5), maximum count (7), a tier-tie case, a walled-region-低 tier case, and the shipped example seed 20260620.
- [x] 1.2 Commit the exported fixtures under `tools/region_runtime_fixtures/` (or `src/test/resources/`) in a stable format the Java parity test can consume byte-for-byte.
- [x] 1.3 Document the fixture format (graph JSON shape + spawn block fields: `region_id`, `world_block`, `seed`) in `docs/ai-kb/13_region_topology.md` (Runtime binding section, added in task group 7).

## 2. Java port of the region RNG and generator

- [x] 2.1 Create a new Java package `com.example.myvillage.region.runtime` (sibling to the existing `com.example.myvillage.sect` and town packages) with the runtime classes this change introduces.
- [x] 2.2 Port the RNG primitives: `_MASK64`, `_FNV_OFFSET`, `_FNV_PRIME`, `_SPLITMIX_*`, `_fnv1a_tagged`, `_splitmix64`, `hash64(seed, tag)` — bit-identical to `tools/buildgen/region_topology.py`.
- [x] 2.3 Port `RegionRng` (numbered-tag stream, `range`/`pick`/`chance`), mirroring the Python method order so the tag counter advances identically.
- [x] 2.4 Port the data model: `RegionProfile`, `Ruleset`, `GenRegion`, `GenEdge`, `RegionGraph` (Java records or immutable classes), with JSON loading from the shipped `data/myvillage/worldgen/region_topology.json` + `region_profile/*.json` via NeoForge's `ResourceManager`.
- [x] 2.5 Port `validate_inputs`, `_select_regions`, `_embed`, `_geometric_edges`, `_spanning_tree`, the tier assignment loop, the edge-typing rule, and the walled-region rule — preserving the exact control flow and RNG tag strings of the Python module.
- [x] 2.6 Port `generate(seed, ruleset, catalog)` and verify its serialized output is byte-identical to the Python output for every fixture seed in task 1.2 (Java parity test under `src/test/java/`).

## 3. Coordinate transform and runtime query service

- [x] 3.1 Define the placement transform: a `REGION_SCALE` constant derived from `RADIUS_WORLD = 4000` and `RADIUS_GRAPH_OUTER = 1.4` (documented in the constant's javadoc and `docs/ai-kb/13_region_topology.md`), with `worldFromGraph(graphPos)` and `graphFromWorld(worldPos)` helpers. No rotation.
- [x] 3.2 Implement `region_at(x, z)` as nearest-region-center lookup via `graphFromWorld` over the cached graph. Return a `Optional<RegionId>` (or sentinel) for points outside the bounded area; document the nearest-center approximation.
- [x] 3.3 Implement `RegionRuntimeService` (server-side): on world-load event, read the world seed, load ruleset + catalog via `ResourceManager`, run the Java `generate(seed, ...)`, cache the `RegionGraph`. Provide `region_at`, and (in later task groups) the spawn-binding and rung-ladder methods.

## 4. Spawn binding

- [x] 4.1 Implement the spawn-region selector: filter `role != "walled"` AND `admitted_subjects` non-empty; sort by `(assigned_tier ASC, distance_from_anchor DESC, qi_midpoint ASC, id ASC)`; take the first.
- [x] 4.2 Implement the safe-surface search: spiral outward from the region center's world block, cap at 256 blocks, accept the first standable block (non-liquid, non-air-below, head-clear). Fall back to vanilla `setworldspawn` semantics if none found.
- [x] 4.3 Wire the spawn binding into `RegionRuntimeService`'s world-load handler: detect whether the world already has a non-default spawn (admin-set); if so, do NOT override. Otherwise call `ServerLevel.setSpawnPos(x, y, z)` exactly once and persist via vanilla.
- [x] 4.4 Verify determinism: recomputing spawn for the same seed yields the same block, across JVM launches and across Python/Java (compare to the fixture's spawn block from task 1.1).

## 5. Rung-ladder API

- [x] 5.1 Implement the rung-ladder derivation: `sorted({r.tier for r in graph.regions if r.role != "walled"})` (ascending distinct tiers), cached per world.
- [x] 5.2 Implement `current_region(player)` and `current_rung(player)` using the player's current block position and `region_at`.
- [x] 5.3 Implement `next_rung_regions(player)` returning the **set** of non-walled regions at the next-higher distinct tier above the player's current rung; return the empty set when the player is already at the top rung (anchor).

## 6. Command surface (minimal)

- [x] 6.1 Add `/myvillage spawn info` (query only: prints the computed spawn region, spawn block, and the calling player's current region/rung/next-rung set). Register under the existing `/myvillage` command dispatcher.
- [x] 6.2 Add `/myvillage spawn recompute` (admin/permission-gated: forces a recompute of spawn for the current world and calls `setSpawnPos`). Document the override-existing-spawn semantics.

## 7. Documentation, specs, and version

- [x] 7.1 Extend `docs/ai-kb/13_region_topology.md` with a "Runtime binding" section (coord-transform formula, spawn rule + sort key, rung ladder + set-based next-rung, the no-override policy for existing custom spawns, and the deferred consumers: compass/map/alignment/mobility). Add see-also to the new `region-runtime-binding` spec.
- [x] 7.2 Update `docs/ai-kb/INDEX.md` row 13 to note the runtime-binding extension; keep the existing see-also links to `07_neoforge_worldgen.md` and the `region-profile` / `region-topology` specs, and add a see-also to `region-runtime-binding`.
- [x] 7.3 Update `README.md` command list with `/myvillage spawn info` and `/myvillage spawn recompute` (description + permission note). Update any "region layer is offline-only" wording to reflect the new runtime presence while keeping the offline generator's role clear.
- [x] 7.4 Update `AGENTS.md` with a runtime-binding contract note: 中州 center = world origin; spawn = lowest-tier eligible region (deterministic sort key, no-override for existing custom spawns); rung API exposes **sets** at tier ties (do not collapse to singletons in code — the alignment system resolves ties later); the runtime is passive (no terrain/biome/chunk-gen writes).
- [x] 7.5 Bump the mod version (large feature: `0.x.y` → `0.(x+1).0`) and update the four files together per the `openspec/config.yaml` rule: `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, `CHANGELOG.md`.

## 8. Validation and acceptance

- [x] 8.1 Run the validation checklist from `docs/ai-kb/09_validation_checklist.md` (Acceptance / preview command checklist): generate, validate, preview, build the jar.
- [x] 8.2 Confirm Python↔Java parity holds for every fixture seed (run both, diff the serialized graphs and the spawn blocks).
- [x] 8.3 In a test world (or via a unit/integration test), confirm: spawn region = lowest-tier eligible; spawn block standable; `region_at` agrees with the placed graph; `next_rung_regions` returns a set at a tier tie and the empty set at the anchor.
- [x] 8.4 Confirm no blocks are written by the runtime beyond the one-time `setSpawnPos` metadata (diff a world's chunk NBT before/after the runtime activates, or assert via a unit test that the runtime service writes nothing).
- [x] 8.5 Update the aggregate `out/preview/index.html` if the preview pipeline surfaces anything new for this change; serve over HTTP and report the host URL for review per the AGENTS.md acceptance convention.
