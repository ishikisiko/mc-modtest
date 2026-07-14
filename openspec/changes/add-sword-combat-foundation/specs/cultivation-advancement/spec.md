## ADDED Requirements

### Requirement: Sword-attack intent participates in advancement interruption and exclusion
An accepted serverbound Qingfeng Sword attack intent SHALL count as the existing attack/swing gameplay interruption while a player is advancing. The advancement session SHALL stop idempotently, apply only its already-declared interruption consequence, and the same intent SHALL NOT also start combat. Conversely, an accepted advancement start SHALL clear transient combat state while preserving any unfinished combat recovery deadline. Combat SHALL not select an advancement result, target stage, duration, or penalty.

#### Scenario: An ordinary advancement player attacks
- **WHEN** the bounded sword intent is accepted as an attack interruption before completion
- **THEN** advancement SHALL end once with the existing ordinary interruption behavior
- **AND** no combat action SHALL start from that same intent

#### Scenario: A bottleneck advancement player attacks
- **WHEN** the bounded sword intent interrupts the current bottleneck
- **THEN** the declared bottleneck penalty SHALL apply at most once
- **AND** duplicate combat/cultivation hooks SHALL not charge it again

#### Scenario: Advancement starts during combat recovery
- **WHEN** the advancement start passes all current server validation
- **THEN** transient combat action state SHALL clear and tracking clients SHALL receive a stop
- **AND** the combat recovery deadline SHALL remain server-owned until expiry
