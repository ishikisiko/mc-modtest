## ADDED Requirements

### Requirement: The supplied PAL artifact has one verified identity
The integration SHALL use root file `PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar` with SHA-256 `b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072`. It SHALL treat the inspected NeoForge mod id as `player_animation_library`, the version as `1.1.4+mc.1.21.1`, and the Java API packages as `com.zigythebird.playeranim` and `com.zigythebird.playeranimcore`; it MUST NOT substitute Epic Fight, Better Combat, old PlayerAnimator, or a guessed PAL id/version.

#### Scenario: The expected jar is present
- **WHEN** Gradle configures the project with the exact root jar
- **THEN** MyVillage SHALL compile against that file and report the verified identity in the PAL integration note

#### Scenario: The expected jar is absent
- **WHEN** Gradle configures a task that needs project dependencies without the exact root filename
- **THEN** configuration SHALL fail with a concise message naming the missing file and expected root location
- **AND** it SHALL not fail later as an unexplained missing PAL class

### Requirement: PAL remains a non-shaded required runtime dependency
The mod SHALL declare `player_animation_library` required on `BOTH` with a version range that admits `1.1.4+mc.1.21.1` but not an unreviewed PAL 1.2 API. MyVillage SHALL put the supplied jar on development compile, client, and dedicated-server runtime classpaths without shading, unpacking, or copying PAL classes. The change SHALL record the embedded MIT license and SHALL leave the third-party binary uncommitted unless the repository owner separately approves vendoring with the required notice.

#### Scenario: Mod metadata is inspected
- **WHEN** `neoforge.mods.toml` is read after integration
- **THEN** it SHALL contain a required `player_animation_library` dependency on `BOTH`
- **AND** it SHALL not declare a guessed or legacy animation-library mod id

#### Scenario: The MyVillage jar is inspected
- **WHEN** the practical MyVillage jar is listed
- **THEN** it SHALL not contain `com/zigythebird/playeranim/**`, `com/zigythebird/playeranimcore/**`, or an embedded PAL jar

### Requirement: One client-only gameplay controller is registered for every client player
Physical-client setup SHALL call `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory` from `FMLClientSetupEvent#enqueueWork`, use one MyVillage layer id and gameplay priority `1600`, and create a `PlayerAnimationController` for each local or remote `AbstractClientPlayer`. The controller SHALL remain inactive unless MyVillage explicitly triggers an animation. Every source file importing PAL or `net.minecraft.client` combat types SHALL remain in a client-only package and entry path.

#### Scenario: A local player object is constructed
- **WHEN** PAL prepares animation factories for the local `AbstractClientPlayer`
- **THEN** the MyVillage layer SHALL resolve through `PlayerAnimationAccess.getPlayerAnimationLayer`
- **AND** it SHALL be a `PlayerAnimationController` at the declared gameplay priority

#### Scenario: A remote player begins tracking
- **WHEN** the physical client constructs a tracked remote `AbstractClientPlayer`
- **THEN** the same MyVillage controller factory SHALL register one independent layer for that player

#### Scenario: A dedicated server scans common code
- **WHEN** common registration, payload, item, attachment, and combat service classes are loaded
- **THEN** none SHALL import or resolve a PAL controller, PAL animation resource cache, `Minecraft`, `AbstractClientPlayer`, or another physical-client combat type

### Requirement: Original animations use PAL's verified resource contract
MyVillage SHALL author original Blockbench/Bedrock-style JSON below `assets/myvillage/player_animations/`. The JSON `animations` object SHALL define exactly the required ids `sword_mode_enter`, `sword_ready_idle`, `basic_sword_01_thrust`, `basic_sword_02_horizontal_cut`, `basic_sword_03_rising_cut`, `basic_sword_04_diagonal_cut`, and `basic_sword_05_lunge_thrust`. File names SHALL NOT be treated as animation ids. No animation, model, texture, or code from another combat mod SHALL be copied.

#### Scenario: PAL reloads MyVillage resources
- **WHEN** `PlayerAnimResources` lists JSON below namespace `myvillage` and directory `player_animations`
- **THEN** all seven ids SHALL resolve as `myvillage:<animation-key>`

#### Scenario: A move animation is validated
- **WHEN** a validator compares one five-move definition with its animation
- **THEN** the animation length SHALL match the move's total ticks within the declared small tolerance
- **AND** the animation SHALL contain torso, both-arm/shoulder, and leg participation rather than only a right-arm rotation

#### Scenario: The fifth animation is validated
- **WHEN** `basic_sword_05_lunge_thrust` is inspected
- **THEN** its pose data SHALL include a visible leg/body step while changing no authoritative player coordinate

### Requirement: PAL playback is triggerable, stoppable, and correctable
The client animation adapter SHALL trigger a resolved PAL animation by resource id, optionally start it at a non-negative elapsed PAL tick supplied by server-time correction, transition between ready and attack states through PAL APIs, and stop an equal-or-older action revision without leaving a held pose. Stopping or natural completion SHALL restore normal walking, jumping, and held-item pose.

#### Scenario: A smoke animation plays
- **WHEN** the local PAL smoke entry triggers `myvillage:sword_mode_enter`
- **THEN** the registered controller SHALL become active and play the resolved animation

#### Scenario: The smoke animation is stopped
- **WHEN** the smoke stop entry is invoked before completion
- **THEN** the controller SHALL stop the triggered animation and return the player model to its normal pose

#### Scenario: A delayed authoritative start arrives
- **WHEN** a start payload identifies an action that began several server ticks earlier
- **THEN** the controller SHALL start or restart the authoritative move at the bounded elapsed animation tick instead of trusting the predicted start time

#### Scenario: A stale stop arrives
- **WHEN** a stop revision is older than the player's current action revision
- **THEN** the client SHALL ignore it and keep the newer animation

### Requirement: Server broadcasts are the source of remote combat animation
Remote player animation SHALL begin and stop only from server-authoritative combat start/stop broadcasts. A client SHALL NOT infer another player's move from held item, swing state, position, or local packets.

#### Scenario: A nearby player starts a move
- **WHEN** the server accepts that player's attack intent and broadcasts the start
- **THEN** every tracking client SHALL animate the broadcast attacker entity with the declared move id, start tick, and revision

#### Scenario: A nearby action is aborted
- **WHEN** the server broadcasts a matching or newer stop revision
- **THEN** tracking clients SHALL stop that attacker's MyVillage controller and restore normal pose

### Requirement: First-person support is evidence-bound
The integration SHALL keep PAL 1.1.4's rejected `FirstPersonMode.THIRD_PERSON_MODEL` path disabled for the actual full-body combat animations. It SHALL register a physical-client-only Qingfeng `IClientItemExtensions` through `RegisterClientExtensionsEvent` and use `applyForgeHandTransform` to provide five distinct, bounded first-person held-sword sequences while the local player has a cultivation attack active with the Qingfeng Sword in the main hand. It SHALL also register a physical-client `RenderHandEvent` listener from the client bootstrap to render one local player-skin/sleeve segmented viewmodel before the ordinary item pass, without cancelling or replacing that item pass. The viewmodel SHALL be MyVillage-owned and SHALL contain a non-rendering shoulder driver, visible forearm and hand joints, and a screen-edge connector that terminates at the computed elbow. It SHALL generate wide/slim and right/left skin/sleeve variants, reset every joint before rendering, honor current skin and sleeve visibility, and omit internal caps that can present as near-plane slabs. It SHALL NOT copy or depend on Epic Fight or GeckoLib code, assets, models, or animations, and SHALL NOT borrow PAL-mutated or shared renderer model parts.

While a visible local player holds the Qingfeng Sword in the main hand in cultivation mode, item and arm SHALL use the animator's same corrected move frame or the same neutral fallback instead of independent elapsed-time clocks. Each move SHALL author distinct shoulder, elbow, and wrist tracks. Forward kinematics and a three-dimensional distal correction SHALL keep the hand endpoint on the sword grip for either hand; the corrected elbow SHALL drive the screen-edge connector. The active viewmodel MAY scale uniformly to `0.45` by normalized progress `0.12` to avoid the near plane only when that scale occurs around the corrected grip, is applied to the elbow target, and returns to `1.00` at both neutral endpoints. Separately damping a complete arm, applying a grip-agnostic model-origin scale, or rendering a complete floating player arm SHALL NOT satisfy this requirement. The independent arm SHALL be absent for off-hand, invisible, non-Qingfeng, or non-cultivation states. The five sequences SHALL map one-to-one to the server-owned move ids and durations, start or correct from the same elapsed tick supplied to PAL playback, and return the sword and arm to their joined neutral cultivation hold at completion or rejection; mode, item, and lifecycle interruption SHALL remove the layer through the same eligibility state. The first-person layers SHALL leave camera orientation unchanged, never animate remote players, and add no client-authored action, hit, damage, movement, or completion authority. Custom PAL body arms/camera SHALL remain documented as unsupported.

The earlier owner-requested continuity revision used an explicit `1.20` amplitude factor. That fixed value is historical and SHALL NOT define the current viewport-coverage requirement. In a `960x540`, `16:9`, FOV-70 reference capture, each move's temporal union of the projected sword-and-arm silhouette from visible wind-up through late recovery SHALL span at least `0.50` of the viewport on at least one screen axis, SHALL intersect the central horizontal band `x=[0.35,0.65]`, and SHALL NOT remain wholly within the lower-right quadrant. The envelope SHALL mean the projection accumulated across the action rather than a requirement for one frame to occlude half the viewport. Across moves one through five, visible wind-up keyframes SHALL remain within normalized progress `0.12-0.16`, strike keyframes within `0.56-0.60`, and late recovery keyframes within `0.84-0.88`, with exact neutral retained at `0.00` and `1.00`. Per-move camera-space translation, rotation, and scale MAY differ to meet the coverage contract, but the shared full parent frame, calibrated wrist grip, and unchanged server-owned total ticks SHALL remain authoritative. The revision SHALL NOT introduce near-plane clipping, grip separation, duplicate arm rendering, or camera rotation, and SHALL NOT change attack acceptance, active windows, combo timing, damage, step authority, or packet content.

If the expanded envelope requires the segmented arm or sleeve to be scaled
during an active move to avoid the near plane, that correction SHALL be smooth,
client-only, anchored at the distal hand endpoint, and shared by the elbow
connector target. It SHALL restore full size at both neutral endpoints and SHALL
NOT move the hand away from the calibrated grip, alter the third-person player
model, or change the action timeline. Scaling around an unanchored model-part
origin SHALL NOT satisfy this requirement.

An eligible local attack SHALL still start at most one packet-free fallback hand/sword swing, either at immediate prediction or at an unpredicted authoritative buffered start, without calling the one-argument `LocalPlayer#swing`, emitting `ServerboundSwingPacket`, or duplicating the dedicated first-person item transform.

#### Scenario: Dedicated first-person sequences pass
- **WHEN** a real client observes the local skin hand and Qingfeng Sword remain joined through all five move sequences without grip separation, clipping, duplicate rendering, camera jumps, indistinguishable poses, or a stuck transform
- **THEN** the final report MAY record the dedicated first-person Qingfeng animation as `pass`
- **AND** it SHALL continue to report custom PAL body arms/camera separately as unsupported

#### Scenario: Independent arm layer is ineligible
- **WHEN** cultivation mode is inactive, the Qingfeng Sword is not in the main hand, the local player is invisible, or the off-hand render event fires
- **THEN** the independent arm layer SHALL render nothing
- **AND** ordinary first-person item/hand behavior SHALL continue without a duplicate arm

#### Scenario: Independent arm returns to a joined neutral hold
- **WHEN** a cultivation Qingfeng action completes or has no active move frame while the local player remains eligible
- **THEN** the item and independent arm SHALL use the same neutral fallback
- **AND** the wrist SHALL remain on the calibrated grip instead of disappearing or snapping to a separately transformed pose

#### Scenario: Independent arm model is selected
- **WHEN** an eligible local cultivation hold or action renders for either the wide or slim player-skin model
- **THEN** the matching right/left segmented arm and enabled sleeve SHALL use the current player skin on the shared corrected sword frame
- **AND** the visible forearm/hand chain SHALL articulate from a screen-edge connector to the corrected elbow without exposing a complete floating arm
- **AND** PAL or shared third-person renderer pose state SHALL NOT alter that arm

#### Scenario: Viewport expansion preserves timing and grip
- **WHEN** the owner requests that each first-person move leave the lower-right corner and sweep across at least half of the reference viewport without changing total action time
- **THEN** only the client-side per-move translation, rotation, and scale curves SHALL change
- **AND** the normalized wind-up, strike, and recovery ranges, all server move totals, active windows, authority checks, damage, step timing, and payload schemas SHALL remain unchanged
- **AND** the item and arm SHALL retain their shared parent frame and calibrated grip throughout the expanded envelope
- **AND** physical-client evidence SHALL still decide whether the five envelopes meet the declared coverage, distinction, grip, and clipping contract

#### Scenario: First-person sequence evidence is absent or fails
- **WHEN** no real-client evidence exists or one declared first-person sequence check fails
- **THEN** the final report SHALL record dedicated first-person Qingfeng animation as `not_verified` or `fail`
- **AND** automated API/resource checks SHALL not promote it to `pass`

#### Scenario: Custom PAL first person is rejected but dedicated feedback remains visible
- **WHEN** the mapped cultivation attack is accepted for immediate prediction or an unpredicted buffered move starts authoritatively
- **THEN** the local client SHALL show the matching first-person Qingfeng move from the corrected action timeline
- **AND** the fallback swing and dedicated item transform SHALL send no vanilla swing packet and SHALL not restore vanilla attack, hit, damage, or movement authority

### Requirement: Gate A proves client and dedicated-server compatibility before combat expansion
Gate A SHALL include exact-jar hashing/metadata/API inspection, Gradle dependency resolution, PAL resource loading, controller registration, a client world play/stop/normal-pose smoke, client startup logs, and a bounded dedicated-server startup/clean-stop smoke. A compiler, linkage, mixin, resource-loader, or client-classloading failure SHALL block Gate B and SHALL be reported with the actual class, method, command, and error.

#### Scenario: Gate A is green
- **WHEN** every automated Gate A command passes and the client world play/stop smoke is directly observed
- **THEN** implementation MAY proceed to the first Qingfeng Sword move

#### Scenario: PAL is incompatible
- **WHEN** the exact supplied jar fails compilation, client initialization, controller playback, resource loading, stop recovery, or dedicated-server startup
- **THEN** implementation SHALL stop before copying the remaining combat moves
- **AND** evidence SHALL include the exact JAR API inspected, minimal reproduction, actual error, and bounded repair options without replacing PAL speculatively
