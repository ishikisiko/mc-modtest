## ADDED Requirements

### Requirement: Skills declare charge and wave behavior as data
Registered combat skill data and its linked active-technique definition SHALL define the weapon family, bounded charge window, power cost, action timing, advancing-wave dimensions, travel speed, target limit, and damage parameters. Runtime code SHALL NOT duplicate balance values in weapon-specific branches. Existing melee move definitions SHALL retain their accepted behavior.

#### Scenario: A sword-qi tuning field is changed
- **WHEN** a valid skill data value changes and the resources are reloaded or the runtime is restarted as supported
- **THEN** the runtime SHALL obtain that value from the registered skill definition
