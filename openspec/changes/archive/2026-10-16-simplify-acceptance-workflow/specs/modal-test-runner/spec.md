## ADDED Requirements

### Requirement: Feature scenarios are selectable and independently prepared
Alchemy evidence SHALL accept a bounded selection of brewing, medicine, persistence, ui and multiplayer cases. Each selected case SHALL prepare its prerequisites without requiring unselected gameplay cases. Unknown, empty or duplicate selections SHALL fail before remote allocation. Reports SHALL identify selected, completed and unrun cases; a subset pass SHALL NOT establish full acceptance. Failures and interrupts SHALL remain failures and earlier output SHALL NOT be overwritten.

#### Scenario: Retry medicine only
- **WHEN** the medicine case is selected on a fresh workspace
- **THEN** the fixture SHALL initialize the player and medicine preconditions without running brewing, UI, persistence or multiplayer
- **AND** the report SHALL explicitly record partial coverage

#### Scenario: A selected case fails
- **WHEN** an assertion or execution fails within a selected case
- **THEN** execution SHALL stop, preserve its failure and leave subsequent cases unrun

### Requirement: Combined delivery uses one immutable workspace
The runner SHALL provide release-alchemy that performs the complete release gate once, clean stage1, and all alchemy runtime cases sequentially in one Sandbox and extracted source workspace. The generic release preset SHALL NOT hardcode feature-specific preliminary test selection. The combined preset SHALL reject subset selection and SHALL retain per-step timing, source/artifact identities, failures and confirmed cleanup. No source mutation, persistent world/build cache, automatic retry or local heavy fallback SHALL be introduced.

#### Scenario: Combined delivery succeeds
- **WHEN** release-alchemy passes
- **THEN** one uploaded fixed source SHALL supply both the fresh jar and full runtime evidence
- **AND** release, clean server and runtime SHALL have separate command results

#### Scenario: Release fails
- **WHEN** the release step fails
- **THEN** game validation SHALL remain unrun and cleanup SHALL still be attempted
