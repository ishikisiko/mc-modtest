## Context

The current immutable player attachment is schema version 1 and the only
persistent cultivation time source is ordinary Minecraft world data, which is
affected by sleep and commands. The playable loop needs a world-shared
cultivation calendar, a personal online-only lifespan, and a durable meditation
reserve before meditation can safely consume time or resources. This change is
the sole schema migration in the five-change series and must preserve every
initiation result and unknown id.

## Goals / Non-Goals

**Goals:**

- Migrate valid v1 profiles to v2 with two non-negative `long` counters.
- Advance one Overworld-owned calendar only when an eligible player is online.
- Advance each eligible living player's lifespan independently, with bounded
  in-memory batching and lifecycle flushes through `CultivationService`.
- Resolve maximum lifespan from the current realm definition and expose derived
  warnings/exhaustion without storing redundant flags.
- Show calendar, lifespan, reserve, and exhaustion on the existing read-only H
  screen through bounded clientbound synchronization.

**Non-Goals:**

- No meditation state, cultivation gain, reserve refill/consumption,
  breakthrough, automatic death, reincarnation, profile reset, offline aging,
  real-time clock, vanilla `/time` coupling, or mutable H-screen controls.
- No manual cultivation clone path and no per-player `SavedData`.

## Decisions

### The current profile becomes v2 through a version-dispatched codec

The current value adds:

```text
lifespanConsumedTicks: long >= 0
meditationQiReserve: long >= 0
```

`CultivationProfile.CODEC` first dispatches on `schema_version`. A retained v1
DTO codec decodes the exact old shape, preserves all ids, roots, maps, numeric
values, and field meanings, then applies a pure v1-to-v2 migration whose two new
values are zero. A v2 codec round-trips the complete current value and is the
only encoder. Versions other than 1 or 2 fail explicitly. Existing over-cap
progress and unknown definition ids are not repaired or clamped during this
migration.

Every profile copy/helper and both initiation services preserve the new values.
`reset` installs the exact v2 default, including zero lifespan and reserve. This
single early migration is preferred to immediate v2 and v3 bumps for two known,
durable counters.

### Realm definitions own maximum lifespan

`RealmDefinition` gains required positive `maximum_lifespan_years`. Shipped
values are mortal `80`, qi refining `120`, and foundation establishment `240`.
Maximum lifespan is not copied into the profile; it is resolved from the
current registry every time status or eligibility is evaluated. A profile with
an unavailable realm remains decodable, continues accumulating raw consumed
ticks when otherwise eligible, and displays an unavailable maximum rather than
being repaired.

### Both clocks count effective server ticks but have different eligibility

`CultivationCalendarSavedData` lives in the Overworld data storage and stores
only non-negative `elapsedCalendarTicks`. One server-post-tick coordinator
increments it once, not once per dimension, when at least one online player is
in survival or adventure mode. A dead eligible-mode player may keep the shared
calendar moving while their own lifespan is paused.

Personal lifespan increments once for each online, alive, non-removed survival
or adventure player. Creative, spectator, dead, and offline players do not gain
personal consumed ticks. AFK status, dimension, sleep, weather, vanilla day
time, `/time set`, and daylight-cycle rules do not alter either calculation.

### Time scale is dynamic and deliberately reinterprets raw history

Server configuration defaults are `24000` effective ticks per cultivation day
and `6` days per cultivation year, so one year is `144000` effective ticks and
an 80-year mortal limit is `11520000` ticks. Both values are positive and their
products with shipped lifespan limits use checked arithmetic.

The raw counters are not rescaled when configuration changes. Changing either
value therefore immediately reinterprets all previously accumulated calendar
and lifespan ticks and can move displayed dates, warnings, or exhaustion in
either direction. Load/reload logs and command-facing diagnostics emit an
explicit operator warning. This trade-off preserves the requested raw-tick
storage and avoids an impossible bulk rewrite of offline player attachments.

### Lifespan commits are batched but disk durability follows vanilla saves

Each online player's pending lifespan ticks remain in a UUID-keyed in-memory
accumulator. Every 600 server ticks, the manager submits one checked addition
through `CultivationService`; a failed update retains the pending amount for
retry. Logout, positive death, dimension change, respawn replacement, and clean
server stopping force the pending amount into the authoritative attachment at
the first safe lifecycle point. The attachment still reaches disk on ordinary
Minecraft player/world saves; the 600-tick cadence is not falsely described as
a hard-crash disk-durability guarantee.

Calendar state is incremented in memory and marked dirty on the same bounded
cadence plus clean save/stop. Additions saturate at `Long.MAX_VALUE` rather than
wrapping negative.

### Warnings and exhaustion are derived, not persisted

For a resolvable realm, remaining ticks equal the non-negative difference
between realm maximum ticks and consumed ticks. Warnings occur when remaining
lifespan first crosses 10, 5, and 1 cultivation years; a per-session de-dup set
prevents repeated messages. Login at or beyond a threshold emits only the most
urgent current warning once. Exhaustion is `consumed >= maximum` and is exposed
as an authoritative query for later meditation and advancement changes.
Consumed ticks remain monotonic after exhaustion; no death or reset occurs.

### Time status uses a separate bounded clientbound snapshot

The v2 profile continues through `myvillage:cultivation_snapshot`. A compact
clientbound time snapshot carries `elapsedCalendarTicks`, the two active scale
values, and the server-derived status needed by presentation. It is sent on
login/respawn/dimension change, configuration reload, and at most once per 600
active ticks, not every tick. This lets creative observers receive a current
shared calendar even though their lifespan does not commit.

The H screen remains local, non-pausing, sharp, and read-only. At raw calendar
tick zero it displays cultivation year 1, day 1. It shows 1-based day within the
six-day year, consumed lifespan, remaining/current maximum, reserve, and an
unavailable marker when current realm data cannot resolve.

## Risks / Trade-offs

- [Changing time scale can instantly exhaust or un-exhaust a player] -> Emit an
  explicit operator warning and document that raw ticks are intentionally
  reinterpreted.
- [Lifecycle ordering can lose pending age during player replacement] -> Keep
  pending state by UUID, test death/respawn and End-return ordering, and flush at
  both pre-replacement and post-replacement safe points without double adding.
- [Frequent profile writes can spam snapshots] -> Commit only every 600 ticks
  or at required lifecycle boundaries and keep the time payload compact.
- [A hard crash can lose unsaved in-memory ticks] -> State the real vanilla save
  durability boundary and never claim the batch interval is an fsync guarantee.
- [Required realm fields break old datapacks] -> Fail datapack loading with the
  offending realm id and document the required positive field.

## Migration Plan

1. Land v2 codecs/migration and update every profile-preserving service/test.
2. Extend realm definitions and validation with the three maximum lifespans.
3. Add configuration, Overworld SavedData, tick/batch/lifecycle management, and
   derived status services.
4. Extend clientbound synchronization and the H screen, then run migration,
   lifecycle, server-smoke, and manual evidence.

Rollback after v2 profiles have been saved is not safe to v1 code because v1
rejects schema 2. Rollback therefore requires restoring a v2-capable decoder or
an explicit offline v2-to-v1 data migration that intentionally discards only
the two new counters; silent reset is forbidden.

## Open Questions

None. The profile fields, realm limits, scale, warning thresholds, batching,
and dynamic reinterpretation policy are fixed.
