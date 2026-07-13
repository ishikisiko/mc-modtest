## ADDED Requirements

### Requirement: Advancement preserves v2 profile identity outside transition fields
A successful advancement SHALL construct one immutable v2 replacement that
changes only realm id, stage id, stage-local progress to zero, and stability by
the declared success cost. It SHALL preserve `lifespanConsumedTicks`,
`meditationQiReserve`, current spiritual power, spiritual root, learned
techniques/mastery, schema version, and every unrelated field. It SHALL use
`CultivationService` as the sole attachment mutation boundary.

#### Scenario: Success preserves durable counters and learning
- **WHEN** a player with nonzero lifespan, reserve, spiritual power, affinities, and mastery advances
- **THEN** all those values SHALL equal their pre-advancement values
- **AND** only transition-owned fields SHALL change

#### Scenario: A transition replacement is invalid
- **WHEN** target references or calculated stability fail profile validation
- **THEN** the previous profile SHALL remain installed and no changed snapshot SHALL be sent

### Requirement: Bottleneck interruption is one atomic stability-only replacement
An eligible player/world interruption of the Qi-III bottleneck SHALL submit at
most one immutable replacement through `CultivationService` that changes only
stability by exact loss `5`, clamped at zero. Ordinary or administrative
teardown SHALL submit no penalty replacement. No interruption SHALL reset
progress or spend reserve.

#### Scenario: A full-progress Qi-III bottleneck is interrupted
- **WHEN** its current stability is 80 and a penalized interruption occurs
- **THEN** one replacement SHALL retain Qi III and progress 1200 with stability 75
- **AND** lifespan, reserve, power, root, and techniques SHALL remain unchanged

#### Scenario: Several hooks observe the same interruption
- **WHEN** interruption handling is invoked repeatedly after session removal
- **THEN** later invocations SHALL install no additional profile replacement
