## Why

0.28.0 moved combat moves and weapons into data files, a registry, a validator, and an in-repository capture workflow, with the claim that a new weapon or a new style needs no combat Java. That claim had only been exercised by the Qingfeng sword it was extracted from. The owner supplied concept art for a second weapon of a different class, the Lingxiao Spear (凌霄枪), and asked for it to be built end to end as the validation of that infrastructure.

## What Changes

- Add the item `myvillage:lingxiao_spear` (凌霄枪) with a generated 3D element model, texture, 2D inventory icon, and geometry contract, following the concept art (`image.png` at the repository root, untracked).
- Add the combat style `myvillage:basic_spear` with five spear moves (mid thrust, sweep, rising flick, overhead smash, dragon lunge), its weapon entry, translations, a first-person rig, and third-person PAL animations in a new `spear_combat.json`.
- Register every item that has a combat weapon entry as `CombatWeaponItem`, a `SwordItem` that does not replay the equip animation when only the held stack's data changes. Qingfeng moves to it.
- Generalize `tools/gen_sword_pal_anims.py`: one pose table per style, each with its own weapon geometry, rules, cut paths, and output file, and a two-handed mode that solves the left arm onto the shaft. `sword_combat.json` stays byte-identical.
- Add an optional off-hand arm to the first-person rig (`rig.off_hand` and four per-key fields). Rigs without it render as before.
- Add optional `off_hand_grip_center` and `trail` fields to the geometry contract, and size the first-person and world trails from the contract instead of the blade span and a fixed 1.7-block radius.
- Fix defects found on the way that also affected Qingfeng: the first-person hit-stop could start behind the pose on screen; a landed hit's durability cost replayed the equip animation; every client combat timeline read the client's game clock, which the server's time packet re-sets mid-move (now one client-owned combat clock); a remote attacker's figure and world trail stopped once per impact instead of once per action.
- Add data rules enforced by the loader, the shared reader, and the validator: explicit hit samples in tick order, equal sample counts per active tick, and cut far ends that reach the weapon's trail tip radius.
- Extend the validator, the model and generator tests, and the capture tool to more than one weapon and style.
- Record what the 0.28.0 infrastructure handled without Java and every place it had to be extended.
- Qingfeng's moves, timing, damage, rig, model, and generated animation do not change; its live first-person presentation changes through the two fixes.

## Capabilities

### New Capabilities

- `lingxiao-spear-weapon`: The spear item, its generated model and geometry contract, the `basic_spear` style and its five moves, the two-handed presentation in third and first person, and the evidence boundary for its review.

### Modified Capabilities

- `combat-style-data`: Optional off-hand arm in the first-person rig; optional `off_hand_grip_center` and `trail` in the geometry contract; combat weapons registered as `CombatWeaponItem` with the durability-only re-equip rule; the first-person hit-stop never moves the pose backwards and starts once per action; one client combat clock; the attacker's stop once per action; explicit samples in tick order.
- `combat-data-validation`: Per-weapon model, texture, generator, and jar checks; off-hand and trail checks; one pose table and output per style with its own weapon rig, two-handed solve, and clearance checks; a standing-target coverage test; sample order, per-tick sample counts, per-weapon cut reach, and a single client game-clock reader.
- `combat-capture-tooling`: Quarter views chosen by weapon length, cropped sheets, a tolerant stability rule, `shot`/`view`/`motion`, combo layouts, tick rate, camera, and per-move hit attribution, an experience reset, fail-fast probe refusals, and a record of the loaded rig.

## Impact

- Java: new `item/CombatWeaponItem`; `item/ModItems` (registration, creative tab); `combat/definition/CombatDataLoader` (sample order); `client/combat` (new `ClientCombatClock` and `LocalSwingTimeline`; `FirstPersonSwing`, `FirstPersonArmIk`, `FirstPersonArmRenderer`, `FirstPersonWeaponTrail`, `FirstPersonWeaponAnimator`, `SwingClock`, `WeaponGeometry`, `CombatWorldTrails`, `CombatImpactFx`, `CombatCameraFx`, `CombatAnimationController`, `ClientCombatEvents`, `ClientCombatState`, `ClientPalSmokeEvents`); their tests. No change to `combat/session`, `combat/runtime`, `combat/network`, or the payload protocol.
- Resources: new `data/myvillage/combat/style/basic_spear.json`, `weapon/lingxiao_spear.json`, `index.json` entries, item models, textures, geometry contract, first-person rig, `player_animations/spear_combat.json`, translations, swords tag; the combat-mode and debug-command messages reworded to be weapon-neutral.
- Tools: new `tools/gen_lingxiao_spear_model.py`; `tools/gen_sword_pal_anims.py`; `tools/validate_sword_combat_foundation.py`; `tools/validate_mod_items.py`; `tools/combat_capture/`; their tests.
- Docs: `README.md`, `AGENTS.md`, `docs/ai-kb/32_pal_combat_integration.md`, `docs/ai-kb/34_combat_data_and_capture.md`, new `docs/ai-kb/35_lingxiao_spear.md`, `docs/ai-kb/INDEX.md`, `CHANGELOG.md` (0.29.0).
- Out of scope: new sound assets (the spear reuses the existing combat sound events), the concept art's floating ribbons and particles, cloth simulation for the pennant and tassels, a recipe, datapack override of style data, bending arms in third person, and any owner verdict.
