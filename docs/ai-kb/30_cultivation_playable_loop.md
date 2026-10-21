# Cultivation Playable Loop

0.75.0：炼气九层到筑基改为服筑基丹概率突破；其余阶段保留确定性突破。根基与补全规则见[筑基丹](../features/foundation-pills.md)，下文 deterministic 仅描述其余阶段及历史机制。

0.64.3 起，灵石修炼和 H 面板计数统一使用 `c:spirit_stones/low_grade`；跨物品混合支付保留原事务和原堆叠回滚。宗门供奉使用同一标签。 标签结构与兼容边界见 [资源合同](../contracts/assets.md#灵石公共标签)。


This note records the first playable cultivation loop, its five serial
foundation changes, the affinity/UI revision, and the 0.48.0 progression extension through Golden Core late. It extends the
foundation and initiation work; it does not replace the
profile, registry, persistence, command, or two-stele contracts described in
[Cultivation Core Foundation](28_cultivation_core.md) and
[Cultivation Initiation Ritual](29_cultivation_initiation_ritual.md).
The in-game summary and its separate interaction boundary are recorded in
[GuideME Cultivation Guide](31_guideme_cultivation_guide.md).

## Shipped Boundary

The dependency order is deliberate:

```text
spirit-stone resources
  -> profile v3, lifespan, and shared calendar
  -> transient meditation state
  -> affinity/direct-stone Basic Breathing settlement
  -> deterministic advancement through Golden Core late
```

The original resource change introduced stones without consumption; the current
gain revision makes them direct server-owned meditation inputs. The time change
does not start meditation, the shared state machine owns eligibility, gain stops
at the cap, and advancement performs exactly one transition. Golden Core late is
the release ceiling. The player chain contains fifteen transitions through
Qi I-IX and early/middle/late Foundation and Golden Core. Nascent Soul player
advancement, pills, facilities, environment requirements, combat/exploration
rewards, tribulation, death from old age and reincarnation remain outside this
loop. Spiritual-power recovery is implemented separately in note 45.

## Authority And Persistence

The logical server owns the complete loop. The client can send only one bounded
action enum: start normal meditation, start spirit meditation, stop, or start
advancement. Sender identity, position, eligibility, duration, inventory,
resource use, stage target, profile replacement, and result are server-derived.
No client-authored coordinate, velocity, profile value, target, or success value
is accepted.

Persistent profile mutation remains an immutable replacement through
`CultivationService`. Profile schema v3 retains every v2 field and adds:

```text
spiritualAffinity = 10 by default
```

The version-dispatched codec retains exact v1 and v2 shapes, preserves existing
fields, unknown ids, over-cap progress, lifespan, and reserve, assigns affinity
`10`, and writes only v3 afterward. `meditationQiReserve` remains stored but is
inert and hidden; v3 gain never credits or spends it. The attachment still uses
`copyOnDeath`; no cultivation `PlayerEvent.Clone` copy path is added.
V3 stability is a non-negative integer without the historical fixed `100`
schema ceiling; its current gameplay cap comes from the resolved stage.
Meditation/advancement sessions, the mastery remainder, warning
de-duplication, and pending lifespan batches remain transient server memory.

H remains non-pausing. Since 0.31.0 it is a panel with a page rail (Profile,
Meditation, Techniques; see [Cultivation Panel](37_cultivation_panel.md)). Profile values,
time status, and session status are clientbound/read-only. Four Meditation-page
buttons send only the existing normal/spirit/stop/advance enum used by V/B/X/N;
the server derives all rates, costs, targets, inventory changes, and results.
Client caches clear on disconnect and never decide eligibility or install a
profile.

## Spirit-Stone Resources

The resource slice adds:

```text
myvillage:low_grade_spirit_stone
myvillage:spirit_stone_ore
myvillage:deepslate_spirit_stone_ore
```

Both ores require an iron-tier-or-better pickaxe. Silk Touch drops the matching
ore block; non-Silk mining drops low-grade spirit stone with the vanilla
`ore_drops` Fortune formula. The main configured feature has stone and deepslate
targets and size `6`; the deep-small feature has the same targets and size `3`.

| Placed feature | Count | Size | Height |
|---|---:|---:|---|
| upper | 30 | 6 | trapezoid, Y 80..384 |
| middle | 3 | 6 | trapezoid, Y -24..56 |
| deep | 3 | 3 | uniform, world bottom..Y 0 |

A NeoForge biome modifier injects all three into Overworld biomes at
`underground_ores`. Existing generated chunks are not retrofitted. There is no
raw ore, smelting chain, stone-manufacturing recipe, higher grade, storage block,
fragment, refining machine, or currency layer. Since 0.50.0, the six herb infusions
and three formula-sheet recipes consume existing low-grade stones; these are
explicit alchemy consumers, not ore-processing recipes (see [alchemy](49_alchemy.md)).

## Calendar And Lifespan

`CultivationCalendarSavedData` is owned by Overworld data storage and contains
only non-negative `elapsedCalendarTicks`. It advances once per server tick while
at least one player is online, whatever their game mode. Personal lifespan advances
only while that player is online, alive, non-removed, and in
survival/adventure. Creative, spectator, dead, and offline players do not age;
sleep, vanilla time, `/time set`, daylight-cycle rules, weather, and dimension
do not change either clock.

Defaults (`cultivation_time` server config) are `ticks_per_day = 24000` (one
Minecraft day-night length), `days_per_year = 24` and `days_per_week = 6`, or
`576000` ticks per year (four weeks). The week is a display unit only: dates
read year, week and day, weeks restart with each year, and a year that is not a
whole number of weeks is allowed with a load/reload warning. At 20 TPS with
someone online, 1 day = 20 real minutes, 1 week = 2 h, 1 year = 8 h, and the
mortal `80` years = 640 h. The time snapshot carries all three values. Realm definitions own required positive maximum lifespans: mortal `80`,
Qi Refining `120`, Foundation Establishment `240`, and Golden Core `500` years. Remaining lifespan,
the `10/5/1`-year warnings, and exhaustion are derived from the current realm and
raw consumed ticks; they are not persisted flags.

Changing any scale value deliberately reinterprets all prior raw calendar and
lifespan ticks. Data is not rescaled, so displayed dates, warnings, and
exhaustion can move in either direction. Config load/reload logs an operator
warning. Lifespan commits are batched at a bounded `600`-tick cadence and forced
through `CultivationService` on logout, death, dimension change, ordinary server
save, and clean stop. The ordinary-save hook persists any newly committed
attachment state and checkpoints the Overworld calendar. A process crash can
therefore lose only the still-pending lifespan batch; the design does not claim
per-tick disk durability.

Exhaustion blocks meditation and advancement but does not kill the player,
clear the profile, or trigger reincarnation. Consumed ticks remain monotonic.

## Meditation And Gain

One UUID-keyed server manager owns both modes:

```text
IDLE
  -> PREPARING_NORMAL (40 eligible ticks) -> MEDITATING_NORMAL
  -> PREPARING_SPIRIT (40 eligible ticks) -> MEDITATING_SPIRIT
  (0.38.0 study: PREPARING_NORMAL -> MEDITATING_NORMAL with study progress)
```

The configurable controls are V normal, B spirit, X stop, N advancement, and H
for the two-tab panel. Start requires an awakened root, learned Basic Breathing,
survival/adventure, life remaining, stable support, no mount/swim/flight/sleep/
item-use conflict, and no positive damage in the previous 100 ticks. Server-
observed movement beyond `0.01` block on any axis, jump, damage, attack/swing,
mining, use, mount, swim/flight/sleep/mode conflict, dimension change, death,
logout, or explicit stop interrupts. Yaw/pitch and opening H are allowed.

Since 0.38.0 the manager has a third mode, `MeditationMode.STUDY` (研读): reading
a technique manual. It is started only by right-clicking a manual (item use,
no new intent), runs through the same 40-tick preparation, anchor, eligibility
and interruption set, and reuses the states `PREPARING_NORMAL` and
`MEDITATING_NORMAL`; the status tells it apart by its optional study progress.
Unlike normal and spirit meditation it does not need a stage that still gains
progress, and its ten-tick batch adds study points to the manual instead of
cultivation progress (gates cost stability). New stop reasons:
`STUDY_ACCEPTED`, `MANUAL_LOST`, `STUDY_GATE`, `STUDY_COMPLETE`,
`STUDY_REQUIREMENTS`. Because the status payload carries the study progress,
the payload protocol is `11`. Rules and numbers:
[Technique System](41_technique_system.md), "Manuals and study".

Active Basic Breathing settles progress every `10` continuously eligible ticks.
Normal mode adds the current non-negative `spiritualAffinity`; default profiles
therefore add `10`. Spirit mode adds a fixed total `50` and atomically consumes
the positive `spirit_stone_cost` authored by the source stage:

| Source stage | Stones/batch | Progress/batch |
|---|---:|---:|
| qi-sensed mortal | 1 | 50 |
| Qi I | 1 | 50 |
| Qi II | 2 | 50 |
| Qi III | 3 | 50 |

A nonempty final spirit batch pays the full cost and clamps output at the cap;
an already capped stage costs nothing. Insufficient ordinary inventory consumes
nothing, applies the due normal-affinity result, and downgrades without another
preparation. Multi-slot removals are restored if profile installation fails.
External containers, current spiritual power, and legacy reserve do not fund a
batch.

Stability is sequential consolidation rather than a parallel year-rate bar. It
does not grow while progress is below cap, including the batch that first fills
progress. Beginning with the next ten-tick batch, either mode adds current
`spiritualAffinity` stability without a stone cost and clamps to integer-floor
half of the current progress cap. Since 0.50.0, active Ningshen medicine scales
only this post-cap affinity gain by its bundled multiplier, rounded down and
clamped to the same cap; no medicine keeps the original result (see [alchemy](49_alchemy.md)).
Basic Breathing mastery alone remains at `10`
per configured cultivation year with one transient remainder.

| Source stage | Progress cap | Stability cap |
|---|---:|---:|
| qi-sensed mortal | 1000 | 500 |
| Qi I | 1100 | 550 |
| Qi II | 1200 | 600 |
| Qi III | 1300 | 650 |

The first four rows are preserved regression anchors. The 0.48.0 extension
adds Qi-IV..IX caps 1400..1900, Foundation caps 3000/3600/4200 and Golden
early/middle caps 6000/7200. All stability caps remain half of current progress
caps. Each source authors its positive spirit cost; the complete cost/cap and
advancement tables are in the [README](../../README.md#cultivation-playable-loop).
Only unawakened mortal and terminal Golden Core late have no cultivation cap.
No progress or stability overflow is stored or transferred.

## Advancement

Advancement reuses the transient manager and the meditation eligibility and
interruption boundary. N carries no target or rule. The current stage definition
owns the explicit target, kind, duration, stability requirement, compatibility
cost, and interruption loss.

| Transition | Duration | Required stability | Exact-cap result | Interrupt loss |
|---|---:|---:|---:|---:|
| qi-sensed mortal -> Qi I (`1000`) | 100 | 500 | 250 | 0 |
| Qi I -> Qi II (`1100`) | 100 | 550 | 275 | 0 |
| Qi II -> Qi III (`1200`) | 120 | 600 | 300 | 0 |
| Qi III -> Qi IV bottleneck (`1300`) | 200 | 650 | 325 | 5 |

Completion is deterministic and revalidated on the final tick. It performs one
immutable replacement, changes realm/stage, resets progress to zero, retains
integer-floor half of actual current stability, preserves every unrelated v3
field, synchronizes once, and returns to idle. The authored compatibility cost
equals required stability minus its floor-half, but is not used as a fixed
runtime subtraction.
There is no random failure, overflow transfer, or chained
multi-stage advancement. Ordinary interruption costs nothing; a
player/world interruption of an authored bottleneck costs its data-defined
loss. The preserved Qi-III and new Qi-VI losses are 5, Qi-IX to Foundation is
10, and Foundation-late to Golden Core is 20. Clean
server stop and registry-reload teardown are penalty-free.

The added stage IDs are `foundation_middle`, `foundation_late`, `golden_early`,
`golden_middle`, and `golden_late`; existing IDs remain unchanged. Profile v4
and payload forms are unchanged. The player statistics aliases cover every
registered stage, with health/attack policy in [note 47](47_realm_stats_wilderness.md).
The panel derives terminal status from synchronized definitions. Capacity
changes preserve wounds, current spiritual power and consumed age; Golden
Core late remains usable for power and study even though progression stops.

## Validation And Manual Evidence

Automated handoff requires the strict baseline, including the capability specs
synchronized from all five archived foundation changes and the affinity/UI
revision, plus the core, initiation, spirit-stone, lifespan,
meditation, gain, and advancement validators, validator unit tests, Gradle
tests/build, jar inspection, and bounded stage-1 acceptance-server smoke. Those
checks establish schemas, references, arithmetic, payload direction, side
safety, packaging, and server startup; they do not prove real-client appearance
or interaction feel.

The complete real-client procedure and pass/fail/`not_verified` ledger live in
[README.md](../../README.md#in-game-acceptance). Until directly observed, ore
readability/distribution, controls and interruptions, both H tabs, exact direct
stone costs, multiplayer clocks, persistence, and advancement remain
`not_verified`.

## See Also

- Baseline specs: [player profile](../contracts/cultivation.md#cultivation-player-profile), [definition registries](../contracts/cultivation.md#cultivation-definition-registries), [persistence lifecycle](../contracts/cultivation.md#cultivation-persistence-lifecycle), [state synchronization](../contracts/cultivation.md#cultivation-state-synchronization), [core validation](../contracts/tooling.md#cultivation-core-validation), [resource export](../contracts/assets.md#resource-export), and [validation](../contracts/tooling.md#validation).
- Spirit resources: [proposal](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/proposal.md) and [delta spec](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/specs/spirit-stone-resources/spec.md).
- Lifespan/calendar: [proposal](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/proposal.md) and [delta spec](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/specs/spirit-stone-resources/spec.md).
- Meditation: [proposal](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/proposal.md) and [delta spec](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/specs/spirit-stone-resources/spec.md).
- Gain: [proposal](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/proposal.md) and [delta spec](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/specs/spirit-stone-resources/spec.md).
- Advancement: [proposal](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/proposal.md) and [delta spec](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/specs/spirit-stone-resources/spec.md).
- Affinity/UI revision: [proposal](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/archive/2026-07-13-add-spirit-stone-resources/proposal.md) and [delta specs](https://github.com/ishikisiko/mc-modtest/tree/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes).
- GuideME summary: [GuideME Cultivation Guide](31_guideme_cultivation_guide.md) and its [change spec](https://github.com/ishikisiko/mc-modtest/blob/1388f192f4dc723ed6393420ca6b0cb9664c5056/openspec/changes/add-guideme-cultivation-guide-slice/specs/guideme-cultivation-guide/spec.md).

The 0.48.0 chain contract is shared by the core/gain/advancement Python validators
through `tools/cultivation_progression_contract.py`. It checks the complete
ordered player chain, required old IDs, no skips/cycles/premature terminal,
source bounds and the original four transition baselines.
Bounded mapped-client evidence is `python3 tools/golden_core_evidence.py`; run
clean acceptance stage 1 first. See [the measured report](../testing/2026-10-14-golden-core.md).
