# Modset Profile

## Purpose

This spec captures the shared modset profile resolver used by generators and validators to decide which namespaces and external-mod block ids are legal.

## Requirements

### Requirement: A modset profile resolves namespaces and legal mod ids from the catalog
A modset profile SHALL map a profile name to the set of active namespaces and the set of legal external-mod block ids, both derived from `exmod/mod_block_catalog.json`. The `vanilla` profile SHALL resolve to the single `minecraft` namespace and an empty mod-id set. The `full` profile SHALL resolve to `minecraft` plus the catalog's confirmed mod namespaces, and to the set of every block id the catalog lists under those confirmed namespaces.

#### Scenario: The vanilla profile permits no mod ids
- **WHEN** the `vanilla` profile is resolved
- **THEN** its active namespaces SHALL be exactly `{minecraft}`
- **AND** its legal mod-id set SHALL be empty.

#### Scenario: The full profile permits confirmed catalog ids
- **WHEN** the `full` profile is resolved
- **THEN** its active namespaces SHALL include the catalog's confirmed mod namespaces
- **AND** its legal mod-id set SHALL contain exactly the ids the catalog lists for those namespaces.

#### Scenario: An unknown profile name is rejected
- **WHEN** a profile name other than `vanilla` or `full` is resolved
- **THEN** resolution SHALL fail with an error naming the unknown profile.

### Requirement: A modset profile classifies palette block ids
A modset profile SHALL classify a palette's block ids relative to itself, reporting two distinct failures for non-`minecraft` ids: an id whose namespace is not in the profile's active namespaces SHALL be reported as `forbidden_mod_blocks`, and an id whose namespace is active but which is absent from the legal mod-id set SHALL be reported as `unknown_mod_blocks`. A `minecraft` id SHALL never be reported by the profile classifier.

#### Scenario: A vanilla-namespace id is ignored by the classifier
- **WHEN** the classifier inspects a `minecraft:` block id under any profile
- **THEN** it SHALL NOT report that id, leaving vanilla-id checking to the registry-based validators.

#### Scenario: A forbidden namespace is reported
- **WHEN** the classifier inspects a non-`minecraft` id whose namespace is not active in the profile
- **THEN** it SHALL report the id under `forbidden_mod_blocks`.

#### Scenario: An active-namespace id missing from the catalog is reported
- **WHEN** the classifier inspects a non-`minecraft` id whose namespace is active but which is not in the legal mod-id set
- **THEN** it SHALL report the id under `unknown_mod_blocks`.
