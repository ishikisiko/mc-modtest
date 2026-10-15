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
Bundled validated rules SHALL map each aptitude to a maximum quality of products made from the same recipe. The quality ladder SHALL be lower, middle, upper and perfect. This ceiling SHALL NOT be interpreted as Huang/Xuan/Di/Tian recipe-grade eligibility. A pure quality-eligibility function SHALL reject qualities above the mapped ceiling. Alchemy SHALL freeze this ceiling at a manual batch start and never produce above it. Forging production remains outside this capability.

#### Scenario: A requested quality exceeds aptitude
- **WHEN** quality eligibility is evaluated for a quality above the aptitude-derived ceiling
- **THEN** it SHALL be rejected regardless of any hypothetical practiced skill level

#### Scenario: Existing alchemy operates
- **WHEN** the furnace completes one of its three manually started recipes
- **THEN** its output SHALL carry the saved batch quality within the initiating player's frozen ceiling

### Requirement: Read-only aptitude page
The H panel SHALL expose a PanelPage reading only PanelContext, displaying the two scores and server-derived product-quality ceilings. Uninitialized players SHALL see an awakening instruction. No client control SHALL change aptitude or imply that forging production is implemented; the page SHALL accurately describe the implemented alchemy-quality connection.

#### Scenario: An awakened player reads crafting aptitude
- **WHEN** the player opens the crafting page
- **THEN** both scores and quality ceilings SHALL match the server snapshot at the supported GUI sizes

See also: [crafting-aptitudes technical note](../../../docs/ai-kb/50_crafting_aptitudes.md), [alchemy-quality](../alchemy-quality/spec.md).
