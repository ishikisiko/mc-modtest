## MODIFIED Requirements

### Requirement: Meditation reserve is a separate durable balance
`meditationQiReserve` SHALL remain a non-negative persistent meditation-only balance. It SHALL NOT be read as, converted to, or substituted for `currentSpiritualPower`; cultivation gain settlement SHALL NOT spend or restore current spiritual power. Separate spiritual-power regeneration MAY use meditation session state to select a data-defined recovery factor, but SHALL commit only through `CultivationService`. Every successful profile replacement and interruption SHALL preserve unspent reserve exactly.

#### Scenario: Spirit meditation spends reserve
- **WHEN** an applicable legacy spirit bonus point is settled
- **THEN** reserve SHALL decrease by exactly one for that bonus point
- **AND** that gain settlement SHALL leave current spiritual power unchanged

#### Scenario: Meditation stops with reserve remaining
- **WHEN** a session is interrupted or stopped after a prior stone conversion
- **THEN** the unspent reserve SHALL remain in the persistent profile

#### Scenario: Power recovery is settled during meditation
- **WHEN** the separate server power-recovery schedule becomes due
- **THEN** recovery SHALL use the meditation factor without consuming or converting reserve

## ADDED Requirements

### Requirement: Power updates preserve the profile schema and unrelated state
Power recovery, spending, and cap normalization SHALL reuse `currentSpiritualPower` and submit complete immutable replacements through `CultivationService`. They SHALL preserve learning, reserve, progress, stability, affinity, lifespan, root and running core technique; this feature SHALL NOT require a new persistent resource field.

#### Scenario: A cast spends power
- **WHEN** the service accepts a valid power cost
- **THEN** only the current power balance SHALL change in the persistent profile
