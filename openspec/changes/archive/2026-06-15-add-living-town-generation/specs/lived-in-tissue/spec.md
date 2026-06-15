## ADDED Requirements

### Requirement: Designed negative space is dressed with daily-life props
The lived-in-tissue layer SHALL fill the plan's reserved negative-space regions with domestic and market props (e.g. water well, 晾衣 drying lines, 柴垛 woodpiles, 菜畦 vegetable plots, market stalls) without blocking circulation.

#### Scenario: Reserved gaps become lived-in
- **WHEN** the tissue layer dresses a reserved negative-space region
- **THEN** the region SHALL receive daily-life or market props
- **AND** no prop SHALL block a traversable street or gate.

### Requirement: The town shows smoke and human-scale light
Dwelling parcels SHALL emit chimney smoke and/or show lit windows, and civic or temple parcels SHALL show appropriate light (e.g. 香炉, lanterns), so the town shows visible signs of habitation. Ground-level smoke and light props SHALL use parcel ground references and free parcel cells, not post-placement roof heightmap hits.

#### Scenario: Habitation is visible
- **WHEN** the tissue layer finishes a town
- **THEN** dwelling parcels SHALL show smoke or lit windows
- **AND** the civic/temple parcel SHALL show its characteristic light source.
- **AND** ground campfires and lantern posts SHALL NOT be placed on template roof cells.

### Requirement: Detail density follows a market-to-lane falloff with wear
Prop density SHALL be highest at the market mouth or central square and fall off toward back lanes, and some surfaces SHALL carry wear or imperfection rather than appearing uniformly pristine.

#### Scenario: Density falls off from the market
- **WHEN** the tissue layer scatters props across the town
- **THEN** the market mouth or square SHALL be denser than the back lanes
- **AND** at least some surfaces SHALL exhibit wear or imperfection.
