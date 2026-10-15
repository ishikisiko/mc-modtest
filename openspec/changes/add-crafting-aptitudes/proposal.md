## Why

The owner chose independent alchemy/forging aptitude as a hard ceiling on the quality of products made from the same recipe (下品/中品/上品/极品), explicitly not the 黄/玄/地/天 recipe tier. Persist and display that foundation before implementing quality-bearing pills or forging.

## What Changes

- Add two server-owned, once-generated crafting aptitudes and data-driven maximum product-quality mappings.
- Keep values through death, reconnect and restart, initialize already-awakened old saves safely, and expose values/caps on a read-only H page.
- Extend the existing clientbound cultivation snapshot and protocol, with equivalent English/pinyin administrator status/set commands.
- Keep current furnace transactions and fixed pill outputs unchanged; quality generation and actual result capping are future work.

## Capabilities

### New Capabilities
- `crafting-aptitudes`: Independent saved aptitudes, generation, quality ceilings and display.

### Modified Capabilities
- `cultivation-state-synchronization`: Fold crafting state and server-derived ceilings into the existing owning-client snapshot.
- `cultivation-persistence-lifecycle`: Add a separate immutable copy-on-death crafting attachment without changing profile v4.
- `cultivation-debug-commands`: Equivalent status/set diagnostics for both crafting professions.
- `validation`: Aptitude data/lifecycle/protocol tests and bilingual panel evidence.

## Impact

Cultivation attachment/service/events/snapshot, protocol20, a PanelPage and View, bundled rules, bilingual text, commands, focused tests/docs. No crafting experience, pill quality component, new recipe, forge, NPC aptitude, furnace-owner semantics, external dependency or profile-schema change. Version follows openspec/config.yaml rules.tasks.
