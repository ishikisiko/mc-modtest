## ADDED Requirements

### Requirement: Spiritual-root generation is a pure deterministic server algorithm
The system SHALL provide a pure `SpiritualRootGenerator` whose result depends only on the Overworld seed, player UUID, fixed mod salt, code-only algorithm version, and the complete sorted set of eligible spiritual-element ids and awakening weights. It SHALL use a repository-owned fixed 64-bit mixing and bounded-draw algorithm rather than Minecraft/JDK random behavior, registry iteration order, Java `String.hashCode` alone, current time/tick, position, current dimension, biome, weather, moon phase, player/level random, or client state. The Overworld seed MUST NOT be persisted in the profile, sent to the client, written to player chat, or emitted to ordinary logs.

#### Scenario: Identical canonical inputs are generated twice
- **WHEN** generation receives the same Overworld seed, UUID, eligible id/weight set, salt, and algorithm version twice
- **THEN** both generated `SpiritualRoot` values SHALL be exactly equal basis point by basis point

#### Scenario: Candidate iteration order changes
- **WHEN** two inputs contain the same eligible id/weight entries in different source orders
- **THEN** generation SHALL produce exactly the same selected ids and affinities

#### Scenario: Awakening occurs outside the Overworld
- **WHEN** a player invokes awakening from the Nether or End
- **THEN** the service SHALL supply the server Overworld seed to the generator
- **AND** current-dimension seed, position, time, and client state SHALL NOT affect the result

#### Scenario: The algorithm contract is regression tested
- **WHEN** fixed seed, UUID, and candidate fixtures run in Java tests
- **THEN** golden vectors SHALL pin the salt, UTF-8 id hashing, 64-bit mixer, bounded draws, selected ids, and final affinity vector

### Requirement: Awakening candidates come from weighted datapack definitions
The awakening service SHALL construct candidates from the current `myvillage:spiritual_element` registry without hard-coding the shipped five element ids. Only definitions whose `awakening_weight` is greater than `0` SHALL be eligible. Before any draw, candidates SHALL be sorted by complete `ResourceLocation` string, duplicate ids SHALL be rejected, and total/cumulative weights SHALL use checked `long` arithmetic so overflow returns a controlled failure.

#### Scenario: A datapack adds an eligible element
- **WHEN** the current registry contains a new spiritual element with positive `awakening_weight`
- **THEN** ordinary awakening SHALL include that id in its weighted candidate set

#### Scenario: A definition has zero awakening weight
- **WHEN** an element definition has `awakening_weight` equal to `0`
- **THEN** ordinary awakening SHALL NOT select that element

#### Scenario: No eligible elements exist
- **WHEN** every current element has weight `0` or the eligible registry set is empty
- **THEN** awakening SHALL return `NO_ELIGIBLE_ELEMENTS`
- **AND** it SHALL NOT change root, stage, or any other profile field
- **AND** it SHALL NOT send a mutation snapshot or play the complete success effect

#### Scenario: Candidate total overflows
- **WHEN** checked accumulation cannot represent the candidate weight total in a `long`
- **THEN** generation SHALL fail in a controlled manner before selection or profile mutation

### Requirement: Initial root element count follows the fixed distribution
The generator SHALL roll a requested count with integer weights `1 element = 10`, `2 = 25`, `3 = 35`, `4 = 20`, and `5 = 10`. It SHALL set `effectiveCount` to the smaller of the rolled count and eligible candidate count, SHALL select at most five elements even when more are eligible, and SHALL perform awakening-weighted selection without replacement.

#### Scenario: Every root-count bucket is exercised
- **WHEN** deterministic fixtures produce draws in each of the five count intervals
- **THEN** the requested counts SHALL be `1`, `2`, `3`, `4`, and `5` with the declared `10/25/35/20/10` weighting

#### Scenario: Only one element is eligible
- **WHEN** the rolled count is greater than one but the candidate set contains one eligible id
- **THEN** the root SHALL contain exactly that one id

#### Scenario: More than five elements are eligible
- **WHEN** the registry contains more than five positive-weight definitions
- **THEN** an ordinary root SHALL contain no more than five distinct ids

#### Scenario: Weighted draws are without replacement
- **WHEN** a multi-element root is generated
- **THEN** no selected element id SHALL occur more than once

### Requirement: Generated affinities are positive exact integer basis points
The generator SHALL produce an immutable map containing only selected element ids, with every selected affinity greater than `0` and the total exactly `10000`. A single selected element SHALL receive `10000`. Multiple elements SHALL each receive a base `1000`, distribute the remainder by stable positive integer weights and integer division, and assign rounding residue by largest remainder with full-id ascending tie-break. It SHALL NOT use floating-point accumulation or assign arbitrary rounding error to a map's final iteration entry.

#### Scenario: One element is selected
- **WHEN** a root has one selected element
- **THEN** that element's affinity SHALL equal `10000`

#### Scenario: Several elements are selected
- **WHEN** a root has two through five selected elements
- **THEN** every affinity SHALL be positive
- **AND** their sum SHALL equal exactly `10000`
- **AND** no unselected or zero-valued id SHALL be stored

#### Scenario: Apportionment remainders tie
- **WHEN** two selected ids have equal largest-remainder values
- **THEN** the extra basis point SHALL be assigned in full `ResourceLocation` string order

#### Scenario: Affinity arithmetic cannot complete safely
- **WHEN** checked integer arithmetic detects overflow or an invariant violation
- **THEN** generation SHALL return a controlled failure rather than an invalid root

### Requirement: Awakening validates state and commits root plus stage atomically
`SpiritualRootAwakeningService` SHALL read the current profile and current registries, obtain the Overworld seed, invoke the pure generator, and submit one immutable replacement through `CultivationService`. Ordinary awakening SHALL require no existing root, realm `myvillage:mortal`, and stage `myvillage:mortal_unawakened` or `myvillage:mortal_qi_sensed`. Success SHALL set the generated root and stage `myvillage:mortal_qi_sensed` in one profile replacement while preserving schema version `1`, realm, cultivation progress, stability, current spiritual power, and learned techniques.

#### Scenario: A default profile awakens
- **WHEN** an unawakened default mortal profile uses the awakening service with eligible elements
- **THEN** the final profile SHALL contain the generated root
- **AND** its stage SHALL equal `myvillage:mortal_qi_sensed`
- **AND** exactly one attachment replacement and one final client snapshot SHALL occur

#### Scenario: An administrator cleared a sensed mortal root
- **WHEN** a mortal profile has stage `myvillage:mortal_qi_sensed` but no root after `clearroot`
- **THEN** ordinary awakening SHALL be allowed and SHALL atomically restore a generated root while retaining that stage

#### Scenario: A non-mortal profile lacks a root
- **WHEN** a rootless profile's realm is not `myvillage:mortal`
- **THEN** awakening SHALL return `INVALID_PROFILE_STATE`
- **AND** it SHALL NOT force the profile back to mortal

#### Scenario: The update boundary rejects the replacement
- **WHEN** `CultivationService` rejects the proposed awakened profile
- **THEN** the service SHALL return `UPDATE_REJECTED`
- **AND** no intermediate root-only or stage-only profile and no changed snapshot SHALL be observable

### Requirement: Awakening is one-time and existing roots are never migrated automatically
If a profile already contains any spiritual root, including one installed by an administrator, ordinary awakening SHALL return `ALREADY_AWAKENED` without generation, overwrite, stage repair, snapshot, or complete success effect. Registry/datapack or generator changes SHALL affect only future generation; already persisted roots, including unknown element ids, SHALL remain unchanged and SHALL NOT be automatically recalculated, deleted, or migrated.

#### Scenario: An awakened player repeats the ritual
- **WHEN** a profile with a spiritual root invokes awakening again
- **THEN** the profile SHALL remain exactly unchanged
- **AND** the existing affinity vector SHALL NOT be rerolled

#### Scenario: Reset is followed by awakening under unchanged inputs
- **WHEN** an administrator resets a player and the Overworld seed, UUID, eligible id/weight set, and algorithm version remain unchanged
- **THEN** the next awakening SHALL reproduce the prior affinity vector exactly

#### Scenario: The datapack changes after awakening
- **WHEN** element ids, positive-weight membership, or awakening weights change after a root was saved
- **THEN** the saved root SHALL remain unchanged
- **AND** newly awakened players SHALL use the new current eligible set
- **AND** reset followed by awakening MAY produce a different root

### Requirement: Technique eligibility is evaluated from the current definition
The system SHALL provide a reusable `TechniqueRequirementEvaluator` that resolves the current profile and `TechniqueDefinition.requirements` against current registries. Minimum-realm and minimum-stage rules SHALL use registered realm/stage ordering and membership; minimum element affinities SHALL require a present root and each declared basis-point threshold. Missing or ambiguous references SHALL fail closed. `TechniqueDefinition.elements` SHALL remain metadata and SHALL NOT replace the explicit requirements map.

#### Scenario: The current profile is below a minimum stage
- **WHEN** a technique requires a registered stage later than the player's stage within the same realm
- **THEN** the evaluator SHALL report requirements not met

#### Scenario: A required affinity is absent or too low
- **WHEN** a technique declares a minimum element affinity and the profile has no root, lacks that id, or has fewer basis points
- **THEN** the evaluator SHALL report requirements not met

#### Scenario: A requirement reference is unavailable
- **WHEN** the current registry cannot resolve the declared minimum realm, stage, or element definition needed for evaluation
- **THEN** the evaluator SHALL fail closed without changing the profile

#### Scenario: Basic breathing is evaluated
- **WHEN** `myvillage:basic_breathing` is loaded from shipped data
- **THEN** its requirements SHALL be minimum realm `myvillage:mortal` and minimum stage `myvillage:mortal_qi_sensed`
- **AND** it SHALL declare no element-affinity restriction

### Requirement: Basic-technique inheritance is a distinct atomic normal-rules service
`TechniqueInheritanceService` SHALL resolve `myvillage:basic_breathing` first. If the definition is missing it SHALL return `TECHNIQUE_NOT_REGISTERED`; otherwise it SHALL reject an already learned entry before checking root or later eligibility state, then require a present spiritual root, apply `TechniqueRequirementEvaluator`, and submit one immutable replacement through `CultivationService`. Success SHALL add exactly `myvillage:basic_breathing -> masteryPoints 0` while preserving schema version, realm, stage, progress, stability, current spiritual power, spiritual root, and every other learned technique. It SHALL NOT equip or execute the technique or grant progress, power, stability, attributes, effects, or advancement.

#### Scenario: An awakened qi-sensed mortal inherits basic breathing
- **WHEN** the current registered definition requirements pass and the technique is not learned
- **THEN** inheritance SHALL return `SUCCESS`
- **AND** one final profile SHALL contain `myvillage:basic_breathing` at mastery `0`
- **AND** exactly one attachment replacement and one final snapshot SHALL occur

#### Scenario: The player has not awakened
- **WHEN** a profile has no spiritual root
- **THEN** inheritance SHALL return `NOT_AWAKENED`
- **AND** the profile and snapshot state SHALL remain unchanged

#### Scenario: Requirements do not pass
- **WHEN** the current definition's realm, stage, or affinity requirements are not satisfied
- **THEN** inheritance SHALL return `REQUIREMENTS_NOT_MET`
- **AND** it SHALL NOT use a hard-coded stage bypass or modify the profile

#### Scenario: Basic breathing is already learned
- **WHEN** the profile already contains `myvillage:basic_breathing` with mastery `350`
- **THEN** inheritance SHALL return `ALREADY_LEARNED`
- **AND** mastery SHALL remain `350`
- **AND** no changed snapshot or complete success effect SHALL occur

#### Scenario: A learned profile later loses its root or stage eligibility
- **WHEN** the current definition exists and the profile already contains `myvillage:basic_breathing` but its root or stage no longer satisfies normal initiation
- **THEN** inheritance SHALL still return `ALREADY_LEARNED`
- **AND** it SHALL not reset mastery or replace the existing profile

#### Scenario: The definition was removed but progress is saved
- **WHEN** the profile contains `myvillage:basic_breathing` but the current technique registry does not
- **THEN** inheritance SHALL return `TECHNIQUE_NOT_REGISTERED`
- **AND** the unknown saved id and its mastery SHALL remain unchanged

### Requirement: Two independent steles expose the two server services
The mod SHALL register `myvillage:spirit_testing_stele` and `myvillage:technique_inheritance_stele` as two separate blocks with matching BlockItems, `myvillage:main` creative-tab entries, `ModBlocks.verifyRegistered` coverage, complete blockstate/block-model/item-model/loot-table/tool-tag and bilingual language resources, and jar inclusion. They SHALL use current vanilla block interaction, execute only on the logical server, prevent main/off-hand double submission, own no BlockEntity/menu/player data, have no recipe or natural generation, and be obtainable through `/give`.

#### Scenario: The testing stele is used successfully
- **WHEN** an eligible player uses `spirit_testing_stele`
- **THEN** the block SHALL call `SpiritualRootAwakeningService` exactly once
- **AND** only a committed `SUCCESS` SHALL produce the full vanilla sound/particle feedback
- **AND** the translatable success message SHALL list affinities descending by value then ascending by id

#### Scenario: The inheritance stele is used successfully
- **WHEN** an eligible awakened player uses `technique_inheritance_stele`
- **THEN** the block SHALL call `TechniqueInheritanceService` exactly once
- **AND** the translatable feedback SHALL state that `basic_breathing` was learned, the `H` profile can show it, and the current version cannot execute it

#### Scenario: A translated element label is unavailable
- **WHEN** affinity feedback cannot resolve an element translation key
- **THEN** it SHALL display the raw `ResourceLocation` and SHALL NOT fail the interaction

#### Scenario: A stele is inspected for excluded systems
- **WHEN** registrations, resources, recipes, and worldgen are validated
- **THEN** neither stele SHALL have a BlockEntity, menu, recipe, natural placement, structure integration, or block-local profile storage

### Requirement: Initiation reuses the existing one-way snapshot and read-only profile screen
Successful initiation mutations SHALL synchronize through the existing clientbound `CultivationSnapshotPayload` to the owning player. The payload/profile shape SHALL remain version `1`; no cultivation play-to-server mutation payload SHALL be added. The client SHALL only cache and render the received root, stage, and learned technique through the existing read-only `H` screen and SHALL NOT generate a root, evaluate inheritance, choose ids/affinities, or submit profile changes.

#### Scenario: Awakening succeeds then inheritance succeeds
- **WHEN** the owning client receives each final snapshot
- **THEN** after awakening the H screen SHALL show `myvillage:mortal_qi_sensed`, the generated affinities, and no automatically learned technique
- **AND** after inheritance it SHALL show `myvillage:basic_breathing` at mastery `0`

#### Scenario: A ritual action fails
- **WHEN** awakening or inheritance returns any non-success result
- **THEN** no mutation snapshot SHALL be sent
- **AND** the client SHALL retain its prior read-only snapshot

#### Scenario: Payload registrations are inspected
- **WHEN** initiation integration is validated
- **THEN** no `AwakenPayload`, root-generation payload, inheritance payload, or other cultivation play-to-server mutation payload SHALL exist
- **AND** the existing flying-sword serverbound payload SHALL remain unchanged

### Requirement: Initiation does not implement cultivation execution or progression
This change SHALL NOT add meditation, basic-breathing execution, spiritual-power cap/recovery, cultivation or mastery gain, automatic realm/stage advancement, breakthroughs, stability growth, technique equipment, combat effects, element bonuses, root quality/tiering, rerolls, sect/region/worldgen integration, quests, NPCs, alchemy, crafting, or a profile schema v2 field. Learning a technique SHALL NOT mean equipping, executing, gaining cultivation, or gaining spiritual power.

#### Scenario: A player completes both steles
- **WHEN** a player awakens and inherits `myvillage:basic_breathing`
- **THEN** the player SHALL remain at realm `myvillage:mortal` and stage `myvillage:mortal_qi_sensed`
- **AND** the ritual SHALL NOT increase cultivation progress, spiritual power, stability, mastery, attributes, or effects
- **AND** `basic_breathing` SHALL still have no executor
