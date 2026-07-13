## MODIFIED Requirements

### Requirement: Meditation controls use V B X N and the H meditation tab
The client SHALL retain configurable V for normal meditation, B for spirit
meditation, X for stop, N for advancement, and H for the cultivation screen.
The H screen SHALL expose a Meditation tab with normal, spirit, stop, and
advance buttons that send exactly the same actions as V, B, X, and N. Opening,
closing, switching, or rendering tabs SHALL send no action. MyVillage SHALL
leave `G` unreserved by default and SHALL NOT add GuideME-specific interception,
remapping, or automatic binding migration; ordinary configurable-key repeat and
screen handling SHALL remain unchanged.

#### Scenario: A meditation button is activated
- **WHEN** the local player activates normal, spirit, stop, or advance in H
- **THEN** the client SHALL send only the matching bounded action once

#### Scenario: The player switches tabs
- **WHEN** the player moves between Profile and Meditation without activating an action button
- **THEN** the client SHALL send no cultivation intent or profile mutation

#### Scenario: Keyboard control remains available
- **WHEN** no conflicting screen captures V, B, X, or N
- **THEN** each key SHALL retain the same action semantics as its H-screen button

#### Scenario: GuideME owns its default item-index key
- **WHEN** MyVillage and GuideME register their default client controls
- **THEN** MyVillage SHALL assign stop meditation to X instead of G
- **AND** MyVillage SHALL not intercept or rewrite GuideME's G binding
