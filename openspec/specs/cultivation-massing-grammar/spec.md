# Cultivation Massing Grammar

## Purpose

This spec captures the cultivation-specific building massing grammar used by cultivation town and sect archetypes.

## Requirements

### Requirement: Cultivation archetypes use a cultivation massing grammar
Cultivation archetypes SHALL build their massing through cultivation-specific builders and SHALL NOT delegate to the Western domestic builders (`build_small_house`, `build_shop`, `build_tavern`, `build_lord_manor`). Each cultivation archetype's massing SHALL be composed from cultivation form elements such as raised platforms, colonnades, eave galleries, sweeping roofs, and tiered roofs rather than inherited cottage or manor massing.

#### Scenario: A cultivation house is not a Western cottage
- **WHEN** `cultivation_house` builds its massing
- **THEN** it SHALL NOT call the `build_small_house` builder
- **AND** its massing SHALL include a raised platform base and a sweeping- or hip-eave roof.

#### Scenario: The town shrine is not a lord manor
- **WHEN** `town_shrine` builds its massing
- **THEN** it SHALL NOT delegate to `build_lord_manor`
- **AND** it SHALL produce a shrine or temple massing with a raised platform, colonnade, and tiered eave roof
- **AND** it SHALL NOT include a corner watch-tower.

### Requirement: Raised stone platform (台基) is a first-class massing element
The massing grammar SHALL provide a raised stone platform (台基) as an explicit massing element that a cultivation volume sits on, taller than the 1-2 cell Western foundation, with a stepped edge and an access stair on the entry side. Hall- and gate-class archetypes SHALL sit on a platform, and platform blocks SHALL resolve through the `PLATFORM_STONE` slot.

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
- **WHEN** a galleried pavilion-class volume is built with more than one story
- **THEN** upper stories SHALL carry a balustraded balcony resolving through the `BALUSTRADE` slot.

### Requirement: Three-bay mountain gate (山门牌坊) massing
The massing grammar SHALL provide a three-bay mountain gate (山门/牌坊) as a built volume: paired pillar bays carrying a tiered eave lintel spanning a central portal, rather than a flat wall opening with a decal. The `sect_gate` archetype SHALL use this massing.

#### Scenario: The sect gate is a built three-bay gate
- **WHEN** `sect_gate` builds its massing
- **THEN** it SHALL produce standing pillar bays flanking a central portal
- **AND** a tiered eave lintel SHALL span the bays
- **AND** the gate SHALL NOT rely on a flat `moon_gate` decal as the gate itself.

### Requirement: Cultivation builds omit Western domestic tells
A cultivation build SHALL NOT place chimneys, fence-post porches, woodpiles, barrel clusters, or fence patches. Quality validation SHALL reject a cultivation structure that contains these Western domestic features.

#### Scenario: No chimney on a cultivation building
- **WHEN** any `cultivation_*` or sect archetype is generated
- **THEN** no chimney element SHALL be present
- **AND** the quality gate SHALL fail the build if a chimney, woodpile, or porch-post feature is detected.

### Requirement: Alchemy furnace (丹炉) replaces the chimney
The `alchemy_room` archetype SHALL feature a 丹炉 (alchemy furnace): a raised brazier/cauldron-and-pillar furnace resolving through the `RITUAL_ANCHOR` and ritual-metal slots, in place of a domestic chimney.

#### Scenario: The alchemy room has a furnace, not a chimney
- **WHEN** `alchemy_room` is generated
- **THEN** it SHALL place a 丹炉 furnace feature through the ritual slots
- **AND** it SHALL NOT place a domestic chimney element.

### Requirement: Walled rear courtyard (后院/院墙) is a first-class massing element
The cultivation massing grammar SHALL provide a walled rear courtyard (后院): a low enclosing wall (院墙) ringing a rear or side open-air courtyard ground patch, with a single gate opening on the entry-adjacent side. The enclosing wall SHALL resolve through stone/tile wall slots (e.g. `BALUSTRADE` / `RIDGE_ORNAMENT` / `PLATFORM_STONE`) and SHALL NOT use Western fence-post or woodpile features. The courtyard floor SHALL reuse the existing courtyard/ground patch element. The walled rear courtyard is a plan-level (ground) element and does not by itself add a roofed silhouette volume; a variant template that uses a rear courtyard for silhouette differentiation SHALL pair it with a rear annex (后罩房) volume.

#### Scenario: A cultivation variant builds a walled rear courtyard
- **WHEN** a cultivation variant is built with a walled rear courtyard
- **THEN** a low enclosing 院墙 wall SHALL ring the courtyard ground patch behind or beside the main volume
- **AND** the wall SHALL leave a single gate opening on the side facing the entry approach
- **AND** the wall blocks SHALL resolve through stone/tile wall slots, not through fence or porch-post features.

#### Scenario: The rear courtyard keeps cultivation, not Western, tells
- **WHEN** a walled rear courtyard is dressed
- **THEN** its fittings SHALL come from cultivation slots (e.g. ritual anchors, planting, water, lanterns)
- **AND** it SHALL NOT introduce chimneys, woodpiles, barrel clusters, or fence patches.

### Requirement: Cultivation wall height, platform height, ridge axis, and ancillary volumes are parametric knobs
The cultivation massing grammar SHALL expose wall height, raised platform (台基) height, roof-ridge axis, and the optional side wing (厢房) and rear annex (后罩房) volumes as parametric knobs that an archetype's variant template selects among, rather than as constants fixed per archetype. Selecting a taller platform and wall SHALL raise the built volume's total height; selecting the transverse ridge axis SHALL change the silhouette's proportion read; selecting a side wing or rear annex SHALL add an attached secondary volume with its own roof.

#### Scenario: A variant raises its platform and wall
- **WHEN** a cultivation variant template specifies a raised platform and a taller wall
- **THEN** the built main volume SHALL use that platform height and wall height
- **AND** another variant of the same archetype MAY use a lower platform and a shorter wall, yielding a visibly different height.

#### Scenario: A variant flips its roof-ridge axis
- **WHEN** a cultivation variant template specifies a ridge axis transverse to the archetype default
- **THEN** the roof ridge SHALL run on the specified axis
- **AND** the resulting eave-front versus gable-front read SHALL change accordingly.

#### Scenario: A variant attaches a side wing or rear annex
- **WHEN** a cultivation variant template specifies a side wing (厢房) or rear annex (后罩房)
- **THEN** an attached secondary volume SHALL be added with its own roof
- **AND** the combined massing SHALL register the additional volume for silhouette scoring.
