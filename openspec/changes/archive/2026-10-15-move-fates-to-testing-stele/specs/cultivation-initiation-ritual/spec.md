## MODIFIED Requirements

### Requirement: Two independent steles expose the two server services
The mod SHALL register `myvillage:spirit_testing_stele` and `myvillage:technique_inheritance_stele` as two separate blocks with matching BlockItems, `myvillage:main` creative-tab entries, `ModBlocks.verifyRegistered` coverage, complete blockstate/block-model/item-model/loot-table/tool-tag and bilingual language resources, and jar inclusion. They SHALL use current vanilla block interaction, execute only on the logical server, prevent main/off-hand double submission, own no BlockEntity/menu/block-local player data, have no recipe or natural generation, and be obtainable through `/give`.

#### Scenario: The testing stele is used successfully
- **WHEN** an eligible rootless player uses `spirit_testing_stele`
- **THEN** the block SHALL call `SpiritualRootAwakeningService` exactly once
- **AND** only a committed `SUCCESS` SHALL produce the full vanilla sound/particle feedback
- **AND** the translatable success message SHALL list affinities descending by value then ascending by id
- **AND** the server SHALL synchronize the final root and open the independent root-first awakening screen for that player

#### Scenario: The inheritance stele is used successfully
- **WHEN** an eligible awakened player uses `technique_inheritance_stele`
- **THEN** the block SHALL call `TechniqueInheritanceService` exactly once
- **AND** the translatable feedback SHALL state that `basic_breathing` was learned and the `H` profile can show it
- **AND** inheritance SHALL not automatically start execution or meditation

#### Scenario: A translated element label is unavailable
- **WHEN** affinity feedback cannot resolve an element translation key
- **THEN** it SHALL display the raw `ResourceLocation` and SHALL NOT fail the interaction

#### Scenario: A stele is inspected for excluded systems
- **WHEN** registrations, resources, recipes, and worldgen are validated
- **THEN** neither stele SHALL have a BlockEntity, menu, recipe, natural placement, structure integration, or block-local profile storage


#### Scenario: An existing root resumes awakening presentation
- **WHEN** a living non-spectator player with an existing root uses the testing stele, including at a later stage or after inheritance
- **THEN** the server SHALL preserve that root, skip root generation, synchronize the current unchanged profile, establish a valid interaction session and open the awakening screen without replaying a fresh-awakening success effect
- **AND** fate draft/result resumption SHALL follow `innate-fates`


### Requirement: Initiation reuses the existing one-way snapshot and read-only profile screen
Successful initiation mutations SHALL synchronize through the current
clientbound profile snapshot. Initiation SHALL add no awaken, root-generation,
affinity-selection, inheritance, or technique-learning serverbound payload. The
separate meditation capability MAY register bounded normal/spirit/stop intent
that carries no profile value and cannot bypass either ritual. H SHALL remain read-only for root and fate results and SHALL not generate roots, evaluate inheritance, choose fate ids or affinities, or submit ritual/fate changes. A separate clientbound notification SHALL open the awakening screen after the server validates a testing-stele interaction; only the separately specified bounded fate intents SHALL mutate its fate choice.

#### Scenario: Awakening succeeds then inheritance succeeds
- **WHEN** the owning client receives each final ritual snapshot
- **THEN** H SHALL show the sensed-stage/root result and later Basic Breathing mastery zero

#### Scenario: A ritual action fails
- **WHEN** awakening or inheritance returns a non-success result
- **THEN** no changed profile snapshot SHALL be sent and the prior client profile SHALL remain

#### Scenario: Payload registrations are inspected
- **WHEN** initiation is validated alongside meditation
- **THEN** no awaken, root-generation, affinity-selection, inheritance or technique-learning serverbound payload SHALL exist
- **AND** only separately specified bounded gameplay intents, including fate selection and session closure, SHALL be permitted serverbound; none SHALL generate or install a client-authored root



### Requirement: Awakening is one-time and existing roots are never migrated automatically
If a profile already contains any spiritual root, including one installed by an administrator, ordinary awakening SHALL return `ALREADY_AWAKENED` without generation, overwrite, stage repair, mutation snapshot, or complete success effect. The testing-stele presentation path SHALL be allowed to synchronize that unchanged profile and open the awakening screen for an existing rooted character at any later stage; that resynchronization SHALL NOT count as a new awakening commit. Registry/datapack or generator changes SHALL affect only future generation; already persisted roots, including unknown element ids, SHALL remain unchanged and SHALL NOT be automatically recalculated, deleted, or migrated.

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
