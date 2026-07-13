# cultivation-definition-registries Specification

## Purpose

Define the synced datapack registries, codecs, paths, and minimal shipped data
for cultivation realms, spiritual elements, and techniques.
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
`RealmDefinition` SHALL contain a non-empty translation key, a non-negative sort order, a non-empty ordered stage list, and an optional next-realm `ResourceLocation`. Each `RealmStageDefinition` SHALL contain a stable stage `ResourceLocation`, a non-empty translation key, and a non-negative stage order. A realm SHALL NOT contain combat logic or speculative cultivation thresholds.

#### Scenario: A realm definition round-trips
- **WHEN** a valid realm with multiple ordered stages and a next realm is encoded and decoded
- **THEN** its translation key, sort order, complete stage records, and optional next-realm id SHALL be preserved

#### Scenario: A realm contains duplicate stage ids or orders
- **WHEN** validation examines a realm whose stage list repeats a stage id or stage order
- **THEN** the definition SHALL be rejected

#### Scenario: A stage is checked against a realm
- **WHEN** a service or command validates a requested realm-stage pair
- **THEN** it SHALL accept the pair only when the selected realm's stage list contains the exact stage id

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
`TechniqueDefinition` SHALL contain a non-empty translation key, a stable-string category, a non-negative grade, a duplicate-free list of element `ResourceLocation` values, and `TechniqueRequirements`. The initial category codec SHALL encode `core`, `active`, `movement`, and `body` as strings and SHALL NOT encode enum ordinals. A technique definition SHALL NOT contain execution code, qi cost, cooldown, damage, projectile, buff, effect-script, or equipment-slot data.

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
The mod SHALL ship element entries `myvillage:metal`, `myvillage:wood`, `myvillage:water`, `myvillage:fire`, and `myvillage:earth`; realm entries `myvillage:mortal`, `myvillage:qi_refining`, and `myvillage:foundation_establishment`; and technique entry `myvillage:basic_breathing`. Each shipped element SHALL explicitly declare `awakening_weight: 1`. The realm stage lists SHALL include `myvillage:mortal_unawakened`, `myvillage:mortal_qi_sensed`, `myvillage:qi_refining_1` through `myvillage:qi_refining_9`, and `myvillage:foundation_early` with valid unique order. `myvillage:basic_breathing` SHALL require minimum realm `myvillage:mortal` and minimum stage `myvillage:mortal_qi_sensed`, SHALL have no minimum element-affinity requirement, and SHALL remain metadata-only with no executor.

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
