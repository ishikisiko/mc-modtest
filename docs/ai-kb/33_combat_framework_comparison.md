# Combat Framework Comparison (Combat Lab)

Factual record of the combat-lab experiment (2026-09-29 to 2026-10-01). The lab
compared the self-developed Qingfeng sword combat with Better Combat and Epic
Fight, using the same sword, scene, and capture flow, to choose a direction for
combat as a core pillar of the mod. It ran outside this repository and outside
OpenSpec. This note is the tracked summary of what it found and what was
decided.

See also:

- Implementation note: [PAL Sword Combat Integration](32_pal_combat_integration.md)
- OpenSpec: [`sword-combat-foundation`](../../openspec/changes/add-sword-combat-foundation/specs/sword-combat-foundation/spec.md)
- OpenSpec: [`player-animation-integration`](../../openspec/changes/add-sword-combat-foundation/specs/player-animation-integration/spec.md)
- Owner-verdict records: `openspec/changes/add-sword-combat-foundation/tasks.md` items 11.7, 12.16, and 13.9

## Sources

| What | Where |
|---|---|
| Lab folder (original, not a git repository) | `/home/ubuntu/code/mc/combat-lab` |
| Copy inside this repository | `out/preview/combat_lab/`, same layout as the lab; `COPY_MANIFEST.md` lists what was left out |
| Full report (Chinese) | `out/preview/combat_lab/report/combat_lab_report.md`; section 0 is the handoff to this repository |
| Grip and sword-model round | `out/preview/combat_lab/report/grip_round_2026-09-30.md` |
| Station reports | `out/preview/combat_lab/stations/*/STATION_REPORT.md`, `stations/D_ef_custom/NOTES.md` |
| Deviations from the lab plan | `out/preview/combat_lab/ops/DECISIONS.md` (D1 to D13) |

`out/` is ignored by git, so the copy exists only on the development host. A
fresh clone has this note and nothing else from the lab.

## Decision

The lab's rule was that captures and reports are evidence and the owner decides
after watching them. The owner's words are quoted without inference.

| Date | Event | Owner's words |
|---|---|---|
| 2026-09-29 | Stations A, B, and C compared | none |
| 2026-09-30 | The lab report records the direction as self-developed (A), not Epic Fight or Better Combat | After watching A next to Epic Fight: A "现在不太行看上去"; "我要的是那种战斗真实动作游戏的感觉" |
| 2026-09-30 | 0.27.0 action-feel revision (`f90240c`), station E | "自研的动作好一些了现在，但是握持这部分完全不行现在就像插入肉里的，非常僵硬。剑的建模也不太行可以优化一下。" |
| 2026-10-01 | 0.27.1 grip and 3D sword model (`f1988d3`), station F | After viewing the 0.27.0 vs 0.27.1 comparison page: "感觉271看上去可以"; then "暂时就这样" |

The 0.27.1 words followed a comparison page, not real-client play. The
real-client ledger in `README.md` keeps its own `not_verified` rows.

## Stations

All stations ran NeoForge 21.1.233 on Minecraft 1.21.1, at `960x540`, captured
at 30 fps on Xvfb with software rendering. The scene was a flat world with
three NoAI iron golems in survival mode. Jar URLs and SHA-256 values are in
report section 11 and `downloads/*/MANIFEST.md`.

| Station | Mods | What was recorded |
|---|---|---|
| A | myvillage 0.26.2 (`686637e`), PAL 1.1.4, GuideME 21.1.17 | Self-developed baseline, first person and F5 |
| B | A plus Better Combat 2.4.0, playerAnimator 2.0.4, Cloth Config 15.0.140 | Zero configuration; a five-move datapack; the same datapack referencing our PAL animations |
| C | A plus Epic Fight 21.15.8 | Zero configuration; an optional datapack; a feature clip (lock-on, roll, skill, guard) |
| D | C plus a data-only jar | One custom Epic Fight thrust, authored with Blender 3.6.23 and the official add-on |
| E | myvillage 0.27.0 development build | Evidence for the action-feel revision |
| F | myvillage 0.27.1 development builds | Evidence for the grip and 3D sword model, compared with E per move |

## Findings

### Better Combat (ARR)

- Zero configuration: an item-id regex (`sword|blade`) gives Qingfeng the
  default three-move sword. The other three MyVillage swords match too.
- A datapack of 2 files and 60 lines reproduces the five moves' damage
  (measured 6.3 / 6.65 / 7 / 7.7 / 8.75, 36.4 in total, equal to station A).
- It can reference our five PAL animations unchanged (2 files, 75 lines). The
  result has stepped interpolation and hit timing that does not match Better
  Combat's upswing convention. The conversion was estimated, not done.
- It cannot express per-move total duration (one 12.5-tick cooldown), oblique
  hit shapes, the move-five lunge, or a per-move target cap.
- Targets are chosen on the client. `server_target_range_validation` is `false`
  by default.
- First person shows the sword only. Our first-person viewmodel does not run.
- Misspelled datapack fields are ignored without a log line, and weapon
  attributes load only at server start (`/reload` has no effect).

### Epic Fight (GPL-3.0-or-later code, ARR assets)

- Zero configuration: an item-id regex (`.*_sword`) puts Qingfeng in the Epic
  Fight sword class with its own three-move combo at 7 damage per hit. The
  other three swords and `rideable_flying_sword` match too.
- It replaces the whole combat feel: stamina, lock-on, roll, guard, weapon
  skills, a full-body first person, and a forward step of about 0.7 to 1 block
  on every attack.
- The report rates its look as the best of the three.
- Station D added one custom move without Java, through a data-only jar and
  headless Blender. Not carried over or not checked: our 0.90 damage
  multiplier, per-phase damage, target cap, and oblique shapes. The animation
  was six scripted keys, so hand-authoring time was not measured. The licence
  for animation JSON built on the GPL rig was not confirmed.
- F3 frame rate on the software renderer was about 29 to 30 fps against 45 fps
  for station A.

### Self-developed

- Only this route has all of: server-authoritative timing, per-move durations
  (11 / 13 / 15 / 17 / 20 ticks), oblique hit shapes, and the move-five lunge.
- Measured cost of 0.26.1 plus 0.26.2 (`a9610a6^..686637e`, net): 8747 lines in
  100 files. Java main 3947, validators 1200 plus 488 of tests, OpenSpec 1056,
  docs 849, Java tests 824, resources 258.
- Rounds E and F brought iteration down to a 5-second hot reload for data
  changes and about 6 minutes for a complete evidence pass.

### Coexistence with MyVillage systems

Recorded, not fixed.

| Surface | Better Combat | Epic Fight |
|---|---|---|
| R combat-mode key | Toggles normally | Same key: one press toggles both mods, in opposite directions |
| Left click | Always taken by Better Combat; in cultivation mode five clicks deal 7 each and our moves never start | Follows R: Epic Fight in its mode, our five moves after R |
| Swing interrupts meditation | No (progress keeps rising) | Yes, but through the forward step counting as movement |
| Meditation keys, flying sword | Normal | Normal |
| GuideME G key | Normal | Same key as lock-on; both work |
| Startup and run logs | No mixin failure or conflict | No mixin failure or conflict |

### Licence

| Mod | Licence | Copy code into this MIT repository |
|---|---|---|
| Better Combat | ARR, source public | No; runtime dependency only |
| playerAnimator (KosmX) | MIT | Yes |
| Cloth Config | LGPL-3.0-only | Library dependency only |
| Epic Fight | GPL-3.0-or-later; assets ARR | No code and no assets; runtime dependency only |

Better Combat's playerAnimator and our Player Animation Library are different
libraries with different packages and mod ids. They loaded together without
conflict lines.

## Capture Biases and Unverified Items

- NoAI golems take no knockback, so Epic Fight's forward step carries the
  player through the target. In station C's first-person clip the last three
  hits are off screen; health readings confirm five hits.
- The host has no audio device. No swing or hit sound was recorded.
- Capture tops out at 30 fps. All three stations reached that during motion,
  so smoothness above 30 fps was not compared.
- Real input feel (latency, chain rhythm) and hit-stop were not judged from the
  clips.
- The cost of fixing either framework's conflicts was not estimated.

## Lab Tooling

The repository's acceptance flow (`-Pcombat_smoke_*` server plus a
physical-client smoke) stays the formal one. The lab harness is a faster way to
collect evidence. Its scripts still point at the lab folder.

| Tool | Lab path | Use |
|---|---|---|
| Development resource pack | `harness/lab.py devpack` | Rig, model, texture, and contract edits take effect on F3+T in about 5 seconds, without a rebuild |
| Grip capture | `harness/grip_shots.py` | Per-move stills at fixed ticks: first person through `/myvillage_pal_smoke first_person`, third person through a barrier wall and `tick rate 5` bursts |
| Before/after sheets | `harness/tools/compare_grip.py` | Pairs two captures by move and moment |
| Offline model preview | `harness/tools/preview_item_model.py` | Renders a JSON item model in about a second |

The repository has no third-person freeze probe. The lab worked around that
with slow-motion bursts.

## Scale-Up Assessment

These are the lab lead's estimates from 2026-10-01. Nothing here was measured.

- More swords on the same five moves: about a day to turn
  `tools/gen_qingfeng_sword_model.py` into one parameter file per sword, then
  hours per sword. The grip code follows the geometry contract, not a
  particular sword.
- The Java side is the blocker. `ModItems.QINGFENG_SWORD` is referenced 13
  times in 8 files outside `ModItems` (at `f1988d3`), and `BasicSwordStyle.DEFINITION`
  is the only style. A registry that selects style, rig, and geometry by held
  item or tag was estimated at 2 to 3 days, once.
- New moves or a new style: about 1 to 2 weeks for five moves. More than half
  is hand-tuning the first-person rig (7 to 8 keys of 12 parameters per move,
  3 to 5 capture rounds each).
- Suggested order before scaling: the style registry; a parameterized sword
  generator with 3D models for the other three swords; then deriving a
  first-person rig draft from the third-person generator's blade-tip path. The
  last one carries design risk.

## Open Items

- A 1-pixel dark green line on the wrist's upper edge at the end of the upward
  cut, visible at 6x. It is suspected to be skin-texture bleed at a face
  border. It needs a retake with another skin; a UV inset on the arm boxes
  would fix it.
- Slim skin and left-hand hold were not captured.
- Sound, real-client feel, and wrist motion in play remain unverified.
