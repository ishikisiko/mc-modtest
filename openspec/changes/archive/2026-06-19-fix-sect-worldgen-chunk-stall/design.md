# Design — fix sect worldgen chunk stall

## Root cause (confirmed by reading)

`SectStructurePiece.footprintBox` declares a piece bounding box spanning the whole compound + mountain skirt:

```
X: base.x − 28  →  base.x + 64 + 28   = 120 blocks ≈ 8 chunks
Z: base.z − 28  →  base.z + 180 + 28  = 236 blocks ≈ 15 chunks
                                        ≈ 120 chunks intersect the piece
```

Minecraft (1.21.1) drives structure placement per chunk: `StructureStart.placeInChunk` passes `postProcess` a `box` equal to the **current chunk's column area**, and calls it once for every chunk whose area intersects the piece box. `SectStructurePiece.postProcess` then runs the *entire* build each time:

```
postProcess(chunk C):
    plan          = SectGenerator.plan(...)          # full plan
    mountain      = buildMountain(...)               # full mountain object
    writeMountain(...)        # loops ~114 × 228 ≈ 26k columns, getBaseHeight each
    placeCloudSea(...)        # loops the full gate→disciple gap
    realizeCompound(...)      # full terraces / slots / galleries / feature
    # WorldGenSink.set discards anything outside C — but the LOOPS still ran
```

So the cost is `O(footprint) × O(overlapping chunks)` ≈ 120 × ~30k–50k `getBaseHeight` calls ≈ 4–6M noise-column samples, plus ~1,200 NBT template re-parses (10 slots × 120 chunks, `ModBlockFallback.loadTemplate` reads + patches NBT every call). The worldgen thread pool saturates; the feature stage's radius-8 neighbor dependency cannot advance; chunk loading appears to halt globally. It is a soft-lock from quadratic work, not a deadlock or exception.

The command path is fine because `SectGenerator.build` runs the realizer **once** on force-loaded chunks.

## Fix: clip iteration to the current chunk

The invariant: **per `postProcess` call, do work proportional only to (footprint ∩ current chunk), and the union over all chunks reproduces today's full result exactly.**

The realizer is shared between the command path (full footprint, one pass) and the worldgen path (per-chunk). So thread a clip rectangle through the shared methods:

```
interface SectSink {
    ...
    /** Inclusive world-space x/z clip; loops skip cells outside it.
        Command sink returns an unbounded clip; worldgen sink returns the chunk box. */
    Clip clip();   // or: boolean inClipXZ(int worldX, int worldZ) + clip bounds
}
```

Then in `SectGenerator`, wrap each loop's x/z range by intersecting with the clip (in local coords: `clipX0 = max(loopX0, clip.x0 − base.x)`, etc.) so the iteration bounds themselves shrink — not just the writes. Methods touched: `writeMountain`, `placeCloudSea`, `carveTerraces`, `placeAxisStairs`, `placeRetainingFaces`, `placeCliffBack`, `placeCoveredGalleries`, plus the slot/feature template placement (templates are already clamped by `StructurePlaceSettings.setBoundingBox(box)`; the *clearVolume* + origin compute can stay, placement is region-safe).

Two implementation shapes considered:

| Option | How | Trade-off |
| --- | --- | --- |
| **A. Clip in the loops (chosen)** | Shared methods read `sink.clip()` and tighten their `for` bounds | Minimal API surface; command path passes an infinite clip → identical behavior; worldgen passes the chunk box → ~256-column work |
| B. Precompute full result once, then slice | Build once into a buffer keyed by sect, each chunk copies its slice | Needs cross-chunk cache of a 120×236×~90 volume = large memory; cache lifecycle/eviction across the chunk system is fragile |

Option A is chosen: smaller, no large buffers, keeps determinism trivially (each cell computed from seed⊕coords as today, just skipped when outside the clip).

## Template caching

`ModBlockFallback.loadTemplate` re-reads NBT from the `ResourceManager` and re-runs `patchStructure` per call. Add a cache keyed by `ResourceLocation` (parsed `StructureTemplate` + substitution count), invalidated on datapack reload. The worldgen path hits ~10 distinct templates total but currently parses each ~120 times.

## Seam consistency (RNG)

Today `templateRandom = RandomSource.create(siteSeed ^ box.minX()*… ^ box.minZ())` uses the **current chunk** origin, so a building straddling two chunks can roll a different variant/rotation per half. Derive the placement RNG from the **sect site** (e.g. `siteSeed` mixed with the slot's stable local origin, not the chunk box). Variant selection in `plan(...)` is already seed/terrace-based and stable; this only affects `placeTemplate`'s `RandomSource`.

## What stays identical

- Datapack `structure_set` (spacing 48 / separation 16 / salt), `structure`, and `#myvillage:has_sect` biome tag — siting unchanged.
- The mountain derivation (`SectMountain`, 反推山形) and compound geometry — every cell still derives from seed⊕coords, so Python/Java parity (`validate_sect_generation.py`) and the force-generate command output are byte-stable for a fixed seed + site.

## Verification

1. New world, `/locate` a sect, walk toward it and `/tp` near/into it — chunks keep loading; no stall.
2. Force-generate the same seed + site via `/myvillage sect`, compare the realized worldgen sect to it — identical compound (spot-check terraces, hall-against-cliff, feature variant, gallery seams across chunk boundaries).
3. Confirm `validate_sect_generation.py` parity still passes (no geometry change).
