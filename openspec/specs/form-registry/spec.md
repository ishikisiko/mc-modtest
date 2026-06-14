# Form Registry

## Purpose

This spec captures the registry-based dispatch mechanism for generated roof and decoration forms.

## Requirements

### Requirement: Roof types are dispatched through a registry
The generator SHALL dispatch roof construction through a name-to-handler registry keyed by roof type name. Adding a new roof type SHALL require registering a handler and SHALL NOT require modifying existing dispatch branches.

#### Scenario: A registered roof type is built
- **WHEN** a volume's roof metadata names a roof type present in the registry
- **THEN** the generator SHALL invoke the registered handler for that name
- **AND** it SHALL pass the grid, active style, rng, and volume node to the handler.

#### Scenario: An unregistered roof type is requested
- **WHEN** a volume names a roof type that is not in the registry
- **THEN** the generator SHALL raise a clear error identifying the missing roof type
- **AND** it SHALL NOT silently fall through to a default roof.

### Requirement: Decoration motifs are dispatched through a registry
The generator SHALL dispatch decoration motif placement through a name-to-handler registry keyed by motif name, replacing hardcoded motif branching. Adding a new motif SHALL require registering a handler only.

#### Scenario: A registered motif is placed
- **WHEN** a decoration node names a motif present in the registry
- **THEN** the generator SHALL invoke the registered motif handler
- **AND** the handler SHALL place the motif using the active style's material slots.

### Requirement: Existing forms migrate without behavior change
The roof and motif registries SHALL include the existing roof types (`gable_roof`, `cross_gable_roof`, `lean_to_roof`) and all existing motifs with behavior identical to the pre-registry implementation.

#### Scenario: Legacy libraries are regenerated after migration
- **WHEN** the `medieval_village` and `chinese_courtyard` libraries are regenerated after the registry migration
- **THEN** the generated structures SHALL be byte-identical to the pre-migration output, or any difference SHALL be explicitly reviewed and accepted.

### Requirement: Style vocabularies are validated against the registry
A style profile's `allowed_roof_types` and `allowed_motifs` SHALL reference names that exist in the corresponding registry.

#### Scenario: A style references an unknown form name
- **WHEN** a style profile lists a roof type or motif with no registered handler
- **THEN** validation SHALL report the unknown form name as an error.
