## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: The foundation exposes no awakening or gameplay command
**Reason**: The foundation intentionally had no awakening command, but this change adds the first narrow rules-based awakening and basic-technique inheritance routes while continuing to exclude meditation, cultivation gain, advancement, and technique execution.

**Migration**: Replace the prohibition with the initiation-only command boundary below; existing administrator commands and their low-level semantics remain available.

## ADDED Requirements

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
