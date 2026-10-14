## Why

The player progression currently stops at Qi Refining IV even though realm statistics and the world ledger support higher realms. The owner requests a normal, playable cultivation path through Golden Core late stage.

## What Changes

- Complete the server-owned progression chain through Qi Refining IX, Foundation early/middle/late and Golden Core early/middle/late; Golden Core late is the deliberate terminal stage.
- Add missing stage definitions, progress caps, deterministic advancement requirements, spirit-stone costs, lifespan and spiritual-power capacities while preserving existing IDs and the first four transitions.
- Extend required registries and numerical aliases, and make the panel show the correct terminal stage.
- Validate chain reachability, boundaries and synchronization; collect bounded real-client input and persistence evidence; update the bilingual guide and command documentation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cultivation-definition-registries`: ship the complete reachable player ladder and terminal-stage contract.
- `cultivation-advancement`: extend deterministic advancement to Golden Core late with the same server-owned checks and half-retention rules.
- `cultivation-gain`: allow configured higher stages to gain progress and stop at their caps; retain terminal refusal.
- `cultivation-state-synchronization`: render the correct progression boundary and requirements from synchronized stage data.
- `cultivation-lifespan-calendar`: include Golden Core player lifespan without resetting age on advancement.
- `cultivation-core-validation`: validate the full reachable ladder, aliases, terminal and cross-realm behavior.
- `spiritual-power-pool`: synchronize player capacity through the expanded realm ladder without refilling on advancement.
- `realm-combat-stats`: cover every expanded player stage with an explicit compatible numerical alias.
- `validation`: enforce current bilingual guidance, full-chain acceptance and separate lifecycle evidence.

## Impact

Player realm JSON, stats aliases, registry baselines, panel readouts, bilingual language/GuideME content, focused validators/tests and developer evidence tooling. Existing profiles and payload schemas remain compatible; no item, artwork, random breakthrough, tribulation, NPC progression tuning or Nascent Soul player unlock is added.
