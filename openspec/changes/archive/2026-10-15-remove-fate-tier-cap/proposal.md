## Why

The owner removed the separate one-high-tier restriction. Fate combinations should be bounded by the configurable point budget, three selections and exclusive groups rather than an additional rarity count.

## What Changes

- Remove rarity-count rejection from the shared client/server selection rules.
- Remove obsolete high-tier error and instruction text; align capture selection logic.
- Preserve budgets, exact selection count, group exclusions, session authority and existing saved choices.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `innate-fates`: permit multiple high-tier fates when budget and group rules allow.

## Impact

FateSelection/FateService, bilingual labels, focused tests, evidence helper, README/KB/spec and release metadata. No payload, attachment schema, model, balance-data or default-budget changes.
