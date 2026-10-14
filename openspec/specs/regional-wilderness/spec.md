# regional-wilderness Specification

## Purpose
Define regional realm ranges, persistent beast birth assignments and bounded projections of existing ledger rogues.

## Requirements
### Requirement: Regions define encounter realm ranges
Bundled data SHALL explicitly map region IDs to inclusive beast and rogue realm-stage ranges and bounded encounter settings. Regions outside the runtime graph or dimensions outside the overworld MUST NOT invent a default high-tier encounter.

#### Scenario: Deterministic in-range draw
- **WHEN** the same region and sampling inputs are used repeatedly
- **THEN** the same eligible beast realm is returned and every result lies inside the configured endpoints

### Requirement: Beast birth realm persists
A naturally generated beast SHALL receive and persist one regional realm assignment, and SHALL derive server health and attack from that assignment. Rejoining, saving, moving or changing dimensions MUST NOT reroll its birth realm. Old command or egg beasts without an assignment SHALL retain baseline attributes.

#### Scenario: Saved beast crosses a boundary
- **WHEN** a natural beast is saved, reloaded and moved into another region
- **THEN** its realm remains unchanged and modifiers do not stack

### Requirement: Wilderness rogues remain ledger projections
WorldSimAvatars SHALL select only living unsected eligible people from the local ledger region whose realm lies in its rogue range. A person SHALL have at most one avatar, with global and local caps and bounded work using loaded chunks. Avatars MUST remain unsaved and server-owned.

#### Scenario: No eligible rogue
- **WHEN** a region has no eligible ledger person
- **THEN** no rogue is fabricated and diagnostics report the empty candidate set
