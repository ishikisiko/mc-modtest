## Why

0.28.0 moved combat moves and weapons into data files, a registry, a validator, and an in-repository capture workflow, with the claim that a new weapon or a new style needs no combat Java. That claim has only been exercised by the Qingfeng sword it was extracted from. The owner supplied concept art for a second weapon of a different class, the Lingxiao Spear (凌霄枪), and asked for it to be built end to end as the validation of that infrastructure.

## What Changes

- Add the item `myvillage:lingxiao_spear` (凌霄枪) with a generated 3D element model, texture, 2D inventory icon, and geometry contract, following the concept art (`image.png` at the repository root, untracked).
- Add the combat style `myvillage:basic_spear` with five spear moves (mid thrust, sweep, rising flick, overhead smash, dragon lunge), its weapon entry, translations, a first-person rig, and third-person PAL animations in a new `spear_combat.json`.
- Generalize `tools/gen_sword_pal_anims.py` so a pose table carries its own weapon geometry and can solve the off hand onto the weapon shaft (two-handed third-person poses). `sword_combat.json` stays byte-identical.
- Add an optional off-hand arm to the first-person rig so a two-handed weapon shows both hands. Weapons whose rig does not ask for it are unchanged.
- Record what the 0.28.0 infrastructure handled without Java and every place it had to be extended.
- Qingfeng's moves, timing, damage, rig, model, and generated animation do not change.

## Capabilities

### New Capabilities

- `lingxiao-spear-weapon`: The spear item, its generated model and geometry contract, the `basic_spear` style and its five moves, the two-handed presentation in third and first person, and the evidence boundary for its review.

### Modified Capabilities

- `combat-style-data`: The first-person rig may declare an off-hand grip; the geometry contract may name an off-hand grip point.
- `combat-data-validation`: The generator checks cover more than one pose table, per-table weapon geometry, and the off-hand shaft fit.

## Impact

- Java: `item/ModItems` (registration and creative tab); `client/combat` first-person arm classes and rig parsing for the off-hand arm; their tests. No change to `combat/session`, `combat/runtime`, `combat/network`, or the payload protocol.
- Resources: new `data/myvillage/combat/style/basic_spear.json`, `weapon/lingxiao_spear.json`, `index.json` entries, item model, textures, geometry contract, first-person rig, `player_animations/spear_combat.json`, translations.
- Tools: new `tools/gen_lingxiao_spear_model.py`; `tools/gen_sword_pal_anims.py`; validator and tests where they assumed one style or one weapon.
- Docs: `README.md`, `AGENTS.md`, `docs/ai-kb/34_combat_data_and_capture.md`, a new KB note for the spear and the validation findings, `docs/ai-kb/INDEX.md`, `CHANGELOG.md` (0.29.0).
- Out of scope: new sound assets (the spear reuses the existing combat sound events), the concept art's floating ribbons and particles, cloth simulation for the pennant and tassels, a recipe, datapack override of style data, and any owner verdict.
