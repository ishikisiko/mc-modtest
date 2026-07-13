## ADDED Requirements

### Requirement: GuideME is a reproducible hard dependency
MyVillage SHALL compile against the published GuideME `21.1.17` API classifier and SHALL load the published full ProGuard GuideME `21.1.17` artifact at development runtime from Maven Central. NeoForge metadata SHALL declare `guideme` required on `BOTH` sides with a compatibility range whose minimum is `21.1.17`. The build SHALL NOT depend on, vendor, commit, or embed the untracked root-level official release shadow jar `guideme-21.1.17.jar`.

#### Scenario: Gradle resolves the integration on a clean checkout
- **WHEN** the root-level GuideME jar is absent and Gradle resolves compile and runtime dependencies
- **THEN** the API and full mod SHALL resolve from Maven Central using the configured GuideME version
- **AND** MyVillage SHALL compile without a local file dependency

#### Scenario: GuideME is omitted from an installed mod set
- **WHEN** NeoForge evaluates MyVillage's dependencies without a compatible GuideME mod
- **THEN** MyVillage SHALL be rejected as missing a required dependency on both client and dedicated server

#### Scenario: The MyVillage jar is inspected
- **WHEN** the practical mod jar is built
- **THEN** it SHALL NOT contain a nested GuideME jar or copied GuideME classes

### Requirement: The cultivation guide has one data-driven identity and exact bilingual topology
The mod SHALL define the data-driven guide `myvillage:cultivation` at `src/main/resources/assets/myvillage/guideme_guides/cultivation.json` with `zh_cn` as its default language. Repository-root `guidebook/` SHALL be the only authored Markdown truth and SHALL contain exactly the default-language page paths `index.md`, `getting_started/initiation.md`, and `getting_started/cultivation_loop.md`, plus path-matched English translations under `_en_us/`. Resource processing SHALL package those six files below `assets/myvillage/guides/myvillage/cultivation/`; navigation and internal links SHALL resolve within the three paths in both languages. The first-slice guide definition SHALL NOT declare `custom_colors`, and its pages SHALL NOT reference a custom color id because the GuideME 21.1.17 custom-color codec path is not a trusted compatibility surface.

#### Scenario: The guide is discovered with the default locale
- **WHEN** GuideME loads `myvillage:cultivation` for a locale without a complete translation
- **THEN** it SHALL resolve packaged `index.md` from the Chinese default-language source
- **AND** both detail pages SHALL remain reachable from the guide navigation

#### Scenario: English is selected
- **WHEN** GuideME loads the guide for `en_us`
- **THEN** `_en_us/index.md`, `_en_us/getting_started/initiation.md`, and `_en_us/getting_started/cultivation_loop.md` SHALL provide a complete path-matched English surface

#### Scenario: The first-slice page tree is inspected
- **WHEN** authored Markdown below root `guidebook/` is enumerated
- **THEN** no fourth topic page or unpaired Chinese/English page SHALL be present in this change
- **AND** no checked-in page mirror SHALL exist under `src/main/resources/assets/myvillage/guides/myvillage/cultivation/`

#### Scenario: The guide definition and page markup are inspected
- **WHEN** first-slice color configuration and components are enumerated
- **THEN** `cultivation.json` SHALL contain no `custom_colors` member
- **AND** no page SHALL depend on a custom color id

### Requirement: The three pages describe the released 0.25.0 cultivation loop
The paired pages SHALL describe only implemented MyVillage 0.25.0 behavior. `index.md` SHALL present the order from spiritual-root awakening and Basic Breathing inheritance through meditation, sequential progress/stability growth, and deterministic advancement, and SHALL identify Qi Refining IV as the release ceiling. `getting_started/initiation.md` SHALL explain the independent testing-stele then inheritance-stele interactions and the non-rerolling/non-resetting repeat boundary. `getting_started/cultivation_loop.md` SHALL combine the Profile/Meditation H-screen surface, normal and direct-spirit-stone modes, current eligibility/interruption boundaries, spirit-stone acquisition and stage-owned costs, progress-before-stability ordering, lifespan gating, and one-stage deterministic advancement with retained integer-floor half stability.

#### Scenario: A new player follows the Chinese guide
- **WHEN** the player reads `index.md`, `getting_started/initiation.md`, and `getting_started/cultivation_loop.md` in order
- **THEN** the described sequence SHALL be sufficient to reach each currently released advancement through Qi Refining IV
- **AND** it SHALL distinguish awakening, inheritance, cultivation progress, stability consolidation, and advancement as separate steps

#### Scenario: The English pair is compared with the Chinese source
- **WHEN** the six pages are reviewed for factual parity
- **THEN** both languages SHALL describe the same prerequisites, ordering, costs, interruption boundary, advancement semantics, and release ceiling

#### Scenario: Deferred cultivation systems are searched
- **WHEN** the guide describes the current version boundary
- **THEN** it SHALL NOT present Qi Refining V or later gain, Foundation Establishment gameplay, pills, facilities, tribulation, old-age death, or reincarnation as playable

### Requirement: Guide controls and indexed resources resolve live shipped entries
Every player-facing control instruction SHALL render the existing configurable mappings `key.myvillage.open_cultivation_profile`, `key.myvillage.start_normal_meditation`, `key.myvillage.start_spirit_meditation`, `key.myvillage.stop_meditation`, and `key.myvillage.start_advancement` through GuideME key-binding components rather than treating their default letters as fixed controls. The initiation page SHALL index both stele ids, and the cultivation-loop page SHALL index the low-grade spirit stone and both spirit-stone ore ids. Representative `ItemLink` and `BlockImage` components SHALL reference fully qualified shipped `myvillage:` ids.

#### Scenario: A player changes a MyVillage key binding
- **WHEN** the player opens a page that names the affected action
- **THEN** GuideME SHALL display the player's current configured binding rather than a hard-coded default letter

#### Scenario: An initiation stele is queried through GuideME item indexing
- **WHEN** GuideME resolves either `myvillage:spirit_testing_stele` or `myvillage:technique_inheritance_stele`
- **THEN** `getting_started/initiation.md` SHALL be its indexed cultivation-guide page
- **AND** the page SHALL contain valid visual/link markup for both shipped blocks

#### Scenario: A spirit-stone entry is queried through GuideME item indexing
- **WHEN** GuideME resolves `myvillage:low_grade_spirit_stone`, `myvillage:spirit_stone_ore`, or `myvillage:deepslate_spirit_stone_ore`
- **THEN** `getting_started/cultivation_loop.md` SHALL be its indexed cultivation-guide page
- **AND** representative stone and ore markup SHALL resolve without an unknown id

### Requirement: The custom handbook opens the guide without a MyVillage payload
The mod SHALL register `myvillage:cultivation_handbook` as a one-stack functional item and expose it in `myvillage:main`. Client use SHALL delegate to GuideME's public API to open `myvillage:cultivation` at the remembered page or default index, and SHALL NOT introduce a MyVillage guide-opening network payload. The item SHALL have bilingual name and tooltip entries.

#### Scenario: A player uses the handbook for the first time
- **WHEN** the player uses `myvillage:cultivation_handbook` in a client with GuideME loaded
- **THEN** GuideME SHALL open `myvillage:cultivation` at `index.md`
- **AND** the handbook stack SHALL remain available

#### Scenario: A player reopens the handbook
- **WHEN** GuideME has remembered a previously visited page for that player and the handbook is used again
- **THEN** the guide SHALL reopen through GuideME's normal remembered-page behavior

#### Scenario: Network registrations are compared
- **WHEN** the handbook integration is added
- **THEN** MyVillage SHALL register no new custom payload for opening, selecting, or navigating guide pages

### Requirement: The handbook reuses the GuideME base visual
The MyVillage handbook item model SHALL inherit `guideme:item/guide_base`. This change SHALL NOT add a MyVillage-specific handbook texture or copy GuideME's guide texture into the MyVillage namespace.

#### Scenario: Handbook client resources are inspected
- **WHEN** `assets/myvillage/models/item/cultivation_handbook.json` is resolved
- **THEN** its visual parent SHALL be `guideme:item/guide_base`
- **AND** no `assets/myvillage/textures/item/cultivation_handbook.png` SHALL be required or packaged

### Requirement: Live preview watches the packaged Markdown source
The build SHALL configure `processResources.from('guidebook').into('assets/myvillage/guides/myvillage/cultivation')` and SHALL provide a `runGuide` client configuration whose GuideME source property points directly at repository-root `guidebook/` and uses the matching `myvillage` source namespace. `runGuide` SHALL set both `guideme.showOnStartup` and `guideme.validateAtStartup` to the guide id `myvillage:cultivation`, allowing GuideME to resolve its configured default index. The change SHALL NOT maintain a second checked-in Markdown tree under `src/main/resources`.

#### Scenario: An author edits a packaged page during runGuide
- **WHEN** the author saves one of the six Markdown files while `runGuide` is active
- **THEN** GuideME SHALL monitor that same source path for live reload
- **AND** GuideME SHALL validate `myvillage:cultivation` at startup
- **AND** no synchronization step to another guide source directory SHALL be necessary

#### Scenario: A normal jar is built
- **WHEN** Gradle processes resources outside the preview run
- **THEN** it SHALL copy the same cultivation-guide tree that `runGuide` monitors into generated resource output
- **AND** it SHALL NOT write the copied pages into the source resource tree

### Requirement: The first guide slice does not create an external documentation pipeline
This change SHALL NOT add MkDocs configuration, Wiki publication, external-site generation, a dual-output common-source converter, or long-form post-ceiling documentation. Those surfaces SHALL remain deferred until the GuideME compatibility slice is accepted.

#### Scenario: The change scope is reviewed
- **WHEN** guide-related build scripts and documentation roots are enumerated
- **THEN** only root `guidebook/`, its one-way packaging mapping, and its direct preview configuration SHALL be part of the guide-authoring path
