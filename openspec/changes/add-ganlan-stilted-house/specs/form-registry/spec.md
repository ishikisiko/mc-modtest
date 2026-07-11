## ADDED Requirements

### Requirement: Ganlan reference motifs are registry-backed
The Ganlan stilted-house slice SHALL add reusable form hooks for raised-floor
support posts, veranda-edge rails, and deep rain-shelter eaves through
registry-backed or metadata-driven form vocabulary. The implementation SHALL NOT
add ad hoc style-prefix branching to place those cues.

#### Scenario: Support post cue dispatches through form vocabulary
- **WHEN** a Ganlan sample requests the stilt support cue
- **THEN** the generator SHALL dispatch through a registered form, motif hook,
  or explicit metadata-driven operation
- **AND** the form SHALL use the active style's material slots.

#### Scenario: Veranda and eave cues do not branch on style prefix
- **WHEN** a Ganlan sample places raised veranda rails or deep eaves
- **THEN** the generator SHALL use the requested cue/form from the sample layout
  or metadata
- **AND** it SHALL NOT infer those cues by matching a style-name prefix.
