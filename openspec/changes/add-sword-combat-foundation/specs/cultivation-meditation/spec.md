## ADDED Requirements

### Requirement: Sword-attack intent participates in meditation interruption and exclusion
An accepted serverbound Qingfeng Sword attack intent SHALL count as the existing attack/swing gameplay interruption while a player is preparing or meditating. The cultivation session SHALL stop idempotently and the same intent SHALL NOT also start a combat action; a later legal intent MAY start combat after both services revalidate current state. Conversely, an accepted meditation start SHALL clear any transient combat session while preserving an unfinished combat recovery lock. Neither service SHALL mutate the other's persistent attachment directly.

#### Scenario: A meditating player presses cultivation attack
- **WHEN** the server accepts the bounded attack intent as an attack/swing interruption
- **THEN** meditation SHALL stop exactly once with the existing attack interruption result
- **AND** no combat move SHALL start from that same intent

#### Scenario: Meditation starts during a combat action
- **WHEN** the server accepts a meditation start after applying current eligibility rules
- **THEN** the current combat action SHALL stop and remote combat animation SHALL clear
- **AND** an unfinished action's recovery deadline SHALL still prevent cancel-to-attack exploitation

#### Scenario: Client state is stale
- **WHEN** the client believes it may attack but the server still owns a non-idle meditation state
- **THEN** server interruption/exclusion rules SHALL decide the result without accepting a client session or combo value
