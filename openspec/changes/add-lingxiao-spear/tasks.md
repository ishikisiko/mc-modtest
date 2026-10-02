## 1. Contract

- [x] 1.1 Fix the ids, paths, model frame, and worker boundaries in `design.md` and strictly validate this change.
- [x] 1.2 Rewrite `proposal.md` and `design.md` to what was built, and add the spec deltas for `lingxiao-spear-weapon`, `combat-style-data`, `combat-data-validation`, and `combat-capture-tooling`.

## 2. Item And Model

- [x] 2.1 Add `tools/gen_lingxiao_spear_model.py` (standard library, deterministic, `--check`, `--report`) writing the 3D model, `separate_transforms` wrapper, 128x128 model texture, 64x64 icon, and geometry contract with `off_hand_grip_center` and `trail`; fullbright head and inlays; hand-zone self-check; left-hand displays not pre-mirrored; its tests.
- [x] 2.2 Register `myvillage:lingxiao_spear` (diamond tier, attack bonus 4, speed −2.8), add it to `#minecraft:swords` and to `myvillage:main` after the swords, add both item names, and extend `tools/validate_mod_items.py` and its tests.
- [x] 2.3 Add `CombatWeaponItem` (no re-equip animation on a data-only stack change), register Qingfeng and the spear with it, require it in `tools/validate_mod_items.py` for every item with a weapon entry, and test the rule.

## 3. Style Data

- [x] 3.1 Author `style/basic_spear.json` (thrust generator for the two thrusts, explicit samples for the sweep, flick, and smash), `weapon/lingxiao_spear.json`, the index entries, and the five move translations.
- [x] 3.2 Add Java and Python data tests for two styles and two weapons, `BasicSpearStyleTest` driving a `CombatSession` through the combo, and the standing-target coverage test.

## 4. Third-Person Poses

- [x] 4.1 Generalize `tools/gen_sword_pal_anims.py` to one pose table per style with its own weapon geometry, 3D model, rules, cut paths, and output; keep `sword_combat.json` byte-identical.
- [x] 4.2 Add the two-handed left-arm solve onto the shaft, its residual checks at and between keys, and the long-weapon ground, hand-separation, and arm-aware body clearance checks.
- [x] 4.3 Author the `basic_spear` pose table (bladed guard; both hands for the guard and mid thrust; release and regrip for the sweep, flick, smash, and lunge) and generate `spear_combat.json`.

## 5. First-Person Rig And Off-Hand Arm

- [x] 5.1 Author `lingxiao_spear_first_person.json` with the right hand only and capture it (data-only stage).
- [x] 5.2 Add `rig.off_hand` and the per-key `off_hand_slide`, `off_hand_roll`, `off_hand_elbow`, `off_hand_hold` to rig parsing, the off-arm solve, and the renderer (drawn only with an empty off-hand slot); Java tests.
- [x] 5.3 Recompose the spear rig around the weapon with the off hand on the shaft.

## 6. Trails And Runtime Fixes

- [x] 6.1 Read `off_hand_grip_center` and `trail` in `SwordGeometry`; size the first-person and world trails from the contract; spread same-tick samples through the tick; pin the unchanged Qingfeng world trail.
- [x] 6.2 Make the first-person hit-stop start at the action's present so a late confirmation never steps the pose back; test it.
- [x] 6.3 Log every refused third-person probe with its reason and log a repeated refused transition once.

## 7. Validator

- [x] 7.1 Extend `tools/validate_sword_combat_foundation.py` to per-weapon model, texture, generator, and jar checks, the off-hand rig and contract checks, and the `trail` check; a two-weapon fixture.

## 8. Capture Tooling

- [x] 8.1 Add `--weapon` defaults by held length (quarter views for long weapons), cropped quarter-view sheets, the tolerant stability rule, and the loaded-rig record.
- [x] 8.2 Add `shot`, `view`, `motion`, and `combo --camera back`; stop at a refused probe at once; tests and README.

## 9. Integration And Evidence

- [x] 9.1 Merge the work streams.
- [x] 9.2 Run the focused validator (except the stale jar), the generator checks, and the Python and Java tests on the merged tree.
- [x] 9.3 First in-game look at the model, and a first headless capture of the spear (stills and a mapped-click combo through all five moves with server damage).
- [x] 9.4 Capture the hit-stop and re-equip fixes before and after for both weapons.
- [ ] 9.5 Second headless capture of the spear and the sword on the final tree (`spear-v2`, `sword-v2`).
- [ ] 9.6 Rebuild the jar at the new version and rerun the focused and aggregate validators, `./gradlew test`, `./gradlew build`, jar inspection, and a dedicated acceptance-server start and clean stop.

## 10. Docs And Release

- [x] 10.1 Add `docs/ai-kb/35_lingxiao_spear.md` with the validation findings, list it in `docs/ai-kb/INDEX.md`, and update notes 32 and 34.
- [x] 10.2 Update `README.md` (item, moves, commands, real-client ledger) and the combat rule and probes in `AGENTS.md`.
- [x] 10.3 Apply the version rule: bump to `0.29.0` in `gradle.properties` and mod metadata, update README jar-name examples, and add the CHANGELOG entry.
- [x] 10.4 Make the combat-mode message weapon-neutral in both languages.
- [ ] 10.5 Fill the CHANGELOG validation numbers from the second capture and the final gates.
- [ ] 10.6 Keep every unobserved real-client item `not_verified`; hand the evidence to the owner for review.
- [ ] 10.7 Owner verdict on the spear's model, moves, and two-handed presentation, and on the changed Qingfeng first-person behaviour.
