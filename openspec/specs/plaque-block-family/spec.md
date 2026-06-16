# Plaque Block Family

## Purpose

This spec captures the custom multipart plaque block family used for wall-mounted and hanging Chinese plaque signs, including block ids, row/column composition, frame presets, and hanging-chain behavior.

## Requirements

### Requirement: Four multipart plaque block ids cover horizontal/vertical × wall/hanging
The mod SHALL register exactly four custom block ids: `myvillage:wall_plaque` (horizontal wall-mounted), `myvillage:wall_plaque_vertical` (vertical wall-mounted), `myvillage:hanging_plaque` (horizontal hanging), and `myvillage:hanging_plaque_vertical` (vertical hanging, for 招幌 / 酒幌). Each id SHALL be registered via `DeferredRegister.Blocks` with its own blockstate file; the four SHALL NOT be folded into one id with `mount` and `orientation` properties.

#### Scenario: The four ids are registered
- **WHEN** the mod is loaded
- **THEN** `myvillage:wall_plaque`, `myvillage:wall_plaque_vertical`, `myvillage:hanging_plaque`, and `myvillage:hanging_plaque_vertical` SHALL all be registered blocks
- **AND** each SHALL have a blockstate file scoped to its own id.

#### Scenario: A horizontal plaque is placed on a wall
- **WHEN** a generator places a `myvillage:wall_plaque` block with `col=left, row=single, facing=north, frame=town_shop_wood_3w`
- **THEN** the block SHALL render with the town_shop_wood_3w left-edge frame texture
- **AND** the blockstate SHALL be valid for `myvillage:wall_plaque`.

#### Scenario: A vertical plaque is placed beside a door
- **WHEN** a generator places a `myvillage:wall_plaque_vertical` block with `row=top, col=single, facing=east, frame=town_inn_lacquered_4h`
- **THEN** the block SHALL render with the town_inn_lacquered vertical top frame texture
- **AND** the blockstate SHALL be valid for `myvillage:wall_plaque_vertical`.

### Requirement: Horizontal plaque blocks support 2D multipart via row × col
Horizontal plaque ids (`wall_plaque`, `hanging_plaque`) SHALL expose two blockstate properties `row` ∈ {top, middle, single, bottom} and `col` ∈ {left, center, single, right}. Size variants SHALL compose from these properties: 3w×1h uses `row=single` with `col` cycling {left, center, right}; 4w×1h and 5w×1h extend the `col=center` repeats; 5w×2h 大字 uses both `row` ∈ {top, bottom} and `col` ∈ {left, center, center, center, right}. The `col` values SHALL describe visual left-to-right order as seen from the plaque's exterior-facing side; generators SHALL NOT assume increasing x/z world coordinates always equal visual left-to-right order.

#### Scenario: A 3w×1h horizontal plaque is composed
- **WHEN** a generator places a 3w×1h plaque at positions x, x+1, x+2
- **THEN** the three blocks SHALL have `col=left`, `col=center`, `col=right` respectively
- **AND** all three SHALL have `row=single`.

#### Scenario: A 5w×2h 大字 plaque is composed
- **WHEN** a generator places a 5w×2h 大字 plaque across 10 cells
- **THEN** the top-row blocks SHALL have `row=top` with `col` cycling left/center/center/center/right
- **AND** the bottom-row blocks SHALL have `row=bottom` with the same `col` cycle.

#### Scenario: A north-facing wall plaque preserves visual reading order
- **WHEN** a generator places a horizontal wall plaque on a north-facing facade
- **THEN** the `col=left` block SHALL occupy the viewer-visible left side of the plaque even if that is the higher x-coordinate side
- **AND** the full inscription texture SHALL read in the same order as the source PNG when viewed from outside the building.

### Requirement: Vertical plaque blocks use single-column multipart
Vertical plaque ids (`wall_plaque_vertical`, `hanging_plaque_vertical`) SHALL use `row` ∈ {top, middle, single, bottom} with `col=single` for all parts. Vertical plaques SHALL NOT support 大字 (no 2w variants); the maximum vertical size SHALL be 1w×5h.

#### Scenario: A 1w×4h vertical plaque is composed
- **WHEN** a generator places a 4-tall vertical plaque
- **THEN** the four blocks SHALL have `row=top`, `row=middle`, `row=middle`, `row=bottom`
- **AND** all four SHALL have `col=single`.

#### Scenario: A 2w vertical plaque is rejected
- **WHEN** a generator or validator encounters a vertical plaque referencing a 2-wide vertical layout
- **THEN** it SHALL fail with a clear error
- **AND** no such blockstate SHALL exist in the vertical plaque blockstate files.

### Requirement: An eight-preset frame catalog spans town, civic, and sect registers
The mod SHALL ship exactly eight curated frame presets: `town_shop_wood_3w`, `town_inn_lacquered_4w`, `town_notice_board_3w`, `tavern_signboard_4w`, `sect_simple_pine_4w`, `sect_scripture_ornate_4w`, `lord_manor_heraldry_5w`, and `sect_treasure_gilded_5w_2h`. Each preset SHALL define its horizontal variant; six of the eight (excluding `town_notice_board_3w` and `lord_manor_heraldry_5w`) SHALL also define a vertical variant. Each preset's frame textures SHALL render a visually distinct combination of edge profile, ornamentation, and material.

#### Scenario: The frame catalog is enumerated
- **WHEN** the frame catalog is loaded
- **THEN** the eight named presets SHALL all be present
- **AND** each preset SHALL declare its horizontal size and (where applicable) its vertical size.

#### Scenario: A preset has only a horizontal variant
- **WHEN** the `town_notice_board_3w` or `lord_manor_heraldry_5w` preset is referenced with `orientation=vertical`
- **THEN** the request SHALL fail with a no-such-variant error
- **AND** generation SHALL fall back to a horizontal placement or skip the plaque entirely.

### Requirement: Hanging plaque placement places vanilla chains above
When a generator places a plaque with `mount=hanging`, it SHALL also place `minecraft:chain[axis=y]` blocks directly above the top-left and top-right parts of the plaque. For plaques 5w or wider, it SHALL place an additional chain above the top-center part. The plaque block itself SHALL NOT require chains (or any block) above for structural support; `canSurvive` SHALL return true regardless of neighbors so players can remove or swap chains without the plaque popping off.

#### Scenario: A 4w hanging plaque is placed
- **WHEN** a generator places a 4w hanging plaque
- **THEN** two `minecraft:chain` blocks SHALL be placed above the left-most and right-most plaque parts
- **AND** no chain SHALL be placed above center parts.

#### Scenario: A 5w hanging plaque is placed
- **WHEN** a generator places a 5w hanging plaque
- **THEN** three `minecraft:chain` blocks SHALL be placed above the left-most, center, and right-most plaque parts.

#### Scenario: A player removes the chains
- **WHEN** a player breaks the `minecraft:chain` blocks above a placed hanging plaque
- **THEN** the plaque block SHALL remain in place
- **AND** no `BlockUpdate` SHALL cause the plaque to drop.

### Requirement: Frame part textures use native 16×16 resolution
Frame multipart textures SHALL be 16×16 PNG files per part. They SHALL NOT use HD resolutions. This preserves visual parity with neighboring 16×16 building blocks (walls, beams, doors).

#### Scenario: A frame texture is inspected
- **WHEN** the texture file for any plaque frame part is loaded
- **THEN** it SHALL be a 16×16 pixel PNG.

### Requirement: Frame multipart assets are organized by preset, mount, and part
Frame textures SHALL live under `assets/myvillage/textures/block/plaque/<mount>/<preset>/<part>.png` where `<mount>` is `wall` or `hanging`, `<preset>` matches the catalog name, and `<part>` is one of `top_left`, `top_center`, `top_right`, `bottom_left`, `bottom_center`, `bottom_right`, `single_left`, `single_center`, `single_right`, plus the vertical-only equivalents `top`, `middle`, `bottom`. Block model JSON and blockstate JSON SHALL be generated from a manifest by a build helper so the asset tree is reproducible.

#### Scenario: The asset tree is populated
- **WHEN** the manifest is processed
- **THEN** every (preset, mount, part) combination referenced by the catalog SHALL produce a corresponding PNG, model JSON, and blockstate variant entry
- **AND** no orphan asset SHALL remain.
