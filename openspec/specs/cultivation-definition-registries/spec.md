# cultivation-definition-registries Specification

## Purpose

Define the synced datapack registries, codecs, paths, and minimal shipped data
for cultivation realms, spiritual elements, and techniques.
See also: [Spiritual Power and Sword Qi](../../../docs/ai-kb/45_spiritual_power_sword_qi.md), [Technique System and Study](../../../docs/ai-kb/41_technique_system.md).
## Requirements
### Requirement: Cultivation definitions are loaded from three datapack registries
The mod SHALL register synced datapack registries with keys `myvillage:realm`, `myvillage:spiritual_element`, and `myvillage:technique` by handling `DataPackRegistryEvent.NewRegistry` on the mod event bus. Each registry SHALL use a persistent `Codec` and a non-null network `Codec`. Realm, element, and technique definitions SHALL NOT be implemented as hard-coded Java tables or definition enums.

#### Scenario: A server loads the cultivation registries
- **WHEN** datapack registries are created during server startup
- **THEN** all three registry keys SHALL be present in the server's `RegistryAccess`
- **AND** their definitions SHALL be loaded through the codecs registered for those keys

#### Scenario: A client joins the server
- **WHEN** the server completes registry synchronization for a compatible client
- **THEN** the client registry access SHALL contain the server's realm, spiritual-element, and technique definitions
- **AND** a profile snapshot SHALL NOT need to duplicate those definitions

### Requirement: Custom registry JSON paths follow the Minecraft 1.21.1 registry-key layout
For entries whose namespace is `myvillage`, realm JSON SHALL load from `data/myvillage/myvillage/realm/`, spiritual-element JSON SHALL load from `data/myvillage/myvillage/spiritual_element/`, and technique JSON SHALL load from `data/myvillage/myvillage/technique/`. The shipped resource roots SHALL therefore be `src/main/resources/data/myvillage/myvillage/realm/`, `src/main/resources/data/myvillage/myvillage/spiritual_element/`, and `src/main/resources/data/myvillage/myvillage/technique/`.

#### Scenario: The shipped mortal definition is discovered
- **WHEN** the server loads entry `myvillage:mortal` from registry `myvillage:realm`
- **THEN** it SHALL read `src/main/resources/data/myvillage/myvillage/realm/mortal.json`

#### Scenario: A datapack provides a foreign entry namespace
- **WHEN** a datapack supplies entry `<pack_namespace>:example` for registry `myvillage:technique`
- **THEN** the registry loader SHALL resolve it under `data/<pack_namespace>/myvillage/technique/example.json`

### Requirement: Realm definitions expose only foundation metadata
`RealmDefinition` SHALL retain its translation, ordering, stages, optional
next-realm, and positive maximum-lifespan fields. Each `RealmStageDefinition`
SHALL retain id, translation, order, optional positive cultivation cap, and
non-negative `spiritual_power_cap` (omitted values default to zero), and
MAY add one optional `advancement` record containing explicit target realm and
stage ids, stable-string kind, positive duration ticks, required stability,
stability cost, and interruption stability loss. Definitions SHALL contain no
gain rates, random chance, executable behavior, combat logic, pills, facility,
environment, or tribulation effects.

#### Scenario: A realm definition round-trips with advancement
- **WHEN** a valid stage has a complete advancement record
- **THEN** every target, kind, duration, requirement, cost, loss, cap, and foundation field SHALL round-trip exactly

#### Scenario: A stage omits advancement
- **WHEN** an otherwise valid stage has no advancement record
- **THEN** it SHALL remain loadable and expose no transition in this release

#### Scenario: A stage is checked against a realm
- **WHEN** a service validates a requested realm-stage pair
- **THEN** it SHALL accept the pair only when the selected realm contains the exact stage id

#### Scenario: A stage power capacity round-trips
- **WHEN** a valid stage declares a spiritual power cap
- **THEN** the codec SHALL preserve it without embedding recovery or combat execution in the definition

### Requirement: Spiritual-element definitions are extensible and non-combat
`SpiritualElementDefinition` SHALL contain a non-empty translation key, a non-negative sort order, an optional display color from `0x000000` through `0xFFFFFF`, and an integer `awakening_weight` from `0` through `1_000_000`. The codec SHALL treat omitted `awakening_weight` as `1` so existing datapacks remain loadable. Weight `0` SHALL exclude the element from ordinary awakening and positive weight SHALL make it eligible for weighted selection. The definition SHALL NOT contain element matchups, damage multipliers, cultivation multipliers, root-quality ranks, or combat bonuses, and the registry SHALL permit datapacks to add elements beyond the five shipped entries.

#### Scenario: An element definition round-trips
- **WHEN** a valid element with a display color and awakening weight is encoded and decoded
- **THEN** its translation key, sort order, color, and awakening weight SHALL be preserved

#### Scenario: An old datapack omits awakening weight
- **WHEN** an otherwise valid spiritual-element JSON does not contain `awakening_weight`
- **THEN** the codec SHALL load it with awakening weight `1`

#### Scenario: An element color is invalid
- **WHEN** an element definition contains a display color below `0x000000` or above `0xFFFFFF`
- **THEN** the definition codec or validation SHALL reject it

#### Scenario: An awakening weight is invalid
- **WHEN** an element definition contains an awakening weight below `0` or above `1_000_000`
- **THEN** the definition codec and shipped-data validation SHALL reject it

#### Scenario: A zero-weight element is loaded
- **WHEN** a datapack intentionally sets `awakening_weight` to `0`
- **THEN** the definition SHALL remain valid and available for profile/display references
- **AND** ordinary awakening SHALL not select it

### Requirement: Technique definitions carry metadata but no executor
`TechniqueDefinition` SHALL contain a non-empty translation key, a stable-string category, a non-negative grade, a duplicate-free list of element `ResourceLocation` values, and `TechniqueRequirements`. The initial category codec SHALL encode `core`, `active`, `movement`, and `body` as strings and SHALL NOT encode enum ordinals. A technique definition SHALL remain declarative and contain no executable code or effect script. Its category-matched `effects.active` block MAY carry a skill reference, non-negative `qi_cost` and positive `slots`; execution SHALL be resolved by the registered combat runtime rather than embedded in the definition. Existing category-matched core, movement and body declarations SHALL retain their codec and eligibility rules.

#### Scenario: A technique definition round-trips
- **WHEN** a valid technique with category, grade, elements, and requirements is encoded and decoded
- **THEN** every metadata field and referenced id SHALL be preserved

#### Scenario: A category is encoded
- **WHEN** an initial technique category is serialized
- **THEN** the encoded value SHALL be its stable lowercase string
- **AND** it SHALL NOT depend on Java declaration order

#### Scenario: A technique repeats an element id
- **WHEN** a technique lists the same element id more than once
- **THEN** the definition SHALL be rejected rather than silently deduplicated

### Requirement: Technique requirements remain declarative and reference-safe
`TechniqueRequirements` SHALL contain optional minimum-realm id, optional minimum-stage id, and an immutable map from element ids to minimum affinity basis points in `0..10000`. A minimum stage SHALL require a minimum realm, and the stage SHALL belong to that realm. Runtime eligibility SHALL be evaluated only through the shared `TechniqueRequirementEvaluator` against the current registries and immutable profile; individual blocks, command handlers, and inheritance services SHALL NOT replace definition rules with hard-coded realm, stage, or element gates.

#### Scenario: Requirements reference a valid realm and stage
- **WHEN** a technique names a registered minimum realm and a stage belonging to that realm
- **THEN** registry-reference validation SHALL accept the pair

#### Scenario: Requirements name a stage without a realm
- **WHEN** a technique supplies a minimum stage but no minimum realm
- **THEN** definition validation SHALL reject it

#### Scenario: Requirements name a stage from another realm
- **WHEN** a technique's minimum stage is absent from its minimum realm's stage list
- **THEN** registry-reference validation SHALL fail with the offending technique, realm, and stage ids

#### Scenario: An affinity requirement is out of range
- **WHEN** a minimum element affinity is below `0` or above `10000`
- **THEN** the technique requirements SHALL be rejected

#### Scenario: A gameplay service checks eligibility
- **WHEN** normal-rules technique inheritance evaluates a registered technique
- **THEN** it SHALL pass that definition's current `TechniqueRequirements` to the shared evaluator
- **AND** it SHALL fail closed on missing or ambiguous registry references

### Requirement: Runtime definition lookups use current RegistryAccess
Commands and services that require a definition SHALL query the current `RegistryAccess` using the three declared registry keys. They SHALL NOT infer definitions from string prefixes, cached static maps, or the existing cultivation structure/style vocabulary.

#### Scenario: A datapack definition is added or removed
- **WHEN** the current registry contents differ from the shipped defaults
- **THEN** command validation and suggestions SHALL reflect the current registry contents
- **AND** they SHALL NOT continue using a stale hard-coded id list

#### Scenario: A player profile names a missing definition
- **WHEN** runtime lookup cannot resolve a syntactically valid stored id
- **THEN** the definition SHALL be treated as unavailable
- **AND** the profile SHALL remain decodable and unchanged

### Requirement: The mod ships the minimum cultivation definition set
The mod SHALL ship element entries `myvillage:metal`, `myvillage:wood`, `myvillage:water`, `myvillage:fire`, and `myvillage:earth`; realm entries `myvillage:mortal`, `myvillage:qi_refining`, `myvillage:foundation_establishment`, and `myvillage:golden_core`; and technique entry `myvillage:basic_breathing`. Each shipped element SHALL explicitly declare `awakening_weight: 1`. The realm stage lists SHALL include `myvillage:mortal_unawakened`, `myvillage:mortal_qi_sensed`, `myvillage:qi_refining_1` through `myvillage:qi_refining_9`, `myvillage:foundation_early`, `myvillage:foundation_middle`, `myvillage:foundation_late`, `myvillage:golden_early`, `myvillage:golden_middle`, and `myvillage:golden_late` with valid unique order. Existing realm and stage ids SHALL retain their meaning and ordering; new Foundation and Golden Core stages SHALL use early/middle/late order. Player realm data SHALL NOT register or unlock Nascent Soul. `myvillage:basic_breathing` SHALL require minimum realm `myvillage:mortal` and minimum stage `myvillage:mortal_qi_sensed`, SHALL have no minimum element-affinity requirement, and SHALL remain metadata-only with no executor.

#### Scenario: Foundation data is validated
- **WHEN** the shipped cultivation data validator runs
- **THEN** it SHALL find every required element, realm, stage, and `basic_breathing` definition
- **AND** each stage SHALL belong to exactly one shipped realm
- **AND** all five shipped elements SHALL have legal explicit awakening weight `1`

#### Scenario: Basic breathing is loaded
- **WHEN** `myvillage:basic_breathing` is resolved from the technique registry
- **THEN** its requirements SHALL name `myvillage:mortal` and `myvillage:mortal_qi_sensed`
- **AND** it SHALL impose no element-id, affinity, or root-count preference
- **AND** loading it SHALL NOT register or execute a cultivation effect

#### Scenario: Any valid awakened root is evaluated for basic breathing
- **WHEN** a `mortal_qi_sensed` profile contains a valid one-, two-, three-, four-, or five-element spiritual root
- **THEN** the element portion of the shipped basic-breathing requirements SHALL pass for every such root

### Requirement: Shipped definitions have English and Chinese names
Every shipped realm, realm stage, spiritual element, and technique SHALL declare a translation key, and `en_us` and `zh_cn` language files SHALL contain every declared key.

#### Scenario: Translation coverage is checked
- **WHEN** shipped cultivation definitions are validated
- **THEN** every declared translation key SHALL resolve in both language files
- **AND** missing or duplicate cultivation translation entries SHALL fail validation

### Requirement: Realm definitions declare maximum lifespan
Every `RealmDefinition` SHALL contain a positive integer
`maximum_lifespan_years`. Runtime lifespan calculations SHALL resolve the field
through current `RegistryAccess` and SHALL not infer it from realm id, sort
order, a Java table, or the player profile. The shipped mortal, qi-refining, and
foundation-establishment, and golden-core values SHALL be `80`, `120`, `240`, and `500` respectively.

#### Scenario: Shipped realm data is validated
- **WHEN** the four shipped realm definitions load
- **THEN** each SHALL expose its exact positive maximum lifespan

#### Scenario: A datapack omits or invalidates the field
- **WHEN** a realm omits `maximum_lifespan_years` or supplies zero, a negative value, or a non-integer
- **THEN** definition loading or reference validation SHALL fail with the realm id and field

#### Scenario: A current realm is resolved
- **WHEN** lifespan status is calculated for a profile
- **THEN** the service SHALL use the maximum from the current registered realm definition

### Requirement: Advancement records are bounded and reference-safe
An advancement record SHALL use kind `ordinary` or `bottleneck`, positive
`duration_ticks`, non-negative stability values derived from the source cap, non-negative
interruption loss, and `stability_cost <= required_stability`. Its target realm
and target stage SHALL resolve as one exact pair through current
`RegistryAccess`; a source SHALL not target itself. An advancement source SHALL declare a cultivation cap and positive spirit cost; `required_stability` SHALL equal integer-floor half that cap, and compatibility `stability_cost` SHALL equal `required_stability - floor(required_stability / 2)`. Successful gameplay advancement SHALL retain integer-floor half of actual current stability instead of subtracting this compatibility field as an additional charge. Unknown kinds, invalid
bounds, missing targets, and mismatched realm/stage targets SHALL fail loading
or deterministic validation with source context.

#### Scenario: A cross-realm target resolves
- **WHEN** mortal qi-sensed targets Qi Refining I in the Qi Refining realm
- **THEN** validation SHALL accept the exact registered realm-stage pair

#### Scenario: A target stage belongs to another realm
- **WHEN** the declared target realm does not contain the declared target stage
- **THEN** validation SHALL reject the source rule rather than infer a different realm

#### Scenario: A major kind appears early
- **WHEN** this release decodes an advancement kind other than `ordinary` or `bottleneck`
- **THEN** it SHALL reject the value as unsupported rather than silently treating it as ordinary

### Requirement: Definitions expose power capacity and executable sword qi
Realm stages SHALL decode non-negative `spiritual_power_cap` values. Existing mortal and Qi capacities SHALL remain unchanged. Foundation early/middle/late SHALL author capacities `200/250/300`, and Golden Core early/middle/late SHALL author `400/500/600`; terminal progression status SHALL NOT remove the final stage's usable power capacity. The generated sword-qi active technique SHALL point to a registered matching-family combat skill. Existing inactive active-technique data SHALL remain data unless an executable skill is registered.

#### Scenario: Invalid stage capacity
- **WHEN** a stage declares a negative power cap
- **THEN** definition loading or validation SHALL reject it with a controlled diagnostic

#### Scenario: The terminal stage is resolved for power
- **WHEN** the server or synchronized client resolves Golden Core late
- **THEN** its power capacity SHALL be available from stage data even though it has no cultivation cap or advancement

### Requirement: Generated beginner manuals keep an affinity-paced short study
Generated grade-one technique `study` blocks SHALL come from `study_by_grade`
in `tools/technique_catalogue/rules.json`, with 160 required points, no gates
and no gate stability cost. At default affinity 10 and 20 TPS without an
element match, the existing preparation and settlement schedule SHALL complete
a fresh manual in approximately 10 seconds including preparation. Higher
grades, element matching, affinity-based gain and the omitted-`study` codec
fallback SHALL remain unchanged. This retune SHALL NOT change ordinary
cultivation gain or generate a new manual item per technique.

#### Scenario: A fresh beginner manual is studied at default affinity
- **WHEN** an eligible affinity-10 player starts a fresh grade-one manual
  without an element match
- **THEN** 40 preparation ticks and sixteen ten-tick gain settlements SHALL
  satisfy the study threshold
- **AND** the existing service-owned completion path SHALL learn the technique
  and consume the manual

#### Scenario: A beginner manual was partly read before the retune
- **WHEN** an eligible player resumes an old manual whose stored comprehension
  already meets the new threshold
- **THEN** the next eligible study settlement SHALL complete the manual
- **AND** no save migration SHALL reset its comprehension or alter other books

#### Scenario: Spiritual affinity is zero
- **WHEN** a player with zero affinity studies a beginner manual below its cap
- **THEN** the shorter threshold SHALL NOT bypass the existing zero-gain rule

### Requirement: Shipped playable stages declare exact cultivation caps
The shipped stage definitions SHALL explicitly author progress caps for every source stage in the playable chain from qi-sensed mortal through Golden Core middle. The existing qi-sensed and Qi-I/II/III caps SHALL remain `1000/1100/1200/1300`. Qi-IV through Qi-IX SHALL declare `1400/1500/1600/1700/1800/1900`; Foundation early/middle/late SHALL declare `3000/3600/4200`; Golden Core early/middle SHALL declare `6000/7200`. Each cultivatable stage SHALL declare a positive `spirit_stone_cost`, derive its stability cap as integer-floor half its progress cap, and use the current registered values during settlement. Unawakened mortal and terminal Golden Core late SHALL omit the cultivation cap and advancement rule. Runtime SHALL NOT synthesize a missing cap from id, order or a formula.

#### Scenario: Initial stage data is validated
- **WHEN** shipped realm definitions are loaded
- **THEN** all fifteen supported source stages SHALL expose their explicitly authored caps and positive spirit costs
- **AND** unawakened mortal and Golden Core late SHALL remain uncapped and unavailable for gain

#### Scenario: A datapack reload changes a cap
- **WHEN** settlement next resolves the player's current stage after a successful registry reload
- **THEN** it SHALL use the current registered cap rather than a cached Java constant

### Requirement: Shipped advancement data defines a complete Golden Core chain
Shipped data SHALL declare exactly fifteen immediate source-target transitions: qi-sensed mortal to Qi I; each Qi layer to the next through Qi IX; Qi IX to Foundation early; Foundation early to middle to late; Foundation late to Golden Core early; and Golden Core early to middle to late. Each cultivatable source SHALL have exactly one authored transition, and Golden Core late SHALL have none. The four existing transitions SHALL retain their current cap, duration, kind, required stability, compatibility cost and interruption-loss values: qi-sensed to Qi I `1000/ordinary/100/500/250/0`, Qi I to II `1100/ordinary/100/550/275/0`, Qi II to III `1200/ordinary/120/600/300/0`, and Qi III to IV `1300/bottleneck/200/650/325/5`. New duration, spirit cost and interruption-loss values SHALL be explicit source-stage data. Qi IX to Foundation early and Foundation late to Golden Core early SHALL use the existing `bottleneck` kind, not a new advancement kind. No source SHALL skip a stage, form a cycle, target Nascent Soul or infer its target at runtime.

#### Scenario: Shipped sequence is validated
- **WHEN** deterministic cultivation data validation runs
- **THEN** it SHALL find the complete fifteen-transition chain with the first four tuples unchanged and every new source-target tuple matching its authored resource
- **AND** it SHALL find no rule or cap at Golden Core late and no player transition into Nascent Soul

#### Scenario: An existing source has an administrator-created profile
- **WHEN** a saved profile already names Qi IV through IX or Foundation early
- **THEN** current registry lookup SHALL make that same id usable under its newly authored rule without resetting its profile counters

#### Scenario: A data change introduces a cycle or skip
- **WHEN** an authored source targets a previous stage, itself, or a non-immediate successor
- **THEN** deterministic shipped-chain validation SHALL reject the resource with source and target context
