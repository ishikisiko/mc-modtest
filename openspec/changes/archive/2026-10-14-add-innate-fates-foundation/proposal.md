## Why

The approved 36-fate design currently exists only as a browser prototype. A small playable slice must establish server authority and persistent choices before complex awakening and exploration systems are added.

## What Changes

- Add an independent immutable, copy-on-death fate attachment, replaced only through CultivationService, without rewriting existing cultivation roots or profile versions.
- Add eight implemented, synchronized data-driven definitions; six candidates, three choices, one lock, configurable server point budget and one permanent confirmation.
- Add a H-panel page and equivalent English/pinyin commands, available after root awakening, including a one-time choice for existing saves.
- Add attribute, growth/study, fire, low-health, recovery and hostile-kill effects, with persisted cooldowns and server-only authority.
- Keep the other 28 traits and root rerolling outside this slice, retaining the design catalogue and roadmap.
- Bump release metadata under the rule in openspec/config.yaml and produce practical build/runtime evidence.

## Capabilities

### New Capabilities
- `innate-fates`: selection, persistence, synchronized definitions, input-only intents and first-batch effects.

### Modified Capabilities
- `cultivation-state-synchronization`: extend the owning snapshot with fate state/budget and allow a bounded fate intent alongside existing actions.
- `validation`: first-batch fate data, authority, lifecycle and client layout checks.

## Impact

Cultivation attachments/service/snapshot, synced registries, panel page, server config, damage and healing events, meditation/study, documentation and validation. Payload protocol increases; existing cultivation profile wire structure and deterministic root awakening remain intact. No new items or assets, no beast AI or world-ledger changes.
