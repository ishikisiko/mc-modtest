## ADDED Requirements

### Requirement: Hui-style reference slice has a settlement group binding
The generator SHALL introduce the Hui-style reference slice through an explicit
`chinese_huipai_mansion` settlement group descriptor. The group SHALL bind the
Hui-style style profile, the Hui-style mansion roster, and a dedicated
Hui-style tianjing layout strategy without dispatching by `style_id` prefix.

#### Scenario: Hui-style group resolves through group descriptor
- **WHEN** the generator selects the `chinese_huipai_mansion` group
- **THEN** it SHALL resolve the `chinese_huipai_mansion` style profile
- **AND** it SHALL use a Hui-style tianjing layout strategy
- **AND** it SHALL NOT select that behavior by string-matching the style id.

#### Scenario: Hui-style group rejects non-roster archetypes
- **WHEN** generation requests an archetype outside the
  `chinese_huipai_mansion` group roster
- **THEN** group validation SHALL reject the request rather than substitute a
  Jiangnan mansion archetype.
