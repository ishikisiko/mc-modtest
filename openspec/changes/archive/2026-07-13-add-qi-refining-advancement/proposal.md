## Why

Stage-capped progress otherwise ends at a dead bar. This change closes the first playable loop with deterministic, interruptible advancement from qi-sensing mortal through the first qi-refining bottleneck result.

## What Changes

- Add explicit definition-driven next-stage, breakthrough kind, duration, required stability, and stability-cost rules.
- Ship the first sequence: qi-sensing mortal `300` cap to Qi Refining I; Qi I `500` to II; Qi II `800` to III; Qi III `1200` through the first bottleneck to Qi IV. Qi IV and later stages remain non-cultivatable in this release.
- Use ordinary durations/requirements of `100 ticks/10 stability/5 cost`, `100/20/10`, and `120/30/15`; use `200 ticks/80 stability/30 cost` for the Qi III bottleneck.
- Extend the same bounded serverbound meditation intent with start breakthrough and add the configurable `N` binding; Change 3 still ships only normal/spirit/stop, and this change adds the fourth action without accepting a client-authored target or result.
- Let movement plus the meditation interruption set cancel advancement. Ordinary advancement has no interruption penalty; only the Qi III-to-IV bottleneck loses exactly 5 stability on a player/world interruption, while orderly server stop or registry-reload teardown does not penalize the player.
- Start advancement only when progress and stability meet the current definition; successful advancement atomically changes realm/stage, resets progress to zero, deducts the stage cost, preserves every other v2 field, and synchronizes once.
- Make conditions deterministic: no random failure, no progress overflow, no chained multi-stage advancement, and no client-authored result.
- Defer Qi IV-IX cultivation, Foundation Establishment, major-realm requirements, pills, facilities, environment checks, reincarnation, and random tribulation.

## Capabilities

### New Capabilities
- `cultivation-advancement`: Definition-driven ordinary and bottleneck advancement, deterministic sessions, interruption penalty, atomic transitions, and the first shipped sequence.

### Modified Capabilities
- `cultivation-player-profile`: Require successful advancement to reset stage-local progress while preserving lifespan, reserve, root, power, and techniques.
- `cultivation-definition-registries`: Add explicit next-stage and advancement-rule fields and validate cross-realm targets uniquely.
- `cultivation-meditation`: Extend the same transient session manager and bounded intent only in this change, preserving the existing V/B/G actions while adding N/start breakthrough.
- `cultivation-state-synchronization`: Report breakthrough state/result clientbound while accepting only a bounded start intent.
- `cultivation-core-validation`: Cover requirements, durations, interruption, no randomness, atomic transition, no chaining, and manual gameplay evidence.
- `docs-knowledge-base`: Document the complete playable-loop boundary and the still-deferred later realms.

## Impact

This affects realm-stage data, advancement services/session state, server messages/status snapshots, H-screen progress display, tests, validators, KB/README acceptance instructions, and the single coordinated feature release bump.
