### Requirement: A region graph of 5–7 regions is generated per seed with a single centered anchor

The region-topology generator SHALL produce a region graph for a given world seed containing between 5 and 7 regions inclusive, with exactly one `anchor` region placed at the map center and the remaining regions embedded around it. The same seed SHALL always produce the same graph. Generation SHALL be constructive: it SHALL NOT discard a candidate graph and regenerate it globally (no re-roll, no global backtracking).

#### Scenario: A graph is generated for a seed

- **WHEN** the generator runs for a world seed
- **THEN** it SHALL output a region graph of 5–7 regions
- **AND** exactly one region SHALL be the anchor, positioned at the map center.

#### Scenario: Generation is deterministic

- **WHEN** the generator runs twice with the same seed
- **THEN** it SHALL produce identical region graphs.

#### Scenario: Generation is constructive, not reject-and-retry

- **WHEN** the generator produces a graph
- **THEN** connectivity and the tier-step limit SHALL be guaranteed by construction
- **AND** the generator SHALL NOT rely on discarding an invalid candidate graph and regenerating to satisfy them.

### Requirement: Tier is assigned outward from the anchor under a tier-step limit

Tier SHALL be assigned starting from the anchor — which SHALL hold the highest tier — and decreasing outward along the connectivity tree, staying within the configured tier range. Any two regions joined by a 连 (passable) edge SHALL differ in tier by at most N = 5.

#### Scenario: The anchor holds the top tier

- **WHEN** tiers are assigned to a generated graph
- **THEN** the anchor region SHALL hold the highest tier in the graph.

#### Scenario: Connected regions respect the tier step

- **WHEN** two regions are joined by a 连 edge
- **THEN** their tier difference SHALL be at most 5.

### Requirement: Edges are typed 连 / 隔 with guaranteed connectivity and a separator palette

The 连 (passable) edges SHALL form a spanning tree over all non-walled regions so every non-walled region is reachable from the anchor through 连 edges, making the world globally traversable by construction. Additional edges between geometric-neighbour regions SHALL be typed by rule: a tier gap greater than N = 5 or an incident `walled` region SHALL be typed 隔 (separated); otherwise the edge MAY be 连. Each 隔 edge SHALL carry a separator type drawn from the palette {特殊山脉 (mountain range), 特殊海洋 (ocean)}.

#### Scenario: The world is globally traversable

- **WHEN** a region graph is generated
- **THEN** the 连 edges over the non-walled regions SHALL form a connected subgraph reaching every non-walled region from the anchor.

#### Scenario: A separated edge carries a palette separator

- **WHEN** an edge is typed 隔
- **THEN** it SHALL carry a separator type of either 特殊山脉 (mountain range) or 特殊海洋 (ocean).

#### Scenario: A large tier gap is separated

- **WHEN** two geometric-neighbour regions differ in tier by more than 5
- **THEN** the edge between them SHALL be typed 隔, not 连.

### Requirement: A walled region is sealed except for at most one pass

A region whose placement role is `walled` SHALL have all incident edges typed 隔, except it MAY retain at most one 连 edge realized as a 关隘 (pass) entry. A walled region SHALL NOT be required to participate in the 连 spanning tree.

#### Scenario: A walled region is sealed

- **WHEN** a region's role is `walled`
- **THEN** all but at most one of its incident edges SHALL be 隔
- **AND** any retained 连 edge SHALL be marked as a 关隘 entry.

### Requirement: The generator emits the graph and a visualization offline only

The generator SHALL emit the region graph as data — regions with tier, role, and position, plus the typed edge list with separator types — and SHALL produce a human-reviewable visualization of the layout. It SHALL NOT write world blocks, take over biome assignment, or run during chunk generation in this capability.

#### Scenario: The generator emits reviewable output

- **WHEN** the generator runs for a seed
- **THEN** it SHALL write the region graph as data and a visualization of the layout
- **AND** it SHALL NOT place any blocks or modify world generation.
