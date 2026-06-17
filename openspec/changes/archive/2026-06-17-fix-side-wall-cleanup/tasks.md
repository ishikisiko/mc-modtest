## 1. Validator first (failing checks document the bug)

- [x] 1.1 Add a `open_side_wall` hard-error check to `quality_check()` (`tools/buildgen/quality.py`): for each closed volume and each wall, every cell in the wall plane from `fh` to the roofline directly above SHALL be non-air unless tagged `OPENING`; report the count and a sample coordinate.
- [x] 1.2 Add a `furniture_on_wall` hard-error check: no `INTERIOR`/`PROTECTED` non-`OPENING` block SHALL occupy a cell lying in a different volume's exterior wall plane.
- [x] 1.3 Run `python3 tools/generate_all_structures.py` (or the smallest reproducing subset) and confirm both checks fire on `main` for at least one blacksmith and one stone-style gabled building; capture the failing coordinates as the baseline.

## 2. Junk blocks — interior furniture leak

- [x] 2.1 Inset the `smithy` zone in `tools/buildgen/archetypes.py:769-770` to `(work.x0 + 1, work.z0 + 1, work.x1 - 1, work.z1 - 1)`, matching `forge`/`storage`.
- [x] 2.2 Harden `spots_along_walls()` (`tools/buildgen/ops.py:1658-1673`) to mount furniture only on a wall cell belonging to the zone's own volume (pass the owning volume into the zone context and reject neighbor cells outside its bounds).
- [x] 2.3 Confirm `furniture_on_wall` no longer fires for blacksmith archetypes (`blacksmith_005/007/008` were the originally reported cases).

## 3. Junk blocks — gable infill material

- [x] 3.1 Add an optional `GABLE_INFILL` slot to `tools/buildgen/styles/*.json`; leave it absent on the three stone styles, and (if the timber look is wanted) declare the plank entry on `medieval_village`.
- [x] 3.2 In `gable_roof()` (`tools/buildgen/ops.py:539-598`) resolve infill from `GABLE_INFILL` else `WALL_MAIN`, drop the 60/40 `ROOF_DARK._planks` mix, and tag each cell with the slot it actually holds.
- [x] 3.3 Verify stone-style gables contain no `dark_oak_planks` and `material_balance` stays within bounds.

## 4. Incomplete wall — gable geometry

- [x] 4.1 Climb the gable triangle to the true `ridge_y` so no apex gap remains (`tools/buildgen/ops.py:586-604`); fill every still-empty in-plane cell.
- [x] 4.2 Back every gable-plane cell that holds only a roof stair with a full gable block in the wall plane, and record it in `gable_cells`.
- [x] 4.3 Confirm `open_gable` and the new `open_side_wall` check pass on gabled buildings across all four styles and several seeds.

## 5. Incomplete wall — connection, plinth, chimney

- [x] 5.1 `_carve_connection()` (`tools/buildgen/passes.py:58-93`): early-return for `vol.meta.get("open")`; shift `zmid` off the parent `WallPlan` post/window/door columns and re-seal any post column the opening crosses.
- [x] 5.2 `wall_frame()` (`tools/buildgen/ops.py:327-330`): floor `stone_rows` at 1 for any wall with `wall_h >= 3` so small wings/sheds keep a stone plinth.
- [x] 5.3 Chimney placement (`tools/buildgen/archetypes.py` + `ops.chimney()`): stop force-overwriting another volume's `FACADE`/`STRUCTURE` wall cell; offset or re-seal where a `side_wing`/shed abuts.

## 6. Lower-priority polish (drop if it destabilizes output without payoff)

- [x] 6.1 Make opposite walls of one volume share a post layout in `plan_wall()`/`plan_building_facades()` (`tools/buildgen/facade.py`). — *Deferred: a shared-post refactor shifted the per-building rng stream and broke byte-stability for non-gabled buildings; dropped per the §6 "drop if it destabilizes" clause to keep the diff surgical.*
- [x] 6.2 Clamp `material_variation_pass` speckle on side/back facades now that the dominant noise sources are gone. — *Deferred: clamping speckle regressed `flat_wall` on side walls (speckle also breaks long runs); the dominant noise is already gone, so speckle is retained. The only follow-up change here was excluding roof-skin gable cells from the `flat_wall` check so the new solid gable infill is not flagged.*

## 7. Regenerate, validate, accept

- [x] 7.1 Regenerate libraries (`python3 tools/generate_all_structures.py`); confirm vanilla flat-roof structures are byte-stable and explain every gabled/blacksmith diff.
- [x] 7.2 Re-run the validator suite (`validate_generated_structures.py`, `validate_building_library.py`, `validate_compound_library.py`, `validate_civic_library.py`, `check_style_policy.py`, `check_cultivation_forms.py`) and refresh `reports/`.
- [x] 7.3 Refresh offline previews (`python3 tools/preview_structure.py --all`) and review via the `out/preview/` entry point.
- [x] 7.4 Stage manual acceptance: spawn a blacksmith and a stone-style gabled building, confirm no furniture on the side wall, no dark planks in the gable, and no roofline/connection holes; capture screenshots.

## 8. Version + docs

- [x] 8.1 Bump to `0.8.1-fix1` across `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, and README jar-name examples.
- [x] 8.2 Add a `0.8.1-fix1` entry to `CHANGELOG.md` summarizing the side-wall fixes.
- [x] 8.3 Build the jar with `./gradlew build`.
