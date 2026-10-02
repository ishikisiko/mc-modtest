# Combat Data and Capture Tooling

Factual note for the 0.28.0 combat infrastructure: move sets and weapons are
bundled data, the runtime picks a style by held item, the validator checks the
data, and the capture workflow lives in the repository. Qingfeng's five moves
did not change. 0.29.0 added the second weapon on top of it, the two-handed
Lingxiao Spear; what that needed is in
[Lingxiao Spear](35_lingxiao_spear.md).

See also:

- How the combat itself works: [PAL Sword Combat Integration](32_pal_combat_integration.md)
- Why the route is self-developed: [Combat Framework Comparison](33_combat_framework_comparison.md)
- The second weapon and the validation findings: [Lingxiao Spear](35_lingxiao_spear.md)
- Specs: [combat-style-data](../../openspec/specs/combat-style-data/spec.md),
  [combat-data-validation](../../openspec/specs/combat-data-validation/spec.md),
  [combat-capture-tooling](../../openspec/specs/combat-capture-tooling/spec.md)
  (archived change `add-combat-data-infrastructure`; 0.29.0 deltas in change
  `add-lingxiao-spear`)
- Capture tool usage: `tools/combat_capture/README.md`

## Where each fact lives

| Fact | Single source |
|---|---|
| Move timing, damage, hit shape, step, reaction, sounds, hit-stop, camera cues | `data/<ns>/combat/style/<path>.json` |
| Which item uses which style, rig, and geometry | `data/<ns>/combat/weapon/<path>.json` |
| Which styles and weapons exist | `data/myvillage/combat/index.json` |
| Third-person poses | one pose table per style in `tools/gen_sword_pal_anims.py`, each generated into its own file (`player_animations/sword_combat.json`, `spear_combat.json`) |
| First-person keys, strike window, contact tick, off hand | the rig named by the weapon file |
| Weapon geometry (grip, off-hand grip, blade, trail span) | the geometry contract named by the weapon file, written by the model generator its `generator` field names (`tools/gen_qingfeng_sword_model.py`, `tools/gen_lingxiao_spear_model.py`) |
| Item class | `CombatWeaponItem` for every item with a weapon file (`item/ModItems.java`) |
| Accepted Qingfeng numbers (regression pin) | `tools/tests/test_combat_style_baseline.py` and the Java equivalence test |

All paths are under `src/main/resources`. An id `ns:path` maps to
`data/<ns>/combat/style/<path>.json` or `.../weapon/<path>.json`; a rig or
geometry location `ns:combat/x.json` maps to `assets/<ns>/combat/x.json`.

## Loading

The files are read from the mod's own resources once, on first use, on the
client and on the dedicated server. Both sides run the same jar, so they hold
the same data without a sync payload. A datapack that shadows these paths has
no effect, and `/reload` does not re-read them. A missing file, an unknown
field, or a broken invariant is a startup error that names the file and field.

## Style file (schema 1)

Top level: `schema`, `id`, `combo_timeout_ticks`,
`minimum_intent_interval_ticks`, `animations.ready_idle`,
`animations.mode_enter`, and `moves` in combo order.

| Move field | Meaning |
|---|---|
| `id` | Move id. Also the PAL animation id and the key in the first-person rig. Unique across all styles. |
| `display_key` | Translation key. |
| `kind` | `thrust` or `cut`. Selects the trail form (streak or band) and the generator's pose checks. |
| `total_ticks`, `active_ticks`, `buffer_start_tick`, `chain_tick` | Server timing. `0 <= start <= end < total`, `start <= buffer < total`, `end < chain <= total`. `chain == total` means the move cannot chain. |
| `damage_multiplier`, `maximum_targets`, `range` | Damage contract. |
| `reaction` | `hitstun_ticks`, `slide_distance`, `lift`, `lateral_bias`. |
| `hitbox` | `shape_family`, `horizontal_tolerance`, `vertical_tolerance`, `samples`. |
| `step` (optional) | `tick`, `maximum_distance`, `support_depth`. |
| `feedback` | `swing_sound`, `swing_pitch`, `hit_sound`, optional `heavy_layer_sound`, `heavy_hit`, `hit_stop_ticks`, `camera_trauma`, `cut_roll_degrees`. Sounds are sound-event ids. |
| `camera` | `hit_pitch_kick`, `hit_roll_kick`, `hit_fov_punch`, `swing_lean_degrees`, `step_fov_surge`. |

`hitbox.samples` is an explicit list of
`{tick, start, end, horizontal_radius, vertical_radius}` or one generator,
sampled over the active ticks:

- `{"generator": "thrust", "first_range", "final_range", "radius"}`
- `{"generator": "arc", "range", "start_angle", "end_angle", "height", "radius"}`
- `{"generator": "diagonal", "descending", "radius"}`

The generators are the pre-0.28.0 helper functions with their constants, so the
five Qingfeng moves load the same sample values as before.

Explicit samples (0.29.0 rules): listed in non-decreasing tick order (Java
loader and `tools/combat_data.py`); once a tick has several, every active tick
has the same number (`tools/combat_data.py`); and for every weapon of the
style, a cut's far ends reach at least that weapon's world-trail tip radius at
every drawn frame (validator, `COMBAT_TRAIL_CUT_REACH`). Reasons in
[Lingxiao Spear](35_lingxiao_spear.md).

## Weapon file (schema 1)

`schema`, `item`, `style`, `first_person_rig`, `geometry`. Two weapons may
share a style and a rig and differ only in geometry. An item belongs to at most
one weapon. A main-hand item with no weapon entry is not a combat weapon. The
item itself must be registered as `CombatWeaponItem` (0.29.0): a landed hit
costs durability, and on a plain `SwordItem` the resent stack replays the
equip animation. Its rule is vanilla's minus that replay (only a change of
`minecraft:damage` alone keeps the weapon in place; `slotChanged` is not
consulted). `tools/validate_mod_items.py` enforces the class and the rule.

Geometry contract fields since 0.29.0 (both optional): `off_hand_grip_center`
(a second hand on the shaft; required by a rig with `rig.off_hand`) and
`trail` (`{base, tip}`, the span that draws the trails; default the blade).
The rig's optional `rig.off_hand` block and its per-key fields are described in
[Lingxiao Spear](35_lingxiao_spear.md).

## Runtime

- `CombatDataLoader` parses the files strictly (no duplicate keys, comments,
  unquoted names, or trailing content) into the definition records and throws
  `CombatDataException` with the file and field. `CombatStyles` is the
  registry: lookups by style id, by item or stack, and by move id.
- `AttackMoveDefinition` carries `kind`, `feedback` (sound ids), and `camera`.
  `BasicSwordStyle` and the index-aligned camera arrays are gone.
- Server: the session runs the style of the held weapon. An unregistered item,
  or a weapon of another style, during an action stops it with
  `WEAPON_CHANGED`. A swap between two weapons of the same style keeps the
  action. Between actions the session is reused, so the revision never goes
  backwards for the client.
- Network: `CombatImpactPayload` carries `moveId`; payload protocol is `7`.
  The two client-to-server combat payloads are still empty.
- Client: `FirstPersonSwingResources` loads one rig and geometry per weapon on
  every resource reload and logs
  `Loaded first-person swing rig <rig> (<n> moves) with sword geometry <geometry>`
  for each. An invalid rig or geometry puts only that weapon on the vanilla
  hold. `FirstPersonWeaponAnimator`, `FirstPersonArmRenderer`,
  `FirstPersonArmIk`, and `FirstPersonArmModel` replace the `Qingfeng*`
  classes.
- World trails take their size from the held item when it is a weapon of the
  move's style, otherwise from the first registered weapon of that style, so a
  START that arrives before the equipment update keeps the right size. Since
  0.29.0 the size comes from the contract: tip radius `0.705` plus grip to
  trail tip, length the `trail` span, both at the model's third-person scale
  (Qingfeng unchanged at 1.7 and 0.795 blocks).
- Client timing (0.29.0): every client combat timeline runs on
  `ClientCombatClock`, the client's own tick count; a server tick is converted
  once when its START arrives, and no other class in `client/combat` reads the
  game clock. A remote attacker's stop and trail freeze run once per action.
  Details in [PAL Sword Combat Integration](32_pal_combat_integration.md).
- A sound id with a valid format that is not registered is not a load error;
  the validator checks the ids against `sounds.json`.
- Entering cultivation mode with no registered weapon in hand plays the first
  style's mode-enter animation, as before.
- Server start logs
  `Combat data loaded: <n> style(s) [...], <m> weapon(s) [...]`.

Smoke probes (`/myvillage_pal_smoke`): `move <n>`, `first_person <n> <tick>`,
and `third_person <n> <tick>` resolve `<n>` (one-based) against the style of the
main-hand weapon. With no registered weapon they log
`PAL_SMOKE <probe> rejected reason=no_weapon` and change nothing. Since
0.29.0 a refused `third_person` hold also logs
`PAL_SMOKE third_person rejected reason=no_layer|missing_animation|trigger_refused`.
`third_person` holds the local player's full-body pose at that tick until
`third_person release`, `stop`, a real action start, or logout. A tick outside
`[0, total_ticks)` is rejected with
`PAL_SMOKE third_person rejected reason=tick_out_of_range`, and a refused probe
changes no pose. The probes log
`PAL_SMOKE first_person|third_person move=<n> tick=<tick>` and send nothing.

The hold is exact. PAL's `SpeedModifier` passes its accumulated remainder to the
animation as the partial tick, and at rate zero that remainder keeps whatever
value in `[0, 1)` it had when the freeze began, so a plain rate-zero hold shows
a pose up to one tick late and differs between runs. While a probe holds, the
layer therefore skips the inner tick and renders with partial tick zero, and
each hold first removes any fade modifier left from an earlier hand-over.

## Tools

- `tools/combat_data.py` (standard library only) is the shared reader:
  `load()` returns the data plus a list of issues, `load_strict()` raises on
  any issue, and `style_rel` / `weapon_rel` / `asset_rel` map ids to paths.
  It rejects unknown fields and checks the timing invariants, id uniqueness,
  and that the index and the directories agree. The Java loader stays
  authoritative for value ranges.
- `tools/gen_sword_pal_anims.py` reads each move's total, active window, chain
  tick, step tick, and kind from the style file. `POSE_TABLES` holds one
  `PoseTable` per style with its own geometry contract, 3D model, `PoseRules`,
  `cut_paths`, and output file; grip compensation, forward kinematics, and the
  reach and cut-path checks use that table's weapon. A `two_handed` table
  solves the left arm onto the shaft (`larm=ON_SHAFT`), and long-weapon rules
  check ground, hand-separation, and body clearance. The generator fails if a
  pose table and its style disagree on the set of moves. `--check` and
  `--report` cover every table.
- `tools/validate_sword_combat_foundation.py` validates the data files, then
  cross-checks every move against its PAL animation (length
  `total_ticks / 20` s), translation, and sound events, and every weapon
  against its item, item model chain and textures, 3D model, rig (`strike`
  covers the active ticks within three ticks; `contact` inside `strike`;
  `rig.off_hand` and off-hand pose fields), and geometry contract (including
  `off_hand_grip_center` and `trail`). It runs `--check` of the model
  generator each contract names (Qingfeng's always) and checks every weapon's
  files in a current jar. It holds no per-move numbers. Its Java source checks
  are limited to: PAL and client imports only under `client/combat`, empty
  client-to-server combat payloads, no vanilla `attack(` call in combat code,
  clientbound payloads without damage or health fields, no named item in
  combat code, and no `getGameTime()` in `client/combat` outside
  `ClientCombatClock`. It also reports the sample-order, per-tick count, and
  cut-reach rules (`COMBAT_DATA_SAMPLE_ORDER`, `COMBAT_DATA_SAMPLE_COUNT`,
  `COMBAT_TRAIL_CUT_REACH`).
- `tools/tests/test_combat_data.py` also expands every move's samples and
  requires that a standing mob straight ahead, from 1.0 block out to the
  move's `range`, is touched by some sample.

## Adding content

A weapon that reuses an existing style:

1. Register the item as `CombatWeaponItem` with its model through the
   mod-item route ([Mod Item Creation](22_mod_item_creation.md)).
2. Provide a geometry contract for its model, written by a model generator
   that the contract's `generator` field names (it must pass `--check`).
3. Add `weapon/<item>.json` naming the style, a rig, and the geometry, and list
   it in `index.json`.

A new move or style:

1. Write or extend the style file and list it in `index.json`.
2. Add a `PoseTable` for the style to `POSE_TABLES` in
   `tools/gen_sword_pal_anims.py` (its weapon's contract and 3D model, rules,
   cut paths, output file) and regenerate.
3. Add the moves to a first-person rig.
4. Add translations, and sound events if the ids are new.
5. Run the validator and tests, then capture and review.

The spear showed what needs no Java (style, weapon, index, explicit samples,
reused sounds, rig keys, a second pose table) and what did (a second hand,
contract-sized trails, the item class); see
[Lingxiao Spear](35_lingxiao_spear.md). A two-handed weapon now needs no Java
either: `off_hand_grip_center` in the contract, `two_handed=True` in its pose
table, and `rig.off_hand` in its rig. Wide or long cuts need explicit samples
(the `arc` generator draws one line per tick, `diagonal` has a fixed reach).

## Capture tooling

`python3 -m tools.combat_capture <command>` collects presentation evidence on
the headless host. It uses the Python standard library plus `Xvfb`, `xdotool`,
`ffmpeg`, and ImageMagick, and drives `./gradlew runAcceptanceServer` and
`./gradlew runClient -Pcombat_smoke_*`.

| Command | Result |
|---|---|
| `run --label L [--weapon ID]` | Whole pass: session, stills, combo run, page. About 6 to 8 minutes. |
| `session start` / `status` / `stop` | Xvfb, acceptance server, and one smoke client under a supervisor. |
| `stills --label L [--views V,...]` | Stills of every move at five key ticks in each view. |
| `combo --label L [--layout default\|sweep\|line] [--tick-rate N] [--camera first\|back]` | Mapped left clicks through the combo, an mp4, target health over rcon before and after, and each loss attributed to a move (`hits_by_move`); layouts place the three targets (one in reach; an arc; a line), `--tick-rate 5` runs at quarter speed, `back` films from F5 back. The client's hit-stop and resync log lines go to `fp_log`. |
| `motion --label L [--views V,...]` | Third-person video of each move's PAL animation played on the client (no server action, no hits). |
| `view VIEW`, `shot PATH` | Put the camera into one view; grab one stable frame. |
| `check`, `ticks` | Host programs and the weapon's data (held length, default views, rig); the moves and key ticks. |
| `reload` | Copies changed combat client resources into `build/resources/main`, presses F3+T, waits for the rig-loaded log line. |
| `compare A B --label L` | Pairs two capture sets by weapon, move, key, and view. |

Facts that matter when using it:

- Output goes to `out/preview/combat_capture/<label>/`. `out/preview/` is
  served publicly from the development host, so pages and manifests carry no
  paths, ports, or credentials.
- The session holds the shared heavy-work lock (`$MC_HEAVY_LOCK`, else
  `.mc-heavy.lock` beside the main checkout) for as long as the server or
  client lives. The host has 4 cores and 7 GB of memory; only one Gradle or
  Minecraft job should run at a time.
- State and the client game directory are under `run-combat-capture/`
  (ignored). The server uses `run-acceptance/` with a fresh superflat world and
  two free ports from 25610 upward; the checkout's own `server.properties` is
  restored at stop.
- Stills use the client freeze probes
  `/myvillage_pal_smoke first_person <n> <tick>` and
  `third_person <n> <tick>`, where `<n>` is one-based within the style of the
  weapon in the main hand. A refused probe stops the wait at once with the
  logged reason. Each still is kept once the frame holds still: two grabs
  byte-identical, or three grabs pairwise within 2 colour levels per channel;
  otherwise it is kept and flagged `[UNSTABLE]`.
- Default views follow the weapon's held length (contract `overall_y` span
  times the third-person scale): under 1.8 blocks (Qingfeng 1.225)
  `fp,tp_back,tp_front`; 1.8 or more (spear 2.7) `fp,tp_back_right,tp_front_left`,
  quarter views at the vanilla 4-block F5 distance with the look turned 50
  degrees off the body. Their sheets are cropped to one fixed box per view and
  enlarged; straight views and `fp` keep full frames.
- The manifest records the rig the client actually loaded (hash, off hand,
  equal to the source tree's copy or not).
- A quarter view cannot be held through a real combo (each action turns the
  body to the look), so quarter-view motion comes from `motion`, which is
  client playback only.
- The player's pitch tilts the third-person arm pose, so compared captures
  must use the same camera settings; the manifest records them.
- The scene sets the player's experience to 0, so stills taken after a combo
  that killed targets compare with stills taken before one.
- The combo run is the mapped-click and server-damage evidence. The freeze
  probes never substitute for it.
- There is no audio device on the host. Sound is not captured.
- The output is developer evidence. It records no owner verdict.

## Not done here

Datapack override or `/reload` of style data, a parameterized sword-model
generator, 3D models for the other three swords, move templates, and an
in-repository offline pose or rig preview (the preview tools used for the spear
are outside the repository; see [Lingxiao Spear](35_lingxiao_spear.md)).
Two-handed grips exist since 0.29.0, but third-person arms stay rigid. The
scale-up order is in
[Combat Framework Comparison](33_combat_framework_comparison.md).
