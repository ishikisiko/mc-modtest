## ADDED Requirements

### Requirement: Hui-style reference motifs are registry-backed
The Hui-style reference slice SHALL add reusable form hooks for the stepped
马头墙 cue and the closed-facade-with-entry cue through the registry-based form
vocabulary. The implementation SHALL NOT add ad hoc style-prefix branching to
place those cues.

#### Scenario: Stepped gable cue dispatches through registry vocabulary
- **WHEN** a Hui-style sample requests the stepped 马头墙 cue
- **THEN** the generator SHALL dispatch through a registered form or motif hook
- **AND** the form SHALL use the active style's material slots.

#### Scenario: Closed facade cue does not branch on style prefix
- **WHEN** a Hui-style sample places the street-facing closed facade
- **THEN** the generator SHALL use the requested cue/form from the sample
  layout or metadata
- **AND** it SHALL NOT infer closed-facade behavior by matching a style-name
  prefix.
