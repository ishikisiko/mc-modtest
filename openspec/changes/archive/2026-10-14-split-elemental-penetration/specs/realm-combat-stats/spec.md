## ADDED Requirements

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
