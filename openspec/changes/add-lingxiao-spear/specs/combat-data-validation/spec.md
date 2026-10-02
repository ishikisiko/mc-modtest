## MODIFIED Requirements

### Requirement: Validator cross-checks data against resources
For every move of every style the validator SHALL require a player animation with that id whose length is `total_ticks / 20` seconds, a translation for the display key, and sound events that exist in `sounds.json`. For every weapon it SHALL require the item to be registered, its item model and every model it wraps or draws in the hand to exist with their textures, the in-hand model to be the 3D model its geometry contract names with elements and first- and third-person hand transforms, the first-person rig to define every move of the weapon's style with a `strike` window that covers the move's active ticks within three ticks, and the geometry contract to carry its required fields. It SHALL check an `off_hand_grip_center` (on the axis, on the handle, ahead of the grip by at least one fist width at the model's third-person scale), a `trail` span (on the axis, base below tip, on the weapon), a rig's `rig.off_hand` block and off-hand pose fields (numbers, `off_hand_hold` within 0 to 1, every slide keeping the hand on the handle, and a contract with `off_hand_grip_center`), run the model generator that each contract's `generator` field names with `--check` (the Qingfeng model generator always), and, when a current jar exists, require every weapon's data, rig, contract, models, and textures in it.

#### Scenario: Strike window misses the active ticks
- **WHEN** a rig move's `strike` window ends before the style's active end tick
- **THEN** the validator reports the move and both windows

#### Scenario: Missing animation is reported
- **WHEN** a style declares a move with no matching player animation
- **THEN** the validator reports the move id

#### Scenario: Off-hand rig on a one-handed weapon is reported
- **WHEN** a rig declares `rig.off_hand` and its weapon's contract has no `off_hand_grip_center`
- **THEN** the validator reports the weapon, the rig, and the contract

#### Scenario: Second weapon's model drift is reported
- **WHEN** a generated file of the spear model differs from its generator's output
- **THEN** the validator reports a model generator drift for that generator

### Requirement: Validator holds no per-move literals and only stable source checks
The validator MUST NOT embed expected timing, damage, or step numbers for any move. Its Java source checks SHALL be limited to invariants that do not depend on class or method names inside the combat packages: Player Animation Library and client imports only under `client/combat`, empty client-to-server combat payload records, no vanilla player `attack(` call in combat code, a clientbound-only impact payload without damage or health, no reference to `ModItems` in combat code, and no game-clock read (`getGameTime()` outside comments) in `client/combat` except in the one client combat clock class.

#### Scenario: Retuning a move does not require a validator edit
- **WHEN** a move's `total_ticks` changes consistently in the style file, the rig, and the regenerated animation
- **THEN** the validator passes without modification

#### Scenario: A second game-clock reader is reported
- **WHEN** a class in `client/combat` other than the client combat clock calls `getGameTime()`
- **THEN** the validator reports `COMBAT_CLIENT_GAME_CLOCK_READ` with the file

## ADDED Requirements

### Requirement: Hit sample rules
The shared reader and the validator SHALL report explicit hit samples whose ticks go backwards (`COMBAT_DATA_SAMPLE_ORDER`) and, once any active tick of a move carries several samples, active ticks with different sample counts (`COMBAT_DATA_SAMPLE_COUNT`). For every weapon and every cut of its style, the validator SHALL require each sample's far end, and the interpolated far end at every drawn world-trail frame from half a tick before the active window to half a tick after it, to lie at least the weapon's trail tip radius from the trail pivot (`COMBAT_TRAIL_CUT_REACH`), with the radius computed per weapon as the world trail computes it.

#### Scenario: Uneven samples per tick are reported
- **WHEN** a cut has three samples on its first active tick and two on its second
- **THEN** the validator reports `COMBAT_DATA_SAMPLE_COUNT` for the move

#### Scenario: Short far end bends the trail
- **WHEN** a spear cut's sample ends 2.4 blocks from the trail pivot while the spear's tip radius is about 2.62
- **THEN** the validator reports `COMBAT_TRAIL_CUT_REACH` with the weapon, the move, the sample, and the radius

### Requirement: One pose table and output per style
`tools/gen_sword_pal_anims.py` SHALL hold one pose table per combat style, each with its own geometry contract, 3D item model, pose rules, cut paths, and output file, and SHALL measure grip compensation, forward kinematics, reach, and cut paths with that table's weapon. `--check` and `--report` SHALL cover every table. A table marked two-handed SHALL solve the left arm onto the shaft for every on-shaft key and check the left fist's offset from the shaft at keys and between keys; a table with long-weapon rules SHALL check the ground clearance of the weapon model's lowest element corner, the separation of the two hands, and the shaft's clearance from torso, head and neck, legs, and arms, each body box including its skin's outer layer, at keys and between keys of every animation.

#### Scenario: Adding a table leaves the sword file unchanged
- **WHEN** the spear table is added and the generator runs
- **THEN** `sword_combat.json` is byte-identical and `spear_combat.json` is written separately

#### Scenario: Off-hand request on a one-handed table fails
- **WHEN** a table that is not two-handed puts the left hand on the shaft
- **THEN** the generator fails and names the style

### Requirement: Standing-target coverage
A Python test SHALL expand every move's hit samples (generators included) and require that a standing mob-sized target straight ahead, with its near face anywhere from 1.0 block out to the move's `range`, is touched by at least one sample, and that the samples reach the move's `range`.

#### Scenario: Gap in a move's reach fails the test
- **WHEN** a move's samples leave a distance between 1.0 block and its range where no sample touches a standing target
- **THEN** the test fails and lists the move and the distances
