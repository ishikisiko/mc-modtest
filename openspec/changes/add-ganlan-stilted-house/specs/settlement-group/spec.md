## ADDED Requirements

### Requirement: Ganlan stilted-house slice has a settlement group binding
The generator SHALL introduce the Ganlan stilted-house reference slice through
an explicit `ganlan_stilted_house` settlement group descriptor. The group SHALL
bind the Ganlan style profile, its archetype roster, and a dedicated raised-floor
layout strategy without dispatching by `style_id` prefix.

#### Scenario: Ganlan group resolves through group descriptor
- **WHEN** the generator selects the `ganlan_stilted_house` group
- **THEN** it SHALL resolve the `ganlan_stilted_house` style profile
- **AND** it SHALL use a raised-floor stilt-house layout strategy
- **AND** it SHALL NOT select that behavior by string-matching the style id.

#### Scenario: Ganlan group rejects non-roster archetypes
- **WHEN** generation requests an archetype outside the
  `ganlan_stilted_house` group roster
- **THEN** group validation SHALL reject the request rather than substitute a
  medieval, Huipai, Jiangnan, or cultivation archetype.
