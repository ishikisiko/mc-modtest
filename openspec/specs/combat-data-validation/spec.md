# combat-data-validation Specification

## Purpose
Define what the focused combat validator, the animation generator, and the regression tests must prove about the combat data files and their consistency with other resources. See-also `docs/ai-kb/34_combat_data_and_capture.md`.

## Requirements
### Requirement: Validator checks combat data files
`tools/validate_sword_combat_foundation.py` SHALL validate every style and weapon file listed in the combat index against the version 1 schema and the move invariants, SHALL reject unknown fields, and SHALL report a file that exists in the style or weapon directory but is missing from the index, or the reverse.

#### Scenario: Misspelled field is reported
- **WHEN** a style file contains `chain_tik` instead of `chain_tick`
- **THEN** the validator reports a finding that names the file and the field

#### Scenario: Unlisted file is reported
- **WHEN** a style file exists on disk but the index does not list it
- **THEN** the validator reports the mismatch

### Requirement: Validator cross-checks data against resources
For every move of every style the validator SHALL require a player animation with that id whose length is `total_ticks / 20` seconds, a translation for the display key, and sound events that exist in `sounds.json`. For every weapon it SHALL require the item to be registered with a model, the first-person rig to define every move of the weapon's style with a `strike` window that covers the move's active ticks within three ticks, and the geometry contract to carry its required fields.

#### Scenario: Strike window misses the active ticks
- **WHEN** a rig move's `strike` window ends before the style's active end tick
- **THEN** the validator reports the move and both windows

#### Scenario: Missing animation is reported
- **WHEN** a style declares a move with no matching player animation
- **THEN** the validator reports the move id

### Requirement: Validator holds no per-move literals and only stable source checks
The validator MUST NOT embed expected timing, damage, or step numbers for any move. Its Java source checks SHALL be limited to invariants that do not depend on class or method names inside the combat packages: Player Animation Library and client imports only under `client/combat`, empty client-to-server combat payload records, no vanilla player `attack(` call in combat code, and a clientbound-only impact payload without damage or health.

#### Scenario: Retuning a move does not require a validator edit
- **WHEN** a move's `total_ticks` changes consistently in the style file, the rig, and the regenerated animation
- **THEN** the validator passes without modification

### Requirement: Accepted Qingfeng values are pinned by one regression test
One Python regression test SHALL pin the accepted `myvillage:basic_sword` timing, damage, chain, and step values so an unintended retune fails a test, and the generator tests SHALL prove the generated animation is byte-identical to the committed file.

#### Scenario: Accidental retune fails the regression test
- **WHEN** `basic_sword.json` changes a pinned value without the test being updated
- **THEN** the regression test fails and names the move and field

### Requirement: Generator takes timing from the style file
`tools/gen_sword_pal_anims.py` SHALL read each move's total, active window, chain tick, step tick, and kind from its style file and MUST NOT repeat them next to the pose table. It SHALL fail when a pose table names a move the style does not define or omits one it does.

#### Scenario: Pose table and style disagree on moves
- **WHEN** the style defines a move that has no pose table
- **THEN** the generator exits with an error naming the move

