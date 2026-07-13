## ADDED Requirements

### Requirement: The cultivation initiation ritual has an indexed same-topic note
The change SHALL add `docs/ai-kb/29_cultivation_initiation_ritual.md`, list it in `docs/ai-kb/INDEX.md`, and cross-link it with the `cultivation-initiation-ritual` capability and affected cultivation baseline specs. The note SHALL document deterministic inputs and datapack-change semantics, fixed algorithm/version boundary, count distribution, exact affinity allocation, atomic awakening, definition-driven inheritance, both stele ids/acquisition paths, command aliases, validation, manual verdict status, and explicit non-goals.

#### Scenario: The new KB note is reviewed
- **WHEN** a contributor follows the learning-chain entry for cultivation initiation
- **THEN** the link SHALL resolve to the new note
- **AND** the note and capability spec SHALL link to each other
- **AND** its stated behavior SHALL match the shipped implementation and current validation commands

### Requirement: Existing cultivation scope documentation is narrowed when initiation ships
The same change SHALL update `docs/ai-kb/28_cultivation_core.md`, README, AGENTS guidance, and directly related command/validation documentation so they no longer state that spiritual-root awakening, initiation commands, or new cultivation blocks are entirely absent. The updated text SHALL state that deterministic awakening and basic-breathing inheritance are implemented while meditation, technique execution, spiritual-power recovery, cultivation gain, and qi-refining advancement remain absent.

#### Scenario: Foundation exclusions are checked after release
- **WHEN** the initiation change is ready for closeout
- **THEN** no current scope statement SHALL still classify the shipped two-step ritual as unimplemented
- **AND** remaining exclusions SHALL be limited to behavior that is still absent

### Requirement: The two-boundary initiation scope exception is documented without becoming precedent
The design and initiation KB note SHALL record that the owner explicitly combined deterministic awakening and basic-breathing inheritance in one change despite the foundation note's normal one-boundary guidance. They SHALL preserve the two steps as independent services/facilities and SHALL state that the exception does not include technique execution or meditation.

#### Scenario: Later work reads the exception
- **WHEN** a future cultivation proposal cites this initiation change
- **THEN** it SHALL find that awakening and inheritance were deliberately combined as consecutive but independent rituals
- **AND** it SHALL NOT infer permission to bundle the next execution, recovery, progression, or advancement boundaries

### Requirement: Version and changelog wording references the single source rule
Documentation SHALL reference the authoritative feature-version task rule in `openspec/config.yaml` rather than restating its arithmetic. The implementation task SHALL update mod version, mod metadata, README jar examples, and CHANGELOG together, and the KB/README feature wording SHALL agree with that release.

#### Scenario: Release docs are reviewed
- **WHEN** the feature version is selected during implementation
- **THEN** every version-sensitive file SHALL identify the same release
- **AND** docs SHALL describe only behavior actually included in that release
