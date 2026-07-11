## ADDED Requirements

### Requirement: Flying sword item is complete and discoverable
The mod SHALL register `myvillage:rideable_flying_sword` as a non-consumable functional item, expose it in `myvillage:main`, and provide English and Chinese names, an item model, and a referenced placeholder item texture.

#### Scenario: Player obtains the item
- **WHEN** a player opens the MyVillage creative tab or runs `/give @s myvillage:rideable_flying_sword`
- **THEN** the flying sword item is available with a localized name and a resolved item model and texture

### Requirement: Item use toggles one owner-bound sword
The server SHALL create a flying-sword entity at the first collision-free underfoot or slightly raised placement for a player who uses the item without an existing owned sword and SHALL mount that player automatically. A player MUST NOT own more than one rideable flying sword; if an owned sword already exists, item use SHALL recall and remove it instead of creating another one. If the bounded placement search finds no block-clear position, the use SHALL fail without leaving a sword or binding.

#### Scenario: First use creates and mounts
- **WHEN** a server player with no owned flying sword uses the item
- **THEN** exactly one `myvillage:rideable_flying_sword` entity is added below that player and the player becomes its sole passenger

#### Scenario: Blocked placement leaves no sword
- **WHEN** every bounded underfoot placement intersects a block collision shape
- **THEN** item use fails without adding a flying sword or retaining an owner binding

#### Scenario: Reuse recalls instead of duplicating
- **WHEN** a server player who already owns a flying sword uses the item again
- **THEN** the old sword is removed, its binding is cleared, and no replacement sword is created by that use

#### Scenario: A second passenger is rejected
- **WHEN** another entity attempts to mount an owner-bound flying sword
- **THEN** the flying sword keeps at most its bound player as one passenger

### Requirement: Client input is bounded and server-authoritative
The client SHALL send only forward, backward, left, right, ascend, and descend key state. The payload MUST NOT contain coordinates, rotation, velocity, acceleration, speed, owner identity, or target entity identity. The server SHALL derive the target sword from the sender's current vehicle, reject unknown input bits or unauthorized senders, and exclusively compute movement and orientation.

#### Scenario: Valid mounted-owner input is accepted
- **WHEN** a living server player sends a valid key bitset while riding the same-level sword bound to that player
- **THEN** the server records the bounded input for that sword and refreshes its input timeout

#### Scenario: Unauthorized input is ignored
- **WHEN** a sender is not the sword's owner and current passenger, is in another level, or sends unknown input bits
- **THEN** the server does not alter the sword's accepted input state or movement

#### Scenario: Payload cannot prescribe motion
- **WHEN** the payload codec and handler are inspected
- **THEN** no client-provided position, yaw, pitch, velocity, acceleration, speed, owner id, or entity id can reach the movement calculation

#### Scenario: Stale input becomes neutral
- **WHEN** the server receives no fresh valid input within the configured timeout
- **THEN** the sword processes zero input rather than continuing the last command indefinitely

### Requirement: Server flight follows the requested controls
The server SHALL map W/S to forward/backward movement, A/D to left/right movement relative to the owner's horizontal view direction, Space to ascent, and Shift to descent. It SHALL normalize diagonal horizontal input, enforce fixed speed limits, disable gravity, keep pitch at zero, follow the owner's horizontal yaw, and apply drag on axes without input so the sword hovers and gradually slows.

#### Scenario: Horizontal controls follow view yaw
- **WHEN** the mounted owner holds W, S, A, or D while looking in a horizontal direction
- **THEN** the server accelerates the sword in the corresponding forward, backward, left, or right direction relative to that yaw

#### Scenario: Vertical controls move without gravity
- **WHEN** the mounted owner holds Space or Shift
- **THEN** the server moves the sword upward or downward within the vertical speed limit without applying gravity

#### Scenario: Neutral input hovers and decelerates
- **WHEN** no movement key is active or accepted input expires
- **THEN** horizontal and vertical velocity approach zero under server-applied drag rather than the sword falling or continuing indefinitely

#### Scenario: Orientation remains horizontal
- **WHEN** the owner changes view yaw or pitch while mounted
- **THEN** the sword follows the owner's horizontal yaw while keeping its own pitch at zero

### Requirement: Flight respects collision and fall safety
The flying sword SHALL retain normal block collision, and the server SHALL reset the mounted player's fall distance while riding.

#### Scenario: Sword meets a solid block
- **WHEN** server-computed movement intersects a solid block collision shape
- **THEN** normal entity collision resolution prevents the sword from moving through the block

#### Scenario: Rider accumulates fall distance
- **WHEN** a player remains mounted during ascent, descent, or horizontal flight
- **THEN** the server clears that player's fall distance each tick

### Requirement: Owner lifecycle removes transient swords
The flying sword SHALL be transient and SHALL automatically disappear when its owner dies, logs out, changes dimension, or exceeds the configured owner distance. Player/entity bindings SHALL be cleared when recalled or found stale, and the sword SHALL NOT persist across world saves or transfer across dimensions.

#### Scenario: Owner dies or logs out
- **WHEN** the bound owner dies or leaves the server
- **THEN** the owned flying sword is discarded and its binding is cleared

#### Scenario: Owner changes dimension
- **WHEN** the bound owner changes dimension
- **THEN** the old-dimension flying sword is discarded rather than transferred or retained

#### Scenario: Owner moves too far away
- **WHEN** the bound owner is no longer riding and exceeds the configured maximum distance from the sword
- **THEN** the server discards the sword

#### Scenario: World saves
- **WHEN** the level saves or an entity chunk unloads
- **THEN** the flying sword is not serialized as a persistent vehicle

### Requirement: Item-model renderer remains client-only
The client SHALL render the flying-sword entity horizontally through the registered flying-sword item model using vanilla item rendering. GeckoLib and custom animation files SHALL NOT be introduced, and common code MUST NOT import or resolve client-only classes.

#### Scenario: Client renders the vehicle
- **WHEN** a client tracks `myvillage:rideable_flying_sword`
- **THEN** the dedicated renderer displays the flying-sword item model horizontally at the entity position and yaw

#### Scenario: Dedicated server starts
- **WHEN** the dedicated acceptance server loads the mod and registers the item, entity, payload, and lifecycle listeners
- **THEN** startup completes without loading `net.minecraft.client` classes from common code

### Requirement: Automated gates and manual gameplay review remain distinct
The implementation SHALL schema-check its contracts, run focused validation, `./gradlew test`, `./gradlew build`, and `./gradlew runAcceptanceServer`. Descent-without-dismount, all movement directions, collision, latency, multiplayer authority, cleanup paths, and item-model appearance SHALL remain explicitly pending until observed in a real client/server session.

#### Scenario: Automated checks pass before manual review
- **WHEN** contract/resource/protocol validation, Gradle tests, build, and dedicated-server startup pass
- **THEN** automated validation is reported green while gameplay and visual acceptance remain `human_review_pending`
