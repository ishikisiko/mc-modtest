## Context

The prior serial changes provide spirit-stone resources, v2 lifespan/reserve,
the effective-year scale, and a server-owned meditation session. Active
meditation still produces no profile result. This change closes the ordinary
practice loop and adds resource acceleration, but stops at the stage cap so the
advancement process remains a separate boundary.

## Goals / Non-Goals

**Goals:**

- Execute learned Basic Breathing during active meditation at fixed
  effective-year progress, stability, and mastery rates.
- Set data-owned caps for qi-sensed through Qi Refining III and prevent normal
  gameplay overflow.
- Settle at 100-tick intervals with transient fixed-point remainders and one
  immutable profile replacement when a whole result exists.
- Convert low-grade spirit stones to persistent reserve and spend reserve only
  on applied bonus progress.
- Downgrade spirit mode to normal clearly when acceleration is unavailable.

**Non-Goals:**

- No automatic advancement, breakthrough metadata/process, Qi IV+ cultivation,
  generic technique-executor framework, primary/equipped technique, spiritual
  power, element efficiency, combat/exploration XP, random bonuses, or lifespan
  speed change.

## Decisions

### Basic Breathing is the only executable meditation technique in this slice

The settlement service resolves current registered
`myvillage:basic_breathing`, verifies the player learned it, and requires an
active server meditation session. It does not add an executor id, script, or
equipment slot to `TechniqueDefinition`; a future multi-technique system needs
its own capability. Missing definition, forgotten technique, invalid realm/stage,
or exhausted lifespan stops settlement with a controlled reason.

### Effective-year rates share the live cultivation time scale

At default `144000` ticks per cultivation year, both modes produce:

| output | normal | spirit |
|---|---:|---:|
| total cultivation progress / year | 100 | 400 |
| stability / year | 10 | 10 |
| Basic Breathing mastery / year | 10 | 10 |

The spirit bonus is therefore 300 progress/year. The session stores integer
numerators/remainders so repeated 100-tick settlements do not discard fractions.
Remainders are transient and are discarded only when the session ends, so the
maximum loss per session is less than one point of each output. A time-scale
configuration reload interrupts active sessions before a new denominator is
used, avoiding mixed-rate remainder interpretation.

### Stage caps are optional definition data

`RealmStageDefinition` gains optional positive `cultivation_cap`. The shipped
caps are:

```text
mortal_qi_sensed: 300
qi_refining_1: 500
qi_refining_2: 800
qi_refining_3: 1200
```

Unawakened mortal, Qi IV-IX, and Foundation Early omit the field and cannot gain
progress in this release. Normal gameplay clamps applied gain to remaining cap
and never stores overflow. Migrated or administrator-created over-cap values
remain preserved and are treated as already capped rather than silently
normalized.

Stability remains bounded at 100. Basic Breathing mastery remains a
non-negative long without a new cap. Both continue at their normal 10/year rate
when progress is capped, allowing foundation stabilization without spending
reserve.

### Reserve is a bonus-progress budget

One `meditationQiReserve` point funds exactly one whole extra progress point;
it does not fund base progress, stability, mastery, time, or spiritual power.
One consumed `myvillage:low_grade_spirit_stone` adds exactly 100 reserve. The
settlement computes the progress actually applicable before spending reserve,
so cap-clamped bonus is never charged.

Inventory is checked only at a 100-tick settlement where a whole bonus point is
due and current reserve is insufficient, not every tick. The ordinary vanilla
player inventory is eligible; external containers and world storage are not.
If neither reserve nor a stone can fund the next bonus point, the server changes
the session to normal meditation and sends one explicit downgrade status rather
than ending practice.

### Item consumption and profile credit form one logical server transaction

The server computes and validates a complete proposed settlement first. If no
stone is needed, `CultivationService` installs one immutable replacement. If a
stone is needed, inventory removal and reserve credit/spend are coordinated on
the logical server: failed removal installs nothing, and a profile-commit
failure restores the consumed item before reporting failure. A successful
settlement sends one final profile snapshot. No intermediate full-reserve
profile is installed.

### Settlement and lifespan remain independent

The manager evaluates every 100 active meditation ticks. If no whole output or
reserve change exists, it updates only transient remainders and sends no profile
snapshot. Lifespan continues through its independent one-tick-per-tick clock in
both modes; acceleration changes output per year, never age rate.

## Risks / Trade-offs

- [Transient remainders are lost on short sessions] -> Bound loss below one
  point and document it rather than add more persistent profile fields.
- [Inventory/profile operations are not disk transactions] -> Guarantee
  same-thread logical rollback and avoid claiming crash-atomic disk durability.
- [Config reload can change the rate denominator] -> Interrupt sessions before
  applying a new scale.
- [Over-cap legacy/admin profiles violate the normal cap] -> Preserve them for
  compatibility, apply no further progress, and distinguish privileged debug
  data from normal gameplay.
- [Future techniques need another architecture] -> Keep this fixed Basic
  Breathing slice explicit instead of prematurely adding scripts/equipment.

## Migration Plan

Add cap codec/data and validation, then the fixed-point settlement service,
reserve/inventory transaction, session downgrade/status integration, H display,
tests, docs, and server smoke. No profile schema migration is needed because v2
already owns reserve. Rollback removes settlement and cap metadata while
preserving accumulated v2 progress, stability, mastery, lifespan, and reserve.

## Open Questions

None. Rates, interval, caps, reserve conversion, cap behavior, and downgrade
policy are fixed.
