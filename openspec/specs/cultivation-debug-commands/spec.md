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
Both `/myvillage cultivation` and `/myvillage xiulian` SHALL expose every English and pinyin literal in the following fixed mapping: `info` / `chakan`, `reset` / `chongzhi`, `setrealm` / `shezhijingjie`, `setprogress` / `shezhixiuwei`, `setstability` / `shezhiwendingdu`, `setpower` / `shezhilingli`, `setroot` / `shezhilinggen`, `clearroot` / `qingchulinggen`, `learn` / `xuexi`, `forget` / `yiwang`, and `setmastery` / `shezhishuliandu`. Each pair SHALL use the same argument names and types, numeric bounds, dynamic registry suggestions, execution handler, permission boundary, diagnostics, return result, atomic mutation semantics, and snapshot synchronization behavior. The aliases SHALL NOT introduce a second service mutation implementation.

#### Scenario: An operator uses a fully pinyin route
- **WHEN** an operator invokes `/myvillage xiulian shezhixiuwei <target> <amount>`
- **THEN** the command SHALL produce the same result as `/myvillage cultivation setprogress <target> <amount>`

#### Scenario: English and pinyin literals are mixed
- **WHEN** an operator uses an English subcommand under `xiulian` or a pinyin subcommand under `cultivation`
- **THEN** Brigadier SHALL accept the route with the same arguments and behavior as its canonical English route

#### Scenario: Command-tree equivalence is tested
- **WHEN** automated tests enumerate both root subtrees and every alias pair
- **THEN** both roots SHALL contain the complete English-and-pinyin literal set
- **AND** each English/pinyin pair SHALL expose an equivalent descendant argument and execution shape

### Requirement: The info command reports the complete current profile
The mod SHALL provide `/myvillage cultivation info [target]`. Without `target`, the executing player SHALL be used; with `target`, the command SHALL use the standard single-player argument. Output SHALL include schema version, realm id, stage id, cultivation progress, stability, current spiritual power, spiritual root or `unawakened`, and every learned technique id with mastery.

#### Scenario: A new player is inspected
- **WHEN** an operator runs `info` for a player with the default profile
- **THEN** output SHALL show schema `1`, `myvillage:mortal`, `myvillage:mortal_unawakened`, zero numeric values, `unawakened`, and no learned techniques

#### Scenario: Stored ids are unavailable
- **WHEN** the inspected profile contains a realm, stage, element, or technique id absent from current registries
- **THEN** output SHALL retain the raw id
- **AND** it SHALL mark that id as `unavailable`
- **AND** inspection SHALL NOT mutate or reset the profile

### Requirement: Reset and scalar commands mutate through CultivationService
The mod SHALL provide `/myvillage cultivation reset <target>`, `setprogress <target> <amount>`, `setstability <target> <0..100>`, and `setpower <target> <amount>`. Targets SHALL use the standard player argument. Progress and power amounts SHALL be non-negative long values, stability SHALL be bounded from `0` through `100`, and all successful operations SHALL call the matching `CultivationService` method.

#### Scenario: A profile is reset
- **WHEN** an operator runs `reset` for a target
- **THEN** the service SHALL install the exact default v1 profile
- **AND** it SHALL synchronize the target immediately

#### Scenario: A scalar amount is invalid
- **WHEN** an operator supplies negative progress or power, or stability outside `0..100`
- **THEN** command/service validation SHALL reject the input
- **AND** the target profile SHALL remain unchanged

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

### Requirement: The foundation exposes no awakening or gameplay command
The cultivation command subtree SHALL NOT provide random awakening, meditation, cultivation-gain, breakthrough, technique-execution, spiritual-power recovery, cooldown, equipment, or combat commands.

#### Scenario: The command tree is inspected
- **WHEN** the registered `/myvillage cultivation` and `/myvillage xiulian` literals are enumerated
- **THEN** only the specified info, reset, deterministic set/clear, learned-technique maintenance commands, and their documented pinyin aliases SHALL be present
