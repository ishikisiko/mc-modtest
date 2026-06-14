# Spec Baseline Governance

## Purpose

These specifications describe the current implementation baseline for a future town-generation project. They are temporary and mutable, not a permanent design freeze. Before changing a boundary, format, generated artifact, or validation rule captured here, contributors should discuss the intended change with the project owner first.

## Requirements

### Requirement: Baseline specs remain changeable
OpenSpec specifications in this repository SHALL be treated as the current agreed baseline, not as immutable architecture.

#### Scenario: A contributor finds a better design
- **WHEN** a contributor identifies a change that would alter a documented boundary, data format, generation behavior, export target, or validation rule
- **THEN** the contributor SHOULD discuss the change with the project owner before implementation
- **AND** the relevant OpenSpec specification SHOULD be updated through the normal spec/change workflow.

### Requirement: Specs describe implemented behavior unless marked otherwise
Main specs under `openspec/specs/` SHALL describe behavior that exists in the repository unless a requirement explicitly marks the behavior as future, placeholder, or not implemented.

#### Scenario: A current limitation is documented
- **WHEN** a capability is intentionally absent or only partially implemented
- **THEN** its spec SHALL state that limitation directly
- **AND** the spec SHALL avoid implying production support for behavior that does not exist.

### Requirement: Project direction remains town generation
The project SHALL treat the current structure library as a step toward multi-category town generation rather than as the final scope of a simple village generator.

#### Scenario: A contributor describes future scope
- **WHEN** project documentation or specs discuss long-term goals
- **THEN** they SHALL allow for multiple settlement categories, varied house types, functional buildings, town pieces, roads, and possible NPC-related systems
- **AND** they SHALL distinguish those goals from currently implemented behavior.

### Requirement: Releases keep version and changelog data synchronized
When a release version or accepted fix version is prepared, contributors SHALL update the mod version, user-facing jar-name examples, and changelog together.

#### Scenario: A large feature addition is prepared for release
- **WHEN** the current version is `0.x.y`
- **THEN** the release version SHALL become `0.(x+1).0`
- **AND** `CHANGELOG.md` SHALL summarize the large addition.

#### Scenario: A small feature addition is prepared for release
- **WHEN** the current version is `0.x.y`
- **THEN** the release version SHALL become `0.x.(y+1)`
- **AND** `CHANGELOG.md` SHALL summarize the small addition.

#### Scenario: A single fix passes validation
- **WHEN** a single fix is accepted after the relevant validation or build step passes
- **THEN** the fix version SHALL keep the base version and add the next ordered suffix such as `-fix1` or `-fix2`
- **AND** `CHANGELOG.md` SHALL identify the fix and validation performed.

#### Scenario: A mod version changes
- **WHEN** `gradle.properties` changes `mod_version`
- **THEN** `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` SHALL be updated in the same change.
