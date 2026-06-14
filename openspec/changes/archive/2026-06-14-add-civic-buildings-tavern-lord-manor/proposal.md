## Why

The Western building library currently covers housing (`small_house`,
`medium_house`, `big_house`), commercial (`small_shop`, `medium_shop`), and
industry (`blacksmith`), but has no public/civic buildings. The project brief
in `README.md` and the `building-generation` spec both call out "functional
buildings such as shops, workshops, storage, markets, services, and civic
pieces" as a forward target. Taverns and the lord's manor (城主府) are the two
highest-leverage civic archetypes for town generation: they anchor a town
square visually and provide recognizable functional role blocks. Adding them
now also exercises the multi-story pipeline with two new massing shapes
(mezzanine great hall and attached tower) that will generalize to future
civic pieces.

## What Changes

- Introduce a new **civic archetype family** with two archetypes:
  - `tavern`: 2-story great-hall building with a **mezzanine** (half-floor)
    over part of the ground floor, optional stable annex, brewing/barrel
    interior, and upstairs inn rooms.
  - `lord_manor`: 2-story civic residence with an **attached tower volume**
    rising one to two stories above the main roof, private quarters with
    bed/chest, council chamber with lectern/bookshelf/bell.
- Emit them through a **dedicated civic library loop**, parallel to the
  Chinese courtyard compound generator, rather than appending to the
  medieval `building-library` loop. Variant counts: `tavern_001..005` (5),
  `lord_manor_001..003` (3).
- Add new **massing node types**:
  - `great_hall_volume` carrying mezzanine metadata (`covers` half-plane).
  - `tower_volume` carrying height-above-main metadata.
  - `stable_annex` reusing the existing shed node shape with a hay floor meta.
- Add a new **`mezzanine_floor_pass`** between `structure_pass` and
  `floor_slab_pass` that lays the partial slab and the matching half-floor
  opening on the upper story.
- Add new **interior zone kinds**: `tavern_hall`, `tavern_inn`,
  `town_chamber`, `town_foyer`, `stable`. Each has its own furniture rules.
- Add a new **material slot** `INTERIOR_CIVIC` carrying
  `brewing_stand`, `lectern`, `bell`, `bookshelf`, `cauldron`, `flower_pot`.
- Add a new **material slot** `FURNITURE` carrying `bed`, `chest` (empty,
  no loot NBT), and reuse `barrel` from `INTERIOR_STORAGE`.
- Add a new **decorative slot** `SIGNAGE` carrying `standing_sign` and
  `wall_sign`, plus `HERALDRY` carrying `standing_banner` / `wall_banner`
  for lord manor tower/gate display.
- **Modify** `medieval_village` style profile: remove `bed`, `chest`,
  `banner`, `sign` from `forbidden_blocks`. All other forbidden entries
  (quartz, concrete, terracotta, warped/crimson, copper, gold, netherite,
  spawner, command_block, shulker, jukebox, beacon, iron_block) remain
  forbidden to preserve the medieval palette.
- Extend the **validator** with civic signature-block rules:
  - Tavern must contain `brewing_stand` OR at least 3 `barrel` blocks.
  - Lord manor must contain `bell` OR `lectern`.
- Extend the **exporter** so civic structures get `place/<id>.mcfunction`
  and a `gallery/civic.mcfunction`, mirroring the Chinese courtyard export
  pattern. The civic gallery column appears in `/myvillage gallery` after
  the Chinese courtyard column.
- Update **README**, **AGENTS.md**, and the OpenSpec specs in the same
  change to reflect new structure IDs, new commands, and the unblocked
  blocks.

No **BREAKING** changes: existing archetypes, styles, generated NBTs, and
commands continue to work unchanged.

## Capabilities

### New Capabilities

- `civic-archetype-family`: A new procedural building family covering
  public/civic buildings. Initial members are `tavern` and `lord_manor`,
  emitted by a dedicated library loop separate from the medieval housing/
  commercial library and the Chinese compound generator. Establishes the
  civic massing vocabulary (great hall with mezzanine, attached tower,
  stable annex), civic interior zones, and civic signature-block
  validation.

### Modified Capabilities

- `building-generation`: Adds `tavern` and `lord_manor` archetypes to the
  supported set, defines the civic library loop variant counts and tier
  plan, and clarifies that civic archetypes are emitted by their own loop
  rather than the medieval library loop.
- `multi-story-massing`: Extends multi-story semantics to cover partial
  mezzanine floors (half-slab with aligned half-opening on the upper story)
  and attached tower volumes that rise above the main volume's roof. Adds
  the `mezzanine_floor_pass` to the canonical pass order between
  `structure_pass` and `floor_slab_pass`.
- `style-profile`: Adds `INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, and
  `HERALDRY` material slots to the style profile schema, and narrows the
  `forbidden_blocks` policy to a curated medieval-palette list (removing
  bed/chest/banner/sign).
- `validation`: Adds civic signature-block requirements per archetype
  (brewing/barrels for tavern, bell/lectern for lord_manor) alongside the
  existing forge and utility-block rules.
- `resource-export`: Adds civic-family export: per-structure
  `place/<id>.mcfunction`, a `gallery/civic.mcfunction`, and inclusion of
  the civic column in the grouped `/myvillage gallery`.

## Impact

- **Code**: `tools/buildgen/archetypes.py` (new builders), `massing.py`
  (new node types in `VOLUME_TYPES`), `ops.py` (mezzanine pass + civic
  interior zone branches + sign/banner/bed/chest placement), `passes.py`
  (new pass slot), `quality.py` and `validate_generated_structures.py`
  (civic rules), `export.py` (civic loop + gallery column),
  `generate_all_structures.py` and `generate_compound_library.py` siblings
  for the new civic library.
- **Style data**: `tools/buildgen/styles/medieval_village.json` updated in
  place (new slots, narrowed forbidden list). No new style file introduced
  for v1; civic family reuses `medieval_village`.
- **Resources**: New NBT files `tavern_001..005.nbt`,
  `lord_manor_001..003.nbt` under
  `src/main/resources/data/myvillage/structure/`. New mcfunction files
  under `function/place/` and `function/gallery/`.
- **Docs**: README "Available Commands", "Generate All Mod Structures",
  and "Current Scope" sections; AGENTS.md acceptance prep; this change's
  specs.
- **Mod jar**: Artifact version bump to `0.5.0` (minor; new content, no
  breaking changes to existing commands or worldgen posture).
- **No runtime/worldgen impact**: Civics remain debug-placeable via
  `/myvillage place` only, matching v0.4 posture.
