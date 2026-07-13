# cultivation-debug-commands Specification

## Purpose

Define the permissioned `/myvillage cultivation` diagnostics and mutation
commands used to inspect and validate server-authoritative player profiles.
## Requirements
### Requirement: Cultivation commands extend the existing protected root
`CultivationCommands` SHALL provide `cultivation` and pinyin `xiulian` subtrees delegated from the existing `/myvillage` command registration. Both subtrees SHALL inherit the root's permission-level-2 requirement. Adding them SHALL NOT refactor or change existing town, sect, gallery, spawn, structure-placement, or flying-sword behavior.

#### Scenario: An operator uses a cultivation command
- **WHEN** a command source with permission level `2` or greater invokes `/myvillage cultivation ...`
- **THEN** the requested cultivation subcommand SHALL execute

#### Scenario: An unprivileged player uses a cultivation command
- **WHEN** a command source below permission level `2` attempts to invoke the subtree
- **THEN** Brigadier SHALL deny access through the existing root permission boundary

### Requirement: Every cultivation command has a structurally equivalent pinyin alias
Both `/myvillage cultivation` and `/myvillage xiulian` SHALL expose every English and pinyin literal in the following fixed mapping: `info` / `chakan`, `reset` / `chongzhi`, `setrealm` / `shezhijingjie`, `setprogress` / `shezhixiuwei`, `setstability` / `shezhiwendingdu`, `setpower` / `shezhilingli`, `setroot` / `shezhilinggen`, `clearroot` / `qingchulinggen`, `learn` / `xuexi`, `forget` / `yiwang`, `setmastery` / `shezhishuliandu`, rules-based `awaken` / `juexing`, and rules-based `initiate` / `rumen`. Each pair SHALL use the same argument names and types, optional-target shape where defined, numeric bounds, dynamic registry behavior, execution handler, permission boundary, diagnostics, return result, atomic mutation semantics, and snapshot synchronization behavior. The aliases SHALL NOT introduce a second service mutation implementation.

#### Scenario: An operator uses a fully pinyin route
- **WHEN** an operator invokes `/myvillage xiulian shezhixiuwei <target> <amount>`
- **THEN** the command SHALL produce the same result as `/myvillage cultivation setprogress <target> <amount>`

#### Scenario: English and pinyin literals are mixed
- **WHEN** an operator uses an English subcommand under `xiulian` or a pinyin subcommand under `cultivation`
- **THEN** Brigadier SHALL accept the route with the same arguments and behavior as its canonical English route

#### Scenario: Initiation aliases are mixed across roots
- **WHEN** an operator invokes any of `cultivation awaken`, `cultivation juexing`, `xiulian awaken`, or `xiulian juexing`
- **THEN** every route SHALL use the same awakening handler and service
- **AND** the corresponding four `initiate`/`rumen` routes SHALL use the same inheritance handler and service

#### Scenario: Command-tree equivalence is tested
- **WHEN** automated tests enumerate both root subtrees and every alias pair
- **THEN** both roots SHALL contain the complete English-and-pinyin literal set
- **AND** each English/pinyin pair SHALL expose an equivalent descendant argument and execution shape

### Requirement: The info command reports the complete current profile
The mod SHALL provide `/myvillage cultivation info [target]` and its existing
structurally equivalent aliases. Output SHALL include schema version, realm,
stage, cultivation progress, stability, current spiritual power,
`lifespanConsumedTicks`, `meditationQiReserve`, spiritual root or `unawakened`,
and every learned technique id with mastery.

#### Scenario: A new player is inspected
- **WHEN** an operator runs `info` for a player with the default profile
- **THEN** output SHALL show schema `2`, mortal/unawakened ids, all numeric fields including lifespan/reserve as zero, and no learned techniques

#### Scenario: Stored ids are unavailable
- **WHEN** the inspected v2 profile contains an id absent from current registries
- **THEN** output SHALL retain and mark the raw id as `unavailable`
- **AND** inspection SHALL not mutate or reset the profile

### Requirement: Reset and scalar commands mutate through CultivationService
The existing scalar/reset routes SHALL mutate through `CultivationService`.
The reset, setprogress, setstability, and setpower English/pinyin routes SHALL
retain their argument and permission contracts and SHALL mutate through
`CultivationService`. Reset SHALL install the exact current-v2 default, including
zero lifespan and reserve. This change SHALL not add commands that let players
author calendar, lifespan, or reserve values.

#### Scenario: A profile is reset
- **WHEN** an operator runs reset for a target
- **THEN** the service SHALL install the exact default v2 profile and synchronize immediately

#### Scenario: A scalar amount is invalid
- **WHEN** an operator supplies negative progress or power, or stability outside `0..100`
- **THEN** command/service validation SHALL reject the input and leave every v2 field unchanged

#### Scenario: A player requests clock mutation
- **WHEN** the command trees are enumerated after this change
- **THEN** no unprivileged or new administrator calendar/lifespan/reserve setter SHALL exist

### Requirement: Realm changes require a registered matching realm-stage pair
The mod SHALL provide `/myvillage cultivation setrealm <target> <realm_id> <stage_id>`. The command SHALL resolve `realm_id` in `myvillage:realm`, SHALL require `stage_id` to occur in that realm's stage list, and SHALL call `CultivationService#setRealmAndStage` only after both checks pass.

#### Scenario: A valid realm and stage are selected
- **WHEN** the selected realm exists and contains the selected stage id
- **THEN** the service SHALL replace the profile's realm and stage together
- **AND** it SHALL preserve all unrelated profile fields

#### Scenario: The realm is absent
- **WHEN** `realm_id` is not registered in the current realm registry
- **THEN** the command SHALL fail with an error naming the missing realm
- **AND** the profile SHALL remain unchanged

#### Scenario: The stage belongs to another realm
- **WHEN** the selected stage is not listed by the selected realm
- **THEN** the command SHALL fail with an error naming the realm-stage mismatch
- **AND** the profile SHALL remain unchanged

### Requirement: The shipped five-element root command validates exact basis points
The mod SHALL provide `/myvillage cultivation setroot <target> <metal> <wood> <water> <fire> <earth>`. Each value SHALL be an integer from `0` through `10000`, their sum SHALL equal exactly `10000`, and the five values SHALL map to `myvillage:metal`, `myvillage:wood`, `myvillage:water`, `myvillage:fire`, and `myvillage:earth`. All five ids SHALL exist in the current spiritual-element registry before the service writes the root. The underlying `SpiritualRoot` and service SHALL remain generic for future element ids.

#### Scenario: Five affinities form a valid root
- **WHEN** all five values are in range, total `10000`, and all five element definitions are registered
- **THEN** the service SHALL install the corresponding spiritual root
- **AND** it SHALL synchronize the target immediately

#### Scenario: Five affinities have the wrong total
- **WHEN** all individual values are in range but their sum is not `10000`
- **THEN** the command SHALL fail with an error that reports the required and actual total
- **AND** the previous profile SHALL remain byte-for-byte equivalent after codec encoding

#### Scenario: A shipped element definition is missing
- **WHEN** one of the five named element ids is absent from the current registry
- **THEN** the command SHALL fail with an error naming that element
- **AND** it SHALL NOT install a partial root

### Requirement: The root can be cleared explicitly
The mod SHALL provide `/myvillage cultivation clearroot <target>` and SHALL route it through `CultivationService#clearSpiritualRoot`. Clearing the root SHALL make awakening derive as false and SHALL preserve all unrelated profile fields.

#### Scenario: An awakened profile is cleared
- **WHEN** an operator runs `clearroot` for a profile with a spiritual root
- **THEN** the new profile SHALL have an empty spiritual root
- **AND** the client SHALL receive the updated snapshot

### Requirement: Technique commands require current registered definitions
The mod SHALL provide `/myvillage cultivation learn <target> <technique_id>`, `forget <target> <technique_id>`, and `setmastery <target> <technique_id> <amount>`. Every supplied technique id SHALL resolve in the current technique registry before mutation. `learn` SHALL add zero mastery, `forget` SHALL remove a learned entry, and `setmastery` SHALL require an already learned technique and a non-negative long amount.

#### Scenario: A registered technique is learned
- **WHEN** `learn` targets `myvillage:basic_breathing` while it is registered and not learned
- **THEN** the profile SHALL gain that technique with zero mastery

#### Scenario: An unregistered technique is learned
- **WHEN** `learn` targets an id absent from the current technique registry
- **THEN** the command SHALL fail with an error naming the id
- **AND** the profile SHALL remain unchanged

#### Scenario: Mastery is set for an unlearned technique
- **WHEN** `setmastery` targets a registered technique absent from the target's learned map
- **THEN** the command SHALL fail without implicitly learning it

#### Scenario: A learned technique is forgotten
- **WHEN** `forget` targets a registered learned technique
- **THEN** the service SHALL remove only that learned entry
- **AND** it SHALL synchronize the target immediately

### Requirement: Registry-backed identifiers have dynamic suggestions
Realm and technique command arguments SHALL suggest ids from the current command source `RegistryAccess`. Stage suggestions for `setrealm` SHALL derive from the selected realm where the parsed command context permits it. Suggestions SHALL NOT use a hard-coded list of shipped ids.

#### Scenario: A datapack adds a technique
- **WHEN** command suggestions are requested after the datapack technique is registered
- **THEN** its id SHALL appear in technique suggestions

#### Scenario: A datapack removes a realm
- **WHEN** realm suggestions are requested after the realm is absent from current registry access
- **THEN** the removed id SHALL NOT be suggested merely because an old player profile references it

### Requirement: Command failures are atomic and diagnostic
Every invalid cultivation command SHALL identify the specific invalid target, id, range, affinity total, realm-stage membership, learned-state, or registry condition. A failed command SHALL NOT call `setData`, partially mutate a map, or send a changed snapshot. A successful command SHALL report the target and resulting operation.

#### Scenario: Validation fails after multiple arguments parse
- **WHEN** a later semantic check such as realm-stage membership or root total fails
- **THEN** none of the earlier parsed values SHALL be written to the target profile

### Requirement: Awakening commands call the ordinary awakening service
The command tree SHALL provide `awaken` and `juexing` under both `cultivation` and `xiulian`. Each literal SHALL execute for the command-source player when no target is supplied and SHALL also expose a standard single-player `target` argument. Every route SHALL inherit `/myvillage` permission level `2` and call one shared handler backed by `SpiritualRootAwakeningService`. The routes SHALL accept no seed, element, affinity, root count, reroll, force, or bypass argument.

#### Scenario: An operator awakens themself
- **WHEN** an operator player runs `/myvillage cultivation awaken` without a target
- **THEN** the shared handler SHALL invoke ordinary awakening for that executing player

#### Scenario: An operator awakens one target through pinyin
- **WHEN** an operator runs `/myvillage xiulian juexing <target>`
- **THEN** the same ordinary awakening service and result mapping SHALL apply to the selected player

#### Scenario: A repeat awakening command runs
- **WHEN** any awakening alias targets an already awakened profile
- **THEN** it SHALL report the controlled already-awakened result
- **AND** it SHALL NOT reroll, overwrite, or force the root

#### Scenario: The awaken argument surface is inspected
- **WHEN** Brigadier descendants under `awaken` and `juexing` are enumerated
- **THEN** only the optional standard single-player target path SHALL exist
- **AND** no seed, element, affinity, count, reroll, or force argument SHALL exist

### Requirement: Inheritance commands call the normal-rules inheritance service
The command tree SHALL provide `initiate` and `rumen` under both `cultivation` and `xiulian`. Each literal SHALL execute for the command-source player when no target is supplied and SHALL also expose a standard single-player `target` argument. Every route SHALL call one shared handler backed by `TechniqueInheritanceService` for fixed id `myvillage:basic_breathing`. It SHALL NOT accept a technique id or call the low-level `learn` handler to bypass awakening or current definition requirements.

#### Scenario: An operator initiates themself
- **WHEN** an operator player runs `/myvillage cultivation initiate` without a target
- **THEN** the shared handler SHALL invoke normal-rules basic-technique inheritance for that player

#### Scenario: An operator initiates one target through pinyin
- **WHEN** an operator runs `/myvillage xiulian rumen <target>`
- **THEN** the same inheritance service and result mapping SHALL apply to the selected player

#### Scenario: A repeat inheritance command runs
- **WHEN** any inheritance alias targets a profile that already knows basic breathing
- **THEN** it SHALL report the controlled already-learned result
- **AND** it SHALL NOT reset existing mastery

#### Scenario: The initiate argument surface is inspected
- **WHEN** Brigadier descendants under `initiate` and `rumen` are enumerated
- **THEN** only the optional standard single-player target path SHALL exist
- **AND** no technique-id or requirement-bypass argument SHALL exist

### Requirement: Rules-based initiation remains distinct from low-level administrator mutation
`awaken`/`juexing` SHALL enforce one-time awakening through `SpiritualRootAwakeningService`, and `initiate`/`rumen` SHALL enforce current `TechniqueDefinition.requirements` through `TechniqueInheritanceService`. Existing `setroot`, `clearroot`, `learn`, `forget`, `setmastery`, and `reset` commands SHALL retain their administrator debugging roles and SHALL NOT be silently repurposed as the normal ritual handlers.

#### Scenario: Learn and initiate are compared
- **WHEN** command handlers are inspected or tested
- **THEN** `learn` SHALL remain the explicit administrator technique mutation path for a supplied registered id
- **AND** `initiate` SHALL use the fixed basic-breathing inheritance service and its awakening/requirements checks

#### Scenario: The final command boundary is enumerated
- **WHEN** both cultivation roots are inspected after this change
- **THEN** they SHALL expose the existing diagnostics/mutation commands plus only the new awakening and basic-inheritance normal-rules routes
- **AND** they SHALL NOT expose meditation, cultivation-gain, breakthrough, technique-execution, recovery, equipment, combat, reroll, or force-awaken commands
