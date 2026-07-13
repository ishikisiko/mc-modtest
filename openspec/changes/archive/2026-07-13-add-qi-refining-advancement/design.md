## Context

The preceding serial changes provide profile v2, lifespan and calendar clocks,
the three-action server-authoritative meditation session, Basic Breathing gain,
and stage-local caps through Qi Refining III. A capped profile cannot progress
without a distinct, interruptible transition. This change closes only the first
advancement slice: qi-sensed mortal through the Qi III-to-IV bottleneck. Qi IV
is a deliberate release ceiling rather than the start of a speculative full
realm system.

## Goals / Non-Goals

**Goals:**

- Put target, kind, duration, stability requirement/cost, and interruption loss
  in registered stage data for four exact transitions.
- Extend the same bounded intent and transient session manager with one start-
  breakthrough action and configurable N key.
- Make ordinary and bottleneck advancement deterministic, server-authoritative,
  mutually exclusive with meditation, and interruptible by the established
  gameplay interruption set.
- Install success or applicable bottleneck loss through one immutable
  `CultivationService` replacement and one final snapshot.

**Non-Goals:**

- No Qi IV-IX gain/advancement, Foundation Establishment, major-realm process,
  environment/facility checks, pills, extra spirit-stone costs, random success,
  tribulation, overflow, chaining, reincarnation, or client-selected target.

## Decisions

### Advancement metadata belongs to the current stage

`RealmStageDefinition` gains an optional `advancement` record:

```text
target_realm
target_stage
kind                  ordinary | bottleneck
duration_ticks
required_stability
stability_cost
interruption_stability_loss
```

Targets are explicit so the mortal-to-Qi-I cross-realm transition is not
inferred from sort order, id spelling, or `next_realm`. Validation resolves the
target through current `RegistryAccess`, requires `stability_cost <=
required_stability <= 100`, and accepts only positive duration and non-negative
loss. The four shipped rules are:

| source | target | kind | duration | required | success cost | interrupt loss |
|---|---|---|---:|---:|---:|---:|
| mortal qi-sensed | Qi I | ordinary | 100 | 10 | 5 | 0 |
| Qi I | Qi II | ordinary | 100 | 20 | 10 | 0 |
| Qi II | Qi III | ordinary | 120 | 30 | 15 | 0 |
| Qi III | Qi IV | bottleneck | 200 | 80 | 30 | 5 |

Qi IV and later stages omit both cap and advancement metadata. The initial kind
codec has only `ordinary` and `bottleneck`; major-realm rules need a later
design rather than an unused enum value.

### Change 5 extends the existing bounded intent, not Change 3

Before this change, `MeditationIntentPayload` has exactly start normal, start
spirit, and stop. This change adds exactly `START_BREAKTHROUGH`; it carries no
target, kind, duration, progress, stability, cost, identity, transform, or
success value. N sends the new action. Existing V/B/G behavior remains, and G
stops either meditation or advancement. Sender identity and every advancement
value are derived on the logical server.

### One transient manager owns mutual exclusion

The existing UUID-keyed cultivation-session manager gains ordinary-advancement
and bottleneck-advancement states. A start is accepted only from idle; starting
while preparing/actively meditating or already advancing is rejected rather
than silently switching. Normal/spirit starts are likewise rejected during
advancement. Advancement has no additional meditation-preparation delay: its
definition duration begins after the start request passes eligibility.

Start requires survival/adventure, alive, non-exhausted lifespan, awakened
root, learned Basic Breathing, stable ground, no mount/swim/flight/sleep/use or
conflicting state, no damage in the last 100 ticks, progress at least the
current cap, stability at least the declared requirement, and valid source,
cap, rule, and target definitions. The server snapshots source ids and rule
values for status but re-resolves and revalidates them before completion.

### Advancement reuses meditation interruptions

Position movement beyond the existing 0.01-block tolerance, jump, damage,
attack/swing, mining, item use, mount, swim/flight/sleep/mode conflict,
dimension change, death, logout, and explicit G stop interrupt advancement.
Yaw/pitch remain allowed. Only the Qi III bottleneck's player/world
interruptions install its exact 5-stability loss, clamped at zero. Ordinary
advancement loses no stability. Clean server shutdown and registry-reload
teardown clear sessions without penalty so administrative lifecycle cannot harm
profiles. Interruption is idempotent, so overlapping hooks cannot charge twice.

### Success is one deterministic profile transition

On the final eligible tick the server revalidates source stage, progress, cap,
stability, rule, and target. Success constructs one immutable replacement with
the target realm/stage, progress zero, and stability reduced by the declared
success cost. Lifespan ticks, meditation reserve, spiritual root, current
spiritual power, learned techniques, and all mastery values are preserved.
There is no random roll and no overflow transfer. The session returns to idle
after one transition, so even an administrator-created over-cap profile cannot
chain multiple stages.

### Client state remains presentation-only

Transition status reports preparation/progress kind, remaining ticks,
interruption reason, and result from server-derived values. It carries no
profile replacement. H remains read-only and may show cap, advancement kind,
requirements, current runtime state, and the Qi-IV release ceiling. No status is
sent per tick; start/interrupt/complete and bounded progress updates are enough.

## Risks / Trade-offs

- [Logout could evade a bottleneck loss] -> Flush the one penalty before the
  session is removed when a live profile is available; test idempotence across
  overlapping logout/dimension/death hooks.
- [Datapack rules change mid-session] -> Tear down on registry reload without a
  penalty, then require a fresh start under current data.
- [Definition target forms a loop] -> Validate the four shipped source/target
  sequence explicitly and reject missing/self/duplicate shipped transitions.
- [N sounds like every future breakthrough] -> Label it generically but show
  an unsupported/release-limit result when the current stage has no rule.

## Migration Plan

Extend the stage codec/data after the cap change, then add the fourth intent
action, advancement states and eligibility, interruption/success transactions,
status/H presentation, tests, validators, docs, and final integration release
task. No profile schema migration is required. Rollback removes advancement
metadata and runtime states while preserving already transitioned valid v2
profiles as ordinary realm/stage data.

## Open Questions

None. The four transitions, controls, timings, requirements, costs, single
bottleneck loss, determinism, release ceiling, and preservation rules are fixed.
