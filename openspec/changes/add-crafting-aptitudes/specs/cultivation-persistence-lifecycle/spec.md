## ADDED Requirements

### Requirement: Crafting aptitude persistence is independent
Crafting aptitudes SHALL use a separate immutable serialized attachment with copyOnDeath, owned for replacement by CultivationService. This SHALL leave CultivationProfile v4 unchanged and SHALL add no manual Clone copy. Missing legacy crafting data SHALL initialize only for an awakened player, without replacing existing generated values.

#### Scenario: A legacy awakened save loads
- **WHEN** the save lacks crafting aptitude data
- **THEN** the server SHALL initialize and persist it once before the first owning snapshot
