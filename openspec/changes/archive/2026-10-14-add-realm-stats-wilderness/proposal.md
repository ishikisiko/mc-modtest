## Why

Combat currently has move multipliers but no common attribute aggregation or inspectable damage calculation. Realm growth and regional wilderness encounters need one validated numerical contract so players, beasts and ledger cultivators agree about their strength and origin.

## What Changes

- Add data-driven base attributes, explicit additive and multiplicative damage stages, typed resistance, and diagnostic breakdowns.
- Define exponential major-realm and linear minor-stage growth, with validated finite bounds and explicit player/ledger stage mapping.
- Apply derived attributes on the server without stacking modifiers or healing on refresh; share damage calculation between melee, sword qi and beasts while preserving vanilla mitigation once.
- Add explicit regional realm ranges and bounded wilderness encounters: persisted beast realm assignments and ledger-owned rogue cultivator projections.
- Add deterministic tests, data validation, runtime inspection commands, server evidence and documentation.

## Capabilities

### New Capabilities
- `realm-combat-stats`: Unified growth, attribute composition, damage stages and diagnostics.
- `regional-wilderness`: Regional encounter realm ranges and bounded beast/rogue placement.

### Modified Capabilities
- `hostile-beast-runtime`: Natural wilderness generation and persistent realm-derived attributes.
- `humanoid-npc-runtime`: Ledger-owned wilderness rogue projections and realm attributes.
- `validation`: Numerical, distribution, lifecycle and runtime evidence gates.
- `combat-capture-tooling`: Explicit isolated natural-monster opt-in for wilderness evidence.

## Impact

New pure numerical code and bundled data; cultivation lifecycle, combat damage, beast spawning, ledger avatar projection and region consumers; validators/tests, README/KB/specs and release metadata. No new models, animations, dependencies, client authority or autonomous NPC hostility.
