# Mod Decor Motif

## Purpose

This spec captures decoration motifs that compose external-mod blocks through style material slots while preserving vanilla fallback behavior.

## Requirements

### Requirement: Decoration motifs compose mod blocks through semantic slots
The generator SHALL provide composition motifs that assemble external-mod blocks for a recognizable prop, resolving each block through a semantic material slot (and the orientation adapter where the family needs it) rather than hardcoding a mod id. A motif SHALL register through the existing motif registry so styles can list it in `allowed_motifs`.

#### Scenario: A motif resolves its blocks through slots
- **WHEN** a registered decoration motif places its blocks
- **THEN** every placed block SHALL be resolved from a material slot of the active style
- **AND** any family with non-vanilla grammar SHALL be oriented through the orientation adapter.

#### Scenario: A style enables a motif
- **WHEN** a style lists a decoration motif in `allowed_motifs`
- **THEN** style-vocabulary validation SHALL accept it because the motif is registered
- **AND** generation for that style MAY place the motif.

### Requirement: A market stall motif composes a 市井 stall
The generator SHALL provide a `market_stall` motif that composes a stall counter, an overhead canopy oriented via the awning family, and a goods display, using the `MARKET_FITTINGS`, `ROOF_TILE`/canopy, and related slots.

#### Scenario: A market stall is placed under the full profile
- **WHEN** `market_stall` is placed for a style loaded under the `full` profile
- **THEN** the counter and display SHALL resolve to `MARKET_FITTINGS` mod ids
- **AND** the canopy SHALL be an awning oriented as a slanted eave over the counter.

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

### Requirement: Every motif degrades to a vanilla fallback
Each decoration motif SHALL produce a coherent result under the `vanilla` profile, resolving every block to its slot's trailing `minecraft:` fallback, with no mod id and no air. A style that omits an optional slot a motif uses SHALL cause that motif to skip the affected element rather than fail.

#### Scenario: A motif runs under the vanilla profile
- **WHEN** a decoration motif is placed for a style loaded with `available_namespaces = {"minecraft"}`
- **THEN** every placed block SHALL be a `minecraft:` id
- **AND** the motif SHALL place no air and SHALL NOT raise an empty-slot error.

#### Scenario: A motif's optional slot is omitted by the style
- **WHEN** a motif references an optional slot the style does not define
- **THEN** the motif SHALL skip the element backed by that slot
- **AND** it SHALL still place the rest of its composition.
