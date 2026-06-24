This delta extends `garden-rockery` with a **hero 假山** path: one named rockery
sculpted offline at 1/16 resolution (`docs/rockery_compressed.json`, a 48×48×48
micro-cube grid = 3×3×3 full blocks) and realized faithfully as a stacked
cluster of baked `myvillage:rockery_block` cells, fixing the spike-field defect
recorded in `docs/ai-kb/15_rockery_form_diagnosis.md`. The generic heightfield
path (one `rockery_block` per parcel cell) is unchanged and out of scope.

## ADDED Requirements

### Requirement: A hero 假山 is ingested from a micro-voxel JSON

The offline tool (`tools/buildgen/rockery_models.py`) SHALL parse a hero rockery
JSON whose `size` is `[48, 48, 48]` micro-cubes (= 3×3×3 full blocks, 1 micro =
1/16 block), with an RLE per-y-layer encoding (`"<count><palette-char>"` runs of
48 cells per z-row), and a palette mapping `a`=air, `s`=stone, `m`=mossy stone,
`w`=water, `g`=grass, `t`=oak log, `l`=oak leaves.

#### Scenario: The micro-grid decodes losslessly

- **WHEN** the hero JSON is parsed
- **THEN** every layer row SHALL expand to exactly 48 cells
- **AND** the decoded non-air micro-cube count SHALL match the source field
  (no run dropped or mis-summed).

#### Scenario: The grid slices into full-block cells

- **WHEN** the decoded 48³ field is sliced by `(x//16, y//16, z//16)`
- **THEN** each non-air micro-cube SHALL fall into exactly one of the ≤27
  full-block cells with cell-local coords `(x%16, y%16, z%16)`
- **AND** fully-air cells SHALL be dropped.

### Requirement: Each rock cell bakes one hero variant

For each full-block cell containing rock (`s` or `m`), the tool SHALL build a
16×16×16 rock mask from its `s`/`m` micro-cubes and bake it through the existing
model + `VoxelShape` pipeline into a hero variant, identified distinctly from
the generic role catalog.

#### Scenario: Hero variants are excluded from generic role sampling

- **WHEN** the variant catalog is enumerated for `rockery.py`'s heightfield
  role sampling
- **THEN** hero variants (marked `hero=True` / `role=None`) SHALL NOT be eligible
  for selection
- **AND** the generic `peak`/`slope`/`base`/`corner`/`standalone` counts SHALL be
  unchanged by this change.

#### Scenario: Stone and moss preserve the source material masks

- **WHEN** a hero cell contains stone (`s`) and mossy stone (`m`) micro-cubes
- **THEN** the two material masks SHALL be greedy-merged separately
- **AND** their model elements SHALL use the shared `swatch_stone` and
  `swatch_mossy` textures respectively
- **AND** the combined `s`+`m` mask SHALL drive the cell's `VoxelShape`,
  preserving the source's 青苔脚 → 石身 banding at micro-voxel resolution.

### Requirement: The hero 假山 is placed as a stacked 3×3×3 cluster

`place_garden_rockery` SHALL, for a parcel tagged as the hero rockery, stamp the
baked cells as a vertically stacked cluster (one block per non-empty rock cell at
its full-block `(dx, dy, dz)`), NOT as a 2D surface heightfield.

#### Scenario: The mass stacks vertically

- **WHEN** the hero 假山 is placed and the resulting NBT is inspected
- **THEN** `myvillage:rockery_block` SHALL appear across at least 3 distinct Y
  layers (the source spans full-block bands y=0,1,2)
- **AND** the placement SHALL NOT degenerate to a 1–2 block-tall scatter.

#### Scenario: The summit is standable and carries the 亭

- **WHEN** the hero cluster is placed
- **THEN** the top rock cell SHALL expose a flat standable top face
- **AND** `place_garden_pavilion` SHALL be able to set `base_y` to that summit so
  the 亭 sits on the peak
- **AND** voxel-walkability validation SHALL report no `voxel_*` errors for the
  garden parcel.

### Requirement: Non-rock materials are realized as a dressing pass, not baked into rock

Grass (`g`) and tree (`t`/`l`) micro-cubes SHALL NOT be baked into the rock
models; they SHALL be emitted as real vanilla block placements layered onto the
baked rock mass. Water (`w`) SHALL be realized by the water scheme below, never
as flowing-water blocks baked into the placement and never inside a
`rockery_block` model element.

#### Scenario: Foliage becomes real blocks

- **WHEN** the hero dressing manifest is applied
- **THEN** the 草帽顶 SHALL be a real grass/moss block on the summit
- **AND** the 小树 SHALL be a real oak sapling or hand-placed tiny oak on the summit
- **AND** no `g`/`t`/`l` micro-cube SHALL appear inside a `rockery_block` model element.

### Requirement: Water is realized by mechanism-appropriate parts (no voxel-shaped fluid)

Minecraft fluids cannot be voxel-shaped, so each water feature SHALL use the
mechanism that fits it: real source blocks for swimmable water, waterlogging for
rock-meets-water, and a baked translucent block for the one visible sub-block
trickle. No flowing-water (`level>0`) block SHALL be baked into the placement.

#### Scenario: The foot pool is a contained real water source

- **WHEN** the hero is placed (including standalone `/myvillage place hero_rockery`)
- **THEN** the 山脚水池 SHALL be `minecraft:water` SOURCE blocks in a
  self-contained basin at the +z foot
- **AND** the basin SHALL hold water without relying on an external `garden_pond`
- **AND** no baked flowing-water block SHALL appear in the placement.

#### Scenario: 山脚入水 is expressed by waterlogging

- **WHEN** a by=0 rock cell sits within the foot basin
- **THEN** that `rockery_block` SHALL be `waterlogged=true`
- **AND** `rockery_block` SHALL implement `SimpleWaterloggedBlock` so water renders
  through the rock model's gaps.

#### Scenario: The visible trickle is a baked translucent block, not a fluid

- **WHEN** the 细瀑 (visible trickle) between the summit outlet and the pool is placed
- **THEN** it SHALL be `myvillage:rockery_cascade`, a translucent water-textured
  block with an empty (passable) `VoxelShape`
- **AND** it SHALL occupy AIR cells only (never co-located with rock in one cell)
- **AND** the encased internal conduit voxels SHALL be omitted.

### Requirement: Hero realization is deterministic and byte-stable

The generator SHALL treat the hero JSON as a fixed input with no per-seed noise,
so re-running it produces byte-identical assets and an identical placement record.

#### Scenario: Regeneration is byte-stable

- **WHEN** `from_voxel_json()` is run twice on the same JSON
- **THEN** the generated model JSONs, blockstate entries, `VoxelShape` table, and
  placement record SHALL be byte-identical
- **AND** a SHA fixture (mirroring `cultivation_style_baseline_hashes.txt`) SHALL
  guard the hero assets against unintended drift.
