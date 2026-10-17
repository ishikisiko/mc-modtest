# Seeded Cultivation Worldplan

0.59.0 adds the opt-in `myvillage:cultivation` world preset. The existing normal world and the legacy region generator in [13](13_region_topology.md) remain available and are not migrated. This note supersedes legacy region-layer deferrals only for the new preset.

The owner-facing rules, creation instructions, CLI, safety model, constraints and acceptance procedure are in [Worldplan acceptance](../worldplan/acceptance.md).

## Ownership and generation

- `blueprints/worldplan/rules_v1.json`: authored macro rules and terrain profile weights.
- `blueprints/worldplan/yunshan_placeholder_v1.json`: the placement fixture of the four tracked Yunshan buildings. The missing external full D1 city is not recreated by assertion.
- `tools/worldplan/generate_assets.py`: compiles the original Yunshan blueprints, validates paths and services, cuts exactly 35 complete 16×32×16 NBT tiles, and writes frozen rules and the native world preset under `build/generated/worldplan/resources`.
- `worldplan/WorldPlanRules`, `WorldPlan`, `WorldPlanExporter`, `WorldPlanCli`: pure JDK/Gson core. The CLI and runtime use the same planner, not independent approximate implementations.
- `region/runtime/RegionGeometry`: optional frozen shape and site reservations. The old RegionGraph constructor and canonical legacy JSON stay unchanged when geometry is absent.
- `worldplan/runtime/PlannedChunkGenerator`: binds the actual world seed in `createState`; constructs the real settings-backed RandomState rather than using ChunkMap's dummy state for custom generators. It preserves native underground generation, implements the plan's surface in NOISE, delegates surface/carvers/decorations, then emits the one tile owned by the current city chunk.
- `PlannedBiomeSource`: reads holders only after registries bind; retains the native underground cave biomes and chooses surface ecology from the plan.
- `WorldPlanReservationMixin` protects ordinary WorldGenRegion writes; `WorldPlanOreMixin` protects the native OreFeature direct-section path. Decoration scopes are thread-local and closed in try-with-resources. Player edits are not blocked.

## Persistence and scope

Frozen JSON rules and a plan fingerprint are encoded in the generator in `level.dat`; tile resource hashes detect incompatible resource replacement. Do not mutate V1 algorithms or remove its assets when old saves still use it. Introduce versioned algorithms and assets instead. Full chunks are never rebuilt from the atlas.

The core is a 4000-block radius, with a continuous terrain blend into stateless outer cells. Outer queries inspect only a bounded neighbourhood and return stable coordinate-based region IDs. Negative coordinates use floor. Queries and exports do not force chunks.

RegionRuntime and the finite core world-sim graph use the same planned geometry. GatePlacement avoids the city reservation. Wilderness reads outer ecological profiles but does not invent infinite ledger people. Region tier is not a player realm index, and the existing realm demographic balance has not been rewritten.

The city is emitted in chunk generation rather than as a vanilla StructureStart, so its lookup entry point is `worldplan city`, not a fictional `/locate` structure ID. Macro dashed routes are graph relationships, not generated highways. Maps sample pre-decoration terrain and cannot substitute for real chunk or visual evidence.

## Checks

Run `tools/worldplan/ci.py` in the isolated cloud environment configured by the feature workflow, or the separate commands in the acceptance document. Checks cover generated-resource round trips, 510 seed layouts, legacy region parity and sim boundaries, real NOISE columns, city tiling, save/restart, opposite visit order, preservation of a player-placed marker, ordinary-world opt-out and native client images. Results live in `reports/worldplan/`, not in an assumed pass statement in this note.

The normal project workflow and shared [world contracts](../contracts/world.md) continue to apply. This feature used GitHub Actions because Modal credentials were not available in the conversation environment; it did not fall back to the owner's Windows machine.
