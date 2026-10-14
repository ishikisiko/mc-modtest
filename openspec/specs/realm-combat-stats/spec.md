# realm-combat-stats Specification

## Purpose
Define the server-owned realm growth, attribute composition and damage diagnostics shared by physical combatants.
## Requirements
### Requirement: Realm growth is explicit and bounded
The server SHALL derive health and attack scaling from data using exponential cultivated major-realm growth and linear minor-stage growth. Mortal baseline and player stage aliases SHALL be explicit. Player aliases SHALL cover the expanded playable registry through Foundation late and Golden Core late, retaining all existing mappings; the shared numerical ladder and its growth coefficients SHALL remain unchanged. A stage alias SHALL match its owning registered realm and zero-based minor-stage order. Rules MUST reject nonfinite numbers, invalid stages, boundary reversals and shipped values outside native attribute limits.

#### Scenario: Linear steps and exponential majors
- **WHEN** stages within one realm or equal-index stages in successive cultivated realms are evaluated
- **THEN** intra-realm differences are constant and successive realm values have the configured ratio

#### Scenario: Golden Core late is reached normally
- **WHEN** the server commits the final player transition to myvillage:golden_late
- **THEN** numerical statistics SHALL resolve it to the existing Golden Core major realm and minor index 2
- **AND** attribute refresh SHALL preserve current wounds and stable modifier identities

### Requirement: Attribute refresh is idempotent
Derived server modifiers SHALL have stable identities and SHALL refresh after profile changes and player lifecycle transitions. Refresh MUST NOT heal or compound prior modifiers.

#### Scenario: Repeated refresh
- **WHEN** a wounded player is refreshed twice at the same realm
- **THEN** effective attributes and current health equal the result after one refresh

### Requirement: Damage stages are shared and inspectable
Melee, sword qi and beast attacks SHALL use one numerical damage policy with explicit base, flat, move, additive-bonus and total factors. Vanilla armor and enchantments SHALL apply once; additional typed resistance SHALL apply once. The armor step SHALL use defender-normalized damage pressure while retaining its full entering damage for the final multiplication. Outgoing damage and its existing realm growth SHALL NOT be normalized again. Diagnostics SHALL separate requested damage, mitigation and health loss, and SHALL expose actual armor-step inputs independently of the original request and later mitigation.

#### Scenario: Neutral modifiers
- **WHEN** bonus and resistance are zero, total multiplier is one, and the defender's resolved armor scale is one
- **THEN** the ordinary move and vanilla mitigation results are preserved

#### Scenario: Invalid resistance
- **WHEN** configuration contains a nonfinite or unsupported resistance
- **THEN** validation fails naming the invalid field

### Requirement: Armor pressure uses defender realm scale
At an applicable armor step, the server SHALL retain the actual entering damage as `D_atArmor`, resolve the defender's existing realm growth as `S_def`, and use `D_pressure = D_atArmor / S_def` only in the vanilla armor pressure calculation. The resulting remaining fraction SHALL multiply the complete `D_atArmor`, not `D_pressure`. The resolver SHALL use the defender's valid existing server-owned realm assignment and `StatsRules.growth`; mortal and unassigned defenders SHALL use one. A missing, malformed, unknown or invalid assignment, or an unusable scale, SHALL safely resolve to one without rewriting the assignment or throwing from the damage path. Scale resolution SHALL NOT infer a realm from maximum health, equipment, the attacker or client data. This change SHALL NOT alter growth coefficients or grant additional armor or toughness.

#### Scenario: Same-realm damage and health scale together
- **WHEN** two valid defenders at different realms wear the same equipment and receive damage scaled by their respective defender realm factors with other mitigation held constant
- **THEN** their armor pressure and remaining armor fraction SHALL match the same unscaled baseline
- **AND** each fraction SHALL be applied to that defender's complete entering damage

#### Scenario: Attacker and defender are at different realms
- **WHEN** a scaled hit reaches a defender whose realm differs from the attacker
- **THEN** pressure SHALL use the defender's scale and the actual entering damage
- **AND** the attacker realm SHALL NOT be applied a second time or substituted for the defender scale

#### Scenario: Assignment cannot be resolved
- **WHEN** a defender has no assignment or its saved realm or stage cannot safely resolve
- **THEN** armor pressure SHALL equal the complete entering damage with scale one
- **AND** damage processing SHALL preserve the original assignment and ordinary vanilla result

#### Scenario: Zero damage reaches an applicable armor calculation
- **WHEN** entering armor damage is zero
- **THEN** normalization SHALL produce zero output without division by zero or a nonfinite result

### Requirement: Normalization preserves vanilla mitigation and equipment costs
Normalization SHALL change only the damage pressure used to compute the armor remaining fraction. The existing armor attribute ceiling of 30, toughness ceiling of 20, and base armor reduction ceiling of 80 percent SHALL remain unchanged. Normalization SHALL NOT impose that base ceiling on the combined result of enchantments, effects, typed resistance or absorption. The source's vanilla bypass tags SHALL continue to decide whether armor participates, regardless of whether the source has a registered custom resistance channel. Source enchantments, Protection, Breach, potion effects, typed resistance and absorption SHALL retain their existing applicable order and execute once. Shields, armor durability, attack-item durability, invulnerability-frame decisions, and prior damage changes SHALL retain their ordinary full-damage semantics. `D_atArmor` SHALL reflect what actually enters this step after any applicable prior shield, invulnerability or damage-event processing, rather than a cached original request. Applicable ordinary vanilla and external-mod damage SHALL follow the same defender policy without being relabeled as a physical source or constrained by the custom outgoing composition budget.

#### Scenario: A source bypasses armor
- **WHEN** source tags skip the armor step for a scaled defender
- **THEN** normalization SHALL NOT restore armor mitigation or scale the surviving damage
- **AND** the source's remaining bypass semantics SHALL remain unchanged

#### Scenario: Breach or Protection modifies mitigation
- **WHEN** an applicable hit carries Breach or the defender carries Protection
- **THEN** each vanilla enchantment effect SHALL participate once at its existing stage
- **AND** no duplicate enchantment or penetration calculation SHALL be introduced by normalization

#### Scenario: Effects resistance and absorption follow armor
- **WHEN** a hit encounters applicable potion effects, typed resistance and absorption
- **THEN** those layers SHALL operate on the complete damage remaining from their preceding layer in the existing order
- **AND** absorption SHALL consume the remaining complete damage rather than normalized armor pressure

#### Scenario: A shield blocks all or part of a hit
- **WHEN** ordinary shield processing blocks all or part of incoming damage
- **THEN** blocking and shield durability SHALL retain their original semantics
- **AND** an armor step SHALL use only the actual unblocked damage that reaches it, without introducing a step for a fully skipped armor path

#### Scenario: Armor and weapon durability are charged
- **WHEN** an accepted attack causes the ordinary equipment damage callbacks
- **THEN** armor and weapon durability costs and callback counts SHALL equal their existing full-damage behavior
- **AND** `D_pressure` SHALL NOT replace the damage used to charge durability

#### Scenario: Invulnerability permits only excess damage
- **WHEN** ordinary invulnerability-frame processing permits only the excess over a prior hit
- **THEN** normalization SHALL use the excess that actually reaches armor
- **AND** it SHALL NOT reuse the larger original request or change the decision to accept or reject damage

#### Scenario: An external source normally uses armor
- **WHEN** a finite external-mod or ordinary vanilla hit uses armor on a realm-assigned defender
- **THEN** the armor fraction SHALL use the same defender-normalized pressure
- **AND** source identity, subsequent custom-channel mapping and the source's own damage amount SHALL remain intact

### Requirement: Armor diagnostics describe the actual armor step
Damage diagnostics SHALL identify `D_atArmor`, `S_def`, `D_pressure` and the complete armor output for the actual damage application. They SHALL distinguish an applied armor step from a skipped or bypassed step and SHALL NOT fabricate an armor calculation from requested damage or later post-mitigation damage. These facts SHALL remain associated with the correct defender and damage invocation, including nested damage, while preserving the existing requested-damage, typed-resistance, absorption and actual-health-loss diagnostics. Invalid-assignment fallback SHALL be distinguishable from a valid realm scale without exposing a client-authoritative mutation path.

#### Scenario: A shield changes the entering amount
- **WHEN** a partially blocked hit reaches armor with less damage than the original request
- **THEN** its trace SHALL show that actual entering amount, the defender scale and the normalized pressure separately
- **AND** the complete armor output SHALL be traceable through subsequent mitigation and health loss

#### Scenario: Another hit occurs during resolution
- **WHEN** damage processing synchronously causes a nested hit
- **THEN** each trace SHALL retain its own target, entering armor damage and defender scale
- **AND** returning from the nested hit SHALL NOT substitute its armor facts into the enclosing damage application

#### Scenario: Armor was bypassed
- **WHEN** the source does not execute an armor calculation
- **THEN** diagnostics SHALL identify that skipped path rather than present a synthetic normalized armor result

### Requirement: Resistance penetration is independent for each classified channel
The mod SHALL register synchronized server-owned attributes `myvillage:metal_resistance_penetration`, `myvillage:wood_resistance_penetration`, `myvillage:water_resistance_penetration`, `myvillage:fire_resistance_penetration`, and `myvillage:earth_resistance_penetration`. Each SHALL default to zero, have a zero lower bound, and obtain its upper bound from the existing `StatsRules.limits.maxPenetration` value rather than a second tuning constant. Existing attribute synchronization, persistence, statistics diagnostics and both language files SHALL expose the independent values without a new client-authoritative payload. The existing `myvillage:resistance_penetration` registry ID SHALL remain available with its saved base value and modifier identities intact, but SHALL apply only to physical damage. Its English and Chinese display labels SHALL explicitly identify physical resistance penetration. Existing physical values SHALL NOT be copied, migrated or implicitly added into any elemental channel.

At the existing typed-resistance step, the server SHALL use the existing mapped damage type to select exactly one matching penetration attribute from the attacker: the retained physical attribute for physical damage, or the corresponding independent elemental attribute for an elemental channel. Other penetration attributes SHALL NOT contribute. Without an applicable living attacker, penetration SHALL be zero. Effective resistance SHALL retain the existing percentage-point subtraction, zero floor and configured bounds; penetration SHALL NOT be reinterpreted as a multiplier, applied twice or used as armor penetration. Unknown sources and sources excluded by the existing bypass policy SHALL retain their existing damage path. This change SHALL NOT alter armor normalization, vanilla mitigation order, source classification, outgoing damage growth, or add elemental attacks, skills, items or damage-source mappings. Physical and fire SHALL remain the currently connected runtime channels; independent routing support for metal, wood, water and earth SHALL NOT itself make attacks in those channels playable.

#### Scenario: One elemental value changes
- **WHEN** the attacker changes one of the five elemental penetration attributes while other attributes remain fixed
- **THEN** only typed-resistance calculations selecting that same elemental channel SHALL observe the new value
- **AND** physical and every other elemental channel SHALL retain their prior calculation

#### Scenario: Physical and fire use different values
- **WHEN** existing physical and fire sources are resolved for the same attacker with distinct physical and fire penetration
- **THEN** the physical source SHALL select only `myvillage:resistance_penetration` and the fire source SHALL select only `myvillage:fire_resistance_penetration`
- **AND** neither source SHALL sum, substitute or reuse the other value

#### Scenario: Penetration exceeds the matching resistance
- **WHEN** valid matching penetration is greater than the defender's resistance in that channel
- **THEN** effective resistance SHALL be zero under the existing subtraction rule
- **AND** the typed-resistance step SHALL NOT increase damage above its entering amount

#### Scenario: A classified source has no applicable attacker
- **WHEN** an existing classified damage source has no living attacker from which attributes can be read
- **THEN** the selected penetration SHALL be zero
- **AND** the defender's matching resistance and all other damage layers SHALL retain their existing behavior

#### Scenario: A legacy physical attribute is restored
- **WHEN** a saved entity has a nonzero base value or saved modifiers for `myvillage:resistance_penetration`
- **THEN** restoration SHALL retain that same physical attribute and its contributions
- **AND** new elemental channels SHALL remain zero unless independently authored values or modifiers exist

#### Scenario: Independently authored values survive restart
- **WHEN** an entity is saved with different independently authored physical and five-element penetration values and later restored
- **THEN** each value and its saved contributions SHALL remain associated with its own registry ID
- **AND** restoration SHALL NOT collapse, swap, duplicate or spread the values between channels

#### Scenario: An unknown or bypassed source is resolved
- **WHEN** the existing source classification or bypass policy does not select a typed-resistance channel
- **THEN** the damage SHALL follow that same existing path without selecting a fallback penetration attribute
- **AND** applicable armor, enchantment, effect, shield, absorption and invulnerability behavior SHALL remain unchanged

#### Scenario: A currently unconnected elemental route is tested
- **WHEN** a deterministic routing fixture selects metal, wood, water or earth
- **THEN** it SHALL select only that element's independently registered penetration attribute
- **AND** the fixture SHALL NOT establish a new shipped attack or damage-source mapping
