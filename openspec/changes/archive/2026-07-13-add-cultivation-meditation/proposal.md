## Why

Awakened players can learn Basic Breathing but still cannot enter a server-owned practice state. This change adds the bounded session and interruption contract while deliberately leaving numeric cultivation gain to the next change.

## What Changes

- Add one in-memory server-authoritative state machine: idle, 40-tick preparation, normal meditation, and spirit-stone meditation.
- Add one bounded input-only payload whose actions are start normal, start spirit-stone, and stop; it contains no position, velocity, duration, inventory, progress, or requested result.
- Add configurable client key bindings (`V` normal, `B` spirit stone, `G` stop) and clientbound-only status feedback while keeping H read-only.
- Require an awakened root, learned `myvillage:basic_breathing`, survival/adventure mode, life remaining, stable ground, no mount/swim/flight/sleep/item use, and no positive damage within 100 server ticks.
- Interrupt on positional movement beyond tolerance, jump, positive incoming damage, attack/swing, mining start, block/entity/item use, mounting, swimming/flight, game-mode conflict, dimension change, death, logout, or explicit stop; yaw and pitch changes remain allowed.
- Keep all sessions transient and stop them without persistent profile writes. Add no progress, stability, mastery, reserve consumption, pose, animation, HUD, or advancement in this change.

## Capabilities

### New Capabilities
- `cultivation-meditation`: Server-owned meditation eligibility, bounded intent, preparation, active modes, interruption, feedback, lifecycle cleanup, and abuse resistance.

### Modified Capabilities
- `cultivation-state-synchronization`: Allow one allowlisted cultivation intent payload and one clientbound runtime-status payload without exposing profile mutation data.
- `cultivation-initiation-ritual`: Keep initiation payload-free and acquisition-only while allowing the separate bounded meditation intent.
- `cultivation-core-validation`: Cover payload shape/direction, session authority, interruption paths, key bindings, and dedicated-server safety.

## Impact

This affects cultivation networking, client key handling/state, server tick and interaction events, an in-memory session manager, runtime messages, tests, validators, docs, and the coordinated release. It does not change the profile schema established by the preceding change.
