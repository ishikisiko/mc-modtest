## Why

The meditation state machine is not a gameplay loop until Basic Breathing produces bounded, persistent results. This change makes ordinary practice free and steady while converting spirit stones into faster progress without altering lifespan rate.

## What Changes

- Define `cultivationProgress` as current-stage progress and add explicit stage caps; meditation stops progress at the cap and never banks overflow.
- Set the initial effective-year rates to 100 progress, 10 stability, and 10 Basic Breathing mastery. Accumulate fixed-point remainders in the session so 5-second settlement does not lose fractional output.
- Set spirit-stone meditation to four times total progress but the same stability and mastery rates as normal meditation.
- Treat one reserve point as one extra progress point; when reserve cannot fund the next whole extra point, consume one low-grade spirit stone to add 100 reserve, then spend reserve gradually. Preserve unspent reserve across interruption and stop without scanning the inventory every tick.
- Perform one immutable profile replacement per 100-tick settlement through `CultivationService`; consume an inventory item only on the logical server and only after the settlement/profile result is validated.
- Allow stability and mastery to continue at a progress cap, consume no reserve once no extra progress can be applied, and explicitly downgrade spirit-stone mode to ordinary meditation when neither reserve nor a stone is available.
- Add no combat/exploration experience, spiritual-power recovery, element efficiency, equipment slot, random bonus, technique executor framework, or automatic advancement.

## Capabilities

### New Capabilities
- `cultivation-gain`: Basic Breathing settlement, fixed-point rates, stage caps, stability/mastery growth, spirit-stone reserve conversion, inventory authority, and no-overflow rules.

### Modified Capabilities
- `cultivation-player-profile`: Define progress as stage-local and reserve as meditation-only rather than combat spiritual power.
- `cultivation-definition-registries`: Add explicit per-stage cultivation caps while leaving transition/breakthrough metadata to the advancement change.
- `cultivation-state-synchronization`: Synchronize only batched settlements and status changes, never per tick.
- `cultivation-initiation-ritual`: Keep learning acquisition-only while allowing separately initiated meditation to execute the learned technique.
- `cultivation-core-validation`: Cover arithmetic, caps, atomic replacement, inventory/reserve behavior, batching, and lifetime-rate invariance.

## Impact

This affects stage definition codecs/data, settlement runtime, inventory interaction, profile service validation, snapshots/H display, tests, validators, docs, and the coordinated release. It depends on the spirit-stone, profile-v2, clock, and meditation changes.
