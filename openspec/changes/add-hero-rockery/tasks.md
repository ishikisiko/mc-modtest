## 1. JSON ingest + cell slicing (`rockery_models.py`)

- [x] 1.1 Add an RLE decoder for `docs/rockery_compressed.json`: parse `size`,
      `palette`, and `layers[]`; expand each `"<n><char>"` run into a dense
      `(x, y, z) -> char` field over 48³. Validate `size == [48,48,48]` and that
      every layer row decodes to exactly 48 cells.
- [x] 1.2 Add `slice_cells(field) -> {(bx,by,bz): {(lx,ly,lz): char}}`: bucket
      each non-air micro-cube into its full-block cell `(x//16, y//16, z//16)`
      with cell-local coords `(x%16, y%16, z%16)`. Drop fully-air cells.
- [x] 1.3 For each cell, split into a **rock mask** (`s`/`m` only → 16³ solid
      field) and a **dressing list** (`w`/`g`/`t`/`l` with cell-local coords).
- [x] 1.4 Assign `moss_level` per rock cell by majority of `m` vs `s` micro-cubes
      (`heavy` if `m`-majority, `none` otherwise; optional `light` mid-band).

## 2. Bake hero variants (`rockery_models.py`)

- [x] 2.1 Add `from_voxel_json()` that, for each non-empty rock cell, feeds its
      16³ rock mask through the existing model-baker (greedy-merge ≤32 cubes) +
      `VoxelShape` builder, producing a `Variant(variant_id="hero_taihu_b{by}_c{bx}{bz}", hero=True, role=None)`.
- [x] 2.2 Extend the `Variant` dataclass / `VARIANT_CATALOG` so `hero=True` /
      `role=None` variants are baked + registered but **excluded** from
      `rockery.py`'s role-sampling pool.
- [x] 2.3 Emit assets for each hero variant: `models/block/rockery_block/hero_taihu_*.json`,
      blockstate entries in `blockstates/rockery_block.json`, and reuse existing
      stone/mossy textures (no new PNGs needed if moss is per-cell).
- [x] 2.4 Regenerate the Java `Variant` enum + `shapeFor` table in
      `RockeryBlock.java` with the hero variants appended (generator-owned region).
- [x] 2.5 Make `RockeryBlock` implement `SimpleWaterloggedBlock`: add a
      `waterlogged` `BooleanProperty`, override `getFluidState`
      (`waterlogged ? WATER.getSource(false) : super`), set it in
      `getStateForPlacement` from the fluid at the position, and propagate in
      `updateShape`. Add `waterlogged=true/false` to the blockstate variants.
- [x] 2.6 Add a new block `myvillage:rockery_cascade` (细瀑/水帘) in `ModBlocks.java`:
      `render_type=minecraft:translucent`, `noCollission`/empty `VoxelShape`
      (passable), `noOcclusion`. Ship `blockstates/rockery_cascade.json` +
      `models/block/rockery_cascade/*.json` (baked thin vertical water geometry,
      faces use `minecraft:block/water_still` with `tintindex: 0`), and register a
      `BlockColor` (via `RegisterColorHandlersEvent.Block`) returning the water
      tint so the grayscale texture renders blue.

## 3. Stacked-cluster placement + dressing

- [x] 3.1 Emit a `HeroRockeryPlacement` record: `cells: {(dx,dy,dz): (variant_id, moss_level, facing)}`
      (dy = `by`, the full-block layer) + `dressing: [(block_state, (dx,dy,dz))]`
      for water/grass/tree, rooted at the cluster origin.
- [x] 3.2 Build the **foliage** dressing: 草帽顶 (`g` → grass/moss block on the
      summit), 小树 (`t`×3 + `l`×14 at cell `(1,2,1)` → an `oak_sapling` or a
      hand-placed tiny oak on the summit, beside the 亭 if one is placed).
- [x] 3.3 Build the **water** dressing per design Decision 6:
      (a) 山脚水池 — carve a 1–2-deep self-contained basin at the +z foot
      (x≈20–28, z≈28–35) and fill with `minecraft:water` SOURCE blocks only;
      (b) 山脚入水 — mark the by=0 rock cells that sit in the basin
      `waterlogged=true`;
      (c) 峰顶出水口 — one `minecraft:water` SOURCE in a 1-deep contained basin at
      the summit cell;
      (d) 细瀑 — place `myvillage:rockery_cascade` in the AIR cells on the visible
      front face between the summit outlet and the pool. NO flowing-water blocks
      are baked into the placement (source-only ⇒ deterministic, no flooding).
- [x] 3.4 In `compound.py`, branch `place_garden_rockery`: when the parcel is
      tagged `hero=taihu`, stamp the cluster + foliage + water dressing at the
      parcel anchor instead of calling `derive_rockery`. Keep the 2D path for
      non-hero parcels.
- [x] 3.5 Ensure the cluster's top rock cell exposes a flat standable summit;
      re-enable `place_garden_pavilion`'s `base_y = rockery standable top` so the
      亭 sits on the peak. Confirm the summit outlet basin does not block the
      standable face.

## 4. Validation + regeneration

- [x] 4.0 Generate a standalone `hero_rockery.nbt` structure (the cluster +
      basin + dressing, self-contained) and confirm `/myvillage place hero_rockery`
      stamps it as a self-sufficient review fragment (basin holds water without an
      external `garden_pond`). Add it to the preview gallery / `place` registry.
      — `generate_hero_rockery_fragment()` (contained stone-floor + grass-rim
      basin); `size=[7,8,8]`, 134 blocks, 19 hero cells, pool sealed + open above.
      Emitted in `generate_compound_library.py`'s mansion branch + `place/hero_rockery.mcfunction`.
- [x] 4.1 Regenerate `chinese_mansion_001..006.nbt`; confirm the 假山 stacks to a
      ~3-block 3×3 mass (NBT shows `rockery_block` across ≥3 Y layers, not 1–2).
      — all 6 show 19 hero cells across 3 Y-layers, 7 waterlogged, cascade+pool
      intact. Fixed a clobber bug: `_place_yard_ground` now runs BEFORE
      `_layout_garden` and the foot pool is a surface pool (y=-1) so the ground
      layer stays hole-free while the garden features stamp on top.
- [x] 4.2 Run voxel-walkability (`validate_mansion`): no `voxel_*` errors; 亭 on
      summit is reachable; foot 水池 does not break path endpoints.
      — 6/6 `passed=True errors=[]`; voxel_reachability visited=1406, unreachable=0, cliff=0.
- [x] 4.3 Add a byte-stability fixture for the hero assets + placement (SHA guard,
      mirroring `cultivation_style_baseline_hashes.txt`).
      — `reports/hero_rockery_baseline_hashes.txt` (fragment placement + hero-asset
      rollup SHA) + `test_hero_rockery_hash.py` + `generate_hero_rockery_fixture.py`.
- [x] 4.4 Update `validate_mod_block_fallback.py` to accept the hero variants
      under both `vanilla` and `full` profiles (still `myvillage:` self-namespace).
      — `validate_mod_block_fallbacks.py` now surfaces `self_namespace_blocks` +
      `hero_variant_count` (19) and probes both modset profiles to assert the
      self decor is fallback-exempt under each; report `passed=True`.

## 5. Docs + acceptance

- [x] 5.1 Add a "Resolved by `add-hero-rockery`" note to
      `docs/ai-kb/15_rockery_form_diagnosis.md`.
- [x] 5.2 Sync the `garden-rockery` delta requirements (this change's spec) into
      the current owning spec under `rebuild-jiangnan-mansion` (the capability
      has not yet been archived into `openspec/specs/`; archive will carry the
      merged spec forward).
- [x] 5.3 Build the mod jar; preview the mansion garden; serve `out/preview/` and
      report the review URL for visual acceptance (per `09_validation_checklist.md`).
- [x] 5.4 Update `README.md` / `CHANGELOG.md` for the new command and bump the
      small-feature version to `0.16.1`, updating all four version-owned files
      involved (follow `openspec/config.yaml` `rules.tasks`).
