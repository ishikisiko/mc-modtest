## Context

After the preceding serial change, the server owns a v2 profile, shared clock,
personal lifespan, and read-only status UI. A learned Basic Breathing technique
still has no active practice session. This change introduces only the transient
server-authoritative state and input boundary; numeric gain and advancement are
deliberately separate changes.

## Goals / Non-Goals

**Goals:**

- Add one in-memory state machine for 40-tick preparation and two meditation modes.
- Accept only sender-bound start-normal, start-spirit, and stop intents.
- Revalidate eligibility on the server and interrupt consistently across movement, combat, interaction, lifecycle, and mode conflicts.
- Provide configurable V/B/G controls and transition/rejection feedback while keeping H read-only.

**Non-Goals:**

- No profile mutation, progress, stability, mastery, reserve or item consumption, spiritual power, pose, animation, HUD, breakthrough state, `START_BREAKTHROUGH` action, or N key binding.
- No client-provided coordinates, velocity, duration, eligibility, resource count, profile value, or result.

## Decisions

### One transient manager owns all sessions

`MeditationSessionManager` stores sessions by player UUID. A session is one of:

```text
IDLE
PREPARING_NORMAL -> after 40 eligible ticks -> MEDITATING_NORMAL
PREPARING_SPIRIT -> after 40 eligible ticks -> MEDITATING_SPIRIT
```

The active record contains mode, counters, anchor position/dimension, and
server-side interruption bookkeeping. It is not a Data Attachment and is never
serialized. The manager may later share interruption helpers with advancement,
but this change does not register or expose a breakthrough action or state.

### The client sends a tiny action enum, not state

`MeditationIntentPayload` has exactly `START_NORMAL`, `START_SPIRIT`, and
`STOP`. Its codec rejects unknown values and contains no other fields. The
handler uses `context.player()` as the only identity, schedules work on the
server thread, and rate-limits repeated start requests. Duplicate stop is
idempotent. One allowlisted enum is smaller than separate mode payloads and
prevents client-authored eligibility or timing.

### Eligibility is checked before and throughout the session

A player must be alive, non-removed, in survival/adventure, not lifespan
exhausted, awakened, and know current registered `myvillage:basic_breathing`.
The player must be on a supporting collision surface, not mounted, swimming,
fall-flying, ability-flying, sleeping, using an item, or in another cultivation
session. The most recent successful positive damage must be at least 100 server
ticks old. Resource availability is deliberately not checked or consumed until
the gain change owns its settlement semantics.

### Movement uses a server anchor while camera rotation is free

Preparation captures authoritative position and dimension. Displacement above
`0.01` block on any axis, leaving support, or jumping interrupts. Yaw and pitch
are excluded, so looking around is allowed. Teleport, knockback, moving blocks,
and dimension travel interrupt through server-observed position/dimension.

### Interruptions are event-driven and idempotent

Positive damage interrupts and refreshes the 100-tick window. Server-observed
attack/swing, mining start, block/entity/item use, mounting, swim/flight/sleep,
game-mode conflict, explicit stop, dimension change, death, logout, and server
stop all end the session. Multiple hooks for one action produce one transition.
Opening the read-only H screen and camera rotation alone do not interrupt.

### Feedback is transition-based and clientbound

A compact clientbound status reports authoritative state, preparation ticks
remaining, and a stable reason code only on start, reject, transition,
interrupt, or stop. It is never sent every tick. The client cache is
presentation-only and clears on disconnect. V starts normal, B starts spirit,
and G stops. H remains non-pausing and read-only.

## Risks / Trade-offs

- [Interaction hooks can double-fire] -> Make stop idempotent and test both hands plus attack/mining paths.
- [Position jitter can falsely interrupt] -> Use authoritative positions and the fixed 0.01-block tolerance.
- [Payload spam can reset preparation] -> Ignore or rate-limit duplicate starts while non-idle.
- [Networking can regress the flying sword] -> Extend the single registrar and retain its protocol tests.
- [Spirit mode is temporarily resource-neutral] -> Document the serial dependency and add downgrade/consumption only in the gain change.

## Migration Plan

Register action/status payloads, then the server manager/events, client
cache/keys, tests, validators, and docs. There is no persistent migration.
Rollback clears sessions and removes payload/key registrations without changing
v2 profiles.

## Open Questions

None. States, timings, actions, keys, and the breakthrough exclusion are fixed.
