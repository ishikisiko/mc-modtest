## Why
Crafting aptitude currently displays a quality ceiling but does not affect any produced item. Connect that existing player attribute to the three implemented medicines, with an explicit brewer and persistent batch result.

## What Changes
- Require a server-validated manual start for each furnace batch; freeze the initiating player's alchemy aptitude and ceiling.
- Roll one server-owned quality per batch, persist it through unload/restart, and lock ingredients during an active batch. Only the ceiling is shown before completion.
- Keep three pill item IDs; carry lower/middle/upper/perfect quality as an item data component, with legacy items retaining lower-quality behavior.
- Scale medicine strength from authored data, persist sustained-effect quality, and show quality and actual effects in item names/tooltips.
- Update furnace UI, commands/docs and real-client evidence. Execute all heavy builds and game tests on Modal, including a two-client authority check.

## Capabilities
### New Capabilities
- `alchemy-quality`: manual batch attribution, persistent quality rolls and quality-dependent medicine behavior.
### Modified Capabilities
- `alchemy-foundation`: automatic brewing becomes explicit one-batch starts; active inputs are locked and saves retain fixed quality.
- `crafting-aptitudes`: alchemy production now consumes the existing quality ceiling; forging remains foundation only.
- `modal-test-runner`: add full release and alchemy-quality evidence presets for cloud-only heavy validation.

## Impact
Alchemy furnace/menu/screen, pill components and medication state, alchemy rules and generated language keys, validation/evidence scripts, Modal presets and usage docs. No new external dependencies, pill IDs, textures, models, skill XP, toxicity, furnace failure or forging production.
