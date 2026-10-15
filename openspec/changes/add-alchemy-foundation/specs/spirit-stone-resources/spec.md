## MODIFIED Requirements

### Requirement: The first resource slice remains cultivation-neutral
The resource layer SHALL keep the low-grade spirit stone a plain item with no
item-use action, raw item, storage block, fragment, refining machine, currency
behavior, or middle/high-grade counterpart. Recipes SHALL NOT manufacture spirit
stones or process their ores. Explicit later cultivation capabilities SHALL own
consumption. Alchemy foundation SHALL permit its six named herb-infusion and
three formula-sheet shapeless recipes to consume the plain low-grade item, with
the exact authored ingredients and non-stone output checked by validation.

#### Scenario: A low-grade spirit stone is used before cultivation integration
- **WHEN** a player uses the item in this resource-only slice
- **THEN** it SHALL perform no cultivation mutation and SHALL not consume itself

#### Scenario: Approved alchemy consumes an existing stone
- **WHEN** a player crafts one of the six herb infusions or three formula sheets
- **THEN** the recipe SHALL consume the specified existing low-grade stone and plant/herb inputs
- **AND** no spirit stone, ore-processing intermediate or higher-grade stone SHALL be produced
