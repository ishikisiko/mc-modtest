# 假山 Form Diagnosis

Factual record of why the original 假山 (rockery) "看不出形态" (no readable
form), and how the defect was resolved. See-also the `garden-rockery` spec
(`openspec/changes/rebuild-jiangnan-mansion/specs/garden-rockery/spec.md`).

## Revised in 0.16.2 (re-sculpt + flood fix)

The first hero sculpt (a hand-authored crude cone) read as a featureless grey
dome **and** drowned itself in water: the placement set the summit outlet and the
山脚 cells `waterlogged=true`, but a waterlogged block carries a *water source*
(`getFluidState`), and a source spreads into adjacent open air — on the exposed
cluster it cascaded flowing water over the whole 假山 (a translucent "blue tent")
and spilled into a surrounding moat, hiding the rock. Two fixes shipped together:

- **Form** — `docs/rockery_compressed.json` is now procedurally authored by
  `tools/buildgen/gen_hero_rockery_sculpt.py` to match the reference `docs/mt.png`:
  a layered 收分 太湖石 (stone-dominant, moss accents) with a spring that issues
  from a grotto carved *inside* the rock and cascades down the terraces into a
  pool embedded in the foot. Iterate offline with the new 48³ micro-voxel
  previewer `tools/buildgen/preview_voxel_field.py` (the block-level
  `preview_structure.py` cannot show sub-block detail).
- **Water** — `derive_hero_rockery` drops `waterlogged` entirely. Visible water is
  a contained pool (sources only, walled) + the non-fluid `rockery_cascade`
  curtain; the spring reads from the baked grotto geometry. No flow, no flooding.
  Cell count went 19 → 20; assets/structures/baseline-hashes regenerated.

## Resolved by `add-hero-rockery`

The shipped mansion garden now uses the fixed micro-voxel sculpt at
`docs/rockery_compressed.json`: a 48×48×48 field sliced into 19 non-empty
`rockery_block` cells and stamped as one stacked 3×3×3 cluster. Stone and moss
remain visible at 1/16-block resolution through separately baked material
masks; real blocks provide the summit foliage, contained source-water pool,
waterlogged rock foot/outlet, and passable `myvillage:rockery_cascade`.

The standalone `/myvillage place hero_rockery` review fragment carries the same
self-contained cluster. The generic heightfield/codebook path remains available
for non-hero specimens and is intentionally unchanged.

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

## Superseded tentative direction (实体石山 + rockery 点缀, "path 1")

Before the reference sculpt arrived, the tentative direction was to rebuild
`derive_rockery` as a vanilla-block **3D voxel fill**
`{(x,y,z): block_state}` over the bbox:

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

`add-hero-rockery` superseded this direction for the named hero specimen. The
3×3×3 sculpt's value is its sub-block silhouette, so downsampling it to vanilla
full blocks would discard the authored form. A future generic large-bbox
rockery may still use this strategy.

## Historical implementation context

This diagnosis originated during `rebuild-jiangnan-mansion`. The later
`add-hero-rockery` change supplied the missing visual contract as a hand-sculpted
micro-voxel source and implemented the dedicated hero path without replacing
the generic codebook generator. The 地面 (gravel-flood) fix shipped in
`0.16.0-fix2` remains unrelated.
