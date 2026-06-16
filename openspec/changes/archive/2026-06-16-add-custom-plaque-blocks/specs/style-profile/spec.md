## ADDED Requirements

### Requirement: Plaque bindings dispatch independently of the SIGNAGE slot
A style profile's plaque placement SHALL be driven by `data/myvillage/plaque_bindings.json`, not by the `SIGNAGE` material slot. When a plaque binding exists for an archetype, the build-gen facade-detail pass SHALL invoke the plaque placement op (`place_wall_plaque` or `place_hanging_plaque` with the binding's `frame`, `orientation`, `mount`, and `inscription`) instead of `ops.wall_hanging` with the `SIGNAGE` slot. When no plaque binding exists for the archetype, the existing `SIGNAGE` slot dispatch SHALL run unchanged.

#### Scenario: A doorway has a plaque binding
- **WHEN** the facade-detail pass runs for an archetype with `entry_signage=true`
- **AND** `plaque_bindings.json` has an entry for that archetype
- **THEN** the pass SHALL invoke the plaque placement op
- **AND** the `SIGNAGE` slot SHALL NOT be consulted for that doorway.

#### Scenario: A doorway has no plaque binding
- **WHEN** the facade-detail pass runs for an archetype with `entry_signage=true`
- **AND** `plaque_bindings.json` has no entry for that archetype
- **THEN** the pass SHALL invoke `ops.wall_hanging` against the `SIGNAGE` slot as before
- **AND** a `wall_sign` (or modded canvas sign under the full profile) SHALL be placed.

#### Scenario: A plaque binding references an unknown frame
- **WHEN** `plaque_bindings.json` references a frame preset that is not in the curated catalog
- **THEN** style-profile validation SHALL fail with `unknown_frame_preset`
- **AND** the offending entry SHALL be named in the report.
