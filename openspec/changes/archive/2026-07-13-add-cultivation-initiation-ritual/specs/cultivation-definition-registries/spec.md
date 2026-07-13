## MODIFIED Requirements

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
