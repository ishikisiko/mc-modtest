# Lingxiao Spear and the Combat Data Validation

Factual note for 0.29.0. The Lingxiao Spear (凌霄枪, `myvillage:lingxiao_spear`,
style `myvillage:basic_spear`) is the second combat weapon and the first one
built on the 0.28.0 combat data infrastructure. It was built to test the claim
that a new weapon or style needs no combat Java. This note records what the
weapon is, where its facts live, and what the validation found.

See also:

- Data, registry, validator, capture tool: [Combat Data and Capture Tooling](34_combat_data_and_capture.md)
- Runtime, rig, arm, trails, hit-stop: [PAL Sword Combat Integration](32_pal_combat_integration.md)
- Specs: change `add-lingxiao-spear` (new capability `lingxiao-spear-weapon`;
  deltas to [combat-style-data](../../openspec/specs/combat-style-data/spec.md),
  [combat-data-validation](../../openspec/specs/combat-data-validation/spec.md),
  [combat-capture-tooling](../../openspec/specs/combat-capture-tooling/spec.md));
  its `design.md` holds the decisions and their reasons.

## Outcome

- The spear was added with no change to `combat/session`, `combat/runtime`,
  `combat/network`, or the payload protocol. `BasicSpearStyleTest` drives a
  real `CombatSession` through the five moves. In the final headless capture
  on `26b99e9` (`out/preview/combat_capture/spear-final*`) all five moves
  played from mapped clicks and the centre target went from `80.0` to `38.28`
  (per move `7.085`, `7.478`, `7.872`, `9.053`, `10.234`) in every
  default-layout run; the sweep hit three targets on an arc, and on a line the
  lunge hit three. Qingfeng still took its target from `80.0` to `44.18`.
- Java changed where the presentation or the item class could not express a
  second weapon (the first-person off-hand arm, contract fields, trail sizing,
  `CombatWeaponItem`) and where the spear exposed runtime defects that also
  affected Qingfeng (see "Findings").

## The weapon

- Item: `CombatWeaponItem` on `Tiers.DIAMOND`, attack bonus 4, speed −2.8
  (tooltip 8 damage, 1.2 speed), in `#minecraft:swords`, in `myvillage:main`
  after the four swords, no recipe. Item contract:
  `genops/contracts/items/lingxiao_spear.json`.
- Model: 79 elements on the contract frame from `y = -16` (butt) to `y = 32`
  (tip), 128x128 model texture, 64x64 icon authored on a 32x32 grid,
  `separate_transforms` wrapper with the 2D icon in `gui`. Third-person scale
  0.9: 2.7 blocks at model scale, about 2.53 blocks in the hand after the
  player renderer's 0.9375. The spearhead, cyan inlays, and gems are fullbright
  through NeoForge element light data. Pennant, `道` plaque, and tassels are
  static.
- Moves (`data/myvillage/combat/style/basic_spear.json`):

| # | Move | Kind | Total | Active | Chain | Damage × | Targets | Range | Hit shape |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 中平扎 mid thrust | thrust | 12 | 4–5 | 8 | 0.90 | 1 | 4.2 | `thrust` generator |
| 2 | 横扫 sweep | cut | 15 | 5–7 | 10 | 0.95 | 4 | 3.8 | 9 explicit samples, left to right |
| 3 | 上挑 rising flick | cut | 15 | 5–7 | 10 | 1.00 | 2 | 3.6 | 12 explicit samples (4 per tick), right-low to left-high |
| 4 | 劈枪 overhead smash | cut | 19 | 7–9 | 14 | 1.15 | 3 | 4.0 | 12 explicit samples (4 per tick), left-high to right-low |
| 5 | 游龙突刺 dragon lunge | thrust | 22 | 8–10 | 22 (no chain) | 1.30 | 3 | 5.0 | `thrust` generator, 1.6-block step |

Sounds reuse `myvillage:combat.sword.*`; moves 4 and 5 are heavy.

## Where each fact lives

All paths under `src/main/resources` unless they start with `tools/`.

| Fact | Single source |
|---|---|
| Item class, attributes, tab | `item/ModItems.java`, `item/CombatWeaponItem.java` |
| Moves, timing, hit shapes, feedback | `data/myvillage/combat/style/basic_spear.json` |
| Item → style, rig, contract | `data/myvillage/combat/weapon/lingxiao_spear.json` |
| Model, textures, icon, contract | `tools/gen_lingxiao_spear_model.py` (sole writer of `models/item/lingxiao_spear.json`, `lingxiao_spear_3d.json`, `textures/item/lingxiao_spear.png`, `lingxiao_spear_model.png`, `combat/lingxiao_spear_geometry.json`) |
| Third-person poses | `BASIC_SPEAR` table in `tools/gen_sword_pal_anims.py` → `player_animations/spear_combat.json` |
| First-person keys and off hand | `assets/myvillage/combat/lingxiao_spear_first_person.json` |
| Item and move names | `lang/en_us.json`, `lang/zh_cn.json` |

## Two hands

Third person (generated, PAL):

- The item rides `right_item` at the rear grip (`grip_center`, `y = -2`). A key
  with `larm=ON_SHAFT` (`off_hand_grip_center`, `y = 11`) or
  `OnShaft(at=y)` solves the rigid left arm so the left fist lands on the
  shaft there, or at the nearest reachable shaft point inside the handle and
  clear of the right fist. An explicit `larm=(yaw, elevation)` is a release.
- PAL arms are rigid, so the left fist reaches only a shaft pointing about 45
  to 120 degrees left of the chest. The guard is bladed (body turned right,
  left foot and hand leading).
- Both hands: guard, ready idle, mode entry, mid thrust (the shaft slides
  through the leading hand). Released for the swing and regripped in recovery:
  sweep (after the wind-up), flick (after the drop), smash (as the spear is
  raised), lunge (as the rear arm drives).
- `--check` fails on a left fist off the shaft at or between keys, the
  model's lowest element corner (barbs, star, pennant, plaque, tassels
  included) below 0.05 block, hands closer than 5 contract px, or the shaft
  within 0.5 player-model px of torso, head and neck, legs, or arms, each box
  including its skin's outer layer. The smash crosses the top on one arc, and
  the cut-sense test compares the samples' far ends with the posed head.

First person (rig data, `FirstPersonArmIk` two-bone solve):

- Both hands through the thrust, sweep, and flick. The smash releases at its
  start and regrips from tick 14; the lunge releases during its strike (ticks
  7.9 to 8.9) and regrips from 15.5 to 18.5.
- The second arm is drawn only while the off-hand slot is empty. With an
  off-hand item the vanilla off-hand pass draws that item instead.

## Rig off-hand schema

`rig.off_hand` (optional; without it the rig renders as before):

| Field | Meaning |
|---|---|
| `shoulder_offset` | The off shoulder is the main shoulder mirrored across the view's vertical plane, with this offset in place of `rig.arm.shoulder_offset` (+x outward). Default: the main arm's. |
| `grip_diagonal` | How far the shaft leans across the off palm, 0 to 50 degrees. Default: the main arm's. |
| `thickness` | The off arm's cross-section, 0.2 to 1.2, as `rig.arm.thickness` (0.29.0-fix1). Default: the main arm's. |
| `upper_arm`, `forearm` | The off arm's bone lengths in blocks, 0.1 to 0.6, as `rig.arm`. Default: the main arm's. |
| `rest_direction` | `[x, y, z]`, any non-zero length (normalised on load): the way the released hand rests from the off shoulder, in the off arm's frame (+x outward, +y up, -z forward). Default `[0.15, -1, -0.2]`: down beside the body, a little out and forward. |
| `rest_reach` | How far the released wrist rests from the off shoulder, as a share of the off arm's length (upper arm plus forearm), 0.3 to 0.97 (the solver's reach clamp, so the rest never moves the shoulder). Default `0.9`. |

`Rig.offArm()` builds the off arm from the block (bones, thickness, grip
diagonal, shoulder offset, with the main arm's follow-through); the rest is
read from the same block. The defaults are the values the solver used before
they were configurable, so a rig without the new fields draws as before. The
shipped spear rig sets only `shoulder_offset`, `grip_diagonal`, and
`thickness`. A modified copy of it with all four new fields is pinned in
`src/test/resources/first_person_off_arm_overrides.json`, which
`FirstPersonOffHandTest` and `tools/tests/test_combat_preview_parity.py` both
check (no shipped rig uses them, so the parity golden cannot). The spear draws its off arm at `0.56` against
the main arm's `0.42`: the leading hand holds the shaft farther from the eye
and shows its whole forearm, so at the main arm's size it read as a thin
stick. The thickness scales the fist, the wrist-to-grip distance, and the
closest the off hand comes to the main grip (6 skin px at the off arm's
thickness); the grip point on the shaft and the main arm do not change.
Per-key fields (interpolated like the others, ignored without the block):

| Field | Default | Meaning |
|---|---|---|
| `off_hand_slide` | 0 | Model px along the shaft from `off_hand_grip_center`, + toward the tip. Must keep the hand on the handle. |
| `off_hand_roll` | 0 | Degrees the hand turns about the shaft from its natural reach. |
| `off_hand_elbow` | 0 | Off elbow swivel, as `elbow`. |
| `off_hand_hold` | 1 | 1 holds the shaft, 0 lets go; between, the hand moves toward its rest (`rest_direction`, `rest_reach`). At 0 the arm is not drawn. |

A point out of reach slides the hand to the nearest reachable shaft point,
never off the shaft or into the main fist. The off arm takes no lag. A rig with
`rig.off_hand` on a contract without `off_hand_grip_center` fails to load.

## Contract fields

Both optional; the Qingfeng contract has neither.

- `off_hand_grip_center` `[x, y, z]`: on the weapon axis, inside `handle`,
  ahead of `grip_center`. The validator also requires one fist width from the
  grip at the model's third-person scale.
- `trail` `{"base": [...], "tip": [...]}`: the span that draws the 剑光
  trails, on the axis, base below tip, within butt bottom to head tip.
  Without it the trail is `head_base`..`head_tip`. The spear's is `y 16..32`
  (16 px; the head alone is 8.6 px, the sword blade 15.9 px).

The contract is format 2 (weapon-neutral names; the spear reads `butt` as the
butt cap, `handle` as the shaft, `collar` as the socket, and `head` as the
spearhead). The rename table is in
[Combat Data and Capture](34_combat_data_and_capture.md).

The first-person trail spans `trail`. The world trail's tip radius is `0.705`
(shoulder to grip) plus grip-to-trail-tip, and its length the `trail` span,
both at the model's third-person scale: spear tip radius about 2.62 blocks,
length 0.9; Qingfeng 1.7 and 0.795, as in 0.28.0.

## Hit sample rules

Found with the spear's multi-sample cuts; they apply to every style, and to a
move's optional world-trail samples (`trail.samples`) as to its hit samples.

| Rule | Why | Enforced by |
|---|---|---|
| Explicit samples in non-decreasing tick order | The world trail walks samples in list order | Java loader, `tools/combat_data.py` (`COMBAT_DATA_SAMPLE_ORDER`) |
| Once a tick has several samples, every active tick has the same number | The trail spaces a tick's n samples 1/n of a tick apart, so uneven counts change its speed at tick boundaries | `tools/combat_data.py` (`COMBAT_DATA_SAMPLE_COUNT`) |
| Every drawn frame of a cut's far end at or beyond the weapon's trail tip radius | The trail head is drawn at min(far-end distance, tip radius); a shorter far end bends the trail inward | validator, per weapon (`COMBAT_TRAIL_CUT_REACH`), on the samples the trail draws (`trail.samples`, else the hit samples) |

The spear's flick and smash far ends sit at least 2.65 blocks from the pivot
(tip radius 2.62). `gen_sword_pal_anims.py --report` prints, per move, how far
the posed spearhead is from the world-trail head: max 0.79 / 0.47 / 0.70 /
0.62 / 0.14 block for the five spear moves.

## Runtime rules shared with Qingfeng

- One client combat clock: `ClientCombatClock` counts client ticks in which
  the level ran; the first-person swing (`LocalSwingTimeline`), prediction and
  chain ticks, impact freezes, the attacker's stop, the world trail, camera
  kicks, and the arm lag read it. A server tick is converted once, when its
  START arrives. The client's game time, which the server's time packet
  re-sets every 20 ticks, is read nowhere else in `client/combat`
  (`COMBAT_CLIENT_GAME_CLOCK_READ`). A reset during a local action logs
  `PAL_SMOKE client_time_jump`.
- One stop per action: a remote attacker's third-person stop and world-trail
  freeze start on the first impact of an action only; each struck entity
  still freezes and shudders. In first person only the first confirmation
  starts a stop, and a confirmation for an action that is no longer current
  is dropped. Each confirmation logs `PAL_SMOKE fp_hit_stop ... result=`
  (`started`, `started_at_contact`, `ignored_stop_already_started`,
  `ignored_too_late_to_catch_up`, `ignored_not_current_action`).
- Re-equip (`CombatWeaponItem`, vanilla's rule minus the durability replay;
  the renderer keeps a stack that `ItemStack.matches` before asking):

| Old and new main-hand stack | Equip animation |
|---|---|
| Identical | no (vanilla) |
| Same item and count, only `minecraft:damage` differs (a hit's durability cost, or a switch to another copy) | no |
| Same item, another component differs (rename, enchantment) | yes |
| Another item or count | yes |
| One of them empty | yes (NeoForge answers before asking the item; both empty: no) |

`slotChanged` is not consulted.

## Regenerate and check

```bash
python3 tools/gen_lingxiao_spear_model.py            # write model, textures, icon, contract
python3 tools/gen_lingxiao_spear_model.py --check    # fail on drift or a failed self-check
python3 tools/gen_lingxiao_spear_model.py --report   # display derivation, grip fit, element count
python3 tools/gen_sword_pal_anims.py                 # write sword_combat.json and spear_combat.json
python3 tools/gen_sword_pal_anims.py --check
python3 tools/gen_sword_pal_anims.py --report        # per key; two-handed tables add the left hand's shaft y and offset
python3 tools/validate_sword_combat_foundation.py
python3 tools/validate_mod_items.py
python3 -m unittest tools.tests.test_gen_lingxiao_spear_model tools.tests.test_gen_sword_pal_anims tools.tests.test_combat_data
python3 -m tools.combat_capture run --label <label> --weapon myvillage:lingxiao_spear
python3 -m tools.combat_preview fp --weapon myvillage:lingxiao_spear --move all --key-ticks --out out/preview/combat_preview/spear_fp.png
.venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity
```

The first-person rig is hand-authored data; edit it and use
`python3 -m tools.combat_capture reload` in a running session, or `F3+T`.

## Offline preview tools

`tools/combat_preview/` (`python3 -m tools.combat_preview fp|pose|model`, see
its README) renders from the mod's resources with numpy and Pillow; without
them it re-executes under `$MC_PREVIEW_PYTHON` or `.venv-preview/`. Moved into
the repository after 0.29.0-fix1; for the same inputs they write the same
sheets, byte for byte, as the copies they came from.

- `fp`: first-person frames of a weapon's rig (item model, arm or arms, trail)
  at any tick. Checked against 25 Qingfeng and 25 spear in-game probe stills:
  silhouette XOR over union at most 0.5 %
  (`out/preview/lingxiao_spear/fp_preview_validation/`,
  `ingame_combat/fp_compare_spear_v1/`).
- `pose`: third-person PAL poses from the animation file and the item model
  (`out/preview/lingxiao_spear/pose_preview_validation/`).
- `model`: the item model in each display context.

The `fp` solver (pose sampling, grip frame, arm lag, main and off arm) is
pinned to the Java by `src/test/resources/first_person_preview_parity.json`:
`FirstPersonPreviewParityTest` (JUnit) and
`tools/tests/test_combat_preview_parity.py` (run with
`.venv-preview/bin/python`) both check it, for both weapons, every move, key
ticks plus every 2.5 ticks, and both main arms. A solver, rig, or contract
change rewrites it with `./gradlew test --tests
com.example.myvillage.client.combat.FirstPersonPreviewParityTest
-PupdatePreviewParity`, and the Python port must then pass. When the golden
was first written the port matched the Java within its rounding (5e-5 block).
The arm mesh, trail, and rasteriser are checked only against in-game stills.

## Evidence

Developer evidence under `out/preview/` (git-ignored; served from the
development host, port 8766 at the time of writing):

| Directory | Content |
|---|---|
| `lingxiao_spear/model/` | Offline model previews and the reference art |
| `lingxiao_spear/ingame_model/` | First in-game look (give, tooltip, tab, icon, hands, ground, frame, night) |
| `combat_capture/spear-v1/`, `lingxiao_spear/ingame_combat/` | First combat capture: stills and mapped-click combo |
| `lingxiao_spear/fp_runtime/` | Hit-stop and re-equip fixes, before and after, both weapons; `forced/`: game-clock resets forced with `/tick sprint`, `freeze`/`step`, rate changes, a Nether teleport, and a death |
| `lingxiao_spear/fp_rig_stage3/`, `lingxiao_spear/poses_v2/` | Offline sheets of the rig and the pose table |
| `combat_capture/spear-final*`, `combat_capture/sword-final*` | Final capture on `26b99e9`: stills, combos in the default, sweep, and line layouts at normal and quarter speed, quarter-view motion; Qingfeng regression against `sword-v2` |
| `lingxiao_spear/ingame_combat_final/` | Crops, world-trail frames from behind, and the offline-renderer comparison with the final stills |
| `lingxiao_spear/review/index.html` | Evidence page for the owner (Chinese), with the not-verified list |
| `combat_capture/spear-fix1-offarm`, `lingxiao_spear/off_arm_thickness/` | 0.29.0-fix1 off-arm thickness: first-person stills, in-game before and after, offline candidates (0.42 to 0.62), a wide-arm skin offline, and the offline-renderer comparison |

None of it is an owner verdict. Not verified anywhere: sound; a second client
(another player's view, one stop per action as others see it); real keyboard
and mouse play; the flick's and smash's world trails from the side; foot
sliding; frame rates on a real GPU; other skins, armour, and capes; a
game-clock reset from server lag, a camera kick running through a reset, and a
clean track of a +1 or +2 reset mid-swing.

## Findings

### Held as documented

- A second style file, weapon file, and index entries loaded with no Java
  change; move ids stayed unique across styles.
- Explicit samples and generators mixed in one style; the existing sound
  events were reused by id.
- The client attached the first-person animator to the new item because it
  has a weapon file; nothing in `combat/**` or `client/combat/**` names it.
- The geometry contract's frame and field set fit a spear (negative y, 48 px)
  with the sword names read as butt, shaft, socket, and head.
- The generator's one-table-and-output-per-style design, the validator's
  animation lookup across files and per-weapon rig checks, and the capture
  tool's `--weapon` worked as designed.

### Had to be extended

- Pose generator: it measured every table with the Qingfeng rig, had fixed cut
  paths and sword thresholds, and no two-hand concept. Now each table carries
  its weapon, rules, and cut paths, with an off-hand solve and long-weapon
  checks.
- Validator: generator drift, the 3D model, textures, and jar contents were
  checked for Qingfeng only, and its fixture held one weapon. Now per weapon
  (the contract's `generator` names the model generator), plus off-hand and
  trail checks.
- Java tests assumed one style and one weapon (`CombatTestData` now has both).
- Capture tool: cameras were framed for a short weapon, every command needed a
  rig, stills needed byte-identical grabs, a refused probe was silent until
  timeout, and there was no third-person motion recording. Now default views
  by held length, cropped sheets, a tolerant stability rule, fail-fast
  refusals, `shot`/`view`/`motion`, `combo --camera back`, target layouts
  (`--layout default|sweep|line`) with per-move hit attribution, quarter-speed
  runs (`--tick-rate`), the client's hit-stop log in the manifest, and an
  experience reset so stills after a combo compare with stills before one.
- Data rules: nothing said in which order explicit samples go or how many a
  tick may have, and nothing tied a cut's samples to the trail they draw (see
  "Hit sample rules").
- First person drew one arm; now the optional off-hand arm.
- Trails: the first-person trail used the blade span and the world trail a
  hard-coded 1.7-block radius with a clamped length, one sample per tick. Now
  both are sized from the contract, and same-tick samples are spread through
  the tick.
- The item class: a plain `SwordItem` replays the equip animation after every
  hit (see below); combat weapons are now `CombatWeaponItem`.

### Latent defects in the base that also affected the sword (fixed here)

- Hit-stop behind the frame on screen: a hit confirmation that arrived after
  the rig's contact tick started the stop at the start of the current tick, so
  the weapon stepped back, held, and swept on. Qingfeng's horizontal cut
  stepped back 114 px at normal speed in the before build. Fixed in
  `SwingClock` (the pose follows the latest action time seen).
- Re-equip after every hit: the durability cost resends the stack and NeoForge's
  default rule replayed the equip animation, so the weapon sank for 5 to 6
  ticks after each landed hit. Fixed by `CombatWeaponItem` (vanilla's rule
  minus the durability replay; see "Runtime rules shared with Qingfeng").
- The client's game clock as combat time: every client combat timeline read
  `Level#getGameTime()`, which the server's time packet re-sets every 20 ticks
  by a tick or more, so a +2 reset skipped the swing two ticks. Fixed with
  `ClientCombatClock`. Forced in game: `/tick sprint 20` re-set the clock by
  +20 as the flick started and the swing played through without a skip.
- One stop per impact instead of per action: a three-target sweep sent three
  impacts, froze a remote attacker about two ticks longer, and ran its world
  trail backwards. Fixed in `CombatImpactFx`; a late confirmation of a
  finished action is now also dropped in first person.
- Left-hand display entries were pre-mirrored by the model generator although
  the game mirrors at apply time. Correct for Qingfeng only because its
  rotations are 0 or 180; the spear generator writes the right-hand values.
  `tools/gen_qingfeng_sword_model.py` is unchanged and still has the
  convention.
- The third-person probe refused a missing animation without a log line, and a
  missing ready idle logged a refused transition every tick. Both now log once
  with a reason.

### Latent defects in the base, not fixed here

- `out_back` easing: `FirstPersonSwing.Pose.interpolate` returns the end pose
  once progress reaches 1, so an `out_back` segment arrives early and never
  overshoots. It affects five Qingfeng keys and two spear keys. Left as is
  because fixing it changes the accepted sword's look; the offline renderer
  reproduces it.
- Respawn and dimension change do not reset the first-person animator:
  `FirstPersonWeaponAnimator.stop` compares with the already-replaced player.
  Harmless now, since the swing runs out on its own clock.

### Consequences of the local clock

- A server tick enters the local clock once, when the START arrives. If the
  client's game time is k ticks off the server's at that moment, the whole
  move is drawn k ticks off (about −1 to +2 on the capture host); the server's
  STOP cuts the last k recovery ticks, or the next move's start is slewed.
  Before, the next time packet corrected it mid-move as a visible skip. Every
  resync in the final capture session was −1.00 tick on a combo's first move.

### Limits that remain

- PAL 1.1.4 parses a `bend` channel but does not render it, and the mod that
  renders bends has no 1.21.1 release. Third-person arms are rigid, so a long
  two-handed weapon cannot be held with both hands through wide swings.
- The `arc` generator draws one line per tick and the `diagonal` generator has
  no reach parameter; wide or long cuts need explicit samples.
- No shipped move uses `trail.samples` yet, so the spear's world trails still
  follow its hit samples; where they must reach far ground targets the trail
  head sits about half a block from the posed head. The radius is the same
  along a whole move.
- The step always stops a fixed 0.6 short of a target (`MAGNETISM_STANDOFF`).
- One `weapon_scale` for the whole weapon; the off shoulder shares the body
  offset.
- Sword-shaped names kept on purpose: the `combat.sword.*` sound events and
  the tool file names (`validate_sword_combat_foundation.py`,
  `gen_sword_pal_anims.py`).
- The F5 camera cannot hold a quarter view through a real combo, and at 4
  blocks a front quarter view cuts a tip more than about 3.1 blocks ahead.
- The offline previews' arm mesh, trail, and rasteriser, and the `pose` and
  `model` tools, have no parity test against the Java; only the `fp` solver
  does. `model` defaults to the Qingfeng geometry contract (pass
  `--geometry` for the spear).
- Third person with an off-hand item: the left hand stays posed on the shaft.
- The pennant side is not mirrored in the off-hand vanilla hold.
- The third-person body restarts a predicted move when the server's start
  arrives more than a tick late (existing behaviour).
- Seen in the final capture: thin first-person trails and a small spearhead at
  the thrust's and lunge's contact; the smash's impact is hidden from behind
  in third person; at the sweep's wind-up the butt collar projects onto the
  shoulder line from the front-right camera although it clears it by 3 px in
  3D; the rigid pennant points up in the smash's first-person contact frame.

### Recommended next steps

1. Draw the leading arm in third person with the first-person two-bone solve,
   so two-handed weapons keep both hands through swings.
2. Let the world-trail radius vary along a move (trail paths can now be
   authored apart from hit samples with `trail.samples`).
3. Fix the Qingfeng generator's left-hand mirroring before any tilt or roll is
   added to its display.
