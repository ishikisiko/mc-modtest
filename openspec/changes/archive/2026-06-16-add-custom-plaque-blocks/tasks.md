## 1. Frame Catalog & Manifest

- [x] 1.1 Define the eight-preset frame catalog manifest at `tools/buildgen/plaque_frames.json` covering `town_shop_wood_3w`, `town_inn_lacquered_4w`, `town_notice_board_3w`, `tavern_signboard_4w`, `sect_simple_pine_4w`, `sect_scripture_ornate_4w`, `lord_manor_heraldry_5w`, `sect_treasure_gilded_5w_2h`, each declaring horizontal size, vertical size (or null), interior bucket, and material/ornament notes
- [x] 1.2 Write `tools/buildgen/gen_plaque_assets.py` to consume the manifest and emit (a) the asset list for frame textures, (b) the blockstate variants for each plaque id, (c) the model JSONs, and (d) a printable asset checklist for the artist
- [x] 1.3 Document the per-preset visual brief (edge profile, material, corner ornament, hanging hardware) in `docs/ai-kb/plaque-frame-brief.md` so the artist has one self-contained reference

## 2. Java Block Registration

- [x] 2.1 Create `src/main/java/com/example/myvillage/block/ModBlocks.java` with a `DeferredRegister.Blocks` registering `wall_plaque`, `wall_plaque_vertical`, `hanging_plaque`, `hanging_plaque_vertical`, each with properties `facing` (N/S/E/W), `frame` (preset id), `row` (top/middle/single/bottom), `col` (left/center/single/right)
- [x] 2.2 Implement a single `Block` subclass (or `BlockBehaviour` config) used by all four ids; `canSurvive` returns true unconditionally so hanging plaques do not pop off when chains are removed
- [x] 2.3 Wire the `DeferredRegister` into `MyVillageMod` (constructor + mod event bus), and verify the four ids appear in the `BuiltInRegistries.BLOCK` after load
- [x] 2.4 Add lang entries (`en_us.json`) for the four block ids and the eight frame preset display names

## 3. Frame Assets

- [x] 3.1 Generate the four blockstate JSONs (`wall_plaque.json`, `wall_plaque_vertical.json`, `hanging_plaque.json`, `hanging_plaque_vertical.json`) from the manifest, enumerating every `(frame, row, col, facing)` variant
- [x] 3.2 Generate the per-part model JSONs under `assets/myvillage/models/block/plaque/<mount>/<preset>/<part>.json` referencing the 16×16 frame textures
- [x] 3.3 Commission or stub the 16×16 frame PNGs under `assets/myvillage/textures/block/plaque/<mount>/<preset>/<part>.png` (start with stubs so the pipeline runs; refine visually in §11)
- [x] 3.4 Add ring/eyelet detail to the top edge of `hanging` mount's `top_left` and `top_right` (and `top_center` for 5w+) parts so the chains visually attach

## 4. Inscription Library v1

- [x] 4.1 Create the bucket directory layout under `data/myvillage/painting_variant/inscription/<bucket>/` and `assets/myvillage/textures/painting/inscription/<bucket>/` for buckets `3w`, `4w`, `5w_1h`, `5w_2h`, `3h`, `4h`, `5h`
- [x] 4.2 Write starter `painting_variant` JSONs for the names referenced in §5 bindings (at minimum: `yuan_yang_lou` 4w + 4h, `za_huo_pu` 3w, `zang_jing_ge` 4w, `lin_lang_ge` 5w_2h, `tavern_inn` 4w, `lord_manor_house` 5w_1h). The `scripture_pavilion` archetype reuses `zang_jing_ge` directly (the name is the 1:1 translation of "scripture pavilion"); no separate scripture-pavilion inscription is needed for v1.
- [x] 4.3 Land the v1 HD inscription PNGs delivered in `calligraphy_signs.zip` (repo root) into `src/main/resources/assets/myvillage/textures/painting/inscription/<bucket>/`. The zip contains seven unique files spanning five buckets; sizes range from 96×32 (3w @ 32 ppb) to 640×256 (5w_2h @ 128 ppb). Full manifest:
  - `3w/za_huo_pu.png` (96×32)
  - `4w/yuan_yang_lou.png` (256×64), `4w/zang_jing_ge.png` (256×64), `4w/tavern_inn.png` (128×32)
  - `4h/yuan_yang_lou.png` (64×256)
  - `5w_1h/lord_manor_house.png` (320×64)
  - `5w_2h/lin_lang_ge.png` (640×256)
  - **NOTE**: the zip also contains `8w/scripture_pavilion_name.png`, but it is a byte-identical duplicate of `4w/zang_jing_ge.png` (same MD5) and the `8w/` folder is a mislabel (the pixel aspect ratio 4:1 confirms 4w geometry, not 8w). Skip this file on extraction; it is not part of the v1 set.
- [x] 4.4 Verify each landed PNG's aspect ratio exactly matches its bucket's block-dimension ratio (3:1, 4:1, 5:1, 5:2, 1:3, 1:4, 1:5); reject on mismatch
- [x] 4.5 Document the calligraphy style guide (calligraphic register per archetype, seal placement, vertical-vs-horizontal composition rules) in `docs/ai-kb/plaque-inscription-style.md`

## 5. Plaque Bindings

- [x] 5.1 Create `data/myvillage/plaque_bindings.json` with at least one entry per plaque-bearing archetype (`cultivation_shop`, `cultivation_inn`, `scripture_pavilion`, `treasure_pavilion`, `sect_gate`/paifang, `tavern`, `lord_manor`), each declaring `frame`, `orientation`, `mount`, and `inscription_pool`. The `scripture_pavilion` entry SHALL reference `zang_jing_ge` (the canonical scripture-pavilion name) — no separate inscription asset is needed for v1.
- [x] 5.2 Write `tools/buildgen/plaque_bindings.py` to load the binding table, validate bucket compatibility at load time, and expose `binding_for(archetype, rng) -> Optional[Binding]` returning a deterministic pick from the pool

## 6. Build-Gen Plaque Ops

- [x] 6.1 Add `ops.place_wall_plaque(grid, style, rng, vol, wall, along, y, binding)` to `tools/buildgen/ops.py` placing the multipart `myvillage:wall_plaque` (or `_vertical`) blocks per the binding's `frame`/`orientation`/`mount`; inscription pixels are baked into the block textures
- [x] 6.2 Add `ops.place_hanging_plaque(grid, style, rng, vol, anchor_pos, facing, binding)` placing the multipart `myvillage:hanging_plaque` (or `_vertical`) blocks plus vanilla `minecraft:chain[axis=y]` blocks above the top-left and top-right parts (and top-center for 5w+)
- [x] 6.3 Ensure the build-gen grid does not emit inscription painting entities; the NBT serializer should ship plaque blockstates only for plaque inscriptions

## 7. Build-Gen Passes & Paifang Motif

- [x] 7.1 Update `facade_detail_pass` in `tools/buildgen/passes.py` so when `entry_signage=true` for a volume, it consults `plaque_bindings.binding_for(archetype, rng)`; if a binding is returned, call `ops.place_wall_plaque`/`ops.place_hanging_plaque`, otherwise fall through to the existing `ops.wall_hanging` against `SIGNAGE`
- [x] 7.2 Update `_sect_gate_paifang_motif` in `tools/buildgen/ops.py` to consult `plaque_bindings` for the sect gate archetype; if a binding is returned, call `ops.place_hanging_plaque` centered on the crossbeam (replacing the current single wall_sign at `(x0+2, y0+2, z0)`); otherwise skip the central tablet and emit a warning
- [x] 7.3 Add archetype plumbing so the build-gen pipeline knows which archetype each generated building belongs to (for binding lookup) — confirm this is already passed through or thread it through if missing

## 8. ModBlockFallback Extension

- [x] 8.1 Extend `ModBlockFallback.patchPalettes` (or add a sibling method) to walk the structure NBT's `entities` list and substitute any `minecraft:painting` whose `variant` resolves to a missing `painting_variant` with a default variant (or remove the entity), logging a one-shot warning
- [x] 8.2 Verify `resolveBlockState` already accepts `myvillage:` ids (it should, since the mod registers them); if not, ensure the registry check in `BuiltInRegistries.BLOCK.containsKey(id)` succeeds for the four plaque ids at runtime

## 9. Validator Updates

- [x] 9.1 Update `tools/validate_generated_structures.py` to permit `myvillage:` namespace under both `vanilla` and `full` profiles (the carve-out is the literal prefix `myvillage:`)
- [x] 9.2 Update `tools/validate_mod_block_fallbacks.py` so `myvillage:` ids in palettes are accepted without requiring a fallback entry (since the mod always ships them); non-`myvillage` non-`minecraft` ids still require fallbacks as today
- [x] 9.3 Update `tools/check_style_policy.py` for the same `myvillage:` self-namespace carve-out
- [x] 9.4 Add plaque signature rules to `tools/validate_generated_structures.py`: `scripture_pavilion_*`, `treasure_pavilion_*`, and `sect_gate_*`/`paifang_*` must contain at least one `myvillage:.*plaque` block and must not contain `myvillage:inscription/...` painting entities; violations fail with `plaque_signature`
- [x] 9.5 Add `tools/validate_plaque_bindings.py` to traverse `plaque_bindings.json` and enforce frame-catalog existence, inscription existence, bucket compatibility, and valid `mount` values (reporting `unknown_frame_preset`, `missing_inscription`, `bucket_mismatch`, `invalid_mount`)
- [x] 9.6 Wire `validate_plaque_bindings.py` into the acceptance prep list in `AGENTS.md` and `README.md`

## 10. Preview Tool Updates

- [x] 10.1 Extend `tools/preview_structure.py` to render the four plaque block ids by reading their blockstate-resolved models and 16×16 frame textures
- [x] 10.2 Extend `tools/preview_structure.py` to render plaque blocks from their blockstate-resolved baked plaque textures
- [x] 10.3 Keep legacy support for `minecraft:painting` entities referencing `myvillage:inscription/...`, including a `missing_inscription_asset` flag when an old entity references a missing PNG

## 11. Regenerate, Refine, Validate Structures

- [x] 11.1 Run `python3 tools/generate_all_structures.py` to regenerate structures containing plaque-bearing archetypes; confirm the new NBTs contain plaque blockstates and no inscription painting entities
- [x] 11.2 Run `python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure` (full profile) and confirm no `forbidden_mod_blocks` errors for `myvillage:` ids
- [x] 11.3 Run `python3 tools/validate_generated_structures.py ... --profile vanilla` and confirm `myvillage:` plaque ids pass while any stray external-mod ids still fail
- [x] 11.4 Run `python3 tools/validate_mod_block_fallbacks.py` and `python3 tools/check_style_policy.py` and confirm the self-namespace carve-out is active
- [x] 11.5 Run `python3 tools/validate_plaque_bindings.py` and confirm all binding entries pass
- [x] 11.6 Run `python3 tools/validate_compound_library.py --group cultivation_sect --count 2` and `python3 tools/validate_civic_library.py` to confirm plaque-required archetypes pass the new plaque_signature gate
- [x] 11.7 Refine the stub frame PNGs (§3.3) with final art (the v1 inscription PNGs are already delivered via `calligraphy_signs.zip` and only need landing per §4.3), iterating against `python3 tools/preview_structure.py --all` until visual review is satisfactory

## 12. Build & Docs

- [x] 12.1 Run `./gradlew build` and confirm the jar packs the four block ids, the blockstate JSONs, the model JSONs, the frame PNGs, the painting_variant JSONs, and the inscription PNGs
- [x] 12.2 Update `README.md` command/usage list to mention plaque placement (no new commands, but the existing `/myvillage place` and `/myvillage gallery` outputs now include plaque-bearing structures)
- [x] 12.3 Update `AGENTS.md` acceptance prep list to include `python3 tools/validate_plaque_bindings.py` and note the `myvillage:` self-namespace carve-out in validator behavior
- [x] 12.4 Update `openspec/specs/` if any baseline specs need post-archive tweaks (deferred to archive step)

## 13. Version Bump

- [x] 13.1 Bump `gradle.properties` `mod_version` from `0.7.1` to `0.8.0` (large feature: four new block ids + new asset system + image library)
- [x] 13.2 Update `src/main/resources/META-INF/neoforge.mods.toml` version to match `0.8.0`
- [x] 13.3 Update `README.md` jar-name examples from `0.7.1` to `0.8.0`
- [x] 13.4 Add a `## 0.8.0` section to `CHANGELOG.md` summarizing: four plaque block ids, eight-preset frame catalog, image-based inscription library, data-driven archetype→plaque bindings, `myvillage:` self-namespace validator carve-out, hanging-plaque chain integration, and preview support for baked plaque block textures
