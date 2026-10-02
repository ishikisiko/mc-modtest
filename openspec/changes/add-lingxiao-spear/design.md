## Context

The Lingxiao Spear is the first weapon added on top of the 0.28.0 combat data infrastructure (`docs/ai-kb/34_combat_data_and_capture.md`). It is deliberately a different weapon class from the Qingfeng jian: about twice as long, held in two hands, with a short head at the end of a long shaft. The work was split into atomic tasks done by separate workers under one lead. This file records what was built and why; the narrative and the validation findings are in `docs/ai-kb/35_lingxiao_spear.md`.

Reference art: `image.png` at the repository root (untracked; not committed).

## Ids and paths

All paths are under `src/main/resources`.

| Thing | Value |
|---|---|
| Item id | `myvillage:lingxiao_spear`; zh_cn `凌霄枪`, en_us `Lingxiao Spear` |
| Item class | `CombatWeaponItem` on `Tiers.DIAMOND`, attack bonus 4, speed −2.8 (8 damage, 1.2 speed), in `#minecraft:swords`, `myvillage:main` after the four swords, no recipe |
| Style id | `myvillage:basic_spear` → `data/myvillage/combat/style/basic_spear.json` |
| Weapon file | `data/myvillage/combat/weapon/lingxiao_spear.json` |
| First-person rig | `assets/myvillage/combat/lingxiao_spear_first_person.json` |
| Geometry contract | `assets/myvillage/combat/lingxiao_spear_geometry.json` |
| Item model wrapper | `assets/myvillage/models/item/lingxiao_spear.json` (`neoforge:separate_transforms`; `gui` keeps the 2D icon) |
| 3D model, model texture, icon | `models/item/lingxiao_spear_3d.json`, `textures/item/lingxiao_spear_model.png` (128x128), `textures/item/lingxiao_spear.png` (64x64) |
| Model generator | `tools/gen_lingxiao_spear_model.py`, sole writer of the wrapper, 3D model, both textures, and the contract |
| Third-person animations | `assets/myvillage/player_animations/spear_combat.json`, written only by `tools/gen_sword_pal_anims.py` |
| Animation ids | `myvillage:spear_ready_idle`, `myvillage:spear_mode_enter`, and the five move ids |
| Move ids, in combo order | `basic_spear_01_mid_thrust` 中平扎, `basic_spear_02_sweep` 横扫, `basic_spear_03_rising_flick` 上挑, `basic_spear_04_overhead_smash` 劈枪, `basic_spear_05_dragon_lunge` 游龙突刺 |

## Model and contract

- Same frame as the Qingfeng contract: weapon along +Y on `x = 8, z = 8`, head flat normal X, edge Z, model pixels. Butt `y = -16`, tip `y = 32` (48 px). 79 elements. Third-person display scale 0.9, so the spear is 2.7 blocks at model scale and about 2.53 blocks in the hand after the player renderer's 0.9375.
- Contract names keep the sword's and are read as: `pommel` = butt cap, `handle` = shaft (`y -13.4..16`), `guard` = socket and star ornament, `blade_base`/`blade_tip` = head (`y 23.4..32`).
- `grip_center` `y = -2` (rear, right hand). New optional `off_hand_grip_center` `y = 11` (leading, left hand). New optional `trail` `{base y 16, tip y 32}`.
- The spearhead, cyan inlays, and gems are fullbright through NeoForge element light data (`neoforge_data` block and sky light 15). The pennant, `道` plaque, and tassels are static geometry. The art's floating ribbons and particles are not built.
- The generator's hand-zone self-check keeps every element except bare shaft out of the rear hand's zone and the leading hand's range (`y 0..13.5`); the art's mid-shaft collar is therefore a painted band.
- The 64x64 icon is authored on a 32x32 grid (2x2 texels per logical pixel) so it survives the 2:1 minification of a GUI-scale-2 slot.
- Left-hand display entries equal the right-hand ones. The game mirrors the left hand when it applies the transform, so pre-mirrored entries (the Qingfeng generator's convention) draw a non-mirror for any rotation other than 0 or 180.

Why `trail`: the head alone is 8.6 px, about half the sword's 15.9 px blade. A trail from 16 to 32 (the socket's lower collar to the tip, 16 px) reads like the sword's at the spear's reach.

## Style `myvillage:basic_spear`

Longer reach and slightly slower than the sword, thrust-led, a wide sweep, a long piercing finisher. The values the design started from were kept:

| # | Move | Kind | Total | Active | Buffer | Chain | Damage × | Targets | Range | Step | Hit-stop |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | mid thrust | thrust | 12 | 4–5 | 4 | 8 | 0.90 | 1 | 4.2 | 0.30 @3 | 1.5 |
| 2 | sweep | cut | 15 | 5–7 | 5 | 10 | 0.95 | 4 | 3.8 | 0.25 @4 | 2 |
| 3 | rising flick | cut | 15 | 5–7 | 5 | 10 | 1.00 | 2 | 3.6 | 0.30 @4 | 2 |
| 4 | overhead smash | cut | 19 | 7–9 | 7 | 14 | 1.15 | 3 | 4.0 | 0.45 @6 | 3.5 |
| 5 | dragon lunge | thrust | 22 | 8–10 | 8 | 22 | 1.30 | 3 | 5.0 | 1.60 @7 | 4 |

- The two thrusts use the `thrust` generator. The sweep, flick, and smash use explicit samples, three per active tick: the `arc` generator draws one line per tick (a wide arc then reads as a few spokes) and the `diagonal` generator's reach is fixed near 2.6 to 2.75 blocks. The flick and smash samples follow the posed spearhead.
- Sounds reuse the `myvillage:combat.sword.*` events. Moves 4 and 5 are heavy.

## Two hands

Third person (`tools/gen_sword_pal_anims.py`, table `BASIC_SPEAR`, `two_handed=True`):

- The item stays on `right_item` at the rear grip. A key with `larm=ON_SHAFT` or `OnShaft(at=y)` solves the rigid left arm so the left fist lands on the shaft at that contract y, or at the nearest reachable shaft point inside the handle and clear of the right fist; the residual is checked at keys and between keys. An explicit `larm=(yaw, elevation)` is a deliberate release.
- With rigid arms (PAL renders no elbow bend, see below) the left fist reaches only a shaft pointing about 45 to 120 degrees left of the chest. The guard is therefore bladed (body turned right, left foot and hand leading). The guard, idle, mode entry, and mid thrust keep both hands on the shaft. The sweep, flick, smash, and lunge release the leading hand for the swing and regrip in recovery; holding on would put the shaft through the body or cripple the arc.
- Spear rules: a cut-path set with spear tip thresholds, the sweep's trail-to-head tolerance (0.48 blocks), and three long-weapon checks (butt and tip at least 0.1 block above ground, at least 5 contract px between the two hands, the shaft at least 0.5 player-model px from torso, head, legs, and arms) sampled at and between keys of every spear animation.

First person (`rig.off_hand`):

- An optional `rig.off_hand` block (`shoulder_offset`, `grip_diagonal`) makes `FirstPersonArmRenderer` draw the off arm, solved by `FirstPersonArmIk.solveOffHand` onto the shaft at `off_hand_grip_center` plus the key's `off_hand_slide`. `off_hand_roll` turns the hand about the shaft, `off_hand_elbow` swivels the elbow, and `off_hand_hold` (1 holds, 0 lets go) moves the hand toward a fixed rest beside the body; at 0 the arm is not drawn. Out of reach, the hand slides to the nearest reachable shaft point.
- The off arm is drawn only while the off-hand slot is empty, and takes no lag. A rig with the block on a contract without `off_hand_grip_center` fails to load. Rigs without the block render exactly as before.
- The spear rig keeps both hands through the thrust, sweep, and flick; the smash releases at its start and the lunge at its strike, both regripping in recovery.

Why the two persons differ: the first-person arm has a two-bone solve with a bending elbow, so it can follow the shaft through a sweep; the third-person PAL arm is one rigid box.

## Runtime changes outside `combat/**`

- `CombatWeaponItem`: a landed hit costs durability, the server resends the stack, and NeoForge's default `shouldCauseReequipAnimation` replays the equip animation for any new stack object, so the first-person weapon sank for 5 to 6 ticks after every hit. A data-only change no longer re-equips; a slot change or another item still does. Every item with a weapon entry must be this class (`tools/validate_mod_items.py`).
- `SwingClock` keeps the action's present (the latest real tick any reader saw) and draws the pose from it. A hit confirmation handled at the start of a frame read the tick before the one already partly drawn; arriving after the rig's contact tick it started the hit-stop there, and the blade stepped back (the sword's horizontal cut by 114 px at normal speed). `confirmHit` now starts the stop at the present, or at the contact tick if the blade has not reached it.
- Trails: the first-person trail spans the contract's `trail` (else the blade). The world trail's tip radius is `0.705` (shoulder to grip) plus grip-to-trail-tip at the model's third-person scale, and its length the `trail` span at that scale, replacing the 1.7-block cap and the clamped blade length with its 1-block fallback. Samples sharing a tick are spread evenly through it. For Qingfeng every drawn frame is unchanged (pinned by `CombatWorldTrailsTest`).
- Smoke probe: `third_person` refusals log `PAL_SMOKE third_person rejected reason=no_layer|missing_animation|trigger_refused`; a refused transition that repeats every tick (a missing ready idle) logs once at info, then at debug.

`combat/session`, `combat/runtime`, `combat/network`, and the payload protocol are unchanged. `BasicSpearStyleTest` drives a real `CombatSession` through the five moves.

## Tools

- `gen_sword_pal_anims.py`: `POSE_TABLES` holds one `PoseTable` per style with its own geometry contract, 3D model, `PoseRules`, `cut_paths`, and output file. Grip compensation, forward kinematics, reach, and the long-weapon checks use the table's weapon. `--check` and `--report` cover every table.
- Validator: model, texture, generator drift, and jar checks for every weapon (the contract's `generator` names its model generator; Qingfeng's stays pinned); the off-hand rig and contract checks; the `trail` check. Fixture and Java test data hold two styles and two weapons.
- `test_combat_data.py`: a standing mob straight ahead from 1.0 block out to each move's `range` is touched by some sample; the samples reach the range.
- Capture: default views by held length (1.8 blocks or more: `fp,tp_back_right,tp_front_left`), cropped quarter-view sheets, a tolerant stability rule, `shot`, `view`, `motion`, `combo --camera back`, fail-fast probe refusals, and the loaded rig in the manifest.

## Evidence

Capture sets under `out/preview/combat_capture/<label>/` and spear evidence under `out/preview/lingxiao_spear/` are developer evidence. Sound, real keyboard play, a second client, and every owner verdict stay `not_verified`.

## Open items

- Third-person arms are rigid: PAL 1.1.4 parses a `bend` channel but does not render it, so a long two-handed weapon cannot keep both hands through wide swings.
- The offline preview tools used for the rig and pose work live outside the repository (`/home/ubuntu/code/mc/combat-lab/harness/tools/`).
- The full findings list and recommended next steps are in `docs/ai-kb/35_lingxiao_spear.md`.
