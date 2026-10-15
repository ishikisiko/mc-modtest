# Alchemy Quality

## Purpose

Connect saved alchemy aptitude to attributed batches and quality-dependent medicine strength.

See also: [Alchemy](../../../docs/ai-kb/49_alchemy.md), [Crafting Aptitudes](../../../docs/ai-kb/50_crafting_aptitudes.md), [alchemy-foundation](../alchemy-foundation/spec.md).

## Requirements

### Requirement: Manual attributed batches
Each furnace batch SHALL require a server-validated start intent from a living non-spectator player using its valid nearby menu with initialized awakened alchemy aptitude. The furnace SHALL require valid materials and empty output, record the brewer UUID and freeze the aptitude and ceiling, lock active input slots and produce exactly one batch without automatic chaining.

#### Scenario: Two players share a furnace
- **WHEN** a second player presses start or attempts to replace ingredients during an active batch
- **THEN** the original batch owner and frozen values SHALL remain unchanged and no additional roll or cost SHALL occur

#### Scenario: Invalid start
- **WHEN** an unawakened, distant or wrong-menu player requests a start
- **THEN** no batch SHALL begin and no inputs SHALL be consumed

### Requirement: Persistent bounded quality
The server SHALL choose one quality from validated data at start, bounded by the frozen aptitude ceiling. The outcome SHALL be saved and SHALL NOT be rerolled on reload, pause, brewer logout, death or aptitude change. The client SHALL see the ceiling but not the outcome before completion. Incomplete legacy or corrupt batch data SHALL preserve items and require a new valid start.

#### Scenario: Restart a blocked batch
- **WHEN** a saved active batch reloads with blocked output
- **THEN** its owner, ceiling and result SHALL remain fixed and clearing output SHALL resume the same batch

### Requirement: Quality-bearing medicines
The three existing pill IDs SHALL carry quality using a bounded persistent synchronized component. Missing quality SHALL mean lower quality with original effects; ordinary new lower pills SHALL retain legacy stack identity. Quality SHALL affect strength only, with sustained-effect quality saved and restored; the shared interval, duration, eligibility, caps and interruption rules SHALL remain.

#### Scenario: Different qualities meet
- **WHEN** two stacks of the same medicine have different quality components
- **THEN** vanilla component-sensitive stacking SHALL prevent merging them

#### Scenario: Reload higher-quality medication
- **WHEN** a sustained higher-quality effect is saved and reloaded
- **THEN** its remaining duration and original quality strength SHALL persist without offline gain

#### Scenario: Legacy medicine
- **WHEN** an old pill or medication state has no quality field
- **THEN** it SHALL retain the pre-quality baseline strength
