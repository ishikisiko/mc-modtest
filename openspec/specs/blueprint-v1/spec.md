# Blueprint V1

## Purpose

This spec captures the current simple blueprint validator baseline. It is temporary and mutable; proposed changes to blueprint semantics or conversion should be discussed with the project owner first.

See also (narrative): [docs/ai-kb/02_blueprint_schema.md](../../../docs/ai-kb/02_blueprint_schema.md).

## Requirements

### Requirement: Blueprint v1 is a simple coordinate-block schema
Blueprint v1 SHALL be a JSON schema with `schema_version: 1`, namespaced `id`, positive `size`, local `palette`, and a `blocks` array.

#### Scenario: A valid minimal blueprint is checked
- **WHEN** a blueprint contains `schema_version: 1`, a namespaced id, `size: [x, y, z]` with positive integers, a palette object, and block entries inside bounds
- **THEN** blueprint validation SHALL succeed unless a block entry violates a block-specific rule.

### Requirement: Blueprint block entries use palette or direct block ids
Each blueprint block entry SHALL define `pos` as three integers and SHALL define either `palette` or `block`.

#### Scenario: A block entry has neither palette nor block
- **WHEN** blueprint validation checks the entry
- **THEN** validation SHALL report that the entry must define `palette` or `block`.

### Requirement: Blueprint state values are string-valued properties
Blueprint block `state`, when present, SHALL be an object whose values are strings.

#### Scenario: A state value is boolean
- **WHEN** blueprint validation sees a state property value of `false`
- **THEN** validation SHALL fail because state values must be strings.

### Requirement: Blueprint export is not currently implemented
Blueprint v1 SHALL be documented as validated-only in the current implementation. The placeholder `tools/export_nbt.py` and `tools/export_schem.py` SHALL NOT be treated as working blueprint exporters.

#### Scenario: A user invokes a placeholder blueprint exporter
- **WHEN** the user runs `tools/export_nbt.py` or `tools/export_schem.py`
- **THEN** the tool SHALL exit with a not-implemented message
- **AND** no successful blueprint export SHALL be implied.
