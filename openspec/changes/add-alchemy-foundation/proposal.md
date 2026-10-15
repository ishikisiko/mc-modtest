## Why

Players need a small survival-accessible preparation loop connecting gathered plants, spirit stones, a physical workbench, and useful consumables. The owner approved one modeled furnace, six distinct herbs, three distinct formula items and three distinct pill items, with a reliable foundation first and tuning later.

## What Changes

- Add a placeable bronze three-foot alchemy furnace with a non-cube model, saved inventory/progress and a server-owned container screen.
- Add six herbs, three reusable physical formula sheets and three consumable pills; expose all thirteen items in the creative tab and survival recipes.
- Brew deterministically using a formula, two herbs and spirit-stone fuel. Save interrupted work; block invalid inputs and full outputs without duplication.
- Add configurable spiritual-power recovery, out-of-combat healing and meditation stability support, with bounded use and shared persistent medication spacing.
- Generate reproducible models/textures and a review page; validate resources, runtime rules, build and bounded server behavior.

## Capabilities

### New Capabilities
- `alchemy-foundation`: Physical furnace, ingredients, formula items, deterministic brewing, pill use and survival access.

### Modified Capabilities
- `spirit-stone-resources`: Explicitly allow approved alchemy consumption recipes without stone manufacturing or ore-processing chains.
- `cultivation-gain`: Preserve affinity-based stability with an explicit bounded server-owned medicine multiplier; no change without medicine.
- `resource-export`: Alchemy inventory and documented acquisition/use commands.
- `validation`: Alchemy generation, focused tests, packaged resources and acceptance ledger.

## Impact

New alchemy Java/runtime/client code, item/block/block-entity/menu registration, a small cultivation service/meditation integration, generated assets, recipes, focused tests and docs. No new dependency, world-generation system, quality gambling, breakthrough probability, permanent statistics, trading or sect simulation change. Release follows openspec/config.yaml rules.tasks.
