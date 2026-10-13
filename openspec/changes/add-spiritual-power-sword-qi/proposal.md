## Why

Cultivation advancement and learned active techniques do not yet provide a usable ranged combat ability. A bounded spiritual-power resource and one learnable sword-qi technique connect the existing sect scripture and manual-study systems to actual combat without expanding several combat systems at once.

## What Changes

- Give current spiritual power a stage-derived cap, server-owned regeneration, atomic spending, administration commands, and a synchronized HUD.
- Add the public grade-one `jianqi_jue` technique through the authored catalogue and existing manual/study and sect-scripture channels.
- Hold and release the combat use input to charge and launch one advancing sword-qi wave. The server owns timing, cost, aim, collision, target limits, and damage.
- Add focused rules/data tests, practical jar and server checks, and real client input evidence without DevBridge.
- Record elapsed time, UTC boundaries, command, exit status, available CPU/RSS metrics, environment, cache state, failures, and retries for test-environment planning.
- Keep dodge and melee free. Bleed, additional weapon families, multi-move active combos, spells, and flying-sword control are outside this change.

## Capabilities

### New Capabilities

- `spiritual-power-pool`: Bounded persistent power, regeneration and spending, derived presentation, and lifecycle handling.
- `sword-qi-technique`: Learned sword-family charge/release action with moving, wall-blocked server damage.

### Modified Capabilities

- `cultivation-definition-registries`: Stage caps and the first executable active-technique reference.
- `cultivation-player-profile`: Service-only power mutations and separation from legacy reserve and cultivation gain.
- `cultivation-state-synchronization`: Read-only power HUD and authoritative charge/wave presentation.
- `cultivation-debug-commands`: Equivalent English/pinyin power administration.
- `cultivation-core-validation`: Power and active-technique data checks.
- `combat-style-data`: Registered skill data and bounded charge/wave definitions.
- `combat-data-validation`: Skill references, wave bounds, and input-only payload checks.
- `combat-capture-tooling`: No-DevBridge mapped-input evidence and measured execution times.
- `validation`: Layered spiritual-power/sword-qi acceptance with explicit observation limits.
- `resource-export`: Generated technique resources and executable skill packaged in the jar.

## Impact

Touches cultivation definitions/services/ticks/commands, combat skill loading and runtime, client combat input and HUD, generated catalogue and language resources, focused validators, tests, capture tools, README and the knowledge base. Existing persisted spiritual power is reused; no second resource balance or new projectile entity is required. Existing melee baselines, server-authority boundaries, manual study, and sect borrowing remain authoritative. Release metadata follows `openspec/config.yaml` `rules.tasks` with target 0.46.0.
