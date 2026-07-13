# Cultivation Playable Loop

This note records the first playable cultivation loop, its five serial
foundation changes, and the current affinity/UI revision. It extends the
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
  -> deterministic advancement through Qi Refining IV
```

The original resource change introduced stones without consumption; the current
gain revision makes them direct server-owned meditation inputs. The time change
does not start meditation, the shared state machine owns eligibility, gain stops
at the cap, and advancement performs exactly one transition. Qi Refining IV is
the release ceiling; Qi IV-IX gain, Foundation
breakthrough, generic technique execution, spiritual-power recovery, pills,
facilities, environment requirements, combat/exploration rewards, tribulation,
death from old age, and reincarnation are not implemented here.

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

H remains non-pausing and uses Profile and Meditation tabs. Profile values,
time status, and session status are clientbound/read-only. Four Meditation-tab
buttons send only the existing normal/spirit/stop/advance enum used by V/B/G/N;
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
raw ore, smelting chain, recipe, higher grade, storage block, fragment, refining
machine, or currency layer.

## Calendar And Lifespan

`CultivationCalendarSavedData` is owned by Overworld data storage and contains
only non-negative `elapsedCalendarTicks`. It advances once per server tick while
at least one survival/adventure player is online. Personal lifespan advances
only while that player is online, alive, non-removed, and in
survival/adventure. Creative, spectator, dead, and offline players do not age;
sleep, vanilla time, `/time set`, daylight-cycle rules, weather, and dimension
do not change either clock.

Defaults are `24000` effective ticks/day and `6` days/year, or `144000` ticks per
year. Realm definitions own required positive maximum lifespans: mortal `80`,
Qi Refining `120`, and Foundation Establishment `240` years. Remaining lifespan,
the `10/5/1`-year warnings, and exhaustion are derived from the current realm and
raw consumed ticks; they are not persisted flags.

Changing either scale value deliberately reinterprets all prior raw calendar and
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
```

The configurable controls are V normal, B spirit, G stop, N advancement, and H
for the two-tab panel. Start requires an awakened root, learned Basic Breathing,
survival/adventure, life remaining, stable support, no mount/swim/flight/sleep/
item-use conflict, and no positive damage in the previous 100 ticks. Server-
observed movement beyond `0.01` block on any axis, jump, damage, attack/swing,
mining, use, mount, swim/flight/sleep/mode conflict, dimension change, death,
logout, or explicit stop interrupts. Yaw/pitch and opening H are allowed.

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
half of the current progress cap. Basic Breathing mastery alone remains at `10`
per configured cultivation year with one transient remainder.

| Source stage | Progress cap | Stability cap |
|---|---:|---:|
| qi-sensed mortal | 1000 | 500 |
| Qi I | 1100 | 550 |
| Qi II | 1200 | 600 |
| Qi III | 1300 | 650 |

These progress caps correspond to target layers I through IV. Unawakened
mortal, Qi IV-IX, and Foundation Early cannot gain progress in this release. No
progress or stability overflow is stored or transferred.

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
equals half the required cap but is not used as a fixed runtime subtraction.
There is no random failure, overflow transfer, or chained
multi-stage advancement. Ordinary interruption costs nothing; only a
player/world interruption of the Qi III bottleneck costs five stability. Clean
server stop and registry-reload teardown are penalty-free.

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

- Baseline specs: [player profile](../../openspec/specs/cultivation-player-profile/spec.md), [definition registries](../../openspec/specs/cultivation-definition-registries/spec.md), [persistence lifecycle](../../openspec/specs/cultivation-persistence-lifecycle/spec.md), [state synchronization](../../openspec/specs/cultivation-state-synchronization/spec.md), [core validation](../../openspec/specs/cultivation-core-validation/spec.md), [resource export](../../openspec/specs/resource-export/spec.md), and [validation](../../openspec/specs/validation/spec.md).
- Spirit resources: [proposal](../../openspec/changes/add-spirit-stone-resources/proposal.md) and [delta spec](../../openspec/changes/add-spirit-stone-resources/specs/spirit-stone-resources/spec.md).
- Lifespan/calendar: [proposal](../../openspec/changes/add-cultivation-lifespan-calendar/proposal.md) and [delta spec](../../openspec/changes/add-cultivation-lifespan-calendar/specs/cultivation-lifespan-calendar/spec.md).
- Meditation: [proposal](../../openspec/changes/add-cultivation-meditation/proposal.md) and [delta spec](../../openspec/changes/add-cultivation-meditation/specs/cultivation-meditation/spec.md).
- Gain: [proposal](../../openspec/changes/add-basic-breathing-cultivation-gain/proposal.md) and [delta spec](../../openspec/changes/add-basic-breathing-cultivation-gain/specs/cultivation-gain/spec.md).
- Advancement: [proposal](../../openspec/changes/add-qi-refining-advancement/proposal.md) and [delta spec](../../openspec/changes/add-qi-refining-advancement/specs/cultivation-advancement/spec.md).
- Affinity/UI revision: [proposal](../../openspec/changes/revise-cultivation-affinity-meditation-ui/proposal.md) and [delta specs](../../openspec/changes/revise-cultivation-affinity-meditation-ui/specs/).
- GuideME summary: [GuideME Cultivation Guide](31_guideme_cultivation_guide.md) and its [change spec](../../openspec/changes/add-guideme-cultivation-guide-slice/specs/guideme-cultivation-guide/spec.md).
