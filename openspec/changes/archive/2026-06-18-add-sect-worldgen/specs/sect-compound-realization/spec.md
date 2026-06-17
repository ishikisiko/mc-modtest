## ADDED Requirements

### Requirement: The compound realizer places onto worldgen-derived terrain

The sect compound realizer SHALL be reusable by the worldgen path: given a terrace profile and the mountain derived from it, the realizer SHALL place the compound's volumes, galleries, stairs, and the flying-bridge feature onto the derived terrain using the same deterministic geometry as the `/myvillage sect [seed]` on-the-spot build. The existing on-the-spot command behavior SHALL be preserved unchanged when no derived terrain is supplied.

#### Scenario: The same realizer serves command and worldgen

- **WHEN** the worldgen path supplies a terrace profile and its derived mountain
- **THEN** the compound realizer SHALL place the same volumes, galleries, stairs, and feature it would for `/myvillage sect [seed]`, sat on the derived terrain
- **AND** the on-the-spot `/myvillage sect [seed]` build SHALL remain unchanged when no derived terrain is supplied.
