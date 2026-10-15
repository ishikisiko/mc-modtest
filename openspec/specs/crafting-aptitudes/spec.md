# Crafting Aptitudes

## Purpose

Provide saved independent player aptitudes and authoritative product-quality ceilings.

## Requirements

### Requirement: Independent crafting aptitudes
The player SHALL have separate alchemy and forging aptitude values with a persisted initialization flag. Their server-owned values SHALL be generated once on root awakening, or once at login for an already-awakened legacy save. Generation SHALL be deterministic per world/player/profession and SHALL not consume the existing spiritual-root RNG.

#### Scenario: A player awakens
- **WHEN** a previously uninitialized player receives a spiritual root
- **THEN** both aptitude values SHALL be installed before the owning snapshot

#### Scenario: Generated values survive lifecycle
- **WHEN** a generated player dies, reconnects, changes dimension, clears/reawakens the root or restarts the server
- **THEN** both saved aptitude values SHALL remain unchanged

### Requirement: Aptitude limits product quality
Bundled validated rules SHALL map each aptitude to a maximum quality of products made from the same recipe. The quality ladder SHALL be lower, middle, upper and perfect. This ceiling SHALL NOT be interpreted as Huang/Xuan/Di/Tian recipe-grade eligibility. A pure quality-eligibility function SHALL reject qualities above the mapped ceiling. Current ungraded pill output and furnace behavior SHALL remain unchanged in this foundation slice.

#### Scenario: A requested quality exceeds aptitude
- **WHEN** quality eligibility is evaluated for a quality above the aptitude-derived ceiling
- **THEN** it SHALL be rejected regardless of any hypothetical practiced skill level

#### Scenario: Existing alchemy operates
- **WHEN** the existing furnace completes one of its three recipes
- **THEN** it SHALL retain the existing fixed ungraded result and SHALL not claim quality generation

### Requirement: Read-only aptitude page
The H panel SHALL expose a PanelPage reading only PanelContext, displaying the two scores and server-derived product-quality ceilings. Uninitialized players SHALL see an awakening instruction. No client control SHALL change aptitude or imply that forging/quality production is implemented.

#### Scenario: An awakened player reads crafting aptitude
- **WHEN** the player opens the crafting page
- **THEN** both scores and quality ceilings SHALL match the server snapshot at the supported GUI sizes
