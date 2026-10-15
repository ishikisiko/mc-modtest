## ADDED Requirements

### Requirement: Alchemy resource export
The jar SHALL include all thirteen alchemy item models, textures, furnace models and blockstate, bilingual text, recipes, loot and bundled rules. README SHALL document survival acquisition, furnace operation and give commands.

#### Scenario: Packaged alchemy
- **WHEN** the feature jar is built
- **THEN** the alchemy validator verifies resources in that jar and the documented items resolve
