# Vertical Landmark

## Purpose

This spec captures the tall cultivation archetypes (`pagoda`, `pavilion`, `bell_drum_tower`) registered as building forms, and the skyline relief rule that requires the civic-core district to contain a configured minimum of above-threshold-height volumes.

## Requirements

### Requirement: Tall cultivation archetypes are registered as forms
The build system SHALL provide `pagoda`, `pavilion`, and `bell_drum_tower` archetypes composed from the existing terraced-massing and `tiered_eave_roof` form vocabulary, registered through the form registry and listed in the cultivation town style's allowed roof types and motifs. These archetypes SHALL NOT be dispatched by matching `style_id` prefixes or archetype-name strings in passes.

#### Scenario: A vertical archetype generates through the registry
- **WHEN** the generator builds a `pagoda`, `pavilion`, or `bell_drum_tower`
- **THEN** the form SHALL resolve through the registered form vocabulary
- **AND** its roof and motif forms SHALL be drawn from the style's allowed roof types and motifs
- **AND** generation SHALL succeed under both `--profile vanilla` and `--profile full`.

### Requirement: The civic core guarantees vertical relief
The plan SHALL require the `civic_core` district to contain at least a configured minimum number of volumes above a configured height threshold, so the core silhouette rises above the surrounding rooflines.

#### Scenario: The core carries tall volumes
- **WHEN** a districted cultivation town plan is produced
- **THEN** the `civic_core` district SHALL contain at least the configured minimum of above-threshold-height volumes
- **AND** at least one of those volumes SHALL be a `pagoda`, `pavilion`, or `bell_drum_tower`.

### Requirement: A skyline validator enforces the relief rule
The town validator SHALL fail a plan whose civic core lacks the required tall volumes, reporting a skyline invariant.

#### Scenario: A flat core fails the skyline check
- **WHEN** the validator runs on a town whose civic core has no above-threshold volumes
- **THEN** it SHALL fail and report a skyline invariant
- **AND** it SHALL pass when the core meets the configured tall-volume minimum.
