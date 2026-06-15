## ADDED Requirements

### Requirement: Slots are populated with mod ids per design-intent role
Style profiles SHALL populate material slots with confirmed external-mod block ids drawn from `exmod/mod_block_catalog.json`, placed at the **front** of the matching slot list so they are preferred when active, while the trailing `minecraft:` fallback required by the existing fallback convention is preserved. Mod ids SHALL be assigned to slots according to their design-intent role: `ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, and `MARKET_FITTINGS`, plus the existing `FURNITURE`, `LIGHTING`, and wall/window-related slots.

#### Scenario: A populated slot prefers a mod id under the full profile
- **WHEN** a style with a populated slot is loaded under the `full` profile and a build operation resolves that slot's primary entry
- **THEN** it SHALL resolve to a mod id
- **AND** the slot's last entry SHALL still be a `minecraft:` fallback.

#### Scenario: The same slot under the vanilla profile resolves to the fallback
- **WHEN** the same style is loaded with `available_namespaces = {"minecraft"}`
- **THEN** namespace filtering SHALL drop the leading mod ids
- **AND** the slot SHALL resolve to its trailing `minecraft:` fallback exactly as before this change.

### Requirement: Vanilla-profile output is unchanged by slot population
Populating slots with mod ids SHALL NOT change generation output under the `vanilla` profile. After this change, generating any affected library under `available_namespaces = {"minecraft"}` SHALL produce output identical to the pre-change `vanilla` output.

#### Scenario: Vanilla output is byte-stable across the change
- **WHEN** a building library is generated under the `vanilla` profile before and after slot population
- **THEN** the two outputs SHALL be identical
- **AND** no mod id SHALL appear in the `vanilla` output.

### Requirement: Populated mod ids reference only confirmed namespaces
Every mod id inserted into a slot SHALL belong to a namespace listed in the catalog's confirmed mod set. A slot SHALL NOT reference a namespace that is absent from `exmod/mod_block_catalog.json`'s confirmed set (e.g. an Asian-decor namespace that is not staged).

#### Scenario: A slot id uses a confirmed namespace
- **WHEN** a style slot lists a non-`minecraft` block id
- **THEN** that id's namespace SHALL be present in the catalog's confirmed mod set.

#### Scenario: An unstaged namespace is rejected
- **WHEN** a slot would reference a namespace not in the confirmed mod set
- **THEN** that id SHALL NOT be added in this change
- **AND** the role SHALL instead use a present-namespace substitute or its vanilla fallback.
