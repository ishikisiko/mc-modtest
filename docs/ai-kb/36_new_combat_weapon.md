# New Combat Weapon Playbook

The procedure for adding a third (or later) combat weapon, written for 0.30.0
after the Lingxiao Spear. `tools/new_combat_weapon.py` speaks in this file's
step ids: `scaffold` writes what is a mechanical renaming of a template weapon
and prints the rest by step, and `progress` reports each checkable step of a
weapon as `DONE`, `PLACEHOLDER`, `MISSING`, `BLOCKED`, or `N/A`. The step
headings below and the tool's `STEPS` must stay in the same order with the
same ids; `tools/tests/test_new_combat_weapon.py` checks it.

See also:

- What a weapon is made of and where each fact lives: [Combat Data and Capture Tooling](34_combat_data_and_capture.md) ("Adding content")
- The second weapon and every rule it surfaced: [Lingxiao Spear](35_lingxiao_spear.md)
- Runtime, rig, arm, trails: [PAL Sword Combat Integration](32_pal_combat_integration.md)
- Item route: [Mod Item Creation](22_mod_item_creation.md)

## The two commands

```bash
# Start: always look at the dry run first.
python3 tools/new_combat_weapon.py scaffold --from myvillage:qingfeng_sword --id myvillage:jade_sword --dry-run
python3 tools/new_combat_weapon.py scaffold --from myvillage:lingxiao_spear --id myvillage:iron_halberd \
    --style myvillage:basic_halberd --moves thrust,sweep,hook,chop,lunge

# Where it stands (exit 0 when no checked step is open, 1 otherwise, 2 for a bad request).
python3 tools/new_combat_weapon.py progress myvillage:iron_halberd [--fast] [--json]
```

`scaffold` writes the weapon file, appends the weapon (and with `--style` the
style) to `index.json`, copies the template's first-person rig, with `--style`
copies the template's style with every style, move, display-key, and
animation id renamed, and adds `TODO(new_combat_weapon)` translation values
after the template's keys in every lang file. It never writes Java, a
generator-owned file (item models, textures, geometry contracts, PAL
animation files, the parity golden), or a test. It refuses, writing nothing,
when any id, key, or file it would create already exists. `--root` points
either command at a copy of the repository.

`progress` re-implements no check. It runs
`validate_sword_combat_foundation.py` (with `--fast`, without the generator
`--check` runs), `validate_mod_items.py`, the PAL generator's `POSE_TABLES`,
`release_gate.py --list`, and the two tests that pin the shipped weapon list,
and sorts their findings into steps. Findings about other weapons are listed
at the end. A style or rig that is still identical to another weapon's, or a
name or contract that still holds `TODO(new_combat_weapon)`, is
`PLACEHOLDER`: `DONE` means "no longer the copy", not "good".

## Steps

### 1. Choose the weapon, its template, and its style (`choose`)

- The weapon id is the item id, `myvillage:<lower_case_name>`.
- Same move set as an existing weapon: template is a weapon of that style, no
  `--style`. The weapon is then drawn with that style's pose table, which was
  solved for the template's model (step 11 checks the fit), and the style's
  hit samples serve both weapons (step 10).
- New move set: `--style myvillage:<name>`, and `--moves` with one name per
  template move to get ids `<style>_NN_<name>`; without it the template's
  suffixes are kept.
- Two-handed: start from `myvillage:lingxiao_spear`, so the rig copy carries
  `rig.off_hand` and the manual steps ask for `off_hand_grip_center` and
  `two_handed=True`.
- No combat Java names an item (`combat/**`, `client/combat/**` resolve
  through `CombatStyles`); nothing in these steps touches them.

### 2. Run the scaffold (`scaffold`)

Dry run, read the manual steps it prints, then run it for real and commit the
scaffold on its own so later diffs show only authored changes.

### 3. Item contract (`item-contract`)

`genops/contracts/items/<item>.json`, from a copy of the template's contract,
matching `genops/schemas/item_contract.schema.json`
([Mod Item Creation](22_mod_item_creation.md)). Done when it exists, its
`item_id` is the weapon id, and it holds no `TODO(new_combat_weapon)`.

### 4. Java item registration (`item-registration`)

In `item/ModItems.java`, a `CombatWeaponItem` (never a plain `SwordItem`: it
replays the equip animation after every durability-costing hit) and its
creative-tab place. The scaffold prints the template's declaration renamed;
set the new tier and attributes. This and the tests are the only Java a
weapon needs.

### 5. Item tag (`item-tag`)

Add the id after the template's in `data/minecraft/tags/item/swords.json`
once the item is registered (a tag naming an unregistered item fails to load
in game). `N/A` when the other combat weapons are not all in the tag.

### 6. Model generator, item model, textures, geometry contract (`model-and-contract`)

Write `tools/gen_<item>_model.py`, starting from the template's generator. It
is the sole writer of the `separate_transforms` item model (3D model as base,
2D icon in `gui`), the `_3d` model, the 64x64 icon with alpha, the model
texture, and `combat/<item>_geometry.json`: format 2 names
(`butt`, `handle`, `collar`, `head`, `head_base`, `head_tip`, `axes.length`,
see [34](34_combat_data_and_capture.md)), `generator` naming the script,
`off_hand_grip_center` for a two-handed weapon, `trail` optional. It must pass
`--check`. Never hand-edit its outputs; never hard-code geometry or grip
offsets in Java.

### 7. Weapon file and index entries (`weapon-data`)

Written by the scaffold: `data/myvillage/combat/weapon/<item>.json` (item,
style, rig, geometry) and the index entries. Appending keeps the first weapon
of a style and the first style in their roles.

### 8. Style moves: timing, damage, reaction, feedback (`style-data`)

New style only (`N/A` for a shared one). Every number in the copied style is
the template's: timing, chain and buffer ticks, damage, targets, range,
reaction, step, feedback, camera. Chain/cancel windows stay server-decided.
`PLACEHOLDER` while the whole style equals another; once one move differs it
is `DONE` with "N move(s) still identical" listed.

### 9. Item and move names (`names`)

Replace every `TODO(new_combat_weapon) ...` value in `lang/en_us.json` and
`lang/zh_cn.json`: the item key, and with a new style each move's
`display_key`.

### 10. Hit samples and world trails (`hit-samples`)

The rules in [35 "Hit sample rules"](35_lingxiao_spear.md#hit-sample-rules):
non-decreasing ticks, equal counts per active tick once any tick has several,
and every drawn frame of a cut's far end at or beyond the weapon's trail tip
radius (`COMBAT_TRAIL_CUT_REACH`). Wide or long cuts need explicit samples.
A move's `trail.samples` draws the world trail apart from the hit volume and
is never read by the server. On a shared style the samples must reach the
longest weapon's radius. `BLOCKED` until step 6 gives the radius.

### 11. Third-person poses (`third-person-poses`)

New style: a `PoseTable` in `POSE_TABLES` of `tools/gen_sword_pal_anims.py`
(start from the template's table: its own contract, 3D model, rules, cut
paths, output file; `two_handed=True` for two hands), then run it and its
`--check`. Never hand-edit the PAL file.

Shared style: no new table. The weapon's grip and axis after its own
`thirdperson_righthand` display must land within 0.1 px and 0.5 degrees of
the table weapon's, which `progress` measures. Look with
`python3 -m tools.combat_preview pose`.

### 12. First-person rig (`first-person-rig`)

Author `combat/<item>_first_person.json` (the copy is the template's):
`rig.weapon_scale`, `rig.shoulder`, `rig.arm`, `rig.off_hand` for two hands,
`neutral`, and per move its keys, `strike` (covering the server active ticks;
the validator's limit is `MAX_STRIKE_TICKS`), and `contact`. Iterate offline
(step 18); in a running session `python3 -m tools.combat_capture reload` or
`F3+T` reloads it. `PLACEHOLDER` while it equals another weapon's rig.

### 13. First-person parity golden (`parity-golden`)

The golden `src/test/resources/first_person_preview_parity.json` covers every
weapon in index order. Rewrite it, then check the Python port:

```bash
flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew test \
    --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest \
    -PupdatePreviewParity -x generateAllStructures --console=plain
.venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity
```

Redo it after every rig or contract change.

### 14. Item validator entry (`item-validator-pin`)

In `tools/validate_mod_items.py`: an entry in `SWORD_CONTRACTS` (names,
icon, attributes), the holder in `CREATIVE_SWORD_ORDER` at its tab place,
and the `_3d` model in `SWORD_3D_MODELS`.

### 15. Tests (`tests`)

- `tools/tests/test_gen_<item>_model.py` for the generator.
- The pinned weapon lists in
  `tools.tests.test_combat_data.CombatDataTest.test_committed_data_is_valid`
  and
  `tools.tests.test_validate_sword_combat_foundation.SwordCombatFoundationValidatorTest.test_valid_repository_fixture_passes`
  (found by the rehearsal: both fail as soon as a weapon is added).
- Java, not checked by `progress`: the style and weapon constants in
  `CombatTestData.java`, and for a new style a test like
  `BasicSpearStyleTest` that drives a real `CombatSession` through the moves.
  Pin accepted values the way `BasicSwordStyleTest` and
  `tools/tests/test_combat_style_baseline.py` pin Qingfeng's.

### 16. Release gate step (`release-gate-step`)

In `tools/release_gate.py` `STEPS`, `generator_check("gen_<item>_model")`
after the template's. `progress` checks `release_gate.py --list`.

### 17. README (`readme`)

`jar tf ... | grep` lines for the item model, the geometry contract, the
rig, and for a new style its PAL file (plus the 3D model and textures) in
both jar listings, and a section with `/give @s <weapon id>`.

### 18. Iterate offline (`preview`)

`python3 -m tools.combat_preview fp|pose|model` (numpy and Pillow from
`.venv-preview/`, see `tools/combat_preview/README.md`). Only the `fp` solver
has a parity test against the Java; the arm mesh, trail, rasteriser, `pose`,
and `model` are checked only against in-game stills.

### 19. Candidate sheets for the owner (`candidate-sheet`)

Where a value is a matter of taste, show candidates instead of picking:
`python3 -m tools.combat_preview sweep --set <json.path>=<v1>,<v2>,...` and
`diff <before> <after>`. The 0.29.0-fix1 off-arm thickness is the worked
example in the preview README.

### 20. Confirm in game once (`capture`)

`python3 -m tools.combat_capture run --label <label> --weapon <weapon id>`
(see `tools/combat_capture/README.md`). It holds the shared heavy-work lock;
on this host only one Gradle or Minecraft job runs at a time. Views default
by length (`fp,tp_back,tp_front` under 1.8 blocks, cropped quarter views
above). `combo --layout sweep|line` and `--tick-rate` give multi-target and
slowed server evidence. `/myvillage_pal_smoke` poses are visual probes, never
mapped-click or server evidence.

### 21. Owner review and the not_verified list (`owner-review`)

An evidence page under `out/preview/` with the stills, combos, and a list of
everything not observed (sound, a second client, real keyboard and mouse
play, other skins, real-GPU frame rates, ...; see the spear's list in
[35](35_lingxiao_spear.md#evidence)). Never infer the owner's texture,
animation, or gameplay verdict.

### 22. Release (`release`)

```bash
python3 tools/bump_version.py <version>   # the version rule's four files
/usr/bin/python3 tools/release_gate.py    # every documented check, one jar
```

Then the CHANGELOG validation from the gate's output, the commit, and the
fast-forward to `main`. Push only after the owner confirms.

## Rehearsal (2026-10-03)

On a scratch copy of the repository at the 0.30.0 release commit:

- `scaffold --from myvillage:lingxiao_spear --id myvillage:iron_halberd
  --style myvillage:basic_halberd --moves thrust,sweep,hook,chop,lunge`
  wrote three files and changed three; `progress` then reported step 7 `DONE`,
  steps 8, 9, and 12 `PLACEHOLDER`, step 10 and 16 `BLOCKED` on step 6, and
  the rest `MISSING` with the file or finding behind each.
- `scaffold --from myvillage:qingfeng_sword --id myvillage:jade_sword` (shared
  style) wrote two files and changed three; step 8 was `N/A` and step 11
  `BLOCKED` until a model exists.
- A second run, `--moves` without `--style`, an unknown template, and a
  `minecraft:` id were each refused with exit 2 and nothing written.
- On the repository itself both shipped weapons report every checked step
  `DONE` (Qingfeng after its rig's `jar tf` line was added to the README).

## Third weapon: the Xuantie gauntlet (0.39.0)

`myvillage:xuantie_gauntlet` (玄铁拳套, style `myvillage:basic_fist`) went through
steps 2 to 20 from `scaffold --from myvillage:qingfeng_sword --style
myvillage:basic_fist --moves straight_punch,horizontal_palm,uppercut,chop,step_double_strike`.
The steps held; what a worn weapon with a free off hand needed beyond them:

- Contract: the format 2 names read for a gauntlet as cuff (`butt`), the hand
  inside the glove (`handle`, `grip_center` at the fist centre), knuckle bar
  (`collar`), knuckle studs (`head`), with `axes.length` the punch direction
  and the `trail` from the knuckle line to the stud tips
  (`tools/gen_xuantie_gauntlet_model.py`). The third-person display lays the
  punch axis along the arm, so the model sits on the fist with `right_item` at rest.
- First person: `rig.arm.grip_diagonal` now runs to 90, where the weapon's +Y
  lies along the hand; `rig.off_hand.free` draws a bare guard hand with no
  contract point; per-key `off_hand_rest` / `off_hand_reach` move it (all in
  Java, the `fp` port, the parity golden, and the validator; schema in
  [35](35_lingxiao_spear.md#rig-off-hand-schema)).
- Pose generator: `worn` and `free_off_hand` table flags with their rules (see
  [34](34_combat_data_and_capture.md), Tools).
- A weapon `family` field, checked against the schools.
- `LiveSwingTimingTest` caught a contact tick whose hit-stop freeze ended exactly
  on a sampled frame time (chop contact 5.3 + 1.2); the contact moved to 5.4.

Not rehearsed: steps 21 and 22 (owner review, release) for the gauntlet.
