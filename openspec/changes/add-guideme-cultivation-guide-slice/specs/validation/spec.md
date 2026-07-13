## ADDED Requirements

### Requirement: Focused GuideME validation covers dependency source and guide integrity
The repository SHALL provide `tools/validate_guideme_cultivation_guide.py` and `tools/tests/test_validate_guideme_cultivation_guide.py`. The validator SHALL check Maven Central API/runtime coordinates, required `BOTH`-side NeoForge metadata, absence of local-jar build wiring, guide-definition JSON including the absence of first-slice `custom_colors`, exact bilingual topology under authoritative root `guidebook/`, the one-way `processResources` output mapping and absence of a checked-in resource mirror, frontmatter/navigation/internal links, shipped item/block and key-binding references, current cultivation factual anchors and deferred-system exclusions, handbook registration/model/language resources, root-source `runGuide` configuration with guide-id startup display and validation, README command order/release synchronization, and practical-jar entries. Negative fixtures SHALL produce specific nonzero failures for representative dependency, page-pair, reference, content, model, preview, command, and packaging drift.

#### Scenario: The focused validator passes
- **WHEN** every declared dependency, guide, content, handbook, preview, documentation, release, and packaging invariant is present
- **THEN** `python3 tools/validate_guideme_cultivation_guide.py` SHALL exit `0`

#### Scenario: A local jar becomes the Gradle dependency source
- **WHEN** a fixture replaces the Maven-resolved GuideME coordinates with `files(...)`, `flatDir`, or other root-jar wiring
- **THEN** the validator SHALL fail with a dependency-source diagnostic

#### Scenario: A translated page or live key-binding component drifts
- **WHEN** a fixture removes one paired page, breaks a page link, references an unknown item id, or replaces a required key-binding component with a fixed-letter instruction
- **THEN** the validator SHALL fail with the affected path or reference

#### Scenario: Handbook packaging drifts
- **WHEN** a fixture removes the registered item/model/language surface, changes the model away from `guideme:item/guide_base`, adds a MyVillage handbook texture, or omits a declared jar entry
- **THEN** the validator SHALL fail with the affected handbook or packaging invariant

### Requirement: GuideME closeout separates automated compatibility from client observation
Closeout SHALL run strict change and baseline spec validation, the focused validator and its tests, existing relevant cultivation and mod-item validators, Gradle tests, the practical jar build and inspection, a bounded `runGuide` client startup smoke, and a bounded dedicated/acceptance-server startup with GuideME present. Automated evidence SHALL establish dependency resolution, compilation, data/resource integrity, packaging, registration, and side-safe startup only; it SHALL NOT substitute for observed guide rendering or interaction.

#### Scenario: Client and dedicated-server smokes start cleanly
- **WHEN** the bounded startup runs load MyVillage with the Maven-resolved GuideME runtime
- **THEN** logs SHALL contain no missing required dependency, guide parse, registry, payload, or client-class-on-server failure
- **AND** each process SHALL be stopped cleanly after the bounded compatibility window

#### Scenario: Automated gates pass without a real guide review
- **WHEN** all source, test, build, jar, and startup checks pass but no reviewer interacts with the guide
- **THEN** compatibility and packaging MAY pass
- **AND** language rendering, navigation, search, item indexing, model rendering, live reload, and handbook interaction SHALL remain `not_verified`

### Requirement: Real-client guide acceptance uses pass fail or not_verified
Manual acceptance SHALL record only `pass`, `fail`, or `not_verified` for guide discovery, Chinese-default fallback, English switching, three-page navigation, search results for the released loop, both stele item-index jumps, spirit-stone item-index jumps, representative `ItemLink`/`BlockImage` rendering, live configured key displays, root-source live reload, custom-handbook opening/reopening, and existing cultivation-screen/gameplay regression. No source validator, jar listing, screenshot-free client startup, or dedicated-server startup SHALL infer a pass for an unobserved item.

#### Scenario: A guide interaction was not directly observed
- **WHEN** closeout has no real-client evidence for that interaction
- **THEN** the ledger SHALL record it as `not_verified`
- **AND** it SHALL NOT inherit a pass from an automated gate

#### Scenario: A configurable binding is reviewed
- **WHEN** the reviewer remaps one MyVillage cultivation action and reopens the relevant page
- **THEN** the item SHALL pass only if GuideME displays the remapped binding rather than the original default letter

#### Scenario: The handbook is reviewed after page navigation
- **WHEN** the reviewer opens the handbook, navigates away from the index, closes it, and uses it again
- **THEN** the item SHALL pass only if the first use opens the correct guide and the second use follows GuideME's remembered-page behavior
