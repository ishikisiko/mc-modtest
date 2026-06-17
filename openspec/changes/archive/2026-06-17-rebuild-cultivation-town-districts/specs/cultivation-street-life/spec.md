## ADDED Requirements

### Requirement: Street dressing uses a cultivation vocabulary
Runtime street and open-region dressing for the cultivation town SHALL use a cultivation-themed vocabulary — shop banners (幌子), spirit-field / planting beds (药圃/灵田), alchemy furnaces (炼丹炉), artifact stalls (法器摊), and formation floor patterns (阵纹) — in place of the prior placeholder vanilla furniture (plain barrels, oak fences, campfires, white wool, podzol).

#### Scenario: Market streets carry cultivation dressing
- **WHEN** the realizer dresses streets and open regions in a market district
- **THEN** it SHALL place cultivation-themed fixtures from the street-life vocabulary
- **AND** it SHALL NOT place the prior placeholder white-wool or podzol fillers as the primary dressing.

### Requirement: Inhabitants populate the town
The realizer SHALL place inhabitants — villager entities and optional spirit-beast entities — distributed across districts so the town reads as occupied rather than empty.

#### Scenario: A realized town is occupied
- **WHEN** a cultivation town is realized
- **THEN** villager entities SHALL be placed across multiple districts
- **AND** the inhabitant count SHALL scale with the town's parcel count rather than being zero.

### Requirement: Optional decor is applied as profile-gated skins only
Cultivation street-life fixtures that rely on external decor mods (staged `fetzisdisplays`) SHALL be applied through profile-gated slots resolved from the mod catalog, so that under `--profile vanilla` every fixture resolves to a vanilla fallback and under `--profile full` the external blocks are used.

#### Scenario: Street life degrades to vanilla without decor mods
- **WHEN** the town is generated under `--profile vanilla`
- **THEN** every street-life fixture SHALL resolve to a vanilla fallback block
- **AND** no external-mod block id SHALL appear in the output.

#### Scenario: Street life uses decor blocks when present
- **WHEN** the town is generated under `--profile full` with the staged decor mod available
- **THEN** the authored external decor blocks SHALL be used for the themed fixtures.
