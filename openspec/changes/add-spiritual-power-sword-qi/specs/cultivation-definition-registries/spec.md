## ADDED Requirements

### Requirement: Definitions expose power capacity and executable sword qi
Realm stages SHALL decode non-negative `spiritual_power_cap` values. The generated sword-qi active technique SHALL point to a registered matching-family combat skill. Existing inactive active-technique data SHALL remain data unless an executable skill is registered.

#### Scenario: Invalid stage capacity
- **WHEN** a stage declares a negative power cap
- **THEN** definition loading or validation SHALL reject it with a controlled diagnostic

## MODIFIED Requirements

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
