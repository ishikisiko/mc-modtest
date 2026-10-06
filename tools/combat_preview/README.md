# Combat preview tooling

Offline renders of combat presentation from the mod's resources, with a numpy
software rasteriser. No Minecraft, no Gradle, no lock: a sheet takes seconds.
Each command writes a PNG sheet and a JSON report beside it (`<out>.json`) and
prints per-frame measurements. Output is developer evidence only.

Run everything from the repository root:

```bash
python3 -m tools.combat_preview fp|pose|model|sweep|diff [options]    # <tool> -h lists its options
```

| Tool | What it draws |
|---|---|
| `fp` | First-person frames of a weapon's rig at any tick: item model, main arm, off arm (`rig.off_hand`), a `paired` weapon's mirrored second on the free off hand (0.39.1, `FirstPersonWeaponTransform.pairedItem`, in the parity golden as `paired_item`), trail, crosshair and HUD outline, as the client's freeze probe shows them. A port of `FirstPersonSwing`, `FirstPersonWeaponTransform`, `FirstPersonArmIk`, `FirstPersonArmLag`, `FirstPersonArmRenderer`/`FirstPersonArmModel`, `FirstPersonWeaponTrail` and `WeaponGeometry`. |
| `pose` | Third-person PAL poses from a `player_animations` file with the item model in the right hand (and, for a `paired` weapon, its mirror image on the left hand as `PairedWeaponLayer` draws it; `--paired auto|on|off`): F5 back and front cameras as the capture tool frames them, orthographic side and top. |
| `model` | An item model in each display context (front, side, iso, hilt, tip, third-person hand, GUI), with the geometry contract's points marked; `tp_pair` (both hands, added by default for a `paired` weapon). |
| `sweep` | Candidate values for rig fields side by side: `fp` frames for each value, close-ups where they differ, changed pixels against the shipped value, and a rig file per candidate. See "Tuning a rig value". |
| `diff` | Before/after evidence from two sets of stills (capture directories or PNG folders): side-by-side sheet, changed pixels and their bounding box per frame, close-ups. See "Tuning a rig value". |

## Interpreter

The tools need numpy and Pillow (`requirements.txt`). When the running
interpreter lacks them, the command re-executes itself under
`$MC_PREVIEW_PYTHON` if set, else `.venv-preview/bin/python` in the repository
root if present; otherwise it exits with the setup command:

```bash
python3 -m venv .venv-preview && .venv-preview/bin/pip install -r tools/combat_preview/requirements.txt
```

`.venv-preview/` is git-ignored. The sheets were checked pixel-identical to
the lab copies under numpy 2.5.3 and Pillow 12.3.0.

## Defaults

- `--root`: `src/main/resources` of this repository (repeatable, first wins; a
  mod jar works for `model`).
- `--vanilla-jar`: `build/moddev/artifacts/neoforge-<neo_version>-client-extra-aka-minecraft-resources.jar`,
  which a Gradle build writes; it supplies vanilla parent models and the
  default skins. Without it the command says so and `fp` draws a placeholder
  skin. `--no-vanilla` skips it.
- `fp` draws the capture client's default skin (`offline:CaptureDev`, slim
  Zuri) with the arm lag on, which is what the freeze-probe stills show.

## Examples

```bash
# the capture tool's five key ticks of every move, without point markers
python3 -m tools.combat_preview fp --weapon myvillage:lingxiao_spear --move all --key-ticks --no-marks \
    --out out/preview/combat_preview/spear_fp.png
# chosen ticks of one move, a candidate rig, the left main arm
python3 -m tools.combat_preview fp --weapon myvillage:qingfeng_sword --move 2 --ticks 0,3.3,4.9,6.25 \
    --rig /tmp/candidate_rig.json --main-arm left --out out/preview/combat_preview/sword_m2.png
# against a capture set: game | render | overlay | silhouette diff per first-person still
python3 -m tools.combat_preview fp --weapon myvillage:lingxiao_spear \
    --compare-capture out/preview/combat_capture/spear-final --out out/preview/combat_preview/spear_vs_game

python3 -m tools.combat_preview pose \
    --animations src/main/resources/assets/myvillage/player_animations/spear_combat.json \
    --animation basic_spear_04_overhead_smash --all-keys --item myvillage:item/lingxiao_spear \
    --show-axis --out out/preview/combat_preview/spear_smash.png

python3 -m tools.combat_preview model myvillage:item/qingfeng_sword --out out/preview/combat_preview/sword_model.png
python3 -m tools.combat_preview model myvillage:item/lingxiao_spear \
    --geometry src/main/resources/assets/myvillage/combat/lingxiao_spear_geometry.json \
    --out out/preview/combat_preview/spear_model.png
```

`model` looks up the Qingfeng geometry contract unless `--geometry` names
another one (or `none`), so pass the spear's contract for the spear. `pose`
finds `assets/<ns>/combat/<item>_geometry.json` by itself.

## Tuning a rig value

`sweep` picks a value offline; `diff` shows afterwards what the in-game stills
changed.

```bash
python3 -m tools.combat_preview sweep --weapon myvillage:lingxiao_spear \
    --set rig.off_hand.thickness=0.42,0.5,0.56,0.62 --frames 1:0,1:4,2:6,3:7.5,5:7.4 \
    --out out/preview/combat_preview/sweep_offarm_thickness
python3 -m tools.combat_preview diff out/preview/combat_capture/spear-final \
    out/preview/combat_capture/spear-fix1-offarm --out out/preview/combat_preview/spear_offarm_diff
```

`sweep` options:

- `--set <json.path>=<v1>,<v2>,...`: a field of the rig file, keys separated by
  `.` and list indices in brackets (`rig.arm.thickness`, `rig.shoulder[1]`,
  `neutral.off_hand_slide`, `moves.<move id>.keys[3].reach`). Each value is JSON
  (`0.5`, `[0.3,-0.27,-0.05]`, `true`) or a bare word (`out_back`); commas
  inside brackets do not split. A path missing from the base rig is accepted
  only when its parent object exists (it introduces an optional field).
  Repeating `--set` gives one column per combination, the first `--set`
  varying slowest, at most 16 columns. Two `--set`s may not address the same
  value.
- `--frames <move>:<tick>,...`: 1-based move numbers; the tick is a number or a
  capture key name (`idle`, `strike_start`, `contact`, `strike_end`,
  `recovery`). Default: `1:idle` (every move starts from the same neutral pose
  with no lag) and the contact tick of every move.
- `--rig` (the base, default the weapon's shipped rig), `--skin`, `--arms`,
  `--main-arm`, `--off-hand-occupied`, `--no-sleeve`, `--root`, `--geometry`,
  `--vanilla-jar` and `--no-vanilla` work as in `fp`, with `fp`'s defaults.
  `--cell` sets the frame width in the grid and `--threshold` the changed-pixel
  threshold (default 36, as `fp --key-threshold`).

Each candidate rig is the base file with only that value edited in the text
(`rigs/c<n>_<slug>.json`), so it can be copied over the shipped rig as a
one-token diff. It is loaded by the same loader as `fp`, so a value the game
would reject stops the command with the loader's message and the candidate's
name. Frames are rendered at 960x540 without point marks and compared with the
base rig's frame. A pixel counts as changed when its summed absolute RGB
difference exceeds the threshold.

Output in `--out`: `grid.png` (rows = frames, columns = candidates; the base
value's column is outlined in gold, and is added as an extra column when no
candidate equals it; the white box is the HUD, which covers that area in game),
`zoom.png` and `zoom/<frame>.png` (each frame cropped to the union of changed
pixels plus 24 px, enlarged by a whole factor, nearest neighbour),
`summary.json` and `summary.txt` (changed pixels per candidate and frame, and
the solver's warnings: shoulder clamped, off hand slid along the shaft, plus
any loud loader message). A candidate other than the base value that changes
no pixel in any frame is flagged `IDENTICAL`: the renderer ignores the path,
the value equals the loader default, or the chosen frames do not show it. When
every candidate is identical the sheet says `NOTHING DIFFERS` and the command
exits 1. The same command writes the same bytes.

`diff <before> <after>`: each argument is a `tools/combat_capture` directory
(its `frames/<view>/` stills, `--view`, default `fp`, in the manifest's order)
or a plain directory of PNGs. Frames pair by file name. Files present on one
side only, and pairs whose sizes differ, are listed on stderr, in the sheet
header and in `summary.json`. `--frames m1_idle,m3_contact` selects frames,
`--threshold` defaults to 36, and the bounding box is inclusive. Output:
`sheet.png` (before | after | changed pixels in red over the dimmed after),
`zoom.png` (the same three cropped to the box plus `--margin`) and
`summary.json`.

Worked example: the spear's off-arm thickness (0.29.0-fix1). The sweep above
writes `out/preview/combat_preview/sweep_offarm_thickness/`. Changed pixels
against the shipped 0.56:

| Frame | 0.42 | 0.5 | 0.62 |
|---|---|---|---|
| m1 t 0 idle | 11190 | 5768 | 6505 |
| m1 t 4 contact | 5613 | 2619 | 2740 |
| m2 t 6 contact | 4991 | 2515 | 2751 |
| m3 t 7.5 strike_end | 11172 | 5379 | 5755 |
| m5 t 7.4 strike_start | 4047 | 1951 | 2085 |

Every changed box lies on the off arm, and no frame reports a slid hand or a
clamped shoulder. The `diff` above on the in-game stills before (0.42) and
after (0.56) gives `m1_idle` 7119 px, box (351,363)-(602,533); `m1_contact`
3888, (555,339)-(683,495); `m3_strike_end` 11005, (151,274)-(351,539). These
are the counts measured by hand at the time. Outside the HUD box the offline
and in-game counts for m1 idle agree (5119 and 5081 px). The whole-frame
offline count is higher because the game's hotbar and hearts cover part of the
arm, while the offline frame only outlines them.

## Parity with the Java

The `fp` solver is a hand port, so it is pinned to the Java through one golden
fixture, `src/test/resources/first_person_preview_parity.json`. For every
shipped weapon, every move, the rig's key ticks, strike window and contact plus
every 2.5 ticks, and both main arms, it holds the sampled pose, the arm lag, the
grip frame, the main arm solved with that lag, and the off arm (joints,
rotations, wrist angles, grip height on the shaft, hold). Two tests check it:

```bash
./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest   # Java against the golden
.venv-preview/bin/python -m unittest tools.tests.test_combat_preview_parity               # Python port against the golden
```

Under an interpreter without numpy the Python test skips and names the
command above. After a deliberate change to the first-person solver
(`FirstPersonSwing`, `FirstPersonWeaponTransform`, `FirstPersonArmIk`,
`FirstPersonArmLag`, `WeaponGeometry`), a rig, or a geometry contract, rewrite
the golden from the Java, then bring `fp_rig.py` in step until the Python test
passes:

```bash
./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest -PupdatePreviewParity
```

The golden covers shipped rigs only. The off-arm fields no shipped rig uses
(`rig.off_hand` `upper_arm`, `forearm`, `rest_direction`, `rest_reach`) are
pinned by `src/test/resources/first_person_off_arm_overrides.json`, a modified
copy of the spear rig with its expected off-arm joints, checked by
`FirstPersonOffHandTest` and by the same Python test; a solver change that moves
them is fixed by hand from the Java test's failure message.

The tools read geometry contracts in format 2 only, like the game: a format 1
contract or a rig with `sword_scale` stops with the format 2 name (see
`docs/ai-kb/34_combat_data_and_capture.md`).

On the shared capture host every Gradle command takes the heavy-work lock:
`flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew ... -x generateAllStructures --console=plain`.

The golden does not cover the arm mesh, the trail, the item transform or the
rasteriser; those were checked against in-game freeze-probe stills with
`--compare-capture` (25 Qingfeng and 25 spear stills, silhouette XOR over union
at most 0.5 %; see `docs/ai-kb/35_lingxiao_spear.md`). `pose` and `model` have
no parity test.

## Not modelled

`fp`: breathing, hit-stop shake, chain and blend-out cross-fades, resync slews,
the equip drop, view bobbing and head-turn sway, FOV-changing fluids, other HUD
states. `pose`: skin outer layers, cape, armour, the idle arm bob on undriven
channels, `bend`, molang values, catmullrom and bezier easing.
