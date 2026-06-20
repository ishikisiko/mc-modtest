## ADDED Requirements

### Requirement: A region profile defines a region's identity, tier, environment, role, and admitted subjects

The region-profile data model SHALL describe each region (洲) with a stable id, a display name, an integer tier within a configured range, a qi range, a danger range, a placement role drawn from `anchor` / `peripheral` / `walled`, and the set of worldgen subjects the region admits. The tier SHALL be the ordinal the topology layer uses for its tier-step rule; qi and danger SHALL be ranges the region exposes to downstream consumers.

#### Scenario: A region profile exposes its fields

- **WHEN** the topology tooling loads a region profile
- **THEN** it SHALL expose the region's id, display name, tier, qi range, danger range, placement role, and admitted-subjects set.

#### Scenario: Placement role is one of the defined roles

- **WHEN** a region profile declares a placement role
- **THEN** the role SHALL be exactly one of `anchor`, `peripheral`, or `walled`
- **AND** any other value SHALL be rejected.

#### Scenario: Admitted subjects name known worldgen subjects

- **WHEN** a region profile lists admitted worldgen subjects
- **THEN** each entry SHALL name a worldgen subject known to the mod (at present, the sect)
- **AND** an unknown subject id SHALL be rejected.

### Requirement: Region profiles are JSON resources under the mod worldgen data tree

Region profiles and the topology ruleset SHALL be stored as JSON under `src/main/resources/data/myvillage/worldgen/`, so the same files drive the offline generator and validator now and can be read by a runtime placement-director later without a second source of truth. The model SHALL NOT be expressed only as in-code tables.

#### Scenario: Region data ships in the resource tree

- **WHEN** the mod resources are built
- **THEN** the authored region profiles and topology ruleset SHALL be present as JSON under `data/myvillage/worldgen/`
- **AND** the offline generator and validator SHALL read those same files.
