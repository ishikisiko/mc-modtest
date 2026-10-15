## MODIFIED Requirements

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
