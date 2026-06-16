## ADDED Requirements

### Requirement: Cultivation archetypes use a cultivation massing grammar
Cultivation archetypes SHALL build their massing through cultivation-specific builders and SHALL NOT delegate to the Western domestic builders (`build_small_house`, `build_shop`, `build_tavern`, `build_lord_manor`). Each cultivation archetype's massing SHALL be composed from cultivation form elements (raised platform, colonnade, eave galleries, sweeping/tiered roofs) rather than inherited cottage/manor massing.

#### Scenario: A cultivation house is not a Western cottage
- **WHEN** `cultivation_house` builds its massing
- **THEN** it SHALL NOT call the `build_small_house` builder
- **AND** its massing SHALL include a raised platform base and a sweeping- or hip-eave roof.

#### Scenario: The town shrine is not a lord manor
- **WHEN** `town_shrine` builds its massing
- **THEN** it SHALL NOT delegate to `build_lord_manor`
- **AND** it SHALL produce a shrine/temple massing (raised platform + colonnade + tiered eave roof) with no corner watch-tower.

### Requirement: Raised stone platform (台基) is a first-class massing element
The massing grammar SHALL provide a raised stone platform (台基) as an explicit massing element that a cultivation volume sits on, taller than the 1–2 cell Western foundation, with a stepped edge and an access stair on the entry side. Hall- and gate-class archetypes SHALL sit on a platform, and platform blocks SHALL resolve through the `PLATFORM_STONE` slot.

#### Scenario: A sect main hall sits on a platform
- **WHEN** `sect_main_hall` builds its massing
- **THEN** the hall volume SHALL rest on a raised platform element of at least 2 cells of exposed height
- **AND** the platform SHALL provide a stepped access stair on the entry side
- **AND** platform blocks SHALL resolve through the `PLATFORM_STONE` slot.

### Requirement: Colonnade veranda (檐廊) wraps hall-class volumes
The massing grammar SHALL provide a colonnade/veranda (檐廊): a row of standoff columns set out from the wall plane carrying a deep eave, producing a shaded ambulatory. Hall- and pavilion-class cultivation archetypes SHALL carry a colonnade on at least the entry face, with columns resolving through the `COLUMN` slot.

#### Scenario: A main hall has a colonnade
- **WHEN** a hall-class cultivation volume is built with a colonnade
- **THEN** a row of columns SHALL stand off the wall plane on the entry face
- **AND** the eave SHALL overhang far enough to cover the colonnade
- **AND** column blocks SHALL resolve through the `COLUMN` slot.

### Requirement: Galleried pavilion (楼阁) and tapering pagoda (塔) massing
The massing grammar SHALL provide a galleried multi-story pavilion (楼阁) with balustraded balconies at upper stories, and a tapering pagoda (塔) whose successive stories inset as they rise and whose crown is a pyramidal roof with a finial. The `scripture_pavilion` archetype SHALL use the pagoda massing.

#### Scenario: The scripture pavilion is a tapering pagoda
- **WHEN** `scripture_pavilion` builds its massing
- **THEN** each story above the first SHALL inset relative to the story below
- **AND** each story SHALL carry its own eave
- **AND** the crown SHALL be a pyramidal roof with a finial.

#### Scenario: A pavilion has a balcony
- **WHEN** a 楼阁-class volume is built with more than one story
- **THEN** upper stories SHALL carry a balustraded balcony resolving through the `BALUSTRADE` slot.

### Requirement: Three-bay mountain gate (山门牌坊) massing
The massing grammar SHALL provide a three-bay mountain gate (山门/牌坊) as a built volume — paired pillar bays carrying a tiered eave lintel spanning a central portal — rather than a flat wall opening with a decal. The `sect_gate` archetype SHALL use this massing.

#### Scenario: The sect gate is a built three-bay gate
- **WHEN** `sect_gate` builds its massing
- **THEN** it SHALL produce standing pillar bays flanking a central portal
- **AND** a tiered eave lintel SHALL span the bays
- **AND** the gate SHALL NOT rely on a flat `moon_gate` decal as the gate itself.

### Requirement: Cultivation builds omit Western domestic tells
A cultivation build SHALL NOT place chimneys, fence-post porches, woodpiles, barrel-clusters, or fence-patches. Quality validation SHALL reject a cultivation structure that contains these Western domestic features.

#### Scenario: No chimney on a cultivation building
- **WHEN** any `cultivation_*` or sect archetype is generated
- **THEN** no chimney element SHALL be present
- **AND** the quality gate SHALL fail the build if a chimney, woodpile, or porch-post feature is detected.

### Requirement: Alchemy furnace (丹炉) replaces the chimney
The `alchemy_room` archetype SHALL feature a 丹炉 (alchemy furnace) — a raised brazier/cauldron-and-pillar furnace resolving through the `RITUAL_ANCHOR` and ritual-metal slots — in place of a domestic chimney.

#### Scenario: The alchemy room has a furnace, not a chimney
- **WHEN** `alchemy_room` is generated
- **THEN** it SHALL place a 丹炉 furnace feature through the ritual slots
- **AND** it SHALL NOT place a domestic chimney element.
