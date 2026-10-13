# sword-qi-technique Specification

## Purpose

Define the server-owned sword qi technique behavior and evidence boundaries for the first learned sword skill.

See also: [Spiritual Power and Sword Qi](../../../docs/ai-kb/45_spiritual_power_sword_qi.md).

## Requirements

### Requirement: Sword qi is learned through the existing technique loop
The public grade-one `myvillage:jianqi_jue` technique SHALL be generated from the catalogue, available through an eligible sect scripture offering, and learned with existing manual comprehension and study rules. Possessing a manual alone SHALL NOT authorize casting. The held weapon SHALL resolve to the skill's registered weapon family without item-id branching.

#### Scenario: A player studies a borrowed manual
- **WHEN** an eligible player borrows the sword-qi manual and completes its study requirements
- **THEN** the learned technique SHALL authorize sword-qi use while holding a matching combat weapon

#### Scenario: A player has not learned the technique
- **WHEN** a player without the learned technique submits charge input
- **THEN** the server SHALL refuse the action without charging power

### Requirement: Charge and release carry only input intent
Charge, release and charge-cancel serverbound payloads SHALL be empty. The server SHALL determine charge time, eligibility, cost, launch direction, damage, and all collision tests. A valid charge SHALL launch at release or its bounded maximum. A cancelled charge SHALL spend no power. Duplicate or stale inputs SHALL NOT launch or charge twice. Opening a GUI or losing focus SHALL send at most one cancel intent and require key-up before another charge starts; the server SHALL accept this cancellation only while charging and SHALL NOT refund or interrupt an already released action.

#### Scenario: Charge is interrupted
- **WHEN** weapon identity, combat mode, life state, dimension, or another incompatible action invalidates a charge
- **THEN** the server SHALL cancel it without launching or spending power

#### Scenario: Release arrives more than once
- **WHEN** release input repeats after a successful launch
- **THEN** no second action or deduction SHALL occur

#### Scenario: Focus is lost during charging
- **WHEN** the client opens a GUI or loses focus with charge input held
- **THEN** it SHALL send one empty cancel intent and wait for key-up before accepting a fresh charge press
- **AND** a server session still charging SHALL cancel without spending, while an already released action SHALL continue without a refund


### Requirement: The advancing wave obeys world collision and damage authority
The wave SHALL advance over server ticks from a server-chosen origin and direction, use bounded data-defined geometry, stop at blocking world geometry, enforce its target limit, and damage a target at most once per launch. Target damage SHALL respect the existing dodge invulnerability and combat damage policy. Client visuals SHALL follow authoritative action state and SHALL NOT produce damage.

#### Scenario: A target stands behind a wall
- **WHEN** a blocking wall intersects the wave before that target
- **THEN** the target SHALL receive no damage from that launch
- **AND** the advancing presentation SHALL stop at the authoritative obstruction

#### Scenario: A target intersects several wave samples
- **WHEN** one launch intersects the same target on several ticks or samples
- **THEN** that target SHALL receive at most one successful damage application
