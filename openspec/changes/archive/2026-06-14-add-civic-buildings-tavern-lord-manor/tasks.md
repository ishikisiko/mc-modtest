## 1. Style Profile Update

- [x] 1.1 Edit `tools/buildgen/styles/medieval_village.json` to remove `bed`, `chest`, `banner`, `sign` from `forbidden_blocks`
- [x] 1.2 Add `INTERIOR_CIVIC` slot with `brewing_stand`, `lectern`, `bell`, `bookshelf`, `cauldron`, `flower_pot`
- [x] 1.3 Add `FURNITURE` slot with `bed` (multiple color variants) and `chest`
- [x] 1.4 Add `SIGNAGE` slot with `standing_sign` and `wall_sign` (oak/spruce variants)
- [x] 1.5 Add `HERALDRY` slot with `standing_banner` and `wall_banner` (red/blue/black/green/yellow color variants)
- [x] 1.6 Update `style.py` to load and expose the new slots via `slot_entry` and `material_slots`
- [x] 1.7 Verify existing medieval library generation still passes (no behavior change for archetypes that don't reference new slots)

## 2. Massing Primitives

- [x] 2.1 Add `great_hall_volume` and `tower_volume` to `VOLUME_TYPES` in `tools/buildgen/massing.py`
- [x] 2.2 Add helper `_tower_volume(graph, main, side, stories_over_main, footprint)` in `archetypes.py` mirroring `_main_volume` shape with own stairwell reservation
- [x] 2.3 Add helper `_mezzanine_meta(main, covers, depth)` that writes `mezzanine = {"covers": ..., "depth": ..., "y_offset": 0}` and marks the upper story as `mezzanine_story = True`
- [x] 2.4 Extend `_reserve_stairwell` to accept a target volume scope (main vs tower) so tower stairwells don't collide with main stairwells
- [x] 2.5 Extend `reserved_stair_footprint` to look up stairwell by volume id (already supports `volume_id`; verify and test for tower case)

## 3. Mezzanine Pass

- [x] 3.1 Add `ops.mezzanine_floor(grid, style, vol, mezzanine_meta)` that lays slab blocks over the configured half-plane at the story boundary, with `STRUCTURE` + `PROTECTED` tags
- [x] 3.2 Make `ops.floor_slab` skip stories flagged `mezzanine_story` (read from volume meta)
- [x] 3.3 Add `passes.mezzanine_floor_pass(ctx)` that iterates volumes with `mezzanine` meta and calls `ops.mezzanine_floor`
- [x] 3.4 Insert `mezzanine_floor_pass` into `passes.PASS_ORDER` between `structure_pass` and `floor_slab_pass`
- [x] 3.5 Add quality check in `quality.py` that a mezzanine volume's uncovered half-plane ceiling is open (no slab blocks above the great hall over the uncovered region)

## 4. Civic Interior Zones

- [x] 4.1 Add interior zone kind branches in `ops.py:interior_zone()` for `tavern_hall`, `tavern_inn`, `town_chamber`, `town_foyer`, `stable`
- [x] 4.2 `tavern_hall`: place 1–2 `brewing_stand` facing the bar wall, 2–4 `barrel` cluster near hearth, 1 `furnace` hearth, 1 `cauldron`, hanging lantern
- [x] 4.3 `tavern_inn`: place 1–3 `bed` (foot+head pair, head against wall, skip window intervals), 1 empty `chest` at bed foot, 1 `crafting_table`
- [x] 4.4 `town_chamber`: place 1 `lectern`, 2+ `bookshelf` along walls, 1 `crafting_table`
- [x] 4.5 `town_foyer`: place 1 `bell` (if no belfry tower), 1 `cauldron`, banner on wall via `HERALDRY` slot
- [x] 4.6 `stable`: lay `hay_block` floor cells, place 1 `fence_gate` opening, optional `water` trough cell
- [x] 4.7 Add `ops.tavern_bar_counter(grid, style, vol, rng)` placing a slab+fence run as the bar counter (called from tavern_hall furnishing)

## 5. Tavern Archetype

- [x] 5.1 Add `build_tavern(style, rng, tier)` in `archetypes.py` returning a `MassingGraph` with `archetype="tavern"`
- [x] 5.2 Define `TAVERN_VARIANTS` list (5 entries) differing on: mezzanine covered half-plane, stable annex presence, footprint, roof axis/overhang
- [x] 5.3 Massing: main volume `stories=2` with `mezzanine` meta, optional `stable_annex` (reuse `shed` with `stable=True` + hay meta), chimney with hearth
- [x] 5.4 Reserve stairwell avoiding the mezzanine half-plane
- [x] 5.5 Place door on the great-hall side; add path patch; add signage (`wall_sign`) above the door via `SIGNAGE` slot
- [x] 5.6 Add interior zones: `tavern_hall` on the open half-plane ground story, `tavern_inn` on the mezzanine half-plane upper story, `stable` in the stable annex if present
- [x] 5.7 Add `tavern` to `BUILDERS` dict
- [x] 5.8 Add `"tavern"` entry to `SCALE_TIERS` with footprints `(15, 11), (17, 11), (17, 13)` (larger than `big_house`)

## 6. Lord Manor Archetype

- [x] 6.1 Add `build_lord_manor(style, rng, tier)` in `archetypes.py` returning a `MassingGraph` with `archetype="lord_manor"`
- [x] 6.2 Define `LORD_MANOR_VARIANTS` list (3 entries) differing on: tower height above main (3 vs 4 stories total), tower attached side, footprint, optional side wing
- [x] 6.3 Massing: main volume `stories=2` with `door_centered=True` meta, one `tower_volume` attached with `belfry=True`, optional heraldry banner meta on the tower top
- [x] 6.4 Reserve main stairwell; reserve tower stairwell scoped to the tower node
- [x] 6.5 Place door at the centered x position (respecting two-cells-from-corner); add path patch; add banner via `HERALDRY` above the door
- [x] 6.6 Add interior zones: `town_foyer` on ground story, `town_chamber` on upper story, private quarters zone (reuse `living` + add bed/chest) on upper story
- [x] 6.7 Furnish belfry: hanging `bell` under the tower ridge (use `belfry=True` meta + interior pass logic)
- [x] 6.8 Add `lord_manor` to `BUILDERS` dict
- [x] 6.9 Add `"lord_manor"` entry to `SCALE_TIERS` with footprints `(17, 13), (19, 13), (19, 15)`
- [x] 6.10 Extend `_door` to honor `door_centered=True` on the main volume meta

## 7. Civic Library Generator

- [x] 7.1 Create `tools/generate_civic_library.py` modeled on `generate_compound_library.py` structure (argparse, deterministic seeds, calls builders, writes NBT + mcfunctions)
- [x] 7.2 Generate 5 tavern variants via `TAVERN_VARIANTS` and 3 lord manor variants via `LORD_MANOR_VARIANTS`, each with a stable seed
- [x] 7.3 Write NBT files to `src/main/resources/data/myvillage/structure/`
- [x] 7.4 Write `place/tavern_001.mcfunction` ... `place/lord_manor_003.mcfunction` with the `~ ~-1 ~` Y offset
- [x] 7.5 Write `gallery/civic.mcfunction` invoking all 8 place functions with 60-block spacing, tavern column before lord manor column
- [x] 7.6 Hook the civic generator into `tools/generate_all_structures.py` so the canonical batch command produces civics alongside the medieval and compound libraries

## 8. Validators

- [x] 8.1 Extend `tools/validate_generated_structures.py` with civic signature rules: `tavern_*` requires brewing_stand OR ≥3 barrel AND ≥1 bed; `lord_manor_*` requires bell OR lectern AND ≥1 banner
- [x] 8.2 Ensure existing `house`/`blacksmith` rules don't match civic filenames (prefix guard)
- [x] 8.3 Create `tools/validate_civic_library.py` modeled on `validate_compound_library.py`: checks all 8 civic NBTs exist, pass signature rules, and that the place/gallery mcfunctions exist
- [x] 8.4 Update the `quality.py` forbidden-blocks gate to consult the updated `medieval_village.json` (no code change expected, but verify)
- [x] 8.5 Add quality check: a `tower_volume` with `belfry=True` must contain a `bell` blockstate

## 9. Exporter / Gallery Wiring

- [x] 9.1 Extend `tools/buildgen/export.py` to emit civic library NBT + mcfunctions (or add a parallel civic export function called by `generate_civic_library.py`)
- [x] 9.2 Extend the `/myvillage gallery` Java command (and/or the generated gallery mcfunction) to include a civic column after the Chinese courtyard column, ordered tavern then lord manor
- [x] 9.3 Verify gallery column count and 60-block spacing still fit within the gallery layout (revisit spacing if total columns > 8)

## 10. Build & Mod Version

- [x] 10.1 Bump mod version from `0.4.1` to `0.5.0` in `gradle.properties` and `build.gradle` (and README expected jar name)
- [x] 10.2 Run `python3 tools/generate_all_structures.py --mc-version 1.21.1 --output src/main/resources/data/myvillage/structure` and confirm all 8 civic NBTs plus existing structures are emitted
- [x] 10.3 Run `python3 tools/validate_generated_structures.py src/main/resources/data/myvillage/structure` and confirm zero errors
- [x] 10.4 Run `python3 tools/validate_compound_library.py --count 6` and confirm zero errors
- [x] 10.5 Run `python3 tools/validate_civic_library.py` and confirm zero errors
- [x] 10.6 Run `./gradlew build` and confirm the jar contains the 8 civic NBTs via `jar tf build/libs/*.jar | grep "data/myvillage/structure"`
- [x] 10.7 Confirm expected jar path is `build/libs/myvillage-0.5.0.jar`

## 11. Documentation

- [x] 11.1 Update `README.md`: add civic structures to "Generate All Mod Structures" expected output list
- [x] 11.2 Update `README.md` "Available Commands" with `/myvillage place tavern_001` and `/myvillage place lord_manor_001` examples
- [x] 11.3 Update `README.md` "Current Scope" to include civic family in the included list
- [x] 11.4 Update `README.md` "Generation Architecture" diagram with the new `mezzanine_floor_pass`, `tower_volume`, civic archetypes, and civic library generator
- [x] 11.5 Update `README.md` expected jar name from `myvillage-0.4.1.jar` to `myvillage-0.5.0.jar`
- [x] 11.6 Update `AGENTS.md` acceptance prep steps to include `tools/validate_civic_library.py` and the new civic commands
- [x] 11.7 Add a short `docs/ai-kb/10_civic_family.md` note describing the civic archetype vocabulary (great hall, mezzanine, tower, belfry, stable) for future contributors

## 12. Manual Acceptance Prep

- [x] 12.1 Confirm all automated validators pass (steps 10.3–10.5)
- [x] 12.2 Confirm the mod jar build succeeds (step 10.6)
- [x] 12.3 Confirm `/myvillage list` shows the 8 new civic templates in a fresh client run
- [x] 12.4 Place `tavern_001`, `lord_manor_001`, and at least one variant of each via `/myvillage place` and capture screenshots for visual review
- [x] 12.5 Run `/myvillage gallery` and confirm the civic column appears in the correct position with readable tavern/lord manor silhouettes
- [x] 12.6 Visual review checklist: mezzanine overhang visible from inside the great hall; tower rises above main roof; belfry bell hangs under tower ridge; banners render on lord manor; beds render in tavern inn; signage renders above tavern door; no roof holes; no gable gaps
