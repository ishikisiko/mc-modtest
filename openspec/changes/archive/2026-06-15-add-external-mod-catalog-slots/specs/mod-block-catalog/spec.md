## ADDED Requirements

### Requirement: A reproducible tool extracts a mod block catalog
The project SHALL provide `tools/extract_mod_catalog.py` that reads external mod assets from `exmod/mod_assets.zip` (each mod's `assets/<modid>/blockstates/*.json`) and emits `exmod/mod_block_catalog.json`. The tool SHALL be re-runnable: running it against the same inputs SHALL produce an equivalent catalog, and it SHALL NOT require any external mod class to be importable.

#### Scenario: Generating the catalog from staged assets
- **WHEN** `tools/extract_mod_catalog.py` runs against `exmod/mod_assets.zip`
- **THEN** it SHALL write `exmod/mod_block_catalog.json`
- **AND** it SHALL read only blockstate/asset JSON, importing no mod Java or Python classes
- **AND** re-running it on unchanged inputs SHALL produce an equivalent catalog.

#### Scenario: A staged asset zip is missing
- **WHEN** the tool runs and `exmod/mod_assets.zip` is absent
- **THEN** the tool SHALL exit with a clear error naming the missing input
- **AND** it SHALL NOT write a partial catalog.

### Requirement: The catalog records block id, blockstate grammar, and textures per mod
`exmod/mod_block_catalog.json` SHALL map each mod namespace to a list of its blocks, where each block entry records its full block id (`<modid>:<block>`), the blockstate property names and value domains declared in its blockstate JSON, and the texture names it references. This gives downstream phases a machine-readable source of truth for orientation grammar and slot population.

#### Scenario: A roof block entry carries its blockstate grammar
- **WHEN** a mod block declares blockstate properties such as `facing`, `half`, or a custom `variant`
- **THEN** its catalog entry SHALL list those property names and their value domains
- **AND** its catalog entry SHALL list the texture names the block references.

#### Scenario: The catalog is grouped by mod namespace
- **WHEN** the catalog contains blocks from multiple mods
- **THEN** each block entry SHALL be reachable under its owning mod namespace key
- **AND** every block id SHALL be prefixed with that namespace.

### Requirement: The catalog merges design intent and a confirmed mod set
The catalog SHALL incorporate the design-intent (落点) notes derived from `exmod/deep-research-report.md`, associating mod block families with their intended aesthetic role (e.g. tiled roof, paper lantern, ritual anchor, market fitting). The confirmed external mod set SHALL be recorded so downstream phases reference a single list rather than re-deriving it.

#### Scenario: A block family carries its intended role
- **WHEN** the report assigns a mod block family an aesthetic role
- **THEN** the catalog SHALL associate that family with the role label
- **AND** the role labels SHALL align with the new semantic slot names (`ROOF_TILE`, `PAPER_LANTERN`, `RITUAL_ANCHOR`, `MARKET_FITTINGS`).

#### Scenario: The confirmed mod set is recorded
- **WHEN** the catalog is finalized
- **THEN** it SHALL record the confirmed external mod set as a list of mod namespaces
- **AND** that list SHALL be the source downstream phases consult for active namespaces.
