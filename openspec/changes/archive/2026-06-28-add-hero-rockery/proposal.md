# add-hero-rockery

## Why

The shipped 假山 reads as a "尖刺阵列" not a mountain. The root cause is recorded
in `docs/ai-kb/15_rockery_form_diagnosis.md`: `derive_rockery` computes a **2D
heightfield** and emits exactly one `myvillage:rockery_block` per cell at the
cell's surface y — it never stacks vertically, so a parcel scatters ~13–18
independent 1-block-tall mini-mountains that never fuse. The diagnosis deferred
the rebuild pending "a reference image from the user before implementation".

That reference has now arrived, and it is better than an image: a hand-sculpted
**micro-voxel model** at `docs/rockery_compressed.json` — a 48×48×48 micro-cube
grid (= **3×3×3 full blocks**, one micro-cube = 1/16 block), RLE-encoded per
y-layer. It describes one 太湖石-class 假山: a tapering (收分) mossy base at the
waterline, a stone body, a grassy cap, a small oak sapling on top, a foot 水池,
and a 1-wide vertical 泉 water thread trickling down the rock.

The JSON's resolution is not arbitrary. **One full block = 16³ micro-cubes =
exactly one `rockery_block` voxel field.** The existing `rockery_models.py`
baker already turns any 16³ field into a model (greedy-merged ≤32 cubes) + a
matching `VoxelShape`. So this sculpt is **27 cells the baker can already eat**
— and only **19 are non-empty** (all 19 carrying rock). The faithful path is nearly
free on the rendering side; what is new is *ingesting the JSON*, *placing the
cells as a stacked cluster* (the fix for the spike-field bug), and *dressing the
non-rock materials* (water / grass / tree) as real vanilla blocks.

This also revises the diagnosis's tentative "path 1" (demote rockery_block,
build the mass from vanilla full blocks). That assumed a large bbox; this sculpt
is a compact 3×3 footprint whose entire value is the **sub-block silhouette** —
exactly what `rockery_block` exists to carry. Downsampling it to vanilla full
blocks would discard the silhouette. So the mass stays on `rockery_block`, fixed
as a 3×3×3 cluster rather than a 2D scatter.

## What Changes

Scope: realize **one named hero 假山** faithfully from `rockery_compressed.json`.
The generic scattered-specimen path (`rockery.py` codebook) is left untouched;
future 假山 may quantize onto the codebook or be re-sculpted — out of scope here.

1. **JSON ingest + slice** — add a `from_voxel_json()` path to
   `rockery_models.py` that parses the RLE micro-grid, slices it into the ≤27
   full-block cells, and for each non-empty rock cell extracts its 16³ rock
   (`s`/`m`) sub-field, assigns one `moss_level` by per-cell majority, and bakes
   a variant (model JSON + `VoxelShape`) exactly as the existing catalog does.

2. **Hero variant registration** — the 19 sculpt-specific cells register as
   their own variant group so the generic `rockery_block` catalog stays lean.
   (Design decides: dedicated variant namespace on `rockery_block`, or a
   separate `rockery_hero_block`. See `design.md`.)

3. **Stacked-cluster placement** — a new placement record (the cluster's
   per-cell `(dx, dy, dz) -> (variant, moss, facing)` map + a dressing manifest)
   that `place_garden_rockery` consumes instead of the 2D heightfield, so the
   19 cells stack into one 3×3×3 mass with a real standable summit.

4. **Dressing pass (real vanilla blocks)** — water `w` (foot 水池 +
   waterlogged 泉 column), grass `g` cap, oak `t`/`l` sapling are emitted as
   ordinary block placements layered onto the baked rock mass, since fluids and
   foliage cannot live inside a custom rock model.

5. **Consumer + walkability** — `place_garden_pavilion`'s 亭-on-peak contract is
   re-satisfiable (the cluster has a flat standable summit); voxel-walkability
   re-validated for the mansion garden.

## Impact

- Code: `tools/buildgen/rockery_models.py` (ingest + bake), `rockery.py` /
  `compound.py` (`place_garden_rockery` consumer + dressing), `RockeryBlock.java`
  (+ hero variants in the enum + `VoxelShape` table) or a new hero block.
- Assets: new `assets/myvillage/{models,blockstates,textures}/...` for the hero
  variants; regenerated `chinese_mansion_*.nbt`.
- Specs: `garden-rockery` (this change's delta) gains the hero-sculpt
  requirements; `15_rockery_form_diagnosis.md` gets a "resolved by" note.
- Out of scope: generic codebook 假山 generation; other mansions' 假山 (may get
  variants or be re-sculpted later).
