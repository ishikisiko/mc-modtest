# 假山 Form Diagnosis

Factual record of why shipped 假山 (rockery) "看不出形态" (no readable form), and
the agreed rebuild direction. See-also the `garden-rockery` spec
(`openspec/changes/rebuild-jiangnan-mansion/specs/garden-rockery/spec.md`).

## Symptom

In-game `/myvillage place chinese_mansion_*`, the 假山 reads as "地上一排小石头
材质的尖刺状，完全没有山" — a row of small stone-textured spikes on the ground,
not a mountain. Confirmed by NBT inspection of `chinese_mansion_001`:
`myvillage:rockery_block` appears on only **two Y layers** (y=0: 3 blocks,
y=1: 13 blocks). The whole 假山 is 1–2 blocks tall.

## Root cause: architecture, not art

The 假山 is built by `tools/buildgen/rockery.py` (`derive_rockery`), which
computes a **2D heightfield** over the parcel bbox and emits
`{cell: (variant_id, moss_level)}` — exactly **one** `myvillage:rockery_block`
per cell, placed at the cell's natural surface y. It never stacks vertically.

Each `myvillage:rockery_block` is a self-contained mini-mountain: a 16×16×16
sub-cell voxel field (role-driven: `peak`/`slope`/`base`/`corner`/`standalone`),
greedy-merged into ≤ 32 model cubes, with a matching `VoxelShape`. Physically
each block occupies one Minecraft cell and reaches ~1 block tall at most
(`ROLE_PROFILES` peak_h ≤ 15 sub-cells ≈ just under one full block).

So the parcel renderer scatters ~13–18 of these independent mini-mountains
across a large bbox (≈ 20×14 in `chinese_mansion`), and because Minecraft block
models render independently with no neighbour awareness, they never fuse into
one mountain — they read as a field of disconnected spikes. The large bbox is
wasted; the heightfield's `max_height=3` is never expressed as vertical stacking.

```
designed:  一座假山 (one mountain)        shipped: 尖刺阵列 (spike field)
                                              ▲    ▲    ▲    ▲    ▲
       ▲                                 █▒  █▒  █▒  █▒  █▒
      ███                              ████████████████████
     █████                             ────────────────────
    ███████     (3D stacked mass)      (flat scatter, 1-2 tall)
```

A secondary mismatch: `place_garden_pavilion`'s contract (compound.py docstring)
says the 亭 may sit **on the rockery peak** (`base_y = the rockery's standable
top`), implying the 假山 was meant to be a climbable mass with a flat standable
summit. The shipped spike field has no such summit, so the 亭 is placed at
ground (`base_y=0`) instead — another unmet original intent.

## Agreed rebuild direction (实体石山 + rockery 点缀, "path 1")

Rebuild `derive_rockery` to emit a **3D voxel fill** `{(x,y,z): block_state}`
over the bbox rather than a 2D `{cell: variant}` map:

- **Mountain body** — vanilla stone / andesite / cobblestone stacked in
  tapering layers (收分) so the mass reads as a real mountain, ~5 blocks tall
  over a 2×3+ footprint (single 假山 ≥ 2×3×3 volume per the user's bar).
- **Climbable summit** — a ≥ 3×3 flat platform near the top plus `stairs` on
  one flank for ascent (satisfies the original standable-top intent and keeps
  voxel-walkability valid). The 亭 can then sit on the summit.
- **太湖石 孔洞 (leak-through holes)** — deterministic carved air pockets
  through the body for the "漏/透" quality.
- **rockery_block demotion** — `myvillage:rockery_block` moves from "the
  paving主力" to a finishing accent: one `peak` variant on the true summit, a
  few `base`/`standalone` accents at the foot. The 16×16×16 sub-cell precision
  still earns its place on these accents, but no longer carries the whole mass.

Material palette, hole distribution, and silhouette profile are pending a
reference image from the user before implementation begins.

## Why this is recorded, not implemented

This is an explore-mode finding (architecture-level defect in a shipped feature
of an in-progress change `rebuild-jiangnan-mansion`, 56/59 tasks). The rebuild
touches `rockery.py` (2D→3D), `compound.py` (`place_garden_rockery` consumer
+ voxel-walkability), the `garden-rockery` spec, and regenerates the mansion
NBTs — a non-trivial change that warrants its own proposal once the reference
image nails down the visual contract. The 地面 (gravel-flood) fix shipped in the
same session (`0.16.0-fix2`) is unrelated to this finding.
