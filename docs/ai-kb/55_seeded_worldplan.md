# Seeded Cultivation Worldplan

0.59.0 adds the opt-in `myvillage:cultivation` world preset. The existing normal world and the legacy region generator in [13](13_region_topology.md) remain available and are not migrated. This note supersedes legacy region-layer deferrals only for the new preset.

The owner-facing rules, creation instructions, CLI, safety model, constraints and acceptance procedure are in [Worldplan acceptance](../worldplan/acceptance.md).

## Ownership and generation

0.59.1-fix1 makes new cultivation worlds use `planned_v3` / `worldplan_v3`. `rules_v3.json` restores the exact V1 biome as the source; `WorldPlanEcotone` consults eight unmodified source samples at a maximum Euclidean radius of 32 blocks. Only authored incompatible group pairs receive a transition biome. A transition never feeds into another sample, so it cannot propagate into the interior or depend on visit order. Outside this local neighbourhood the original biome ID, including `grove`, is retained. The canopy-safe spawn and city audit remain in place.

V2 was too broad: averaging climates changed entire biome interiors and displaced the snowline. The V3 regression measures original snow-type retention in the reproduction area, plus both original endpoints and the maximum transition width. `legacy_v2_hashes.json` and jar-derived V2 sample fixtures preserve already-created V2 saves.

0.59.1 originally made newly created cultivation worlds use `planned_v2` / `worldplan_v2`.
`rules_v2.json` adds a bounded continuous climate field and ordered temperature/moisture biome bands over the unchanged V1 geography. Core regions and outer cells blend climate with the same deterministic spatial ownership; the radial core/outer transition blends both sides. No per-block random biome dithering is used. Climate configuration is frozen into the save.

Existing V1 worlds retain their codec, algorithm, terrain, biomes and city templates. `legacy_v1_hashes.json` pins the released frozen rules and 35 NBT resources; generation fails on drift. The 0.59.0 jar-derived test fixture pins three seeds and 51 sample coordinates. New ecology therefore requires a new world, not a `/reload` or deletion of chunks from an old save.

New V2 rules enable `clear_spawn_canopy`: decoration cannot place a canopy anywhere above the spawn pad, and first-world creation caps `spawnRadius` at the pad radius. Player builds and existing worlds' gamerules remain untouched; older frozen V2 rules without this flag keep their previous behavior.

- `blueprints/worldplan/rules_v1.json`: authored macro rules and terrain profile weights.
- `blueprints/worldplan/yunshan_placeholder_v1.json`: the placement fixture of the four tracked Yunshan buildings. The missing external full D1 city is not recreated by assertion.
- `tools/worldplan/generate_assets.py`: compiles the original Yunshan blueprints, validates paths and services, cuts exactly 35 complete 16×32×16 NBT tiles, and writes frozen rules and the native world preset under `build/generated/worldplan/resources`.
- `worldplan/WorldPlanRules`, `WorldPlan`, `WorldPlanExporter`, `WorldPlanCli`: pure JDK/Gson core. The CLI and runtime use the same planner, not independent approximate implementations.
- `region/runtime/RegionGeometry`: optional frozen shape and site reservations. The old RegionGraph constructor and canonical legacy JSON stay unchanged when geometry is absent.
- `worldplan/runtime/PlannedChunkGenerator`: binds the actual world seed in `createState`; constructs the real settings-backed RandomState rather than using ChunkMap's dummy state for custom generators. It preserves native underground generation, implements the plan's surface in NOISE, delegates surface/carvers/decorations, then emits the one tile owned by the current city chunk.
- `PlannedBiomeSource`: reads holders only after registries bind; retains the native underground cave biomes and chooses surface ecology from the plan.
- `WorldPlanReservationMixin` protects ordinary WorldGenRegion writes; `WorldPlanOreMixin` protects the native OreFeature direct-section path. Decoration scopes are thread-local and closed in try-with-resources. Player edits are not blocked.

## Persistence and scope

Frozen JSON rules and a plan fingerprint are encoded in the generator in `level.dat`; tile resource hashes detect incompatible resource replacement. Do not mutate released V1/V2/V3 algorithms or remove their assets when old saves still use them. Introduce versioned algorithms and assets instead. Full chunks are never rebuilt from the atlas.

The core is a 4000-block radius, with a continuous terrain blend into stateless outer cells. Outer queries inspect only a bounded neighbourhood and return stable coordinate-based region IDs. Negative coordinates use floor. Queries and exports do not force chunks.

RegionRuntime and the finite core world-sim graph use the same planned geometry. GatePlacement avoids the city reservation. Wilderness reads outer ecological profiles but does not invent infinite ledger people. Region tier is not a player realm index, and the existing realm demographic balance has not been rewritten.

The city is emitted in chunk generation rather than as a vanilla StructureStart, so its lookup entry point is `worldplan city`, not a fictional `/locate` structure ID. Macro dashed routes are graph relationships, not generated highways. Maps sample pre-decoration terrain and cannot substitute for real chunk or visual evidence.

## Checks

The [0.59.1-fix1 local-ecotone verification record](../testing/2026-10-17-worldplan-local-ecotones.md) records V3's original-snow retention, seven native biome samples, mapped traversal, spawn/city checks and V1/V2/V3 reopen compatibility.

The [0.59.1 owner-PC and cloud verification record](../testing/2026-10-17-worldplan-ecology.md) records the initial canopy failure, the final scoped checks, the conservative uncovered-dirt limitation and the matching jar hash.

Focused cloud build: `.venv-modal/bin/python -m tools.modal_runner run --preset worldplan-build --ref <commit> --timeout 1800`. This generates/checks owned resources, tests worldplan/region and direct simulation consumers, and builds the jar. The owner-authorized DevHost client can separately verify old V1/V2 worlds, new V3 local ecotones and the audit classifier.

`audit_city` retains `raw_differences` and raw block hashes. It reports `natural_changes` separately only for expected grass becoming plain dirt while the original template's opaque cover is still intact. `differences` and `complete` refer to the remaining changes; `strict_complete` requires raw equality. Missing roads/walls, uncovered dirt edits and altered cover are not ignored. Exact reverse-order hash tests disable random ticks explicitly; gameplay audits keep normal random ticks.

Run `tools/worldplan/ci.py` in the isolated cloud environment configured by the feature workflow, or the separate commands in the acceptance document. Checks cover generated-resource round trips, 510 seed layouts, legacy region parity and sim boundaries, real NOISE columns, city tiling, save/restart, opposite visit order, preservation of a player-placed marker, ordinary-world opt-out and native client images. Results live in `reports/worldplan/`, not in an assumed pass statement in this note.

The normal project workflow and shared [world contracts](../contracts/world.md) continue to apply. This feature used GitHub Actions because Modal credentials were not available in the conversation environment; it did not fall back to the owner's Windows machine.
