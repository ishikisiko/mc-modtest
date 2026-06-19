## 1. Clip the worldgen build to the current chunk

- [x] 1.1 Add a clip abstraction to `SectSink` (inclusive world-space x/z bounds, e.g. `clip()` returning a `Clip` with an `isInsideXZ`/bounds accessor); `ServerLevelSink` returns an unbounded clip, `WorldGenSink` returns the per-chunk `box` bounds
- [x] 1.2 Tighten the iteration bounds (not just the writes) in `SectGenerator.writeMountain` and `placeCloudSea` by intersecting their x/z ranges with the sink clip in local coords
- [x] 1.3 Tighten iteration in `realizeCompound`'s sub-passes — `carveTerraces`, `placeAxisStairs`, `placeRetainingFaces`, `placeCliffBack`, `placeCoveredGalleries` — to the clip
- [x] 1.4 Confirm slot/feature template placement stays region-safe: keep `StructurePlaceSettings.setBoundingBox(box)`; skip a slot whose bounds do not intersect the clip to avoid redundant `clearVolume`/`loadTemplate` work
- [x] 1.5 Keep `WorldGenSink.set`'s box check only as a safety net; verify the command path passes an unbounded clip so its output is unchanged

## 2. Cache parsed templates across chunks

- [x] 2.1 Add a parsed-template cache in `ModBlockFallback` keyed by `ResourceLocation` (cached `StructureTemplate` + substitution count), invalidated on datapack reload
- [x] 2.2 Route `loadTemplate` through the cache so repeated worldgen calls reuse the parse instead of re-reading + re-patching NBT

## 3. Seam-consistent placement RNG

- [x] 3.1 Derive `templateRandom` (and any per-slot placement RNG) from the stable sect site + slot origin, not the current chunk `box` origin, so a building straddling a chunk boundary places the same variant/rotation in both halves

## 4. Verify the fix

- [x] 4.1 New world: `/locate` a sect, walk toward it and `/tp` near/into it — confirm chunks keep loading with no global stall (before/after on the same seed) — confirmed in-game: chunks load on approach (screenshot)
- [ ] 4.2 Force-generate the same seed + site with `/myvillage sect` and compare to the worldgen-generated sect — identical compound, with no mismatched halves across chunk seams
- [x] 4.3 Run `tools/validate_sect_generation.py` (and the sect mountain/compound validators) to confirm geometry parity is unchanged

## 6. Fix terrain/compound Y alignment (found during 4.x verification)

- [x] 6.1 Root cause: the mountain/terrace passes placed via `plan.base.offset(x, absoluteY, z)`, double-adding `base.getY()` (terrain floated ~base-height above the buildings, which `realizeSlots` placed at the absolute Y). Mountain `height`/terrace `elevation` are absolute (compared against the absolute natural surface), so the placement must use the absolute Y directly.
- [x] 6.2 Add a `SectGenerator.at(base, localX, worldY, localZ)` helper and route all terrain passes through it — `writeMountain`, `placeCloudSea`, `carveTerraces`, `placeAxisStairs`, `placeRetainingFaces`, `placeCliffBack`, `placeCoveredGalleries`, the flying-bridge deck — so terrain and buildings share one absolute Y frame
- [x] 6.3 Confirm `realizeSlots` / detached-feature placement (already absolute) and the dy=0 force-load corner offset are unchanged; recompile clean

## 5. Release

- [x] 5.1 Bump version `0.11.0` → `0.11.0-fix1` across `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together
- [ ] 5.2 Build the jar and confirm it loads; smoke-test `/locate` + approach once more on the release build
