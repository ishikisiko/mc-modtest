## MODIFIED Requirements

### Requirement: Existing cultivation scope documentation is narrowed when initiation ships
The final serial change SHALL update cultivation core/initiation notes, README,
AGENTS guidance, commands/controls, and validation documentation so scope text
matches the first playable loop. It SHALL describe deterministic awakening,
Basic Breathing inheritance, ordinary/spirit meditation, lifespan/calendar,
spirit-stone resources, gain through Qi III, and advancement through the Qi-III
bottleneck. Remaining exclusions SHALL name Qi IV+ cultivation/advancement,
Foundation Establishment, major-realm processes, random/material/environment
systems, and lifespan exhaustion consequences rather than claiming all
meditation or progression is absent.

#### Scenario: Old exclusions are checked after release
- **WHEN** the five serial changes are ready for closeout
- **THEN** no current scope statement SHALL classify their shipped resources, clocks, meditation, gain, or four transitions as absent
- **AND** later-stage and lifecycle exclusions SHALL remain explicit

## ADDED Requirements

### Requirement: The first playable cultivation loop has an indexed same-topic note
The final serial change SHALL add
`docs/ai-kb/30_cultivation_playable_loop.md`, list it in
`docs/ai-kb/INDEX.md`, and cross-link it with the lifespan/calendar,
meditation, gain, advancement, profile, registry, synchronization, resource,
and validation capabilities. The concise note SHALL document the server-owned
loop, time scale and dynamic-reinterpretation warning, exact rates/caps/reserve,
V/B/G/N and H roles, exact transition table, interruption/atomicity rules,
release ceiling, validation commands, and manual evidence boundary.

#### Scenario: The playable-loop note is reviewed
- **WHEN** a contributor follows the cultivation learning chain
- **THEN** the indexed note and same-topic specs SHALL link to each other
- **AND** its constants, controls, commands, and exclusions SHALL match shipped behavior

### Requirement: User-facing acceptance docs cover the complete serial feature
README and acceptance guidance SHALL document item ids, ore/new-chunk behavior,
configuration, calendar/lifespan display, initiation order, V/B/G/N controls, H
read-only status, ordinary/spirit gain, reserve conversion, all four advancement
requirements, Qi-IV release limit, validation commands, and real-client
pass/fail/`not_verified` checks. They SHALL not present backend CRAFT/GenOps
identifiers as required owner-facing usage.

#### Scenario: A player follows the acceptance path
- **WHEN** the documented commands and controls are read in order
- **THEN** the player SHALL be able to prepare ore/resources, complete both steles, meditate in both modes, inspect H, and test four transitions
- **AND** every observation unavailable to automation SHALL be labeled for manual verdict
