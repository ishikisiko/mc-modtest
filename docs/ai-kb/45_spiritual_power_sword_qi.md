# Spiritual Power and Sword Qi (0.46.0)

The first active-technique runtime connects a bounded spiritual-power pool to
one learned sword-family skill. Melee and movement dodge remain free. The
broader ranged-combat brief is background; this slice does not implement its
bleed, additional skills, spell projectiles, or flying-sword control.

## Sources of truth

| Fact | Owner |
|---|---|
| Stage capacity | `data/myvillage/myvillage/realm/*.json`, stage `spiritual_power_cap` |
| Recovery schedule and factors | `data/myvillage/qi/rules.json` |
| Technique, learning requirements, base cost | `tools/technique_catalogue/catalogue.json`, generated `technique/jianqi_jue.json` |
| Charge, travel, geometry, damage and feedback | `data/myvillage/combat/skill/jianqi_jue.json`, indexed by `combat/index.json` |
| Starter scripture availability | `world_sim/rules.json` `player.scripture_hall.public_techniques`, consumed by the existing scripture offering rules |
| Persistent balance | Existing profile v4 `currentSpiritualPower`; mutations only through `CultivationService` |

The new technique uses the existing grade/category manual item and its
technique component. It creates no item per technique. It is public, grade one,
has no elemental prerequisite, and requires Qi Refining I for normal study.
The generated catalogue now has 132 techniques; Basic Breathing remains the
separate hand-written registry entry. Other active techniques and body effects
remain data without an executable runtime.

## Power lifecycle

`cultivation/qi/SpiritualPower`, `QiRules`, `QiRegeneration` and
`SpiritualPowerRuntime` separate pure arithmetic from server lifecycle work.
Spending is atomic; insufficient or invalid costs leave the profile unchanged.
Recovery uses the running core-technique factor, accumulates fractional gain,
and discards gain at capacity rather than banking it. The meditation multiplier
applies only during actual meditation, not study, preparation or advancement.
The legacy reserve and cultivation gain remain separate.

A successful spend delays the next recovery; a rejected spend does not.
Login, respawn and dimension changes restart the full recovery delay. Logout
and death clear transient clocks. There is no offline gain or free refill.
Legacy values above the stage cap and stage reductions are clamped through the
service; explicit out-of-cap administration is rejected. The profile codec is
unchanged. Invalid startup power rules fail loading; invalid reloads retain the
last valid rules, and valid reloads clamp and restart the delay.

`QiBarLayer` reads only the existing profile snapshot and synchronized realm
registry. Since 0.46.2 it shows a jade seal, current/capacity and power bar in
the upper-left corner in cultivation combat mode or briefly after a balance
change, with a gold charge indicator and refusal flash.
`QiBarLayout` contains the separately tested screen arithmetic.

## Learned sword skill

`CombatSkillDefinition` is bundled skill data. The linked active technique owns
its base cost; charge scales cost, width and damage. Weapon and school family
resolution authorizes the action, without a named-item branch. `CombatSkillSession`
tracks charge/release and wave travel alongside the existing `CombatSession`.
`CombatWaveResolver` handles advancing collision, and the existing damage policy,
dodge window and per-session target bookkeeping remain authoritative.

`CombatChargeIntentPayload`, `CombatReleaseIntentPayload` and
`CombatCancelChargeIntentPayload` are empty. The
server determines elapsed charge, direction, cost, damage and collision. Release
or full charge starts one action; a duplicate release cannot deduct twice.
Invalidating the charge cancels without spending. Opening a GUI or losing
window focus sends one cancel intent; `QiUseLatch` requires a key-up before
another charge press. The server cancels only a still-charging session, never
refunds a released action, and never lets this intent interrupt its wave. Client input intercepts the
use action only for a learned matching skill in cultivation combat mode;
ordinary scripture and NPC interactions retain their existing path. Presentation
uses server state and `ClientCombatClock`, never a client damage calculation.
Protocol 17 adds the clientbound skill phases. `SwordQiWaveRenderer` draws a
crescent mesh between received positions without extrapolating through a wall.
Since 0.46.2 its thicker body and brighter leading edge improve visibility;
the mesh stays inside the received authoritative wave bounds. This changes
presentation only, not damage, collision, charge cost, speed or range.
In 0.56.0-fix1 the authored half-width/half-height increase from 1.1/0.25 to
2.0/0.45. Both the server envelope and the existing crescent renderer consume
these values: uncharged width/thickness is 4.0/0.9 blocks and full charge is
6.0/1.35. This is an explicit collision-area change, unlike the earlier cosmetic
polish. Shape, damage, target cap, cost, speed and range retain their existing rules.
Wider edges still stop at walls; the full-charge horizontal blade clears flat ground.
`--preset sword-qi-area` tests authored collision/mesh bounds and mapped lateral,
vertical, outside-envelope and wall cases without unrelated full-release scenarios.
Charge uses enchant particles and the HUD; release originally reused the diagonal first/third-person attack clip. In
0.56.0-fix2 it selects the existing horizontal cut and emits at tick 5, where the
authored blade crosses forward. Optional `reaction_move` retains the previous
hitstun/slide/lift independently from the animation; older skill files fall back
to `feedback_move`. Java and Python data validation cover both references.
The presentation-only profile `assets/myvillage/combat/sword_qi_wave.json` owns
rim colors, thin cross-section, curve and three separated fading wisps. It replaces
the swollen wedge while preserving fix1's authoritative width/height. RELEASED
seeds the first visual segment from the server launch point; WAVE packets alone
advance it, with no pre-release drawing or extrapolation beyond a wall. Direction (yaw and pitch) is frozen at release.
The full swept envelope is conservative near oblique walls; an edge obstruction
stops the whole wave. Received damage, weapon/slot changes and incompatible
actions cancel a charge; cancellation after release does not refund the cost.

## Commands and evidence

All four cultivation/xiulian and qi/lingli route combinations are equivalent:

```mcfunction
/myvillage cultivation qi [target]
/myvillage cultivation qi set <target> <amount>
/myvillage cultivation qi fill <target>
/myvillage xiulian lingli shezhi <target> <amount>
/myvillage xiulian lingli buman <target>
/myvillage cultivation manual @s myvillage:jianqi_jue
```

The manual command is an administrator fixture. Normal play borrows the public
manual from the sect scripture shelf, studies it to completion, equips a
sword-family weapon, enters cultivation combat mode, then holds and releases
use (default right mouse). Holding to full charge releases automatically.
Grade-one study now takes about 10 seconds including preparation at default
affinity and 20 TPS; see [Study numbers](41_technique_system.md#study-numbers).

```bash
python3 -m tools.combat_capture sword-qi --journey --language en_us --out out/preview/sword_qi_journey
python3 -m tools.combat_capture sword-qi --hud --language en_us --out out/preview/sword_qi
python3 -m tools.combat_capture sword-qi --hud --reuse-world --language zh_cn --out out/preview/sword_qi_zh
```

This capture uses real mapped mouse input, RCON fixtures/readback, server
`SWORD_QI` logs and MP4 recordings, without DevBridge. Its manifest distinguishes
admin-prepared boundary trials from the acquisition loop. `--journey` uses
real dialogue, borrowing and study after preparing an eligible prospect,
without admin join, manual grant or learn commands. Run it before boundary
fixtures. `--hud` captures idle/charge at three window sizes; session language
defaults to `en_us`, with explicit `--language zh_cn`. `--reuse-world` preserves
only the isolated capture world between sessions, enabling restart checks,
and fails early when it is absent. Capture and stage-1 preparation share
`run-acceptance`: clean stage-1 must run before the save/restart sequence.
Its default cleanup removes the whole profile, including the capture world;
`--no-clean` preserves state but does not prove a clean startup baseline. A pose probe
or server-ready line is not evidence of a successful cast.

The persistent [test-environment and timing report](../testing/2026-10-14-spiritual-power.md)
summarizes measurements, failures, reruns and memory-sampling limits.
The run's measured command ledger is `reports/qi-sword-wave-tests/timings.jsonl`.
It records UTC boundaries, command, wall time, status, cache context, CPU time
and peak RSS where measurable, keeping failures and reruns. Session and trial
manifests separately retain readiness and action durations. RSS scope and warm
caches must accompany any machine comparison; the measurements are observations
of this host, not a hardware capacity guarantee.

Observed developer evidence includes the acquired-skill loop, 11 boundary
trials, final menu cancellation, exact-zero melee/dodge, 12 bilingual HUD PNGs,
resource reload, two-client release/wave presentation and same-world restart.
The restart uses a separate labelled admin lifecycle fixture because clean
acceptance preparation deleted the original capture world; it is not a
continuous restart of the natural-acquisition journey. See the timing report
for that failed attempt, rerun and the distinction between command and phase
times. Observer charge-particle clarity remains unverified because target
cleanup particles contaminated the image.

The README acceptance ledger is the user-facing result. Owner look, sound,
feel, FPS and unobserved multiplayer/lifecycle behavior remain `not_verified`.
The older developer observations above describe 0.46.0. The 0.46.2 furnishing,
HUD and crescent polish requires a separate owner review; building the jar
does not establish their in-game appearance. No game is launched as part of
the owner's build-only request.

## See also

- [41_technique_system.md](41_technique_system.md), [42_movement_dodge.md](42_movement_dodge.md), [43_player_sect_entry.md](43_player_sect_entry.md), [34_combat_data_and_capture.md](34_combat_data_and_capture.md)
- [spiritual-power-pool](../contracts/cultivation.md#spiritual-power-pool), [sword-qi-technique](../contracts/cultivation.md#sword-qi-technique), [cultivation-player-profile](../contracts/cultivation.md#cultivation-player-profile), [combat-capture-tooling](../contracts/tooling.md#combat-capture-tooling), [validation](../contracts/tooling.md#validation)
- [INDEX.md](INDEX.md)

Area-fix developer evidence: [0.56.0-fix1 record](../testing/2026-10-16-sword-qi-area.md).

Horizontal thin-blade follow-up: [0.56.0-fix2 evidence](../testing/2026-10-16-sword-qi-horizontal-blade.md).

## Charging stance (0.56.0-fix3)

Optional `charge_pose_ticks` selects the preparation prefix of the skill's feedback
animation; it defaults to zero for old skill files. Sword qi uses the horizontal
cut's left-waist coil at tick 3, before the first-person strike begins at 3.3.
Since 0.56.0-fix4 the grip sits at waist height and the blade points nearly
horizontally backward. First person keeps the hilt low-left within view; the
blade tip is behind the camera. This is the shared horizontal-cut preparation,
so ordinary combo move 2 also uses the new coil.
Accepted CHARGING drives the first-person shared item/arm sampler and the remote
PAL layer through that prefix and holds it. No probe or separate client hit authority
is used. The partial tick is pinned when PAL reaches the held key.

On release, the existing charge byte reconstructs the completed prefix. With a
positive charge pose, charge duration is bounded to 255 ticks so integer server
charge ages reconstruct exactly. Both `CombatSkillSession.waveActive` and the
external attack window use `release_ticks - completed_prefix`. The animation/trail
starts at that prefix, so total visual contact remains at tick 5, with two ticks
remaining after a fully prepared coil. Quick taps use their partial preparation.
GUI/weapon/damage cancellation and lifecycle removal clear the held pose; the local
hand captures its last displayed pose for the normal blend-out. No pose data enters
server damage, cost or target selection.

`--preset sword-qi-charge` checks timing arithmetic, schema compatibility, client
animation/input regressions, then mapped casts/cancellations and a second client's
normal-speed charging/release observation. Actual evidence is recorded separately.

Charging stance evidence: [0.56.0-fix3](../testing/2026-10-16-sword-qi-charge-stance.md).

Waist-draw visual and parity evidence: [0.56.0-fix4](../testing/2026-10-16-sword-qi-waist-charge.md).
