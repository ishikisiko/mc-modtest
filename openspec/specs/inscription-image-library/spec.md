# Inscription Image Library

## Purpose

This spec captures the data-driven image library used for plaque inscriptions, including source PNG organization, baked full plaque textures, and compatibility rules between inscriptions and plaque frames.

## Requirements

### Requirement: Inscriptions are baked into full plaque block textures from PNG assets
Each plaque inscription SHALL have a registry datum under `data/myvillage/painting_variant/inscription/<bucket>/<id>.json` pointing at one PNG in `assets/myvillage/textures/painting/inscription/<bucket>/<id>.png`. The asset generator SHALL bake that PNG into one full plaque texture for each bound frame/mount/orientation, using `assets/myvillage/textures/block/plaque/<mount>/<frame>/horizontal_full.png` for horizontal plaques and `vertical_full.png` for vertical plaques. Each generated plaque part model SHALL reference that same full texture and SHALL select its own rectangular region with the face UV coordinates. During baking, the generator SHALL preserve the full source inscription canvas, fit it inside the full plaque interior with margins, and tint strokes against the plaque surface for readable contrast. It SHALL NOT downsample the calligraphy into per-block 16x16 tile textures or leave dark strokes invisible on dark plaque surfaces. Generated structures SHALL NOT place `minecraft:painting` entities for plaque inscriptions.

#### Scenario: An inscription is loaded from data
- **WHEN** the mod loads `data/myvillage/painting_variant/inscription/4w/zang_jing_ge.json`
- **THEN** the painting variant SHALL declare `width=4`, `height=1`, and `asset_id=myvillage:inscription/4w/zang_jing_ge`
- **AND** the corresponding PNG SHALL exist under `assets/myvillage/textures/painting/inscription/4w/`.

#### Scenario: A plaque bakes its inscription
- **WHEN** a generator places a plaque with `frame=sect_scripture_ornate_4w` and `inscription=zang_jing_ge`
- **THEN** the placed `myvillage:wall_plaque` blocks SHALL reference blockstate-resolved models whose front faces sample a shared full plaque texture containing the `zang_jing_ge` calligraphy
- **AND** no `minecraft:painting` entity SHALL be emitted for the inscription.

#### Scenario: A dark inscription source is baked onto a dark plaque
- **WHEN** an inscription PNG contains dark calligraphy strokes and the bound plaque preset has a dark wood or lacquer surface
- **THEN** the baked full plaque block texture SHALL retint the strokes to a high-contrast plaque-appropriate color
- **AND** the calligraphy SHALL remain visible without depending on the block behind the plaque.

### Requirement: Inscriptions are bucketed by orientation × interior size
Inscription assets SHALL be grouped into buckets matching the frame interior: `3w`, `4w`, `5w_1h`, `5w_2h`, `3h`, `4h`, and `5h`. An inscription in a given bucket SHALL only be paired with a frame preset whose interior matches. The binding table SHALL enforce this constraint; mismatches SHALL fail validation.

#### Scenario: A horizontal 3-char name is paired with a 3w frame
- **WHEN** the binding table pairs inscription `yuan_yang_lou` (in bucket `4w`) with frame `town_inn_lacquered_4w`
- **THEN** the bucket and interior match
- **AND** the pairing SHALL be accepted.

#### Scenario: A bucket mismatch is rejected
- **WHEN** the binding table pairs an inscription in bucket `4w` with a frame whose interior is `5w_1h`
- **THEN** validation SHALL fail with a `bucket_mismatch` error
- **AND** generation SHALL refuse to emit the pairing.

### Requirement: Inscription PNGs use HD resolution with a per-inscription tier, frames do not
Inscription PNGs SHALL use at least 32 pixels per block of width and at most 128 pixels per block of width. The artist MAY pick any integer px-per-block value in that range per inscription; the recommended baselines are 32 px/block for town-tier presets (rustic shops, market stalls, wayside inns), 64 px/block for civic and sect presets (scripture pavilion, treasure pavilion, lord manor), and 128 px/block for 大字 grand-sect plaques (main hall, sect gate centerpiece). The aspect ratio of the PNG SHALL exactly match the bucket's block-dimension ratio. Inscription PNGs SHALL NOT use the native 16×16 block resolution. The resolution mismatch with the 16×16 frame textures is intentional; it lets calligraphic strokes render crisply while the frame remains visually consistent with neighboring 16×16 building blocks.

#### Scenario: A town-tier inscription PNG is inspected
- **WHEN** an inscription PNG for a 3w or 4w town-tier plaque is loaded
- **THEN** the PNG SHALL be at least 96 (3w at 32 px/block) or 128 (4w at 32 px/block) pixels wide
- **AND** the PNG MAY be up to 384 (3w at 128) or 512 (4w at 128) pixels wide
- **AND** the aspect ratio SHALL match the bucket's block dimensions (3:1 or 4:1).

#### Scenario: A grand-sect 大字 inscription PNG is inspected
- **WHEN** an inscription PNG for a 5w_2h 大字 plaque is loaded
- **THEN** the PNG SHALL be between 160×64 (32 px/block) and 640×256 (128 px/block)
- **AND** the aspect ratio SHALL be exactly 5:2.

#### Scenario: A vertical inscription PNG is inspected
- **WHEN** an inscription PNG for a 4h vertical plaque is loaded
- **THEN** the PNG SHALL be between 32×128 (32 px/block) and 128×512 (128 px/block)
- **AND** the aspect ratio SHALL be exactly 1:4.

### Requirement: Inscriptions are single-image-per-plaque, not per-character
Each plaque name (e.g. "鸳鸯楼") SHALL be one PNG covering the full interior of the plaque. The PNG SHALL NOT be split into per-character tiles. The same name in horizontal and vertical orientations SHALL be two separate PNGs because composition, kerning, and seal placement differ.

#### Scenario: A horizontal and a vertical variant of the same name
- **WHEN** the library ships "鸳鸯楼" in both horizontal and vertical orientations
- **THEN** there SHALL be two PNGs: `4w/yuan_yang_lou.png` and `4h/yuan_yang_lou.png`
- **AND** the two SHALL be independent compositions, not rotations of each other.

### Requirement: Inscriptions are extensible through data plus deterministic asset regeneration
Adding a new inscription for an existing frame geometry SHALL require a new `painting_variant` JSON, a new PNG under the appropriate bucket, and a binding-table reference followed by deterministic plaque asset regeneration. It SHALL NOT require new Java code when the frame geometry already exists. Removing an inscription SHALL require deleting its JSON and PNG and removing its references from the binding table, which validation will flag.

#### Scenario: A modpack adds an inscription
- **WHEN** a modpack's datapack adds `data/myvillage/painting_variant/inscription/4w/my_custom_name.json` and the resource pack adds the corresponding PNG
- **THEN** the inscription SHALL be referenceable in `plaque_bindings.json`
- **AND** rerunning `tools/buildgen/gen_plaque_assets.py` SHALL bake the inscription into the bound plaque block textures without Java code changes.

#### Scenario: A reference to a removed inscription is caught
- **WHEN** `plaque_bindings.json` references an inscription id whose JSON or PNG has been deleted
- **THEN** validation SHALL fail with `missing_inscription`
- **AND** the report SHALL name the missing id.

### Requirement: Inscriptions and frames are decoupled catalogs
The frame catalog (curated, eight presets) and the inscription library (open, image-based) SHALL be independent registries. A binding entry, not the catalogs themselves, SHALL pair them. The same frame preset SHALL be pairable with many inscriptions; the same inscription SHALL be pairable with many frames (within bucket compatibility).

#### Scenario: One frame pairs with many inscriptions
- **WHEN** the `sect_scripture_ornate_4w` frame is paired with `zang_jing_ge`, `scripture_pavilion_name`, and `martial_pavilion_name`
- **THEN** all three pairings SHALL be valid
- **AND** each SHALL produce a visually distinct plaque.

#### Scenario: One inscription pairs with many frames
- **WHEN** the inscription `yuan_yang_lou` (bucket `4w`) is paired with both `town_inn_lacquered_4w` and `tavern_signboard_4w`
- **THEN** both pairings SHALL be valid
- **AND** both SHALL produce a visually distinct plaque.
