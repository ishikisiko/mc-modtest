## 1. Custom structure registration & siting

- [x] 1.1 Add `src/main/java/com/example/myvillage/sect/SectStructure.java` with a registered `StructureType`, `findGenerationPoint`, and `generatePieces`.
- [x] 1.2 Add worldgen data under `src/main/resources/data/myvillage/worldgen/` (structure + structure_set + placement) and a high-relief biome tag under `tags/`; gate siting to the tag and set sparse spacing + minimum separation.
- [x] 1.3 Make siting world-seed reproducible and verify no force-load is used (terrain bakes during chunk gen).
- [x] 1.4 Register the structure for `/locate`; remove the "No worldgen is registered" statement and wire registration in `MyVillageMod`.

## 2. Mountain derivation (反推山形)

- [x] 2.1 Add a derivation module that reads the terrace profile + parameters exported by `build-sect-compound` and builds a heightfield: terrace elevations as skeleton, seed-driven noise for inter-terrace and outer slopes.
- [x] 2.2 Resolve an outer blend skirt interpolating derived→natural heightmap across a tunable radius; assert no abrupt seam except the intended cliff-back.
- [x] 2.3 Produce the sheer cliff-back face behind the summit terrace's cliff-back edge.
- [x] 2.4 Write the mountain as solid built stone during chunk gen, surviving chunk boundaries; confirm terraces rest at planned elevations (no float/bury).

## 3. Cloud sea & detached spire

- [x] 3.1 Place the horizontal cloud-sea surface (white/tinted glass) at the configured Y between gate and disciple terraces; add optional powder-snow wisps at terrace edges with feathered/randomized edges.
- [x] 3.2 When the compound selects the detached-spire feature, raise the solitary peak under the detached volume with a gap to the main mountain; place the detached volume on the spire (no unsupported gap) and span the flying bridge.

## 4. Compound placement on derived terrain

- [x] 4.1 Reuse the `build-sect-compound` realizer to place volumes, galleries, stairs, and the feature onto the derived terrain with the same deterministic geometry as the command build.
- [x] 4.2 Confirm the on-the-spot `/myvillage sect [seed]` build is unchanged when no derived terrain is supplied.

## 5. Command & discovery

- [x] 5.1 Extend the `/myvillage sect` branch with a force-generate mode that builds a worldgen-style sect (with derived mountain) at a location and accepts a detached-spire variant argument (one of three / none); omitting it falls back to per-seed selection.

## 6. Validation & acceptance

- [x] 6.1 Add worldgen checks to `reports/sect_generation_validation.json`: seed-reproducible siting, biome gating, minimum separation, blend-skirt seam, terraces at planned elevations, feature random-but-deterministic.
- [x] 6.2 Run a multi-seed survey (siting frequency, feature presence rate, generation cost) and add a worldgen preview to the preview aggregate; serve and report the review URL per `AGENTS.md`.
- [x] 6.3 Update `README.md` (worldgen behavior, force-generate command, `/locate`), `AGENTS.md`, `CHANGELOG.md`, and `META-INF/neoforge.mods.toml` in this change; build the mod jar when practical.
