## ADDED Requirements

### Requirement: Change 5 adds one fourth action to the same bounded intent
This change SHALL add one fourth action to the same bounded intent.
After the meditation change's exact normal/spirit/stop protocol, it SHALL
extend the same serverbound action codec with exactly
`START_BREAKTHROUGH`. The action SHALL contain no target, kind, duration,
progress, stability, cost, loss, success, profile, identity, position, or
velocity field. V, B, and G SHALL retain start-normal, start-spirit, and stop;
configurable N SHALL send start breakthrough. G SHALL stop either meditation or
advancement.

#### Scenario: The post-advancement action codec is inspected
- **WHEN** protocol validation enumerates its actions and fields
- **THEN** it SHALL contain exactly start normal, start spirit, stop, and start breakthrough
- **AND** the server SHALL derive sender identity and all transition values

#### Scenario: Change 3 is inspected without Change 5
- **WHEN** the earlier meditation change is validated at its own boundary
- **THEN** it SHALL still contain only V/B/G and the three original actions
- **AND** N/start breakthrough SHALL be attributable only to this change

### Requirement: Meditation and advancement share one exclusive session owner
The existing transient cultivation session manager SHALL add advancement states
without running a second simultaneous state machine for the same player.
Meditation starts SHALL be rejected during advancement, advancement starts
SHALL be rejected during preparation/meditation, and the common stop and
interruption routing SHALL remove the one current state idempotently.

#### Scenario: Both starts arrive in one server interval
- **WHEN** the server accepts one start before handling the other
- **THEN** only the first eligible session SHALL exist
- **AND** the second request SHALL be rejected as conflicting
