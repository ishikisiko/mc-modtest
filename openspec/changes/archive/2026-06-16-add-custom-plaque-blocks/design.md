## Context

Today the mod registers **zero** custom blocks, items, or entities — `MyVillageMod` registers only commands and a palette-patch listener (`ModBlockFallback`). There is no `assets/myvillage/` directory. All signage in generated structures flows through the `SIGNAGE` style slot, which resolves to vanilla `wall_sign` / `standing_sign` (or external-mod canvas signs under the `full` profile). Two placement sites consume the slot:

- `ops.wall_hanging()` (called by `facade_detail_pass` when a volume has `entry_signage` meta) — places a single wall sign above the door.
- `_sect_gate_paifang_motif()` — places a single wall sign at `(x0+2, y0+2, z0)`, centered on the 5-wide paifang crossbeam.

Both produce a 1-block-wide, OCR-styled plank that reads poorly as a Chinese 匾额. The pipeline is otherwise healthy for this kind of extension: deterministic offline generation, palette-based `ModBlockFallback` substitution at place time, and a clean separation between build-gen ops, style profiles, and validation.

## Goals / Non-Goals

**Goals**
- Replace the signage placement at doorways and paifang centers with a real plaque system: multi-block wide, image-based calligraphic inscription, multiple curated frame styles spanning town→sect registers, both horizontal and vertical orientations, both wall-mounted and hanging.
- Make inscriptions drop-in extensible — adding a new name is one PNG + one JSON, zero code change.
- Preserve the pipeline's palette-based substitution model; honor `vanilla`/`full` profile semantics with the mod's own namespace treated as always-resolvable.

**Non-Goals**
- Per-character tile composition. Inscriptions are one PNG per plaque so the artist controls kerning, seals, and layout. (Re-evaluate if a large modpack demands a font-based path.)
- Editable in-game inscription text. The generator bakes the inscription at generation time; players cannot edit the calligraphy. (Use vanilla signs next to the plaque if editable text is needed.)
- Wind animation for hanging variants (招幌 / 酒幌). v1 is static. The block does not preclude a future `wind_phase` blockstate.
- Vertical 大字 (e.g. 2w×5h plaques). Real-world grand calligraphy plaques are horizontal; verticals stay single-column.
- Live-world terrain coupling for plaque placement. Plaques anchor to building/paifang geometry just like the current signage, not to world features.

## Decisions

### D1 — Four block ids, not one with `mount`+`orientation` state
Four separate ids: `myvillage:wall_plaque`, `myvillage:wall_plaque_vertical`, `myvillage:hanging_plaque`, `myvillage:hanging_plaque_vertical`. Rationale: the four have genuinely different geometries (back face flush vs open, top edge flat vs with hanging hardware), and splitting them keeps each blockstate file tractable. Adding new presets touches one id's blockstate, not a 16× combinatorial explosion. Cost: 4 Java registrations instead of 1, which is negligible.

**Alternative considered:** one id with `mount` ∈ {wall, hanging} × `orientation` ∈ {horizontal, vertical}. Rejected because the resulting blockstate file is `mount × orientation × row × col × facing × frame_preset` ≈ thousands of variants, and the four geometries share little model JSON.

### D2 — 2D multipart via `row` × `col` for horizontal 大字
Each horizontal plaque part is a 1×1×1 block. The multipart geometry is captured by two blockstate properties: `row` ∈ {top, middle, single, bottom} and `col` ∈ {left, center, single, right}. Sizes compose:
- 3w×1h: `row=single`, `col` over {left, center, right} → 3 parts
- 5w×1h: `row=single`, `col` over {left, center, center, center, right} → 5 parts (center repeats)
- 5w×2h 大字: `row` over {top, bottom}, `col` over {left, center, center, center, right} → 10 parts

Vertical plaques invert: `row` along the long axis, `col=single`. Rationale: this is the natural 2D generalization of bed/door multipart; supports all size variants from one blockstate namespace; asset count grows linearly with presets, not with size combinations.

The `col` labels are visual labels from the outside viewer's perspective, not raw world-coordinate order. For wall-mounted horizontal plaques, the generator maps `left`/`right` to the facade-visible left/right side for each facing so a north-facing front plaque still reads left-to-right when viewed from outside.

**Alternative considered:** separate block id per `(width, height, orientation)` combo. Rejected — ~16 ids for the size matrix, none sharing models.

### D3 — Inscriptions bake into full plaque block textures
Each inscription is tracked by a datum (`data/myvillage/painting_variant/inscription/<bucket>/<id>.json`) pointing at one PNG in `assets/myvillage/textures/painting/inscription/<bucket>/<id>.png`. The plaque asset generator reads the binding table and bakes that PNG into one full plaque texture per bound frame/mount/orientation under `assets/myvillage/textures/block/plaque/<mount>/<frame>/` (for example `horizontal_full.png` or `vertical_full.png`). Each multipart block model references the same full texture and uses its face UV window to sample only its own region. Rationale: the HD inscription PNGs remain decoupled from frame selection and preserve their resolution across the whole plaque face, while runtime placement uses normal block rendering instead of a hanging `minecraft:painting` entity that can fail survival checks and drop.

**Alternative considered:** composite the inscription into the frame block's model as a second texture layer (`textures: {base: frame, overlay: inscription}`). Rejected because it bakes the (frame × inscription) product into blockstate variants and blocks modpack extensibility.

**Alternative considered:** custom entity with `EntityRenderer` and a `texture_id` field. Rejected for v1 — adds client-side renderer registration and sync, breaks the "zero renderer code" posture, and the painting path already gives us per-instance texture selection.

### D4 — HD inscriptions, native frames
Inscription PNGs use 32–128 pixels per block of width (artist's choice, recommended 32 for town-tier, 64 for civic/sect, 128 for 大字 grand-sect). Frame multipart part textures use native 16×16 per part. Rationale: calligraphy needs fine stroke detail (飞白, brush texture) and important plaques benefit from extra headroom; the per-inscription tier lets the artist spend pixels where they matter without bloating the atlas for every rustic shop sign. Frame ornamentation is geometric and benefits from visual parity with surrounding 16×16 blocks. The resolution mismatch reads correctly — the inscription looks painted onto the frame surface.

### D5 — Single-image-per-plaque, not per-character
Each plaque name (e.g. "鸳鸯楼") is one PNG covering the full interior, with the artist controlling kerning, vertical position, signature seal. Rationale: most plaque names are 2–4 characters; per-character tiles would save few assets while removing the artist's control over composition. The library grows by `O(names × orientations)`; acceptable for the curated scope.

### D6 — Bucket organization by orientation × interior size
Inscription assets are grouped into buckets that match the frame's interior opening: `3w`, `4w`, `5w_1h`, `5w_2h`, `3h`, `4h`, `5h`. An inscription is only compatible with a frame preset whose interior matches its bucket. The binding JSON enforces this; mismatches fail validation. Rationale: structural compatibility is a hard constraint, not a preference — declaring it in the asset path makes wrong combinations physically impossible to reference.

### D7 — Data-driven archetype → plaque binding
A new `data/myvillage/plaque_bindings.json` maps each archetype to an ordered pool of `(frame_preset, orientation, mount, inscription_pool)` tuples. The generator picks deterministically by seed. Adding a new plaque combination is one JSON entry — no build-gen code change. Rationale: this is the same pattern as `style-profile` slot lists; preserves deterministic generation while making the binding table authorable by non-programmers.

### D8 — Hanging variants place vanilla `minecraft:chain`
When `mount=hanging`, the generator places `minecraft:chain[axis=y]` blocks above the top-left and top-right parts of the plaque (3 chains for 5w+). The plaque block's left/right part textures include ring/eyelet details at the top edge to visually receive the chain. The block itself does **not** structurally require chains (no `canSurvace` check) — players can swap chains for future modded hardware (tassels, silk cords) without the block popping off.

### D9 — `myvillage:` self-namespace exempt from foreign-namespace prohibition
Under the `vanilla` profile, `myvillage:` ids are always legal. Rationale: the prohibition targets *external* mods (which `vanilla` exists to strip); the mod's own namespace is always resolvable because the mod jar ships the assets. Concretely: `tools/validate_mod_block_fallbacks.py`, `tools/check_style_policy.py`, and `tools/validate_generated_structures.py` skip the foreign-namespace check for the literal prefix `myvillage:`. This is a one-line carve-out, not a structural change to profile semantics.

### D10 — `ModBlockFallback` extended for entity NBT (graceful degradation only)
The patcher today walks palette entries. It will gain a second pass that walks the structure's `entities` list and substitutes any painting whose `variant` is unresolvable with a fallback (default: a vanilla painting variant, or removal). In practice this rarely fires because the mod ships its own inscriptions, but the patcher must not crash on a missing `painting_variant`.

## Risks / Trade-offs

- **First custom blocks in the mod → unexpected friction** → Mitigation: the change ships 4 ids with deliberately small blockstate files; the multipart+model machinery is well-trodden (beds, doors, curtains). The asset pipeline (preview tool, generate_all) is updated in the same change.
- **Blockstate file size for 5w×2h variants** → Mitigation: the blockstate JSON is data, generated by a small Python helper from a manifest; not hand-maintained.
- **Painting entity in structure NBT** → new layer for `ModBlockFallback` and for the offline preview tool. Mitigation: extend the patcher and preview together; both already handle palette entry substitution, the entity pass is the same shape.
- **Inscription × orientation asset combinatorics** → "鸳鸯楼" needs a horizontal PNG and a vertical PNG. Mitigation: curated, bounded — every name in v1 has at most 2 PNGs. Acceptable cost of single-image composition.
- **Validator self-namespace carve-out widens the attack surface for accidentally shipping wrong ids** → Mitigation: the carve-out is the literal prefix `myvillage:` only; any other namespace under `vanilla` still fails. Add a unit test that asserts `minecraft:`-only output under `vanilla` for any non-`myvillage:` namespace.
- **HD inscription textures may clash visually with neighboring 16×16 blocks** → Mitigation: inscriptions sit on plaque faces that are themselves framed by the 16×16 frame textures; the visual transition is at the frame edge, not at neighboring blocks.

## Open Questions

- Should the four block ids share a single `Block` class with `propertiesFor` dispatch, or four trivial `Block` subclasses? *Proposal: one class, four registrations — the behavior is identical, only the registered id and default state differ.*
- Does `plaque_bindings.json` need to support weighted random within an archetype's pool, or is uniform-by-seed enough? *Proposal: uniform-by-seed for v1; revisit if curated weighting is requested.*
- Should the preview tool render baked plaque block textures directly or reconstruct inscriptions from the source PNGs? *Decision: render the blockstate-resolved plaque textures directly, with legacy painting overlays only when old structures still contain them.*
