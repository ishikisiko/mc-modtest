## MODIFIED Requirements

### Requirement: Server-owned deterministic brewing
The furnace SHALL own five saved slots and brew from bundled validated rules after a manual one-batch start. A formula is reusable, herb order is interchangeable, and herbs/fuel SHALL be consumed exactly once with output creation. Active input slots SHALL be locked; invalidated inputs cancel progress, while full or incompatible output pauses without consuming ingredients. Chunk unload SHALL pause production; breaking SHALL drop held inventory exactly once. Owner, aptitude ceiling and rolled result SHALL persist with progress.

#### Scenario: Interrupted brew
- **WHEN** a valid partly brewed furnace is saved and reloaded
- **THEN** its inventory, fixed result and matching progress resume without duplicate costs or offline catch-up

#### Scenario: Invalid or obstructed brew
- **WHEN** ingredients mismatch or output is full
- **THEN** the furnace produces no output and consumes no input
