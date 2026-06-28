## Context

Two priors make this change cheap:

1. **`rebuild-chinese-courtyard`** shipped the `CompoundGraph` z-band abstraction (`outer_yard_band` / `inner_gate_band` / `main_yard_band`) explicitly written to generalize to `jin_count ∈ {1, 2, 3}`. The follow-up was always planned; this is that follow-up, scoped to 江南 form (not 北京 multi-进).
2. **`add-custom-plaque-blocks`** proved the `myvillage:` self-namespace decorative-block architecture: `DeferredRegister.Blocks` + per-id blockstate + `assets/` tree + Python generator placement. The `myvillage:wall_plaque` is the first instance; 假山 is the second. The shared protocol is extractable.

And one piece of prior art makes the 假山 cheap:

3. **`sect_mountain.py`** (`DerivedMountain.height(x, z)`) is a complete deterministic heightfield + value-noise + skirt-derivation paradigm, mirrored between Python and Java (`SectMountain.java`). 假山 is this paradigm at 1/4 scale, swapping dirt+stone for andesite+moss. No new algorithm.

Constraints:

- **One mod, no external-mod runtime dependency for decor**. The user explicitly redirected this: "我们这是在做 MOD 啊, MOD 添加自己物品假山". The `myvillage:` self-namespace is always legal (per `AGENTS.md`); 假山 is a mod-owned block, not a Chisels & Bits consumer.
- **No `/myvillage` command-surface breaking change for `chinese_courtyard`**. The `chinese_courtyard_001..006.nbt` filenames stay; their content regenerates (照壁侧立 + 倒座 side alley + 垂花门 multi-cell + transition stairs). `/myvillage place chinese_courtyard_001` keeps working.
- **The voxel-walkability validator is the universal fix.** It applies to `chinese_courtyard`, `chinese_mansion`, and the small-courtyard unit. If it surfaces defects in `cultivation_sect_*` or `medieval_*`, those are fixed in a separate small change (not in scope here, but the validator runs on them).
- **江南 form overrides 北京 fidelity for `chinese_courtyard`.** Per user direction ("我要的其实是古代大户府邸而不是简单的北京四合院"), the 照壁侧立 / 倒座 alley / 垂花门 multi-cell fixes are adopted retroactively on the courtyard family. The original `rebuild-chinese-courtyard` 北京 one-进 fidelity is dropped where it conflicts with walkability.
- **Spec-compatible additions.** Existing `courtyard-compound` invariants (perimeter closed, gate on perimeter, water/planting structural, water routes around) are honored. The 照壁 side-standing is an *addition* (a 照壁 parcel node placed off-axis), not a removal of the 影壁 requirement.

## Goals / Non-Goals

**Goals:**

- A reviewer walks into a placed `chinese_mansion_NNN.nbt` from the street gate and reaches the 正房, 厢房, 楼阁, 假山, and 水池 — **3D voxel walkable end-to-end, validated by the new voxel-walkability validator, no jumping, no dead ends, no 影壁封轴**.
- The 6 `chinese_mansion_NNN` NBTs read as 江南大宅 (照壁侧立 + 敞厅 + 楼阁 + 山水花园), not as 北京 multi-进 or Chinese-skinned manor.
- 假山 has sub-block-precision geometry (太湖石-level皱褶 / 孔洞 / 悬挑), mod-owned, no external dependency.
- The `mod-decor-block-family` protocol is abstracted so the next decor block (pond-edge stone / lattice / pavement) is a 1-2-day add.
- 徽派天井大屋 is captured as a design-retention spec, not lost.
- `chinese_courtyard` regenerates with the same walkability fixes (照壁侧立 + side alley + multi-cell 垂花门 + transition stairs) — a reviewer no longer hits 影壁封轴 on the old family either.

**Non-Goals (explicitly deferred):**

- 4-进 compounds (`jin_count=4` with 第四进 花园深化 + 跨院). The `jin_count` master axis supports it, but the shipped library is `jin_count=3` only.
- 假山 蹬道 (climbing-path variant) — the rockery is base/slope-climbable (VoxelShape standable top), but no dedicated 蹬道 variant with stairs ascending the rockery surface. Deferred.
- 徽派天井大屋 implementation — the `huipai-tianjing-mansion` spec captures the form; no code, no NBT.
- Additional decor block classes (pond_stone, garden_lattice, garden_pavement, ridge_ornament) — protocol is defined, no instance other than 假山.
- Town-block-level integration of `chinese_mansion`. The mansion is large (~50×75 lot); the town-block tiler continues to use `generate_small_courtyard`.
- Java-side BlockEntity for 假山 — depth-2 (model + VoxelShape) is the choice; depth-3 (runtime bit editing) is a separate future capability if ever needed.

## Decisions

### D1: 假山 = depth-2 (sub-block model + VoxelShape), NOT depth-3 (runtime BlockEntity)

**Choice:** `myvillage:rockery_block` carries a fixed variant catalog (~30-50 variants by role), each with a pre-baked `.json` model and a pre-baked `VoxelShape`. No BlockEntity, no runtime bit data. Variants are placed by the Python generator based on a heightfield; players cannot edit individual bits.

**Alternatives considered:**

- *Depth-1 (整方块变体, texture-only).* Rejected: still reads as "cube with a rock texture" at close range; doesn't deliver 太湖石-level detail.
- *Depth-3 (BlockEntity + 16³ bits + dynamic BakedModel).* Rejected for this change: it is "build a mini Chisels & Bits" (~19 days, custom BakedModel, dynamic VoxelShape, NBT bloat, multiplayer sync risk, product overlap with C&B). The user's redirect ("mod adds its own items") makes depth-2 the natural fit — we ship pre-baked假山 as mod content, like `wall_plaque` ships pre-baked frame presets.
- *Hybrid (depth-2 generator + depth-3 editor).* Rejected: coupling generator content with an editor system entangles two product intents. Depth-3 is a separate future capability if ever needed.

**Rationale:** depth-2 reuses the `wall_plaque` architecture (one `DeferredRegister.Blocks` id, per-variant blockstate, pre-baked `assets/`) at ~7-8 days, with the only new mechanism being the merged-`VoxelShape` Java helper (standard vanilla API; stairs/slabs/fences all merge AABBs). The model catalog is generated offline by a Python tool that reuses `sect_mountain.py`'s heightfield.

### D2: 照壁侧立 (江南 form) replaces 影壁封轴 (北京 form) for both `chinese_mansion` and the regenerated `chinese_courtyard`

**Choice:** The screen wall parcel (`照壁` in 江南 / `影壁` in 北京 — same parcel node type, renamed) is placed **off-axis** — to one side of the gate, blocking the sightline to the main axis at an angle, never covering the axis. This applies to `chinese_mansion` (江南 native form) AND retroactively to `chinese_courtyard` (the regenerated 6 NBTs adopt the side-standing form).

**Alternatives considered:**

- *Keep 影壁 on-axis for `chinese_courtyard`, only side-stand in `chinese_mansion`.* Rejected: this leaves `chinese_courtyard` with the 影壁封轴 bug that the user reported as "堵住". The user's redirect is explicit that the 北京 fidelity is the wrong target; the walkability fix is more important than preserving the axis-centered 影壁.
- *Open a 2-tall doorway through the on-axis 影壁.* Rejected: it visually reads as "wall with a hole", not as the 江南 free-standing side panel. The side-standing form is both walkable AND visually correct for 江南.
- *Move 影壁 entirely outside the gate (街门外照壁).* Considered (real form, common in 江南), but rejected for this change: the outside-the-gate 照壁 is a separate street-furniture parcel that affects worldgen placement (it would be outside the compound lot). Defer to a future change.

**Rationale:** the side-standing 照壁 solves the 影壁封轴 walkability defect at the root, AND aligns with 江南 form. The 北京 axis-centered 影壁 was a design choice in `rebuild-chinese-courtyard`; the user has redirected, so it is dropped.

### D3: Architecture B (per-class block id) for the `mod-decor-block-family` protocol, not architecture C (single super-id)

**Choice:** Each decor class gets its own `myvillage:` block id: `myvillage:rockery_block`, future `myvillage:pond_stone`, `myvillage:garden_lattice`, etc. The protocol spec defines the shared contract (blockstate shape, asset layout, registration path, VoxelShape contract) but does not fold all classes into one `myvillage:decor_block`.

**Alternatives considered:**

- *Architecture A (single id, all variants).* Rejected: a single `rockery_block` id with 40 variants is already at the edge of blockstate-space tractability; folding 假山 + 花窗 + 铺地 into one id explodes it.
- *Architecture C (`myvillage:decor_block` + `family=rockery` blockstate property).* Rejected: VoxelShape cannot be fully data-driven (the Java side must merge AABBs per variant), so the "zero Java change to add a class" promise is false. Per-class ids are honest and each class's Java is small.

**Rationale:** mirrors `plaque-block-family` (four separate ids, one per geometry class). Adding a new decor class is one new id + one `Block` subclass + one model catalog — checklist-able in the protocol spec.

### D4: `_place_band_transition_stairs` replaces `_place_plinth_stairs`, bridging every `|Δy| ≥ 2` adjacent path pair

**Choice:** Rename `_place_plinth_stairs` to `_place_band_transition_stairs` and extend it: walk every pair of 4-neighbor path cells, and where the natural surface y differs by `≥ 2`, place N ascending `stone_brick_stairs[facing=<uphill>, half=bottom]` blocks bridging the gap. The current axis-only behavior is a special case (`Δy = plinth_h` at one axis cell).

**Alternatives considered:**

- *Keep axis-only, accept the off-axis cliffs.* Rejected: this is exactly the "41 dy=±3 transitions" defect the explore probe found. Players hit 3-block cliffs at every gallery-to-plinth boundary.
- *Slab ramp instead of stairs.* Rejected: visually weak, doesn't read as "step".
- *Raise the entire path to plinth_h, no transition.* Rejected: the outer yard path at y=-1 is a deliberate ground-level read; raising it floods the outer yard with a stone apron.

**Rationale:** the explore probe found 41 `|Δy|=3` transition pairs in `chinese_courtyard_001` alone, all unbridged. The generalized stair pass fixes them all with one algorithm. Each transition cell consumes 1 stair block; the visual is a "stepped approach" to the plinth, which reads correctly as a 台基 transition.

### D5: 水池 = freeform (value-noise binalization), not the strict `_rect` rectangle

**Choice:** `garden_pond` parcel uses a 2D value-noise binalization over the pond bbox (seed-driven, deterministic) to produce an irregular shoreline. `minecraft:water` is written at y=-1 inside the shoreline. The current `_bounded(_rect(...))` rectangle is kept for the small-courtyard unit (`generate_small_courtyard`) for byte-stability; the mansion's `garden_pond` uses the new freeform.

**Alternatives considered:**

- *Keep strict rectangle for mansion too.* Rejected: 江南园林 水池 is never rectangular; a rectangle reads as a swimming pool, not a 园林池.
- *Hand-authored polygon per mansion variant.* Rejected: 6 hand-polygons is art work, not algorithm work; the value-noise binalization is one Python function.

**Rationale:** the value-noise binalization is the 2D analog of `sect_mountain.heightfield` (both are deterministic noise over a bbox). One function, ~30 lines, captures the 江南 irregular shoreline.

### D6: VoxelShape standable top for `base` + `slope` rockery variants, NOT for `peak`

**Choice:** The `base` and `slope` rockery variants expose a flat standable top face in their `VoxelShape` (玩家可登 mid-mountain, walk on the rockery surface). The `peak` variants do not (陡峭, VoxelShape is solid-blocking to peak height).

**Alternatives considered:**

- *All variants solid-blocking.* Rejected: reduces 假山 to pure decoration; the user said "山水也要有" — being able to climb the rockery is part of 山水 experience.
- *All variants standable.* Rejected: a standable peak face lets the player stand on a 1-cell spike; visually wrong.
- *A dedicated 蹬道 variant with stairs ascending the surface.* Deferred (Non-Goals) — adds a variant catalog dimension and a stair-routing pass; the base/slope standable top is the cheap version.

**Rationale:** `base` and `slope` variants already need a `VoxelShape`; making the top standable is one `VoxelShapeArray` call. The 假山 becomes partially climbable without a dedicated 蹬道 system.

### D7: 徽派天井大屋 captured as design-retention spec only

**Choice:** The `huipai-tianjing-mansion` spec is written with full form vocabulary (天井 model, 堂 sequence, 四水归堂, 马头墙) but all requirements are `FUTURE:`-prefixed and no current validator covers them. No code, no NBT, no `chinese_mansion`-style library.

**Alternatives considered:**

- *Defer 徽派 entirely, no spec.* Rejected: the form was explored in detail during this change's discovery (the草图 comparison). Losing it means a future change redesigns from scratch.
- *Implement 徽派 alongside 江南 in this change.* Rejected: 徽派 requires a 天井 model (the院子 model doesn't generalize — 天井 is small, deep, roofed, with 四水归堂 water collection) which is a ground-layer rewrite; that's a separate change's worth of work.

**Rationale:** design retention costs ~1 day of spec writing and saves ~1 week of future redesign. The spec is honest about its status (no implementation).

### D8: Variant table for `chinese_mansion` — 6 visibly distinct 3-进 compounds

**Choice:** `select_variant(seed)` for `chinese_mansion` is a deterministic template table of 6 hand-authored rows, each pinning a distinct `(gate_form, garden_scale, tower_count, roof_grade, open_hall_bays)` tuple. `seed % 6` picks the row.

**Alternatives considered:**

- *Independent RNG per axis.* Rejected: same failure mode as the original `chinese_courtyard` (low-entropy clustering).
- *More than 6 rows.* Deferred: 6 NBTs is the project's compound-library default; expand if a future change wants 9 or 12.

**Rationale:** mirrors `rebuild-chinese-courtyard`'s D3 — hand-authored table of visibly distinct rows.

### D9: Voxel-walkability validator runs on ALL compound families, defects in `cultivation_sect`/`medieval` fixed separately

**Choice:** The new voxel-walkability validator (`validate_compound` + `validate_small_courtyard` extensions) runs on every compound family. If it surfaces defects in `cultivation_sect_*` or `medieval_*` (which were not the user's complaint but might have similar 3D cliffs), those are fixed in a **separate small change**, not this one. This change's byte-stability guard covers `cultivation_sect_*` and `medieval_*` only if the validator passes them clean.

**Alternatives considered:**

- *Suppress voxel-walkability for non-courtyard families.* Rejected: silent suppression defeats the point of the validator.
- *Fix any defect in any family in this change.* Rejected: scope creep; the user's complaint is about courtyards.

**Rationale:** the validator is universal; the fixes are scoped. Honest about what's in/out.

## Risks / Trade-offs

- **[NBT regeneration breaks worlds that placed old `chinese_courtyard_*`]** → The 6 NBTs regenerate with 照壁侧立 + side alley + multi-cell 垂花门 + transition stairs. Old placements keep old blocks (chunk NBT); new `/place` gets the new form. **Mitigation:** `CHANGELOG.md` documents the breaking regeneration; pre-1.0 mod, no migration promised.
- **[照壁侧立 visually changes the `chinese_courtyard` silhouette]** → The 照壁 moves from a 5-wide axis-centered wall to a side-standing panel. Reviewers who accepted the original 影壁 silhouette will see a different form. **Mitigation:** this is the user's explicit redirect; the new form is also more walkable. Document in CHANGELOG.
- **[假山 variant catalog aesthetic — 30-50 variants may look "samey"]** → Programmatic generation can produce variants that differ in detail but read similarly at thumbnail distance. **Mitigation:** D2's "5 手工微调 hero variants" (主峰, 副峰, 孤赏石, 池畔石, 门道石) provide visual anchors; the remaining 25-45 are programmatically generated fill. Acceptance rule: the 6 mansion NBTs differ on silhouette_score ≥ 15.
- **[`garden_pond` freeform water may produce unreachable islands]** → The value-noise binalization can isolate a cell inside the pond. **Mitigation:** the voxel-walkability validator catches this (`voxel_unreachable_endpoint`); the binalization post-process fills any 1-2 cell isolated land cells back to water.
- **[`_place_band_transition_stairs` may place stairs inside a building footprint]** → The generalized stair pass walks all path-adjacent pairs; if a path cell is adjacent to a building footprint, the stair could collide. **Mitigation:** the pass skips pairs where either cell is in `building_cells()`; the path-network endpoint rule already excludes door-front cells.
- **[voxel-walkability validator may surface pre-existing defects in `cultivation_sect_*` / `medieval_*`]** → D9 says these are fixed separately. **Mitigation:** the byte-stability guard for those families is relaxed to "voxel-walkability errors MUST be zero"; if errors surface, the guard fails and a separate fix change is filed.
- **[VoxelShape merge for `peak` variants may exceed vanilla's 64-AABB-per-shape soft limit]** → A complex peak variant with many sub-voxels can produce > 64 AABBs after merge, which vanilla handles but with collision-check cost. **Mitigation:** `rockery_models.py` runs a voxel-merge pass (greedy meshing in AABB space) to keep each variant's AABB count ≤ 32.
- **[`tower_house` 2-story interior may have stairwell collision with facade windows]** → The existing `multi-story-massing` stairwell placement avoids door + window bays, but a 江南 楼阁 facade is window-denser than the medieval original. **Mitigation:** the `tower_house` archetype declares its window bays explicitly so the stairwell placement honors them.

## Migration Plan

1. **Validator first** (`courtyard-voxel-walkability`): implement the 3D BFS validator + the `_place_band_transition_stairs` generalization. Run on `chinese_courtyard_001` — it will fail (the 影壁封轴 + 3-block cliffs). This proves the validator catches the user's complaint.
2. **照壁 + 倒座 + 垂花门 fixes** (`courtyard-compound` delta): side-standing 照壁, 倒座 side alley, 垂花门 multi-cell passage. Regenerate `chinese_courtyard_001..006.nbt`. Validator now passes on the courtyard family.
3. **Decor-block protocol** (`mod-decor-block-family`): spec only at this stage, no implementation. Defines the contract that 假山 will plug into.
4. **假山 first instance** (`garden-rockery`): `myvillage:rockery_block` Java registration + `rockery_models.py` offline generator + `rockery.py` runtime heightfield placement. Place a standalone 假山 in a test compound; verify variant rendering + VoxelShape standable top.
5. **`chinese_mansion` 3-进 layout** (`chinese-mansion-compound`): `_compute_yard_bands(jin_count=3)`, 江南特色 parcels (敞厅, 楼阁, 花园), variant table. Regenerate `chinese_mansion_001..006.nbt`. Validator passes.
6. **花园** (`garden-rockery` continued): `garden_pond` freeform water + `garden_rockery` heightfield placement + `garden_pavilion` 亭. Wire into the mansion's 花园 band.
7. **徽派 design retention** (`huipai-tianjing-mansion`): spec only.
8. **Validate and close**: full validation checklist; preview; build jar; staged manual acceptance places ≥2 `chinese_mansion` NBTs in-game + ≥1 `chinese_courtyard` to confirm walkability.

No runtime migration (no saved-world NBT migration is performed or promised).

## Open Questions

- **Should `chinese_courtyard` keep the 影壁 name (北京) or rename to 照壁 (江南) for the parcel node?** The node type is shared; the name carries semantic weight. Default: rename to `screen_wall` (neutral) for both, with the 北京/江南 distinction carried by `meta.form ∈ {jingbi, zhaobi}`. Defer to implementation review.
- **Should `garden_pavilion` reuse the cultivation pavilion's geometry verbatim, or get a 江南-specific 亭 form (e.g. circular plan)?** Default: reuse verbatim (square plan, 卷棚 roof); a circular-plan 江南 亭 is a follow-up. Defer to visual review.
- **Should the voxel-walkability validator enforce "every interior cell standable" (full coverage) or only "every endpoint reachable"?** Full coverage is stronger but may flag decorative non-walkable cells (e.g. a 园中树 base). Default: endpoints only; full coverage is a stricter future option. Defer to implementation review.
- **Should 4-进 sketch live in `chinese-mansion-compound` spec or `huipai-tianjing-mansion`?** It's a 江南 extension, so default: in `chinese-mansion-compound` as a future-extension section. Defer.
