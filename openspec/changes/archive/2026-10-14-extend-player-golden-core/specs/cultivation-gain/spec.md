## MODIFIED Requirements

### Requirement: Stage caps prevent normal gameplay overflow
Normal and spirit settlements SHALL resolve optional positive
`cultivation_cap` from the current registered source stage, apply at most the
remaining progress, and never carry excess into another stage. A missing cap
SHALL make that stage non-cultivatable and non-chargeable. Existing over-cap data
SHALL be preserved but receive no further progress.

#### Scenario: Progress reaches a cap mid-settlement
- **WHEN** calculated normal or spirit gain exceeds the exact remaining stage capacity
- **THEN** stored progress SHALL equal the cap and no overflow SHALL be banked

#### Scenario: An over-cap migrated or debug profile meditates
- **WHEN** stored progress already exceeds the current cap
- **THEN** raw progress SHALL remain unchanged and no progress or stone cost SHALL be applied

#### Scenario: A stage has no cap in this release
- **WHEN** a player at terminal Golden Core late attempts cultivation gain
- **THEN** settlement SHALL reject the stage without gain, item cost, or synthesized future cap

#### Scenario: A newly supported stage cultivates
- **WHEN** an otherwise eligible Qi-IV through IX, Foundation, or Golden-Core-early/middle profile completes a due normal or spirit settlement
- **THEN** settlement SHALL use its current authored cap and cost under the existing arithmetic, never infer a future-stage cap

### Requirement: Stability and mastery remain one-times rate at the progress cap
Stability SHALL consolidate only after full progress, at current spiritual affinity per eligible ten-tick settlement, up to integer-floor half the source cultivation cap. Basic Breathing mastery SHALL remain on the independent `10/year` clock in both modes even when progress is capped. Spirit mode SHALL multiply neither stability nor mastery, and SHALL charge no stone for stability work.

#### Scenario: A capped player stabilizes normally
- **WHEN** progress is capped and stability or mastery can still increase
- **THEN** applicable stability SHALL accrue at current affinity up to its derived cap and mastery SHALL accrue on its unchanged configured-year clock

#### Scenario: Stability is already at its derived cap
- **WHEN** another stability point becomes due
- **THEN** stability SHALL remain at its stage-derived cap without blocking applicable mastery

### Requirement: Spirit meditation gains fifty progress for a complete layer-priced batch
An active eligible server-owned spirit session SHALL require registered learned
Basic Breathing to produce spirit progress. Every ten eligible active ticks,
the server SHALL derive a complete cost from the authoritative source stage's
optional positive `spirit_stone_cost` definition field and,
when funded, remove that many `myvillage:low_grade_spirit_stone` items and add
exactly `50` total progress when at least 50 capacity remains. Qi-sensed mortal
and Qi I SHALL cost `1`, Qi II SHALL cost `2`, and Qi III SHALL cost `3`. Newly cultivatable higher stages SHALL declare their own positive source-stage costs; the server SHALL read them from current data, never derive them from an id prefix or reuse a lower-stage Java constant. A
nonempty final batch with less than 50 remaining capacity SHALL pay the same
complete cost and clamp output to the exact cap. Affinity SHALL not alter spirit
gain or cost.

#### Scenario: Qi II completes a funded spirit batch
- **WHEN** an eligible Qi-II profile has at least 50 remaining capacity and at least two ordinary-inventory stones
- **THEN** exactly two stones SHALL be consumed and progress SHALL increase by exactly `50`

#### Scenario: Qi-sensed initiation remains usable
- **WHEN** an eligible qi-sensed mortal completes a funded spirit batch
- **THEN** exactly one stone SHALL be consumed for the same fixed `50` progress

#### Scenario: A funded final batch has thirty capacity
- **WHEN** an eligible Qi-III profile is 30 points below its cap and holds at least three stones
- **THEN** exactly three stones SHALL be consumed and progress SHALL increase by exactly `30` to the cap

#### Scenario: Affinity changes during spirit mode
- **WHEN** the authoritative profile affinity differs from `10`
- **THEN** a funded spirit batch SHALL still produce the fixed total `50` before cap clamping

#### Scenario: A cultivatable stage omits its spirit cost
- **WHEN** a registered source stage has a cultivation cap but no positive `spirit_stone_cost`
- **THEN** definition validation SHALL reject the inconsistent stage rather than infer a cost from its id

#### Scenario: A higher-stage spirit batch is funded
- **WHEN** a supported Foundation or Golden Core source has remaining progress capacity and its complete data-defined cost is present in ordinary inventory
- **THEN** one due spirit settlement SHALL consume exactly that complete cost and add `50` or the exact remaining capacity, preserving the existing atomic commit and downgrade rules
