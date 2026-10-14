## RENAMED Requirements

- FROM: `### Requirement: Shipped initial stages declare exact cultivation caps`
- TO: `### Requirement: Shipped playable stages declare exact cultivation caps`

- FROM: `### Requirement: Shipped advancement data defines exactly four transitions`
- TO: `### Requirement: Shipped advancement data defines a complete Golden Core chain`

## MODIFIED Requirements

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

### Requirement: Shipped playable stages declare exact cultivation caps
The shipped stage definitions SHALL explicitly author progress caps for every source stage in the playable chain from qi-sensed mortal through Golden Core middle. The existing qi-sensed and Qi-I/II/III caps SHALL remain `1000/1100/1200/1300`. Qi-IV through Qi-IX SHALL declare `1400/1500/1600/1700/1800/1900`; Foundation early/middle/late SHALL declare `3000/3600/4200`; Golden Core early/middle SHALL declare `6000/7200`. Each cultivatable stage SHALL declare a positive `spirit_stone_cost`, derive its stability cap as integer-floor half its progress cap, and use the current registered values during settlement. Unawakened mortal and terminal Golden Core late SHALL omit the cultivation cap and advancement rule. Runtime SHALL NOT synthesize a missing cap from id, order or a formula.

#### Scenario: Initial stage data is validated
- **WHEN** shipped realm definitions are loaded
- **THEN** all fifteen supported source stages SHALL expose their explicitly authored caps and positive spirit costs
- **AND** unawakened mortal and Golden Core late SHALL remain uncapped and unavailable for gain

#### Scenario: A datapack reload changes a cap
- **WHEN** settlement next resolves the player's current stage after a successful registry reload
- **THEN** it SHALL use the current registered cap rather than a cached Java constant

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

### Requirement: Definitions expose power capacity and executable sword qi
Realm stages SHALL decode non-negative `spiritual_power_cap` values. Existing mortal and Qi capacities SHALL remain unchanged. Foundation early/middle/late SHALL author capacities `200/250/300`, and Golden Core early/middle/late SHALL author `400/500/600`; terminal progression status SHALL NOT remove the final stage's usable power capacity. The generated sword-qi active technique SHALL point to a registered matching-family combat skill. Existing inactive active-technique data SHALL remain data unless an executable skill is registered.

#### Scenario: Invalid stage capacity
- **WHEN** a stage declares a negative power cap
- **THEN** definition loading or validation SHALL reject it with a controlled diagnostic

#### Scenario: The terminal stage is resolved for power
- **WHEN** the server or synchronized client resolves Golden Core late
- **THEN** its power capacity SHALL be available from stage data even though it has no cultivation cap or advancement
