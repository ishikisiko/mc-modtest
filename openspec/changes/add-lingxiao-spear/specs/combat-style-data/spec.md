## MODIFIED Requirements

### Requirement: Client presentation follows the weapon entry
The client SHALL load the first-person rig and geometry named by each weapon entry on every resource reload and SHALL apply the first-person animator, arm, and trails to any registered weapon. A weapon whose rig or geometry is missing or invalid SHALL fall back to the vanilla hold without affecting other weapons. The first-person trail SHALL span the geometry contract's `trail` (else the blade), and the world trail SHALL be drawn with a tip radius of the shoulder-to-grip reach plus the contract's grip-to-trail-tip distance and a length of the `trail` span, both at the item model's third-person scale, with hit samples that share a server tick spread evenly through that tick.

#### Scenario: Invalid rig affects only its weapon
- **WHEN** one weapon's rig file fails validation on reload
- **THEN** that weapon uses the vanilla hold and the error names the rig, while other weapons keep their rigs

#### Scenario: A long weapon's world trail is not capped at sword length
- **WHEN** a remote player performs a spear move whose hit samples reach beyond 1.7 blocks from the trail pivot
- **THEN** the world trail's head is drawn at the spear's own tip radius, not at the sword's 1.7 blocks

#### Scenario: Sword trail is unchanged
- **WHEN** the Qingfeng contract, which has no `trail` field, sizes the world trail
- **THEN** every drawn frame of every Qingfeng move equals the 0.28.0 trail

## ADDED Requirements

### Requirement: Geometry contract optional fields
A geometry contract MAY give `off_hand_grip_center`, which SHALL lie on the weapon axis inside the handle and ahead of `grip_center`, and MAY give `trail` as `{"base": [x, y, z], "tip": [x, y, z]}` on the weapon axis with the base below the tip, within the weapon from the pommel's bottom to the blade tip. A contract without `off_hand_grip_center` describes a one-handed weapon. A contract that breaks these rules SHALL fail to load and leave only its weapon on the vanilla hold.

#### Scenario: Off-hand point behind the main grip is rejected
- **WHEN** a contract's `off_hand_grip_center` lies below its `grip_center`
- **THEN** the geometry fails to load and its weapon uses the vanilla hold

### Requirement: Optional first-person off-hand arm
A first-person rig MAY declare `rig.off_hand` (`shoulder_offset`, `grip_diagonal` within 0 to 50). With it, and only while the player's off-hand slot is empty, the arm renderer SHALL also draw the off arm with its hand solved onto the shaft at the contract's `off_hand_grip_center` plus the pose's `off_hand_slide`, turned by `off_hand_roll`, its elbow swivelled by `off_hand_elbow`, and moved toward a rest beside the body as `off_hand_hold` falls from 1 to 0 (not drawn at 0). The four pose fields SHALL interpolate with the pose and default to 0, 0, 0, and 1. A rig with the block on a weapon whose contract has no `off_hand_grip_center`, or whose slide puts the hand off the handle, SHALL fail to load. A rig without the block SHALL render as before.

#### Scenario: Rig without the block is unchanged
- **WHEN** the Qingfeng rig, which has no `rig.off_hand`, is loaded
- **THEN** only the main arm is drawn and every pose equals the pose before this change

#### Scenario: Off-hand item suppresses the off arm
- **WHEN** a player holds a weapon whose rig declares `rig.off_hand` and has an item in the off hand
- **THEN** the off arm is not drawn and the off-hand item keeps its vanilla hand pass

### Requirement: Combat weapons are CombatWeaponItem
Every item that has a combat weapon entry SHALL be registered as `CombatWeaponItem`, which SHALL replay the equip animation only when the slot changes or the item differs, never when only the stack's data changes.

#### Scenario: Durability loss does not re-equip
- **WHEN** a landed combat hit costs the held weapon durability and the server resends the stack
- **THEN** the first-person weapon keeps its place on screen instead of dropping and climbing back

### Requirement: First-person hit-stop never moves the pose backwards
The first-person swing SHALL be drawn from the latest action time any reader has seen, never from an earlier reading. A hit confirmation SHALL start the hit-stop at the rig's contact tick when the drawn swing has not reached it, and otherwise at that latest time, so the frozen pose is the one on screen.

#### Scenario: Late confirmation freezes the pose on screen
- **WHEN** the hit confirmation for a cut arrives after the swing has passed the rig's contact tick
- **THEN** the hit-stop holds the pose already drawn and the weapon does not step back along the move
