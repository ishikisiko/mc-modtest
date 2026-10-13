## ADDED Requirements

### Requirement: Power commands have equivalent language aliases
Power administration SHALL expose `qi` and `lingli` under both `cultivation` and `xiulian`, with equivalent permission checks, target selection, set and fill behavior. Set SHALL reject values outside the resolved cap; fill SHALL use the resolved cap. Info SHALL report current power and capacity.

#### Scenario: Alias routes fill the same profile
- **WHEN** an administrator executes any equivalent English or pinyin fill route
- **THEN** the service SHALL apply the same capped immutable power update
