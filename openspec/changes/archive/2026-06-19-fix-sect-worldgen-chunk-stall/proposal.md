## Why

After v0.11.0 shipped worldgen sects, approaching a `/locate`d sect freezes chunk loading: no new chunks generate anywhere, the freeze triggers *before* the sect is even visible, and `/tp` does not escape it. The `/myvillage sect` force-generate command is unaffected.

The cause is structural, not a crash. The single `SectStructurePiece` declares a bounding box of roughly **8 × 15 chunks** (`SITE_WIDTH 64 + 2·MOUNTAIN_MARGIN 28 = 120` blocks by `SITE_DEPTH 180 + 2·28 = 236` blocks ≈ 120 chunks). Minecraft calls `postProcess` once per overlapping chunk, and **each call re-runs the entire build over the full footprint** — `writeMountain` + `placeCloudSea` + `realizeCompound` — relying on `WorldGenSink.set` to merely *discard* writes outside the current chunk. The loops are never clamped, only the final write is. So each chunk redundantly performs ~30k–50k `chunkGenerator.getBaseHeight` noise-column samples plus re-parses every slot template's NBT from the `ResourceManager` (no cache), and this is multiplied across ~120 overlapping chunks. The worldgen executor saturates, the radius-8 feature-stage dependency front can no longer advance, and chunk loading appears to halt entirely.

In short: the work is **O(footprint × overlapping-chunks)** when it must be **O(footprint)** total. The class doc already states the intended contract — "each call writes only the cells inside that chunk's region" — but the computation does not honor it.

## What Changes

- **Clamp each `postProcess` to the current chunk.** Every build loop (`writeMountain`, `placeCloudSea`, terrace carve / axis stairs / retaining / cliff-back / slots / galleries / feature) SHALL iterate only over the intersection of its geometry with the incoming per-chunk `box`, so a chunk does ~hundreds of columns of work, not the whole ~26k-column footprint. Discarding out-of-range writes at the sink is kept only as a safety net, not the primary bound.
- **Cache parsed templates across chunks.** Slot/feature templates SHALL be parsed once and reused for all overlapping chunks of the same sect, instead of re-reading and re-patching NBT from the `ResourceManager` on every `postProcess`.
- **Make per-chunk slices seam-consistent.** Template placement RNG SHALL be derived from the sect site (stable across chunks), not from the current chunk's `box` origin, so a building straddling a chunk boundary places the same variant/rotation in both halves.
- **Fix the terrain/compound Y misalignment.** (Found during verification — the worldgen path never ran to completion before, so this was never visible.) The mountain/terrace passes placed via `base.offset(x, absoluteY, z)`, double-adding `base.getY()`, so the derived terrain floated ~a base-height above the buildings (which `realizeSlots` placed at the absolute Y). Terrain and compound SHALL share one absolute elevation frame.
- **No change to siting, biome gating, separation, or `/locate`.** The compound *geometry* (terrace layout, slot placement, mountain silhouette) is unchanged; only the absolute Y the terrain passes write to is corrected, and the on-the-spot `/myvillage sect` command benefits from the same Y fix. Python/Java plan parity is unaffected.
- **Verify the freeze is gone.** The change concludes with a buildable jar where generating near and walking/tp-ing into a worldgen sect loads chunks without stalling, and the realized sect matches the force-generated one for the same seed.

## Capabilities

### Modified Capabilities
- `sect-worldgen-structure`: the worldgen bake-in requirement gains a per-chunk-bounded execution guarantee — generating the chunks overlapping a sect MUST NOT do work proportional to the whole footprint per chunk, and MUST NOT stall chunk loading.

## Impact

- `src/main/java/com/example/myvillage/sect/SectStructurePiece.java` — clamp build loops to the per-chunk `box`; stable template RNG; reuse a per-sect template cache.
- `src/main/java/com/example/myvillage/sect/SectGenerator.java` — the shared realizer methods (`writeMountain`, `placeCloudSea`, `realizeCompound` and its sub-passes) accept a clip bound so the worldgen path can restrict iteration to the current chunk while the command path passes the full footprint unchanged.
- `src/main/java/com/example/myvillage/sect/SectSink.java` / `ServerLevelSink` — possibly extended to expose the per-chunk clip for worldgen; command path keeps full-footprint behavior.
- `src/main/java/com/example/myvillage/town/ModBlockFallback.java` — add/confirm a parsed-template cache so repeated `loadTemplate` calls during worldgen do not re-parse NBT.
- No datapack change (`structure_set` spacing/separation, `structure`, biome tag) — siting is unchanged.
- Version bump (single validated fix): `0.11.0` → `0.11.0-fix1`. MC 1.21.1, NeoForge, vanilla blocks only.
