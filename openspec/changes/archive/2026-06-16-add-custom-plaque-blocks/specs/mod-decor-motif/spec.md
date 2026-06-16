## MODIFIED Requirements

### Requirement: Ritual and gate motifs route through the new slots
The existing ritual/altar motifs (`incense_altar`, `spirit_array`) SHALL resolve their focal props through the `RITUAL_ANCHOR` and `MARKET_FITTINGS` slots so they place mod blocks under the `full` profile. The sect gate / 牌坊 motif SHALL resolve its central tablet through the plaque block family (`myvillage:wall_plaque` or `myvillage:hanging_plaque`) paired with an inscription from `plaque_bindings.json`, NOT through the `SIGNAGE` slot. The paifang's flanking `MARKET_FITTINGS` props SHALL continue to resolve through the `MARKET_FITTINGS` slot.

#### Scenario: A ritual altar places its anchor from the RITUAL_ANCHOR slot
- **WHEN** a ritual/altar motif is placed under the `full` profile
- **THEN** its focal anchor SHALL resolve from the `RITUAL_ANCHOR` slot (e.g. a brazier or arcane pedestal)
- **AND** surrounding supporting blocks SHALL resolve from their existing slots.

#### Scenario: A paifang places its central tablet via the plaque block family
- **WHEN** the sect gate / 牌坊 motif is placed
- **THEN** its central tablet SHALL be a `myvillage:hanging_plaque` (or `myvillage:wall_plaque` per the binding) with `frame`, `orientation`, and `inscription` resolved from `plaque_bindings.json`
- **AND** the `SIGNAGE` slot SHALL NOT be consulted for the central tablet
- **AND** the paifang's flanking `MARKET_FITTINGS` props SHALL still resolve through the `MARKET_FITTINGS` slot.

#### Scenario: A paifang has no plaque binding
- **WHEN** the sect gate motif runs for an archetype with no entry in `plaque_bindings.json`
- **THEN** the central tablet SHALL be skipped (no `wall_sign`, no plaque)
- **AND** validation SHALL emit a warning, not an error, for the missing tablet.
