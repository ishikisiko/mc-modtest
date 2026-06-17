## ADDED Requirements

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
