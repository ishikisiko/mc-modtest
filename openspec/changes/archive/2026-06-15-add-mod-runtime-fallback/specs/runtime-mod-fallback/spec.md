## ADDED Requirements

### Requirement: Registry-absent block ids resolve to a vanilla fallback
At placement time, any block id absent from the live block registry SHALL be resolved
to a deterministic vanilla (`minecraft:`) fallback block state. The fallback SHALL be a real, placeable block
state — the resolver SHALL NOT place `minecraft:air` and SHALL NOT crash when it
encounters an unknown id. A block id that IS present in the registry SHALL be placed
unchanged, including its blockstate properties.

#### Scenario: A mod block is placed in a world without that mod
- **WHEN** placement encounters `ars_nouveau:wall_sconce[...]` and `ars_nouveau` is not installed
- **THEN** the resolver SHALL place the mapped vanilla fallback state for that id
- **AND** it SHALL NOT place air and SHALL NOT throw.

#### Scenario: A mod block is placed in a world with that mod
- **WHEN** placement encounters `supplementaries:awning[...]` and `supplementaries` is installed
- **THEN** the resolver SHALL place `supplementaries:awning[...]` unchanged with its properties.

#### Scenario: A vanilla block is unaffected
- **WHEN** placement encounters a `minecraft:` id that exists in the registry
- **THEN** the resolver SHALL place it unchanged.

### Requirement: The fallback map is generated from style slot data
The `mod_id → vanilla fallback state` table SHALL be exported by a generator step
from the same per-style slot lists the building generator reads, so the runtime
fallback for a mod id is the trailing `minecraft:` fallback of the slot(s) that list
it. The table SHALL be a generated data file shipped under
`src/main/resources/data/myvillage/`, not a map hand-maintained in Java, and the
generator SHALL be reproducible. Where one mod id appears in slots with differing
vanilla fallbacks, the generator SHALL pick the fallback by a documented,
deterministic precedence.

#### Scenario: The fallback table is regenerated
- **WHEN** the fallback-map generator runs against the current style slot lists
- **THEN** it SHALL write a data file mapping every catalog mod id placed by any style to a `minecraft:` fallback state
- **AND** re-running it without source changes SHALL produce a byte-identical file.

#### Scenario: A mod id is added to a slot upstream
- **WHEN** a new catalog mod id is inserted into a style slot whose fallback is `minecraft:lantern`
- **THEN** regenerating the fallback map SHALL map that mod id to `minecraft:lantern` without any Java edit.

### Requirement: Confirmed decor mods are declared as optional dependencies
`src/main/resources/META-INF/neoforge.mods.toml` SHALL declare each confirmed decor
mod namespace (`ars_nouveau`, `farmersdelight`, `supplementaries`, `fetzisdisplays`,
`mcwfurnitures`, `mcwwindows`) as a `type = "optional"` dependency. The mod SHALL load
standalone when none of them are present and SHALL load them before itself when they
are present. No decor mod SHALL be declared `required`.

#### Scenario: The mod loads without the decor mods
- **WHEN** the mod is installed in a pack containing none of the optional decor mods
- **THEN** it SHALL load without error.

#### Scenario: The decor mods load when present
- **WHEN** the mod is installed alongside the optional decor mods
- **THEN** each declared mod SHALL be an optional dependency ordered before `myvillage`.
