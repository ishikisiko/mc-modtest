## Context

The Lingxiao Spear is the first weapon added on top of the 0.28.0 combat data infrastructure (`docs/ai-kb/34_combat_data_and_capture.md`). It is deliberately a different weapon class from the Qingfeng jian: about twice as long, held in two hands, with a small blade at the end of a long shaft. The work is split into atomic tasks done by separate workers; this file is the shared contract. A worker that needs to deviate from a value marked **fixed** stops and reports instead of deviating. Values marked *initial* are starting points that the owning worker may tune and must report.

Reference art: `/home/ubuntu/code/mc/mc-modtest/image.png` (untracked; do not commit it).

## Fixed ids and paths

All paths are under `src/main/resources`.

| Thing | Value |
|---|---|
| Item id | `myvillage:lingxiao_spear`; zh_cn `凌霄枪`, en_us `Lingxiao Spear` |
| Style id | `myvillage:basic_spear` → `data/myvillage/combat/style/basic_spear.json` |
| Weapon file | `data/myvillage/combat/weapon/lingxiao_spear.json` |
| First-person rig | `myvillage:combat/lingxiao_spear_first_person.json` → `assets/myvillage/combat/lingxiao_spear_first_person.json` |
| Geometry contract | `myvillage:combat/lingxiao_spear_geometry.json` → `assets/myvillage/combat/lingxiao_spear_geometry.json` |
| Item model wrapper | `assets/myvillage/models/item/lingxiao_spear.json` (`neoforge:separate_transforms`; `gui` keeps a 2D icon) |
| 3D model | `assets/myvillage/models/item/lingxiao_spear_3d.json` |
| Model texture | `assets/myvillage/textures/item/lingxiao_spear_model.png` |
| Icon texture | `assets/myvillage/textures/item/lingxiao_spear.png` |
| Model generator | `tools/gen_lingxiao_spear_model.py` (sole writer of the five files above and the contract) |
| Third-person animations | `assets/myvillage/player_animations/spear_combat.json`, written only by `tools/gen_sword_pal_anims.py` |
| Animation ids | `myvillage:spear_ready_idle`, `myvillage:spear_mode_enter` |
| Move ids, in combo order | `myvillage:basic_spear_01_mid_thrust`, `myvillage:basic_spear_02_sweep`, `myvillage:basic_spear_03_rising_flick`, `myvillage:basic_spear_04_overhead_smash`, `myvillage:basic_spear_05_dragon_lunge` |
| Translation keys | `combat.myvillage.move.<move path>`; zh_cn 中平扎 / 横扫 / 上挑 / 劈枪 / 游龙突刺 |

## Model frame (fixed)

Same frame as the Qingfeng contract so the runtime reads it unchanged: the weapon runs along +Y on the axis `x = 8, z = 8`, the spearhead's flat normal is X and its edge (width) is Z, units are model pixels (16 per block).

- The model uses the full vanilla element range on Y: butt end at `y = -16`, tip at `y = 32` (48 px, 3 blocks at scale 1). In the hand it is drawn at about 0.85 to 0.9 scale, so the spear is about 2.6 blocks long, roughly 1.45 times the player's height.
- Contract fields keep their sword names and are read as: `pommel` = butt cap (枪鐏), `handle` = the shaft, `guard` = the spearhead socket and its ornament, `blade_base`/`blade_tip` = the spearhead. They must stack along +Y in that order.
- `grip_center` is the main (rear, right) hand on the shaft, *initial* `y = -2`.
- New optional field `off_hand_grip_center`: the leading (left) hand on the shaft, *initial* `y = 11`. A contract without the field describes a one-handed weapon.

## Look (from the concept art)

A dark navy-black shaft with gold collars carrying cyan inlays (three near the butt, one mid-shaft, two below the head); a gold butt cap with a cyan gem and a short cyan tassel; a gold socket with a star-shaped ornament and a cyan gem; a leaf-shaped spearhead in glowing ice-cyan with a pale edge and a dark-gold spine; below the socket a white pennant with a blue yin-yang emblem and a small gold `道` plaque with a cyan tassel. The glowing parts (spearhead, cyan inlays and gems) are fullbright through NeoForge element light data if the 21.1.233 model loader supports it. The pennant, plaque, and tassels are static geometry. The floating ribbons and particles in the art are not built.

## Style `myvillage:basic_spear`

Design intent: longer reach and slightly slower than the sword, thrust-led, a wide sweep that hits more targets, and a long piercing finisher. The numbers are *initial*; the style worker owns them and keeps the loader invariants.

| # | Move | Kind | Total | Active | Buffer | Chain | Damage × | Targets | Range | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | mid thrust 中平扎 | thrust | 12 | 4–5 | 4 | 8 | 0.90 | 1 | 4.2 | quick poke, small step |
| 2 | sweep 横扫 | cut | 15 | 5–7 | 5 | 10 | 0.95 | 4 | 3.8 | level arc, about 140° |
| 3 | rising flick 上挑 | cut | 15 | 5–7 | 5 | 10 | 1.00 | 2 | 3.6 | low to high, lifts the target |
| 4 | overhead smash 劈枪 | cut | 19 | 7–9 | 7 | 14 | 1.15 | 3 | 4.0 | high to low, heavy |
| 5 | dragon lunge 游龙突刺 | thrust | 22 | 8–10 | 8 | 22 | 1.30 | 3 | 5.0 | long step, pierces a line, heavy, cannot chain |

- `kind` stays `thrust` or `cut`. Hit shapes use the three generators where their fixed constants fit a spear, and explicit `samples` where they do not (the `diagonal` generator's reach is fixed near 2.6 to 2.75 blocks).
- Sounds reuse the existing `myvillage:combat.sword.*` events. No new sound assets.
- Item: a `SwordItem` on `Tiers.DIAMOND` like the other MyVillage swords, with spear attributes (*initial* attack bonus 4, speed −2.8), `stacksTo(1)`, in the `myvillage:main` tab after the swords.

## Two hands

- Third person: the item stays on `right_item`. The pose table marks the spear table as two-handed, and the generator solves the left arm so the left hand lands on the shaft at the off-hand grip point (or the nearest reachable shaft point), and checks the residual. Poses where the left hand deliberately leaves the shaft say so per key.
- First person, stage 1 (base only): the existing right-arm rig, no Java change. This is the data-only validation capture.
- First person, stage 2: an optional `rig.off_hand` block makes the renderer draw a left arm whose hand is solved onto the shaft. Rigs without the block render exactly as before.

## Workers and file ownership

Workers edit only their own files. Nobody but the lead commits. Nobody runs Gradle, Minecraft, or `tools.combat_capture session/run/stills/combo` unless their brief says they hold the heavy slot; this host has 4 cores and 7 GB, and heavy jobs run one at a time under `flock /home/ubuntu/code/mc/.mc-heavy.lock`.

| Worker | Owns |
|---|---|
| model | `tools/gen_lingxiao_spear_model.py`, its test, the five generated model/texture/contract files |
| style | `data/myvillage/combat/**` for the spear, `index.json`, move translation keys in `lang/*.json`, data tests |
| item | `item/ModItems.java`, the item-name translation keys, mod-item validator expectations |
| pal-generator | `tools/gen_sword_pal_anims.py` and its test (multi-table, per-table geometry, off-hand solve) |
| pal-poses | the spear pose table in `tools/gen_sword_pal_anims.py`, generated `spear_combat.json` |
| first-person rig | `assets/myvillage/combat/lingxiao_spear_first_person.json` |
| off-hand arm | `client/combat/FirstPerson*` Java, rig parsing, Java tests |
| docs | OpenSpec deltas and tasks, README, AGENTS, KB, CHANGELOG, version files |

## Evidence

Capture sets go to `out/preview/combat_capture/<label>/` through `python3 -m tools.combat_capture ... --weapon myvillage:lingxiao_spear`. The page is developer evidence. Sound, real keyboard play, a second client, and every owner verdict stay `not_verified`.

## Findings log

Each worker reports, for its part: what the 0.28.0 infrastructure handled as documented, what it could not express, and what was extended. The docs worker collects these into the KB note.
