# Design — add-hero-rockery

## Source: `docs/rockery_compressed.json`

- `size`: `[48, 48, 48]` micro-cubes = **3×3×3 full blocks** (1 micro = 1/16 block).
- `palette`: `a`=air, `s`=stone, `m`=mossy stone, `w`=water, `g`=grass,
  `t`=oak log, `l`=oak leaves.
- `layers[]`: one per y-level (`y` 5..39, 35 non-empty layers), each a list of
  48 RLE rows (`"20a9w19a"` = 20 air, 9 water, 19 air) indexed by z; x within row.
- Occupied y is 5..39; the rock mass is y=6..34, the cap+tree y=29..39.

### Measured material totals (non-air = 18 128 micro-cubes)

`s`=11 418, `m`=6 546, `w`=79, `g`=68, `l`=14, `t`=3. Rock (`s`+`m`) = 98.6%;
everything else is sparse dressing.

### Full-block cell occupancy (3×3×3, fill = solid micro / 4096)

```
band by=0 (山脚/水线, 青苔为主)     band by=1 (山身, 纯石)        band by=2 (帽顶)
 (0,0): 14.8% m463 s143           (0,1,0):  0.5% s21          (1,2,1): 1.4%
 (0,1): 42.7% m900 s848           (0,1,1): 10.8% s442          g20 s19 w1 t3 l14
 (0,2): 17.2% m520 s185           (0,1,2):  0.9% s36
 (1,0): 42.7% m900 s848           (1,1,0): 10.8% s442
 (1,1): 63.4% s1791 m767 w38      (1,1,1): 75.8% s3051 g48 w4
 (1,2): 47.5% s960 m948 w36       (1,1,2): 14.2% s580
 (2,0): 17.2% m520 s185           (2,1,0):  0.9% s36
 (2,1): 46.6% s960 m948           (2,1,1): 14.2% s580
 (2,2): 19.9% s580 m234           (2,1,2):  1.4% s57
```

**19 non-empty cells, every one carrying some rock** (even the cap cell
`(1,2,1)` has a 19-voxel stone nub under its foliage; verified by Section 1
ingest). Moss is **vertically banded**: by=0 cells are mossy-dominant or
mixed; by=1 cells are pure stone — so a per-cell `moss_level` by majority is a
near-lossless approximation and avoids needing multi-texture-per-element models.

## Decision 1 — Faithful baked cells (not full-block downsample)

Realize the rock mass as **one baked `rockery_block` variant per non-empty rock
cell**, placed as a stacked 3×3×3 cluster. Rationale: 48³ = 27×16³ is a perfect
fit for the existing baker; the sculpt's value is its sub-block silhouette over
a tiny 3×3 footprint, which vanilla full blocks cannot express. This both
honors the deliberate 1/16 authoring and fixes the spike-field bug (the cells
stack instead of scattering).

Rejected: (B) quantize each cell onto the existing peak/slope/base codebook —
loses fidelity, kept for the *generic* path, not the hero; (D) vanilla
full-block fill (old "path 1") — discards the silhouette; (C) slab/stair build —
only ½-block resolution.

## Decision 2 — Per-voxel material via flat color swatches (色块)

**Revised** (was: per-cell `moss_level` majority + single texture). The existing
`rockery_block` model hardcodes `minecraft:block/stone` for every texture ref, so
`moss_level` is visually inert — a per-cell tint would not actually show. And we
have something better than a tint: the source carries **exact `s`/`m` material
per micro-cube**, so the 青苔脚 → 石身 gradient is known at sub-cell resolution.

So each hero cell's model is baked from **two material masks**: the `s` (stone)
voxels and the `m` (mossy) voxels are greedy-merged **separately** and emitted as
two element groups textured with two flat **color swatches** — `swatch_stone`
(taihu grey) and `swatch_mossy` (mossy green), shared 16×16 PNGs with light
grain. The material banding then renders **within each block**, straight from the
48³ data — strictly more faithful than a per-cell majority tint, and it sidesteps
the inert-`moss_level` gap entirely (hero material is baked, not tinted).

`moss_level` is still set on the blockstate (the property is shared with generic
variants) but is cosmetically irrelevant for hero cells. The combined `s`+`m`
mask still drives the `VoxelShape` (collision is material-agnostic). Box budget:
each material mask greedy-merges to ≤32 boxes independently; hero cells are
strongly single-material-dominant so the combined element count stays modest.

(The generic catalog's inert-`moss_level` binding is a separate, broader issue —
36 variants, procedural single-material — and is out of this change's scope.)

## Decision 3 — Hero variants isolated from the generic catalog

The 19 cells are sculpt-specific and must not bloat the generic role catalog
(`peak`/`slope`/`base`/...) that `rockery.py` samples for scattered specimens.

**Chosen:** a dedicated hero-variant group on `rockery_block` named
`hero_taihu_<cell>` (e.g. `hero_taihu_b0_c11`), appended to the `Variant` enum +
`shapeFor` table by the same generator pass, but excluded from
`VARIANT_CATALOG`'s role-sampling pool (a `hero=True` / `role=None` flag already
exists on the `Variant` dataclass — extend its semantics). Keeps one block class,
one blockstate file, one asset tree; the generic path simply never picks them.

Alternative considered: a separate `myvillage:rockery_hero_block`. Cleaner
namespace separation but doubles the Java/asset/validator surface for a 19-cell
one-off. Defer unless the enum grows unwieldy across multiple hero sculpts.

## Decision 4 — Dressing pass as real vanilla blocks

Water/grass/tree cannot live inside a custom rock model (fluids don't render in
block models; foliage needs real blocks for tint/flow). Emit them as a separate
ordered manifest layered onto the baked rock:

- **Water** — handled by Decision 6 (real source pool + waterlogged 山脚 +
  summit outlet + baked translucent 细瀑); NOT baked into the rock models.
- **草帽顶** — `g` at the summit → a `minecraft:grass_block` (or `moss_block`)
  capping the top standable cell so the summit reads as planted.
- **小树** — `t`×3 + `l`×14 at cell `(1,2,1)` → one `minecraft:oak_sapling` or a
  hand-placed tiny oak (1 log + small leaf cluster) on the summit, beside the 亭
  if a 亭 is placed.

## Decision 5 — Placement record + consumer

`from_voxel_json()` emits a `HeroRockeryPlacement`:
`{cells: {(dx,dy,dz): (variant_id, moss_level, facing)}, dressing: [...]}` rooted
at the cluster origin. `place_garden_rockery` (compound.py) switches: if the
parcel is tagged `hero=taihu`, it stamps the cluster + dressing at the parcel
anchor instead of running `derive_rockery`'s 2D heightfield. The cluster's top
cell exposes a flat standable face, so `place_garden_pavilion`'s `base_y = the
rockery's standable top` contract works again, and voxel-walkability re-passes.

Determinism: the JSON is the fixed input; no per-seed noise on the hero. The
generated assets + the placement are byte-stable (a fixture/SHA guard like the
existing `cultivation_style_baseline_hashes.txt` pattern).

## Decision 6 — Water realization (voxel-faithful where it shows, functional where it counts)

Minecraft fluids cannot be voxel-shaped: the `LiquidBlockRenderer` always fills
a full x/z footprint, `level` only varies the top-surface height, and
waterlogging fills the whole cell. So water is split by what each part needs,
rather than forced into one mechanism.

- **山脚水池 (foot pool)** — real `minecraft:water` **SOURCE blocks only**, a
  **surface-level pool** (water at the foot/ground y, dy=0) in a self-contained
  basin at the +z foot, matching the JSON pool (x≈20–28, z≈28–35, block-cell
  ~(1,·,1–2)). **Revised during implementation (task 4.1):** the pool sits at the
  ground surface y=-1 (not a sunken y=-2 pocket) so it reads as a body at water
  level AND so the mansion's ground-layer validator sees water — not an air hole —
  at every garden cell; the consumer carves the cell directly above each pool
  source to air so the water shows. The standalone fragment ships a contained
  basin (stone floor at y=-2 + grass rim at y=-1 forming the walls); in the
  mansion the yard-ground pass runs BEFORE `_layout_garden`, so the pool (and the
  山脚 rock foot) stamp their own y=-1 surface on top of the ground fill rather
  than being clobbered by it. Source-only ⇒ no baked flowing water, no flooding,
  deterministic; reflective + swimmable. An adjacent `garden_pond` in the mansion
  garden merges with it visually (so 山脚入水 composes naturally).
- **山脚入水 (rock meeting water)** — the by=0 rock cells sitting in the pool are
  **waterlogged**. Requires `rockery_block` to implement `SimpleWaterloggedBlock`
  (+ `waterlogged` blockstate property + `getFluidState`); water then renders
  through the rock model's gaps — the full-block analog of the JSON's
  water-and-rock-sharing-a-cell voxels.
- **峰顶出水口 (summit spring outlet)** — the JSON's exposed top water voxel
  (x24, y34, z18). **Implemented** by waterlogging the cap cell `(1,2,1)` rather
  than placing a free `minecraft:water` source: a free source on the flat summit
  would flow off and flood the climbable top / break voxel-walkability, whereas
  waterlogging renders water seeping through the cap's gaps (出水口) with zero
  flow. Feeds the 细瀑 ribbon below.
- **细瀑 (visible trickle)** — the one part that earns voxel resolution. A NEW
  decorative block `myvillage:rockery_cascade`: `render_type=minecraft:translucent`,
  `minecraft:block/water_still` texture (its `.mcmeta` gives the shimmer),
  `tintindex` + a registered `BlockColor` returning the water tint, a baked thin
  vertical water geometry (≈2–4 micro wide × 1–2 deep strip), and an **empty
  `VoxelShape`** (passable, non-collidable). It is its OWN block — a translucent
  water model cannot co-render cleanly with opaque rock in one model — so it
  occupies the AIR cells on the visible front face between the summit outlet and
  the pool. Visual-only (not a fluid: no flow / swim / physics); it reads as a
  thin 泉水细瀑 at sub-block width without flooding anything.
- **暗渠 (internal conduit)** — the encased middle thread voxels (four-side stone
  neighbours, invisible) are **DROPPED**. The "spring" reads from summit outlet →
  visible 细瀑 → foot pool, which is the JSON author's 引水入池 intent.

## Resolved decisions (from exploration)

- **Placement site** — BOTH: the `chinese_mansion` garden parcel AND a standalone
  `/myvillage place hero_rockery` review fragment. The hero carries its own basin,
  so the standalone preview is self-sufficient.
- **泉 / 水池** — superseded by Decision 6 (no longer open).

## Revision (0.16.2) — re-sculpt + flood fix

Post-acceptance review (in-game screenshot) showed the shipped hero read as a
crude grey dome drowned in flowing water. Two corrections, kept within this
change's architecture (micro-voxel `rockery_block` cluster + dressing):

- **Source sculpt is now procedural.** `docs/rockery_compressed.json` is authored
  by `tools/buildgen/gen_hero_rockery_sculpt.py` (deterministic) to match the
  reference `docs/mt.png`: a layered 收分 太湖石, stone-dominant with moss accents,
  a spring issuing from a grotto carved *inside* the rock that cascades down the
  terraces into a pool embedded in the foot. New offline tool
  `tools/buildgen/preview_voxel_field.py` renders the 48³ micro-field directly so
  the form can be iterated without launching Minecraft.
- **Decision 6 waterlogging is reversed.** Waterlogging a `rockery_block` makes it
  a spreading water source (it floods the exposed cluster), so the 山脚入水 /
  峰顶出水口 are no longer realized by `waterlogged=true`. `derive_hero_rockery`
  emits no waterlogged cells; visible water is a contained pool (sources only) +
  the non-fluid `rockery_cascade` curtain, and the spring reads from the baked
  grotto geometry. Cell count 19 → 20; assets/structures/baseline hashes refreshed.

## Revision — miniature summit tree + source-faithful water

Acceptance review found two remaining scale/continuity defects: the summit tree
was replaced by full Minecraft blocks, and the decorative cascade was stamped
at a fixed full-block coordinate outside the receding mountain face.

- **Tree/grass** — `g`/`t`/`l` are now baked as separate visual-only material
  masks in the summit hero cell. The source sculpt uses a leaning bonsai trunk,
  visible lateral branches and asymmetric cloud-like foliage pads. These masks
  do not contribute to `VoxelShape`, so summit walkability remains rock-driven.
- **Water** — `w` is now baked as a visual-only animated/tinted material mask in
  every water-bearing hero cell. The source field itself supplies the continuous
  grotto → stepped rock face → embedded foot-pool geometry. Water-bearing hero
  models render in the translucent pass; opaque rock textures remain opaque
  within that pass. The fixed external `rockery_cascade` column is no longer
  placed. Real fluid remains limited to the sealed full-block foot pool.
