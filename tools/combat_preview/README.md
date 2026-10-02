# Combat preview tooling

Offline renders of combat presentation from the mod's resources, with a numpy
software rasteriser. No Minecraft, no Gradle, no lock: a sheet takes seconds.
Each command writes a PNG sheet and a JSON report beside it (`<out>.json`) and
prints per-frame measurements. Output is developer evidence only.

Run everything from the repository root:

```bash
python3 -m tools.combat_preview fp|pose|model [options]    # <tool> -h lists its options
```

| Tool | What it draws |
|---|---|
| `fp` | First-person frames of a weapon's rig at any tick: item model, main arm, off arm (`rig.off_hand`), trail, crosshair and HUD outline, as the client's freeze probe shows them. A port of `FirstPersonSwing`, `FirstPersonSwordTransform`, `FirstPersonArmIk`, `FirstPersonArmLag`, `FirstPersonArmRenderer`/`FirstPersonArmModel`, `FirstPersonSwordTrail` and `SwordGeometry`. |
| `pose` | Third-person PAL poses from a `player_animations` file with the item model in the right hand: F5 back and front cameras as the capture tool frames them, orthographic side and top. |
| `model` | An item model in each display context (front, side, iso, hilt, tip, third-person hand, GUI), with the geometry contract's points marked. |

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
(`FirstPersonSwing`, `FirstPersonSwordTransform`, `FirstPersonArmIk`,
`FirstPersonArmLag`, `SwordGeometry`), a rig, or a geometry contract, rewrite
the golden from the Java, then bring `fp_rig.py` in step until the Python test
passes:

```bash
./gradlew test --tests com.example.myvillage.client.combat.FirstPersonPreviewParityTest -PupdatePreviewParity
```

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
