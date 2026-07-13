# Docs Knowledge Base

## Purpose

These specifications govern how the repository's documentation knowledge base is organized and kept consistent. They define the single entry map for `docs/ai-kb/` and `openspec/specs/`, the cross-referencing between same-topic docs and specs, the single-source rule for shared conventions, the rule that scope statements must reflect shipped behavior, and the write-in guideline for adding new knowledge-base content.
## Requirements
### Requirement: The knowledge base has a single discoverable entry map

The repository's documentation SHALL provide one entry point that maps the knowledge base: it SHALL list the `docs/ai-kb/` learning chain and link the `openspec/specs/` capability index. The primary contributor-facing documents (`README.md` and `AGENTS.md`) SHALL each point to that entry map rather than duplicating the listing.

#### Scenario: A new contributor finds the knowledge base

- **WHEN** a new contributor or agent opens `README.md` or `AGENTS.md`
- **THEN** they SHALL find a link to the knowledge-base entry map
- **AND** the entry map SHALL link both the `docs/ai-kb/` learning chain and the `openspec/specs/` index
- **AND** every link in the entry map SHALL resolve to an existing file or spec.

### Requirement: Same-topic docs and specs cross-reference each other

When a `docs/ai-kb/` document and an `openspec/specs/` capability cover the same topic, each SHALL carry a see-also reference to the other so a reader can move between the narrative doc and the normative spec without searching.

#### Scenario: Worldgen doc and spec are linked

- **WHEN** a reader is in `docs/ai-kb/07_neoforge_worldgen.md`
- **THEN** it SHALL link the sibling worldgen spec(s) under `openspec/specs/`
- **AND** that worldgen spec SHALL link back to the ai-kb doc.

### Requirement: Shared rules have a single authoritative location

A rule or convention referenced in more than one document SHALL have exactly one authoritative location; every other mention SHALL reference that location rather than restate the rule, so the statements cannot drift apart.

#### Scenario: The version-bump rule is single-sourced

- **WHEN** the version-bump / changelog-synchronization rule is referenced
- **THEN** its authoritative statement SHALL live in one place (`openspec/config.yaml` `rules.tasks`)
- **AND** other documents (such as `AGENTS.md`) SHALL reference that location instead of repeating the `0.x.y`/`-fix` mechanics.

### Requirement: Scope statements reflect shipped behavior

Documentation that states what the project does or does not include SHALL reflect behavior that has actually shipped. When a capability is released, the same change SHALL update any scope or "not included" statement that the release contradicts.

#### Scenario: A shipped capability is removed from the exclusion list

- **WHEN** a capability previously listed as "not included" has shipped (for example, sect worldgen in v0.11.0)
- **THEN** the scope/"not included" statement SHALL no longer list it as absent
- **AND** any remaining exclusion text SHALL be narrowed to what is still true.

### Requirement: New documentation follows a write-in guideline

When new factual, technical knowledge-base content is added, it SHALL be placed under `docs/ai-kb/`, linked from the knowledge-base entry map, and given a see-also reference to any same-topic spec — all within the change that adds it.

#### Scenario: A new ai-kb document is added

- **WHEN** a contributor adds a new `docs/ai-kb/` document
- **THEN** the knowledge-base entry map SHALL be updated to list it
- **AND** if a same-topic spec exists, the new document and that spec SHALL gain see-also references to each other.

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
